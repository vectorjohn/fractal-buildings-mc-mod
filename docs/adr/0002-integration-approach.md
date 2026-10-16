---
tags: adr
status: superseded
date: "2026-10-16"
author: researcher
approvedBy: "John, 2026-10-16"
supersededBy:
  - "[[docs/adr/0003-worldgen]]"
dependsOn:
related:
  - "[[docs/adr/0001-building-direction]]"
research:
  - "[[docs/research/synthesis]]"
  - "[[docs/research/worldgen/structure-pipeline]]"
  - "[[docs/research/worldgen/extension-points]]"
  - "[[docs/research/worldgen/terrain-biome-queries]]"
  - "[[docs/research/worldgen/surface-level-and-flattening]]"
  - "[[docs/research/worldgen/keeping-structures-out]]"
  - "[[docs/research/overlays/lithostitched-deep-dive]]"
  - "[[docs/research/overlays/terrablender]]"
  - "[[docs/research/overlays/lost-cities-road-planning]]"
  - "[[docs/research/mods/the-lost-cities]]"
  - "[[docs/research/postgen/overview]]"
---

# 0002. Integration approach to spike: structure-based district starts over a region plan

**Superseded by [ADR 0003](0003-worldgen.md)** (John, 2026-10-16): every decision here that
is still in force is restated there.

What Phase 2 spikes: cities enter the world as many structure starts, one per
district, reading one pure plan per region cell, with no fixed city size. The
worldgen ADR (ROADMAP 2.10) finalises this after the spikes.

## Context

Track 4 measured the limits ([structure-pipeline](../research/worldgen/structure-pipeline.md)):
blocks beyond ±8 chunks of a start are silently dropped; one start per
structure per chunk; starts, references and features run on one serial
`worldgen` lane per dimension; `/locate` calls placement from other threads
without building pieces. Biomes are cheap to sample anywhere (~40 ms per 512²
layer, *probe*); exact heights cost 0.43–0.51 ms per column in game
([terrain-biome-queries](../research/worldgen/terrain-biome-queries.md)).
John approved the synthesis's I1–I6 and spike scope at the Phase 1 gate
(synthesis.md sign-off, commit ef48bee), and asked for **effectively
unbounded cities**: "512 is too small", though a 512 limit "could still be
interesting".

## Options

1. **Structures, one start per district**: all of `/locate`, bounding boxes,
   saving and the beardifier for free; works with any generator using vanilla
   `createStructures`. Cons: ±8 chunks per start, coordination between
   starts, the serial lane.
2. **Per-chunk feature** (Lost Cities): no size limit, order-independent by
   construction. Cons: no `/locate`, no boxes, no beard; vanilla structures
   overlap it.
3. **Biome overlay**: an "urban" biome replaces the biomes a city should span
   (every position still has one biome) and doesn't shape terrain; no Fabric
   overworld biome API.
4. **Custom chunk generator**: total control, but an empty-router
   `RandomState` without a mixin, and clashes with world presets.
5. **Post-generation** (track 7): unbounded, but timing depends on players.

## Decision

- **I1** Spike option 1: a `RandomSpreadStructurePlacement` subclass answers
  "is this chunk a district origin?" cheaply from position; the district's
  pieces come from the cached cell plan in the lazy piece builder; one rigid
  piece per building plus the road and wall segments in its box; pieces save
  references (cell, building id). Option 2 is the fallback; stop work on 3
  and 4.
- **I2** Terrain: adapt first (`beard_thin`, stepped foundations, reject
  steep lots); fill-only flattening (S7) only if S5 fails; no terraces.
- **I3** Veto foreign surface structures in cities with our own mixin, margin
  from the field's F2 − F1 edge distance.
- **I4** No required dependencies; publish `fractalbuildings:settlement` as a
  2D density-function type.
- **I5** Post-generation is a backup; its batch 2 waits for S3 and S6.
- **I6 Scale: no fixed city size cap in v1.** The target is effectively
  unbounded cities built from districts. **512 × 512 is the floor to prove
  first**, the size every estimate so far was made for, not a limit; nothing
  beyond it is measured yet. The ±8-chunk box bounds a district (~250
  blocks), not a city. What doesn't scale for free is the whole-cell plan on
  the serial lane (~0.5 s per 512² with exact heights, so ~8 s at 2048²).
  Expected design past that point (*inferred*, for S6 and 2.10 to confirm): a
  two-level plan (a cheap city skeleton with roads on position-hashed lines
  and the coarse height screen, then district detail built lazily and warmed
  off the lane), and neighbouring settled cells joining into one conurbation
  through hashed gates on cell borders. S6 measures how far it goes; if it
  finds a ceiling, that becomes 6.5's default "maximum city size", not a
  design limit.
- **Spike scope:** S3 adds a road crossing a district border and `/locate`
  through the placement subclass; S6 adds evict-and-regenerate byte compare
  and "how large can a city go"; S9 adds the Lithostitched compat check; S1
  and S2 shrink to what track 4 left open (ROADMAP 2.1, 2.2).

## Consequences

- Critical path: S3 → S6, and S5 → S7. S3 is the hinge between options 1
  and 2; the same pure plan feeds either adapter, so a fallback costs an
  adapter, not a redesign.
- Cities larger than one cell need agreed endpoints across cell borders (the
  open road question in ARCHITECTURE.md), which Phase 5 must solve, not just
  the spike.
- Pieces that save references risk seams after a config or version change
  mid-world; rendezvous choice limits the damage, and S6 weighs it against
  storing IR.
- **Revisit** if S3's seams can't be fixed by ownership or S6 finds pieces
  too heavy (switch to the feature adapter); if S7 needs more than three
  density hooks or pack makers need more than our density function
  (reconsider Lithostitched); post-generation is promoted only if both base
  paths fail.
