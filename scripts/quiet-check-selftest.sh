#!/usr/bin/env bash
# Self-test for scripts/quiet-check.sh windows and perf.sh's verdict line (QUIETHOLE), with a fake worker
# `job` and private lock files: no ssh, no Gradle, no timing, ~30 s.
#   scripts/quiet-check-selftest.sh      prints one line per case, exits 1 if any fails
# CPU of other processes is not judged here (--cpu 9999): this host is never idle, and the CPU sampler is
# the same code as the one-look check.
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
Q=$ROOT/scripts/quiet-check.sh
T=$(mktemp -d); trap 'touch "$T"/w*/stop 2>/dev/null; kill $(jobs -p) 2>/dev/null; wait 2>/dev/null; rm -rf "$T"' EXIT
mkdir -p "$T/claude"
export FB_QUIET_CLAUDE_DIR=$T/claude FB_QUIET_SAMPLE=1 FB_SUITE_SLOTS=2
unset JOB_RESULTS
# fake `job`: `status` prints $T/jobs (ts -l lines), `status ID` prints $T/info.ID
cat > "$T/job" <<EOF
#!/usr/bin/env bash
[[ \$1 == status ]] || exit 2
if [[ -n \${2:-} ]]; then cat "$T/info.\$2" 2>/dev/null; exit 0; fi
echo "ID   State      Output               E-Level  Times(r/u/s)   Command [run=1/3]"
cat "$T/jobs"
EOF
chmod +x "$T/job"
export FB_QUIET_JOB=$T/job
fin() { echo "$1  finished   /x/ts-out.$1 0        300.00/0.00/0.00 [$2]systemd-run --user ... -- bash .fb-remote-run.sh"; }
run() { echo "$1  running    /x/ts-out.$1                        [$2]systemd-run --user ... -- bash .fb-remote-run.sh"; }
info() { printf 'Exit status: died with exit code 0\nCommand: x\nSlots required: 1\nEnqueue time: %s\nStart time: %s\nEnd time: %s\n' "$2" "$2" "$3" > "$T/info.$1"; }
fails=0
check() { if [[ $2 == *"$3"* ]]; then echo "ok   $1"; else echo "FAIL $1: want '$3' in:"; sed 's/^/       /' <<< "$2"; fails=$((fails + 1)); fi; }
nocheck() { if [[ $2 != *"$3"* ]]; then echo "ok   $1"; else echo "FAIL $1: did not want '$3' in:"; sed 's/^/       /' <<< "$2"; fails=$((fails + 1)); fi; }
# a perf-like run: one look at the start, the window around the "timed" part, the verdict
perf_like() {   # perf_like DIR DURING_CMD: prints quiet.txt and the verdict line
	local d=$1; mkdir -p "$d"
	"$Q" --own-job fb-mine --cpu 9999 --window-start "$d/win" >> "$d/quiet.txt"
	"$Q" --own-job fb-mine --cpu 9999 --own-heavy --label start >> "$d/quiet.txt"
	eval "$2"
	sleep 2
	"$Q" --window-end "$d/win" >> "$d/quiet.txt"
	cat "$d/quiet.txt"
	"$Q" --verdict "$d/quiet.txt"
}

# 1. the HEAVY13 hole: two other jobs start AND finish between two looks -> NOT A MEASUREMENT, both named
{ fin 398 fb-old; run 401 fb-mine; } > "$T/jobs"
out=$(perf_like "$T/w1" '{ fin 398 fb-old; run 401 fb-mine; fin 402 fb-vq3base-1; fin 403 fb-vq3head-1; } > "$T/jobs"; info 402 "Sun Nov  1 15:34:47 2026" "Sun Nov  1 15:39:49 2026"; info 403 "Sun Nov  1 15:34:47 2026" "Sun Nov  1 15:39:49 2026"')
check "overlap: window BUSY" "$out" "quiet-check: window BUSY"
check "overlap: verdict NOT A MEASUREMENT" "$out" "perf: NOT A MEASUREMENT (busy host: window: 2 other worker job(s) during the window"
check "overlap: names job 402 with times" "$out" "worker job 402 [fb-vq3base-1] finished, start Sun Nov  1 15:34:47 2026, end Sun Nov  1 15:39:49 2026"
check "overlap: names job 403" "$out" "worker job 403 [fb-vq3head-1]"
nocheck "overlap: the older finished job is not counted" "$out" "job 398"
nocheck "overlap: our own job is not counted" "$out" "job 401 "

# 2. nothing else on the worker -> quiet host
{ fin 398 fb-old; run 401 fb-mine; } > "$T/jobs"
out=$(perf_like "$T/w2" ':')
check "quiet: window QUIET" "$out" "quiet-check: window QUIET"
check "quiet: verdict quiet host" "$out" "perf: quiet host ("

# 3. another job already running at the start that finishes before the end -> busy
{ run 400 fb-other; run 401 fb-mine; } > "$T/jobs"
info 400 "Sun Nov  1 15:00:00 2026" "Sun Nov  1 15:40:00 2026"
out=$(perf_like "$T/w3" '{ fin 400 fb-other; run 401 fb-mine; } > "$T/jobs"')
check "running at start: verdict NOT A MEASUREMENT" "$out" "perf: NOT A MEASUREMENT"
check "running at start: names job 400" "$out" "worker job 400 [fb-other] finished"

# 4. a job queued (not started) during the window -> busy (the queue must stay empty)
{ run 401 fb-mine; } > "$T/jobs"
out=$(perf_like "$T/w4" '{ run 401 fb-mine; echo "404  queued     (file)       [fb-later]systemd-run x"; } > "$T/jobs"')
check "queued during: NOT A MEASUREMENT" "$out" "worker job 404 [fb-later] queued"

