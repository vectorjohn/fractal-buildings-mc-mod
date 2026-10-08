# Terralith

**Track:** overlays · **Date:** 2026-10-08 (revised same day) · **Author:** assistant

## What it is

Stardust Labs' overworld overhaul: 146 biomes in the overworld list (vanilla
plus ~95 of its own), new terrain shapes and a few structures.
[Modrinth](https://modrinth.com/mod/terralith). Maintained: 2.6.5 for 26.3
(2026-09-27), as a datapack and Fabric/NeoForge jars. The Fabric jar is the
datapack (identical `data/`) plus a config screen and resource conditions for
toggles; **no mixins**, no Java worldgen. Needs Lithostitched as a mod.

## Approach

All data, layered on vanilla's 26.3 noise router:
- **Router:** `noise_settings/overworld` keeps vanilla's slots but points
  `continents` at `effective_continentalness` (a light reshaping spline over
  vanilla continents), and forces aquifers flooded inside cliff zones.
- **Terrain shape:** rewritten `offset`, `factor`, `jaggedness`: the vanilla
  model (nested cubic splines continents → erosion → ridges / ridges_folded)
  with its own tables (~64 nested splines in `offset`). `depth` and
  `sloped_cheese` stay vanilla and pick these up. Climate noises are retuned
  (`temperature`, `vegetation`, `erosion` octaves) for larger, varied zones.
- **Extra shapes** (custom density functions in `terralith:overworld/*`), all
  gated by 2D splines over continents/erosion/temperature so they follow
  climate cells: *additive* (dunes, arches, spike tendrils, max'ed into
  `extra_terrain_sum`) and *subtractive* (noise-driven cliff carving,
  `subtract_terrain_sum`). `final_density` is vanilla's form with
  `max(sloped_cheese + beardifier, extra)` and `min(..., subtract)` spliced
  in, plus its own cave cheese. Several folders look unused (`archd1`,
  `cliff_cringe`, `skylands/noise`: nothing references them).
- **Biomes:** an explicit multi-noise list (1708 entries) over the 6 climate
  parameters, with weirdness cut into ~58 bands to fit more variants per cell.
  Skylands are biomes in an ocean-continentalness cell.
- **Surface:** overrides `material_rule/overworld/*` and inserts one large
  `terralith:preliminary_surface` rule (biome + noise-threshold + steep bands).
- **Features:** its own placed/configured features per biome, 39 vanilla biome
  files overridden, ~18 vanilla placed features (ores, disks, trees) retuned.

## Procedural vs templated

Terrain and biomes fully procedural (authored spline tables); structures
(fortified/desert villages, mage towers, camps, rubble) are jigsaw/NBT.

## How it hooks into worldgen

Registry overrides of vanilla JSON (last pack wins), file by file. The biome
list ships twice: `dimension/overworld.json` with inline biomes, load-condition
skipped when Lithostitched is present, and a
`multi_noise_biome_source_parameter_list/overworld` override using
Lithostitched's extended list keys. Toggles (skylands off etc.) are
Lithostitched biome injectors. Deterministic; nothing chunk-order dependent.

## Licence

**Stardust Labs License** (custom, not open source;
[text](https://github.com/Stardust-Labs-MC/license)): no copying into other
projects; a clause restricts use with AI systems. Under John's decision
(ASSUMPTIONS.md, 2026-10-08) I read the 2.6.5+26.3 datapack and Fabric jar
**for the approach only**: file layout, router wiring, which functions feed
which, spline coordinates (not values), parameter-list structure. Nothing
copied; local copies deleted. Never copy its JSON or tables.

## What to borrow

- **Shape features as `max`/`min` layers over the base terrain**, gated by
  climate splines: our flatten wrapper fits the same slot naturally.
- **Weirdness sub-bands for variants**, not new climate axes.
- Biome palettes by **convention tags**: it fills 41 `c:is_*` biome tags
  (hot, dry, snowy, mountain, sandy...) for all its biomes. Use those.

## What to avoid / integration consequences

- **Structures on top work**: `final_density` keeps `beardifier`, so jigsaw
  terrain adaptation still flattens under our pieces.
- **Height estimate lies under dunes/arches**: vanilla 26.3
  `chunk_surface_level` = interpolated `preliminary_surface_level`; Terralith's
  version includes cliff carving but not the additive shapes. Site picking must
  sample real heightmaps (or the full density), not the estimate.
- **Flattening city cells**: wrap the router's `final_density` *and*
  `preliminary_surface_level` (whatever file won); never override the files.
  Inside cliff zones aquifers are forced flooded: fade the mask out there.
- **"Ocean" ≠ low continentalness**: skylands land sits in ocean cells.
- **Tectonic + Terralith**: both override `noise_settings/overworld`,
  `final_density`, `erosion`, `preliminary_surface_level`; mixed load order
  mixes them. Our wrapper must not assume either.

## Verdict

Compatibility target: purely data on vanilla's router, so a router-slot
wrapper plus tag-based palettes composes with it; never override its files.

**Sources / what I couldn't verify:** read Terralith 2.6.5+26.3 datapack and
Fabric jar (metadata, router, density-function graph, parameter-list shape,
material-rule types), vanilla 26.3 router JSON from the Loom cache for
comparison. Didn't run it. Unchecked: which game code reads
`chunk_surface_level` in 26.3; how skylands float (offset/factor in that cell,
presumably); whether its toggles still match 26.3 registry names.
