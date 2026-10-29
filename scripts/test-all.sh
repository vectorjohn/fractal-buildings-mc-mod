#!/usr/bin/env bash
# Run every test layer (ROADMAP "Testing strategy") and print a short summary.
#
#   scripts/test-all.sh [--no-client] [--full] [--planner-heavy] [--perf] [--help]
#
#   --no-client   skip layer 5 (Minecraft client game tests under Xvfb)
#   --full        the full client layer (FB_LAYER=full: adds Roads, Foliage, Compat) and the district-order test on dedicated servers
#                 (scripts/order-test.sh, not heavy; needs mod/build/run/order/eula.txt, see its header). Default: FB_LAYER=quick, no order test
#   --planner-heavy  also run the tests tagged @Tag("heavy"): planner-core (-PfbTests=heavy, about 80 min; TESTSPEED) and the mod's (OrderSimMutantTest, ~1 min). Not part of --full: the to-main run adds it
#   --perf        also run scripts/perf.sh (the 6.7 budget check, ~10 min, heavy lock; fails above the CI ceilings of mod/perf-budget.json)
#
# Works from any cwd and any worktree; re-execs itself inside the dev shell (scripts/dev) when
# not already in the dev shell. Each step's output goes to build/test-all/<step>.log
# (gitignored); the terminal only shows progress and the summary. Exit status is
# non-zero if any step fails.
#
# Steps are detected, not assumed: a Gradle task or npm script that does not exist
# yet is reported as "skipped: not present yet", so the script picks up new layers
# (CLI determinism, viewer screenshots, game tests) as they land.
#
# The client step takes the heavy lock (<main repo root>/.claude/heavy.lock) itself
# and waits for it. Don't run this script under that lock yourself: it would wait
# on its own lock forever.
#
# The fast layers (everything before the client layer) run under one of the machine-wide
# suite slots (scripts/suite.sh; FB_SUITE_SLOTS, default 2), with a "waiting for suite
# slot" line when all are busy; the slot is released before the client and perf layers.
set -uo pipefail

# The header: every comment line after the shebang, up to the first non-comment line.
usage() { awk 'NR == 1 { next } !/^#/ { exit } { sub(/^# ?/, ""); print }' "$0"; }

RUN_CLIENT=1
RUN_PERF=0
FULL=0
PLANNER_HEAVY=0
for arg in "$@"; do
	case "$arg" in
		--no-client) RUN_CLIENT=0 ;;
		--perf) RUN_PERF=1 ;;
		--full) FULL=1 ;;
		--planner-heavy) PLANNER_HEAVY=1 ;;
		-h|--help) usage; exit 0 ;;
		*) echo "test-all: unknown argument: $arg" >&2; usage >&2; exit 2 ;;
	esac
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "${IN_NIX_SHELL:-}" ]]; then
	exec "$ROOT/scripts/dev" "$ROOT/scripts/test-all.sh" "$@"
fi

cd "$ROOT" || exit 2
# The heavy lock lives in the main checkout's .claude/, shared by all worktrees.
# A copy without .git (scripts/remote.sh on the worker) uses its own root.
if GIT_COMMON="$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)"; then
	MAIN_ROOT="$(dirname "$GIT_COMMON")"
else
	MAIN_ROOT="$ROOT"
fi
HEAVY_LOCK="$MAIN_ROOT/.claude/heavy.lock"

OUT="$ROOT/build/test-all"
rm -rf "$OUT"
mkdir -p "$OUT"
START_MARK="$OUT/.start"
touch "$START_MARK"
export NO_COLOR=1 FORCE_COLOR=0 CI=1
GRADLE=(./gradlew --console=plain)

# Results, one entry per step, in order.
STEPS=() ; declare -A STATUS DETAIL LOGS
FAILED=0

say() { printf '%s\n' "$*"; }
record() { # step status detail
	STEPS+=("$1"); STATUS[$1]="$2"; DETAIL[$1]="$3"
	[[ "$2" == fail ]] && FAILED=1
	printf '  %-18s %s%s\n' "$1" "$2" "${3:+  ($3)}"
}
skip() { record "$1" skipped "${2:-not present yet}"; }

