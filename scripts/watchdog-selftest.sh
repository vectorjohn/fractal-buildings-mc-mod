#!/usr/bin/env bash
# shellcheck disable=SC2016 # single quotes are for the fake commands
# Self-test for scripts/watchdog.sh (W8): fake registry entries, locks in a temp dir, a fake orphaned
# "JVM" (a renamed sleep, matched through FB_WATCHDOG_JVM_RE) and fake transcripts. Every process it
# checks is one this test started; the test stops them itself at the end. ~20 s.
#   scripts/watchdog-selftest.sh      one line per case, exits 1 if any fails
set -uo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WT="$(cd "$SRC/.." && pwd)"
T=$(mktemp -d)
D="$WT/build/watchdog-selftest.$$"   # the fake JVM's working directory must be inside the repo
mkdir -p "$D" "$T/locks" "$T/state" "$T/procs" "$T/tr/s1/subagents"
PIDS=()
cleanup() {
	local p; for p in "${PIDS[@]}"; do kill -TERM -- "-$p" 2>/dev/null; kill -TERM "$p" 2>/dev/null; done
	[[ -n ${FAKE:-} ]] && kill -TERM -- "-$(ps -o pgid= -p "$FAKE" | tr -d " ")" 2>/dev/null
	[[ -n ${DESK:-} ]] && kill -TERM -- "-$(ps -o pgid= -p "$DESK" | tr -d " ")" 2>/dev/null
	rm -rf "$T" "$D"
}
trap cleanup EXIT
TAG="fbwdtest$$"
export FB_STATE_DIR=$T/state FB_LOCK_DIR=$T/locks FB_PROCS_DIR=$T/procs FB_TRANSCRIPTS=$T/tr \
	FB_WATCHDOG_JVM_RE="$TAG" FB_WATCHDOG_WT_EVERY=999999
unset FB_WATCHDOG_NOW
W=$SRC/watchdog.sh
TSV=$T/state/status/watchdog.tsv LOG=$T/state/status/watchdog.log
fails=0
check() { if [[ $2 == "$3" ]]; then echo "ok   $1"; else echo "FAIL $1: got '$2', want '$3'"; fails=$((fails + 1)); fi; }
has() { awk -F'\t' -v k="$1" -v l="$2" '$1 ~ k && $2 == l' "$TSV" | wc -l; }
deadpid() { bash -c 'echo $$'; }   # a pid that has just exited
mkdir -p "$T/state/status"; : > "$T/state/status/watchdog.worktrees"   # seeded: no real worktree scan

# --- fixtures ---
# a. registered group whose owner is dead, group alive -> orphan
setsid sleep 120 & G1=$!; PIDS+=("$G1")
DEAD=$(deadpid)
printf 'id=pg-%s\nkind=pgroup\npgid=%s\nowner=%s\nlabel=fake client\nbudget=0\nsince=%s\nhost=%s\nworktree=%s\n' \
	"$G1" "$G1" "$DEAD" "$(date +%s)" "$HOSTNAME" "$WT" > "$T/procs/pg-$G1.proc"
# b. registered, owner and group dead -> pruned
printf 'id=pg-99999999\nkind=pgroup\npgid=99999999\nowner=%s\nhost=%s\n' "$DEAD" "$HOSTNAME" > "$T/procs/pg-99999999.proc"
# c. registered, owner alive (this test), over budget
setsid sleep 120 & G2=$!; PIDS+=("$G2")
printf 'id=pg-%s\nkind=pgroup\npgid=%s\nowner=%s\nlabel=slow job\nbudget=10\nsince=%s\nhost=%s\n' \
	"$G2" "$G2" "$$" "$(( $(date +%s) - 100 ))" "$HOSTNAME" > "$T/procs/pg-$G2.proc"
# d. worker jobs: waiter dead and not fetched -> stale-job; fetched -> pruned
printf 'status=running\n' > "$T/job1.state"; printf 'status=fetched\n' > "$T/job2.state"
printf 'id=remote-fb-j1\nkind=remote\nowner=%s\nstate=%s\njob=fb-j1\nworker=w1\nsince=%s\nhost=%s\n' "$DEAD" "$T/job1.state" "$(date +%s)" "$HOSTNAME" > "$T/procs/remote-fb-j1.proc"
printf 'id=remote-fb-j2\nkind=remote\nowner=%s\nstate=%s\njob=fb-j2\nhost=%s\n' "$DEAD" "$T/job2.state" "$HOSTNAME" > "$T/procs/remote-fb-j2.proc"
# e. heavy lock held with a holder note (client-test quick label), sleeping holder
setsid bash -c '. "$1/lib/locks.sh"; exec 9>>"$2/heavy.lock"; fb_lock_take 9 "$2/heavy.lock" "client-test FB_LAYER=quick"; sleep 120' _ "$SRC" "$T/locks" & G3=$!; PIDS+=("$G3")
# f. self-deadlock: a holder whose own child waits for the same lock (HEAVY2 shape)
setsid bash -c 'exec 8>>"$1/merge.lock"; flock 8; flock "$1/merge.lock" true' _ "$T/locks" & G4=$!; PIDS+=("$G4")
# g. an unregistered "JVM" in the repo, re-parented, its group leader gone
env -u WAYLAND_DISPLAY setsid bash -c 'cd "$1"; (exec -a "$2-KnotClient" bash -c "sleep 120; :") & disown' _ "$D" "$TAG" < /dev/null > /dev/null 2>&1
# g2. the same with a desktop display (John's own client): never listed
WAYLAND_DISPLAY=wayland-0 setsid bash -c 'cd "$1"; (exec -a "$2-KnotClient-desk" bash -c "sleep 120; :") & disown' _ "$D" "$TAG" < /dev/null > /dev/null 2>&1
# h. transcripts: one quiet for 40 min, one handed back, one fresh
printf '{"type":"assistant","message":{"content":[{"type":"tool_use","name":"Bash","input":{"command":"x","description":"Run the slow thing"}}]}}\n' > "$T/tr/s1/subagents/agent-quiet1.jsonl"
printf '{"agentType":"coder","description":"item X"}' > "$T/tr/s1/subagents/agent-quiet1.meta.json"
printf '{"type":"assistant","message":{"content":[{"type":"tool_use","name":"SubagentHandback","input":{}}]}}\n' > "$T/tr/s1/subagents/agent-done1.jsonl"
printf '{}\n' > "$T/tr/s1/subagents/agent-fresh1.jsonl"
touch -d '-40 min' "$T/tr/s1/subagents/agent-quiet1.jsonl" "$T/tr/s1/subagents/agent-done1.jsonl"
sleep 1
FAKE=$(pgrep -f "^$TAG-KnotClient " | head -n1); DESK=$(pgrep -f "^$TAG-KnotClient-desk" | head -n1)

