# 6.26 FOLIAGEFAIL: FoliageClientGameTest red after PORCHSTEP

Branch `claude/p6-foliagefail`, 2026-10-29, lead-coder.

## Symptom

`FoliageClientGameTest` failed on run-phase6 c2f082d8 (and on DEADPORT's merge c33c37ec) with
`seed 1 street: 1 natural leaves in the clear volume (surface+1..4) at the end, samples [309,76,-1693 spruce_leaves]`.
It passed at 067e5415 (run-phase6 2c06e7af).

## Bisect

- 117aeb45 (DOORBANK), the perf worker radeonsi: pass; seed 1 street columns 734 (as before).
- c2f082d8 (PORCHSTEP + docs), local Intel: fail; street columns 736.
- The fixed branch shows the same leaf (now counted as unclaimed) on both the Intel iGPU and the perf worker, so it is not a host difference.

Culprit: 46a0c194 (6.19 PORCHSTEP).

## Cause

A diagnostic `explain` line (ledger cells, lot, building raster, ground boxes and SpillClear trace for every leaf the check counts) gave
`309,76,-1693: ledger 72=oak_stairs 73=air 74=air 75=air | lot d2_m11.l4 workshop (inside) | building raster 72=STAIR 73..75=AIR | spill no pass`.

The test calls every ledger column whose top claimed cell is air over a claimed solid a "street column", and that includes building
columns. PORCHSTEP put a step (a stair block) in front of the workshop's door. The step became the top solid of that column, so the
test's window (surface+1..4) moved up one block to 73..76. The building claims only 73..75 there, so y 76 is no piece's air. A
neighbour chunk's spruce crown is allowed to stand there, as it can anywhere outside the plan. Neither SpillClear (street air) nor
BuildingAirClear (a building's air) was ever supposed to clear that cell. The leaf is held (not an orphan), and there is 3 blocks of
clearance over the step.

## Fix

The street headroom check in `FoliageClientGameTest.headroom` counts only cells the ledger claims as air. For a real street nothing
changes, because a street claims all four cells. The road check is unchanged, since its window is `RoadSurface.HEADROOM`, which the
road clears in full. Leaves in the unclaimed cells of the window are printed as `natural leaves in its unclaimed cells N` but don't
fail the test. Every leaf that is counted still gets the `explain` line, so the next failure names its owner.

The control (leaf clear off) still has a signal: street 25 (26 before, minus this one unclaimed leaf), road 17 to 24.

## Verification

- `FB_ONLY=FoliageClientGameTest scripts/client-test.sh` at d7cf99a4, local Intel: pass; the perf worker radeonsi: pass (evidence
  `p6-foliagefail-local` and its twin named after the perf worker). Seed 1 shows `unclaimed 1` and claimed 0 on both hosts.
- `scripts/order-test.sh`: see the report (it only changes the test, so the mod's main code is the run branch's).

## Finding (not fixed)

The test's end state varies between runs of one commit. The held leaves above the seed 1 street after the pass are 334 or 374 (now
336/376), and the road counts vary by tens. That has been so since at least 2026-10-27, on both hosts. Nothing asserts on it, and the
orphan and headroom counts stay 0. The likely cause is chunk order in how neighbour passes notch crowns (`CUT_KEPT` 79 to 84). This
is recorded in NOTES.
