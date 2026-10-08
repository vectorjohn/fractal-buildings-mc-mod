# Towns and Towers (T&T)

**What it is.** A very popular (18M+ Modrinth downloads) structure pack by
Kubek, Biban_Auriu and Cristelknight999 adding biome-themed villages, pillager
outposts and ships. [Modrinth](https://modrinth.com/mod/towns-and-towers),
[CurseForge](https://www.curseforge.com/minecraft/mc-mods/towns-and-towers).
No public source repo (its own config text says "GitHub Repository: [NEED TO
MAKE ONE]"); read the published jar `t_and_t-fabric-neoforge-1.13.12.jar`
(Minecraft 26.3, Sept 2026).

**Approach.** A **pure datapack in a jar**: zero Java classes, 840 NBT pieces,
44 structures, and a dependency on Cristel Lib for config. Each village is a
standard `minecraft:jigsaw` structure with a cultural theme per biome (meadow →
Swiss, flower forest → Japanese, badlands → pueblo, snowy taiga → Viking,
birch forest → Romanian, …), each with pools `town_centers`, `streets`,
`houses`, `decor`, `terminators` (and `dogs`); about 35 NBTs per theme.
"Exclusive" themes (Iberian, Nilotic, Tudor, Oriental, …) appear only when a
biome mod supplies their biomes.

**Procedural vs templated.** Entirely templated; procedural only in jigsaw
assembly. Variety per village is low (`size` 4, i.e. depth 4) but the theme
count is high.

**Worldgen hook.** Vanilla jigsaw, `beard_thin` terrain adaptation,
`project_start_to_heightmap: WORLD_SURFACE_WG`, `max_distance_from_center: 116`
(the vanilla cap with terrain adaptation). Unlike CTOV it uses **its own
structure sets** (`towns`, `towers`, `other`) with spacing/separation 48/24
chunks for towns and towers and 32/16 for others, so it **adds** settlements
on its own grid alongside vanilla villages; Cristel Lib exposes those numbers
as a config file. Oddly, almost every structure uses generation step
`fluid_springs` rather than `surface_structures` (reason not documented; it
places them late, after lakes).

**Determinism.** Vanilla jigsaw: planned from seed and start chunk,
chunk-independent.

**Biome adaptation.** One theme per biome via **biome tags** listing the vanilla
biome plus optional (`"required": false`) biomes from Terralith, BoP,
Wythers, Tectonic, Regions Unexplored, BWG, Traverse, … so compatibility is
hand-maintained per biome mod. This is the most explicit "culture follows
biome" mapping of the mods read so far.

**Licence.** **CC BY-NC-ND 4.0** per the jar's `LICENSE_Towns_and_Towers` and
`fabric.mod.json` ("you do not make any changes to the mod aside from config
files"). Modrinth's licence field says CC BY-NC-SA 4.0; the jar is the
stricter, so treat it as ND. Study only; copy nothing.

**What to borrow (ideas only).**
- Biome → architectural culture mapping as a style-selection table, with
  optional entries for modded biomes. A good model for our style/palette
  selection.
- Separate structure sets with exposed spacing config, if we add rather than
  replace villages.
- Proof that a pure-data jigsaw pack can be very popular: the bar our
  generated settlements must meet visually.

**What to avoid.**
- Shallow villages (depth 4 inside a 116-block radius): small and samey once a
  theme has been seen; scaling variety means authoring more NBTs.
- Hand-maintained per-mod biome lists; prefer conventional tags
  (`#c:` / `#minecraft:is_*`) with a fallback.
- Undocumented choices like the `fluid_springs` step.

**Verdict.** Strong reference for *theming by biome* and for what players
already like; nothing to learn technically beyond vanilla jigsaw. Do not copy
(ND licence, no source).

**Not verified.** In-game look (not run; the
[gallery](https://imgur.com/a/CCZbqMn) was not opened); why `fluid_springs`;
whether Cristel Lib does anything to placement beyond config.
