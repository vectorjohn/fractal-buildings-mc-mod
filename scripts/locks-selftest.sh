#!/usr/bin/env bash
# Self-test for scripts/lib/locks.sh and scripts/locks.sh (W2 lock hygiene): holder notes, waiter
# lines, the self-deadlock refusal, --wait, the listing (with plain flock users), and the library
# wired into scripts/snapshot-test.sh. Temp dir only (FB_LOCK_DIR), never the real .claude locks; ~15 s.
#   scripts/locks-selftest.sh      prints one line per case, exits 1 if any fails
set -uo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
T=$(mktemp -d)
PIDS=()
cleanup() { for p in "${PIDS[@]}"; do kill "$p" 2>/dev/null; done; wait 2>/dev/null; rm -rf "$T"; }
trap cleanup EXIT
export FB_LOCK_DIR=$T/locks FB_LOCK_NOTE_EVERY=1
mkdir -p "$FB_LOCK_DIR"
L=$SRC/locks.sh H=$FB_LOCK_DIR/heavy.lock
fails=0
ok() { echo "ok   $1"; }
bad() { echo "FAIL $1: $2"; fails=$((fails + 1)); }
check() { if [[ $2 == "$3" ]]; then ok "$1"; else bad "$1" "got '$2', want '$3'"; fi; }
has() { if grep -qE -- "$2" "$3"; then ok "$1"; else bad "$1" "no /$2/ in $(tr '\n' '|' < "$3" | cut -c1-400)"; fi; }
hasnt() { if grep -qE -- "$2" "$3"; then bad "$1" "unexpected /$2/"; else ok "$1"; fi; }
waitfor() { local i; for ((i = 0; i < 100; i++)); do eval "$1" && return 0; sleep 0.1; done; return 1; }

# 1. hold: runs the command, passes its exit code, note present while held and gone after
"$L" hold heavy --label "case one" -- bash -c "cat $H.holder > $T/note1; exit 3"; rc=$?
check "hold passes the exit code" "$rc" 3
has "note has the label" '^label=case one$' "$T/note1"
has "note has the pid" '^pid=[0-9]+$' "$T/note1"
has "note has since" '^since=[0-9]+$' "$T/note1"
check "note removed on release" "$([[ -e $H.holder ]] && echo yes || echo no)" no
"$L" path heavy > "$T/p"; check "path heavy" "$(cat "$T/p")" "$H"

# 2. a holder in the background; a waiter prints the holder at once and every FB_LOCK_NOTE_EVERY s
"$L" hold heavy --label "long holder" -- sleep 4 & PIDS+=($!); hp=$!
waitfor "[[ -s $H.holder ]]" || bad "holder note appeared" "no note"
"$L" list > "$T/list1"
has "list shows holder label" '^heavy.lock +held: pid [0-9]+ for [0-9]+s .*long holder' "$T/list1"
"$L" hold heavy --label "patient" -- true 2> "$T/wait2" & PIDS+=($!); wp=$!
waitfor "ls $H.wait.* > /dev/null 2>&1" || bad "waiter registered" "no wait file"
"$L" list > "$T/list2"
has "list shows the waiter" 'waiter: pid [0-9]+ waiting [0-9]+s: patient' "$T/list2"
wait "$wp"; check "waiter got the lock after the holder" "$?" 0
has "waiter named the holder" 'lock: waiting for heavy.lock, held by pid [0-9]+ .*long holder' "$T/wait2"
has "waiter repeated the holder" 'lock: still waiting for heavy.lock' "$T/wait2"
has "waiter said when it got it" 'lock: got heavy.lock after' "$T/wait2"
check "wait file removed" "$(ls "$H".wait.* 2>/dev/null | wc -l)" 0
wait "$hp" 2>/dev/null

# 3. --wait: gives up with 6
"$L" hold heavy -- sleep 3 & PIDS+=($!); hp=$!
waitfor "[[ -s $H.holder ]]"
"$L" hold heavy --wait 1 -- true 2> "$T/w3"; check "--wait gives up with 6" "$?" 6
has "--wait says why" 'not acquired in 1 s' "$T/w3"
kill "$hp" 2>/dev/null; wait "$hp" 2>/dev/null

