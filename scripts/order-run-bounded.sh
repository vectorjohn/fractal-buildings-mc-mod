#!/usr/bin/env bash
# Run one order-test server command with a bounded shutdown (ORDERHANG).
#
#   scripts/order-run-bounded.sh VERDICT_FILE RUN_TIMEOUT_S GRACE_S -- cmd args...
#
# Runs cmd in its own session (so its whole process group, Gradle client and server JVM, can be killed without touching anyone
# else's). Waits for it to exit. Two limits:
#   * no verdict yet after RUN_TIMEOUT_S: kill the group (exit 124, as `timeout` did);
#   * a verdict file (non-empty) exists but cmd is still alive GRACE_S later: the test result is in and only the shutdown hangs
#     (2026-10-31: the server logged "Stopping server / Saving worlds" after its PASS, then the run sat until the 3000 s timeout).
#     Print one `SHUTDOWN SLOW` line, save a jstack of each java process (<label>.hang-jstack.<pid>), kill the group, and exit 0: the verdict, not the exit status, is the result.
# Otherwise exits with cmd's own status. The kill is TERM, then KILL 10 s later, of this run's process group only.
set -u
VF="$1"; TMO="$2"; GRACE="$3"; shift 4
setsid "$@" &
pid=$!
t0=$SECONDS; tv=""
killgrp() { kill -TERM -- "-$pid" 2>/dev/null || true; for _ in 1 2 3 4 5 6 7 8 9 10; do kill -0 "$pid" 2>/dev/null || break; sleep 1; done; kill -KILL -- "-$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; }
trap 'killgrp; exit 143' TERM INT HUP
while kill -0 "$pid" 2>/dev/null; do
	if [ -z "$tv" ] && [ -s "$VF" ]; then tv=$SECONDS; fi
	if [ -n "$tv" ] && [ $((SECONDS - tv)) -ge "$GRACE" ]; then
		echo "order-test: SHUTDOWN SLOW: verdict $(head -c 60 "$VF" | tr '\n' ' ')written, server still alive $GRACE s later (run $((SECONDS - t0)) s); killing its process group $pid" >&2
		# the evidence the first occurrence lacked: a thread dump of each Java process of this group (best effort)
		for p in $(pgrep -g "$pid" 2>/dev/null); do
			[ "$(cat "/proc/$p/comm" 2>/dev/null)" = java ] && timeout 20 jstack "$p" > "${VF%.verdict}.hang-jstack.$p" 2>&1
		done
		killgrp; exit 0
	fi
	if [ -z "$tv" ] && [ $((SECONDS - t0)) -ge "$TMO" ]; then
		echo "order-test: run timeout after $TMO s without a verdict; killing its process group $pid" >&2
		killgrp; exit 124
	fi
	sleep 1
done
wait "$pid"