# Run a command with output to the step's log. Returns the command's status.
run_logged() { # step cmd...
	local step=$1; shift
	local log="$OUT/$step.log"
	LOGS[$step]="$log"
	printf '%s\n' "\$ $*" > "$log"
	# Children don't inherit the suite-slot fd: only this script holds the slot, so a
	# daemon or server a step leaves behind can't keep it after the script ends.
	if [[ -n "${SUITE_SLOT_FD:-}" ]]; then
		"$@" >> "$log" 2>&1 {SUITE_SLOT_FD}>&-
	else
		"$@" >> "$log" 2>&1
	fi
}

# --- JUnit XML counts -------------------------------------------------------
# Sums tests/failures/errors/skipped over JUnit XML reports under the given paths
# (Gradle replaces a test task's reports when it reruns, so they are current even
# when the task was up to date). Prints "tests failures errors skipped".
junit_counts() {
	local files
	files=$(find "$@" -name 'TEST-*.xml' 2>/dev/null)
	[[ -z "$files" ]] && { echo "0 0 0 0"; return; }
	# shellcheck disable=SC2086
	grep -ho '<testsuite [^>]*>' $files | awk '
		function attr(s, n,   r) { if (match(s, n "=\"[0-9]+\"")) { r = substr(s, RSTART, RLENGTH); gsub(/[^0-9]/, "", r); return r + 0 } return 0 }
		{ t += attr($0, "tests"); f += attr($0, " failures"); e += attr($0, " errors"); s += attr($0, " skipped") }
		END { print t + 0, f + 0, e + 0, s + 0 }'
}
fmt_counts() { # tests failures errors skipped
	local t=$1 f=$2 e=$3 s=$4
	[[ $t -eq 0 ]] && { echo "no JUnit tests"; return; }
	echo "$((t - f - e - s))/$t passed$( ((f + e)) && echo ", $((f + e)) failed")$( ((s)) && echo ", $s skipped")"
}

# Copies of a module's JUnit XML, saved after each run: $JUNIT_SAVED/<module>/<run>/.
# The --planner-heavy run replaces planner-core/build/test-results, so the live dir
# then holds only the heavy results and would hide a failure in the default run.
JUNIT_SAVED="$OUT/junit"
save_junit() { # module run
	rm -rf "${JUNIT_SAVED:?}/$1/$2"
	mkdir -p "$JUNIT_SAVED/$1/$2"
	if [[ -d "$ROOT/$1/build/test-results" ]]; then
		cp -r "$ROOT/$1/build/test-results/." "$JUNIT_SAVED/$1/$2/"
	fi
}

