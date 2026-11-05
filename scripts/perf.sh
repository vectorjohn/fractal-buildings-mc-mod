#!/usr/bin/env bash
# Performance budget (ROADMAP 6.7, plan B8): measure on live vanilla terrain and check mod/perf-budget.json.
#
#   scripts/perf.sh [--planner-only] [--check-only] [--client]   (the first two skip rows that were not measured; a full run fails on any absent row)
#   --world-mod-only: diagnostic, only the mod world's server run (no planner run, no base world, no merge, no check); logs every start decision ([LANE-START])
#   --world-only: both server runs (mod, base) and the merge, no planner run; the budget check allows absent rows (scripts/profile.sh uses it)
#   FB_PERF_VARIANTS="base mod": the world runs in that order (default "mod base"; PROFILE: an order-effect check)
#   FB_PERF_MODES=arrival (COLDTICK, diagnostic): only that pass (main, arrival or both); FB_PERF_ARRIVAL_RELEASE=plan (arrival pass) releases the teleport hold into chunks that are not FULL
#
# Takes the heavy lock itself (timings); don't wrap it in flock. Steps:
#   1. mod:test PerfPlannerTest (no server): cold plan, region layer, multi-player cache pressure, veto query -> mod/build/perf/planner.json
#   2. the world half, on a DEDICATED SERVER with no client and no renderer (PERFSRV; timings no longer share cores with lavapipe):
#      the mod world (:mod:runPerfServer, PerfServerHarness) then the VANILLA world (PERFBASE: tools/vanilla-server runPerfBase, VanillaPerfHarness,
#      Fabric Loader + Fabric API and the harness only, no fractalbuildings; it was the mod with settlement frequency 0 before): /locate, a mock
#      player's elytra-speed flight, cold and warmed chunks vs vanilla, region growth, heap, lane counters) -> world-mod.json, world-base.json, merged by
#      scripts/perf-merge.mjs into mod/build/perf/world.json. The run dir mod/build/run/perfServer needs eula.txt with eula=true; the script
#      writes it when missing (CLAUDE.md standing EULA approval: a local test server, 127.0.0.1, online-mode=false, set below).
#      --client runs the OLD form instead: mod:runClientGameTest with FB_PERF=only (PerfClientGameTest, in a lavapipe-rendered client),
#      kept to compare old and new numbers.
#   3. scripts/perf-check.mjs compares both with the budget (fails above the CI ceiling, warns above the budget) and prints a table;
#      a timing row skipped for load prints a `PERF SKIPPED (load)` line, and a `loadSkipGuard` row (first tile, region cold,
#      /locate fresh) skipped far over its ceiling exits 5 (`PERF SKIPPED ROW FAR OVER CEILING`); self-test node scripts/perf-check.test.mjs;
#      a row over its ceiling with `knownOver` (a bug the user accepted for 0.1.0; ceilings are never raised) prints `PERF KNOWN OVER` and
#      does not fail unless 25 % worse than its recorded value; also writes mod/build/perf/summary.md.
# Quiet-machine rule (CLAUDE.md): a timing from a busy host is not a measurement. scripts/quiet-check.sh judges the host when the
# heavy lock is taken and after each step (no other job or agent work on the host: locks, other jobs of the worker it runs in, other processes' CPU;
# loadavg recorded, not judged) and WATCHES THE WHOLE RUN (QUIETHOLE): --window-start at the first check samples locks and other
# processes' CPU every FB_QUIET_SAMPLE s and records the worker's job list; --window-end before the verdict fails the window if any
# other worker job ran, started or finished in between, or any sample was busy. All of it goes to mod/build/perf/quiet.txt;
# the run ends with "perf: quiet host" or "perf: NOT A MEASUREMENT (busy host: <what overlapped>)" (quiet-check.sh --verdict).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# A copy without .git (scripts/remote.sh on the worker) uses its own root.
if GIT_COMMON="$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)"; then
	MAIN_ROOT="$(dirname "$GIT_COMMON")"
else
	MAIN_ROOT="$ROOT"
