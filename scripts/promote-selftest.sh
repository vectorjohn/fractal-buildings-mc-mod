#!/usr/bin/env bash
# Self-test for scripts/promote.sh on a throwaway repo (a few seconds).
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
t=$(mktemp -d); trap 'rm -rf "$t"' EXIT
cd "$t"; git init -q -b main; git config user.name T; git config user.email t@t
mkdir -p scripts docs/runs docs/log docs/plans mod/src/main
fail() { echo "promote-selftest: FAIL: $*" >&2; exit 1; }
c() { git add -A; git commit -q -m "$1"; }
cp "$here/promote.sh" scripts/; echo base > mod/src/main/A.java; echo l0 > docs/runs/ledger.md; c "init"
git checkout -q -b claude/run-x
echo l1 >> docs/runs/ledger.md; c "Ledger: start"
echo a1 >> mod/src/main/A.java; echo log > docs/log/a.md; c "6.1 ALPHA: first feature"
echo l2 >> docs/runs/ledger.md; echo r > docs/ROADMAP.md; c "Ledger: ALPHA merged"
echo p > docs/plans/beta.md; c "BETA: plan"
git checkout -q -b john/docs-feedback; echo j > docs/STANDARDS.md; c "John feedback: docs/STANDARDS.md"
git checkout -q claude/run-x; git merge -q --no-ff john/docs-feedback -m "Merge branch 'john/docs-feedback' into claude/run-x"
echo b1 >> mod/src/main/A.java; c "BETA: faster lookups"
echo l3 >> docs/runs/ledger.md; c "Ledger: BETA merged"

# 1: dry run classifies and folds; tree equal outside docs/runs; main untouched.
main0=$(git rev-parse main)
scripts/promote.sh --dry-run --run claude/run-x --summary "$t/s1.md" >/dev/null
[ "$(git rev-parse main)" = "$main0" ] || fail "main moved"
got=$(git log --reverse --format=%s main..claude/promote-dryrun | tr '\n' '|')
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
got=$(git log --reverse --format=%s main..promote/next | tr '\n' '|')
[ "$got" = "feat(GAMMA): third|" ] || fail "incremental: $got"

# 3: refuses when base is not an ancestor of the run branch.
echo x > other; c "direct commit on main"
scripts/promote.sh --run claude/run-x --branch promote/x >/dev/null 2>&1 && fail "accepted a diverged base"
echo "promote-selftest: PASS"