# 5. the job list unreachable at the end -> not a measurement
{ run 401 fb-mine; } > "$T/jobs"
out=$(perf_like "$T/w5" 'chmod -x "$T/job"')
chmod +x "$T/job"
check "unreachable: NOT A MEASUREMENT" "$out" "worker job list unavailable at the window end"

# 6. local: someone else (another session, not our child) takes the heavy lock for 3 s inside the window,
#    and is gone again before the window ends -> the sampler saw it
unset FB_QUIET_JOB
out=$(perf_like "$T/w6" 'setsid -f flock "$T/claude/heavy.lock" sleep 3; sleep 1')
check "foreign heavy lock in between: NOT A MEASUREMENT" "$out" "window: heavy lock in"
check "foreign heavy lock: holder named" "$out" "sleep 3"

# 7. local: the heavy lock held by the run itself (perf.sh holds fd 9) -> quiet; a suite slot held by
#    someone else -> busy
out=$( exec 9>"$T/claude/heavy.lock"; flock 9; perf_like "$T/w7" ':' )
nocheck "own heavy lock: not busy" "$out" "busy: heavy lock"
check "own heavy lock: quiet host" "$out" "perf: quiet host"
out=$(perf_like "$T/w8" 'setsid -f flock "$T/claude/suite.lock.2" sleep 3; sleep 1')
check "foreign suite slot in between: NOT A MEASUREMENT" "$out" "window: suite slot 2 in"

# 8. --verdict alone: a quiet.txt with only one-look QUIET lines (no window) is not a measurement either,
#    and a busy one-look line is quoted
printf 'quiet-check: start QUIET perfw load=1 1 1\nquiet-check: world-end QUIET perfw load=5 4 3\n' > "$T/q8"
out=$("$Q" --verdict "$T/q8"); rc=$?
check "no window line: NOT A MEASUREMENT" "$out" "no window check"
[[ $rc == 1 ]] && echo "ok   no window line: exit 1" || { echo "FAIL no window line: exit $rc"; fails=$((fails + 1)); }
printf 'quiet-check: window start x\nquiet-check: start QUIET x\nquiet-check: after-planner BUSY x load=1; busy: heavy lock\nquiet-check: window QUIET x\n' > "$T/q9"
out=$("$Q" --verdict "$T/q9")
check "busy step line: quoted" "$out" "busy host: after-planner: heavy lock"

# 9. perf.sh is wired to it
grep -q -- '--window-start "$QWIN"' "$ROOT/scripts/perf.sh" && grep -q -- '--window-end "$QWIN"' "$ROOT/scripts/perf.sh" && grep -q -- '--verdict "$LOGDIR/quiet.txt"' "$ROOT/scripts/perf.sh" \
	&& echo "ok   perf.sh starts and ends the window and prints the verdict" || { echo "FAIL perf.sh wiring"; fails=$((fails + 1)); }

# 10. --worker over ssh (a fake ssh runs the fake job), resolved through a private worker pool file
mkdir -p "$T/bin"
cat > "$T/bin/ssh" <<EOS
#!/usr/bin/env bash
while [[ \$1 == -o ]]; do shift 2; done
echo "\$1" > "$T/ssh-host"; shift
[[ \$1 == job ]] && shift
exec "$T/job" "\$@"
EOS
chmod +x "$T/bin/ssh"
printf '[perfw]\nhost = perf-host\nroles = perf heavy\nslots = 3\n[w2]\nhost = w2-host\nroles = heavy\n' > "$T/workers.conf"
chmod +x "$T/job"; { run 401 fb-mine; run 405 fb-someone; } > "$T/jobs"
wq() { PATH="$T/bin:$PATH" FB_WORKERS_CONF=$T/workers.conf "$Q" --own-job fb-mine "$@" 2>&1; }
out=$(wq --worker perfw); check "--worker NAME: busy, names the job" "$out" "busy: 1 job(s) running/queued
  405 running fb-someone"
check "--worker NAME: asks its host" "$(cat "$T/ssh-host")" "perf-host"
out=$(wq --worker perf); check "--worker ROLE: the perf worker" "$(cat "$T/ssh-host") $out" "perf-host quiet-check: BUSY perfw jobs running/queued=1"
out=$(wq --worker w2); check "--worker w2: its own host" "$(cat "$T/ssh-host")" "w2-host"
out=$(wq --perf-host); check "deprecated --<ssh host>: resolved through the pool, warns" "$(cat "$T/ssh-host") $out" "perf-host quiet-check: --perf-host is deprecated: use --worker perfw"
{ run 401 fb-mine; } > "$T/jobs"
out=$(wq --perf-host); check "deprecated --<ssh host>: quiet" "$out" "QUIET perfw jobs running/queued=0"
out=$(wq --worker nosuch); rc=$?; check "--worker nosuch: exit 2 with the pool hint" "$rc $out" "2 quiet-check: no enabled worker matches 'nosuch'"
out=$(wq --nosuch-host); rc=$?; check "unknown --flag: exit 2" "$rc $out" "2 quiet-check: unknown --nosuch-host"
out=$(PATH="$T/bin:$PATH" FB_WORKERS_CONF=$T/none.conf "$Q" --worker perf 2>&1); rc=$?
check "--worker without a pool file: exit 2, points to the example" "$rc $out" "no worker pool is configured"
check "--worker without a pool file: exit 2" "$rc" "2"

sleep 1
left=$(pgrep -f "quiet-check.sh --sample-loop $T" || true)
[[ -z $left ]] && echo "ok   no sampler left running" || { echo "FAIL samplers left: $left"; fails=$((fails + 1)); }
(( fails == 0 )) && echo "quiet-check-selftest: all passed" || echo "quiet-check-selftest: $fails failed"
(( fails == 0 ))
