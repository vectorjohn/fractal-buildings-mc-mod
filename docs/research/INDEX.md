# Research index

Statuses, set by John: `new` (not yet looked at), `reviewed` (read, no action),
`deep-dive` (spend more time on it), `drop` (stop researching it).
Agents check statuses before a research batch; the coordinator adds the lines.

This file is union-merged (`.gitattributes`): keep one note per line, in the
form `- [Name](track/slug.md) — one-line verdict — status: new`, appended at
the end of its track's list. Notes use [TEMPLATE.md](TEMPLATE.md).

## Track 1: Minecraft mods (`mods/`)

- [The Lost Cities](mods/the-lost-cities.md) — best prior art for order-independent chunk-by-chunk city planning (per-chunk Feature, no size limit, flattens terrain); wrong model for building variety (stacked 16×16 hand-made parts); MIT — status: new
- [YUNG's structure mods and API](mods/yungs-structure-mods.md) — reference for how far jigsaw stretches (128-block radius, 116 with terrain adaptation); study/link, don't copy; LGPL-3.0 — status: new
- [ChoiceTheorem's Overhauled Village](mods/ctov.md) — best-in-class templated villages (~2,000 templates), polite integration via vanilla village set + biome tags; ideas only; CC BY-NC-ND 4.0 — status: new
- [Towns and Towers](mods/towns-and-towers.md) — strongest reference for per-biome architectural themes; templated, no public source (jar is data only); CC BY-NC-ND 4.0 — status: new

## Track 2: GDMC (`gdmc/`)

## Track 3: PCG literature (`pcg/`)

- [Split grammars (Wonka et al. 2003)](pcg/split-grammars.md) — strong fit for building shells/façades on a voxel grid; borrow child-inside-parent (lot containment), per-floor/bay control grammar, one draw per rule per building — status: new
- [CGA shape grammar (Müller et al. 2006)](pcg/cga-shape-grammar.md) — best reference for massing+façades; adopt staged order, occlusion queries and snap lines via a typed Java rule API, not its rule language — status: new
- [Parish & Müller 2001, procedural cities](pcg/parish-muller-cities.md) — good model for roads+lots within a region cell; order-dependent, so run once per region cell and cache, never per chunk — status: new
- [WFC and model synthesis](pcg/wfc-model-synthesis.md) — only as a bounded detail filler with grammar-fixed borders and a fallback; no global structure, and chunked variants break order-independence — status: new

## Track 4: Worldgen internals (`worldgen/`)

## Track 5: Region overlays (`overlays/`)

- [TerraBlender](overlays/terrablender.md) — proves a biome-independent region layer works at scale, but wrong vehicle for cities (random blobs that replace biomes); LGPL-3.0, supports 26.3 — status: new
- [Lithostitched](overlays/lithostitched.md) — closest match to our settlement layer: domain-warped Voronoi region field plus density-gated structures, all data-driven; borrow the design, don't depend on it; MIT — status: new
- [Terralith](overlays/terralith.md) — compatibility target, not a model; overrides overworld noise, so cities must sit on top as structures with biome-tag palettes; licence restricts AI use (see ledger) — status: new
- [The Lost Cities: city placement](overlays/lost-cities-city-placement.md) — best placement model to port (position-seeded city field × biome multiplier, height veto); cities cross biome borders freely; MIT — status: new

## Track 6: Aesthetics (`aesthetics/`)

- [Depth and layering in walls](aesthetics/depth-and-layering.md) — adopt as the core façade model: walls as layers (plinth, frame, infill, recessed openings, floor bands, projections) with depth offsets; spacing rules mostly synthesised — status: new
- [Palettes and gradients](aesthetics/palettes-and-gradients.md) — adopt: each role maps to a 3–5 block light→dark list, a deterministic weathering field picks per cell, 60/30/10 main/secondary/accent; random moss is the anti-pattern — status: new
- [Roofs](aesthetics/roofs.md) — adopt: roof as a function of footprint, pitches 2:1/1:1/1:2, 1-block eave default, split sections past ~12-block gable span or ~15×15 footprint — status: new
- [Vanilla villages baseline](aesthetics/vanilla-villages.md) — baseline, not a model: measured sizes/materials of five variants (typical 5×5 walls, 3 high, 1:1 roof); borrow biome→material map, beat it on depth, scale, terrain fit, street facing — status: new

## Phase 2 spikes (`spikes/`)

