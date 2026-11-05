#!/usr/bin/env bash
# Run test commands on a snapshot of one commit, so tests of a shared branch never run in its checkout
# (where every Gradle run would need the merge lock). Used by squash-merge.sh for post-merge tests, and
# for the batched heavy runs on the run branch.
#
#   scripts/snapshot-test.sh [--name N | --cold] <any checkout of the repo> <rev> <label> '<cmd>' ['<cmd>' ...]
#   e.g. scripts/snapshot-test.sh .claude/worktrees/run-phase6 HEAD postmerge-x 'scripts/test-all.sh --no-client'
#        scripts/snapshot-test.sh --name heavy .claude/worktrees/run-phase6 HEAD heavy-batch \
#          scripts/client-test.sh scripts/flythrough.sh scripts/perf.sh
#
# Warm (default): one persistent detached worktree <main root>/.claude/worktrees/<N> (default N=postmerge, or
# $FB_SNAPSHOT_NAME, which squash-merge.sh passes through),
# reused across runs and guarded by <main root>/.claude/snapshot-<N>.lock (not the merge lock; a second
# caller waits its turn). N=postmerge keeps its old lock name, .claude/postmerge.lock, so older copies of
# this script and squash-merge.sh on other branches still share it. The snapshot- prefix keeps the lock
# apart from the machine-wide ones: it used to be .claude/<N>.lock, so --name heavy held heavy.lock and
# every heavy command in the batch waited on it forever. A name whose worktree is on a branch (an agent's
# or the run's checkout) is refused, as is one that would map to the heavy, merge or suite locks. Each run moves it to <rev> with `checkout --detach -f` + `clean -fd`: tracked files match the
# commit exactly and untracked files go, but ignored build output stays (Gradle build/ and .gradle/,
# viewer/node_modules), so Gradle's up-to-date checks reuse what the source change didn't touch. If
# viewer/package-lock.json changed, node_modules is deleted (test-all.sh then runs npm ci).
# --cold: a fresh worktree .claude/worktrees/postmerge-<sha>, removed afterwards (FB_KEEP_SNAPSHOT=1 keeps it).
#
# Each command runs in turn (all of them, even after a failure) through scripts/evidence.sh, so its logs,
# screenshots and EVIDENCE.md land in their own folder under .claude/reports/<label>-evidence/.
# Exit: 0 all passed, else the first failing command's exit code; 2 bad args or no worktree; 6 lock wait
# timed out (FB_SNAPSHOT_WAIT seconds, default 10800).
# Suite slots: scripts/test-all.sh takes a machine-wide suite slot for its fast layers itself (FB_SUITE_SLOTS);
# pass a bare full Gradle run as 'scripts/suite.sh ./gradlew build' so it takes one too. Heavy commands take
# the heavy lock instead (or go to the worker box: 'scripts/remote.sh -- scripts/client-test.sh').
set -uo pipefail
usage() { sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
NAME=${FB_SNAPSHOT_NAME:-postmerge} COLD=0
while [ $# -gt 0 ]; do
  case "$1" in
    --name) NAME=$2; shift 2 ;;
    --cold) COLD=1; shift ;;
    -*) usage ;;
    *) break ;;
  esac
done
[ $# -ge 4 ] || usage
case "$NAME" in *[!a-zA-Z0-9_-]*|'') echo "bad --name"; exit 2 ;; esac
SRC=$1 REV=$2 LABEL=$3; shift 3
ROOT=$(dirname "$(git -C "$SRC" rev-parse --path-format=absolute --git-common-dir)") || exit 2
SHA=$(git -C "$SRC" rev-parse --verify "$REV^{commit}") || exit 2

if [ "$COLD" = 1 ]; then
  WT="$ROOT/.claude/worktrees/postmerge-${SHA:0:10}"
  n=1; while [ -e "$WT" ]; do WT="$ROOT/.claude/worktrees/postmerge-${SHA:0:10}-$n"; n=$((n+1)); done
  git -C "$ROOT" worktree add --detach "$WT" "$SHA" > /dev/null 2>&1 || { echo "could not create $WT"; exit 2; }
else
  WT="$ROOT/.claude/worktrees/$NAME"
  if [ "$NAME" = postmerge ]; then LOCKF="$ROOT/.claude/postmerge.lock"; else LOCKF="$ROOT/.claude/snapshot-$NAME.lock"; fi
  case "${LOCKF##*/}" in
    heavy.lock|merge.lock|suite.lock*) echo "--name $NAME would take the machine-wide ${LOCKF##*/}"; exit 2 ;;
  esac
  if [ -e "$WT/.git" ] && git -C "$WT" symbolic-ref -q HEAD > /dev/null 2>&1; then
    echo "--name $NAME: $WT is on branch $(git -C "$WT" symbolic-ref --short HEAD), not a snapshot; pick another name"
    exit 2
  fi
  mkdir -p "$ROOT/.claude"; exec 8>>"$LOCKF"
  echo "waiting for $LOCKF ..."
  # holder note, a waiter line every 5 min, refusal (exit 8) when an ancestor holds it (scripts/lib/locks.sh)
  . "$(cd "$(dirname "$0")" && pwd)/lib/locks.sh"
  fb_lock_take 8 "$LOCKF" "snapshot-test --name $NAME $LABEL at ${SHA:0:10}" "${FB_SNAPSHOT_WAIT:-10800}"; lrc=$?
  [ "$lrc" = 0 ] || { echo "snapshot lock $NAME not acquired"; exit "$lrc"; }
  if [ -e "$WT/.git" ]; then
    OLD=$(git -C "$WT" rev-parse HEAD 2>/dev/null || echo none)
    git -C "$WT" checkout -q --detach -f "$SHA" || { echo "could not move $WT to $SHA"; exit 2; }
    git -C "$WT" clean -q -fd   # no -x: ignored build output and node_modules stay warm
    if [ "$OLD" = none ] || [ -n "$(git -C "$WT" diff --name-only "$OLD" "$SHA" -- viewer/package-lock.json)" ]; then
      rm -rf "$WT/viewer/node_modules"
    fi
  else
    git -C "$ROOT" worktree prune
    git -C "$ROOT" worktree add --detach "$WT" "$SHA" > /dev/null 2>&1 || { echo "could not create $WT"; exit 2; }
  fi
fi
echo "SNAPSHOT $WT at $SHA"
# The evidence script of the snapshot's own commit, if it has one; else the one next to this script.
EV="$WT/scripts/evidence.sh"; [ -x "$EV" ] || EV="$(cd "$(dirname "$0")" && pwd)/evidence.sh"
RC=0
for CMD in "$@"; do
  echo "RUN $CMD"
  (cd "$WT" && "$EV" "$LABEL" bash -c "$CMD") 8>&-; rc=$?
  if [ "$rc" != 0 ]; then echo "FAILED ($rc): $CMD"; [ "$RC" = 0 ] && RC=$rc; fi
done
if [ "$COLD" = 1 ]; then
  if [ "${FB_KEEP_SNAPSHOT:-0}" = 1 ]; then
    echo "kept $WT (remove: git -C $ROOT worktree remove --force $WT)"
  else
    git -C "$ROOT" worktree remove --force "$WT" || echo "could not remove $WT; remove it by hand"
  fi
fi
[ -n "${LOCKF:-}" ] && fb_lock_note_clear "$LOCKF"
exit $RC
