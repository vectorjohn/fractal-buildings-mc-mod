# Docs watcher prompt

The prompt the coordinator gives the docs-sync watcher: one `Agent` call with
`subagent_type: assistant`, `model: haiku`, `run_in_background: true`
(`.claude/agents/coordinator.md`, "the user's review loop"). Copy everything
between the markers verbatim, filling in `<integration checkout>` and
`<run branch>`. It lives outside `.claude/agents/` so it is never loaded as an
agent definition. Changing it: edit here; the next watcher started picks it up.

---PROMPT START---
task: docs-sync watcher (long-running, cheap). slug: docs-watch. Do NOT edit any files, start agents or merge anything yourself.

Integration checkout: <integration checkout> (branch <run branch>).

Loop: start this as ONE background Bash command (run_in_background: true, timeout 7200000), then wait for it to finish:

cd <integration checkout> && end=$(( $(date +%s) + 6600 )); merged=0; while [ $(date +%s) -lt $end ]; do out=$(scripts/docs-sync.sh 2>&1); rc=$?; if [ $rc -eq 10 ]; then merged=1; echo "$out" >> /tmp/claude-1500/docs-watch-merged.txt; sleep 300; continue; fi; if [ $rc -ne 0 ]; then echo "STOP rc=$rc"; echo "$out"; exit $rc; fi; if [ $merged -eq 1 ]; then echo "FEEDBACK MERGED (debounced)"; cat /tmp/claude-1500/docs-watch-merged.txt; rm -f /tmp/claude-1500/docs-watch-merged.txt; exit 10; fi; sleep 600; done; echo ROUND-END

(rc 10 = the user's feedback merged: it keeps syncing every 5 min until a sync finds nothing new, then reports once — a 5-min debounce. Any other non-zero rc reports at once.)

When the command finishes:
- output ends with ROUND-END → start the same command again (you are authorised to restart yourself indefinitely; the user approved this). Do not report, and do not end your turn with a status message: a ROUND-END is never a reason to finish.
- otherwise (FEEDBACK MERGED or STOP rc=N) → finish, with a final message of at most 10 lines: the rc, the merge sha(s) and files from docs-sync's output, verbatim key lines. The coordinator acts and starts a new watcher.
Never print the full command output into your own reasoning beyond that; never run anything else.
---PROMPT END---
