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
| [desert](desert.md) | desert, badlands (savanna) | courtyard house, adobe, flat roofs |
| [jungle](jungle.md) | jungles (swamps) | tropical stilt house, verandas, deep eaves |
| [snowy](snowy.md) | snowy biomes and peaks (stony peaks) | alpine and Nordic stone and timber |
| [taiga](taiga.md) | taigas (dark forest, windswept, pale garden) | Scandinavian log and plank |

Gaps worth a decision (for John):

- **No material set for tall buildings.** Every style's palette is a house
  palette, and a lot's style comes from the biome, so high-rises in a jungle
  or plains city use bamboo or birch walls, against
  [DS-1](../STANDARDS.md). Options: a per-style `tall` palette (stone, brick,
  terracotta, concrete, glass in the style's colours), or a shared
  urban/modern style that dense quarters use whatever the biome.
- **Savanna** borrows desert (Phase 5 gate question 10); a savanna brief
  (acacia, terracotta, thatch-like roofs) would be its own style.
- **Wealth and building type** don't change materials yet (a hall or temple
  in dressed stone, a cottage in rough).

To add a brief: copy a short one, keep the headings (Precedents, Materials,
Proportions and massing, Roof, Windows and doors, Avoid), stay under a page,
and add a row here.
