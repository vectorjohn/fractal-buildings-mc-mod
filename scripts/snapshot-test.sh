#!/usr/bin/env bash
# Run test commands on a detached snapshot worktree of one commit, so tests of a shared branch never
# run in its checkout (where every Gradle run would need the merge lock). Used by squash-merge.sh for
# post-merge tests, and for the batched heavy runs on the run branch.
#
#   scripts/snapshot-test.sh <any checkout of the repo> <rev> <label> '<command>' ['<command>' ...]
#   e.g. scripts/snapshot-test.sh .claude/worktrees/run-phase6 HEAD heavy-batch \
#          scripts/client-test.sh scripts/flythrough.sh scripts/perf.sh
#
# Creates <main root>/.claude/worktrees/postmerge-<short sha>[-n] at <rev> (detached) and runs each
# command there in turn (all of them, even after a failure) through scripts/evidence.sh, so each one's
# logs, screenshots and EVIDENCE.md land in their own folder under .claude/reports/<label>-evidence/.
# Then removes the worktree (FB_KEEP_SNAPSHOT=1 keeps it). A fresh worktree builds from cold (and runs
# npm ci for the viewer), so expect a few minutes more than in a warm one.
# Exit: 0 all passed, else the first failing command's exit code; 2 bad args or no worktree.
set -uo pipefail
[ $# -ge 4 ] || { sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
SRC=$1 REV=$2 LABEL=$3; shift 3
ROOT=$(dirname "$(git -C "$SRC" rev-parse --path-format=absolute --git-common-dir)") || exit 2
SHA=$(git -C "$SRC" rev-parse --verify "$REV^{commit}") || exit 2
WT="$ROOT/.claude/worktrees/postmerge-${SHA:0:10}"
n=1; while [ -e "$WT" ]; do WT="$ROOT/.claude/worktrees/postmerge-${SHA:0:10}-$n"; n=$((n+1)); done
git -C "$ROOT" worktree add --detach "$WT" "$SHA" > /dev/null 2>&1 || { echo "could not create $WT"; exit 2; }
echo "SNAPSHOT $WT at $SHA"
# The evidence script of the snapshot's own commit, if it has one; else the one next to this script.
EV="$WT/scripts/evidence.sh"; [ -x "$EV" ] || EV="$(cd "$(dirname "$0")" && pwd)/evidence.sh"
RC=0
for CMD in "$@"; do
  echo "RUN $CMD"
  (cd "$WT" && "$EV" "$LABEL" bash -c "$CMD"); rc=$?
  if [ "$rc" != 0 ]; then echo "FAILED ($rc): $CMD"; [ "$RC" = 0 ] && RC=$rc; fi
done
if [ "${FB_KEEP_SNAPSHOT:-0}" = 1 ]; then
  echo "kept $WT (remove: git -C $ROOT worktree remove --force $WT)"
else
  git -C "$ROOT" worktree remove --force "$WT" || echo "could not remove $WT; remove it by hand"
fi
exit $RC
