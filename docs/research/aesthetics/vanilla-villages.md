# Vanilla village variants as a baseline

**Track:** 6 Aesthetics · **Kind:** project (Mojang, vanilla) · **Date:** 2026-10-08

## What it is

The five biome villages (plains, desert, savanna, snowy, taiga; meadows reuse
plains). Sources: Minecraft Wiki [Village](https://minecraft.wiki/w/Village)
and [Village/Structure](https://minecraft.wiki/w/Village/Structure); the
vanilla data files themselves via the [misode/mcmeta](https://github.com/misode/mcmeta)
`data` branch (26.4-snapshot-3): `worldgen/structure/village_*.json`,
`worldgen/structure_set/villages.json`, `template_pool/village/*/houses.json`,
`processor_list/mossify_10_percent.json`, and the 157 `structure/village/*/houses/*.nbt`
templates, which I parsed with a throwaway NBT reader (sizes, block counts,
roof layer dumps; scratch only, not committed).

## Approach in three sentences

A jigsaw structure (`size` 6, `max_distance_from_center` 80, start projected
to `WORLD_SURFACE_WG`, `terrain_adaptation: beard_thin`) grows from a town
centre along street pieces that carry house sockets; houses are picked by
weight from a per-biome pool of hand-built templates. Structure set: random
spread, spacing 34, separation 8 chunks, one of the five types by biome tag.
Weathering is a processor (plains and taiga: cobblestone → mossy cobblestone
with probability 0.1); farms get crop processors.

## Procedural vs templated

Templated buildings, procedural layout. Measured (piece size X×Y×Z;
"walls" = enclosed box at wall height):

| Type | Typical small house | Walls | Roof | Main materials |
|---|---|---|---|---|
| plains | 7×7×7 piece | 5×5, 3 high, log corner posts on cobble | 1:1 oak-stair hip, 1-block eave all round | oak planks/logs, cobblestone, some white terracotta |
| taiga | 7×7×7 | 5×5, cobble base + spruce logs | 1:1 gable of spruce logs, 1-block eave and gable overhang | spruce log, cobblestone, trapdoor shutters |
| savanna | 7×7×7 | 5×5 terracotta (yellow/red/orange variants of one plan) | 1:1 acacia-stair hip, 1-block eave | acacia, coloured terracotta |
| desert | 5×5 to 7×7, 4–5 high | 5×5, no frame | flat, slab/wall parapet, no eave | smooth/cut sandstone, terracotta accents |
| snowy | 7×7 or 5–7 × 5 | 5–7 wide, spruce; or compact snow/ice huts | steep plank gable without eave, snow layers; huts are flat-topped | spruce planks, stripped spruce, snow, packed/blue ice |

Medium houses and job buildings run 7–14 × 5–13 footprints; the largest
(plains library) is 11×10×17. All interiors are 3 blocks high.

## How it hooks into worldgen

Standard jigsaw structure; covered by track 4. Relevant here: `beard_thin`
fills/carves a skirt under pieces, and paths only replace grass, water,
lava, sand and sandstone and don't go below one level of water (wiki).

## Licence

Mojang data (Minecraft EULA): studied, never copied or redistributed.
mcmeta is a mirror of it. Wiki text CC BY-NC-SA 3.0.

## What to borrow

- Biome → material mapping (table above) as default palettes; users already
  read these as "this biome's village".
- Small, cheap, legible buildings: 5×5 interior, 3-high walls, 1:1 roofs with
  1-block eaves read well at a distance and are a good minimum size.
- One plan reused with palette swaps (the three savanna terracotta houses):
  palette variation is a cheap variety multiplier.
- Clear silhouettes per biome: pitched in wet/cold, flat in desert.

## What to avoid (where vanilla reads as fake)

- Flat facades: windows flush, no plinth step or bands, minimal trim.
- Uniform 10% random moss ("smallpox" speckle).
- Everything the same scale; no building over ~2 storeys; repeated pieces
  are easy to spot (plains has 8 small-house templates).
- Terrain: houses perch on hills or sit on dirt skirts; paths climb any slope
  (wiki); no foundations stepping down slopes.
- No relation between building and street (fronts don't consistently face
  the road, no plots or fences delimiting lots).

## Checks we could test

Use vanilla as the floor: our generator's houses should match or beat it on
a facade-flatness score, distinct-material count and roof eave presence, and
never be smaller than a 5×5 interior with 3-high walls. A gallery that
renders the vanilla templates beside ours (via a tiny NBT → IR importer,
dev-time only, not shipped) would make the comparison visual.

## Verdict

Baseline, not a model. Borrow the biome palettes and minimum proportions;
the generator's job is the depth, scale variety, terrain fit and street
relationship that vanilla lacks.

## Could not verify

Footprints and overhangs were measured with a heuristic script and four
roof dumps, so a few rows (snowy, desert medium) may be off by a block. The
wiki's description of village sizes/house counts is qualitative; I didn't
generate villages in game. Older (pre-1.14) village behaviour not covered.
