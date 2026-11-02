# 6.30 PORTALCOLD-M: measure the first portal trip into a cold settlement

2026-10-30, `claude/p6-portalcold-m`. Measurement only; generation and TeleportHold untouched.

- Added `PortalColdHarness` (dedicated server, installed through `-Dfb.testHook`, run `:mod:runPortalColdServer`, `scripts/portalcold.sh`), `MockPlayer.join` with a game mode. One fresh JVM and world per case: wild, wild2, town, metro. Findings: [spikes/portalcold.md](../research/spikes/portalcold.md), raw rows in `docs/research/spikes/portalcold/`.
- Result (the perf worker, loadavg 4-6): `getPortalDestination` 12.7 s wild, 36.6 s town, 65.3 s metropolis core; 4 ms when the 25 x 25 chunks are already loaded. 625 chunks are generated in that one call, server thread parked.
- Surprises: driving the vanilla tick path (`setAsInsidePortal`) never completed for the mock player (first attempt also had a wrong portal position), so the harness calls `getPortalDestination` and `teleport` directly. The TPHOLD hook (`teleport` head) is too late for this stall.
- Heavy runs: three on the perf worker, one on the merged tree (`b7ab02d0`), evidence `.claude/reports/p6-portalcold-m-evidence/b7ab02d067-20261030T230411Z`. One local run at loadavg 16-22 (town 40.3 s, metro 60.4 s).
- Mutation check: not applicable (a measurement; `wild2` is the control that shows the harness reads ~0 when nothing is generated).
- Decision for John: (a) accept / (b) warm / (c) hold. The numbers point to (c) with the hook on `getPortalDestination` and async chunk tickets, see the note.
