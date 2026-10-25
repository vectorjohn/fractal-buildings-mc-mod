#!/usr/bin/env bash
# Squash-merge a feature branch into the integration checkout. Holds
# <repo root>/.claude/merge.lock only for the merge itself, in this one process:
# clean-checkout check, squash, duplicate check, commit. Nothing is left
# half-merged: on a conflict or a failed commit the checkout is reset to HEAD
# before the lock is released. The lock is released when the commit is made
# (or the script exits or is killed), so a crashed caller can't hold it.
#
# Post-merge tests (optional) then run WITHOUT the merge lock, never in the
# integration checkout itself: scripts/snapshot-test.sh moves the persistent,
# warm worktree .claude/worktrees/postmerge to the new commit under its own
# lock (.claude/postmerge.lock; a second merge's tests wait their turn) and
# pins the logs in .claude/reports/postmerge-<slug>-evidence/.
#
# Usage (one Bash call; write the commit message to a file first):
#   timeout 3600 scripts/squash-merge.sh <integration checkout> <integration branch> \
#       claude/<slug> <message file> ['<post-merge test command>']
# A merge train: call it once per branch with no test command, then run
# scripts/snapshot-test.sh once on the last commit.
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
SHA=$(git -C "$I" rev-parse HEAD)
echo "MERGED $SHA"

# Release the lock now (flock -u drops it even if a child inherited fd 9).
flock -u 9; exec 9>&-

if [ -n "$TEST" ]; then
  "$(cd "$(dirname "$0")" && pwd)/snapshot-test.sh" "$I" "$SHA" "postmerge-${FB#claude/}" "$TEST" \
    || { echo "POST-MERGE TESTS FAILED on $SHA"; exit 5; }
fi
exit 0
