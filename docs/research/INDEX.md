# Research index

Statuses, set by John: `new` (not yet looked at), `reviewed` (read, no action),
`deep-dive` (spend more time on it), `drop` (stop researching it).
Agents check statuses before a research batch; the coordinator adds the lines.

This file is union-merged (`.gitattributes`): keep one note per line, in the
form `- [Name](track/slug.md) — one-line verdict — status: new`, appended at
the end of its track's list. Notes use [TEMPLATE.md](TEMPLATE.md).

## Track 1: Minecraft mods (`mods/`)

- [The Lost Cities](mods/the-lost-cities.md) — best prior art for order-independent chunk-by-chunk city planning (per-chunk Feature, no size limit, flattens terrain); wrong model for building variety (stacked 16×16 hand-made parts); MIT — status: reviewed
- [YUNG's structure mods and API](mods/yungs-structure-mods.md) — reference for how far jigsaw stretches (128-block radius, 116 with terrain adaptation); study/link, don't copy; LGPL-3.0 — status: drop
- [ChoiceTheorem's Overhauled Village](mods/ctov.md) — best-in-class templated villages (~2,000 templates), polite integration via vanilla village set + biome tags; ideas only; CC BY-NC-ND 4.0 — status: drop
- [Towns and Towers](mods/towns-and-towers.md) — strongest reference for per-biome architectural themes; templated, no public source (jar is data only); CC BY-NC-ND 4.0 — status: reviewed
- [Repurposed Structures](mods/repurposed-structures.md) — widest biome-variant catalogue (16 village styles, ~3,500 NBTs) and useful site checks (biome radius, terrain range, liquid, feature-cancelling tags); same jigsaw size wall; LGPL-3.0, read don't copy — status: reviewed
- [Moog's structures](mods/moogs-structures.md) — scattered single-piece templates, not settlements; take per-set disable toggle, spacing slider and strict flatness veto; LGPL-3.0 code, NBTs All Rights Reserved (not opened) — status: reviewed
- [Recurrent Complex](mods/recurrent-complex.md) — best prior art for rule-driven variation: plan-once transformers, inherited boolean variables, reachability-checked room-grid mazes (MC-free, tested core); 1.12-era, order-dependent; MIT + Apache-2.0 maze core — status: reviewed
- [Astruct](mods/astruct.md) — cautionary prototype: off-thread jigsaw placed near players only, clamped to 64–256 blocks, single Y, no blending, player-path-dependent; only its hashed cell centres are worth borrowing; MIT — status: reviewed

## Track 2: GDMC (`gdmc/`)

- [GDMC-HTTP and GDPC](gdmc/gdmc-http-and-gdpc.md) — reference and optional tool for recording terrain fixtures, not a dependency; its heightmap types map onto our probe interfaces; MIT — status: reviewed
- [GDMC competition](gdmc/gdmc-competition.md) — adopt its four criteria and human-anchored 0–10 scale as our review rubric; its common failures (terraforming scars, floating foundations, buried doors) become automated checks — status: reviewed
- [Mike's Angels (GDMC 2022 winner)](gdmc/mikes-angels-2022-iterative-planning.md) — strong city-layout reference: plots cut from road frontage scored on slope/water/height; seeded-agent planning loop may suit a region planner; no licence, ideas only — status: reviewed
- [Frightful Hobgoblin (GDMC 2024 winner)](gdmc/frightful-hobgoblin-2024.md) — reference for terrain fit (site cost, cut-and-fill foundations, retaining walls, traffic-worn paths); simulation approach doesn't fit worldgen; MIT + extra condition, ideas only — status: reviewed
- [JEB_GEN_ (GDMC 2023 winner)](gdmc/jeb-gen-2023.md) — best adaptability mostly by rejecting bad sites (fill >3/cell, height spread) via prefix-sum lot stats; take its A* road cost (no 2-block steps, climb/water penalties, road reuse discount, ellipse bound); no licence, ideas only — status: reviewed
- [Gaussianly Filtered (GDMC 2025 winner)](gdmc/gaussianly-filtered-2025.md) — paths merge into lanes by nudging A* toward a blurred path map; portable: Gaussian-blur grading around house pads, doors face downhill; all local; CC BY-NC 4.0, ideas only — status: new
- [Grimoire / TOME](gdmc/grimoire-tome.md) — districts grown from seeds with height-change cost so borders fall on ridges/water; block rings become roads so every lot fronts a street; MST main roads; fits a cached region planner; no licence, ideas only — status: new
- [Hauck's UrbanSettlementGenerator (2019)](gdmc/hauck-urban-settlement-generator.md) — repeated partitions keep the flattest non-overlapping lots (vs modal height); MST roads via A* with cost 1+Δh²; MIT but Python 2/MCEdit, reference only — status: new
- [GDMC first-year report (2018 entries)](gdmc/gdmc-first-year-report.md) — two families (greedy scored placement vs top-down splitting); failure checklist: doors facing water, unbridged rivers, no map-wide adaptation; ideas only — status: new

## Track 3: PCG literature (`pcg/`)

- [Split grammars (Wonka et al. 2003)](pcg/split-grammars.md) — strong fit for building shells/façades on a voxel grid; borrow child-inside-parent (lot containment), per-floor/bay control grammar, one draw per rule per building — status: deep-dive
- [CGA shape grammar (Müller et al. 2006)](pcg/cga-shape-grammar.md) — best reference for massing+façades; adopt staged order, occlusion queries and snap lines via a typed Java rule API, not its rule language — status: deep-dive
- [Split/CGA grammars deep-dive](pcg/grammars-deep-dive.md) — adopt split/repeat/comp as a typed Java API on integer boxes; exact integer split solver (mirrored leftover: symmetric on 28/28 widths in a throwaway proof); rendezvous-hashed rule choice per scope; context queries read earlier phases only; massing = volume assembly with a lot-containment check, roofs as heightfield; no grammar language; closest prior art Eger 2022 (CRAPL, ideas only) — status: new
- [Parish & Müller 2001, procedural cities](pcg/parish-muller-cities.md) — good model for roads+lots within a region cell; order-dependent, so run once per region cell and cache, never per chunk — status: reviewed
- [WFC and model synthesis](pcg/wfc-model-synthesis.md) — only as a bounded detail filler with grammar-fixed borders and a fallback; no global structure, and chunked variants break order-independence — status: new
- [Townscaper](pcg/townscaper.md) — borrow coarse occupancy grid → neighbour-derived modules as the massing→blocks stage; skip the irregular grid; WFC only as a bounded variant picker; proprietary (Sylves reimplementation MIT) — status: new
- [Watabou city generators](pcg/watabou-city-generators.md) — compact reference for district-typed city plans (Voronoi wards, streets on ward borders, recursive lot bisection); run once per region cell; TownGeneratorOS GPL-3.0, ideas only — status: new
- [Merrell et al. 2010, residential layouts](pcg/merrell-residential-layouts.md) — best model of a believable house brief (room graph, privacy gradient, stacking/lot checks usable as validators); too slow and not guaranteed correct to be the generator — status: new
- [Lopes et al. 2010, constrained-growth floor plans](pcg/constrained-growth-floor-plans.md) — best fit for interiors: grid-native, ms–1 s, reachability guaranteed by a door pass; run after the shell grammar, fed by a Merrell-style room program — status: new
- [MarkovJunior](pcg/markovjunior.md) — toolbox of bounded grid rewrite passes (cleanup, detail, reachability checks) after the grammar, not the generator; MIT, small subset portable to Java — status: new
- [Chen et al. 2008, tensor-field streets](pcg/tensor-field-streets.md) — use its terrain-derived direction field (roads follow contours/coasts) and density-spacing rule for outskirts and paths; keep bisection/growth for the core; order-dependent, run once per region cell — status: new
- [Vanegas et al. 2012, parcel subdivision](pcg/parcel-subdivision.md) — best lot-subdivision reference: recursive OBB split with street-access rule and area/frontage bounds (integer bisection on axis-aligned blocks); each block independent; skeleton strips later — status: new
- [Straight-skeleton roofs](pcg/straight-skeleton-roofs.md) — on grid footprints hip height = chessboard distance to outside; gables/cross-gables as max over wings; "closed roof" = full coverage + filled steps >1; campskeleton (Apache-2.0) / kendzi (BSD-3) in reserve, CGAL GPL — status: new
- [Kelly & McCabe 2006 city survey](pcg/kelly-mccabe-city-survey.md) — low value now; useful pointers: Greuter's hash-seeded on-demand city (precedent for our determinism model) and Sun's least-elevation road stepping — status: reviewed
- [Greuter et al. 2003, pseudo-infinite cities](pcg/greuter-pseudo-infinite-cities.md) — confirms our determinism model (hashed seed per cell, on-demand, discardable cache); cells never interact, hence our region plan; suggests an evict-and-regenerate byte-compare test — status: new
- [Bao 2013 / Wu 2018 optimisation layouts](pcg/optimisation-layouts-bao-wu.md) — too slow for worldgen and Wu's time-limited solver breaks determinism; borrow Wu's constraints as IR checks, regions→aisles→rooms for big buildings, Bao's soft terms for generate-N-keep-best — status: new
- [Nystrom, rooms and mazes](pcg/rooms-and-mazes.md) — adopt its connect pass (spanning tree from the entrance + tunable extra doors) and order-independent dead-end removal as the interior reachability/repair stage; skip the maze; MIT — status: new

## Track 4: Worldgen internals (`worldgen/`)

- [Structure pipeline (26.3)](worldgen/structure-pipeline.md) — structure-based integration fits; hard limits for every structure type: blocks >8 chunks from the start chunk are silently dropped, one start per structure per chunk; placement pluggable (subclass RandomSpreadStructurePlacement to keep /locate) — status: new
- [Terrain height and biome queries](worldgen/terrain-biome-queries.md) — live probes viable (getBaseHeight ~0.2 ms/column, order-independent); biome lookups are order-dependent at exact climate ties (fix: fixed priming lookup) and 3D (sample at surface Y) — status: new
- [Jigsaw assembly and terrain adaptation](worldgen/jigsaw.md) — don't use jigsaw assembly for layout (greedy, no backtracking, 128-block cap); do reuse vanilla terrain adaptation, which grades custom pieces as rigid boxes for free — status: new
- [Extension points (26.3)](worldgen/extension-points.md) — structure-based is the only option with first-class hooks; drop custom chunk generator (final NoiseBasedChunkGenerator) and biome overlay (Fabric API can't add overworld biomes); city-mask density function is a viable flattening fallback — status: new
- [Surface level and flattening](worldgen/surface-level-and-flattening.md) — preliminary_surface_level is a cheap screen (~1/50 cost, 12–15 blocks low, p90 8 after bias) not a foundation probe; flattening must wrap final_density, chunk_surface_level and aquifer surface_level, prefer fill to cut; plan reads unwrapped terrain — status: new
- [Keeping structures out of cities](worldgen/keeping-structures-out.md) — use our own small veto mixin on tryGenerateStructure and StructureCheck.canCreateStructure, driven by a structure tag; exclusion zones and biome tags can't do it; Lithostitched spawn condition works but adds a dependency — status: new
- [Custom generator and procedural pool element](worldgen/custom-generator-and-pool-element.md) — drop the custom chunk generator (non-NoiseBased generators get an empty-router RandomState); a procedural StructurePoolElement could put our houses into vanilla villages — worth a small spike — status: new

## Track 5: Region overlays (`overlays/`)

- [TerraBlender](overlays/terrablender.md) — proves a biome-independent region layer works at scale, but wrong vehicle for cities (random blobs that replace biomes); LGPL-3.0, supports 26.3 — status: new
- [Lithostitched](overlays/lithostitched.md) — closest match to our settlement layer: domain-warped Voronoi region field plus density-gated structures, all data-driven; borrow the design, don't depend on it; MIT — status: deep-dive
- [Lithostitched deep-dive](overlays/lithostitched-deep-dive.md) — borrow design, don't depend (~80 mixins, replaces jigsaw assembler); take hashed 2D Voronoi with per-cell decisions at the site and priority-ordered slot wrapping; flaws: seed truncated to 32 bits, 3D cellular sliced at y=0 (17% sliver cells); settles flattening: wrap final_density + chunk_surface_level + aquifer surface_level, never the shared preliminary_surface_level entry; MIT — status: new
- [Terralith](overlays/terralith.md) — compatibility target, not a model: data-only layered on vanilla's noise router; wrap router slots (final_density + preliminary_surface_level) and use its c: biome tags for palettes; height estimates miss dunes/arches — status: new
- [The Lost Cities: city placement](overlays/lost-cities-city-placement.md) — best placement model to port (position-seeded city field × biome multiplier, height veto); cities cross biome borders freely; MIT — status: new
- [Tectonic](overlays/tectonic.md) — terrain can be flattened inside city cells compatibly: wrap final density with a cached city mask (not "depth"); spike candidate and compat-test target, not a dependency; MIT (repo LICENSE says CC0) — status: new
- [Biolith](overlays/biolith.md) — third biome-injection approach (edits vanilla biome table + per-lookup adjust); can't define settlement regions, compat target only; re-implement its BiomePerimeters idea for border palette blending; LGPL-3.0 — status: new
- [The Lost Cities: road planning](overlays/lost-cities-road-planning.md) — strong deterministic road model (world-wide hashed 3-tier street grid clipped to city; highway hub per 128×128-chunk cell, mutual-choice links); avoid its grid-straight geometry and save-dependent hub choices; MIT — status: new

## Track 6: Aesthetics (`aesthetics/`)

- [Depth and layering in walls](aesthetics/depth-and-layering.md) — adopt as the core façade model: walls as layers (plinth, frame, infill, recessed openings, floor bands, projections) with depth offsets; spacing rules mostly synthesised — status: new
- [Palettes and gradients](aesthetics/palettes-and-gradients.md) — adopt: each role maps to a 3–5 block light→dark list, a deterministic weathering field picks per cell, 60/30/10 main/secondary/accent; random moss is the anti-pattern — status: new
- [Roofs](aesthetics/roofs.md) — adopt: roof as a function of footprint, pitches 2:1/1:1/1:2, 1-block eave default, split sections past ~12-block gable span or ~15×15 footprint — status: new
- [Vanilla villages baseline](aesthetics/vanilla-villages.md) — baseline, not a model: measured sizes/materials of five variants (typical 5×5 walls, 3 high, 1:1 roof); borrow biome→material map, beat it on depth, scale, terrain fit, street facing — status: new
- [vibecraft rule sheets](aesthetics/vibecraft.md) — useful numbers: scale table, window-to-wall ratios (0.10 fortress → 0.30 modern), window rhythm, element × primary/secondary/accent palette; ignore its randomised-material tip; MIT, no code — status: new
- [Automatic block colour table](aesthetics/block-colour-table.md) — dev-time palette check only (lightness + busyness from Loom's cached client jar); commit script and orderings, not textures; never at runtime; finding: stone variants order weathering, not shade — status: new
- [WesterosCraft / Conquest style guides](aesthetics/style-guides-westeroscraft-conquest.md) — little geometry; adopt consistency rules (one timber + one infill colour, thatch only ≥1:1, ornament for wealth) and a style × class × region model; caps façades at 2 depth layers — status: new
- [Roof forms and massing](aesthetics/roof-forms-and-massing.md) — roof = pitch profile over each cell's distance to footprint edge, max over parts (equals straight skeleton for grid footprints); support gable, hip (L/T/U), shed, flat+parapet, helm first; never curved — status: new

## Track 7: Out-of-worldgen generation (`postgen/`)

- [Distant Horizons](postgen/distant-horizons.md) — players accept terrain 256 chunks out, but it is mostly unsaved LOD (cuts structures); its real-chunk mode is the pregenerator ticket loop and can lag servers; borrow ticket pattern + throttles; LGPL-3.0 — status: new
- [Chunk pregenerators](postgen/pregenerators.md) — Chunky: loading ticket to FULL, ~50 in flight; 26.3 has public `addTicketAndLoadWithRadius`, no dependency needed; vanilla ~40 chunks/s (≈100 s per 64×64 region), C2ME ~450 (unmeasured here); Chunky GPL-3.0, C2ME MIT — status: new
- [Post-generation placement](postgen/post-generation-placement.md) — `/place structure` writes every chunk of the box on the live level with no 8-chunk cap but no `/locate` registration; `LevelChunk.setBlockState` handles heightmaps, light, saving; problems are cost and pop-in, not correctness; 26.3 claims to confirm listed — status: new
- [Out-of-worldgen overview](postgen/overview.md) — pipeline: deterministic sites → pregenerate on trigger → snapshot terrain → plan off-thread → write unseen; elytra outruns it; verdict: backup/add-on (admin capitals, extras beyond 8 chunks), not the main path — status: new

## Track 8: Precomputed building pools (`pool/`)

## Synthesis

- [Landscape synthesis (1.8)](synthesis.md) — buildings: volume massing in lot hull + typed Java split/repeat/comp grammar for floors/bays/façades, roofs as heightfield, room-graph interiors; integration: structures, one start per district, cached per-cell plan, per-chunk feature fallback, track 7 backup; gate decisions B1–B4, I1–I6 with recommended answers — status: new

## Phase 2 spikes (`spikes/`)

