#!/usr/bin/env bash
# Run a test command in the current worktree and pin its evidence (CLAUDE.md "Parallel work",
# "Heavy runs: evidence"): the exact commit tested, the command, the result, the load, and a copy of
# the logs and screenshots it produced, in a folder that outlives the worktree.
#
#   scripts/evidence.sh <label> <command...>
#   e.g. scripts/evidence.sh p6-foliage scripts/client-test.sh
#        scripts/evidence.sh p6-foliage scripts/test-all.sh --no-client
#
# Writes <main root>/.claude/reports/<label>-evidence/<short sha>-<UTC time>/ with EVIDENCE.md (commit,
# dirty files if any, command, exit code, start/end, loadavg, copied files) and copies of every known
# test artifact the command wrote (build/test-all/*.log, build/client-test/gradle.log, the client game
# test log, mod/build/screenshots/*.png, mod/build/perf/*, mod/build/flythrough/*, build/server-only/*,
# viewer/out/** screenshots).
# Prints "EVIDENCE <dir>" and exits with the command's exit code. Takes no lock itself: the commands
# it runs (client-test.sh, perf.sh, test-all.sh) take the heavy lock where they need it.
set -uo pipefail
[ $# -ge 2 ] || { sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
LABEL=$1; shift
WT=$(git rev-parse --show-toplevel) || exit 2
MAIN_ROOT=$(dirname "$(git -C "$WT" rev-parse --path-format=absolute --git-common-dir)")
SHA=$(git -C "$WT" rev-parse HEAD)
DIRTY=$(git -C "$WT" status --porcelain --untracked-files=no)
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
OUT="$MAIN_ROOT/.claude/reports/$LABEL-evidence/${SHA:0:10}-$STAMP"
mkdir -p "$OUT"
MARK="$OUT/.start"; touch "$MARK"
START=$(date -u +%FT%TZ); LOAD0=$(cut -d' ' -f1-3 /proc/loadavg)

"$@"; RC=$?

END=$(date -u +%FT%TZ); LOAD1=$(cut -d' ' -f1-3 /proc/loadavg)
# Copy artifacts written by this run (newer than the start mark), keeping their relative paths.
copied=()
for rel in build/test-all build/client-test build/server-only mod/build/screenshots \
           mod/build/run/clientGameTest/logs/latest.log mod/build/perf mod/build/flythrough viewer/out; do
  [ -e "$WT/$rel" ] || continue
  while IFS= read -r f; do
    r=${f#"$WT/"}; mkdir -p "$OUT/$(dirname "$r")"; cp -p "$f" "$OUT/$r"; copied+=("$r")
  done < <(find "$WT/$rel" -type f -newer "$MARK" ! -name '*.lock' 2>/dev/null | sort)
done
rm -f "$MARK"
{
  echo "# Evidence: $LABEL"
  echo
  echo "- commit: $SHA ($(git -C "$WT" log -1 --format=%s "$SHA"))"
  echo "- branch: $(git -C "$WT" branch --show-current | grep . || echo detached) (worktree $WT)"
  if [ -n "$DIRTY" ]; then echo "- DIRTY tree at start (evidence does not pin a commit):"; echo "$DIRTY" | sed 's/^/    /'; else echo "- tree: clean"; fi
  echo "- command: $*"
  echo "- exit: $RC"
  echo "- start: $START (loadavg $LOAD0); end: $END (loadavg $LOAD1)"
  echo "- files (${#copied[@]}):"
  for r in "${copied[@]}"; do echo "    $r"; done
} > "$OUT/EVIDENCE.md"
echo "EVIDENCE $OUT (exit $RC)"
exit $RC
