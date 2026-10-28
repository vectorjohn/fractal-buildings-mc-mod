#!/usr/bin/env bash
# Performance budget (ROADMAP 6.7, plan B8): measure on live vanilla terrain and check mod/perf-budget.json.
#
#   scripts/perf.sh [--planner-only] [--check-only] [--client]   (the first two skip rows that were not measured; a full run fails on any absent row)
#   --world-mod-only: diagnostic, only the mod world's server run (no planner run, no base world, no merge, no check); logs every start decision ([LANE-START])
#
# Takes the heavy lock itself (timings); don't wrap it in flock. Steps:
#   1. mod:test PerfPlannerTest (no server): cold plan, region layer, multi-player cache pressure, veto query -> mod/build/perf/planner.json
#   2. the world half, on a DEDICATED SERVER with no client and no renderer (PERFSRV; timings no longer share cores with lavapipe):
#      :mod:runPerfServer twice, variant mod then base (PerfServerHarness: /locate, a mock player's elytra-speed flight, cold and warmed
#      chunks vs a no-settlement world, region growth, heap, lane counters) -> world-mod.json, world-base.json, merged by
#      scripts/perf-merge.mjs into mod/build/perf/world.json. The run dir mod/build/run/perfServer needs eula.txt with eula=true; the script
#      writes it when missing (CLAUDE.md standing EULA approval: a local test server, 127.0.0.1, online-mode=false, set below).
#      --client runs the OLD form instead: mod:runClientGameTest with FB_PERF=only (PerfClientGameTest, in a lavapipe-rendered client),
#      kept to compare old and new numbers.
#   3. scripts/perf-check.mjs compares both with the budget (fails above the CI ceiling, warns above the budget) and prints a table;
#      also writes mod/build/perf/summary.md.
# Quiet-machine rule: numbers that decide a budget need loadavg under 2; the check prints the load measured.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# A copy without .git (scripts/remote.sh on the worker) uses its own root.
if GIT_COMMON="$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)"; then
	MAIN_ROOT="$(dirname "$GIT_COMMON")"
else
	MAIN_ROOT="$ROOT"
fi
HEAVY_LOCK="$MAIN_ROOT/.claude/heavy.lock"
mkdir -p "$(dirname "$HEAVY_LOCK")" "$ROOT/mod/build/perf"
cd "$ROOT"
PLANNER_ONLY=0; CHECK_ONLY=0; CLIENT=0; MOD_ONLY=0
for a in "$@"; do case "$a" in --planner-only) PLANNER_ONLY=1 ;; --check-only) CHECK_ONLY=1 ;; --client) CLIENT=1 ;; --world-mod-only) MOD_ONLY=1 ;; *) echo "unknown $a" >&2; exit 2 ;; esac; done
LOGDIR="$ROOT/mod/build/perf"
SRV="$ROOT/mod/build/run/perfServer"
if [ "$CHECK_ONLY" = 0 ]; then
	if [ "$PLANNER_ONLY" = 0 ] && [ "$CLIENT" = 0 ] && ! grep -qx 'eula=true' "$SRV/eula.txt" 2>/dev/null; then
		# standing EULA approval (CLAUDE.md): local test server bound to 127.0.0.1 with online-mode=false (server.properties below)
		mkdir -p "$SRV" && echo eula=true > "$SRV/eula.txt"
		echo "perf: wrote $SRV/eula.txt (standing EULA approval, local test server)" >&2
	fi
	rm -f mod/build/perf/*.json
	echo "perf: waiting for heavy lock ..." >&2
	# a failing gradle run (build error, test exception, crashed client) must fail the script: no `| ... || true`
	if [ "$MOD_ONLY" = 0 ]; then
	set +e
	flock "$HEAVY_LOCK" nix develop "$ROOT" -c ./gradlew --console=plain :mod:test -Dfb.perf=1 --tests '*PerfPlannerTest*' --rerun-tasks > "$LOGDIR/planner.log" 2>&1
	rc=$?
	set -e
	grep -E "PERF |FAILED" "$LOGDIR/planner.log" || true
	if [ "$rc" != 0 ]; then echo "PERF FAIL: the planner run exited $rc (see $LOGDIR/planner.log)" >&2; tail -30 "$LOGDIR/planner.log" >&2; exit 1; fi
	fi
	if [ "$PLANNER_ONLY" = 0 ] && [ "$CLIENT" = 1 ]; then
		# scripts/client-test.sh takes the heavy lock itself and watches for hangs and crashes
		set +e
		FB_PERF=only FB_PERF_OUT="$LOGDIR" "$ROOT/scripts/client-test.sh" > "$LOGDIR/world.log" 2>&1
		rc=$?
		set -e
		grep -E "\[PERF\]|FAILED|BUILD" "$LOGDIR/world.log" || true
		if [ "$rc" != 0 ]; then echo "PERF FAIL: the client run exited $rc (see $LOGDIR/world.log)" >&2; tail -30 "$LOGDIR/world.log" >&2; exit 1; fi
	elif [ "$PLANNER_ONLY" = 0 ]; then
		# both worlds under one hold of the heavy lock (fd 9; the children do not inherit it, so a daemon cannot keep it)
		mkdir -p "$SRV"
		exec 9>"$HEAVY_LOCK"
		flock 9
		VARIANTS="mod base"; [ "$MOD_ONLY" = 1 ] && VARIANTS=mod && export FB_LANE_LOG=1 # per-start [LANE-START] lines, diagnostics only
		for v in $VARIANTS; do
			rm -rf "$SRV/world" "$SRV/logs"
			cat > "$SRV/server.properties" <<P
online-mode=false
server-ip=127.0.0.1
server-port=25598
level-seed=987654321
level-name=world
white-list=false
enable-rcon=false
enable-query=false
enable-status=false
view-distance=4
simulation-distance=12
spawn-protection=0
max-tick-time=-1
P
			set +e
			timeout "${FB_PERF_TIMEOUT:-3600}" nix develop "$ROOT" -c ./gradlew --console=plain --no-daemon :mod:runPerfServer -Pfb.perfVariant=$v -Pfb.perfOut="$LOGDIR" 9>&- > "$LOGDIR/world-$v.log" 2>&1
			rc=$?
			set -e
			grep -E "\[PERF\]|FAILED|BUILD" "$LOGDIR/world-$v.log" || true
			if [ "$rc" != 0 ] || [ ! -f "$LOGDIR/world-$v.json" ]; then echo "PERF FAIL: the $v server run exited $rc or wrote no rows (see $LOGDIR/world-$v.log)" >&2; tail -30 "$LOGDIR/world-$v.log" >&2; exit 1; fi
		done
		exec 9>&-
		if [ "$MOD_ONLY" = 1 ]; then echo "perf: --world-mod-only: diagnostic run, no merge and no budget check (see $LOGDIR/world-mod.log)" >&2; exit 0; fi
		nix develop "$ROOT" -c node scripts/perf-merge.mjs "$LOGDIR"
	fi
fi
ALLOW=""
if [ "$PLANNER_ONLY" = 1 ] || [ "$CHECK_ONLY" = 1 ]; then ALLOW="--allow-absent"; fi
exec nix develop "$ROOT" -c node scripts/perf-check.mjs mod/perf-budget.json mod/build/perf $ALLOW
