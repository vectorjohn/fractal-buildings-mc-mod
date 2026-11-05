#!/usr/bin/env bash
# Self-test for scripts/ask-user.sh and scripts/needs-user.sh: asks (blocking first, --done, --list),
# classifier denials found in fake transcripts (agent label, reason, command, --ack, no re-adding),
# build files changed since main, open QUESTIONS.md and REVIEW.md lines, john/* branches. Uses a
# throwaway git repo, state dir and transcript dir; touches nothing shared. Exit 0 all pass, 1 a failure.
#   scripts/needs-user-selftest.sh
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d "${TMPDIR:-/tmp}/needs-user-selftest.XXXXXX"); trap 'rm -rf "$T"' EXIT
export FB_STATE_DIR="$T/state" FB_REPO="$T/repo" FB_TRANSCRIPTS="$T/tx" FB_AGENT=selftest FB_RUN_BRANCH=claude/run-x
unset FB_ITEM FB_DENIAL_HOURS
A="$HERE/ask-user.sh" N="$HERE/needs-user.sh" PAGE="$T/state/status/needs-user.md"
fails=0
ok()   { echo "ok   $1"; }
bad()  { echo "FAIL $1"; fails=$((fails+1)); }
check() { local name=$1; shift; if "$@"; then ok "$name"; else bad "$name"; fi; }
has()  { grep -qF -- "$2" <<<"$1"; }
hasnt() { ! grep -qF -- "$2" <<<"$1"; }
rc()   { "$@" >/dev/null 2>&1; echo $?; }
before() { awk -v a="$2" -v b="$3" 'index($0,a) && !x { x=NR } index($0,b) && !y { y=NR } END { exit !(x && y && x<y) }' <<<"$1"; }

# repo: main; run branch with QUESTIONS, REVIEW and a gradle.properties change; john/foo ahead of it
g() { git -C "$T/repo" -c user.name=t -c user.email=t@t -c commit.gpgsign=false "$@"; }
mkdir -p "$T/repo/docs" && echo a > "$T/repo/README.md" && g init -q -b main && g add -A && g commit -qm init
g checkout -qb claude/run-x
cat > "$T/repo/docs/QUESTIONS.md" <<'EOF'
# Open questions for the user

Intro text.

# Release

- [ ] **Release basics** (2026-11-02, from the audit): read the LICENSE.
- [x] **Answered one** (2026-11-01): done.

# Design

- [ ] **Roads first** (2026-10-31): run the prototype?
EOF
cat > "$T/repo/docs/REVIEW.md" <<'EOF'
# Things for the user to review

- [ ] **Old item**: look at it.
  - [ ] a sub-item that is not counted
- [x] **Settled item**: fine.
- [ ] **New item (2026-11-05)**: look at this too.
EOF
echo "minecraft_version=2" > "$T/repo/gradle.properties"
g add -A && g commit -qm "build: bump"
g checkout -qb john/foo && echo j > "$T/repo/j.md" && g add -A && g commit -qm "john edit" && g checkout -q claude/run-x

# transcripts: one subagent with a denied Bash call, a decoy quoting the text, one session denial
mkdir -p "$T/tx/s1/subagents"
D='Permission for this action was denied by the Claude Code auto mode classifier. Reason: [Interfere With Workloads]. If you have other tasks'
now=$(date -u +%Y-%m-%dT%H:%M:%S.000Z)
{
	printf '%s\n' "{\"type\":\"assistant\",\"timestamp\":\"$now\",\"message\":{\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_AAAA1111bbbb2222\",\"name\":\"Bash\",\"input\":{\"command\":\"kill 4242\\nsleep 1\"}}]}}"
	printf '%s\n' "{\"type\":\"user\",\"timestamp\":\"$now\",\"message\":{\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"toolu_AAAA1111bbbb2222\",\"is_error\":true,\"content\":\"$D\"}]}}"
	printf '%s\n' "{\"type\":\"user\",\"timestamp\":\"$now\",\"message\":{\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"toolu_decoy\",\"content\":\"grep output: $D\"}]}}"
} > "$T/tx/s1/subagents/agent-x1.jsonl"
echo '{"agentType":"coder","description":"FOO coder"}' > "$T/tx/s1/subagents/agent-x1.meta.json"
{
	printf '%s\n' "{\"type\":\"assistant\",\"timestamp\":\"$now\",\"message\":{\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_CCCC3333dddd4444\",\"name\":\"Bash\",\"input\":{\"command\":\"scripts/squash-merge.sh foo\"}}]}}"
	printf '%s\n' "{\"type\":\"user\",\"timestamp\":\"$now\",\"message\":{\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"toolu_CCCC3333dddd4444\",\"is_error\":true,\"content\":[{\"type\":\"text\",\"text\":\"${D/Interfere With Workloads/Modify Shared Resources}\"}]}]}}"
} > "$T/tx/s1.jsonl"

