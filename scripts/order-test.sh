#!/usr/bin/env bash
# The district-order test (B2, ROADMAP 6.1 / 6.2 evidence) on renderer-free dedicated servers (TESTLAYERS).
#
#   scripts/order-test.sh [--jobs N] [--only label,label,...] [--radius R]
#
# It was a client game test (16 fresh worlds, ~20 min, no screenshots). Now one dedicated-server JVM and one fresh world per run label
# (OrderServerHarness, loaded by the mod's -Dfb.testHook; :mod:runOrderServer), up to N at a time (default 6), NOT under the heavy lock
# (no renderer; timings are not measured). Same assertions as before: all seven chunk orders (forward, reverse, shuffle1, shuffle2,
# spiral, threads, threads_evict) give the same ledger digest as forward and equal the core's reference raster, hooks/roads/ledger 0/0/0,
# unknown-spill classification, the saved-config world, and the seven mutants (counter, twice, skip-air, early-step, random, world-stone, lakes)
# each caught by its own check, against the centre-tile baselines (clean-centre, reverse-centre).
#   phase 1 (independent): forward clean-centre config mutant-*     phase 2 (compare with a phase 1 baseline): the other orders, reverse-centre
# Results: build/order-test/<label>.{verdict,report,run.json,log,server.log}; exit 0 only when every run says PASS.
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
MAIN_ROOT="$(dirname "$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir)")"
JOBS=6; ONLY=""; RADIUS=1
while [ $# -gt 0 ]; do case "$1" in
	--jobs) JOBS="$2"; shift 2 ;; --only) ONLY="$2"; shift 2 ;; --radius) RADIUS="$2"; shift 2 ;;
	-h | --help) sed -n 2,22p "$0"; exit 0 ;; *) echo "order-test: unknown argument $1" >&2; exit 2 ;; esac; done
[[ $JOBS =~ ^[1-9][0-9]*$ ]] || { echo "order-test: --jobs must be a positive number" >&2; exit 2; }

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
cd "$ROOT"

echo "order-test: building once ..." >&2
./gradlew --console=plain -q :mod:gametestClasses :mod:configureLaunch :mod:generateLog4jConfig :mod:generateDLIConfig > "$OUT/build.log" 2>&1 || { tail -30 "$OUT/build.log" >&2; echo "order-test: build failed" >&2; exit 1; }

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

pass=0; fail=0
for l in "${ALL[@]}"; do
	v="$(cat "$OUT/$l.verdict" 2>/dev/null || echo 'FAIL no verdict (the server did not finish; see '"$OUT/$l.log"')')"
	if [ "$v" = PASS ]; then pass=$((pass + 1)); else fail=$((fail + 1)); echo "order-test: $l: $v" >&2; fi
done
cat "$OUT"/*.report > "$OUT/order-report.txt" 2>/dev/null || true
echo "order-test: $pass of ${#ALL[@]} runs passed, $fail failed, $((SECONDS - T0)) s wall (jobs $JOBS, load $(cut -d' ' -f1-3 /proc/loadavg)); report $OUT/order-report.txt"
[ "$fail" = 0 ]
