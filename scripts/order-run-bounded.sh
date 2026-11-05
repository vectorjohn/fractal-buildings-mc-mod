#!/usr/bin/env bash
# Run one order-test server command with a bounded shutdown (ORDERHANG).
#
#   scripts/order-run-bounded.sh VERDICT_FILE RUN_TIMEOUT_S GRACE_S -- cmd args...
#
# Runs cmd in its own session (so its whole process group, Gradle client and server JVM, can be killed without touching anyone
# else's). Waits for it to exit. Two limits:
#   * no verdict yet after RUN_TIMEOUT_S: kill the group (exit 124, as `timeout` did);
#   * ORDER_BIND_LOG=<server log> in the environment (ORDERPORT): no verdict yet and that log says "FAILED TO BIND TO PORT": kill the group at once, exit 75 (the caller retries on another port).
#   * a verdict file (non-empty) exists but cmd is still alive GRACE_S later: the test result is in and only the shutdown hangs
#     (2026-10-31: the server logged "Stopping server / Saving worlds" after its PASS, then the run sat until the 3000 s timeout).
#     Print one `SHUTDOWN SLOW` line, save a jstack of each java process (<label>.hang-jstack.<pid>), kill the group, and exit 0: the verdict, not the exit status, is the result.
# Otherwise exits with cmd's own status. The kill is TERM, then KILL 10 s later, of this run's process group only.
set -u
VF="$1"; TMO="$2"; GRACE="$3"; shift 4
setsid "$@" &
pid=$!
# Process registry (W8, scripts/lib/procs.sh): this run's process group, owned by this script, so
# scripts/watchdog.sh can report it as an orphan if this script dies and leaves the server behind.
# shellcheck source=lib/procs.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib/procs.sh"
fb_proc_register "pg-$pid" kind=pgroup "pgid=$pid" "owner=$$" "label=order-test run ${VF##*/}" \
	"budget=$((TMO + GRACE + 120))" "log=${ORDER_BIND_LOG:-}" "cmd=$(printf '%s ' "$@" | cut -c1-200)"
trap 'fb_proc_unregister "pg-$pid"' EXIT
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
	if [ -z "$tv" ] && [ -n "${ORDER_BIND_LOG:-}" ] && grep -qs "FAILED TO BIND TO PORT" "$ORDER_BIND_LOG"; then
		echo "order-test: bind failure in $ORDER_BIND_LOG; killing its process group $pid (exit 75)" >&2
		killgrp; exit 75
	fi
	if [ -z "$tv" ] && [ $((SECONDS - t0)) -ge "$TMO" ]; then
		echo "order-test: run timeout after $TMO s without a verdict; killing its process group $pid" >&2
		killgrp; exit 124
	fi
	sleep 1
done
wait "$pid"; rc=$?
# the server may have died on the bind failure before the loop saw its log
if [ ! -s "$VF" ] && [ -n "${ORDER_BIND_LOG:-}" ] && grep -qs "FAILED TO BIND TO PORT" "$ORDER_BIND_LOG"; then exit 75; fi
exit $rc
