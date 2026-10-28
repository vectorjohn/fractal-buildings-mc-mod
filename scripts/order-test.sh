#!/usr/bin/env bash
# The district-order test (B2, ROADMAP 6.1 / 6.2 evidence) on renderer-free dedicated servers (TESTLAYERS).
#
#   scripts/order-test.sh [--jobs N] [--only label,label,...] [--radius R]
#
# It was a client game test (16 fresh worlds, ~20 min, no screenshots). Now one dedicated-server JVM and one fresh world per run label
# (OrderServerHarness, loaded by the mod's -Dfb.testHook; :mod:runOrderServer), up to N at a time (default: from memory, below), NOT under the heavy lock
# (no renderer; timings are not measured). Same assertions as before: all seven chunk orders (forward, reverse, shuffle1, shuffle2,
# spiral, threads, threads_evict) give the same ledger digest as forward and equal the core's reference raster, hooks/roads/ledger 0/0/0,
# unknown-spill classification, the saved-config world, and the seven mutants (counter, twice, skip-air, early-step, random, world-stone, lakes)
# each caught by its own check, against the centre-tile baselines (clean-centre, reverse-centre).
#   phase 1 (independent): forward clean-centre config mutant-*     phase 2 (compare with a phase 1 baseline): the other orders, reverse-centre
# Results: build/order-test/<label>.{verdict,report,run.json,log,server.log}; build/order-test/STATUS says RUNNING while it runs, then
# COMPLETE or INCOMPLETE with the counts.
#
# Exit codes: 0 every run PASS; 1 a run gave a FAIL verdict (a real test failure); 2 bad arguments or the build failed; 4 ORDER TEST
# DID NOT COMPLETE: at least one run ended without a verdict (its server was killed, crashed or timed out; on beebox usually the
# per-job memory cap, OOM), so there is no full report. Rerun it (fewer --jobs); don't read it as a test result. When the whole script
# is killed (an OOM kill on beebox stops every process of the job) it prints nothing more: STATUS still says RUNNING, and
# scripts/remote.sh prints the OOM kill itself and exits 137.
#
# Jobs and memory (ORDERMEM): one run is a Gradle client + a single-use Gradle daemon (-Xmx2G, gradle.properties) + the server JVM
# (no -Xmx: the JVM default, 1/4 of the memory it sees, cgroup-aware); FB_ORDER_RUN_MB (default 3000) is the budget per run. Default
# --jobs = min(6, (budget - 1 GiB) / FB_ORDER_RUN_MB), at least 1; the budget is the tightest cgroup v2 memory.max above this process
# (when one is set; the lower of it and MemAvailable), else MemAvailable. An explicit --jobs wins but warns when over the budget. Under
# a cgroup cap the build step runs --no-daemon, so no idle daemon holds memory during the runs. A beebox job (MemoryMax 28 %, 7.9 GiB)
# gets 2. The final line gives the cgroup's memory peak when there is a cap.
#
# EULA: each run dir mod/build/run/order/<label>/ gets a copy of mod/build/run/order/eula.txt (this worktree's, else the main checkout's).
# When neither says eula=true, the script writes eula=true to this worktree's file itself and prints one line saying so (so remote.sh
# copies, which leave ignored files out, work): local test servers in the project's test dirs, 127.0.0.1, online-mode=false, covered by
# the standing EULA approval in CLAUDE.md. Each run checks its server.properties binds 127.0.0.1 with online-mode=false (else that run FAILs, unstarted).
#
# Works from any cwd and worktree; enters the dev shell itself. Env: FB_ORDER_TIMEOUT (seconds per run, default 3000), FB_ORDER_PORT (first port, default 25610).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ -z "${IN_NIX_SHELL:-}" ]; then exec nix develop "$ROOT" -c "$0" "$@"; fi
# no .git in a scripts/remote.sh copy: then the main checkout is this one
MAIN_ROOT="$(dirname "$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || echo "$ROOT/.git")")"
JOBS=""; ONLY=""; RADIUS=1
while [ $# -gt 0 ]; do case "$1" in
	--jobs) JOBS="$2"; shift 2 ;; --only) ONLY="$2"; shift 2 ;; --radius) RADIUS="$2"; shift 2 ;;
	-h | --help) sed -n '2,/^set -euo/p' "$0" | sed '$d'; exit 0 ;; *) echo "order-test: unknown argument $1" >&2; exit 2 ;; esac; done
[ -z "$JOBS" ] || [[ $JOBS =~ ^[1-9][0-9]*$ ]] || { echo "order-test: --jobs must be a positive number" >&2; exit 2; }

# Memory budget (MB): the tightest cgroup v2 memory.max from this process's cgroup up to the root, else MemAvailable (the lower of
# the two when both apply); one run is budgeted FB_ORDER_RUN_MB, with 1 GiB kept back for the script, the build and the rest.
CG_MAX=""; CG_DIR=""
cg="$(sed -n 's/^0:://p' /proc/self/cgroup 2>/dev/null || true)"
if [ -n "$cg" ] && [ "$cg" != / ]; then
	d="/sys/fs/cgroup$cg"
	while [ "$d" != /sys/fs/cgroup ] && [ "$d" != / ]; do
		v="$(cat "$d/memory.max" 2>/dev/null || echo max)"
		if [[ $v =~ ^[0-9]+$ ]] && { [ -z "$CG_MAX" ] || [ "$v" -lt "$CG_MAX" ]; }; then CG_MAX=$v; CG_DIR=$d; fi
		d="$(dirname "$d")"
	done
