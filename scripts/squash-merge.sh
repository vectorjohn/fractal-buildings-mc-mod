#!/usr/bin/env bash
# Squash-merge a feature branch into the integration checkout. Holds
# <repo root>/.claude/merge.lock only for the merge itself, in this one process:
# clean-checkout check, squash, duplicate check, review-archive check, commit. Nothing is left
# half-merged: on a conflict or a failed commit the checkout is reset to HEAD
# before the lock is released. The lock is released when the commit is made
# (or the script exits or is killed), so a crashed caller can't hold it.
#
# --cleanup then removes the feature branch's worktree (`git worktree remove`, no --force) and deletes
# the branch (-D: a squash doesn't mark it merged), BEFORE any test starts. Refused for the integration
# branch, a claude/run-* branch or a branch not under claude/; if the worktree can't be removed (dirty),
# both are kept and a CLEANUP KEPT line says so. The exit code doesn't change.
#
# Post-merge tests (optional) then run WITHOUT the merge lock, never in the integration checkout
# itself: scripts/postmerge-run.sh runs scripts/snapshot-test.sh, which moves the persistent, warm
# worktree .claude/worktrees/postmerge to the new commit under its own lock (.claude/postmerge.lock; a
# second merge's tests wait their turn) and pins the logs in .claude/reports/postmerge-<slug>-evidence/.
# By default they run DETACHED (setsid nohup), so they outlive the caller's tool time limit: the script
# prints a `POSTMERGE PENDING ...` line (pid, log, result file, evidence folder) and exits 0 at once.
# Check them later with scripts/postmerge-status.sh <slug|sha> (PASS / FAIL / RUNNING: exit 0 / 1 / 3);
# every start and outcome is also a line in .claude/postmerge.log. --wait runs them in the foreground
# instead (the old behaviour, for to-main and anything that must not report before they end) and
# exits 5 when they fail.
#
# Usage (one Bash call; write the commit message to a file first):
#   timeout 3600 scripts/squash-merge.sh [--cleanup] [--wait] <integration checkout> <integration branch> \
#       claude/<slug> <message file> ['<post-merge test command>']
# A merge train: call it once per branch with no test command, then run
# scripts/snapshot-test.sh once on the last commit.
#
# Exit codes: 0 merged (sha printed; post-merge tests, if any, PENDING, or passed with --wait) |
#   2 checkout not clean / wrong branch / bad arguments |
#   3 merge conflict (reset; files printed) | 4 commit failed (reset) |
#   5 merged, but post-merge tests failed (--wait) or could not be started (commit kept) |
#   6 lock not acquired |
#   7 merge would bring back items archived in docs/REVIEW-ARCHIVE.md, or a
#     second copy of a docs/REVIEW.md item title (reset;
#     items printed; fix the feature branch, see scripts/review-check.sh) |
#   8 subject format refused (only with FB_SUBJECT_STRICT=1; nothing merged).
# Subject format (H1, docs/plans/workflow-history.md): the message's first line should be
# `type(CODE): summary`, type one of feat fix perf test docs build chore revert, scope the item code
# (else the area). A first line that doesn't match prints a `SUBJECT FORMAT` warning and merges anyway;
# FB_SUBJECT_STRICT=1 refuses it (exit 8, before the lock) instead, for when the warning period ends.
# Test hook: FB_POSTMERGE_DIR (state dir of postmerge-run.sh / postmerge-status.sh).
set -u
CLEANUP=0 WAIT=0
while [ $# -gt 0 ]; do
  case "$1" in
    --cleanup) CLEANUP=1; shift ;;
    --wait) WAIT=1; shift ;;
    -*) echo "unknown option $1"; exit 2 ;;
    *) break ;;
  esac
