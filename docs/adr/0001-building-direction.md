---
tags: adr
status: accepted
date: "2026-10-16"
author: researcher
approvedBy: "John, 2026-10-16"
supersededBy:
dependsOn:
related:
  - "[[docs/adr/0002-integration-approach]]"
research:
  - "[[docs/research/synthesis]]"
  - "[[docs/research/pcg/grammars-deep-dive]]"
  - "[[docs/research/pcg/split-grammars]]"
  - "[[docs/research/pcg/cga-shape-grammar]]"
  - "[[docs/research/pcg/greuter-pseudo-infinite-cities]]"
  - "[[docs/research/pcg/constrained-growth-floor-plans]]"
  - "[[docs/research/aesthetics/roof-forms-and-massing]]"
---

# 0001. Building direction: volume massing, then a grammar for façades

Buildings of every size, from cottages to skyscrapers, are made by a pure,
phased pipeline: massing by volume assembly, a split grammar for floors, bays
and façades, roofs as heightfields, and interiors from a room graph.

## Context

Phase 1 compared three ways to make a building from a lot
([synthesis](../research/synthesis.md)). The constraints: output is a pure
function of (seed, region cell, lot), derived whole and clipped per chunk;
`planner-core` has no Minecraft; invariants (in the lot, reachable rooms,
closed roofs, nothing floating) should hold by construction where possible;
and variety must not turn into noise (Wonka's Fig. 2,
[split-grammars](../research/pcg/split-grammars.md)). John approved the
direction at the Phase 1 gate (synthesis.md sign-off, commit ef48bee) and
added: **we are not building houses only; focus on large buildings and
skyscrapers**. The decision below is the synthesis's B1–B4 with that emphasis.

## Options

1. **Shape grammar (split / CGA)**: rules decompose shapes; rule choice by
   hashed draws keyed by scope. Pros: deterministic per rule, containment for
   façades by construction, ms per building, ideas free of licences. Cons:
   massing needs a hull check; a flat rule set degrades into noise.
2. **WFC / model synthesis, MarkovJunior**: tile constraints. Pros: local
   coherence. Cons: no global structure (entrance to street, joined wings,
   reachability), unbounded retries, chunk-order dependence if solved per
   chunk.
3. **Graph plus authored templates** (jigsaw, Lost Cities parts). Pros: lowest
   cost, hand-built quality. Cons: variety capped by piece count, NBT needs
   Minecraft classes, other mods' assets are ND or LGPL; contradicts goal 1.

## Decision

Option 1, as a fixed sequence of phases, each reading only frozen snapshots
of earlier phases ([grammars-deep-dive](../research/pcg/grammars-deep-dive.md)):

- **B1 Pipeline.** Brief (style × class × region, early draws such as roof
  form) → **massing** by volume assembly inside the lot hull, checked for
  containment, with **one floor grid per building** → roofs as a heightfield
  over the volumes → façades by `comp` + exact integer split/repeat, once per
  face per floor band → interiors → openings and bounded detail passes →
  palette by weathering field. WFC only inside fixed borders.
- **B2 Rules** are a typed Java API (exact `IntSplit`, rendezvous `Choice`,
  phase snapshots); styles, palettes and briefs are JSON parameters. No
  grammar language.
- **B3 Authored content** is small role-level terminals only. No NBT.
- **B4 Interiors (Phase 4):** rooms, doors and stairs, reachable by
  construction; furniture as hooks.

**Large buildings and towers are first-class classes**, not an extension:

- *Massing:* towers are stacked setback volumes, each upper volume inside the
  one below (Greuter's "wedding cake"), so upper floors are supported by
  construction; podium, shaft and crown are control-grammar bands. Big
  low buildings (halls, blocks, courtyards) are unions of volumes as for houses.
- *Floor grid:* one grid of storey heights per building (taller ground floor,
  repeated typical floors), so façade splits run once per band and windows
  align over any number of storeys.
- *Interiors:* a vertical core (stairs, later lifts) placed first and kept
  through every floor ("shafts first",
  [constrained-growth](../research/pcg/constrained-growth-floor-plans.md));
  each floor is grown around it, so reachability holds per floor from the
  core. Typical floors repeat with per-band variation.
- *Limits (read from 26.3 data):* the overworld spans y −64 to 319, so about
  60 storeys of 4 blocks above sea level; taller needs a data-pack height
  change, out of scope. The ±8-chunk structure limit is horizontal (references
  are per chunk column, full height), so it bounds a footprint, not a height:
  a building must lie inside one district, and a district inside its start's
  ~248-block box, so footprints up to ~200 blocks are possible if the city
  plan draws district borders around big lots (*inferred*,
  [ADR 0002](0002-integration-approach.md)).

## Consequences

- Phase 3 starts with the IR and a plain generator, then the `IntSplit` and
  `Choice` property tests; 3.11's diversity score judges sameness. Phase 4's
  building types should name high-rise, block and hall classes next to the
  current house, shop, tower and temple list (a ROADMAP change for the
  coordinator).
- Block count, not rule count, dominates cost for tall buildings (~125k blocks
  of walls and floors for a 40×40×240 tower, arithmetic). That pushes S6
  towards pieces that store references rather than IR, and may call for
  interiors generated per floor from (building, floor) hashes instead of held
  whole in the cache (*inferred*).
- Track 8 (a precomputed candidate pool, R8) would sit in front of this
  pipeline as a cache of its pure outputs; it does not change this decision.
- **Revisit** if: Phase 4 galleries fail "would a person build this" while
  diversity is fine (shift weight to authored modules over an occupancy grid,
  [townscaper](../research/pcg/townscaper.md)); 3.11 shows sameness in massing
  (switch to occupancy-cell massing; same voxel output); style work stalls on
  recompiles or third-party style packs become a goal (JSON over the same
  operators).