# --- pass 1 (now) ---
"$W" --quiet --check; rc=$?
check "--check exits 1 with act findings" "$rc" 1
check "orphan: registered group, owner dead" "$(has "^orphan:pg-$G1\$" act)" 1
check "orphan: suggested command is a group TERM" "$(awk -F'\t' -v k="orphan:pg-$G1" '$1 == k { print $4 }' "$TSV")" "kill -TERM -- -$G1"
check "pruned: owner and group gone" "$([ -e "$T/procs/pg-99999999.proc" ] && echo kept || echo pruned)" pruned
check "over-budget: owner alive" "$(has "^over-budget:pg-$G2\$" act)" 1
check "stale-job: waiter dead" "$(has '^stale-job:fb-j1$' act)" 1
check "stale-job: --attach suggested" "$(awk -F'\t' '$1 == "stale-job:fb-j1" { print $4 }' "$TSV" | grep -c -- '--attach fb-j1')" 1
check "fetched job pruned" "$([ -e "$T/procs/remote-fb-j2.proc" ] && echo kept || echo pruned)" pruned
check "deadlock: waiter's ancestor holds merge.lock" "$(has '^deadlock:merge.lock:' act)" 1
check "heavy lock: no stall yet" "$(has '^stall:heavy.lock:' act)" 0
check "unregistered orphan JVM listed" "$(has "^orphan[?]:$FAKE\$" act)" 1
check "desktop client (WAYLAND_DISPLAY) not listed" "$([ -n "$DESK" ] && has "^orphan[?]:$DESK\$" act)" 0
check "quiet agent listed (note)" "$(has '^quiet:agent-quiet1$' note)" 1
check "quiet agent: last command shown" "$(grep -c 'last command: Run the slow thing' "$TSV")" 1
check "handed-back and fresh agents not listed" "$(grep -c 'agent-done1\|agent-fresh1' "$TSV")" 0
check "log: NEW line per finding" "$(grep -c ' NEW ' "$LOG")" "$(wc -l < "$TSV")"
check "page written" "$(grep -c '^# Needs action' "$T/state/status/watchdog.md")" 1

# --- report only: everything is still running ---
check "orphan group still alive" "$(kill -0 "$G1" 2>/dev/null && echo alive)" alive
check "over-budget group still alive" "$(kill -0 "$G2" 2>/dev/null && echo alive)" alive
check "fake JVM still alive" "$(kill -0 "$FAKE" 2>/dev/null && echo alive)" alive
check "deadlocked holder still alive" "$(kill -0 "$G4" 2>/dev/null && echo alive)" alive
check "script has no executed kill (only kill -0)" \
	"$(grep -nE '(^|[;&|({]\s*)(kill|pkill|killall)\b' "$W" | grep -v 'kill -0' | wc -l)" 0
check "script never stops Gradle daemons" "$(grep -c 'gradlew --stop' "$W")" 0

# --- pass 2: same state -> no new log lines ---
n1=$(wc -l < "$LOG")
"$W" --quiet
check "unchanged pass: log unchanged" "$(wc -l < "$LOG")" "$n1"

# --- pass 3: 50 min later -> heavy lock over its quick budget (40 min) and idle (no CPU) ---
FB_WATCHDOG_NOW=$(( $(date +%s) + 3000 )) "$W" --quiet
check "stall: heavy held past the quick budget" "$(has "^stall:heavy.lock:" act)" 1
check "idle: holder tree used no CPU" "$(has "^idle:heavy.lock:" act)" 1

# --- pass 4: the orphan goes away (this test stops its own process) -> CLEARED ---
kill -TERM -- "-$G1" 2>/dev/null; sleep 0.5
"$W" --quiet
check "cleared: CLEARED line" "$(grep -c " CLEARED orphan:pg-$G1\$" "$LOG")" 1
check "cleared: entry pruned" "$([ -e "$T/procs/pg-$G1.proc" ] && echo kept || echo pruned)" pruned

# --- worktree note from the hourly cache; units ---
printf 'worktree:claude/x\tnote\tstale worktree x\tgit worktree remove x\n' > "$T/state/status/watchdog.worktrees"
"$W" --quiet
check "worktree note from the cache" "$(has '^worktree:claude/x$' note)" 1
U=$("$W" --print-units --every 7)
check "units: ExecStart is this script" "$(grep -c "^ExecStart=$W --quiet\$" <<<"$U")" 1
check "units: timer interval" "$(grep -c '^OnUnitActiveSec=7min$' <<<"$U")" 1
"$W" --every x > /dev/null 2>&1; rc=$?
check "bad --every: exit 2" "$rc" 2

echo "$fails failure(s)"
[ "$fails" = 0 ]
