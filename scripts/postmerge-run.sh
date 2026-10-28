#!/usr/bin/env bash
# Run one merge's post-merge tests and record the outcome in a result file, so the outcome survives the
# caller: scripts/squash-merge.sh starts this detached (setsid nohup) after the squash commit, and
# scripts/postmerge-status.sh reads the result file. Not meant to be called by hand (but it can be).
#
#   scripts/postmerge-run.sh <integration checkout> <sha> <slug> '<test command>' ['<test command>' ...]
#
# Runs scripts/snapshot-test.sh <checkout> <sha> postmerge-<slug> <cmds...> (the warm postmerge snapshot,
# its own lock, evidence under .claude/reports/postmerge-<slug>-evidence/).
# State (all under <main root>/.claude/, gitignored):
#   postmerge/<sha10>-<slug>.result  key=value lines; status=RUNNING, then PASS or FAIL (or KILLED on
#                                     SIGTERM/SIGINT); rc, pid, log, evidence, start, end, cmd
#   postmerge/<sha10>-<slug>.log     the snapshot-test.sh output
#   postmerge.log                    one line per START / PASS / FAIL / KILLED / DIED, for grep
# Exit: the test command's exit code (0 PASS).
# Test hooks: FB_POSTMERGE_DIR (state dir; postmerge.log goes next to it), FB_POSTMERGE_SNAPSHOT (the
# command run in place of snapshot-test.sh, same arguments).
set -uo pipefail
[ $# -ge 4 ] || { sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
I=$1 SHA=$2 SLUG=$3; shift 3
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT=$(dirname "$(git -C "$I" rev-parse --path-format=absolute --git-common-dir)") || exit 2
DIR=${FB_POSTMERGE_DIR:-$ROOT/.claude/postmerge}
PLOG="$(dirname "$DIR")/postmerge.log"
SNAP=${FB_POSTMERGE_SNAPSHOT:-$HERE/snapshot-test.sh}
mkdir -p "$DIR"
BASE="$DIR/${SHA:0:10}-$SLUG"
RES="$BASE.result" LOG="$BASE.log"
EVBASE="$ROOT/.claude/reports/postmerge-$SLUG-evidence"
START=$(date -u +%FT%TZ)
CMDS=$(printf '%s; ' "$@"); CMDS=${CMDS%; }

# Write the result file atomically (tmp + mv), so a reader never sees half of it.
write() { # <status> [rc] [end]
  local ev
  ev=$(sed -n 's/^EVIDENCE \(.*\) (exit .*/\1/p' "$LOG" 2>/dev/null | paste -sd' ')
  {
    echo "status=$1"
    echo "sha=$SHA"
    echo "slug=$SLUG"
    echo "rc=${2:-}"
    echo "pid=$$"
    echo "start=$START"
    echo "end=${3:-}"
    echo "log=$LOG"
    echo "evidence=${ev:-$EVBASE/ (not yet written)}"
    echo "cmd=$CMDS"
  } > "$RES.tmp.$$" && mv -f "$RES.tmp.$$" "$RES"
}
logline() { # <status> [rc]
  echo "$(date -u +%FT%TZ) $1 sha=${SHA:0:10} slug=$SLUG${2:+ rc=$2} result=$RES" >> "$PLOG"
}

: > "$LOG"
write RUNNING
logline START
CHILD=
# shellcheck disable=SC2329 # called through trap
on_signal() {
  trap '' TERM INT
  write KILLED 143 "$(date -u +%FT%TZ)"; logline KILLED 143
  # Detached (setsid), this script leads its own process group: stop the whole test tree with it.
  if [ "$(ps -o pgid= -p $$ | tr -d ' ')" = "$$" ]; then kill -TERM -- "-$$" 2>/dev/null
  elif [ -n "$CHILD" ]; then kill -TERM "$CHILD" 2>/dev/null; fi
  exit 143
}
trap on_signal TERM INT

"$SNAP" "$I" "$SHA" "postmerge-$SLUG" "$@" >> "$LOG" 2>&1 < /dev/null &
CHILD=$!
wait "$CHILD"; RC=$?
# wait returns early (>128) when a trapped signal arrives; on_signal has exited by then.
CHILD=
END=$(date -u +%FT%TZ)
if [ "$RC" = 0 ]; then write PASS 0 "$END"; logline PASS 0
else write FAIL "$RC" "$END"; logline FAIL "$RC"; fi
exit "$RC"