# 4. self-deadlock: an ancestor holds the lock (the HEAVY2 shape), via hold and via an inherited fd
timeout 10 "$L" hold heavy --label outer -- "$L" hold heavy --label inner -- true 2> "$T/d4"; rc=$?
check "nested hold refused with 8" "$rc" 8
has "refusal names the deadlock" 'LOCK SELF-DEADLOCK: heavy.lock is held by this process or its ancestor' "$T/d4"
( flock 9; timeout 10 "$L" hold heavy -- true 2> "$T/d5"; echo $? > "$T/d5rc" ) 9>> "$H"
check "inherited locked fd refused with 8" "$(cat "$T/d5rc")" 8
( flock 9; timeout 10 bash -c ". $SRC/lib/locks.sh; exec 7>>$H; fb_lock_take 7 $H lib" 9>&- 2> "$T/d6"; echo $? > "$T/d6rc" ) 9>> "$H"
check "library: parent holding (fd closed in child) refused with 8" "$(cat "$T/d6rc")" 8

# 5. stale notes and plain flock users
printf 'pid=999999\nlabel=ghost\nsince=1\n' > "$H.holder"
"$L" list > "$T/l5"; has "stale note, free lock: free" '^heavy.lock +free$' "$T/l5"
( flock 9; sleep 3 ) 9>> "$H" & PIDS+=($!); fp=$!
waitfor "grep -q FLOCK /proc/locks && $L list | grep -q 'heavy.lock *held'"
"$L" list > "$T/l6"
has "plain flock holder shown without a note" 'heavy.lock +held: pid [0-9]+ \(no note; plain flock\?\)' "$T/l6"
hasnt "stale note not shown as holder" 'ghost' "$T/l6"
flock "$H" true & PIDS+=($!); fw=$!
waitfor "$L list | grep -q 'waiting (no note)'" && ok "plain flock waiter listed" || bad "plain flock waiter listed" "$("$L" list | tr '\n' '|')"
wait "$fp" "$fw" 2>/dev/null; rm -f "$H.holder"

# 6. suite slots are listed with their in-file note
exec {sfd}<> "$FB_LOCK_DIR/suite.lock.1"; flock "$sfd"; echo "pid $$ since now: selftest suite" > "$FB_LOCK_DIR/suite.lock.1"
"$L" list > "$T/l7"; has "suite slot listed with its note" '^suite.lock.1 +held: pid [0-9]+ since now: selftest suite' "$T/l7"
exec {sfd}>&-

# 7. bad usage
"$L" hold nosuch -- true 2> /dev/null; check "unknown lock name exits 2" "$?" 2
"$L" hold heavy true 2> /dev/null; check "hold without -- exits 2" "$?" 2

# 8. snapshot-test.sh takes its lock through the library: note while running, refusal under an ancestor
R=$T/repo
git init -q -b main "$R"; git -C "$R" config user.email t@example.invalid; git -C "$R" config user.name t
mkdir -p "$R/scripts/lib"; cp "$SRC/snapshot-test.sh" "$SRC/evidence.sh" "$R/scripts/"; cp "$SRC/lib/locks.sh" "$SRC/lib/tested.sh" "$R/scripts/lib/"
printf '/.claude/\n' > "$R/.gitignore"; git -C "$R" add -A; git -C "$R" commit -qm base
SL=$R/.claude/snapshot-st.lock
unset FB_LOCK_DIR
"$R/scripts/snapshot-test.sh" --name st "$R" HEAD lbl "cat $SL.holder > $T/snapnote" > "$T/s8" 2>&1; rc=$?
check "snapshot-test runs under the library" "$rc" 0
has "snapshot lock note has the label" '^label=snapshot-test --name st lbl at ' "$T/snapnote"
check "snapshot note cleared at the end" "$([[ -e $SL.holder ]] && echo yes || echo no)" no
timeout 20 "$SRC/locks.sh" hold "$SL" -- "$R/scripts/snapshot-test.sh" --name st "$R" HEAD lbl true > "$T/s9" 2>&1; rc=$?
check "snapshot-test under its own lock's holder exits 8" "$rc" 8
has "snapshot-test says self-deadlock" 'LOCK SELF-DEADLOCK: snapshot-st.lock' "$T/s9"
git -C "$R" worktree remove --force "$R/.claude/worktrees/st" 2>/dev/null

if (( fails )); then echo "locks-selftest: $fails FAILED"; exit 1; fi
echo "locks-selftest: all passed"
