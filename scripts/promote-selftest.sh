#!/usr/bin/env bash
# Self-test for scripts/promote.sh on throwaway repos (a few seconds).
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
t=$(mktemp -d); trap 'rm -rf "$t"' EXIT
fail() { echo "promote-selftest: FAIL: $*" >&2; exit 1; }
c() { git add -A; git commit -q -m "$1"; }
hist() { git log --reverse --format=%s "$1" | tr '\n' '|'; }
newrepo() { # fresh repo in $t/$1 with promote.sh and one commit on main
  mkdir -p "$t/$1"; cd "$t/$1"; git init -q -b main; git config user.name T; git config user.email t@t
  mkdir -p scripts docs/runs docs/log docs/plans mod/src/main
  cp "$here/promote.sh" scripts/; echo base > mod/src/main/A.java; echo l0 > docs/runs/ledger.md; c "init"
}

newrepo r1
git checkout -q -b claude/run-x
echo l1 >> docs/runs/ledger.md; c "Ledger: start"
echo a1 >> mod/src/main/A.java; echo log > docs/log/a.md; c "6.1 ALPHA: first feature"
echo l2 >> docs/runs/ledger.md; echo r > docs/ROADMAP.md; c "Ledger: ALPHA merged"
echo p > docs/plans/beta.md; c "BETA: plan"
git checkout -q -b john/docs-feedback; echo j > docs/STANDARDS.md; c "User feedback: docs/STANDARDS.md"
git checkout -q claude/run-x; git merge -q --no-ff john/docs-feedback -m "Merge branch 'john/docs-feedback' into claude/run-x"
echo b1 >> mod/src/main/A.java; c "BETA: faster lookups"
echo l3 >> docs/runs/ledger.md; c "Ledger: BETA merged"

# 1: dry run classifies and folds; tree equal outside docs/runs; main untouched.
main0=$(git rev-parse main)
scripts/promote.sh --dry-run --run claude/run-x --summary "$t/s1.md" >/dev/null
[ "$(git rev-parse main)" = "$main0" ] || fail "main moved"
got=$(hist main..claude/promote-dryrun)
want="feat(ALPHA): first feature|docs(BETA): plan|perf(BETA): faster lookups|"
[ "$got" = "$want" ] || fail "history: $got"
grep -q 'tree check: PASS' "$t/s1.md" || fail "tree check"
git diff --quiet main claude/promote-dryrun -- docs/runs || fail "docs/runs changed on promote branch"
git log -1 --format=%B claude/promote-dryrun | grep -q "^Promoted-from: $(git rev-parse claude/run-x)$" || fail "trailer"
scripts/promote.sh --dry-run --run claude/run-x >/dev/null 2>&1 && fail "overwrote an existing branch without --replace"

# 2: land it, merge main back, add work: the next promotion is incremental.
git checkout -q main; git merge -q --ff-only claude/promote-dryrun
git checkout -q claude/run-x; git merge -q --no-ff main -m "Merge main into claude/run-x"
echo g1 >> mod/src/main/A.java; c "GAMMA: third"
git checkout -q main
scripts/promote.sh --run claude/run-x --branch promote/next >/dev/null
got=$(hist main..promote/next)
[ "$got" = "feat(GAMMA): third|" ] || fail "incremental: $got"

# 3: refuses when base is not an ancestor of the run branch.
echo x > other; c "direct commit on main"
scripts/promote.sh --run claude/run-x --branch promote/x >/dev/null 2>&1 && fail "accepted a diverged base"