# Test cases in JUnit classes whose name mentions determinism, in any module.
# A module with saved copies (planner-core: default and heavy runs) is counted from
# them instead of its live build/test-results.
determinism_counts() {
	local files d m dirs=()
	for d in "$ROOT"/*/build/test-results; do
		m=$(basename "$(dirname "$(dirname "$d")")")
		[[ -d "$JUNIT_SAVED/$m" ]] || dirs+=("$d")
	done
	for d in "$JUNIT_SAVED"/*; do
		[[ -d "$d" ]] && dirs+=("$d")
	done
	(( ${#dirs[@]} )) || { echo "0 0 0 0"; return; }
	files=$(find "${dirs[@]}" -name 'TEST-*eterminis*.xml' 2>/dev/null)
	[[ -z "$files" ]] && { echo "0 0 0 0"; return; }
	# shellcheck disable=SC2086
	junit_counts $files
}

# --- Suite slot for the fast layers (released before the heavy ones) ----------
# shellcheck source=lib/suite-slot.sh
. "$ROOT/scripts/lib/suite-slot.sh"
suite_slot_acquire "test-all $ROOT" || { say "test-all: no suite slot"; exit 1; }
[[ -n "$SUITE_SLOT_FD" ]] && say "  suite slot: $SUITE_SLOT_FILE"

# --- Gradle task detection --------------------------------------------------
say "test-all: $ROOT"
say "  logs in $OUT"
TASKS_FILE="$OUT/gradle-tasks.txt"
if ! "${GRADLE[@]}" -q tasks --all > "$TASKS_FILE" 2>"$OUT/gradle-tasks.err"; then
	say "test-all: './gradlew tasks --all' failed; see $OUT/gradle-tasks.err"
	cp "$OUT/gradle-tasks.err" "$OUT/gradle-tasks.log"
	exit 1
fi
has_task() { grep -qE "^$1( |$)" "$TASKS_FILE"; }
# First task (fully qualified) matching an extended regex on the task name, if any.
find_task() { grep -oE "^$1( |$)" "$TASKS_FILE" | head -n1 | tr -d ' '; }

say ""
say "Running:"

# --- Layer 1 (+ boundary check): planner-core --------------------------------
if has_task 'planner-core:build'; then
	if run_logged planner-core "${GRADLE[@]}" :planner-core:build; then st=pass; else st=fail; fi
	record planner-core $st "$(fmt_counts $(junit_counts "$ROOT/planner-core/build/test-results")); boundary check incl."
	save_junit planner-core default
else
	skip planner-core
fi

# --- Layer 1b (--planner-heavy): the tests tagged @Tag("heavy") (TESTSPEED), excluded from the default run above
if (( PLANNER_HEAVY )) && has_task 'planner-core:build'; then
	if run_logged planner-heavy "${GRADLE[@]}" :planner-core:test -PfbTests=heavy; then st=pass; else st=fail; fi
	record planner-heavy $st "$(fmt_counts $(junit_counts "$ROOT/planner-core/build/test-results")); @Tag(heavy) tests"
	save_junit planner-core heavy
fi

# The mod's @Tag("heavy") tests (ORDERSIM S7: OrderSimMutantTest, 30 to 42 s) run with the planner-heavy ones.
if (( PLANNER_HEAVY )) && has_task 'mod:build'; then
	if run_logged mod-heavy "${GRADLE[@]}" :mod:test -PfbTests=heavy; then st=pass; else st=fail; fi
	record mod-heavy $st "$(fmt_counts $(junit_counts "$ROOT/mod/build/test-results")); @Tag(heavy) tests (order sim mutants)"
	save_junit mod heavy
fi

# --- CLI -------------------------------------------------------------------
if has_task 'cli:build'; then
	if run_logged cli "${GRADLE[@]}" :cli:build; then st=pass; else st=fail; fi
	record cli $st "$(fmt_counts $(junit_counts "$ROOT/cli/build/test-results"))"
else
	skip cli
fi

# --- Layer 2: determinism ----------------------------------------------------
# Either a dedicated Gradle task (any project, name containing "determinism"), or
# JUnit classes named *Determinism* that ran in the steps above.
det_task=$(find_task '[a-zA-Z-]+:[a-zA-Z]*[Dd]eterminism[a-zA-Z]*')
if [[ -n "$det_task" ]]; then
	if run_logged determinism "${GRADLE[@]}" ":$det_task"; then st=pass; else st=fail; fi
	record determinism $st ":$det_task; $(fmt_counts $(determinism_counts))"
else
	read -r t f e s < <(determinism_counts)
	if (( t > 0 )); then
		if (( f + e > 0 )); then st=fail; else st=pass; fi
		record determinism $st "$(fmt_counts "$t" "$f" "$e" "$s") in *Determinism* test classes (ran above)"
	else
		skip determinism "not present yet: no *Determinism* tests or task"
	fi
fi

# --- Layer 4: mod build + server game tests ----------------------------------
if has_task 'mod:build'; then
	mod_tasks=(:mod:build)
	gt_task=$(find_task 'mod:run[Gg]ame[Tt]est[a-zA-Z]*')
	[[ -n "$gt_task" ]] && mod_tasks+=(":$gt_task")
	if run_logged mod "${GRADLE[@]}" "${mod_tasks[@]}"; then st=pass; else st=fail; fi
	d="$(fmt_counts $(junit_counts "$ROOT/mod/build"))"
	# Vanilla game test runner logs e.g. "All 3 required tests passed :)" or "N required tests failed".
	gt_line=$(grep -oE '(All [0-9]+ required tests? passed|[0-9]+ required tests? failed)' "${LOGS[mod]}" | tail -n1)
	if [[ -n "$gt_task" ]]; then
		d+="; server game tests (:$gt_task): ${gt_line:-no count in log}"
	elif [[ -n "$gt_line" ]]; then
		d+="; server game tests: $gt_line"
	else
		d+="; server game tests: not present yet"
	fi
	record mod $st "$d"
	save_junit mod default
else
	skip mod
fi

# --- Design block reference up to date (cheap; after the mod build, which caches the jars) ---
# scripts/design-blocks.sh --check: 0 current, 1 stale (rerun the script and commit), 2 Loom's
# Minecraft jars not cached yet (skipped, not failed: a mod build fetches them).
if [[ -x "$ROOT/scripts/design-blocks.sh" ]]; then
	run_logged design-blocks "$ROOT/scripts/design-blocks.sh" --check; rc=$?
	case $rc in
		0) record design-blocks pass "docs/design/blocks.md and agent tables current" ;;
		2) skip design-blocks "Loom's Minecraft jars not cached; see ${LOGS[design-blocks]}" ;;
		*) record design-blocks fail "stale or broken (exit $rc): run scripts/design-blocks.sh and commit; see ${LOGS[design-blocks]}" ;;
	esac
else
	skip design-blocks
fi

# --- Viewer: unit tests (layer 1 for the viewer) ------------------------------
VIEWER="$ROOT/viewer"
npm_scripts=""
if [[ -f "$VIEWER/package.json" ]]; then
	# Script names from package.json "scripts" (npm run --json prints the object).
	npm_scripts=$(cd "$VIEWER" && npm run --json 2>/dev/null | grep -oE '^  "[^"]+"' | tr -d ' "')
	ensure_node_modules() {
		if [[ ! -f "$VIEWER/node_modules/.package-lock.json" || "$VIEWER/package-lock.json" -nt "$VIEWER/node_modules/.package-lock.json" ]]; then
			run_logged viewer-npm-ci bash -c "cd '$VIEWER' && npm ci"
		fi
	}
	if grep -qx test <<< "$npm_scripts"; then
		if ! ensure_node_modules; then
			record viewer fail "npm ci failed; see ${LOGS[viewer-npm-ci]}"
		else
			if run_logged viewer bash -c "cd '$VIEWER' && npm test"; then st=pass; else st=fail; fi
			# Vitest summary line, e.g. "Tests  1 failed | 3 passed (4)".
			vt=$(sed 's/\x1b\[[0-9;]*m//g' "${LOGS[viewer]}" | grep -E '^ *Tests +' | tail -n1 | sed -E 's/^ *Tests +//; s/ +/ /g')
			record viewer $st "${vt:-no Vitest summary in log}"
		fi
	else
		skip viewer "no 'test' npm script"
	fi
else
	skip viewer "no viewer/package.json"
fi

# --- Layer 3: viewer screenshots (Playwright) ----------------------------------
shot_script=$(grep -xE '(screenshot|screenshots|shot|smoke|e2e|playwright|gallery)(:[a-z0-9-]+)?' <<< "$npm_scripts" | head -n1)
if [[ -n "$shot_script" ]]; then
	if ! ensure_node_modules; then
		record viewer-shots fail "npm ci failed"
	else
		if run_logged viewer-shots bash -c "cd '$VIEWER' && npm run '$shot_script'"; then st=pass; else st=fail; fi
		n=$(find "$VIEWER" -path "$VIEWER/node_modules" -prune -o -name '*.png' -newer "$START_MARK" -print | wc -l)
		record viewer-shots $st "npm run $shot_script; $n new PNG(s)"
	fi
else
	skip viewer-shots "not present yet: no screenshot/e2e/gallery npm script"
fi

# --- Layer 4b (--full): the district-order test (B2) on dedicated servers, no renderer, not under the heavy lock
if (( FULL )); then
	if run_logged order "$ROOT/scripts/order-test.sh"; then st=pass; else st=fail; fi
	record order $st "$(grep -E "^order-test: [0-9]+ of|^order-test: ORDER TEST DID NOT COMPLETE|^order-test: no eula" "${LOGS[order]}" | tail -n1 | cut -c1-200)"
fi

# --- Layer 5: client game tests (heavy lock) ----------------------------------
suite_slot_release   # fast layers done; heavy layers queue on the heavy lock instead
if (( ! RUN_CLIENT )); then
	skip client "--no-client"
elif ! has_task 'mod:runClientGameTest'; then
	skip client "not present yet: no :mod:runClientGameTest"
elif { [[ ${FB_USE_DISPLAY:-} != 1 ]] && ! command -v xvfb-run > /dev/null; } || ! command -v flock > /dev/null; then
	record client fail "xvfb-run or flock not on PATH"
else
	mkdir -p "$(dirname "$HEAVY_LOCK")"
	say "  client: waiting for heavy lock $HEAVY_LOCK ..."
	# scripts/client-test.sh takes the heavy lock, hides WAYLAND_DISPLAY (else SDL picks
	# Wayland over Xvfb), and kills a client left hanging after a crash or past
	# FB_CLIENT_TIMEOUT, so a failed run can't hold the lock.
	export FB_LAYER="$( ((FULL)) && echo full || echo "${FB_LAYER:-quick}" )"
	if run_logged client "$ROOT/scripts/client-test.sh"; then st=pass; else st=fail; fi
	gt_line=$(grep -oE '(All [0-9]+ required tests? passed|[0-9]+ required tests? failed|[0-9]+ client game tests? [a-z]+)' "${LOGS[client]}" | tail -n1)
	record client $st "layer $FB_LAYER; ${gt_line:-no count in log}; /proc/loadavg at end: $(cut -d' ' -f1-3 /proc/loadavg)"
fi

# --- Perf budget (opt-in; heavy lock inside scripts/perf.sh) -------------------
if (( RUN_PERF )); then
	if run_logged perf "$ROOT/scripts/perf.sh"; then st=pass; else st=fail; fi
	record perf $st "$(grep -E "^perf ok|^PERF FAIL" "${LOGS[perf]}" | tail -n1); table: mod/build/perf/summary.md"
fi

# --- Summary --------------------------------------------------------------------
say ""
say "=== test-all summary ==="
declare -A LAYER=([planner-core]="L1" [planner-heavy]="L1" [mod-heavy]="L4" [cli]="L1" [determinism]="L2" [mod]="L4" [viewer]="L1" [viewer-shots]="L3" [client]="L5" [order]="L4" [perf]="L5")
for s in "${STEPS[@]}"; do
	printf '%-3s %-13s %-8s %s\n' "${LAYER[$s]:-}" "$s" "${STATUS[$s]}" "${DETAIL[$s]}"
done

say ""
say "Screenshots (new this run):"
shots=$(find "$ROOT" \( -path "$ROOT/.git" -o -path '*/node_modules' -o -path "$OUT" \) -prune -o \
	-name '*.png' -newer "$START_MARK" -print 2>/dev/null | sort)
if [[ -n "$shots" ]]; then sed 's/^/  /' <<< "$shots"; else say "  none"; fi

say ""
say "Logs:"
for s in "${!LOGS[@]}"; do printf '  %s\n' "${LOGS[$s]}"; done | sort
for f in "$ROOT/mod/run/logs/latest.log" "$ROOT/mod/build/run/clientGameTest/logs/latest.log"; do
	[[ -f "$f" && "$f" -nt "$START_MARK" ]] && say "  $f"
done

say ""
if (( FAILED )); then
	say "test-all: FAILED"
	exit 1
fi
say "test-all: OK"
