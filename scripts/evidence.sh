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
# Test ledger (W3): every run appends a row to <main root>/.claude/tested.tsv (tree, layer, exit, host,
# renderer, commit, label, folder; format in scripts/lib/tested.sh). When the worktree is clean and that
# exact tree already passed the same reusable layer (test-all, client-test, order-test, server-only,
# Gradle test/build/check, npm test) with its evidence folder still there, it does not rerun: it prints
# "ALREADY PASSED <old folder>", writes a folder pointing to it and exits 0. FB_RETEST=1 always reruns
# (heavy batches, to-main). perf, profile, fly-through, galleries, attaches and other commands always run.
# Query: scripts/tested.sh LAYER [REV].
set -uo pipefail
[ $# -ge 2 ] || { sed -n '2,32p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
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

# Test ledger (W3; scripts/lib/tested.sh): the layer this command runs and the tree it runs on.
# shellcheck source=lib/tested.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib/tested.sh" 2>/dev/null || {
  # an old checkout without the library: no ledger, nothing skipped
  tested_layer() { TESTED_LAYER=- TESTED_REUSABLE=0; }; tested_tree() { echo dirty; }; tested_record() { :; }; tested_lookup() { return 1; }
}
if [ -n "$JOB_STATE" ]; then
  # the job's command and forwarded FB_* variables, as remote.sh quoted them (printf %q)
  JOB_ENV=$(sed -n 's/^env=//p' "$JOB_STATE")
  eval "jw=($JOB_ENV $JOB_CMD)" 2>/dev/null || jw=($JOB_CMD)
  tested_layer "${jw[@]}"
  case "$JOB_COMMIT" in *-dirty|'') TREE=dirty ;; *) TREE=$(git -C "$WT" rev-parse --verify -q "$SHA^{tree}" || echo dirty) ;; esac
  TESTED_REUSABLE=0   # an attach fetches a job that ran; never skipped
else
  tested_layer "$@"
  TREE=$(tested_tree "$WT")
fi

# Looked up before this run creates its own folder (which could reuse a deleted folder's name).
PREV=""
if [ "${FB_RETEST:-0}" != 1 ] && [ "$TESTED_REUSABLE" = 1 ]; then PREV=$(tested_lookup "$TREE" "$TESTED_LAYER") || PREV=""; fi

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
# ALREADY PASSED: the identical tree passed this layer before and its evidence is still there. Heavy
# batches and the to-main run set FB_RETEST=1 and always rerun.
if [ -n "$PREV" ]; then
  IFS=$'\t' read -r P_TIME _ _ _ P_HOST _ P_COMMIT P_LABEL P_EV _ <<< "$PREV"
  {
    echo "# Evidence: $LABEL (ALREADY PASSED, not rerun)"
    echo
    echo "- commit: $SHA ($(git -C "$WT" log -1 --format=%s "$SHA"))"
    echo "- tree: $TREE (clean)"
    echo "- command: $*"
    echo "- layer: $TESTED_LAYER"
    echo "- exit: 0 (reused)"
    echo "- reused: passed at $P_TIME on $P_HOST, commit $P_COMMIT, label $P_LABEL"
    echo "- original evidence: $P_EV"
    echo "- rerun anyway: FB_RETEST=1 scripts/evidence.sh $LABEL $*"
  } > "$OUT/EVIDENCE.md"
  tested_record "$TREE" "$TESTED_LAYER" reused "$P_HOST" - "$SHA" "$LABEL" "$OUT" "$*"
  echo "ALREADY PASSED $P_EV (layer $TESTED_LAYER, tree ${TREE:0:12}, passed $P_TIME on $P_HOST; FB_RETEST=1 reruns)"
  echo "EVIDENCE $OUT (exit 0)"
  exit 0
fi
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
# Ledger row: host from the remote state or anywhere route this run wrote, renderer from the client log.
HOST=local REND=-
for r in "${copied[@]}"; do
  case "$r" in
    build/remote/*.state) h=$(sed -n 's/^worker=//p' "$OUT/$r"); [ -n "$h" ] && HOST=$h ;;
    build/anywhere/*) h=$(sed -n 's/^anywhere: -> \([^ ]*\).*/\1/p' "$OUT/$r" | tail -n1); [ -n "$h" ] && [ "$HOST" = local ] && HOST=$h ;;
  esac
done
[ -n "$JOB_STATE" ] && { h=$(sed -n 's/^worker=//p' "$JOB_STATE"); [ -n "$h" ] && HOST=$h; }
for r in "${copied[@]}"; do
  case "$r" in *.log) d=$(grep -ho 'Using graphics device: .*' "$OUT/$r" 2>/dev/null | head -n1 | cut -c24-100); [ -n "$d" ] && { REND=$d; break; } ;; esac
done
tested_record "$TREE" "$TESTED_LAYER" "$RC" "$HOST" "$REND" "$SHA" "$LABEL" "$OUT" "$*"
echo "- ledger: layer $TESTED_LAYER, tree $TREE, host $HOST (scripts/tested.sh)" >> "$OUT/EVIDENCE.md"
echo "EVIDENCE $OUT (exit $RC)"
exit $RC
