#!/usr/bin/env bash
# Run a test command in the current worktree and pin its evidence (CLAUDE.md "Parallel work",
# "Heavy runs: evidence"): the exact commit tested, the command, the result, the load, and a copy of
# the logs and screenshots it produced, in a folder that outlives the worktree.
#
#   scripts/evidence.sh <label> <command...>
#   e.g. scripts/evidence.sh p6-foliage scripts/client-test.sh
#        scripts/evidence.sh p6-foliage scripts/test-all.sh --no-client
#        scripts/evidence.sh p6-foliage scripts/remote.sh --attach <job>   (or --attach-last)
#
# Writes <main root>/.claude/reports/<label>-evidence/<short sha>-<UTC time>[-N]/ with EVIDENCE.md (commit,
# dirty files if any, command, exit code, start/end, loadavg, copied files) and copies of every known
# test artifact the command wrote (build/test-all/*.log, build/client-test/gradle.log, the client game
# test log, build/anywhere/* (scripts/anywhere.sh: which host ran it; also a "route:" line),
# build/remote/*.log and *.state (scripts/remote.sh jobs; also a "remote job:" line each), mod/build/screenshots/*.png, mod/build/perf/*, mod/build/flythrough/*, build/server-only/*, build/order-test/*,
# viewer/out/** screenshots).
# Re-attach: if this run dies with its worker waiter (SIGKILL, session end), its EVIDENCE.md says
# "interrupted"; the job runs on. Rerun as `scripts/evidence.sh <label> scripts/remote.sh --attach <job>`
# (the job is named in build/remote/*.state, `scripts/remote.sh --list`, and the route file of
# scripts/anywhere.sh): the new folder pins the job's commit, command and start (from its state file;
# --attach-last is resolved to the job's name first) and copies what the attach fetched.
# Prints "EVIDENCE <dir>" and exits with the command's exit code. Takes no lock itself: the commands
# it runs (client-test.sh, perf.sh, test-all.sh) take the heavy lock where they need it.
# Concurrent runs get their own folders, but runs overlapping in ONE worktree share its build/ paths, so
# each also copies whatever the other wrote meanwhile; use separate worktrees for clean evidence.
set -uo pipefail
[ $# -ge 2 ] || { sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
LABEL=$1; shift
WT=$(git rev-parse --show-toplevel) || exit 2
MAIN_ROOT=$(dirname "$(git -C "$WT" rev-parse --path-format=absolute --git-common-dir)")
SHA=$(git -C "$WT" rev-parse HEAD)
DIRTY=$(git -C "$WT" status --porcelain --untracked-files=no)
STAMP=$(date -u +%Y%m%dT%H%M%SZ)

# scripts/remote.sh --attach <job> | --attach-last: pin the job (its state file) rather than the
# worktree as it is now.
JOB_STATE="" is_remote=0
args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do
  case "${args[i]}" in
    *remote.sh) is_remote=1 ;;
    --attach) (( is_remote )) && JOB_STATE="$WT/build/remote/$(basename "${args[i+1]:-x}" .state).state"
              [[ -f ${args[i+1]:-} ]] && JOB_STATE=${args[i+1]} ;;
    --attach-last)
      (( is_remote )) || continue
      best="" bt=0
      for f in "$WT"/build/remote/fb-*.state; do
        [ -e "$f" ] || continue
        st=$(sed -n 's/^status=//p' "$f"); s=$(sed -n 's/^started=//p' "$f")
        case "$st" in fetched|cancelled|gone) continue ;; esac
        (( ${s:-0} >= bt )) && { best=$f; bt=${s:-0}; }
      done
      [ -n "$best" ] || { echo "evidence.sh: --attach-last: no unfetched job in $WT/build/remote" >&2; exit 2; }
      JOB_STATE=$best; args[i]=--attach; args=("${args[@]:0:i+1}" "$(basename "$best" .state)" "${args[@]:i+1}") ;;
  esac
done
set -- "${args[@]}"
JOB_NAME="" JOB_COMMIT="" JOB_STARTED="" JOB_CMD=""
if [ -n "$JOB_STATE" ]; then
  [ -f "$JOB_STATE" ] || { echo "evidence.sh: no state file $JOB_STATE (scripts/remote.sh --list)" >&2; exit 2; }
  JOB_NAME=$(sed -n 's/^job=//p' "$JOB_STATE"); JOB_COMMIT=$(sed -n 's/^commit=//p' "$JOB_STATE")
  JOB_STARTED=$(sed -n 's/^started=//p' "$JOB_STATE"); JOB_CMD=$(sed -n 's/^cmd=//p' "$JOB_STATE")
  full=$(git -C "$WT" rev-parse --verify -q "${JOB_COMMIT%-dirty}^{commit}") && SHA=$full