fi
AVAIL_MB=$(awk '/^MemAvailable:/ {print int($2 / 1024)}' /proc/meminfo 2>/dev/null || echo 0)
if [ -n "$CG_MAX" ]; then
	# minus what is already in use there and can't be reclaimed (anon: e.g. Gradle daemons left by test-all's earlier layers)
	ANON=$(awk '$1 == "anon" {print $2}' "$CG_DIR/memory.stat" 2>/dev/null || true); ANON=${ANON:-0}
	MEM_MB=$(( (CG_MAX - ANON) / 1048576 )); MEM_SRC="cgroup memory.max $((CG_MAX / 1048576)) MB less $((ANON / 1048576)) MB in use ($CG_DIR)"
	if [ "$AVAIL_MB" -gt 0 ] && [ "$AVAIL_MB" -lt "$MEM_MB" ]; then MEM_MB=$AVAIL_MB; MEM_SRC="MemAvailable $AVAIL_MB MB (under cgroup memory.max $((CG_MAX / 1048576)) MB)"; fi
else
	MEM_MB=$AVAIL_MB; MEM_SRC="MemAvailable $AVAIL_MB MB"
fi
RUN_MB="${FB_ORDER_RUN_MB:-3000}"
[[ $RUN_MB =~ ^[1-9][0-9]*$ ]] || { echo "order-test: FB_ORDER_RUN_MB must be a positive number" >&2; exit 2; }
FIT=$(( (MEM_MB - 1024) / RUN_MB )); [ "$FIT" -ge 1 ] || FIT=1
if [ -z "$JOBS" ]; then
	JOBS=$FIT; [ "$JOBS" -le 6 ] || JOBS=6
	echo "order-test: --jobs $JOBS from memory: $MEM_SRC, $RUN_MB MB per run (FB_ORDER_RUN_MB)" >&2
elif [ "$JOBS" -gt "$FIT" ]; then
	echo "order-test: WARNING --jobs $JOBS is over the memory budget ($FIT at $RUN_MB MB per run; $MEM_SRC): servers may be OOM-killed and the run end INCOMPLETE (exit 4)" >&2
fi

BASE="$ROOT/mod/build/run/order"
EULA=""
for f in "$BASE/eula.txt" "$MAIN_ROOT/mod/build/run/order/eula.txt"; do
	if grep -qx 'eula=true' "$f" 2>/dev/null; then EULA="$f"; break; fi
done
if [ -z "$EULA" ]; then
	mkdir -p "$BASE"; echo eula=true > "$BASE/eula.txt"; EULA="$BASE/eula.txt"
	echo "order-test: no eula.txt found; wrote eula=true to $EULA (local test servers, 127.0.0.1, online-mode=false: standing EULA approval in CLAUDE.md, John 2026-10-22/26)" >&2
fi

OUT="$ROOT/build/order-test"
PHASE1=(forward clean-centre config mutant-counter mutant-twice mutant-skip-air mutant-early-step mutant-random mutant-world-stone mutant-lakes)
PHASE2=(reverse shuffle1 shuffle2 spiral threads threads_evict reverse-centre)
ALL=("${PHASE1[@]}" "${PHASE2[@]}")
if [ -n "$ONLY" ]; then
	IFS=, read -ra WANT <<< "$ONLY"
	for w in "${WANT[@]}"; do [[ " ${ALL[*]} " == *" $w "* ]] || { echo "order-test: unknown run '$w' (known: ${ALL[*]})" >&2; exit 2; }; done
	sel() { local out=(); for l in "$@"; do [[ ",$ONLY," == *",$l,"* ]] && out+=("$l"); done; echo "${out[@]:-}"; }
	read -ra PHASE1 <<< "$(sel "${PHASE1[@]}")"; read -ra PHASE2 <<< "$(sel "${PHASE2[@]}")"
	ALL=("${WANT[@]}")
else
	rm -rf "$OUT"
fi
mkdir -p "$OUT"
echo "RUNNING since $(date -u +%FT%TZ), jobs $JOBS (if this stays, the script was killed before it finished)" > "$OUT/STATUS"
cd "$ROOT"

echo "order-test: building once ..." >&2
BUILD_DAEMON=(); [ -n "$CG_MAX" ] && BUILD_DAEMON=(--no-daemon)
./gradlew --console=plain -q "${BUILD_DAEMON[@]}" :mod:gametestClasses :mod:configureLaunch :mod:generateLog4jConfig :mod:generateDLIConfig > "$OUT/build.log" 2>&1 || {
	tail -30 "$OUT/build.log" >&2; echo "BUILD FAILED" > "$OUT/STATUS"; echo "order-test: build failed (exit 2; no run started)" >&2; exit 2; }

