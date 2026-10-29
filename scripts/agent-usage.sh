#!/usr/bin/env bash
# agent-usage.sh: summarise Claude Code agent transcripts (time, calls, context tokens).
#
# Usage:
#   scripts/agent-usage.sh <agent-id | transcript.jsonl> ... [--from ISO] [--to ISO]
#
#   An agent id (e.g. a0d42c828b2d3d68c, as recorded in the run ledger) is looked up
#   under ~/.claude/projects/<this project>/*/subagents/. A session transcript
#   (<session-uuid>.jsonl, the coordinator) can be passed as a path; use --from/--to
#   (UTC ISO prefixes, e.g. 2026-10-05T10:20) to cut out one milestone's window.
#
# Columns:
#   active_min   wall time with gaps over 10 min removed (usage-limit pauses, idle waits)
#   calls        API requests (unique assistant message ids)
#   tools        tool calls made
#   peak_ctx     largest prompt sent (input + cache write + cache read)
#   cache_read / cache_write   summed over all calls
#
# Caveats:
#   - Output tokens are not shown: transcripts record usage at stream start, so they
#     undercount badly.
#   - Gradle time is not separated out: agents run suites in the background and wait
#     for a notification, so it shows up as gaps (or inside active time if < 10 min).
#
# Needs jq (in the flake dev shell): scripts/dev scripts/agent-usage.sh ...
set -euo pipefail

from=0000; to=9999; files=()
proj="$HOME/.claude/projects/$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)" | tr '/.' '--')"
while [ $# -gt 0 ]; do
  case "$1" in
    --from) from=$2; shift 2 ;;
    --to)   to=$2; shift 2 ;;
    *.jsonl) files+=("$1"); shift ;;
    *) f=$(find "$proj" -path "*/subagents/agent-$1.jsonl" 2>/dev/null | head -1)
       [ -n "$f" ] || { echo "no transcript for agent $1 under $proj" >&2; exit 1; }
       files+=("$f"); shift ;;
  esac
done
[ ${#files[@]} -gt 0 ] || { sed -n 2,25p "$0"; exit 1; }

printf '%-20s %-11s %-16s %10s %6s %6s %9s %11s %11s  %s\n' \
  agent type start_utc active_min calls tools peak_ctx cache_read cache_write description
for f in "${files[@]}"; do
  meta="${f%.jsonl}.meta.json"
  if [ -f "$meta" ]; then
    type=$(jq -r '.agentType // "-"' "$meta"); desc=$(jq -r '.description // ""' "$meta")
  else
    type=session; desc=""
  fi
  name=$(basename "$f" .jsonl); name=${name#agent-}
  jq -rs --arg from "$from" --arg to "$to" --arg name "${name:0:20}" --arg type "$type" --arg desc "$desc" '
    [ .[] | select(.timestamp != null and .timestamp >= $from and .timestamp <= $to) ] as $L
    | if ($L|length) == 0 then "\($name)  (no entries in window)" else
      ($L | map(select(.type=="assistant")) | group_by(.message.id) | map(.[-1].message)) as $m
      | ($L | map(.timestamp | sub("\\.[0-9]+Z$";"Z") | fromdateiso8601)) as $t
      | ([range(1; $t|length) as $i | ($t[$i]-$t[$i-1]) | select(. <= 600)] | add // 0) as $active
      | [ $name, $type, $L[0].timestamp[0:16],
          (($active/60)|floor), ($m|length),
          ([$m[].content[]? | select(.type=="tool_use")] | length),
          ([$m[].usage | .input_tokens + .cache_creation_input_tokens + .cache_read_input_tokens] | max // 0),
          ([$m[].usage.cache_read_input_tokens] | add // 0),
          ([$m[].usage.cache_creation_input_tokens] | add // 0), $desc ]
      | "\(.[0])\t\(.[1])\t\(.[2])\t\(.[3])\t\(.[4])\t\(.[5])\t\(.[6])\t\(.[7])\t\(.[8])\t\(.[9])"
      end' "$f" |
  awk -F'\t' 'NF<10 {print; next} {printf "%-20s %-11s %-16s %10s %6s %6s %9s %11s %11s  %s\n",$1,$2,$3,$4,$5,$6,$7,$8,$9,$10}'
done
