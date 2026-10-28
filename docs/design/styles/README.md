# Style briefs

One page per style the generator has: the tradition it draws on, its
materials, proportions, roof, windows and doors, and what to avoid. The
`architect` and `interior-designer` judge a building against its style's
brief. Briefs are **proposed** (agent-drafted) until John marks one
accepted; the style files in `planner-core/src/main/resources/styles/` are
the code, a brief is the intent, and a gap between the two is a finding.

| Style | Biomes (own; fallback in brackets) | Tradition |
|---|---|---|
| [plain](plain.md) | everything unclaimed | neutral small-town house; the base all styles extend |
| [medieval](medieval.md) | none (by name) | northern European timber-framed town |
| [plains](plains.md) | plains, meadow, forests (cherry grove, beach) | rural farmhouse and cottage |
| [desert](desert.md) | desert, badlands (savanna, until its own style) | courtyard house, adobe, flat roofs |
| [jungle](jungle.md) | jungles (swamps) | tropical stilt house, verandas, deep eaves |
| [snowy](snowy.md) | snowy biomes and peaks (stony peaks) | alpine and Nordic stone and timber |
| [taiga](taiga.md) | taigas (dark forest, windswept, pale garden) | Scandinavian log and plank |
| [savanna](savanna.md) | none yet (savanna biomes use desert until ROADMAP 7.6) | Sahel mud brick, thatched compounds, acacia and terracotta |
| [mass-timber](mass-timber.md) | none yet (forested biomes, mid-rise and tall, ROADMAP 7.7) | modern engineered timber (CLT, glulam), up to 20 storeys |

Savanna and mass-timber are briefs without a style file yet (John,
2026-10-25).

Tiers ([DS-37](../STANDARDS.md), John 2026-10-25): cities and
metropolises are modern, so the older-look styles above (medieval, and the
biome house styles as they stand) are for hamlets, villages and, by default,
towns; a city lot gets a modern or tall look, with the biome still tinting
its colours and local materials (ROADMAP 7.7). Gap: no modern style exists
yet for low and mid-rise city buildings.

Gaps worth a decision (for John):

- **No material set for tall buildings.** Every style's palette is a house
  palette, and a lot's style comes from the biome, so high-rises in a jungle
  or plains city use bamboo or birch walls, against
  [DS-1](../STANDARDS.md). Partly answered (John, 2026-10-25): in forested
  biomes, tall buildings use the [mass-timber](mass-timber.md) style (wood
  allowed up to 20 storeys in that look only), and ROADMAP 7.7 makes style
  choice depend on height and tier. Answered for other biomes (John,
  2026-10-26, [DS-41](../STANDARDS.md)): each style gets a `tall` variant
  (stone, brick, terracotta, concrete, glass in the style's colours) that
  inherits a shared plain default, so a style without one gets the shared
  look; later, over about 20 storeys in a metropolis core, one shared glass
  tower look tinted only at the base.
- **Savanna** now has its own brief ([savanna.md](savanna.md); John,
  2026-10-25); the style file is ROADMAP 7.6, desert stands in until then.
- **Wealth and building type** don't change materials yet (a hall or temple
  in dressed stone, a cottage in rough).
To add a brief: copy a short one, keep the headings (Precedents, Materials,
Proportions and massing, Roof, Windows and doors, Avoid), stay under a page,
and add a row here.

# Road surfaces

One material per road ([DS-30](../STANDARDS.md)), by tier and biome. The
table is the architect's (2026-10-25), moved here from DS-30; which blocks
have slabs and stairs is in [blocks.md](../blocks.md) (`dirt_path` and
`gravel` have none, so they keep full-block steps, DS-31).

| Where | Material | Wear (cracked or mossy, at most 15 % of cells, scattered; agent's number) |
|---|---|---|
| Tracks: paths, hamlet and village streets, roads between settlements below city | `dirt_path`; `gravel` in snowy, mountain, stony and gravelly biomes; `mud_bricks` in desert, badlands, savanna | none |
| Town streets (no city is non-modern, DS-37) | `stone_bricks`; `smooth_sandstone` in desert, badlands, savanna (John 2026-10-26, ROADS1: keeps the street hierarchy above `mud_bricks` tracks) | stone: `cracked_stone_bricks` (full blocks only), `mossy_stone_bricks` (any shape; jungle, swamp, taiga, dark forest only) |
| City and metropolis arterials, main streets, roads between cities | `cobbled_deepslate` ("asphalt"; open: check in game against `polished_deepslate` and `gray_concrete`, below) | none exists |
| City and metropolis streets and lanes | `light_gray_concrete` | none exists |
| Sidewalks (DS-31) | `white_concrete` | none |
| Bridge decks | city and metropolis, and roads between two cities: the road's own material ([DS-44](../STANDARDS.md)); elsewhere the road's material or one plank species; bamboo only on an all-bamboo footbridge (DS-30 amendment) | as the road |

All-bamboo footbridge (DS-30 amendment): deck `bamboo_planks` or `bamboo_mosaic`,
rails `bamboo_fence`, posts `bamboo_block`.

ROADS1 follow-up (architect review 2026-10-26, accepted by John
2026-10-26, coordinator chat; ROADMAP 7.10):

- **Desert, badlands and savanna** take the desert road set (tracks
  `mud_bricks`, town streets `smooth_sandstone`, which has slab and stairs
  and is the vernacular desert-village look) until savanna (7.6) or
  badlands get their own rows.
- **Plaza paving is one calm material** ([DS-2](../STANDARDS.md)): one
  block per style, plus the same wear allowance as roads, not a per-cell
  mix such as stone bricks with cobblestone. Quays are a street kind, so
  they get one surface too (DS-30).
- **Is cobbled deepslate "asphalt"?** Dark enough from 30 blocks, but up
  close it reads as cobbles. Compare it in game against `polished_deepslate`
  (smooth, slab and stairs) and `gray_concrete` (slab and stairs, [blocks.md](../blocks.md))
  next to the light grey streets, and keep whichever reads as asphalt;
  John has the last look.
