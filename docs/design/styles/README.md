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
  choice depend on height and tier. Still open for other biomes: a
  per-style `tall` palette (stone, brick, terracotta, concrete, glass in the
  style's colours), or a shared urban/modern style that dense quarters use
  whatever the biome.
- **Savanna** now has its own brief ([savanna.md](savanna.md); John,
  2026-10-25); the style file is ROADMAP 7.6, desert stands in until then.
- **Wealth and building type** don't change materials yet (a hall or temple
  in dressed stone, a cottage in rough).

To add a brief: copy a short one, keep the headings (Precedents, Materials,
Proportions and massing, Roof, Windows and doors, Avoid), stay under a page,
and add a row here.
