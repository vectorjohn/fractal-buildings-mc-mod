# ORDERHANG: order-test shutdown hang

**Symptom.** `scripts/order-test.sh` run `mutant-lakes` wrote its PASS verdict, the server log showed a normal stop, and the run still ran to the 3000 s `timeout` (exit 124) on a host at load 25-90 (QUAYLAZY step 4).

**Cause (as far as it can be known).** The only bound on a run was `timeout 3000 ./gradlew ... :mod:runOrderServer`. The harness writes the verdict and calls `server.halt(false)`; after that nothing in the script looked at the verdict, it just waited for the Gradle client to exit. A JVM or Gradle exit that stalls under load therefore cost the whole timeout. We have no thread dump, and it did not reproduce alone, so the stalled thread is unknown; the script-side defect (waiting on the process rather than the result) is certain.

**Fix.** `scripts/order-run-bounded.sh` runs the command in its own session and polls: verdict present and process alive after `FB_ORDER_GRACE` (default 120 s) gives a `SHUTDOWN SLOW` line, a `jstack` of each java process in the group saved as `build/order-test/<label>.hang-jstack.<pid>`, then TERM/KILL of that process group only, exit 0 (the verdict is the result, so no exit 4). No verdict by `FB_ORDER_TIMEOUT` is still exit 124. `order-test.sh` header documents it; exit-code contract unchanged.

**Verification.** `scripts/order-run-bounded.test.sh` (7 checks: fake server that writes a verdict then sleeps with a child, is bounded and the child is dead; clean exit passes the status through with no SHUTDOWN SLOW; no verdict gives 124). Mutation: grace check disabled makes the hang case never return (test killed by an outer timeout). Order test result: see the report.
