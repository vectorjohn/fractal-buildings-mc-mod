# Block notes

Hand-written judgement for the block reference [blocks.md](blocks.md), which
`scripts/design-blocks.sh` generates from this file and the game (block ids,
variants and colours are never typed here; the script derives them and fails
on an id that isn't in the registry). Edit the cells, then rerun the script:
it also refreshes the table in the architect and interior-designer agents.

Rules for the table rows below:

- One row per material family. **Bases**: block ids; the script finds each
  one's variants (`_stairs`, `_slab`, `_wall`, `_fence`, `_door`, ...).
  `{dye}` expands to the 16 dye colours. `id:stem` sets the stem used to look
  up variants (`id:` = look up none), for ids whose name would match the
  wrong family.
- **Colour**: `auto` (texture mean of each base), `none`, or a short text.
- Keep cells short: the generated table is paid on every agent call (budget
  about 5k tokens). Biomes are vanilla biome names or our styles (medieval,
  plains, desert, jungle, snowy, taiga, modern, classical, gothic, fantasy).
- **High-rise**: Y = fits tall urban buildings, accent = trim only, N = keep
  to small or rural buildings, timber = planks and stripped wood (never
  logs) as walls only in the mass-timber style up to 20 storeys, else
  accent ([DS-1](STANDARDS.md)).
- Every stairs/slab/wall block in the game must belong to a row here or to
  the Ignore line, so a Minecraft update that adds one fails the script.

Ignore: petrified_oak_slab

# Woods

| Family | Bases | Colour | Biomes / styles | Uses | High-rise |
|---|---|---|---|---|---|
| oak | oak_planks oak_log stripped_oak_log | auto | plains, forest, meadow; medieval, plains villages | all-round walls and floors; log as timber frame and posts | timber |
| spruce | spruce_planks spruce_log stripped_spruce_log | auto | taiga, snowy, grove, mountains | roofs, timber frame, cabins, dark floors | timber |
| birch | birch_planks birch_log stripped_birch_log | auto | birch forest, meadow, plains | light walls and floors, bright interiors | timber |
| jungle | jungle_planks jungle_log stripped_jungle_log | auto | jungle, bamboo jungle | huts, treehouses, stilts, warm floors | N |
| acacia | acacia_planks acacia_log stripped_acacia_log | auto | savanna, badlands edge | orange walls/roofs with grey bark posts | N |
| dark oak | dark_oak_planks dark_oak_log stripped_dark_oak_log | auto | dark forest; mansions, gothic, taiga | roofs, framing, rich floors, furniture | accent |
| mangrove | mangrove_planks mangrove_log stripped_mangrove_log mangrove_roots:  | auto | mangrove swamp, swamp | red-brown walls; roots as stilt/lattice screens | N |
| cherry | cherry_planks cherry_log stripped_cherry_log | auto | cherry grove; East-Asian-inspired | pink walls, floors, delicate trim | N |
| pale oak | pale_oak_planks pale_oak_log stripped_pale_oak_log | auto | pale garden; modern, gothic | near-white floors and walls, minimal interiors | timber |
| poplar | poplar_planks poplar_log stripped_poplar_log | auto | dappled forest (new in 26.x) | light walls and floors; untried by us, check in game | N |
| bamboo | bamboo_planks bamboo_mosaic bamboo_block: stripped_bamboo_block: | auto | bamboo jungle, jungle; tropical | yellow walls, mosaic floors, block as poles | timber, patterned only |
| crimson | crimson_planks crimson_stem stripped_crimson_stem | auto | nether, crimson forest; fantasy | magenta-red accents; nether woods don't burn | accent |
| warped | warped_planks warped_stem stripped_warped_stem | auto | nether, warped forest; fantasy | teal accents, doors; fireproof | accent |

# Stone and rock

| Family | Bases | Colour | Biomes / styles | Uses | High-rise |
|---|---|---|---|---|---|
| stone | stone smooth_stone cobblestone mossy_cobblestone | auto | everywhere; medieval, rural | foundations, plinths (cobble), smooth stone as clean sills and floors | Y |
| stone bricks | stone_bricks mossy_stone_bricks cracked_stone_bricks chiseled_stone_bricks | auto | temperate, cold, mountains; medieval, castles | walls, towers, civic buildings; mossy/cracked as wear | Y |
| granite | granite polished_granite | auto | stone patches everywhere; warm | warm pink-brown floors and trim | Y |
| diorite | diorite polished_diorite | auto | stone patches everywhere | white-grey trim, columns, classical accents | Y |
| andesite | andesite polished_andesite | auto | stone patches everywhere | neutral grey paving, plinths, floors | Y |
| deepslate | deepslate:  cobbled_deepslate polished_deepslate deepslate_bricks cracked_deepslate_bricks deepslate_tiles cracked_deepslate_tiles chiseled_deepslate | auto | deep caves, mountains, cold; gothic, industrial | dark walls, slate-look roofs (tiles), heavy bases | Y |
| tuff | tuff polished_tuff tuff_bricks chiseled_tuff chiseled_tuff_bricks | auto | deep caves, trial chambers; modern | grey-green walls, modern facades | Y |
| blackstone | blackstone polished_blackstone polished_blackstone_bricks cracked_polished_blackstone_bricks chiseled_polished_blackstone gilded_blackstone | auto | nether, bastions; gothic, fortress | black walls, plinths, gothic trim; gilded as rare accent | Y |
| basalt | basalt polished_basalt smooth_basalt | auto | basalt deltas, nether; industrial | column-grain pillars, dark smooth facing | Y |
| calcite / dripstone | calcite dripstone_block | auto | stony peaks, geodes; dripstone caves | calcite as white render; dripstone as tan rubble | accent |
| sandstone | sandstone smooth_sandstone cut_sandstone chiseled_sandstone | auto | desert, beach; desert villages, Egyptian | walls, roofs, paving; chiseled as frieze | Y |
| red sandstone | red_sandstone smooth_red_sandstone cut_red_sandstone chiseled_red_sandstone | auto | badlands; adobe, Mesa styles | orange walls, roofs, trim | Y |
| cinnabar | cinnabar polished_cinnabar cinnabar_bricks chiseled_cinnabar | auto | sulfur caves (new in 26.x) | red stone accents; untried by us, check in game | accent |
| sulfur | sulfur polished_sulfur sulfur_bricks chiseled_sulfur | auto | sulfur caves (new in 26.x) | yellow stone accents; untried by us, check in game | accent |

# Brick, earth, clay, concrete

| Family | Bases | Colour | Biomes / styles | Uses | High-rise |
|---|---|---|---|---|---|
| bricks | bricks | auto | temperate towns; medieval, industrial, Victorian | walls, chimneys, roofs (stairs) | Y |
| mud | mud_bricks packed_mud mud | auto | desert, savanna, mangrove swamp; adobe | adobe walls; packed mud as rammed earth; mud as wet ground | N |
| terracotta | terracotta | auto | badlands | warm brown-orange walls, roof tiles look | Y |
| dyed terracotta | {dye}_terracotta | auto | badlands strata; plaster everywhere | muted wall colours: white = plaster/stucco, yellow/orange/brown = render | Y |
| glazed terracotta | {dye}_glazed_terracotta | auto | warm ocean ruins; Mediterranean, Moorish | patterned floor and wall tiles, accents (rotates with facing) | accent |
| concrete | {dye}_concrete {dye}_concrete_powder | auto | modern towns only | flat vivid walls, modern facades; powder falls (gravity) | Y |
| quartz | quartz_block smooth_quartz quartz_bricks quartz_pillar chiseled_quartz_block | auto | nether; classical, modern | white walls, columns (pillar), cornices | Y |
| prismarine | prismarine prismarine_bricks dark_prismarine sea_lantern | auto | ocean monuments; coastal, fantasy | teal walls and roofs; sea lantern as light | accent |
| nether bricks | nether_bricks red_nether_bricks cracked_nether_bricks chiseled_nether_bricks | auto | nether fortresses; gothic, fantasy | dark red-purple walls, fences, fortress trim | Y |
| end and purpur | end_stone end_stone_bricks purpur_block purpur_pillar | auto | the End; fantasy | pale yellow and lilac walls, pillars | accent |
| resin | resin_block resin_bricks chiseled_resin_bricks | auto | pale garden | orange brick accents | accent |

# Metal and glass

| Family | Bases | Colour | Biomes / styles | Uses | High-rise |
|---|---|---|---|---|---|
| copper | copper_block cut_copper chiseled_copper | auto | everywhere; industrial, steampunk, modern | roofs, cladding, doors, grates, lanterns; oxidises over time unless waxed | Y |
| exposed copper | exposed_copper exposed_cut_copper exposed_chiseled_copper | auto | as copper | stage 2 of 4: mottled roofs | Y |
| weathered copper | weathered_copper weathered_cut_copper weathered_chiseled_copper | auto | as copper | stage 3 of 4: blue-green roofs | Y |
| oxidized copper | oxidized_copper oxidized_cut_copper oxidized_chiseled_copper | auto | as copper; old towns, churches | verdigris roofs, spires, domes | Y |
| iron | iron_block | auto | everywhere; industrial, modern | iron bars as railings and window grilles, doors, chains | Y |
| glass | glass tinted_glass {dye}_stained_glass | auto | everywhere | windows (panes thinner); tinted blocks light; stained for churches, shops | Y |

# Soft, organic, ground

| Family | Bases | Colour | Biomes / styles | Uses | High-rise |
|---|---|---|---|---|---|
| wool | {dye}_wool | auto | everywhere; tents, interiors | carpets as rugs/tablecloths, upholstery, awnings, banners; burns | N |
| thatch and organic | hay_block dried_kelp_block moss_block bone_block mushroom_stem | auto | plains, savanna, swamp; rustic | hay as thatch roofs, moss as green roofs/carpet, mushroom stem as pale wall | N |
| snow and ice | snow_block packed_ice blue_ice | auto | snowy plains, ice spikes; igloos, snowy villages | igloo walls, snowy roofs, ice floors and windows | N |
| ground and paths | dirt_path gravel coarse_dirt rooted_dirt | auto | everywhere | roads, yards; gravel falls (gravity) | N |
| leaves | oak_leaves spruce_leaves birch_leaves jungle_leaves acacia_leaves dark_oak_leaves mangrove_leaves cherry_leaves pale_oak_leaves azalea_leaves flowering_azalea_leaves | biome-tinted green (cherry pink, pale oak grey) | as their wood | hedges, green roofs, planters (set persistent) | N |

# Furnishing

| Family | Bases | Colour | Biomes / styles | Uses | High-rise |
|---|---|---|---|---|---|
| lighting | lantern soul_lantern torch candle sea_lantern glowstone shroomlight ochre_froglight verdant_froglight pearlescent_froglight redstone_lamp end_rod campfire soul_campfire | none | lantern rustic; froglights/sea lantern modern; end rod modern lamps | hang lanterns from chains; hide light blocks under carpet or behind trapdoors | Y |
| storage and shelves | chest barrel bookshelf chiseled_bookshelf decorated_pot flower_pot | none | everywhere | shops, homes; barrels as stools/tubs; every wood has a `_shelf` | Y |
| workstations | crafting_table furnace smoker blast_furnace cartography_table fletching_table smithing_table loom stonecutter grindstone lectern brewing_stand cauldron composter anvil bell | none | villages: each marks a trade | workshops; smoker/furnace as stove; cauldron as sink | Y |
| beds and soft | {dye}_bed {dye}_carpet {dye}_banner | none | everywhere | bedrooms; carpets as rugs; banners as flags and curtains | Y |
| furniture tricks | oak_stairs oak_trapdoor oak_fence oak_pressure_plate oak_sign iron_trapdoor | none | any wood or metal | chair = stairs (+ sign/trapdoor arms); table = fence + pressure plate or carpet; shutters = trapdoors beside windows; counter = slabs | Y |
