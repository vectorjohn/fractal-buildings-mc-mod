# Keeping vanilla villages and other structures out of our cities

**Track:** worldgen · **Date:** 2026-10-08 · **Author:** researcher

## What it is

Vanilla structure sets don't know about our region layer, so a village,
outpost or modded structure can start inside a city. These are the options
for stopping that, each with its cost and risk, read from 26.3 source and
Lithostitched (`26.3` branch, commit 0083d79).

## Where a start can be stopped (verified)

Starts are decided in two places, and both must agree, or `/locate` and
explorer maps point at structures that never generate:

- **Generation**: `ChunkGenerator.createStructures` →
  `tryGenerateStructure` (private) → `Structure.generate` →
  `findValidGenerationPoint`. Returning false makes the set try its next
  weighted entry.
- **Locate**: `StructureCheck.checkStart` → `canCreateStructure` (private) →
  `findValidGenerationPoint`, on whatever thread runs `/locate`.

`findValidGenerationPoint` is public and overridable, and Lithostitched's
`DelegatingStructure` overrides it. So a mixin on the base method is bypassed
by wrapped structures. Hooking the two call sites is robust.

## Options

| Option | How | Cost | Risk |
|---|---|---|---|
| **A. Data-pack `exclusion_zone`** | override `structure_set/villages.json` etc. with `exclusion_zone {other_set: ours, chunk_count ≤ 16}` | no code | **One zone per placement**, and outposts' slot is already taken (villages). It tests our *placement* chunks, not real cities, so our district-origin grid must be ≤ 16 chunks apart. Last-loaded pack wins on `villages.json`. `@Deprecated`. Covers only the sets we override, not modded ones. |
| **B. Biome tags** (`has_structure/*`) | remove biomes from tags | no code | Can't express region cells. Only works with a city biome, which we rejected ([extension-points.md](extension-points.md)). |
| **C. Our veto mixin** | `@Inject(HEAD, cancellable)` into `ChunkGenerator.tryGenerateStructure` (return false) and `StructureCheck.canCreateStructure` (return false) when the start position (+ a margin) falls in a city cell and the structure is in our tag | two small mixins; one pure region-cell lookup per candidate start (rare: villages are one per 34×34 chunks) | Mixins on private methods can break between MC versions (names are stable in unobfuscated 26.x). Only the start *position* is known before generation, so we need a margin (jigsaw ≤ 128 + 12; vanilla villages 80). A custom generator that overrides `createStructures` bypasses it. |
| **D. Lithostitched `set_structure_spawn_condition`** | JSON modifier wrapping each structure in a `DelegatingStructure` with a `PlacementCondition` (e.g. `sample_density` on our city-mask density function) | no mixin of ours | **Hard dependency.** It swaps `Holder.Reference` values in the structure registry. The condition is tested at the stub position only. Covers only the structures we list. |
| **E. Integrate, don't exclude** | the planner treats vanilla village candidates as obstacles or districts. `RandomSpreadStructurePlacement.getPotentialStructureChunk` is pure; the real spawn needs `findValidGenerationPoint`, as `StructureCheck` does | no mixin; one jigsaw start evaluation per nearby candidate (builds the start piece) | Doesn't stop other mods' structures. Planner complexity. |

## Licence

Minecraft: proprietary, study only. Lithostitched: MIT.

## What to borrow

- **C as the default**, driven by our structure tag
  `fractalbuildings:excluded_from_cities`. Default it to surface structures:
  villages, outposts, ruined portals, abandoned camps, igloos, pyramids.
  Underground ones (mineshafts, trial chambers, ancient cities, strongholds)
  stay. Use the same predicate at both call sites, so `/locate` stays honest.
- **E as an option for later**: a city founded on a vanilla village site reads
  as intentional.
- **D only as a compat path** for packs that already use Lithostitched.

## What to avoid

- A veto that depends on anything but seed and position (for example "is
  chunk X generated").
- Vetoing on the post-generation bounding box in `tryGenerateStructure` only.
  `/locate` would then disagree.

## Hypotheses from ARCHITECTURE.md

- Not covered by the primer. **New constraint**: structure-based cities need a
  veto hook for foreign surface structures. Data packs alone can't express it
  for all structures.

## Probe tests worth writing after 0.5

- A server test with the veto mixin and a fixed region cell over a known
  village chunk. Assert no village start is generated there, and that
  `/locate structure minecraft:village_plains` skips it.

## Verdict

Use our own two-site veto mixin (C), tag-driven, deciding from position plus
margin. Exclusion zones and biome tags can't do the job, and Lithostitched (D)
works but costs a dependency.

**Sources / what I couldn't verify:** read `ChunkGenerator.createStructures`
and `tryGenerateStructure`, `StructureCheck.checkStart` and
`canCreateStructure`, `Structure.findValidGenerationPoint`,
`AbstractSpreadingStructurePlacement.ExclusionZone`,
`ChunkGeneratorStructureState.hasStructureChunkInRange` and the vanilla
`structure_set/*.json` (26.3), plus Lithostitched `DelegatingStructure` and
`SetStructureSpawnConditionModifier`. No mixin was written or run.