fi

# The folder is created atomically (mkdir without -p on the leaf): when another run with the same label
# already took <sha10>-<stamp> (both started in the same second), this one gets <sha10>-<stamp>-2, -3, ...
# Before this, such runs shared one folder, one start mark and one EVIDENCE.md (VILLROAD, 2026-10-28).
PARENT="$MAIN_ROOT/.claude/reports/$LABEL-evidence"
mkdir -p "$PARENT" || exit 2
OUT="$PARENT/${SHA:0:10}-$STAMP"; n=1
until mkdir "$OUT" 2>/dev/null; do
  n=$((n + 1)); OUT="$PARENT/${SHA:0:10}-$STAMP-$n"
  [ "$n" -le 100 ] || { echo "evidence.sh: cannot create a folder under $PARENT" >&2; exit 2; }
done
MARK="$OUT/.start"; touch "$MARK"
# An attach copies what the job wrote since it started (rsync keeps the worker's mtimes).
[ -n "$JOB_STARTED" ] && touch -d "@$JOB_STARTED" "$MARK"
START=$(date -u +%FT%TZ); LOAD0=$(cut -d' ' -f1-3 /proc/loadavg)
{
  echo "# Evidence: $LABEL (INTERRUPTED or still running)"
  echo
  echo "- command: $*"
  echo "- start: $START; this file is replaced when the command ends. If it stays, the run was killed:"
  echo "  a worker job runs on; see scripts/remote.sh --list and rerun as"
  echo "  scripts/evidence.sh $LABEL scripts/remote.sh --attach <job>"
} > "$OUT/EVIDENCE.md"

"$@"; RC=$?

END=$(date -u +%FT%TZ); LOAD1=$(cut -d' ' -f1-3 /proc/loadavg)
# Copy artifacts written by this run (newer than the start mark), keeping their relative paths.
copied=()
for rel in build/test-all build/client-test build/server-only build/order-test build/anywhere build/remote mod/build/screenshots \
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
  if [ -n "$JOB_NAME" ]; then
    echo "- commit: $SHA ($(git -C "$WT" log -1 --format=%s "$SHA" 2>/dev/null || echo "not in this repo")); from remote job $JOB_NAME (commit $JOB_COMMIT), worktree HEAD now $(git -C "$WT" rev-parse --short=10 HEAD)"
    echo "- attached to: $JOB_NAME, started $(date -u -d "@$JOB_STARTED" +%FT%TZ), job command: $JOB_CMD"
  else
    echo "- commit: $SHA ($(git -C "$WT" log -1 --format=%s "$SHA"))"
  fi
  echo "- branch: $(git -C "$WT" branch --show-current | grep . || echo detached) (worktree $WT)"
  if [ -n "$JOB_NAME" ]; then
    case "$JOB_COMMIT" in *-dirty) echo "- DIRTY tree when the job was sent (evidence does not pin a commit)" ;; *) echo "- tree: clean when the job was sent" ;; esac
  elif [ -n "$DIRTY" ]; then echo "- DIRTY tree at start (evidence does not pin a commit):"; echo "$DIRTY" | sed 's/^/    /'; else echo "- tree: clean"; fi
  echo "- command: $*"
  echo "- exit: $RC"
  echo "- start: $START (loadavg $LOAD0); end: $END (loadavg $LOAD1)"
  for r in "${copied[@]}"; do [[ $r == build/anywhere/* ]] && echo "- route: $(tr '\n' ' ' < "$OUT/$r" | cut -c1-500)"; done
  for r in "${copied[@]}"; do
    [[ $r == build/remote/*.state ]] || continue
    st=$(sed -n 's/^status=//p' "$OUT/$r"); jn=$(sed -n 's/^job=//p' "$OUT/$r")
    echo "- remote job: $jn ($(sed -n 's/^host=//p' "$OUT/$r") job $(sed -n 's/^id=//p' "$OUT/$r")): $st, exit $(sed -n 's/^exit=//p' "$OUT/$r")"
    case "$st" in fetched|cancelled|gone) ;; *) echo "  NOT FETCHED: finish with scripts/evidence.sh $LABEL scripts/remote.sh --attach $jn" ;; esac
  done
  echo "- files (${#copied[@]}):"
  for r in "${copied[@]}"; do echo "    $r"; done
} > "$OUT/EVIDENCE.md"
echo "EVIDENCE $OUT (exit $RC)"
exit $RC
