---
tags: research
track: pool
date: "2026-10-16"
author: researcher
licence: none
---

# Building pools: cost, memory and variety

## What it is

The numbers behind [overview](overview.md): what a building costs per class,
where that cost lands on the serial lane, what a pool would hold in memory,
how visible reuse is, and what John's ~30 s first-launch budget buys. There is
no real generator yet (Phase 3), so building costs come from a **throwaway toy
pipeline** (*probe*) scaled by an *inferred* factor.

## The probe

A single-file Java benchmark (scratchpad, not committed) runs a toy version of
the [synthesis](../synthesis.md) pipeline on a byte role grid:

- L-shaped massing with floor slabs, then a gable roof heightfield;
- an exact integer bay split per side with the mirrored remainder, with the
  same bays on every floor;
- constrained room growth plus a BFS per floor;
- a hashed trim detail pass;
- a support flood fill (the floating-block invariant);
- greedy run-merge into IR boxes.

JDK 25, one thread, median of 20 or more runs after warm-up. Load was 9.8–10.3
on 20 CPUs (spikes building), so treat the times as indicative.

| Class | Voxels | Solid | IR boxes | Median |
|---|---|---|---|---|
| cottage 9×7, 1 floor | 630 | 286 | 76 | 0.02 ms |
| house 12×10, 2 floors | 1,920 | 954 | 271 | 0.13 ms |
| townhouse 7×14, 4 floors | 2,156 | 1,168 | 533 | 0.08 ms |
| block 24×24, 8 floors | 27,072 | 10,907 | 3,071 | 0.92 ms |
| tower 32×32, 60 floors | 265,216 | 119,509 | 38,050 | 5.4 ms |
| same, typical floor memoised | 265,216 | 118,519 | 37,292 | 2.6 ms |
| tower 48×48, 100 floors, memoised | 983,808 | 459,696 | 134,400 | 10.0 ms |

**Scale to a real generator ×5–20** (*inferred*): best-of-N massing
candidates, depth layers, more detail passes, IR objects with provenance
tags, invariant checks and serialisation. That gives a **house at ~0.5–2 ms**
and a **60-floor tower at ~15–200 ms**. Memoising the typical floor
*inside* one tower halved the toy's cost with no visible reuse.

## Where it lands on the lane

- A district (~250 blocks, ~256 chunks) takes ~6.4 s to generate at vanilla's
  ~40 chunks/s ([pregenerators](../postgen/pregenerators.md), not measured
  here).
  - **Village or town district:** 60–150 houses × 0.5–2 ms = 0.03–0.3 s,
    **0.5–5%** of the district time.
  - **Downtown district:** 20–60 towers × 15–200 ms = 0.3–12 s,
    **5% to nearly 200%**. Skyscrapers are where generic generation can
    dominate the lane, and this is the case the proposal is right about.
- City plan: about 0.5 s per city for terrain probes
  ([synthesis](../synthesis.md)). It is site-specific, so it can be prefetched
  but not pooled.
- Block writes: a dense downtown chunk column holds ~30k solid blocks (the toy
  tower spread over 2×2 chunks). Write cost per block on a `ProtoChunk` is
  **not measured**. At an assumed 50–200 ns that is 1.5–6 ms per chunk, against
  ~25 ms per chunk for vanilla overall. This is the part no pool can remove.

## Memory

Compact IR is ~16 B per box (role plus a box in shorts); Java records are
~3× that.

| Entry | Compact |
|---|---|
| House | ~4 KB |
| 8-floor block | ~50 KB |
| Whole tower | ~600 KB |
| Tower stored as base + one typical floor + crown | ~30 KB |

Keys: 6 styles × 5 types × 12 footprint classes × 4 height classes × 4
attachment cases = 5,760 keys. At N = 8 variants that is 46k entries.

- All 46k entries at house size: ~180 MB.
- 480 whole towers (6 styles × 10 footprints × 8): ~290 MB compact.
- 1,440 tower components (8 bases, 8 floor plates and 8 crowns per style and
  footprint): **15–60 MB**.

A generic pool built before the world's demand is known is either huge or
mostly unused. A pool built from the demand of nearby cities is prefetch.

## Variety: how visible is reuse?

Assume uniform choice among N variants of one key.

| N | Chance a lot shares its variant with one of its 6 neighbours | Chance 20 buildings in one view are all distinct | Copies per variant among 200 lots |
|---|---|---|---|
| 8 | 55% | ~0 | 25 |
| 32 | 17% | 0.04% | 6 |
| 128 | 5% | 21% | 1.6 |

A neighbour-exclusion rule in the plan removes adjacent repeats. Repeats
within one street view are still near-certain unless per-instance passes
(palette, weathering, mirroring, ground-floor use, roof detail) hide them.
Yaw is not free variety, because the door must face the street. Components
repeat the way real buildings do: 8 bases × 8 floor plates × 8 crowns gives
512 towers from 24 entries, provided the plates share a structural grid (same
core and stair position) so that they stack.

## What 30 s at first launch buys

Assume 4 workers (half of an 8-core machine, as Lost Cities sizes its pool),
so ~120 core-seconds.

- **Exact prefetch:** a 1,000-building town is ~0.5 s of terrain plus
  0.5–2.5 s of buildings, so 30 s covers ~50–100 towns, or 10–400 downtown
  districts. That is far more than a player reaches in the first minutes. Only
  the spawn cell has to be ready before the player enters, which takes
  seconds, not 30.
- **Generic pool:** all house keys at N = 8 (6 styles × 50 keys) take 1–5 s.
  Whole towers take 7–96 core-seconds and don't fit in memory. Tower
  components fit.

## Licence

Own probe and arithmetic; no third-party code.

## Verdict

Houses are too cheap to be worth pooling (≤ 5% of lane time), and pooling them
is visible. Towers can dominate the lane: prefetch them and memoise their
typical floors first. Pool their components only if S6 or Phase 5 show
stalls that prefetch doesn't cover.

**Sources / what I couldn't verify:** the toy measures orders of magnitude,
not our generator; the ×5–20 factor is a guess to be replaced by the Phase 3
timing test. Block write cost and vanilla's 40 chunks/s were not measured
here. District size and building counts are planning assumptions.