done
[ $# -ge 4 ] || { sed -n '2,44p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
I=$1 IB=$2 FB=$3 MSG=$4 TEST=${5:-}
HERE="$(cd "$(dirname "$0")" && pwd)"
[ -s "$MSG" ] || { echo "message file $MSG missing or empty"; exit 4; }
SUBJ=$(head -n1 "$MSG")
if ! printf '%s\n' "$SUBJ" | grep -Eq '^(feat|fix|perf|test|docs|build|chore|revert)(\(.+\))?: '; then
  if [ "${FB_SUBJECT_STRICT:-0}" = 1 ]; then
    echo "SUBJECT FORMAT REFUSED: first line '$SUBJ' is not 'type(CODE): summary' (feat fix perf test docs build chore revert)"; exit 8
  fi
  echo "SUBJECT FORMAT: first line '$SUBJ' is not 'type(CODE): summary' (feat fix perf test docs build chore revert); merging anyway"
fi
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

# Union merge of REVIEW.md can resurrect archived items (edited or ticked
# copies the exact DUPLICATE check above misses), or a stale second copy of an
# item with the same title. Refuse the merge if it does.
if ! "$HERE/review-check.sh" -C "$I" --source index --base HEAD; then
  echo "REVIEW CHECK FAILED: not merged; resetting. Remove the items above from"
  echo "docs/REVIEW.md on $FB (run scripts/review-check.sh there) and merge again."
  git -C "$I" reset --merge
  exit 7
fi

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

# Cleanup before any test, so a caller killed during the tests leaves nothing half-done.
if [ "$CLEANUP" = 1 ]; then
  if [ "$FB" = "$IB" ] || [[ $FB != claude/* || $FB == claude/run-* ]]; then
    echo "CLEANUP REFUSED for $FB (integration, run or non-claude branch); nothing removed"
  else
    FWT=$(git -C "$I" worktree list --porcelain | awk -v b="branch refs/heads/$FB" \
      '/^worktree /{w=substr($0,10)} $0==b{print w; exit}')
    if [ -n "$FWT" ] && ! git -C "$I" worktree remove "$FWT"; then
      echo "CLEANUP KEPT $FWT and $FB: worktree remove failed (above); remove both by hand"
    elif git -C "$I" branch -D "$FB" > /dev/null; then
      echo "CLEANUP removed ${FWT:-(no worktree)} and branch $FB"
    else
      echo "CLEANUP removed ${FWT:-(no worktree)}; branch $FB NOT deleted (above)"
    fi
  fi
fi

[ -n "$TEST" ] || exit 0
SLUG=${FB#claude/}
STATE="${FB_POSTMERGE_DIR:-$ROOT/.claude/postmerge}"
BASE="$STATE/${SHA:0:10}-$SLUG"
if [ "$WAIT" = 1 ]; then
  "$HERE/postmerge-run.sh" "$I" "$SHA" "$SLUG" "$TEST" \
    || { echo "POST-MERGE TESTS FAILED on $SHA (result $BASE.result)"; exit 5; }
  echo "POST-MERGE PASSED on $SHA (result $BASE.result)"
  exit 0
fi
mkdir -p "$STATE"
rm -f "$BASE.result"
# New session, no terminal, no inherited stdout/stderr pipe: the caller's tool call can return (or be
# killed at its time limit) without taking the tests with it.
setsid nohup "$HERE/postmerge-run.sh" "$I" "$SHA" "$SLUG" "$TEST" \
  > "$BASE.nohup" 2>&1 < /dev/null &
PID=$!   # replaced by the pid the runner writes into the result file
for _ in $(seq 1 50); do [ -s "$BASE.result" ] && break; sleep 0.1; done
if [ ! -s "$BASE.result" ]; then
  echo "POST-MERGE NOT STARTED on $SHA: no result file $BASE.result after 5 s; see $BASE.nohup"
  exit 5
fi
PID=$(sed -n "s/^pid=//p" "$BASE.result" | grep . || echo "$PID")
echo "POSTMERGE PENDING sha=$SHA slug=$SLUG pid=$PID log=$BASE.log result=$BASE.result evidence=$ROOT/.claude/reports/postmerge-$SLUG-evidence/"
echo "  check: $HERE/postmerge-status.sh $SLUG   (PASS exit 0, FAIL 1, RUNNING 3)"
exit 0
