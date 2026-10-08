#!/usr/bin/env bash
# Squash-merge a feature branch into the integration checkout, holding
# <repo root>/.claude/merge.lock for the WHOLE sequence in this one process:
# clean-checkout check, squash, duplicate check, commit, optional post-merge
# tests. The lock is released when the script exits (or is killed), so a
# crashed caller can't hold it. Nothing is left half-merged: on a conflict or
# a failed commit the checkout is reset to HEAD before the lock is released.
#
# Usage (one Bash call; write the commit message to a file first):
#   timeout 3600 scripts/squash-merge.sh <integration checkout> <integration branch> \
#       claude/<slug> <message file> ['<post-merge test command>']
#
# Exit codes: 0 merged (sha printed) | 2 checkout not clean / wrong branch |
#   3 merge conflict (reset; files printed) | 4 commit failed (reset) |
#   5 merged, but post-merge tests failed (commit kept) | 6 lock not acquired.
set -u
I=$1 IB=$2 FB=$3 MSG=$4 TEST=${5:-}
[ -s "$MSG" ] || { echo "message file $MSG missing or empty"; exit 4; }
ROOT=$(dirname "$(git -C "$I" rev-parse --path-format=absolute --git-common-dir)")
exec 9>"$ROOT/.claude/merge.lock"
flock -w 1800 9 || { echo "merge lock not acquired in 30 min"; exit 6; }

dirty=$(git -C "$I" status --porcelain --untracked-files=no -- . ':!docs/runs')
[ -z "$dirty" ] || { echo "integration checkout not clean:"; echo "$dirty"; exit 2; }
[ "$(git -C "$I" branch --show-current)" = "$IB" ] \
  || { echo "integration checkout not on $IB"; exit 2; }

if ! git -C "$I" merge --squash "$FB"; then
  echo "MERGE CONFLICT in:"
  git -C "$I" diff --name-only --diff-filter=U
  git -C "$I" reset --merge
  git -C "$I" status --porcelain --untracked-files=no -- . ':!docs/runs'
  exit 3
fi

# Union-merged files: report added lines that now occur more than once.
for f in docs/research/INDEX.md docs/NOTES.md docs/ASSUMPTIONS.md docs/REVIEW.md; do
  git -C "$I" diff --cached -U0 -- "$f" | sed -n 's/^+\([^+].*\)$/\1/p' \
    | sort -u | while IFS= read -r line; do
        n=$(git -C "$I" show ":$f" | grep -cxF -- "$line")
        [ "$n" -gt 1 ] && echo "DUPLICATE in $f ($n x): $line"
      done
done

# Commit only the index (never -a); docs/runs edits stay uncommitted.
if ! git -C "$I" commit -F "$MSG"; then
  echo "commit failed; resetting"
  git -C "$I" reset --merge
  exit 4
fi
echo "MERGED $(git -C "$I" rev-parse HEAD)"

if [ -n "$TEST" ]; then
  (cd "$I" && bash -c "$TEST") || { echo "POST-MERGE TESTS FAILED"; exit 5; }
fi
exit 0
