---
tags: research
track: pool
date: "2026-10-16"
author: researcher
licence: none
---

# Building pools: verdict, interface, and what S6 should measure

## What it is

The track 8 verdict on John's precomputed-pool proposal, and how it feeds
Phase 2 (S6) and Phases 3–6. It draws on [overview](overview.md),
[cost-model](cost-model.md), [vanilla-precompute](vanilla-precompute.md) and
[lost-cities-planning-cache](lost-cities-planning-cache.md).

## Verdict

1. **Now: prefetch and memoise behind a pure-cache interface.**
   - The per-cell plan and every building's IR are pure functions.
   - Workers compute them ahead of the lane. Triggers: a district start being
     created, and player positions beyond view distance.
   - The lane joins the shared future, or computes the value itself.
   - Same bytes either way, with no pool and no loss of variety. This is
     vanilla's stronghold-ring pattern and Lost Cities' planning service.
2. **Inside a building: memoise repeats.** Typical floors and repeated bay
   modules. This halved the toy tower's cost, and nobody can see it.
3. **Later, if measured: pool components, not houses.** Houses cost ≤ 5% of a
   district's lane time, and reusing them shows within one street view. Towers
   can cost more than the lane itself. If prefetch can't hide that, pool tower
   bases, typical floor plates and crowns on a shared structural grid.
4. **First launch:** use the blocked time to prefetch the exact cells around
   spawn, during level load. That takes seconds. Don't build a generic pool:
   at 46k potential entries it is either huge or mostly unused.

## Interface shape for planner-core (sketch, not production code)

Keep "pool as cache" a policy switch from day one: the generator sees only a
small value type, never the site.

```java
record BuildingSpec(int genVersion, long specSeed, StyleId style, BuildingType type,
                    int width, int depth, int floors, RoofForm roof, Attach attach) {}
interface ShellGenerator { BuildingIr generate(BuildingSpec spec); } // pure, no probes, no Site
interface InstancePass { PlacedBuilding apply(BuildingIr shell, Site site); } // foundation,
                                                    // party-wall masks, palette, weathering
interface IrSource {                       // the only way the plan or adapter gets IR
  BuildingIr get(BuildingSpec spec);       // join an in-flight future, or compute inline
  void prefetch(BuildingSpec spec);        // a hint; a no-op is correct
}
interface SpecPolicy { BuildingSpec specFor(Lot lot, Brief brief, CellPlan plan); }
```

- **Exact policy (default):** `specSeed = H(world, cell, lot, "building")`, and
  the size is the lot's own.
- **Pooled policy:** quantise the lot to a key. Pick a variant by weighted
  rendezvous over ids 0..N−1, excluding the neighbours' picks, inside the
  whole-cell plan. Then `specSeed = H(world, key, variant)`.

Switching policies changes buildings, never code. Rules:

- `BuildingSpec` has exact `equals`/`hashCode` and a stable byte form (for a
  future persisted signature).
- N comes from config, never from progress.
- Variant content never reads its siblings.

## What S6 should measure (the generator doesn't exist yet)

Use a synthetic `ShellGenerator` with a calibrated busy-loop: 1 ms per house,
50 ms and 200 ms per tower, and ~30k blocks per downtown chunk.

1. **Lane time per district, by part:** placement test, plan (probes + layout),
   IR generation, pieces, save, clip, block writes. Measure at village, town
   and downtown scale, with a cold and a warm cache.
2. **Block write cost** on a `ProtoChunk` in ns per block. This is the floor
   no pool removes.
3. **Prefetch lead:** the time from a district start's creation to its first
   `postProcess`. Measure walking, sprinting, elytra (or `/tp` steps) and a
   Chunky-style pregeneration. Count IR lookups that were done, in flight, or
   computed inline.
4. **Worker contention:** chunks per second with 0, 2 and 4 busy workers.
   Does prefetch slow vanilla's noise threads?
5. **Cache memory** at steady state, compact vs object IR. Add the existing
   evict, regenerate and compare-bytes test, extended to IR prefetched on N
   threads.

**Proposed decision rule:** consider pooling a class only if, with prefetch on,
its generation is still > 10% of downtown lane time, or > 5% of its lookups
compute inline at elytra speed.

## Later phases

- **3.1:** a compact IR form (role + box arrays) and a spec hash.
- **3.3:** v0 written as `ShellGenerator` + `InstancePass`.
- **3.10:** byte-identical IR across cold, warm, evicted and prefetched
  caches.
- **Phase 3:** a per-class timing test, to replace the ×5–20 guess.
- **4.x:** typical-floor memo; tower components on a structural grid.
- **5.x:** lot widths snapped to standard widths per style, which is
  believable on its own and makes pooling possible; neighbour exclusion.
- **6.x:** prefetch triggers, worker count (config; Lost Cities uses
  cores / 2), and spawn-cell prefetch during level load.
- **ADR 0002:** add one line, "plan and IR are pure caches; workers may
  prefetch them; the lane joins".

## The case against, and what would change it

- **Total CPU, not latency.** On 4-core machines, or servers with players far
  apart, prefetch moves work without reducing it. Only reuse cuts the total.
- **Curation.** A pool can afford best-of-K: generate K candidates per variant
  and keep the best by a pure score, which raises the quality floor. Inline,
  that costs K× per lot. This is the strongest argument for a pool. **If**
  Phase 4 review finds an ugly tail rather than sameness, curate through a
  pool.
- **Bounded worst case.** A pool caps how much one freak building can cost.
- **Evidence that would change my mind:**
  - towers still cost > 50 ms after the typical-floor memo;
  - S6 finds the prefetch lead shorter than IR time at elytra speed;
  - workers measurably cut chunk throughput.

## Licence

Own analysis.

**Sources / what I couldn't verify:** the notes above. All thresholds are
proposals for John and the S6 owner. Nothing here was measured in game.