# 4 (b): a docs-feedback merge on the run branch after a code commit gets its own
# docs commit named after the user's files; one touching only bookkeeping folds.
newrepo r4
git checkout -q -b claude/run-x
echo a1 >> mod/src/main/A.java; c "ALPHA: first feature"
git checkout -q -b john/docs-feedback; echo j > docs/STANDARDS.md; mkdir -p docs/design; echo k > docs/design/x.md
c "User feedback: docs/STANDARDS.md docs/design/x.md"
git checkout -q claude/run-x; git merge -q --no-ff john/docs-feedback -m "Merge branch 'john/docs-feedback' into claude/run-x"
git checkout -q john/docs-feedback; git merge -q --ff-only claude/run-x   # docs-sync.sh fast-forwards it
echo q > docs/REVIEW.md; c "User feedback: docs/REVIEW.md"
git checkout -q claude/run-x; git merge -q --no-ff john/docs-feedback -m "Merge branch 'john/docs-feedback' into claude/run-x"
echo z >> mod/src/main/A.java; c "fix(GAMMA): keep the thing"   # semantic subjects (verifier.md, H1) pass through
git checkout -q -b claude/delta; echo d > mod/src/main/D.java; c "DELTA: work"
git checkout -q claude/run-x; git merge -q --no-ff claude/delta -m "Merge claude/delta: feat(DELTA): a new thing"
scripts/promote.sh --run claude/run-x --summary "$t/s4.md" >/dev/null
got=$(hist main..promote/run-x)
[ "$got" = "feat(ALPHA): first feature|docs: review notes on STANDARDS.md, x.md|fix(GAMMA): keep the thing|feat(DELTA): a new thing|" ] || fail "docs-feedback: $got"
grep -q "Needs a look (0)" "$t/s4.md" || fail "semantic subjects flagged"
git log -1 --format=%B promote/run-x~2 | grep -q "plus 1 bookkeeping commits" || fail "bookkeeping-only feedback merge not folded"
grep -q 'tree check: PASS' "$t/s4.md" || fail "docs-feedback tree check"

# 5: a main commit the run had not merged yet is kept by the early promoted
# commits (not reverted by them and re-added at the run's merge of main); a
# file both sides changed takes the run's side until that merge. A commit
# that only touches the overrides file is bookkeeping.
newrepo r5
echo one > conf.txt; c "conf"
git checkout -q -b claude/run-x
echo a1 >> mod/src/main/A.java; echo run > conf.txt; c "ALPHA: first feature"
git checkout -q main; echo tool > scripts/tool.sh; echo main > conf.txt; c "a tool on main"
git checkout -q claude/run-x
echo b1 >> mod/src/main/A.java; c "BETA: second"
git merge -q --no-ff main -m "Merge main into claude/run-x" >/dev/null 2>&1 || true
echo run+main > conf.txt; git add -A; git commit -q --no-edit
echo '# none' > scripts/promote-overrides.tsv; c "promote overrides for the to-main"
git checkout -q main
scripts/promote.sh --run claude/run-x --summary "$t/s5.md" >/dev/null
[ "$(hist main..promote/run-x)" = "feat(ALPHA): first feature|feat(BETA): second|" ] || fail "unmerged main: $(hist main..promote/run-x)"
[ "$(git show promote/run-x~1:scripts/tool.sh)" = tool ] || fail "first promoted commit reverted main's tool.sh"
[ "$(git show promote/run-x~1:conf.txt)" = run ] || fail "conflicted file did not take the run's side"
git diff --quiet main promote/run-x~1 -- scripts || fail "first promoted commit changed scripts/"
grep -q 'not merged yet (kept, not reverted): 1' "$t/s5.md" || fail "kept count: $(grep 'not merged yet' "$t/s5.md")"
grep -q "of those, 1 took the run's side .*: conf.txt$" "$t/s5.md" || fail "conflict line: $(grep 'of those' "$t/s5.md")"
grep -q 'tree check: PASS' "$t/s5.md" || fail "unmerged-main tree check"