fi
HEAVY_LOCK="$MAIN_ROOT/.claude/heavy.lock"
# shellcheck source=lib/locks.sh
. "$ROOT/scripts/lib/locks.sh"   # holder note, waiter lines, self-deadlock refusal
# Process registry (W8/W11, scripts/lib/procs.sh): each Gradle run gets its own process group (job control,
# so it stays in this session and scripts/quiet-check.sh still counts it as ours), registered with this
# script as owner: scripts/watchdog.sh reports it and scripts/reap.sh can stop it if this script dies.
# The EXIT trap stops a run still going when this script ends (error, Ctrl-C, TERM).
# shellcheck source=lib/procs.sh
. "$ROOT/scripts/lib/procs.sh"
RUN_PG=""
stop_run() {
	[ -n "$RUN_PG" ] || return 0
	if kill -0 -- "-$RUN_PG" 2>/dev/null; then
		echo "perf: stopping process group $RUN_PG (TERM)" >&2
		kill -TERM -- "-$RUN_PG" 2>/dev/null
		for _ in 1 2 3 4 5 6 7 8 9 10; do kill -0 -- "-$RUN_PG" 2>/dev/null || break; sleep 1; done
		kill -KILL -- "-$RUN_PG" 2>/dev/null || true
	fi
	fb_proc_unregister "pg-$RUN_PG"; RUN_PG=""
}
trap stop_run EXIT
trap 'exit 130' INT
trap 'exit 143' TERM HUP
run_reg() { # LABEL BUDGET_S OUTFILE CMD...: CMD in its own registered process group, output to OUTFILE; returns CMD's exit
	local label=$1 budget=$2 out=$3 pid rc
	shift 3
	set -m; "$@" 9>&- < /dev/null > "$out" 2>&1 & pid=$!; set +m
	if [ "$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ')" = "$pid" ]; then   # never register a group we can't prove is ours
		RUN_PG=$pid
		fb_proc_register "pg-$pid" kind=pgroup "pgid=$pid" "owner=$$" "label=perf.sh $label" "budget=$budget" "log=$out" \
			"worktree=$ROOT" "cmd=$(printf '%s ' "$@" | cut -c1-200)"
	fi
	wait "$pid"; rc=$?
	[ -n "$RUN_PG" ] && fb_proc_unregister "pg-$RUN_PG"
	RUN_PG=""
	return "$rc"
}
mkdir -p "$(dirname "$HEAVY_LOCK")" "$ROOT/mod/build/perf"
cd "$ROOT"
PLANNER_ONLY=0; CHECK_ONLY=0; CLIENT=0; MOD_ONLY=0; WORLD_ONLY=0
for a in "$@"; do case "$a" in --planner-only) PLANNER_ONLY=1 ;; --check-only) CHECK_ONLY=1 ;; --client) CLIENT=1 ;; --world-mod-only) MOD_ONLY=1 ;; --world-only) WORLD_ONLY=1 ;; *) echo "unknown $a" >&2; exit 2 ;; esac; done
LOGDIR="$ROOT/mod/build/perf"
SRV="$ROOT/mod/build/run/perfServer"
SRVBASE="$ROOT/mod/build/run/perfServerBase"
if [ "$CHECK_ONLY" = 0 ]; then
	if [ "$PLANNER_ONLY" = 0 ] && [ "$CLIENT" = 0 ] && ! grep -qx 'eula=true' "$SRV/eula.txt" 2>/dev/null; then
		# standing EULA approval (CLAUDE.md): local test server bound to 127.0.0.1 with online-mode=false (server.properties below)
		mkdir -p "$SRV" && echo eula=true > "$SRV/eula.txt"
		echo "perf: wrote $SRV/eula.txt (standing EULA approval, local test server)" >&2
	fi
	rm -f mod/build/perf/*.json
	# Quiet-machine rule (CLAUDE.md "Parallel work"): judged once the heavy lock is ours (the first line, "start"); later lines
	# record load and other holders (that load includes this run's own work)
	: > "$LOGDIR/quiet.txt"
	# the first check also starts the window watcher (QUIETHOLE); it stops with this script or at --window-end below
	QWIN="$LOGDIR/quiet-window"; rm -rf "$QWIN"
	qc() {
		local l=$1
		if [ ! -s "$LOGDIR/quiet.txt" ]; then l=start; "$ROOT/scripts/quiet-check.sh" --window-start "$QWIN" >> "$LOGDIR/quiet.txt" || true; fi
		"$ROOT/scripts/quiet-check.sh" --own-heavy --label "$l" >> "$LOGDIR/quiet.txt" || true
	}
	echo "perf: waiting for heavy lock ..." >&2
	# a failing gradle run (build error, test exception, crashed client) must fail the script: no `| ... || true`
	if [ "$MOD_ONLY" = 0 ] && [ "$WORLD_ONLY" = 0 ]; then
	set +e
	exec 9>>"$HEAVY_LOCK"
	fb_lock_take 9 "$HEAVY_LOCK" "perf.sh planner" || exit $?
	qc start
	run_reg planner 3600 "$LOGDIR/planner.log" "$ROOT/scripts/dev" --fresh ./gradlew --console=plain --no-daemon :mod:test -Dfb.perf=1 --tests '*PerfPlannerTest*' --rerun-tasks
	rc=$?
	set -e
	grep -E "PERF |FAILED" "$LOGDIR/planner.log" || true
	if [ "$rc" != 0 ]; then echo "PERF FAIL: the planner run exited $rc (see $LOGDIR/planner.log)" >&2; tail -30 "$LOGDIR/planner.log" >&2; exit 1; fi
	qc after-planner
	fb_lock_drop 9 "$HEAVY_LOCK"; exec 9>&-
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
		exec 9>>"$HEAVY_LOCK"
		fb_lock_take 9 "$HEAVY_LOCK" "perf.sh world" || exit $?
		qc world-start
		VARIANTS="${FB_PERF_VARIANTS:-mod base}"; [ "$MOD_ONLY" = 1 ] && VARIANTS=mod && export FB_LANE_LOG=1 # per-start [LANE-START] lines, diagnostics only
		MODES="main"; [ "${FB_PERF_ARRIVAL:-1}" = 1 ] && [ "$MOD_ONLY" = 0 ] && MODES="main arrival" # COLDARRIVAL: a second pair of fresh worlds, so the metropolis is cold
		[ -n "${FB_PERF_MODES:-}" ] && MODES="$FB_PERF_MODES" # COLDTICK diagnostic: e.g. FB_PERF_MODES=arrival runs only the arrival pass (the summary then lacks rows: not a measured run)
		for mode in $MODES; do
		OUTN="world"; [ "$mode" = arrival ] && OUTN="world-arrival"
		for v in $VARIANTS; do
			VSRV="$SRV"; [ "$v" = base ] && VSRV="$SRVBASE" # PERFBASE: the base world is the vanilla server of tools/vanilla-server (no fractalbuildings at all)
			mkdir -p "$VSRV"; echo eula=true > "$VSRV/eula.txt"
			rm -rf "$VSRV/world" "$VSRV/logs"; rm -f "$LOGDIR/$OUTN-$v.json"
			cat > "$VSRV/server.properties" <<P
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
			if [ "$v" = base ]; then
				run_reg "world $mode $v" $(( ${FB_PERF_TIMEOUT:-3600} + 300 )) "$LOGDIR/$OUTN-$v.log" timeout "${FB_PERF_TIMEOUT:-3600}" "$ROOT/scripts/dev" --fresh ./gradlew --console=plain --no-daemon -p tools/vanilla-server runPerfBase -Pfb.perfMode=$mode -Pfb.perfOut="$LOGDIR"
			else
				run_reg "world $mode $v" $(( ${FB_PERF_TIMEOUT:-3600} + 300 )) "$LOGDIR/$OUTN-$v.log" timeout "${FB_PERF_TIMEOUT:-3600}" "$ROOT/scripts/dev" --fresh ./gradlew --console=plain --no-daemon :mod:runPerfServer -Pfb.perfVariant=$v -Pfb.perfMode=$mode -Pfb.perfOut="$LOGDIR" ${FB_PERF_QUAYTRACE:+-Pfb.quayTrace=$FB_PERF_QUAYTRACE} ${FB_PERF_SAMPLE:+-Pfb.perfSample=$FB_PERF_SAMPLE}
			fi
			rc=$?
			set -e
			grep -E "\[PERF\]|FAILED|BUILD" "$LOGDIR/$OUTN-$v.log" || true
			# PERFBASE: the vanilla run must contain nothing of ours (checked on its log), or it is not a vanilla baseline
			if [ "$v" = base ] && grep -qi "fractalbuildings\|Fractal Buildings" "$LOGDIR/$OUTN-$v.log" "$VSRV/logs/latest.log" 2>/dev/null; then echo "PERF FAIL: the base run mentions fractalbuildings: not a vanilla baseline" >&2; exit 1; fi
			if [ "$rc" != 0 ] || [ ! -f "$LOGDIR/$OUTN-$v.json" ]; then echo "PERF FAIL: the $mode $v server run exited $rc or wrote no rows (see $LOGDIR/$OUTN-$v.log)" >&2; tail -30 "$LOGDIR/$OUTN-$v.log" >&2; exit 1; fi
		done
		done
		qc world-end
		exec 9>&-
		if [ "$MOD_ONLY" = 1 ]; then echo "perf: --world-mod-only: diagnostic run, no merge and no budget check (see $LOGDIR/world-mod.log)" >&2; exit 0; fi
		"$ROOT/scripts/dev" --fresh node scripts/perf-merge.mjs "$LOGDIR"
	fi
fi
if [ "$CHECK_ONLY" = 0 ] && [ -f "$LOGDIR/quiet.txt" ]; then
	# the whole run: any other worker job or busy sample between the first check and now (QUIETHOLE)
	[ -f "$QWIN/start" ] && { "$ROOT/scripts/quiet-check.sh" --window-end "$QWIN" >> "$LOGDIR/quiet.txt" || true; }
	cat "$LOGDIR/quiet.txt"
	# other job or agent work on the host at any step or in between (not our own load): not a measurement
	"$ROOT/scripts/quiet-check.sh" --verdict "$LOGDIR/quiet.txt" >&2 || true
fi
ALLOW=""
if [ "$PLANNER_ONLY" = 1 ] || [ "$CHECK_ONLY" = 1 ] || [ "$WORLD_ONLY" = 1 ]; then ALLOW="--allow-absent"
elif [ "${FB_PERF_ARRIVAL:-1}" != 1 ]; then ALLOW="--allow-absent-prefix arrival."; fi # COLDARRIVAL pair not run: only its rows may be missing
exec "$ROOT/scripts/dev" --fresh node scripts/perf-check.mjs mod/perf-budget.json mod/build/perf $ALLOW
