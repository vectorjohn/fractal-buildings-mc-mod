#!/usr/bin/env bash
# Self-test for scripts/docs-sync.sh on throwaway git repos in a temp dir (a main checkout on
# `main`, an integration worktree on claude/run-x, a docs worktree on john/docs-feedback). Never
# touches the real worktrees, locks or SilverBullet; a few seconds.
#   scripts/docs-sync-selftest.sh      prints one line per check, exits 1 if any fails
set -uo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DS=$SRC/docs-sync.sh
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
fails=0 n=0
check() { if [[ $2 == "$3" ]]; then echo "ok   $1"; else echo "FAIL $1: got '$2', want '$3'"; fails=$((fails + 1)); fi; }
has() { if grep -q -- "$2" "$T/out"; then echo "ok   $1"; else echo "FAIL $1: no '$2' in:"; sed 's/^/     /' "$T/out"; fails=$((fails + 1)); fi; }

# setup: fresh repo R with worktrees C (run branch) and D (docs branch); sets R C D PM
setup() {
  n=$((n + 1)); R=$T/r$n
  git init -q -b main "$R"
  git -C "$R" config user.email selftest@example.invalid; git -C "$R" config user.name selftest
  mkdir -p "$R/docs"
  printf '# Review\n\n- [ ] **Open item** look\n' > "$R/docs/REVIEW.md"
  printf '# Review archive\n\n- [x] **Old item** done\n' > "$R/docs/REVIEW-ARCHIVE.md"
  printf 'line one\nline two\n' > "$R/docs/a.md"
  mkdir -p "$R/docs/runs"; printf "# Run ledger\n" > "$R/docs/runs/ledger.md"
  printf '/.claude/\n' > "$R/.gitignore"
  git -C "$R" add -A; git -C "$R" commit -qm base
  git -C "$R" branch claude/run-x; git -C "$R" branch john/docs-feedback
  C=$R/.claude/worktrees/run D=$R/.claude/worktrees/docs-feedback
  git -C "$R" worktree add -q "$C" claude/run-x; git -C "$R" worktree add -q "$D" john/docs-feedback
  PM=$R/.claude/postmerge; mkdir -p "$PM"
}
# sync [args...]: run docs-sync on the current repo; output in $T/out, exit code echoed
sync() { FB_POSTMERGE_DIR=$PM "$DS" --checkout "$C" --no-silverbullet "$@" > "$T/out" 2>&1; echo $?; }
commit_in() { git -C "$1" add -A; git -C "$1" commit -qm "$2"; }

# 1. nothing to do: exit 0, exactly one line
setup
check "quiet: exit 0" "$(sync)" 0
check "quiet: one line" "$(cat "$T/out")" "docs-sync: nothing new"

# 2. The user edits a doc: committed, merged (exit 10), docs fast-forwarded to the run branch
echo "@John 2026-10-29: why?" >> "$D/docs/a.md"
check "feedback: exit 10" "$(sync)" 10
check "feedback: commit subject" "$(git -C "$R" log -1 --format=%s john/docs-feedback^2)" "User feedback: docs/a.md"
has "feedback: merged line" "USER FEEDBACK MERGED into claude/run-x: $(git -C "$C" rev-parse HEAD)"
has "feedback: file list" "files: docs/a.md"
check "feedback: merge commit on run" "$(git -C "$C" log -1 --format=%s)" "Merge branch 'john/docs-feedback' into claude/run-x"
check "feedback: docs ff'd to run" "$(git -C "$D" rev-parse HEAD)" "$(git -C "$C" rev-parse HEAD)"
check "feedback: the user's text on run" "$(tail -n1 "$C/docs/a.md")" "@John 2026-10-29: why?"
check "feedback: second run quiet" "$(sync)" 0
check "feedback: second run line" "$(cat "$T/out")" "docs-sync: nothing new"

# 3. run branch moved: docs fast-forwarded, still exit 0 and one line
echo "new" > "$C/docs/b.md"; commit_in "$C" "run work"
check "ff: exit 0" "$(sync)" 0
check "ff: one line" "$(wc -l < "$T/out" | tr -d ' ')" 1
has "ff: nothing new" "^docs-sync: nothing new (fast-forwarded john/docs-feedback"
check "ff: docs at run" "$(git -C "$D" rev-parse HEAD)" "$(git -C "$C" rev-parse HEAD)"

# 4. main has commits the run branch lacks: exit 11, not merged by the script
echo "user on main" > "$R/docs/m.md"; commit_in "$R" "user note on main"
check "main: exit 11" "$(sync)" 11
has "main: line" "MAIN MOVED: main has 1 commit"
check "main: not merged" "$(git -C "$C" rev-list --count claude/run-x..main)" 1
git -C "$C" merge -q --no-ff -m "merge main" main
check "main merged: exit 0" "$(sync)" 0

