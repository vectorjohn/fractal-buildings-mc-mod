# ChoiceTheorem's Overhauled Village (CTOV)

**What it is.** A popular (10M+ Modrinth downloads) mod and datapack adding
new villages and pillager outposts.
[Modrinth](https://modrinth.com/mod/ct-overhaul-village),
[source](https://github.com/ChoiceTheorem/ChoiceTheorem-s-overhauled-village)
(read branch `26.2` at 090534d, Aug 2026).

**Approach.** Each village is a standard **jigsaw** structure built from
hand-built NBT pieces: a town-centre start pool, then `roads`, `house`,
`deco`, `terminator` pools (plains: 19 houses, 7 roads, 7 deco), with
`fallback` pools so failed slots become decoration. 22 village themes
(beach, desert oasis, mesa fortified, jungle tree, mountain alpine, underground,
christmas, …), each in **small / medium / large** versions that differ only in
jigsaw depth (`size` 4 / 5 / 6), plus 14 outpost variants. Variety inside a
theme comes from piece choice and **rule processors** that randomly swap
blocks (30 % stone bricks to cracked, 30 % to mossy, 15 % planks to stripped
logs, …).

**Procedural vs templated.** Fully templated buildings (about 2,000 village
NBTs); the only procedural parts are jigsaw assembly and per-block weathering.
The Java code is about 20 files: config, registration, and one processor
(`modular_compat`) that applies extra processor lists only when another mod is
loaded.

**Worldgen hook.** Structures of type `lithostitched:jigsaw` (Lithostitched's
jigsaw, see track 5) with `terrain_adaptation: beard_thin`,
`project_start_to_heightmap: WORLD_SURFACE_WG`, `use_expansion_hack`, and
`max_distance_from_center: 80`. At server start it uses Lithostitched's
`AddWorldgenModifiersEvent` to **append its structures to vanilla's
`minecraft:villages` and `minecraft:pillager_outposts` structure sets** with
configurable weights, so it shares vanilla's spacing grid and never
double-spawns villages; a config list enables variants. Size is bounded by the
jigsaw limits (depth 20, 128-block radius); CTOV stays far inside them (radius
80), so it never needs a workaround.

**Determinism.** Vanilla jigsaw: planned once from seed and start chunk,
chunk-independent.

**Biome adaptation.** Purely by **biome tags**: each variant's `biomes` is a
vanilla tag (`#minecraft:has_structure/village_plains`, `#minecraft:is_beach`,
`#minecraft:is_mountain`, …), so biome mods like Terralith or BoP get villages
automatically. Several themes share a tag (plains, plains fortified, …) and
compete by weight. No material swapping by biome within a theme.

**Licence.** **CC BY-NC-ND 4.0** (repo `LICENSE`, Modrinth). We may look at it
and describe it; no derivatives, so do not copy pieces, pools or code.

**What to borrow (ideas only).**
- Hooking into vanilla's village structure set, so our settlements replace a
  share of villages rather than adding a competing grid: compatible and
  locatable.
- Biome tags, not biome lists, for compatibility with biome mods.
- Small/medium/large as one structure with three depth limits.
- Fallback pools and weathering processors as a cheap last layer.

**What to avoid.**
- Variety by authoring: ~2,000 NBTs for 22 themes, and still each house type
  repeats inside a village. This is the scaling wall our project exists to get
  past.
- Rigid pieces on a thin beard: placement depends on authored pieces
  matching the terrain they land on (vanilla jigsaw behaviour; e.g. issue
  #147, "all buildings in village_jungle_tree are set into the ground by one
  block"). Slope handling not checked in game.

**Verdict.** Best-in-class *templated* village variety and the clearest example
of polite integration (vanilla set + biome tags + Lithostitched). Low value as
a generation technique; useful as the visual bar a generated village has to
beat. Do not copy (ND licence).

**Not verified.** In-game look (not run); how seasonal themes (christmas,
halloween) are gated; whether the `.jso` files under
`lithostitched/worldgen_modifier/` are dead leftovers (the Java event
appears to have replaced them).