# 6 (a): rewrite main, reconnect the run branch with `merge -s ours main`, promote again.
newrepo r6
echo l1 >> docs/runs/ledger.md; c "Ledger: phase 0"
echo a1 >> mod/src/main/A.java; c "Merge run phase0: ALPHA first"
echo n > docs/NOTES.md; c "NOTES: a fact"
git checkout -q -b claude/run-y
echo l2 >> docs/runs/ledger.md; c "Ledger: start"
echo d1 >> mod/src/main/A.java; c "DELTA: fourth"
old=$(git rev-parse main) root=$(git rev-list --max-parents=0 main)
scripts/promote.sh --base "$root" --run main --branch promote/rewrite --summary "$t/s6a.md" >/dev/null
grep -q 'tree check: PASS' "$t/s6a.md" || fail "rewrite tree check"
git diff --quiet "$old" promote/rewrite -- . ':(exclude)docs/runs' || fail "rewrite differs outside docs/runs"
git update-ref refs/heads/main promote/rewrite "$old"
scripts/promote.sh --run claude/run-y --branch promote/y0 >/dev/null 2>&1 && fail "promoted before the reconnect"
before=$(git rev-parse HEAD^{tree})
git merge -q -s ours main -m "Reconnect to rewritten main (no file changes)"
[ "$(git rev-parse HEAD^{tree})" = "$before" ] || fail "reconnect changed files"
scripts/promote.sh --run claude/run-y --branch promote/y --summary "$t/s6b.md" >/dev/null
got=$(hist main..promote/y)
[ "$got" = "feat(DELTA): fourth|" ] || fail "after rewrite: $got"
[ "$(git rev-parse promote/y^)" = "$(git rev-parse main)" ] || fail "not stacked on the rewritten main"
grep -q 'tree check: PASS' "$t/s6b.md" || fail "after-rewrite tree check"
grep -q 'not merged yet (kept, not reverted): 0' "$t/s6b.md" || fail "after rewrite: unexpected merge-tree"

# 7 (c): overrides. A row for a walked commit renames it; rows for already
# promoted commits or commits the run cannot reach are skipped; a row naming
# a commit outside the walked range that the run reaches is refused.
cd "$t/r6"
git checkout -q -b claude/other "$old"; echo o > other.txt; c "OTHER: elsewhere"; other=$(git rev-parse HEAD)
git checkout -q claude/run-y
git checkout -q -b feat/side; echo s >> mod/src/main/A.java; c "side work"; side=$(git rev-parse HEAD)
git checkout -q claude/run-y; git merge -q --no-ff feat/side -m "Merge feat/side"; m=$(git rev-parse HEAD)
git checkout -q main
delta=$(git log --format='%H %s' claude/run-y | awk '$2=="DELTA:" {print $1}')
oldnotes=$(git log --format='%H %s' "$old" | awk '$2=="NOTES:" {print $1}')
printf '# comment\n%s\t-\tfeat(core): the fourth thing\n%s\t-\tfeat(old): already promoted\n%s\tdocs\n%s\t-\tfix(side): side work\n' \
  "${delta:0:8}" "${oldnotes:0:8}" "${other:0:8}" "${m:0:8}" > "$t/ov.tsv"
scripts/promote.sh --run claude/run-y --branch promote/ov --overrides "$t/ov.tsv" --summary "$t/s7.md" >/dev/null
got=$(hist main..promote/ov)
[ "$got" = "feat(core): the fourth thing|fix(side): side work|" ] || fail "overrides: $got"
grep -q 'overrides: .*(2 applied; skipped: 1 already promoted or base history, 1 for other runs)' "$t/s7.md" || fail "override counts: $(grep overrides: "$t/s7.md")"
printf '%s\t-\tfeat(side): wrong commit\n' "${side:0:8}" >> "$t/ov.tsv"
out=$(scripts/promote.sh --run claude/run-y --branch promote/ov2 --overrides "$t/ov.tsv" 2>&1) && fail "accepted an override outside the walked range"
grep -q 'outside the walked range' <<<"$out" || fail "refusal message: $out"
git show-ref -q --verify refs/heads/promote/ov2 && fail "wrote a branch despite the refusal"
printf '%s\tcod\n' "${delta:0:8}" > "$t/ov3.tsv"
scripts/promote.sh --run claude/run-y --branch promote/ov3 --overrides "$t/ov3.tsv" >/dev/null 2>&1 && fail "accepted a bad class"
printf '%s\tbk\t%s\n' "${delta:0:8}" "feat(x): unused" > "$t/ov4.tsv"   # bk folds it: the message heads nothing
scripts/promote.sh --run claude/run-y --branch promote/ov4 --overrides "$t/ov4.tsv" --summary "$t/s7b.md" >/dev/null
grep -q 'override message unused' "$t/s7b.md" || fail "unused override message not reported"
echo "promote-selftest: PASS"