# 5. conflict: run branch and the user edit the same line -> exit 1, merge aborted, all kept
setup
sed -i 's/line one/line ONE run/' "$C/docs/a.md"; commit_in "$C" "run edit"
RUNHEAD=$(git -C "$C" rev-parse HEAD)
sed -i 's/line one/line one -- @John no/' "$D/docs/a.md"
check "conflict: exit 1" "$(sync)" 1
has "conflict: names file" "merge of john/docs-feedback conflicted: docs/a.md"
check "conflict: run head unchanged" "$(git -C "$C" rev-parse HEAD)" "$RUNHEAD"
check "conflict: checkout clean" "$(git -C "$C" status --porcelain)" ""
check "conflict: no MERGE_HEAD" "$(test -e "$(git -C "$C" rev-parse --git-path MERGE_HEAD)" && echo yes || echo no)" no
check "conflict: the user's commit kept" "$(git -C "$D" log -1 --format=%s)" "User feedback: docs/a.md"
check "conflict: the user's text kept" "$(head -n1 "$D/docs/a.md")" "line one -- @John no"

# 6. review-check failure: the user's edit brings back an archived item -> exit 1, aborted
setup
printf -- '- [ ] **Old item** done\n' >> "$D/docs/REVIEW.md"
RUNHEAD=$(git -C "$C" rev-parse HEAD)
check "review-check: exit 1" "$(sync)" 1
has "review-check: line" "review-check failed"
check "review-check: run head unchanged" "$(git -C "$C" rev-parse HEAD)" "$RUNHEAD"
check "review-check: checkout clean" "$(git -C "$C" status --porcelain)" ""

# 7. dirty integration checkout: exit 1, nothing merged, the user's commit kept
setup
echo dirty >> "$C/docs/a.md"
echo "@John: x" >> "$D/docs/REVIEW.md"
check "dirty: exit 1" "$(sync)" 1
has "dirty: line" "is not clean"
check "dirty: the user committed" "$(git -C "$D" log -1 --format=%s)" "User feedback: docs/REVIEW.md"
check "dirty: run not merged" "$(git -C "$C" rev-list --count claude/run-x..john/docs-feedback)" 1

# 7b. only uncommitted docs/runs/ edits (the coordinator's ledger): merged, ledger left as it was
setup
echo "- in flight: X" >> "$C/docs/runs/ledger.md"; echo "# new run" > "$C/docs/runs/new.md"
echo "@John: ledger ok?" >> "$D/docs/a.md"
check "ledger dirty: exit 10" "$(sync)" 10
has "ledger dirty: merged line" "USER FEEDBACK MERGED into claude/run-x"
check "ledger dirty: merge has no docs/runs" "$(git -C "$C" diff --name-only HEAD^1 HEAD -- docs/runs)" ""
check "ledger dirty: edit kept" "$(tail -n1 "$C/docs/runs/ledger.md")" "- in flight: X"
check "ledger dirty: still uncommitted" "$(git -C "$C" status --porcelain -- docs/runs | paste -sd,)" " M docs/runs/ledger.md,?? docs/runs/new.md"
check "ledger dirty: the user's text on run" "$(tail -n1 "$C/docs/a.md")" "@John: ledger ok?"

# 7c. dirty ledger and the docs branch changed docs/runs/: exit 1, nothing merged, both kept
setup
echo "- coordinator line" >> "$C/docs/runs/ledger.md"
echo "@John: on the ledger" >> "$D/docs/runs/ledger.md"
RUNHEAD=$(git -C "$C" rev-parse HEAD)
check "ledger touched: exit 1" "$(sync)" 1
has "ledger touched: line" "would touch docs/runs/ .*docs/runs/ledger.md"
check "ledger touched: run head unchanged" "$(git -C "$C" rev-parse HEAD)" "$RUNHEAD"
check "ledger touched: coordinator edit kept" "$(tail -n1 "$C/docs/runs/ledger.md")" "- coordinator line"
check "ledger touched: the user committed" "$(git -C "$D" log -1 --format=%s)" "User feedback: docs/runs/ledger.md"
check "ledger touched: no MERGE_HEAD" "$(test -e "$(git -C "$C" rev-parse --git-path MERGE_HEAD)" && echo yes || echo no)" no

# 7d. dirty ledger and a conflict elsewhere: aborted, ledger edit kept
setup
sed -i 's/line one/line ONE run/' "$C/docs/a.md"; commit_in "$C" "run edit"
echo "- coordinator line" >> "$C/docs/runs/ledger.md"
sed -i 's/line one/line one -- @John no/' "$D/docs/a.md"
check "ledger conflict: exit 1" "$(sync)" 1
has "ledger conflict: names file" "merge of john/docs-feedback conflicted: docs/a.md"
check "ledger conflict: only the ledger dirty" "$(git -C "$C" status --porcelain | paste -sd,)" " M docs/runs/ledger.md"
check "ledger conflict: edit kept" "$(tail -n1 "$C/docs/runs/ledger.md")" "- coordinator line"

