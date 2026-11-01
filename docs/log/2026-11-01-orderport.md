# ORDERPORT: order-test never loses a run to a port collision

Cause: ports were fixed (`FB_ORDER_PORT` 25610 + run index); on 2026-11-01 (READSDEFAULT verify, bf137ecb) another process held reverse-centre's port, the server logged "FAILED TO BIND TO PORT" and the run had no verdict (exit 4).

Done: `scripts/order-port.sh` (sourced): `pick_port` takes the first free port from PORT0+idx (nothing listening via /dev/tcp, and an atomic `mkdir build/order-test/ports/<port>` claim so concurrent runs never share). `order-run-bounded.sh` gets `ORDER_BIND_LOG`: a bind failure in the server log before any verdict kills the run's own process group and exits 75; `order-test.sh` then retries on the next port (max 6), logging `PORT RETRY`. Exit codes 0/1/2/4 unchanged.

Verified: `scripts/order-port.test.sh` (dummy perl listener skipped, claim skipped, 6 concurrent picks distinct, exit 75 on bind failure, normal status passes), `order-run-bounded.test.sh` still passes, full order-test run (evidence in the report).

Not fixed (same risk, different pattern, client or harness also knows the port): server-only-test.sh 25599, perf.sh 25598, portalcold.sh 25597.