# --- asks
id1=$("$A" "first, not blocking"); check "ask prints an id" [ "$id1" = A1 ]
id2=$("$A" --blocking --item FOO --cmd 'kill 4242' "please kill 4242"); check "second id" [ "$id2" = A2 ]
check "empty ask exits 2" [ "$(rc "$A" "  ")" = 2 ]
check "page written by ask-user" test -s "$PAGE"
page=$(cat "$PAGE")
check "blocking ask listed first" before "$page" "**A2**" "**A1**"
check "ask shows agent and item" has "$page" "selftest, FOO"
check "ask shows its command" has "$page" '`kill 4242`'
out=$("$A" --list); check "--list shows open asks" has "$out" "please kill 4242"
"$A" --done A2 "done by the user" >/dev/null
check "--done twice exits 1" [ "$(rc "$A" --done A2)" = 1 ]
page=$(cat "$PAGE"); check "closed ask leaves the page" hasnt "$page" "please kill 4242"
out=$("$A" --list --all); check "--list --all shows the answer" has "$out" "answer: done by the user"

# --- denials
page=$("$N")
check "denial: agent label from meta" has "$page" "coder FOO coder"
check "denial: reason" has "$page" "[Interfere With Workloads]"
check "denial: command, first line only" has "$page" '`kill 4242 ...`'
check "denial: session transcript" has "$page" "session s1"
check "denial: array content" has "$page" "[Modify Shared Resources]"
check "decoy is not a denial" [ "$(grep -vc '^#' "$T/state/status/denials.tsv")" = 2 ]
"$N" --ack bbbb2222 >/dev/null
page=$("$N")
check "--ack by short id drops it" hasnt "$page" "kill 4242 ..."
check "acked denial is not re-added" [ "$(grep -vc '^#' "$T/state/status/denials.tsv")" = 2 ]
check "--ack of nothing exits 1" [ "$(rc "$N" --ack nosuch)" = 1 ]
"$N" --ack all >/dev/null; page=$("$N"); check "--ack all" has "$page" "None in the last 48 h"

# --- repo sections
check "build file listed" has "$page" '`gradle.properties`: '
check "open questions counted" has "$page" "2 open in [QUESTIONS.md]"
check "questions grouped by heading" has "$page" "Design:"
check "question title and date" has "$page" "**Roads first** (2026-10-31)"
check "answered question left out" hasnt "$page" "Answered one"
check "review items counted (top level only)" has "$page" "2 open in [REVIEW.md]"
check "newest review title" has "$page" "**New item (2026-11-05)**"
check "john branch ahead" has "$page" '`john/foo`: 1 commit(s)'

# --- watchdog findings (W8)
check "no watchdog file: not checked" has "$page" "Not checked (no \`.claude/status/watchdog.tsv\`"
printf 'orphan:pg-1\tact\torphan: process group 1 runs on\tkill -TERM -- -1\nquiet:a\tnote\tquiet agent a\t\n' > "$T/state/status/watchdog.tsv"
page=$("$N")
check "watchdog act finding listed with its command" has "$page" '- orphan: process group 1 runs on `kill -TERM -- -1`'
check "watchdog note left out" hasnt "$page" "quiet agent a"
check "--quiet prints nothing" [ -z "$("$N" --quiet)" ]
check "bad option exits 2" [ "$(rc "$N" --frobnicate)" = 2 ]

echo; if [[ $fails == 0 ]]; then echo "needs-user-selftest: all passed"; else echo "needs-user-selftest: $fails FAILED"; exit 1; fi
