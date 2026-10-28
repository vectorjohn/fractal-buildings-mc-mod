#!/usr/bin/env bash
# shellcheck disable=SC2016 # single quotes are for the fake test commands
# Self-test for the detached post-merge flow: scripts/squash-merge.sh (--cleanup, detached and --wait),
# scripts/postmerge-run.sh and scripts/postmerge-status.sh, on a throwaway git repo in a temp dir with a
# fake snapshot-test.sh (runs the test command with bash -c, prints an EVIDENCE line). No Gradle, no
# real merge, no shared lock; ~30 s.
#   scripts/postmerge-selftest.sh      prints one line per case, exits 1 if any fails
set -uo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
T=$(mktemp -d)
cleanup() { pkill -TERM -f "$T/repo/scripts/postmerge-run.sh" 2>/dev/null; sleep 0.3; rm -rf "$T"; }
trap cleanup EXIT
R=$T/repo
git init -q -b run "$R"
git -C "$R" config user.email selftest@example.invalid; git -C "$R" config user.name selftest
mkdir -p "$R/scripts" "$R/docs" "$R/.claude"
cp "$SRC"/{squash-merge,review-check,postmerge-run,postmerge-status}.sh "$R/scripts/"
printf '# Review\n' > "$R/docs/REVIEW.md"; printf '# Review archive\n' > "$R/docs/REVIEW-ARCHIVE.md"
printf '/.claude/\n' > "$R/.gitignore"
git -C "$R" add -A; git -C "$R" commit -qm base
cat > "$T/fake-snap" <<EOF
#!/usr/bin/env bash
# <checkout> <sha> <label> <cmd...>, like snapshot-test.sh
echo "SNAPSHOT fake at \$2"
mkdir -p $T/ev/\$3
bash -c "\$4"; rc=\$?
echo "EVIDENCE $T/ev/\$3 (exit \$rc)"
exit \$rc
EOF
chmod +x "$T/fake-snap"
export FB_POSTMERGE_SNAPSHOT=$T/fake-snap
unset FB_POSTMERGE_DIR
SM=$R/scripts/squash-merge.sh ST=$R/scripts/postmerge-status.sh PLOG=$R/.claude/postmerge.log
echo "merge it" > "$T/msg"
fails=0
check() { if [[ $2 == "$3" ]]; then echo "ok   $1"; else echo "FAIL $1: got '$2', want '$3'"; fails=$((fails + 1)); fi; }
# a feature branch claude/<1> with one commit, in its own worktree
branch() { git -C "$R" worktree add -q -b "claude/$1" "$T/wt-$1" run 2>/dev/null; echo "$1" > "$T/wt-$1/$1.txt"
           git -C "$T/wt-$1" add -A; git -C "$T/wt-$1" commit -qm "$1"; }
status() { "$ST" "$1" > "$T/st.out" 2>&1; echo $?; }
# wait (up to 20 s) until the status of $1 is no longer RUNNING
settle() { for _ in $(seq 1 100); do [ "$(status "$1")" != 3 ] && return; sleep 0.2; done; }

# 1. detached: returns at once, cleans up first, RUNNING then PASS, result file and log lines
branch pass
t0=$(date +%s)
"$SM" --cleanup "$R" run claude/pass "$T/msg" 'sleep 3; echo hello; exit 0' > "$T/m1.out" 2>&1; rc=$?
t1=$(date +%s)
check "detached merge exits 0" "$rc" 0
check "detached merge returns before the tests end" "$(( t1 - t0 < 3 ))" 1
check "PENDING line printed" "$(grep -c '^POSTMERGE PENDING sha=.* slug=pass pid=[0-9]* log=.* result=.* evidence=' "$T/m1.out")" 1
check "cleanup removed the worktree" "$([ -e "$T/wt-pass" ] && echo kept || echo gone)" gone
check "cleanup deleted the branch" "$(git -C "$R" branch --list claude/pass | wc -l)" 0
check "status RUNNING exit 3" "$(status pass)" 3
check "status line says RUNNING" "$(head -n1 "$T/st.out" | cut -d' ' -f1-2)" "POSTMERGE RUNNING"
settle pass
check "status PASS exit 0" "$(status pass)" 0
SHA=$(git -C "$R" rev-parse HEAD)
check "status by sha prefix" "$(status "${SHA:0:7}")" 0
RES=$R/.claude/postmerge/${SHA:0:10}-pass.result
check "result file status=PASS" "$(sed -n 's/^status=//p' "$RES")" PASS
check "result file evidence" "$(sed -n 's/^evidence=//p' "$RES")" "$T/ev/postmerge-pass"
check "test output in the log" "$(grep -c '^hello$' "$R/.claude/postmerge/${SHA:0:10}-pass.log")" 1
check "postmerge.log START+PASS" "$(grep -c "slug=pass" "$PLOG") $(grep -c " PASS sha=${SHA:0:10} slug=pass rc=0" "$PLOG")" "2 1"