PORT0="${FB_ORDER_PORT:-25610}"
# On TERM/INT/HUP stop every run this script started (gradle, its server JVM): otherwise a killed script leaves servers on their ports.
kill_tree() { local c; for c in $(pgrep -P "$1" 2>/dev/null); do kill_tree "$c"; done; kill "$1" 2>/dev/null || true; }
trap 'for c in $(pgrep -P $$ 2>/dev/null); do kill_tree "$c"; done; exit 143' TERM INT HUP
run_label() {
	local label="$1" idx="$2" dir="$BASE/$1"
	rm -rf "$dir/world" "$dir/logs" "$OUT/$label".{verdict,report,run.json}
	mkdir -p "$dir/config"
	cp "$EULA" "$dir/eula.txt"
	# seed 987654321 and the gate city's settlement: the same world the client test made; max-tick-time off (a run is one long server task)
	cat > "$dir/server.properties" <<P
online-mode=false
server-ip=127.0.0.1
server-port=$((PORT0 + idx))
level-seed=987654321
level-name=world
white-list=false
enable-rcon=false
enable-query=false
enable-status=false
view-distance=4
simulation-distance=4
spawn-protection=0
max-tick-time=-1
P
	# the EULA approval covers local offline test servers only: never start one that isn't
	if ! grep -qx 'server-ip=127.0.0.1' "$dir/server.properties" || ! grep -qx 'online-mode=false' "$dir/server.properties"; then
		echo "FAIL $dir/server.properties must bind 127.0.0.1 with online-mode=false" > "$OUT/$label.verdict"
		echo "order-test: $label: server.properties not local/offline; not started" >&2; return 3
	fi
	# gap 3: the config world is made while the file says cellSize 2048; every other run has no config file (the defaults)
	rm -f "$dir/config/fractalbuildings.json"
	[ "$label" = config ] && echo '{"cellSize": 2048}' > "$dir/config/fractalbuildings.json"
	local t0=$SECONDS rc=0
	timeout "${FB_ORDER_TIMEOUT:-3000}" ./gradlew --console=plain --no-daemon :mod:runOrderServer -Pfb.orderRun="$label" -Pfb.orderOut="$OUT" -Pfb.orderRadius="$RADIUS" > "$OUT/$label.log" 2>&1 || rc=$?
	cp "$dir/logs/latest.log" "$OUT/$label.server.log" 2>/dev/null || true
	echo "order-test: $label finished in $((SECONDS - t0)) s (exit $rc): $(cat "$OUT/$label.verdict" 2>/dev/null | cut -c1-200 || true)" >&2
}
phase() {
	local i=0 l
	for l in "$@"; do
		while [ "$(jobs -rp | wc -l)" -ge "$JOBS" ]; do wait -n || true; done
		run_label "$l" "$IDX" &
		IDX=$((IDX + 1))
	done
	wait || true
}
IDX=0; T0=$SECONDS
echo "order-test: phase 1 (${#PHASE1[@]} runs, $JOBS at a time) ..." >&2
phase "${PHASE1[@]}"
echo "order-test: phase 2 (${#PHASE2[@]} runs) ..." >&2
phase "${PHASE2[@]}"

pass=0; fail=0; missing=()
for l in "${ALL[@]}"; do
	if [ ! -s "$OUT/$l.verdict" ]; then missing+=("$l"); continue; fi
	v="$(cat "$OUT/$l.verdict")"
	if [ "$v" = PASS ]; then pass=$((pass + 1)); else fail=$((fail + 1)); echo "order-test: $l: $v" >&2; fi
done
cat "$OUT"/*.report > "$OUT/order-report.txt" 2>/dev/null || true
PEAK=""
if [ -n "$CG_DIR" ] && [ -r "$CG_DIR/memory.peak" ]; then PEAK=", cgroup memory peak $(( $(cat "$CG_DIR/memory.peak") / 1048576 )) of $((CG_MAX / 1048576)) MB"; fi
SUMMARY="$pass of ${#ALL[@]} runs passed, $fail failed, ${#missing[@]} no verdict, $((SECONDS - T0)) s wall (jobs $JOBS, load $(cut -d' ' -f1-3 /proc/loadavg)$PEAK)"
if [ "${#missing[@]}" -gt 0 ]; then
	for l in "${missing[@]}"; do echo "order-test: $l: no verdict (server killed, crashed or timed out; see $OUT/$l.log, $OUT/$l.server.log)" >&2; done
	echo "INCOMPLETE $SUMMARY; no verdict: ${missing[*]}" > "$OUT/STATUS"
	echo "order-test: ORDER TEST DID NOT COMPLETE (no report for: ${missing[*]}; killed? OOM? timeout?) $SUMMARY. Exit 4: not a test result; rerun (fewer --jobs?)"
	exit 4
fi
echo "COMPLETE $SUMMARY" > "$OUT/STATUS"
echo "order-test: $SUMMARY; report $OUT/order-report.txt"
[ "$fail" = 0 ]
