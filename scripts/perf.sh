#!/usr/bin/env bash
# Performance budget (ROADMAP 6.7, plan B8): measure on live vanilla terrain and check mod/perf-budget.json.
#
#   scripts/perf.sh [--planner-only] [--check-only]   (both skip rows that were not measured; a full run fails on any absent row)
#
# Takes the heavy lock itself (timings); don't wrap it in flock. Steps:
#   1. mod:test PerfPlannerTest (no server): cold plan, region layer, multi-player cache pressure, veto query -> mod/build/perf/planner.json
#   2. mod:runClientGameTest with FB_PERF=only (PerfClientGameTest, other client tests skip): /locate, elytra flight, cold and warmed
#      chunks vs a no-settlement world, region growth, heap, lane counters -> mod/build/perf/world.json
#   3. scripts/perf-check.mjs compares both with the budget (fails above the CI ceiling, warns above the budget) and prints a table;
#      also writes mod/build/perf/summary.md.
# Quiet-machine rule: numbers that decide a budget need loadavg under 2; the check prints the load measured.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_ROOT="$(dirname "$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir)")"
HEAVY_LOCK="$MAIN_ROOT/.claude/heavy.lock"
mkdir -p "$(dirname "$HEAVY_LOCK")" "$ROOT/mod/build/perf"
cd "$ROOT"
PLANNER_ONLY=0; CHECK_ONLY=0
for a in "$@"; do case "$a" in --planner-only) PLANNER_ONLY=1 ;; --check-only) CHECK_ONLY=1 ;; *) echo "unknown $a" >&2; exit 2 ;; esac; done
LOGDIR="$ROOT/mod/build/perf"
if [ "$CHECK_ONLY" = 0 ]; then
	rm -f mod/build/perf/*.json
	echo "perf: waiting for heavy lock ..." >&2
	# a failing gradle run (build error, test exception, crashed client) must fail the script: no `| ... || true`
	set +e
	flock "$HEAVY_LOCK" nix develop "$ROOT" -c ./gradlew --console=plain :mod:test -Dfb.perf=1 --tests '*PerfPlannerTest*' --rerun-tasks > "$LOGDIR/planner.log" 2>&1
	rc=$?
	set -e
	grep -E "PERF |FAILED" "$LOGDIR/planner.log" || true
	if [ "$rc" != 0 ]; then echo "PERF FAIL: the planner run exited $rc (see $LOGDIR/planner.log)" >&2; tail -30 "$LOGDIR/planner.log" >&2; exit 1; fi
	if [ "$PLANNER_ONLY" = 0 ]; then
		# scripts/client-test.sh takes the heavy lock itself and watches for hangs and crashes
		set +e
		FB_PERF=only FB_PERF_OUT="$LOGDIR" "$ROOT/scripts/client-test.sh" > "$LOGDIR/world.log" 2>&1
		rc=$?
		set -e
		grep -E "\[PERF\]|FAILED|BUILD" "$LOGDIR/world.log" || true
		if [ "$rc" != 0 ]; then echo "PERF FAIL: the client run exited $rc (see $LOGDIR/world.log)" >&2; tail -30 "$LOGDIR/world.log" >&2; exit 1; fi
	fi
fi
ALLOW=""
if [ "$PLANNER_ONLY" = 1 ] || [ "$CHECK_ONLY" = 1 ]; then ALLOW="--allow-absent"; fi
exec nix develop "$ROOT" -c node scripts/perf-check.mjs mod/perf-budget.json mod/build/perf $ALLOW