# 7e. a staged docs/runs/ edit still refuses (the merge commit would take it)
setup
echo "- staged" >> "$C/docs/runs/ledger.md"; git -C "$C" add docs/runs/ledger.md
echo "@John: z" >> "$D/docs/a.md"
check "ledger staged: exit 1" "$(sync)" 1
has "ledger staged: line" "has staged changes: docs/runs/ledger.md"
check "ledger staged: run not merged" "$(git -C "$C" rev-list --count claude/run-x..john/docs-feedback)" 1

# 8. post-merge FAIL / DIED: reported once (exit 12); old ones on the first run only recorded
setup
now=$(date -u +%FT%TZ)
printf 'status=FAIL\nsha=aaaaaaaaaa11\nslug=p6-old\nrc=1\nstart=2020-01-01T00:00:00Z\nend=2020-01-01T01:00:00Z\nlog=/nonexistent\n' > "$PM/aaaaaaaaaa-p6-old.result"
touch -d '2020-01-01' "$PM/aaaaaaaaaa-p6-old.result"
check "postmerge old: exit 0" "$(sync)" 0
printf 'status=FAIL\nsha=bbbbbbbbbb22\nslug=p6-new\nrc=1\nstart=%s\nend=%s\nlog=/nonexistent\n' "$now" "$now" > "$PM/bbbbbbbbbb-p6-new.result"
printf 'status=RUNNING\nsha=cccccccccc33\nslug=p6-dead\npid=999999999\nstart=%s\nlog=/nonexistent\n' "$now" > "$PM/cccccccccc-p6-dead.result"
check "postmerge new: exit 12" "$(sync)" 12
has "postmerge new: FAIL line" "POSTMERGE FAIL p6-new bbbbbbbbbb"
has "postmerge new: DIED line" "POSTMERGE DIED p6-dead cccccccccc"
check "postmerge new: old not reported" "$(grep -c p6-old "$T/out")" 0
check "postmerge seen: exit 0" "$(sync)" 0

# 9. exit priority: feedback (10) + main moved (11) + post-merge (12) -> 12, all printed
echo "x" > "$R/docs/m2.md"; commit_in "$R" "main again"
echo "@John: y" >> "$D/docs/a.md"
printf 'status=KILLED\nsha=dddddddddd44\nslug=p6-k\nrc=143\nstart=%s\nend=%s\nlog=/nonexistent\n' "$now" "$now" > "$PM/dddddddddd-p6-k.result"
check "priority: exit 12" "$(sync)" 12
has "priority: feedback printed" "USER FEEDBACK MERGED"
has "priority: main printed" "MAIN MOVED"
has "priority: killed printed" "POSTMERGE KILLED p6-k"

# 10. SilverBullet down: restart command run in the docs worktree; still down -> exit 1
setup
check "silverbullet: exit 1" "$(FB_SB_URL=http://127.0.0.1:9/ FB_SB_START='touch sb-started' FB_SB_WAIT=1 \
  FB_POSTMERGE_DIR=$PM "$DS" --checkout "$C" > "$T/out" 2>&1; echo $?)" 1
has "silverbullet: line" "SilverBullet down"
check "silverbullet: restart ran in docs worktree" "$(test -e "$D/sb-started" && echo yes || echo no)" yes

# 10b. the generated status pages (W12): STATUS.md and STATUS/ at the docs root are never feedback, even before
# .gitignore lists it; an edit beside it is committed without it
setup
echo "# Status (generated)" > "$D/STATUS.md"
mkdir -p "$D/STATUS"; echo "# Watchdog (copy)" > "$D/STATUS/watchdog.md"; echo "# Jobs (copy)" > "$D/STATUS/jobs.md"
check "status page: exit 0" "$(sync)" 0
check "status page: nothing new" "$(cat "$T/out")" "docs-sync: nothing new"
check "status page: no commit" "$(git -C "$D" rev-list --count claude/run-x..john/docs-feedback)" 0
echo "@John: real feedback" >> "$D/docs/a.md"
check "status page + edit: exit 10" "$(sync)" 10
check "status page + edit: only the edit" "$(git -C "$R" log -1 --format=%s john/docs-feedback^2)" "User feedback: docs/a.md"
check "status page + edit: page untracked" "$(git -C "$D" ls-files STATUS.md)" ""
check "status page + edit: page kept" "$(cat "$D/STATUS.md")" "# Status (generated)"
check "status page + edit: STATUS/ untracked" "$(git -C "$D" ls-files STATUS)" ""
check "status page + edit: STATUS/ kept" "$(cat "$D/STATUS/watchdog.md")" "# Watchdog (copy)"

# 11. defaults: run from inside the checkout with no arguments picks its branch
setup
check "defaults: exit 0" "$(cd "$C" && FB_POSTMERGE_DIR=$PM "$DS" --no-silverbullet > "$T/out" 2>&1; echo $?)" 0
check "defaults: line" "$(cat "$T/out")" "docs-sync: nothing new"

[ "$fails" = 0 ] && echo "docs-sync selftest: all passed" || echo "docs-sync selftest: $fails FAILED"
[ "$fails" = 0 ]
