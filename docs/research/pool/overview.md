---
tags: research
track: pool
date: "2026-10-16"
author: researcher
licence: none
---

# Precomputed building pools: the design space

## What it is

John's proposal (2026-10-16): after launch, worker threads pre-generate a pool
of *candidate* buildings from generic inputs (style, biome, footprint, height);
worldgen then picks from the pool with a pure function of (seed, chunk) and
only fits, details and weathers the pick. The aim is less work on the serial
worldgen lane ([structure-pipeline](../worldgen/structure-pipeline.md)), which
matters more now that John wants unbounded cities and skyscrapers. Numbers are
in [cost-model](cost-model.md), precedents in
[vanilla-precompute](vanilla-precompute.md) and
[lost-cities-planning-cache](lost-cities-planning-cache.md), and the verdict in
[recommendation](recommendation.md). Evidence tags as in ARCHITECTURE.md.

## Approach: three mechanisms in one idea

The proposal bundles three things that can be adopted separately:

| Mechanism | What it does | Gains | Costs |
|---|---|---|---|
| **Quantisation** | A lot maps to a finite key, and the building becomes f(key, variant) instead of f(lot) | Makes reuse possible | Variety: the same building appears on many lots |
| **Memoisation** | Each (key, variant) is computed once and kept | CPU, but only when inputs repeat (needs quantisation) | Memory |
| **Prefetch** | Work is done on workers *before* the lane asks for it | Lane time | Workers compete with vanilla's noise threads |

Prefetch needs neither of the others. The per-cell plan is a pure function of
the cell, so a worker can compute the *exact* plan and every building's IR for
the cells near a player (or for a district whose start the lane just created)
before the features step clips them. Quantisation is the only part that costs
variety, and memoisation pays only through it.

## What is generic and what is site-specific

| Stage ([synthesis](../synthesis.md)) | Reads | Pool key could hold it? |
|---|---|---|
| City plan: districts, roads, lots, briefs | seed, cell, coarse heights, biomes | No: the site itself. Prefetch only |
| Massing, floor datum, roof heightfield | lot width × depth, frontage side, floors, roof form | Yes, if lot sizes are quantised |
| Façades (`Comp` + split) | massing + which sides are party walls | Yes, with attachment in the key (4 cases) |
| Interiors, openings, most detail | massing, façade | Yes |
| Foundation or plinth depth | exact heights under the footprint | No. Small per-instance pass; `beard_thin` grades below the floor |
| Palette and weathering | district biome, weathering field | No, but cheap: one lookup per block, and the IR emits roles |
| Clip and block writes | chunk box | No. Per chunk, on the lane |

So the poolable part is the grammar body (massing through detail), and it is
generic **only if lot dimensions come from a small set**. With exact lot sizes
almost no two specs collide, and a pool degenerates into a per-lot cache. The
lever is planning: parcel subdivision that snaps lots to standard widths per
style (as real burgage plots and 25-ft city lots do). That is believable, and
it is a planning decision, not a cache trick. **The biome palette class
should not be in the key**: generators emit roles, and palettes are applied
per instance.

## The determinism trap

The pool's *content* and the *choose* function must both be pure. Things
that must never influence content:

- **Time and progress.** "Pool size = what the workers finished in 30 s", or
  "use the entry if ready, else a simpler building", makes the world depend on
  the machine. N per key comes from config, and the fallback is always the same
  computation run inline (join the in-flight future or compute it): the pool
  is a **pure cache, never a source of truth**.
- **Siblings.** "Variant 5 avoids looking like the variants already built"
  depends on build order. Variant i = f(seed, key, i) alone. If variants must
  differ, generate the key's N variants as one unit f(seed, key), or stratify
  the parameter space by i.
- **Shared mutable state:** a shared RNG, `HashMap` iteration, a cache
  read inside generation, the biome resolver's per-thread tie state (prime it
  on worker threads too, [terrain-biome-queries](../worldgen/terrain-biome-queries.md)).
- **Choose by `mod N`.** `H(seed, lot) mod N` reassigns nearly every lot when N
  changes. Weighted rendezvous over variant ids (the project's `Choice`)
  moves only the lots the new variant wins. The neighbour rule ("not the same
  variant as the lot next door") runs inside the whole-cell plan, so it is
  pure too.

Eviction and recomputation are then harmless: an evicted entry comes back
byte-identical (the S6 "evict, regenerate, compare bytes" test covers it).

## Persistence

- **P0, regenerate on load (recommended first).** Prefetch makes warm-up cheap,
  and there is nothing to invalidate.
- **P1, a persisted performance cache under a signature** (Lost Cities' hub
  cache). The signature must cover *every* input: seed, generator version,
  config and style data. Generic entries don't read terrain, which keeps their
  signature honest. Lost Cities' does not cover terrain mods
  ([lost-cities-planning-cache](lost-cities-planning-cache.md)).
- **P2, persisted source of truth** (Valheim stores its world-creation
  location placement in the save, so an update adds new locations only through
  a re-placement command). This pins old buildings across mod updates. It is
  S6's "store IR vs references" question, not a pool question.

## Interaction with the serial lane and the lazy plan

The lane path stays: district start → `plan(cell)` → pieces carry
(cell, lot) → `postProcess` → `ir(spec)` → per-instance passes → clipped block
writes. Each lookup is a join on a shared future (vanilla's stronghold rings
and Lost Cities' planning service do exactly this). If a worker got there
first, the lane waits less; otherwise the lane computes it itself. The result
is the same either way. Triggers: when a district start is created (the
features front arrives several chunks later), and from player positions
beyond view distance.

## Procedural vs templated

A whole-building pool turns the generator into a runtime *template* source:
N stamps per key, which is the variety ceiling the synthesis rejected for
option C (Repurposed Structures' identical houses). Within one street view,
duplicates are near-certain for any N a pool can afford
([cost-model](cost-model.md)). Pooling *components*, which real buildings
repeat anyway (a tower's typical floor, a bay module), keeps the assembly
procedural.

## Licence

Own analysis; no code read for this page beyond the linked notes.

## Verdict

Adopt prefetch and memoisation behind a pure-cache interface now. Treat
whole-building quantisation as an option, mainly for towers and civic
buildings, and pool *components* (floor plates, bases, crowns) rather than
houses. The first-launch budget is better spent prefetching the exact cells
around spawn than building a generic pool.

**Sources / what I couldn't verify:** own analysis over the notes linked
above. Costs are a toy probe plus inference ([cost-model](cost-model.md)). How
long the lead is from district start to the first `postProcess` was not
measured; S6 should measure it.
