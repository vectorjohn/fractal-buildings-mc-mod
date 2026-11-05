#!/usr/bin/env bash
# shellcheck disable=SC2016 # single quotes are for the fake commands
# Self-test for scripts/bg.sh (W7): a copy of bg.sh and lib/procs.sh in a temp dir, with fake
# evidence.sh and anywhere.sh that print their arguments; state and registry in the temp dir.
# No Gradle, no real lock, no worker; ~25 s.
#   scripts/bg-selftest.sh      one line per case, exits 1 if any fails
set -uo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
T=$(mktemp -d)
cleanup() { pkill -TERM -f "$T/s/bg.sh __run" 2>/dev/null; sleep 0.3; rm -rf "$T"; }
trap cleanup EXIT
mkdir -p "$T/s/lib" "$T/wt"
cp "$SRC/bg.sh" "$T/s/"; cp "$SRC/lib/procs.sh" "$T/s/lib/"
printf '#!/usr/bin/env bash\necho "fake-evidence label=$1"; l=$1; shift; "$@"; rc=$?; echo "EVIDENCE /ev/$l/x (exit $rc)"; exit $rc\n' > "$T/s/evidence.sh"
printf '#!/usr/bin/env bash\necho "fake-anywhere"; [ "$1" = -- ] && shift; "$@"\n' > "$T/s/anywhere.sh"
chmod +x "$T/s/"*.sh
export FB_BG_DIR=$T/jobs FB_PROCS_DIR=$T/procs FB_BG_POLL=0.2
BG=$T/s/bg.sh
fails=0
check() { if [[ $2 == "$3" ]]; then echo "ok   $1"; else echo "FAIL $1: got '$2', want '$3'"; fails=$((fails + 1)); fi; }
st() { "$BG" status "$1" > "$T/st.out" 2>&1; echo $?; }
cd "$T/wt" || exit 1

# 1. start returns at once; RUNNING, registered; then PASS with .done, evidence, log, registry gone
t0=$(date +%s)
"$BG" pass -- bash -c 'sleep 2; echo hello' > "$T/o1" 2>&1; rc=$?
check "start exits 0" "$rc" 0
check "start returns before the job ends" "$(( $(date +%s) - t0 < 2 ))" 1
check "BG STARTED line" "$(grep -c '^BG STARTED label=pass pid=[0-9]* mode=anywhere ' "$T/o1")" 1
check "RUNNING exit 3" "$(st pass)" 3
check "registered while running" "$(sed -n 's/^label=//p' "$T"/procs/pg-*.proc 2>/dev/null)" "bg pass"
check "no .done while running" "$([ -e "$T/jobs/pass.done" ] && echo yes || echo no)" no
"$BG" wait pass --max 20 > /dev/null; rc=$?
check "wait ends PASS exit 0" "$rc" 0
check ".done holds 0" "$(cat "$T/jobs/pass.done")" 0
check "default mode: evidence then anywhere" "$(head -n2 "$T/jobs/pass.log" | paste -sd' ')" "fake-evidence label=pass fake-anywhere"
check "command output in the log" "$(grep -c '^hello$' "$T/jobs/pass.log")" 1
check "evidence recorded" "$(sed -n 's/^evidence=//p' "$T/jobs/pass.state")" /ev/pass/x
check "registry entry removed" "$(ls "$T/procs" | wc -l)" 0
check "jobs.log START+PASS" "$(grep -c 'label=pass' "$T/jobs.log")" 2

# 2. failing job, --direct with --ev: FAIL exit 1, rc in .done, no anywhere
"$BG" --direct --ev item-x fail -- bash -c 'exit 7' > /dev/null 2>&1
"$BG" wait fail --max 20 > /dev/null; rc=$?
check "FAIL exit 1" "$rc" 1
check ".done holds 7" "$(cat "$T/jobs/fail.done")" 7
check "--direct --ev: evidence label, no anywhere" "$(paste -sd' ' "$T/jobs/fail.log")" "fake-evidence label=item-x EVIDENCE /ev/item-x/x (exit 7)"

# 3. --direct --no-evidence: the bare command; --no-evidence alone refused
"$BG" --direct --no-evidence bare -- bash -c 'echo bare' > /dev/null 2>&1
"$BG" wait bare --max 20 > /dev/null
check "bare: only the command's output" "$(cat "$T/jobs/bare.log")" bare
"$BG" --no-evidence x -- true > /dev/null 2>&1; rc=$?
check "--no-evidence without --direct: exit 2" "$rc" 2

# 4. busy label refused; bad label refused; finished label replaced
"$BG" busy -- sleep 30 > /dev/null 2>&1
"$BG" busy -- true > "$T/o4" 2>&1; rc=$?
check "busy label: exit 2" "$rc" 2
check "busy label: BG BUSY line" "$(grep -c 'BG BUSY' "$T/o4")" 1
"$BG" 'bad/label' -- true > /dev/null 2>&1; rc=$?
check "bad label: exit 2" "$rc" 2
"$BG" --direct --no-evidence pass -- bash -c 'echo again' > /dev/null 2>&1
"$BG" wait pass --max 20 > /dev/null
check "finished label replaced" "$(cat "$T/jobs/pass.log")" again

# 5. wait --max on a running job: exit 3; stop: KILLED, whole group gone, .done 143
"$BG" wait busy --max 1 > /dev/null; rc=$?
check "wait --max timeout: exit 3" "$rc" 3
PID=$(sed -n 's/^pid=//p' "$T/jobs/busy.state")
"$BG" stop busy > /dev/null; rc=$?
check "stop: exit 0" "$rc" 0
for _ in $(seq 1 50); do [ -e "$T/jobs/busy.done" ] && break; sleep 0.1; done
check "stop: KILLED exit 1" "$(st busy) $(head -n1 "$T/st.out" | cut -d' ' -f2)" "1 KILLED"
check "stop: .done holds 143" "$(cat "$T/jobs/busy.done")" 143
sleep 0.5
check "stop: process group gone" "$(pgrep -g "$PID" | wc -l)" 0
"$BG" stop busy > /dev/null; rc=$?
check "stop again: exit 1" "$rc" 1

# 6. the caller's process group killed right after the start (a tool time limit): the job carries on
setsid bash -c "'$BG' --direct --no-evidence survive -- bash -c 'sleep 2; echo survived'; sleep 60" > "$T/o6" 2>&1 &
CALLER=$!
for _ in $(seq 1 50); do grep -q '^BG STARTED' "$T/o6" && break; sleep 0.1; done
kill -KILL -- "-$CALLER" 2>/dev/null; wait "$CALLER" 2>/dev/null
check "killed caller: still RUNNING" "$(st survive)" 3
"$BG" wait survive --max 20 > /dev/null; rc=$?
check "killed caller: job PASS" "$rc" 0

# 7. SIGKILL of the runner: DIED, logged once
"$BG" --direct --no-evidence died -- sleep 30 > /dev/null 2>&1
PID=$(sed -n 's/^pid=//p' "$T/jobs/died.state")
kill -KILL -- "-$PID"; sleep 0.3
check "SIGKILL: DIED exit 1" "$(st died) $(head -n1 "$T/st.out" | cut -d' ' -f2)" "1 DIED"
st died > /dev/null
check "DIED logged once" "$(grep -c 'DIED label=died' "$T/jobs.log")" 1

# 8. list mode, unknown job, log
check "list shows every job" "$("$BG" status | wc -l)" 6
check "unknown job: exit 2" "$(st nosuch)" 2
check "log tail" "$("$BG" log bare)" bare

echo "$fails failure(s)"
[ "$fails" = 0 ]