# 2. detached, failing test
branch fail
"$SM" "$R" run claude/fail "$T/msg" 'exit 7' > "$T/m2.out" 2>&1; rc=$?
check "failing detached merge still exits 0" "$rc" 0
check "no cleanup without --cleanup" "$(git -C "$R" branch --list claude/fail | wc -l)" 1
settle fail
check "status FAIL exit 1" "$(status fail)" 1
check "FAIL rc recorded" "$(head -n1 "$T/st.out")" "POSTMERGE FAIL $(git -C "$R" rev-parse HEAD) fail rc=7"

# 3. --wait: the old behaviour
branch waitfail
"$SM" --wait "$R" run claude/waitfail "$T/msg" 'exit 1' > "$T/m3.out" 2>&1; rc=$?
check "--wait failing exits 5" "$rc" 5
check "--wait failing status FAIL" "$(status waitfail)" 1
branch waitpass
"$SM" --wait --cleanup "$R" run claude/waitpass "$T/msg" 'exit 0' > "$T/m4.out" 2>&1; rc=$?
check "--wait passing exits 0" "$rc" 0
check "--wait passing status PASS" "$(status waitpass)" 0

# 4. the caller's process group is killed right after the merge (a tool time limit): tests carry on
branch survive
setsid bash -c "'$SM' --cleanup '$R' run claude/survive '$T/msg' 'sleep 2; exit 0'; sleep 60" > "$T/m5.out" 2>&1 &
CALLER=$!
for _ in $(seq 1 50); do grep -q '^POSTMERGE PENDING' "$T/m5.out" && break; sleep 0.1; done
kill -KILL -- "-$CALLER" 2>/dev/null; wait "$CALLER" 2>/dev/null
check "killed caller: still RUNNING" "$(status survive)" 3
settle survive
check "killed caller: tests finished PASS" "$(status survive)" 0

# 5. KILLED (SIGTERM to the runner) and DIED (SIGKILL: the result file says RUNNING, process gone)
branch term
"$SM" "$R" run claude/term "$T/msg" 'sleep 30' > /dev/null 2>&1
SHA=$(git -C "$R" rev-parse HEAD)
kill -TERM "$(sed -n 's/^pid=//p' "$R/.claude/postmerge/${SHA:0:10}-term.result")"
settle term
check "SIGTERM: status KILLED exit 1" "$(status term) $(head -n1 "$T/st.out" | cut -d' ' -f2)" "1 KILLED"
branch died
"$SM" "$R" run claude/died "$T/msg" 'sleep 30' > /dev/null 2>&1
SHA=$(git -C "$R" rev-parse HEAD)
kill -KILL -- "-$(sed -n 's/^pid=//p' "$R/.claude/postmerge/${SHA:0:10}-died.result")"
sleep 0.3
check "SIGKILL: status DIED exit 1" "$(status died) $(head -n1 "$T/st.out" | cut -d' ' -f2)" "1 DIED"
check "DIED logged once" "$(status died >/dev/null; grep -c " DIED sha=${SHA:0:10} slug=died" "$PLOG")" 1

# 6. cleanup keeps a dirty worktree (and its branch), refuses run branches
branch dirty
echo scratch >> "$T/wt-dirty/dirty.txt"
"$SM" --cleanup "$R" run claude/dirty "$T/msg" > "$T/m6.out" 2>&1; rc=$?
check "dirty worktree: merge exits 0" "$rc" 0
check "dirty worktree: CLEANUP KEPT" "$(grep -c '^CLEANUP KEPT' "$T/m6.out")" 1
check "dirty worktree: branch kept" "$(git -C "$R" branch --list claude/dirty | wc -l)" 1
branch run-x
"$SM" --cleanup "$R" run claude/run-x "$T/msg" > "$T/m7.out" 2>&1
check "run branch: CLEANUP REFUSED" "$(grep -c '^CLEANUP REFUSED' "$T/m7.out")" 1

# 7. list mode, unknown run
check "list shows every run" "$("$ST" | wc -l)" 7
check "unknown run exits 2" "$(status nosuch)" 2

echo "$fails failure(s)"
[ "$fails" = 0 ]
