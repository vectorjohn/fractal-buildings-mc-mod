# Architecture

The design as it stands. Parts marked **hypothesis** are to be confirmed by
research track 4 and the Phase 2 spikes; settled decisions live in
[adr/](adr/). (From John's original roadmap doc, 2026-10-07.)

## Overview

A pure planner produces one **Building IR** (and, later, city plans). The
three.js viewer and the Fabric adapter are two consumers of that same IR.

```
                 ┌──────────── probes (terrain height, biome) ───────────┐
                 │  synthetic (tests) · recorded dump (viewer/CLI) · live MC │
                 └──────────────────────────┬────────────────────────────┘
 seed, region cell, lot, style ──► planner-core (pure Java) ──► Building IR / city plan
                                                                  │            │
                                                     cli → JSON → viewer    mod adapter
                                                     (three.js, galleries)  (roles → blocks,
                                                                             IR → structure pieces)
```

**Probes are the key seam.** Behind the same interface, the planner can read
synthetic terrain in unit tests, terrain recorded from a real world in the
viewer, or live terrain in Minecraft. Bugs found in-game can therefore be
replayed outside it.

Module rules (also in CLAUDE.md): `planner-core` never imports Minecraft;
generators emit abstract material roles; everything is deterministic from the
seed and position.

## Worldgen primer: where cities fit (hypothesis)

A city can be planned knowing the biomes it covers: biomes are a pure function
of seed and position, so a structure can sample them anywhere without
generating chunks. Everything here is a working hypothesis for track 4 to
confirm against the 26.x sources.

**Generation runs in stages, per chunk:**

1. **structure_starts:** decides which structures begin in this chunk and lays
   out all their pieces up front.
2. **structure_references:** nearby chunks record which starts overlap them.
3. **biomes:** stores the chunk's biomes.
4. **terrain:** base terrain, surface blocks, carvers.
5. **features:** places features and structure pieces, clipped to this chunk.
6. **Lighting, spawning, then full.**

**Structures are decided before the chunk's own biome stage, but that doesn't
matter.** Overworld biomes come from six climate parameters (temperature,
humidity, continentalness, erosion, weirdness, depth) computed from noise.
Structure code can query the biome source at any coordinate, including the
whole footprint of a planned city. Vanilla structures already check biomes when
they start.

**Terrain height can also be estimated before generation.** The chunk generator
exposes base-height queries that structures like villages use to project to the
surface. Exact method names in 26.x are to be confirmed.

**Size is the real constraint.** One jigsaw structure's pieces stay within 128
blocks horizontally of its start (116 with terrain adaptation). Pieces also
only reach chunks that hold a reference to the start, and that search radius
is believed to be about 8 chunks (to verify). A large city is therefore many
coordinated structure starts, not one.

**Terrain adaptation exists.** Structures can reshape surrounding terrain:
`beard_thin` (villages, outposts), `beard_box` (ancient cities), `bury`
(strongholds), `encapsulate` (trial chambers).

## A second layer above biomes (proposal)

A **settlement region layer**: our own deterministic field, independent of
vanilla biomes.

- The world is divided into large region cells (jittered grid or Voronoi),
  seeded from the world seed. Each cell decides whether it holds a settlement,
  its centre, size and culture.
- The city plan is a pure function of the cell. It is computed on first use and
  cached (deterministic, bounded, thread-safe).
- Each district is its own structure start on a sub-grid inside the cell. It
  reads the shared plan and emits only its own pieces.
- Each district samples the biomes beneath it to choose a palette. A city
  straddling desert and forest gets sandstone streets on one side and timber on
  the other, with a blend at the border.

Three ways to integrate, compared in Phase 1 and tested in Phase 2:

| Approach | How | Gains | Costs |
| --- | --- | --- | --- |
| Structure-based (proposed) | Custom structure type plus region layer; vanilla biomes untouched | Works with other biome and terrain mods; uses vanilla piece clipping and saving | Must coordinate many starts; bounded by reference radius |
| Biome overlay | Inject an urban biome into the biome source (TerraBlender-style) | Cities affect grass, mobs and surface rules | Invasive; conflicts with biome mods; defeats cities crossing biomes |
| Custom chunk generator | Own generator owns terrain and cities (The Lost Cities model) | Total control over layout and terrain | Poor compatibility; must reimplement or wrap vanilla terrain |
