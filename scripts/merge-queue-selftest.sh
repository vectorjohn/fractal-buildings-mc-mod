#!/usr/bin/env bash
# Self-test for scripts/merge-queue.sh (W10) on a throwaway git repo in a temp dir: a stub
# scripts/test-all.sh (fails when a tracked file says BAD; counts its runs; can be held with a file) and a
# stub scripts/regen-goldens.sh (golden/all.txt = sorted src/*.txt, golden/other.txt = src/other.in), with
# the real evidence.sh, test ledger, board.sh, bg.sh, test-scope.sh, review-check.sh and lib/. No Gradle,
# no shared state (everything under the temp dir); ~1 min.
#   scripts/merge-queue-selftest.sh      prints one line per check, exits 1 if any fails
# Cases: a train of 3 passes (linear fast-forward, cleanup, board lanes, HEAVY BATCH DUE); the test ledger
# skips a tree that already passed; a failing middle item is bisected out, the others merge; a conflict
# leaves the item queued with a note (and it is not retried until the run branch moves); a conflict only in
# generated files is resolved by regeneration, and the regen guard fails an item whose regeneration
# touches generated files it did not change; a runner started under merge.lock refuses (LOCK SELF-DEADLOCK);
# a runner SIGKILLed mid-train leaves the run branch as it was and the next runner merges the items; the run
# branch moving during the suite by a docs-only commit (no retest) or a code commit (rebuild and retest);
# add --start runs the runner detached through bg.sh.
set -uo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
T=$(mktemp -d); [[ -n ${MQ_SELFTEST_KEEP:-} ]] && echo "temp dir kept: $T"
cleanup() { [[ -n ${BGPID:-} ]] && kill -KILL -- "-$BGPID" 2>/dev/null; rm -rf "$T"; }
trap cleanup EXIT; [[ -n ${MQ_SELFTEST_KEEP:-} ]] && trap - EXIT
unset FB_TESTED_FILE FB_RETEST FB_LAYER FB_ONLY FB_PERF FB_CLIENT_CMD FB_MQ_DIR FB_MQ_WORKTREE FB_STATE_DIR FB_RUN_BRANCH FB_SUBJECT_STRICT
export FB_BG_DIR=$T/bg FB_PROCS_DIR=$T/procs FB_HEAVY_EVERY=3 FB_LOCK_NOTE_EVERY=1 FB_MERGE_LOCK_WAIT=20
export FB_MQ_TEST="scripts/test-all.sh --no-client" FB_MQ_REGEN="scripts/regen-goldens.sh" FB_MQ_GENERATED="golden"
R=$T/repo IB=claude/run-test
git init -q -b "$IB" "$R"
git -C "$R" config user.email selftest@example.invalid; git -C "$R" config user.name selftest
mkdir -p "$R/scripts/lib" "$R/docs" "$R/src" "$R/golden"
cp "$SRC"/{merge-queue,review-check,evidence,tested,board,bg,test-scope}.sh "$SRC/test-scope.tsv" "$R/scripts/"
cp "$SRC"/lib/{locks,squash,tested,procs}.sh "$R/scripts/lib/"
cat > "$R/scripts/test-all.sh" <<EOF
#!/usr/bin/env bash
# stub fast suite: hold while $T/slow exists, count the run, fail on BAD
touch $T/started
while [[ -e $T/slow ]]; do sleep 0.1; done
echo "\$(git rev-parse --short HEAD)" >> $T/runs
if git grep -q BAD -- . ':!scripts'; then echo "stub suite: BAD found"; exit 1; fi
echo "stub suite: pass"
EOF
cat > "$R/scripts/regen-goldens.sh" <<'EOF'
#!/usr/bin/env bash
cat src/*.txt | sort > golden/all.txt
cp src/other.in golden/other.txt
EOF
chmod +x "$R/scripts/"*.sh
printf '# Review\n' > "$R/docs/REVIEW.md"; printf '# Review archive\n' > "$R/docs/REVIEW-ARCHIVE.md"
printf '/.claude/\n/build/\n' > "$R/.gitignore"
echo base > "$R/src/base.txt"; echo other-1 > "$R/src/other.in"; printf 'one\ntwo\nthree\n' > "$R/shared.txt"
(cd "$R" && scripts/regen-goldens.sh)
git -C "$R" add -A; git -C "$R" commit -qm "chore: base"
MQ=$R/scripts/merge-queue.sh
fails=0
check() { if [[ $2 == "$3" ]]; then echo "ok   $1"; else echo "FAIL $1: got '$2', want '$3'"; fails=$((fails + 1)); fi; }
has() { if grep -qE -- "$2" "$3"; then echo "ok   $1"; else echo "FAIL $1: no /$2/ in $3: $(tail -n 5 "$3" | tr '\n' '|' | cut -c1-500)"; fails=$((fails + 1)); fi; }
runs() { wc -l < "$T/runs" | tr -d ' '; }
: > "$T/runs"
state() { local d; for d in "$R"/.claude/merge-queue/items/*/; do grep -qx "slug=$1" "$d/meta" && st=$(sed -n 's/^state=//p' "$d/meta"); done; echo "${st:-none}"; }
note() { local d; for d in "$R"/.claude/merge-queue/items/*/; do grep -qx "slug=$1" "$d/meta" && n=$(sed -n 's/^note=//p' "$d/meta"); done; echo "${n:-}"; }
lane() { "$R/scripts/board.sh" show "$1" 2>/dev/null | head -n1 | sed 's/^[^:]*: \([^ ]*\).*/\1/'; }
head_() { git -C "$R" rev-parse HEAD; }
clean_() { git -C "$R" status --porcelain --untracked-files=no | wc -l | tr -d ' '; }
lock_free() { if flock -n "$R/.claude/merge.lock" true; then echo free; else echo held; fi; }
# branch NAME FROM: worktree $T/wt-NAME on claude/NAME; then edit there and `commit NAME subject`
branch() { git -C "$R" worktree add -q -b "claude/$1" "$T/wt-$1" "${2:-$IB}" 2>/dev/null; }
commit() { git -C "$T/wt-$1" add -A; git -C "$T/wt-$1" commit -qm "${2:-$1}"; }
msg() { printf '%s\n\n- body of %s\n\nCo-Authored-By: test <t@example.invalid>\n' "$2" "$1" > "$T/msg-$1"; echo "$T/msg-$1"; }
add() { local o=(); while [[ $1 == --* ]]; do o+=("$1"); shift; done
        "$MQ" add "${o[@]+"${o[@]}"}" "$R" "$IB" "claude/$1" "$(msg "$1" "$2")" > "$T/add-$1.out" 2>&1; }

# ---- 1. a train of three passes ---------------------------------------------------------------
"$R/scripts/board.sh" --run-branch "$IB" batch done "$IB" "selftest start" > /dev/null
B0=$(head_)
for x in a b c; do branch $x; echo "$x" > "$T/wt-$x/$x.txt"; commit $x; done
add --cleanup a "feat(A): add a"; add b "feat(B): add b"; add c "fix(C): add c"
check "add prints QUEUED" "$(grep -c '^QUEUED a ' "$T/add-a.out")" 1
check "queued item on the board lane merge-queue" "$(lane b)" merge-queue
"$MQ" run "$R" "$IB" --once > "$T/r1.out" 2>&1; rc=$?
check "train of 3: exit 0" "$rc" 0
check "train of 3: subjects in queue order" "$(git -C "$R" log --format=%s -3 | tac | paste -sd'|')" "feat(A): add a|feat(B): add b|fix(C): add c"
check "train of 3: linear fast-forward from the old head" "$(git -C "$R" rev-parse HEAD~3) $(git -C "$R" rev-list --merges "$B0..HEAD" | wc -l)" "$B0 0"
check "train of 3: one suite run" "$(runs)" 1
check "train of 3: items merged" "$(state a) $(state b) $(state c)" "merged merged merged"
check "train of 3: board lane merged" "$(lane c)" merged
check "train of 3: --cleanup removed a's worktree and branch" "$([ -e "$T/wt-a" ] && echo kept || echo gone) $(git -C "$R" branch --list claude/a | wc -l)" "gone 0"
check "train of 3: b kept (no --cleanup)" "$(git -C "$R" branch --list claude/b | wc -l)" 1
has "train of 3: HEAVY BATCH DUE printed" "HEAVY BATCH DUE" "$T/r1.out"
has "train of 3: HEAVY_BATCH_DUE event" "HEAVY_BATCH_DUE" "$R/.claude/merge-queue/events.tsv"
check "train of 3: checkout clean, merge lock free, no holder note" "$(clean_) $(lock_free) $([ -e "$R/.claude/merge.lock.holder" ] && echo note || echo none)" "0 free none"
has "train of 3: test ledger row (fast, exit 0)" "	fast	0	" "$R/.claude/tested.tsv"
check "train of 3: the queue worktree is detached" "$(git -C "$R/.claude/worktrees/mergequeue" symbolic-ref -q HEAD || echo detached)" detached

# ---- 2. the ledger skips a tree that already passed -------------------------------------------
branch d; echo d > "$T/wt-d/d.txt"; commit d
(cd "$T/wt-d" && scripts/evidence.sh d scripts/test-all.sh --no-client > /dev/null 2>&1)
n0=$(runs); add d "feat(D): add d"
"$MQ" run "$R" "$IB" --once > "$T/r2.out" 2>&1
check "ledger: merged" "$(state d) $(git -C "$R" log -1 --format=%s)" "merged feat(D): add d"
check "ledger: no new suite run (tree = the branch's tested tree)" "$(runs)" "$n0"
has "ledger: ALREADY PASSED noted" "ALREADY PASSED" "$R/.claude/merge-queue/events.tsv"

# ---- 3. a failing middle item is bisected out -------------------------------------------------
for x in x y z; do branch $x; echo "$x" > "$T/wt-$x/$x.txt"; done
echo BAD >> "$T/wt-y/y.txt"
for x in x y z; do commit $x; done
add x "feat(X): add x"; add y "feat(Y): add y"; add z "feat(Z): add z"
n0=$(runs); B0=$(head_)
"$MQ" run "$R" "$IB" --once > "$T/r3.out" 2>&1; rc=$?
check "bisect: exit 0" "$rc" 0
check "bisect: x and z merged, y failed" "$(state x) $(state y) $(state z)" "merged failed merged"
check "bisect: run branch got x then z, linear" "$(git -C "$R" log --format=%s "$B0..HEAD" | tac | paste -sd'|') $(git -C "$R" rev-list --merges "$B0..HEAD" | wc -l)" "feat(X): add x|feat(Z): add z 0"
check "bisect: no y.txt on the run branch" "$(git -C "$R" cat-file -e HEAD:y.txt 2>/dev/null && echo there || echo absent)" absent
check "bisect: y on the board lane blocked" "$(lane y)" blocked
check "bisect: 4 suite runs (xyz, x, xy, xz)" "$(( $(runs) - n0 ))" 4
has "bisect: note names the evidence" "fast suite failed with it" <(note y; echo)
check "bisect: y's branch kept" "$(git -C "$R" branch --list claude/y | wc -l)" 1

# ---- 4. a conflict leaves the item queued with a note -----------------------------------------
branch p; sed -i 's/two/two-p/' "$T/wt-p/shared.txt"; commit p
branch q; echo q > "$T/wt-q/q.txt"; commit q
sed -i 's/two/two-run/' "$R/shared.txt"; git -C "$R" commit -qam "fix: shared on the run branch"
add p "feat(P): change shared"; add q "feat(Q): add q"
B0=$(head_)
"$MQ" run "$R" "$IB" --once > "$T/r4.out" 2>&1; rc=$?
check "conflict: exit 0" "$rc" 0
check "conflict: p held as conflict, q merged" "$(state p) $(state q)" "conflict merged"
check "conflict: note names the file" "$(note p)" "MERGE CONFLICT in shared.txt"
check "conflict: p stays in lane merge-queue" "$(lane p)" merge-queue
check "conflict: run branch has only q" "$(git -C "$R" log --format=%s "$B0..HEAD" | paste -sd'|')" "feat(Q): add q"
check "conflict: checkout and queue worktree clean" "$(clean_) $(git -C "$R/.claude/worktrees/mergequeue" status --porcelain | wc -l)" "0 0"
"$MQ" run "$R" "$IB" --once > "$T/r4b.out" 2>&1
check "conflict: retried once the run branch moved, still conflict" "$(state p) $(grep -c 'CONFLICT p' "$T/r4b.out")" "conflict 1"
"$MQ" run "$R" "$IB" --once > "$T/r4c.out" 2>&1
has "conflict: not retried while the run branch stays put" "queue empty" "$T/r4c.out"
"$MQ" drop p > /dev/null
check "drop: p dropped" "$(state p)" dropped

# ---- 5. generated-file conflicts and the regen guard ------------------------------------------
B0=$(head_)
for x in g1 g2 g3; do branch $x "$B0"; echo "$x" > "$T/wt-$x/src/$x.txt"; (cd "$T/wt-$x" && scripts/regen-goldens.sh); done
echo other-2 > "$T/wt-g3/src/other.in"   # g3 changes other.in but does not regenerate golden/other.txt
for x in g1 g2 g3; do commit $x; done
add g1 "feat(G1): g1"; add g2 "feat(G2): g2"; add g3 "feat(G3): g3"
"$MQ" run "$R" "$IB" --once > "$T/r5.out" 2>&1
check "regen: g1 and g2 merged, g3 failed" "$(state g1) $(state g2) $(state g3)" "merged merged failed"
check "regen: golden/all.txt has both" "$(git -C "$R" show HEAD:golden/all.txt | grep -c '^g[12]$')" 2
check "regen: g2's commit has the Regenerated-by trailer" "$(git -C "$R" log -1 --format=%B | grep -c '^Regenerated-by: merge-queue')" 1
check "regen: g1 (based on the tip) not regenerated" "$(git -C "$R" log -1 --format=%B HEAD~1 | grep -c '^Regenerated-by')" 0
has "regen guard: g3's note names golden/other.txt" "regen guard: .*golden/other.txt" <(note g3; echo)

# ---- 6. a runner under merge.lock refuses -----------------------------------------------------
branch e; echo e > "$T/wt-e/e.txt"; commit e; add e "feat(E): add e"
B0=$(head_)
flock "$R/.claude/merge.lock" "$MQ" run "$R" "$IB" --once > "$T/r6.out" 2>&1; rc=$?
check "self-deadlock: exit 9" "$rc" 9
has "self-deadlock: LOCK SELF-DEADLOCK line" "LOCK SELF-DEADLOCK" "$T/r6.out"
check "self-deadlock: nothing merged, item still queued" "$(head_) $(state e)" "$B0 queued"

# ---- 7. a runner killed mid-train -------------------------------------------------------------
branch k; echo k > "$T/wt-k/k.txt"; commit k; add k "feat(K): add k"
touch "$T/slow"; rm -f "$T/started"
setsid "$MQ" run "$R" "$IB" --once > "$T/r7.out" 2>&1 < /dev/null &
BGPID=$!
for _ in $(seq 1 100); do [[ -e $T/started ]] && break; sleep 0.1; done
check "crash: the suite started" "$([ -e "$T/started" ] && echo yes)" yes
check "crash: items in the train" "$(state e) $(state k)" "train train"
kill -KILL -- "-$BGPID"; wait "$BGPID" 2>/dev/null; BGPID=""; rm -f "$T/slow"; sleep 0.3
check "crash: run branch unchanged, checkout clean, merge lock free" "$(head_) $(clean_) $(lock_free)" "$B0 0 free"
"$MQ" run "$R" "$IB" --once > "$T/r7b.out" 2>&1; rc=$?
check "crash: the next runner merges both" "$rc $(state e) $(state k)" "0 merged merged"
has "crash: recovery noted" "back to queued \(runner stopped mid-train\)" "$R/.claude/merge-queue/events.tsv"

# ---- 8. the run branch moves during the suite --------------------------------------------------
moved_run() { # NAME FILE: queue NAME, hold its suite, commit FILE on the run branch meanwhile, let it finish
	branch "$1"; echo "$1" > "$T/wt-$1/$1.txt"; commit "$1"; add "$1" "feat($1): add $1"
	touch "$T/slow"; rm -f "$T/started"
	"$MQ" run "$R" "$IB" --once > "$T/r8-$1.out" 2>&1 &
	local p=$!
	for _ in $(seq 1 100); do [[ -e $T/started ]] && break; sleep 0.1; done
	mkdir -p "$(dirname "$R/$2")"; echo "$1" >> "$R/$2"; git -C "$R" add -A; git -C "$R" commit -qm "docs: moved by $1"
	rm -f "$T/slow"; wait "$p"
}
n0=$(runs); moved_run m1 docs/notes.md
check "moved (docs only): merged on top of the docs commit" "$(state m1) $(git -C "$R" log --format=%s -2 | paste -sd'|')" "merged feat(m1): add m1|docs: moved by m1"
check "moved (docs only): no retest" "$(( $(runs) - n0 ))" 1
has "moved (docs only): noted" "without a retest" "$T/r8-m1.out"
n0=$(runs); moved_run m2 src/code.txt
check "moved (code): merged on top" "$(state m2) $(git -C "$R" log --format=%s -2 | paste -sd'|')" "merged feat(m2): add m2|docs: moved by m2"
check "moved (code): rebuilt and retested" "$(( $(runs) - n0 ))" 2

# ---- 9. add --start: detached runner through bg.sh --------------------------------------------
branch s; echo s > "$T/wt-s/s.txt"; commit s
"$MQ" add --start "$R" "$IB" claude/s "$(msg s 'feat(S): add s')" > "$T/r9.out" 2>&1
"$R/scripts/bg.sh" wait merge-queue --max 60 > /dev/null 2>&1; rc=$?
check "start: the detached runner passed and merged s" "$rc $(state s)" "0 merged"
"$MQ" list --all > "$T/list.out" 2>&1
has "list shows merged items" "merged +s " "$T/list.out"

# ---- 10. stop after the train, and stop --now --------------------------------------------------
for x in t1 t2; do branch $x; echo "$x" > "$T/wt-$x/$x.txt"; commit $x; done
add t1 "feat(T1): t1"; add t2 "feat(T2): t2"
touch "$T/slow"; rm -f "$T/started"
FB_MQ_TRAIN=1 "$MQ" start "$R" "$IB" > /dev/null 2>&1
for _ in $(seq 1 100); do [[ -e $T/started ]] && break; sleep 0.1; done
"$MQ" stop > "$T/r10.out" 2>&1; rm -f "$T/slow"
"$R/scripts/bg.sh" wait merge-queue --max 60 > /dev/null 2>&1
check "stop: the current train merged, the next item still queued" "$(state t1) $(state t2)" "merged queued"
check "stop: no stop file left behind" "$([ -e "$R/.claude/merge-queue/stop" ] && echo left || echo none)" none
"$MQ" stop > "$T/r10b.out" 2>&1
has "stop without a runner: says so" "no runner running" "$T/r10b.out"
B0=$(head_); touch "$T/slow"; rm -f "$T/started"
"$MQ" start "$R" "$IB" > /dev/null 2>&1
for _ in $(seq 1 100); do [[ -e $T/started ]] && break; sleep 0.1; done
"$MQ" stop --now > /dev/null 2>&1
"$R/scripts/bg.sh" wait merge-queue --max 30 > /dev/null 2>&1; rm -f "$T/slow"
check "stop --now: run branch unchanged, item back to queued, locks free" "$(head_) $(state t2) $(lock_free) $(flock -n "$R/.claude/merge-queue.lock" true && echo free)" "$B0 queued free free"
"$MQ" run "$R" "$IB" > /dev/null 2>&1
check "stop --now: the next runner merges it" "$(state t2)" merged

# ---- 10b. no verdict, base fails, recovery after the fast-forward (review W10) -----------------
printf '#!/usr/bin/env bash\necho "OOM stub"; exit 137\n' > "$T/oom.sh"; chmod +x "$T/oom.sh"
branch v1; echo v1 > "$T/wt-v1/v1.txt"; commit v1; branch v2; echo v2 > "$T/wt-v2/v2.txt"; commit v2
add v1 "feat(V1): v1"; add v2 "feat(V2): v2"
B0=$(head_)
FB_MQ_TEST="$T/oom.sh" "$MQ" run "$R" "$IB" --once > "$T/r10c.out" 2>&1; rc=$?
check "no verdict (137): exit 1, nothing blamed, both queued, run branch unchanged" "$rc $(state v1) $(state v2) $(head_)" "1 queued queued $B0"
has "no verdict: noted" "suite gave no verdict \(rc 137" "$R/.claude/merge-queue/events.tsv"
echo BAD > "$R/bad.txt"; git -C "$R" add -A; git -C "$R" commit -qm "fix: break the run branch"
B0=$(head_)
"$MQ" run "$R" "$IB" --once > "$T/r10d.out" 2>&1; rc=$?
check "base fails: exit 1, nothing blamed, both queued, run branch unchanged" "$rc $(state v1) $(state v2) $(head_)" "1 queued queued $B0"
has "base fails: BASE_FAILS event" "BASE_FAILS" "$R/.claude/merge-queue/events.tsv"
git -C "$R" rm -q bad.txt; git -C "$R" commit -qm "fix: unbreak the run branch"
"$MQ" run "$R" "$IB" --once > "$T/r10e.out" 2>&1
check "after the base is fixed: both merged" "$(state v1) $(state v2)" "merged merged"
# a runner killed between its fast-forward and recording it: fake the ff marker for an item whose squash is on the run branch
branch w; echo w > "$T/wt-w/w.txt"; commit w; add w "feat(W): w"
wd=$(for d in "$R"/.claude/merge-queue/items/*/; do grep -qx slug=w "$d/meta" && echo "${d%/}"; done)
git -C "$R" merge --squash -q claude/w > /dev/null && git -C "$R" commit -qm "feat(W): w"
sed -i 's/^state=.*/state=train/' "$wd/meta"
printf 'base=%s tip=%s\n%s %s\n' "$(git -C "$R" rev-parse HEAD~1)" "$(head_)" "$wd" "$(head_)" > "$R/.claude/merge-queue/ff"
B0=$(head_)
"$MQ" run "$R" "$IB" --once > "$T/r10f.out" 2>&1
check "ff recovery: item recorded as merged, not merged twice, marker gone" "$(state w) $(head_) $([ -e "$R/.claude/merge-queue/ff" ] && echo left || echo gone)" "merged $B0 gone"

# ---- 11. bad input ----------------------------------------------------------------------------
"$MQ" add "$R" main claude/s "$T/msg-s" > /dev/null 2>&1; check "main as integration branch refused" "$?" 2
FB_SUBJECT_STRICT=1 "$MQ" add "$R" "$IB" claude/b "$(msg b 'no format')" > /dev/null 2>&1; check "strict subject refused" "$?" 8

echo "$fails failure(s)"
(( fails == 0 ))
