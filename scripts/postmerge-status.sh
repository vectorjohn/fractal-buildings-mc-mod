#!/usr/bin/env bash
# Report the state of post-merge test runs started by scripts/squash-merge.sh (through
# scripts/postmerge-run.sh), from their result files in <main root>/.claude/postmerge/.
#
#   scripts/postmerge-status.sh                 one line per run, newest first (last 20; --all for every one)
#   scripts/postmerge-status.sh <slug|sha>      the newest run matching a slug (exact, with or without
#                                               claude/) or a sha prefix: status line, result file, the
#                                               evidence folders, and the last log lines while RUNNING or
#                                               after a FAIL
# Status: RUNNING (tests going or waiting for the postmerge lock / a suite slot), PASS, FAIL, KILLED (got
# SIGTERM/SIGINT), DIED (RUNNING, but its process is gone: the machine rebooted or it was SIGKILLed; this
# script marks it DIED in the result file and in .claude/postmerge.log once).
# Exit (single run): 0 PASS | 1 FAIL, KILLED or DIED | 3 RUNNING | 2 no such run. List mode exits 0.
# The machine-readable log is <main root>/.claude/postmerge.log (one START/PASS/FAIL/KILLED/DIED line each).
# Test hook: FB_POSTMERGE_DIR (state dir, as for postmerge-run.sh).
set -uo pipefail
usage() { sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
ALL=0 Q=
for a in "$@"; do
  case "$a" in
    --all) ALL=1 ;;
    -h|--help|-*) usage ;;
    *) Q=$a ;;
  esac
done
ROOT=$(dirname "$(git -C "$(dirname "$0")" rev-parse --path-format=absolute --git-common-dir)") || exit 2
DIR=${FB_POSTMERGE_DIR:-$ROOT/.claude/postmerge}
PLOG="$(dirname "$DIR")/postmerge.log"
get() { sed -n "s/^$2=//p" "$1" | head -n1; }

# RUNNING with a dead process -> DIED (re-read first: the run may have just finished).
status_of() { # <result file>
  local f=$1 s pid
  s=$(get "$f" status)
  if [ "$s" = RUNNING ]; then
    pid=$(get "$f" pid)
    if ! { [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null \
           && tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -q postmerge-run; }; then
      s=$(get "$f" status)
      if [ "$s" = RUNNING ]; then
        sed 's/^status=RUNNING$/status=DIED/' "$f" > "$f.tmp.$$" && mv -f "$f.tmp.$$" "$f"
        echo "$(date -u +%FT%TZ) DIED sha=$(get "$f" sha | cut -c1-10) slug=$(get "$f" slug) result=$f" >> "$PLOG"
        s=DIED
      fi
    fi
  fi
  echo "$s"
}

mapfile -t FILES < <(ls -1t "$DIR"/*.result 2>/dev/null)
if [ -z "$Q" ]; then
  [ ${#FILES[@]} -gt 0 ] || { echo "no post-merge runs in $DIR"; exit 0; }
  n=0
  for f in "${FILES[@]}"; do
    [ "$ALL" = 1 ] || [ $n -lt 20 ] || break
    printf '%-8s %s %-28s start %s end %s\n' "$(status_of "$f")" "$(get "$f" sha | cut -c1-10)" \
      "$(get "$f" slug)" "$(get "$f" start)" "$(get "$f" end | grep . || echo -)"
    n=$((n + 1))
  done
  exit 0
fi

Q=${Q#claude/}
HIT=
for f in "${FILES[@]}"; do
  if [ "$(get "$f" slug)" = "$Q" ] || [[ $Q =~ ^[0-9a-f]{4,40}$ && $(get "$f" sha) == "$Q"* ]]; then HIT=$f; break; fi
done
[ -n "$HIT" ] || { echo "no post-merge run for '$Q' in $DIR"; exit 2; }
S=$(status_of "$HIT")
echo "POSTMERGE $S $(get "$HIT" sha) $(get "$HIT" slug) rc=$(get "$HIT" rc | grep . || echo -)"
echo "  result:   $HIT"
echo "  log:      $(get "$HIT" log)"
echo "  start:    $(get "$HIT" start)  end: $(get "$HIT" end | grep . || echo -)"
LOG=$(get "$HIT" log)
EV=$(sed -n 's/^EVIDENCE \(.*\) (exit .*/\1/p' "$LOG" 2>/dev/null | paste -sd' ')
echo "  evidence: ${EV:-$(get "$HIT" evidence)}"
if [ "$S" != PASS ] && [ -s "$LOG" ]; then echo "  last log lines:"; tail -n 8 "$LOG" | cut -c1-200 | sed 's/^/    /'; fi
case "$S" in PASS) exit 0 ;; RUNNING) exit 3 ;; *) exit 1 ;; esac
