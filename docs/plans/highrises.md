# Plan: high-rise floor programme, stairwell, roof access and tall styles

Status: plan, not started; revised 2026-10-25 with the reviewers' answers.
Author: lead-coder. ROADMAP items **7.13** (floor programme, enclosed
stairwell, lift shaft, roof access) and **7.7** (style by height, mass
timber), touching **7.14** (modern city style) and **DS-34** (high-rise looks,
still proposed). Rules: [DS-1](../design/STANDARDS.md), DS-8, DS-11, DS-15,
DS-22 (amended), DS-23..25, DS-29, DS-32, DS-33, DS-37; build notes
[notes/highrises.md](../design/notes/highrises.md); briefs
[styles/mass-timber.md](../design/styles/mass-timber.md) and
[styles/README.md](../design/styles/README.md). Reviews this revision folds
in: [highrise-plan-architect.md](../review/reports/highrise-plan-architect.md) and
[highrise-plan-interior.md](../review/reports/highrise-plan-interior.md) (2026-10-25); earlier the
architect's tall-styles recommendation
([design-tall-styles-architect.md](../review/reports/design-tall-styles-architect.md)).

# What the generator does today (checked 2026-10-25, base 88219f92)

`fractal-cli floorplan --seed 3 --lot 28x28 --style plain --type highrise`
(26 storeys, podium 1, two shaft tiers, crown 2):

- Storey 0 is an `entry` of 78 cells (4 clear) beside a room with a bed; the
  stair stands open in a `stair` room of 132 cells (storey 0), 80 (storey 1)
  and 40 (storey 12), entered from a corridor.
- Typical floors **do** repeat, but only in runs of 3: shaft2 (storeys 9-23)
  gets a new partition plan at storeys 9, 10, 12, 15, 18, 21, 23. Cause:
  `FacadePass` changes the window rhythm every `bandSize` (3) storeys, so the
  interior's `Info` (window cells are `reserved`) differs, and
  `InteriorPass.draft` draws the new plan with `c.hash("interior/plan", idx, s)`,
  keyed by storey. The facade rhythm change also breaks DS-25 (windows stack)
  on every tower.
- Every unit is a generic `room` with bed, table, chest and workstation
  (`RoomProgram.DEFAULT`); no lobby, no special floors, no shaft, no way onto
  the flat roof (the core's last flight ends on the top storey).
- Core: `MassingOps.core` puts a 2 x len core (len = tallest storey + 1)
  against the back wall; flights alternate rows (`InteriorPass.flight`).
- The style is the lot's biome style (`SpecPolicyV1`: request style, or the
  biome at the lot centre for `auto`), so a jungle high-rise has bamboo walls,
  and a 7-10-storey `tower` has log beams in every style.

# Goal

A tower that reads as designed: a lobby at the door, typical floors that
repeat one plan per setback tier with a double-loaded corridor, 1-3 taller
special floors, one enclosed stairwell stacked from lobby to roof beside a
3x3 lift shaft, a stair house with a machine room onto a guarded flat roof,
windows that stack and meet DS-33's glass share; and walls in high-rise
materials picked from the building's real height and biome, one wall
material per building (towers of 7-10 storeys included).

# Decisions from the reviews (2026-10-25)

| Question | Decision | Where |
|---|---|---|
| Shaft doorway | 1x2 glass pane (`window` role); not a facade window, not a door | B3a |
| Shaft pit | one `light` hook on the pit floor (storey 0, a corner cell against the back wall) | B3a |
| Stair-well edge beside the landing row | 3-deep stairwell, open well (drops 1-3; 4-5 under 6-high storeys) by default; a 4-deep stairwell with a guard row is one constant away (John) | B1, B2 |
| Accessible roof guard | 1-high wall parapet plus 1-high `rail` ring on the roof the stair house opens onto; other roofs keep `parapet 1` | B3b |
| Lobby and special floors | 6 high (5 clear) everywhere; double-height lobby for 30+ storeys later | B1 |
| Hall direction | parallel to the street front when plate depth <= 1.5 x width; deeper plates a known issue until B2b (core along z) | B1, B2b |
| Towers of 7-10 storeys | remapped to `keep` variants that extend the base style with `"biomes": []`; no stairwell or shaft (DS-22 exempts defensive and bell towers) | A2k |
| Tall palettes | corrections below; every variant sets `beam`, `trim`, `deck`, `rail` | A1, A2 |
| Stair house | one box over the whole core block: stair part plus a machine room over the shaft, each with a door onto the roof | B1, B3b |
| Banquet floors | the hall rows stay a `corridor` foyer; the banquet room opens off it by a 2-3 wide archway; the stairwell never opens into the banquet room (DS-22) | B2 |
| Lobby back-of-house | `office` on the side nearer the entrance, `storage` on the other and behind | B2 |
| Banquet back band | `kitchen` on the side nearer the stairwell door's far end, `storage` on the other and behind | B2 |
| Deep units (DS-11) | a windowless private `entry` strip on the hall side, facade rooms side by side behind it; rooms aspect <= 2 (John may prefer fewer, wider units) | B2 |
| Unit main room | use `flat` (living with kitchenette, DS-10), its `hearth` is a stove under 7.5, never a fireplace (DS-15) | B2, B3a |
| Lights (DS-24) | `ceiling_light` on an interior grid of pitch <= 10, hung on an `iron_chain` (length clear - 3) | B3a |
| Window-per-room test (DS-25) | every `flat`, `bedroom`, `office`, `lobby`, `banquet`, `open` leaf has a window cell on its boundary run; `entry`, `storage`, `kitchen`, `corridor`, `stairwell`, `plant` exempt | B2 |
| Special-floor facade | taller windows in the tier's bay columns, never a new bay draw (DS-25) | B3b |
| Glass share (DS-33) | masonry high-rises 35-50 %: 2-wide windows in 4-bays and 3-tall windows (sill 0), measured | A2, A3, B4 |

# Approach and key choices

## 1. Tall styles are style variants picked in `SpecPolicyV1` (7.7)

A **variant** is an ordinary style file (palette, facade, roof keys) whose
`biomes` is empty. A data table `styles/variants.json` maps (base style,
type, storey count, biome, tier) to a weighted set of variant ids;
`SpecPolicyV1` applies it once, after it has drawn the storey count, and
writes the variant id into `spec.style`. Everything downstream (mod
`Variant.pick`, viewer colours, the ground `StyleAt`) already keys palettes
by the style id in the IR, so the mod and viewer need no change.

- **Why here:** the storey count is drawn in `SpecPolicyV1` (`storeys =
  r.range(lo, hi)`); massing only ever lowers it (high-rise capacity cap), so
  "spec storeys <= 20" guarantees the built tower is <= 20 storeys (timber cap
  safe) and "spec storeys >= 7" may at worst give a 6-storey building a tall
  style, which DS-1 allows. `Briefs` only knows a storey *range*, which can
  straddle 6/7.
- **One wall material per building (P2)** comes for free: each variant's
  structural roles are single-block lists, and the per-building choice between
  variants (brick, stone, terracotta, concrete; spruce, oak, birch timber) is
  the weighted draw. No change to `Variant.pick`'s per-cell hash.
- **Either answer to John's question is a data edit:** a shared urban style is
  one default rule mapping every style to the `tall-*` family; per-style tints
  add one rule per style (`desert` -> `desert-tall`). Glass towers (DS-34) are
  a rule with `tiers: [metropolis]`, `minStoreys: 21`; cherry grove is one
  `biomes` row.
- **Inheritance:** `Json.merge` copies top-level keys, so a variant must say
  `"biomes": []` itself whenever its parent lists biomes (otherwise
  `StyleField`/`forBiome` would pick it). High-rise variants extend `tall`;
  the tower `keep` variants extend their base style (keeping its small
  windows and tower facade) with an explicit `"biomes": []`. A loader check
  in A1 enforces it.
- Rejected: a separate `urban` style chosen in `Briefs` (straddling ranges;
  loses DS-1's tint); a per-building palette index in the IR (viewer, mod
  and `Variant` changes for something a style id already carries).

## 2. The programme is decided in massing and re-derived, not stored

The special floors are taller (6 high, 5 clear), so they must be known when
the `StoreyGrid` is built. `TowerProgram.plan(n, podium, bandStarts, crownStart,
Draws)` returns a kind per storey (LOBBY, TYPICAL, BANQUET, OPEN, SUITES,
STAIRHOUSE). `HighriseMassing` calls it to set heights; later passes call
`TowerProgram.read(BuildContext)`, which recovers the same inputs from the
final massing (podium = `main0.to()`, band starts = each shaft mass's
`from()`, crown = the crown mass's `from()`) and calls `plan` with the same
draw key (`c.draws("highrise/programme", n)`). One code path, no new field on
`Massing` (a record built in a dozen places).

## 3. The stair core is today's `Core` plus a landing row

The switchback in `InteriorPass.flight` lands at alternating ends (even
storeys at the low-x end, odd at the high-x end), so a door at "the landing
end" would move from storey to storey and break `floor-repeat`. Instead the
**stairwell room** is the 2-row core plus a **landing row** on the hall
side, walled on all four sides, with **one door in the middle of the landing
row's long side**, the same cell on every storey. From the landing row a
walker reaches the low-end landing (even storeys) or the high-end landing
(odd storeys) along the row; the landing row is never cut. Checked by
walking the flight code: with len = 7, h = 4, even storey s: front row cut
over x+3..x+5, x+2 is the flush top step, x, x+1, x+6 are floor; back row
x+1..x+4 rising, x free. So landing(x) -> front(x) -> back(x) -> step x+1.
Odd storeys mirror it at x+6. `Core`, `flight` and `MassingCheck` M8 stay
unchanged.

- **Stairwell depth `SD` (3 or 4) is one constant** in `TowerBlock` (B1) and
  read from the stairwell rect in `TowerPlate` (B2). `SD = 3` (default): the
  landing row runs beside the front row's cut cells (drops 1-3; 4-5 under a
  6-high storey; fall damage starts above 3). `SD = 4` (if John says the DS-8
  amendment covers interior wells): a **guard row** between landing row and
  core with a 2-high `rail` screen over x+1..x+len-2, open at both ends so the
  walker still reaches x and x+len-1. All block geometry below is written with
  `g = SD - 2` (rows in front of the core: 1 or 2). Decide before B1 merges;
  afterwards it is still a constant, but every plate test and golden moves.
- Rejected: two flights per storey (a U stair with a half landing): with
  4-high storeys the half landing has no headroom, and `flight`, `StairRise`
  and the Core contract would be rewritten.

## 4. A lift shaft and a core block

Beside the stairwell, sharing its end wall: a **shaft** 3 x 3 inside, walled,
open through every slab from storey 0 to the top habitable storey (bottom slab
kept as the pit floor, with one hung lantern; top slab closes it and is the
machine room's floor), with a **doorway per storey** in the hall-side wall,
closed by a 1x2 glass pane until Create (7.2) replaces it. The shaft is not a
room. Stairwell + shaft = the **core block**: `SD` deep, len + 4 long (len + 6
with the end partitions), long side on the hall, hall parallel to the street
front.

## 5. Plate templates instead of the guillotine planner

Typical and special floors are drawn by explicit templates (`TowerPlate`)
that output an ordinary `FloorPlan` (leaves, cuts, doors, `leafAt`, corridor),
so partitions, hooks, room ids and flights in `InteriorPass` work unchanged.

- **CORRIDOR** (typical floors, suites): a hall `hw` wide (2; 3 when the
  podium interior is 30+ on both sides) along x across the whole plate,
  directly in front of the core block's landing row. Band B (street side) and
  band A (core side) are cut into units by cross partitions that avoid
  window cells, so units match the facade bays. A unit has a private `entry`
  strip on the hall side when its band is deeper than 7 (table in B2), then
  facade rooms side by side, each with a window. End units put their door
  next to the hall end (dead end <= 1). Cells behind the core block become a
  back room of the neighbouring unit, entered from its `flat`.
- **GRAND** (lobby, open floor): the street band plus the hall rows are one
  grand room; band A becomes back-of-house beside and behind the core block,
  each room with a door onto the grand room. The stairwell and shaft open
  onto the grand room (DS-22 allows lobby and open floor).
- **BANQUET** (GRAND variant): the hall rows stay a `corridor` foyer across
  the plate; the banquet room is band B, opening off the foyer by a 2-3 wide
  archway; the stairwell and shaft open onto the foyer (DS-22: never into
  the banquet room).
- **RACETRACK** (later step B2b, plates whose interior depth is 23+): a second
  hall behind the core band, cross halls round its ends. Until it lands,
  CORRIDOR is used everywhere, with the `entry` strip absorbing the depth.
- Plans are drawn with `c.draws("highrise/plate", massIndex, kind)`, never
  keyed by storey, so typical floors before and after a special floor are
  identical.
- Rejected: steering the guillotine planner with more reserved cells (no
  guarantee of halls, can't express "every unit off the hall").

## 6. Facade bands follow the programme

`FacadePass.layout` gets an overload taking a band function. The high-rise
passes `TowerProgram` bands: the lobby storey, one band per setback tier for
typical **and** special storeys (so the bay x positions are the tier's on
every storey), the top storey. Special storeys and the lobby then get a
taller `Win` in the same columns (`height(s) - 3`, so 3 tall in a 6-high
storey), the "taller band" DS-32 asks for, without breaking DS-25. Within a
tier the window rhythm no longer changes, so windows stack and plans repeat.
The stair house's faces are blank except its two doors.

## 7. Roof access: a stair house with a machine room

The core runs one storey higher, into a **stair house**: one mass, one 4-high
storey on top of the top tier, footprint the whole core block plus its walls
(`(len + 6) x (SD + 2)`), parent the top tier. Its storey plan: the
stairwell, a partition, and a 3x3 **machine room** (use `plant`) over the
shaft, whose floor is the shaft's top slab. Each has a door on the north
face onto the roof. The roof the stair house opens onto is guarded by a
1-high wall parapet with a 1-high rail on top (2 above the deck, see-through
at eye height 1.62). Small buildings (ladder and trap door) are a separate
later step (R2): they need new roles.

# Steps

Each step is one coder's worth. **[P]** marks steps that can start at once
and run side by side; the others say what they wait for. File ownership is
listed so parallel steps don't touch the same file; shared generated files
are handled as in "Shared files" below.

## A1 [P] Style variants mechanism (7.7)

- New `planner-core/src/main/resources/styles/variants.json`
  (`"format": "fb-style-variants/1"`, a `rules` list, first match wins):
  `{ "id": "...", "styles": [base ids] (absent: any), "types": [...]
  (absent: any), "minStoreys": 1, "maxStoreys": 1000, "biomes": [...]
  (absent: any), "tiers": [...] (absent: any; a rule with tiers never matches
  a request without a tier), "pick": {"styleId": weight, ...} }`.
- New `core/gen/v1/style/StyleVariants.java`: loads and validates the table
  once: every `pick` id is a loaded style whose **merged** `biomes` is empty
  (so a variant extending a biome style must set `"biomes": []`); weights >
  0. `String select(String base, String type, int storeys, String biome,
  String tier, long specSeed)`: first matching rule, weighted draw with
  `Choice.pick(Hash64.hash(specSeed, "style-variant"), rule.id, options)`
  over options in sorted id order; no match, or `base` itself a pick target of
  any rule: return `base` (never remap twice).
- `SpecPolicyV1.spec`: after `storeys` is drawn, `style =
  StyleLibrary.get(StyleVariants.select(style.id(), t.id(), storeys,
  biome, null, specSeed))` with `biome = req.biome().biome(cx, cz)` (`null`
  when the request has no biome probe: rules with `biomes` then never match);
  the air-rights `lead` check below it reads the new style. (Tier is `null`
  until A5.)
- Ship one rule: `{"id": "tall-default", "types": ["highrise", "block"],
  "minStoreys": 7, "pick": {"tall": 1}}` and a minimal `tall.json` (`extends:
  plain`, `biomes: []`, flat roof, the `tall` row of A2's table **with every
  role set**: plain's `beam` oak_log, `trim` stripped_oak_log, `deck`
  oak_planks and `rail` oak_fence must not leak through). `block` never
  reaches 7 storeys today (`BlockType` caps at 5); keep it for later.
- CLI: `floorplan`/`gen` print the effective style already (IR `spec.style`);
  check that `gen --style plain --type highrise` reports `tall`.
- Tests (`StyleVariantsTest`): plain highrise 10..60 storeys -> `tall`; house
  1-3 -> `plain`; a rule with `tiers` never matches `tier == null`; a rule
  with `biomes` matches only those; determinism (same spec seed, same pick;
  over 1000 seeds a 2:1 pick lands within 60-73 %); loading fails on an
  unknown pick id or a pick style whose merged biomes are not empty (feed a
  bad table through a package-private `parse(String)`).
  `StyleLibraryTest.biomesMapToOneStyle` still passes. Mutation: drop the
  "first match" break -> the order test fails.
- Owns: `StyleVariants.java`, `variants.json` (A2/A2k/A3/A4 add rules after
  A1 merges), `SpecPolicyV1.java`, `tall.json` (first version).
- Changes output of every high-rise: regenerate goldens (below). Small.

## A2 [P] Shared tall family (7.7, DS-1, DS-33)

Data plus a static test. Author with 7.12's facade keys: start after 7.12
step 1 merges, or rebase onto it.

- `tall.json` (shared default, `extends: plain`, `biomes: []`) and four
  variants that `extend: tall`: `tall-brick`, `tall-stone`,
  `tall-terracotta`, `tall-concrete`. Roof: `forms {"flat": 1}`, `parapet 1`.
- **Facade for DS-33 (masonry high-rises 35-50 % glass).** Today
  `FacadePlan.Layout.windowWidth` gives a 2-wide window only in even bays of
  4+, so `bayMin 2, bayMax 3` would give 1x2 windows in 2-3 bays: 17-25 %.
  Use `bayMin 4`, `bayMax 4`, `windowWidth 2`, `windowHeight 3`, `sill 0`
  (floor-to-ceiling 2x3 windows in 4-bays: 2/4 x 3/4 = 37.5 % of a 4-high
  storey), `symmetric true`, `doorWidth 2`, `plinth true`, `stringCourse
  true`, `cornice true`, `corners "none"`, `timber "none"`. If 7.12 allows
  2-wide windows in 3-bays, `bayMin 3, bayMax 4` with `windowHeight 2,
  sill 1` (33-50 %) is the alternative; pick by B4's measured share.
- Palette (one block per role; architect-reviewed 2026-10-25; every variant
  sets all of these explicitly):

  | variant | wall | beam | trim | foundation | roof | floor | stair | deck | door | fence, rail |
  |---|---|---|---|---|---|---|---|---|---|---|
  | tall | light_gray_concrete | smooth_stone | smooth_stone | polished_andesite | smooth_stone | polished_andesite | stone_bricks | smooth_stone | dark_oak_door | iron_bars |
  | tall-brick | bricks | stone_bricks | smooth_sandstone | polished_andesite | smooth_stone | polished_andesite | stone_bricks | smooth_stone | spruce_door | iron_bars |
  | tall-stone | stone_bricks | polished_andesite | smooth_stone | polished_andesite | smooth_stone | smooth_stone | stone_bricks | smooth_stone | dark_oak_door | iron_bars |
  | tall-terracotta | white_terracotta | smooth_sandstone | smooth_sandstone | polished_andesite | smooth_stone | polished_andesite | stone_bricks | smooth_stone | spruce_door | iron_bars |
  | tall-concrete | white_concrete | light_gray_concrete | smooth_stone | polished_andesite | smooth_stone | polished_andesite | stone_bricks | smooth_stone | dark_oak_door | iron_bars |

  `window` stays `glass_pane`. Viewer `colours` per role from the block colour
  table (`PaletteCalmTest` checks they match the blocks). Floors in andesite
  are allowed (DS-1 exempts floors); smooth_stone has a slab but no stairs
  (`ShapeTable` falls back), fine while cornices are full blocks.
- `variants.json` default rule: `"pick": {"tall-brick": 3, "tall-stone": 2,
  "tall-terracotta": 2, "tall-concrete": 2}` (agent's weights).
- New `TallPaletteTest`: for every style that appears in any `pick`: merged
  biomes empty; it is not a `styles` base of any rule (no chains); `wall`,
  `beam`, `trim`, `roof`, `foundation` and `deck` are single-block lists;
  every block of those roles is in a DS-1 whitelist (stone, brick,
  terracotta, concrete, quartz, deepslate, tuff, blackstone, sandstone, iron,
  copper, glass families, matched by an id substring list kept in the test);
  no `_log`, `_wool`, `bamboo`, `_planks` or `_fence` (wood) in those roles
  or `rail` except `_planks`/stripped wood in styles whose id starts
  `mass-timber` (A3); every block exists in `vanilla-26.3-blocks.txt`.
- Gallery: `highrise-plain` and `highrise-flat` presets now show tall
  variants; look at the PNGs, compare with the previous gallery.
- Owns: `tall*.json`, `styles/index.txt` lines (union-merged), the default
  rule in `variants.json`, `TallPaletteTest`. Small.

## A2k [P] Keep variants for towers of 7-10 storeys (DS-1)

`ClassTable` gives towers 4-6 (villages) and 4-8 (towns), `TypeLimits` up to
10, and every style's `beam` is a log, so even stone towers break DS-1.

- Three style files, palette overrides only, each extending the **base style**
  with an explicit `"biomes": []`: `keep` (extends `plain`; wall
  stone_bricks, beam polished_andesite, trim stone_bricks, foundation
  cobblestone, and every other role of `plain` that is a log, plank or wooden fence
  set to masonry: deck smooth_stone, fence and rail `cobblestone_wall`; the
  architect checks it in the gallery); `keep-desert` (extends `desert`; wall cut_sandstone, beam
  smooth_sandstone, trim chiseled_sandstone); `keep-jungle` (extends
  `jungle`; wall mossy_stone_bricks, beam stone_bricks, trim stone_bricks).
- Rules above the default, `"types": ["tower"], "minStoreys": 7`: base
  `desert` (and `savanna` when it exists) -> `keep-desert`; `jungle` ->
  `keep-jungle`; any other -> `keep`. The tower keeps its open stair (DS-22
  exempts defensive and bell towers): no stairwell, no shaft.
- Tests: `TallPaletteTest` covers the keeps (they are pick targets); a
  `TowerTypeTest` case: a 9-storey tower request in `medieval` gets `keep`,
  a 6-storey one stays `medieval`. Gallery: the tower presets with storeys
  7+ (add a `tower-tall` preset if none draws 7+).
- Owns: `keep*.json`, the tower rules. Small.

## A3 [P] Mass-timber family (7.7, DS-1 exception, DS-29)

- `mass-timber.json` (`extends: tall`, `biomes: []`) per the brief: plank
  walls, stripped-wood frame one tone from the wall, stone or concrete base
  (`foundation` smooth_stone), flat roof, the same 35-50 % glass facade as
  A2 (`bayMin 4`, `bayMax 4`, `windowWidth 2`, `windowHeight 3`, `sill 0`),
  `door` of the species, `deck` smooth_stone, `rail` iron_bars. Species
  variants extending it, one block per structural role:
  `mass-timber-spruce` (spruce_planks / stripped_spruce_wood),
  `-oak` (oak_planks / stripped_oak_wood), `-birch` (birch_planks /
  stripped_birch_wood), `-pale-oak` (pale_oak_planks /
  stripped_pale_oak_wood), `-jungle` (jungle_planks / stripped_jungle_wood).
  No bamboo variant (patterned bamboo needs wall `axis`, step A6).
- `variants.json` rules **above** the default, `"types": ["highrise",
  "block"], "minStoreys": 7, "maxStoreys": 20`, one per species with its
  biomes (brief table): spruce: taiga, old_growth_pine_taiga,
  old_growth_spruce_taiga, dark_forest; oak: forest, flower_forest; birch:
  birch_forest, old_growth_birch_forest, cherry_grove (John to confirm);
  pale-oak: pale_garden; jungle: jungle, sparse_jungle, bamboo_jungle. Pick
  weights 7 mass timber : 3 `tall-stone` (DS-29 "lean to", agent's numbers).
  Check each biome id against the game's list (the poplar wood of
  `dappled_forest` is untried: leave it out, note it).
- Tests: `TallPaletteTest` allows planks and stripped wood only in
  `mass-timber*`; new `MassTimberRuleTest`: no rule that picks a
  `mass-timber*` style has `maxStoreys` > 20 or `minStoreys` < 1; a taiga
  highrise of 15 storeys can pick `mass-timber-spruce`, of 21 never; a plains
  highrise never picks mass timber.
- Gallery preset `highrise-forest` (lot 28x28, `flat:64+biomes:split:...`
  taiga on both sides, storeys forced into 10-19 through the brief or a
  seed range that draws them; look at the PNGs).
- Owns: `mass-timber*.json`, its rules, `MassTimberRuleTest`, the preset.
  Small to medium.

## A4 (waits for John) Per-style tints

Only if John picks per-style tall variants: `desert-tall` (cut/smooth
sandstone, terracotta), `snowy-tall` and `taiga-tall` (stone bricks,
deepslate trim, copper roof), `jungle-tall` (mossy stone bricks, dark
terracotta), `plains-tall` (bricks, light terracotta), each `extends: tall`,
plus one rule per base style above the default. Data and gallery only;
`TallPaletteTest` covers it. If he picks one shared urban style, nothing to
do. Small.

## A5 (after the architect's 7.14 brief) Tier in the brief, modern city style

- `gen/Brief`: add `String tier` (nullable; keep the old constructors);
  `LotBrief.brief()` and `DistrictScenes`/`WorldHooks` callers pass the
  settlement tier; `SpecPolicyV1` passes `brief.tier()` to `select`.
- `modern.json` and its variants per the brief, and rules `"tiers": ["city",
  "metropolis"], "maxStoreys": 6` (DS-37: towns keep the older look).
- Tests: a city house picks a modern variant, a town house does not.
  This is ROADMAP 7.14; listed here because it reuses A1. Medium.

## A6 (later) Glass tower look, patterned bamboo

Glass tower (DS-34, after John accepts it): a rule `"tiers": ["metropolis"],
"minStoreys": 21` to `tall-glass`, whose facade needs a curtain-wall system
(mullions, spandrels) that style keys can't express today: plan it then.
Patterned bamboo needs `axis` on wall boxes (IR, viewer, mod). Not now.

## B0 [P] High-rise design checks (DS-22, DS-24, DS-25, DS-32)

Write the checks first, against the IR only, so B1-B3 have a target.

- New `core/check/HighriseChecks.java`: `List<Violation> check(BuildingIr ir)`
  for `spec.type == highrise` with ids:
  - `highrise-lobby`: storey 0 has a room of use `lobby` that contains the
    inside cell of the main entrance and is the `to`/`from` of the
    stairwell's storey-0 door; its storey is 6+ high (5+ clear).
  - `floor-repeat`: for every pair of consecutive storeys whose rooms have the
    same use multiset and the same storey height and lie in the same volume,
    the interior `wall`/`door` boxes and the window openings are equal after
    a y shift.
  - `hall-access`: every room of use `flat` or `office` has a door to a room
    of use `entry` (its own), `corridor`, `lobby` or `open`, and every `entry`
    a door to a `corridor`; no `flat`/`office` reached only through another
    unit's room; no `bedroom` reached only through a `bedroom`; every
    `corridor` 2+ wide (3+ when the podium interior is 30+ both ways).
  - `stairwell-enclosed`: every storey with flights has one room of use
    `stairwell`; it contains every core cell of that storey; it has exactly
    one `door` opening that is not a stair, onto `corridor`, `lobby` or
    `open` (never `banquet`, a unit or a back room), or outside on the stair
    house, at the same x, z on every storey.
  - `shaft-present`: a 3x3 column of air cells, walled, from storey 0's
    standing level to the top habitable storey, with one window-role doorway
    per storey facing a `corridor`, `lobby` or `open` room, a light hook on
    its pit floor, and a `plant` room above it on the stair-house storey.
  - `room-window` (DS-25): every room of use `flat`, `bedroom`, `office`,
    `lobby`, `banquet`, `open` has a window opening (exterior faces only; the
    shaft's glass doorway does not count).
  - `room-light` (DS-24, coarse): every room has a light or ceiling_light
    hook, and every room with w + d >= 15 has a ceiling_light no further
    than 6 (Chebyshev, cells) from every floor cell of the room.
  - `roof-reachable`: a flood fill from the street doors only (the same
    walking rules as `Invariants.roomsReachable`; reuse it through a
    package-private helper rather than a copy) reaches a standing cell on the
    top roof; and the top roof's edge has a wall cell with a `rail` cell
    above it all round (DS-8 amendment).
- Unit tests on tiny hand-built IRs (one pass and one fail case per check),
  and a `HighriseChecksOutputTest` over 32 V1 high-rise cases that is
  `@Disabled("until B3")` and records today's counts in its message.
- `fractal-cli check` and `city-check`: report these as warnings (a
  "design" section), not violations, until B4 makes them violations.
- Owns: `HighriseChecks.java`, its tests, the CLI report lines. Doesn't
  touch generator code. Coordinate with 7.9 (DS-1/DS-25 checks) if it is in
  flight: same "design checks as warnings" pattern, separate files; if 7.9
  lands an `openings-aligned` or window-per-room check first, reuse it for
  `room-window`. Medium.

## B1 [P] Tower massing: programme, core block, stair house (7.13)

In `types/highrise/` only.

- `TowerProgram` (new): `enum Kind {LOBBY, TYPICAL, BANQUET, OPEN, SUITES,
  STAIRHOUSE}`; `static Kind[] plan(int n, int podium, int[] bandStarts, int
  crownStart, Draws r)` for habitable storeys `0..n-1` plus index n =
  STAIRHOUSE; `static TowerProgram read(BuildContext c)` (null when the
  massing is the fallback box, i.e. has no core); `boolean special(int s)`;
  `int band(int s)` (facade band key: 0 the lobby storey, `100 + massIndex`
  for typical **and special** storeys of a tier (same bays, section 6), a top
  key for the mass's top storey).
  - Specials only if n >= 12: count 1 (n < 20), 1-2 (20-35), 2-3 (36+).
    Candidates: the first storey above the podium, the first storey of each
    later shaft band (a setback), the last storey below the crown (or the top
    storey if no crown). Drawn without replacement, never two adjacent. Kind
    by candidate: above podium {OPEN 1, BANQUET 1}; at a setback {BANQUET 1,
    OPEN 1}; under the crown {SUITES 2, BANQUET 1} (agent's weights).
- Heights: `LOBBY_H = 6`, `SPECIAL_H = 6`, `TYPICAL = 4`, `STAIRHOUSE_H = 4`;
  `capacity` counts them (`ROOF_ROOM` stays 3 above the stair house's slab).
  Every programme boundary is also a height change or the entrance storey
  (no two adjacent specials), so `InteriorPass`'s band split sees it.
  Double-height lobbies (30+ storeys) are a later item.
- `TowerBlock` (new, building coordinates): record of the core, the side the
  shaft is on, hall width, and `static final int SD = 3` (stairwell depth;
  4 adds the guard row, section 3); with `g = SD - 2`:
  `stairwell()` = `[k.x, k.x+len) x [k.z-g, k.z+2)`; `guardRow()` = the row
  `k.z-1` when `SD == 4`, else none; `shaft()` = 3x3 at x
  `[k.x+len+1, k.x+len+4)` (east) or `[k.x-4, k.x-1)` (west), z `[k.z-g,
  k.z-g+3)`: flush with the landing row, so its hall-side wall is the hall
  partition line for either `SD`; with `SD == 4` the one row behind it
  (`k.z+1`, beside the core) is wall fill inside the block; `hall()` z
  range `[k.z-g-1-hw, k.z-g-1)` (the partition between hall and block is the
  row `k.z-g-1`); `cells()` the block incl. its partitions; `boolean
  fits(Mass tier)`: band B (tier front interior row to the hall's front
  partition) >= 3 deep; `tier.maxZ() == k.z+3` (block back is the back wall)
  or `tier.maxZ() >= k.z+7` (a room of 3+ behind it); on each x side the
  block's end partition is the exterior wall itself, or 3+ interior cells lie
  between the wall and that partition (room for a unit); not against the
  wall on both sides when there is a room behind the block. Core always
  along x (`sx = len, sz = 2`).
- `HighriseMassing.attempt`: build the podium as today; place the block in
  the podium interior: hall centred in z, or, when the podium interior is
  23+ deep, the block centred in z (room for RACETRACK, B2b); block centred
  in x shifted by a draw of -2..2; shaft side drawn. `insets()` shrinks a
  side until `fits(next)` holds (as it does for `MIN_SIDE` today); an attempt
  fails if a tier can't fit. Then `TowerProgram.plan`, the grid with
  programme heights, the stair-house mass over the **whole core block**:
  `new Mass("stair" + i, "stairhouse", bx0-1, k.z-g-1, blockLen+2, SD+2, n,
  n+1, topTier.id(), false)` where `[bx0, bx0+blockLen)` covers stairwell,
  inner partition and shaft; and one `Core(k.x, k.z, len, 2, 0, n+1)`.
- Hall direction: the hall runs along x (parallel to the front). If the
  podium interior is deeper than 1.5 x its width, record it in a counter
  (test output) as the known issue B2b fixes; don't fail the attempt.
- `HighriseType` limits unchanged (lots 20..44); a 20x20 lot gets at most
  one small setback.
- Tests (`HighriseMassingTest`, lots 20/28/36/44 x 200 seeds x maxHeight
  {120, 200, 256}, run with `SD` 3 and, through a package-private override,
  4): `MassingCheck` clean; one core from 0 to n+1, along x, 2 deep, len =
  `Core.length`; `TowerBlock.fits` for every tier; heights: storey 0 = 6,
  stair house = 4, specials by the count rule, on candidate storeys, never
  adjacent, height 6; stair house parent = top tier, footprint = core block +
  walls; top + `ROOF_ROOM` <= maxHeight; `TowerProgram.read` of the built
  massing equals the `plan` the attempt used. Mutation: let `fits` accept a
  2-deep band B -> the fits test fails. Existing `HighriseTypeTest` passes
  (update its expectations where the stair house adds a storey).
- Output is valid on its own (the stair house's floor is planned by the old
  planner; no roof door until B3b). Owns `HighriseMassing.java`,
  `TowerProgram.java`, `TowerBlock.java`, `HighriseMassingTest`. Medium to
  large.

## B2 [P] Plate templates (geometry only)

Pure geometry with its own input record, so it needs nothing from B1.

- Make `interior/FloorPlan` and its `Leaf`, `Cut`, `Door` records public
  (constructors too). Nothing else in `interior/` changes in this step.
- New `types/highrise/TowerPlate.java`:
  `record Spec(int w, int d, int[] stairwell /* x0,z0,x1,z1 plate cells,
  half-open; depth 3 or 4 */, int[] shaft, int hallZ0, int hallZ1,
  boolean[][] reserved, Template template, String grandUse, int unitTarget,
  String unitUse /* "flat" or "office" */, boolean entryStrip, int
  entranceX)`; `enum Template {CORRIDOR, GRAND, BANQUET, STAIRHOUSE}`;
  `record Result(FloorPlan plan, String[] uses, int stairwell, int
  corridor)`; `static Result plan(Spec s, Draws r)`. `reserved` marks window
  cells (the facade's inside cells), as `InteriorPass.info` builds it.
- Partitions are `Cut`s: the hall's two sides (full width; the stairwell's
  and shaft's hall-side walls are the back line), the block's ends and back,
  the shaft walls, unit cross partitions, entry-strip and inner partitions.
  "Legal" cut: no reserved cell on the line, both sides 3+ wide.
- **CORRIDOR:** corridor leaf = hall rows full width (`corridor`). Stairwell
  leaf = the stairwell rect (`stairwell`; with a guard row the rect is 4
  deep and still one leaf: the rail screen is emitted by B3a); its door at
  `(x0 + (x1-x0)/2, hallZ1)` facing south. Shaft cells: `leafAt = -1`, no cut
  inside (B3a emits the shaft and its doorway).
  - Units per band segment `[a, b)` along x: `k = max(1, (L + 1) / (W + 1))`
    with `L = b - a`, `W` the unit target; ideal cuts at even spacing, each
    moved to the nearest legal x within +-2, dropped if none. A leftover
    narrower than 4 becomes a `storage` closet with its door on the hall.
    Unit door in the hall-side partition: next to the hall end for the first
    and last unit of a band (1 cell in), else the unit's middle cell.
  - Unit depth (DS-11), with `D` the band depth and `entryStrip` true:

    | D | entry strip E (use `entry`, door from the hall) | facade room depth F |
    |---|---|---|
    | <= 7 | none (the hall door opens into the `flat`/`office`) | D |
    | 8-10 | 2 | D - 3 |
    | 11-12 | 3 | D - 4 |
    | 13+ | D - 9 (known issue until RACETRACK) | 8 |

    Facade rooms sit side by side behind the strip, each touching the
    facade, each with a door from the strip (or from the `flat` when there is
    no strip; a bedroom never only through a bedroom). Every facade room's
    width >= max(its minimum, ceil(F / 1.5)), never below ceil(F / 2), so
    aspect <= 2. With `entryStrip` false (John's alternative), units are
    instead widened until every facade room meets the same aspect with depth
    D (fewer, wider units; same code path, the strip depth is 0).
  - Apartments (`unitUse = flat`): studio = one `flat`, unit 4-8 wide; 1-bed
    = `flat` >= 4 wide + `bedroom` >= 4 (3x4 minimum), unit >= 9; 2-bed =
    `flat` >= 5 + 2 bedrooms >= 4, unit >= 14. A segment narrower than 9 gets
    a studio. Offices (`unitUse = office`): one `office` room, width >=
    max(6, ceil(F / 1.5)). Suites: 2-bed units, target 14-18, 2-4 per floor,
    each >= 60 cells; leftovers merge into the neighbour.
  - Back rooms behind the block: split at the block's middle x into one or
    two rooms, each joined by a door to the `flat` (never the bedroom) or
    `office` of the unit on its side (or both to the one side that has a
    unit); the band-A unit beside the block puts its `flat` on the core
    side. Use `bedroom` (apartments) or `office`.
- **GRAND** (`grandUse` `lobby` or `open`): the grand leaf = band B plus the
  hall rows, full width. Band A beside the block, per side: lobby: `office`
  on the side nearer `entranceX`, `storage` on the other; open floor: `office`
  both sides. Behind the block: `storage`. Each with a door onto the grand
  room (behind rooms: onto the back room of their side). A back-of-house
  segment deeper than 1.5 x its width is cut in depth: the front part keeps
  the use and the door, the back part is `storage` reached from it.
- **BANQUET:** corridor leaf = the hall rows (`corridor`, the foyer); the
  banquet leaf = band B, joined to the foyer by an archway 2-3 wide at the
  middle of the partition (a `Door` with `arch` true and `span` 2-3); band A
  per side: `kitchen` on the side nearer the stairwell door's far end,
  `storage` on the other, `storage` behind (its door toward the kitchen side
  when the split allows); kitchen minimum 3x4, depth cut as in GRAND.
- **STAIRHOUSE:** the stairwell leaf and a `plant` leaf (the 3x3 over the
  shaft) with the inner partition between; no door between them (each opens
  onto the roof, B3b).
- Fallback: if a band is too shallow for its template, return null (caller
  falls back to the guillotine planner) and count it in a test.
- Tests (`TowerPlateTest`, synthetic plates w in 13..40, d in 12..40, blocks
  at legal positions, stairwell depth 3 and 4, reserved = window cells
  every 2-4 along the boundary rows/columns, all templates, unit targets
  6/10/14, `entryStrip` true and false):
  - every cell is in one leaf, a cut or the shaft; leaves are rectangles
    with min side 3 (the corridor and entry strips excepted, min side 2);
  - aspect (long / short side) <= 2 for every `flat`, `bedroom`, `office`,
    `kitchen` leaf (DS-11);
  - every `flat`, `bedroom`, `office`, `lobby`, `banquet`, `open` leaf has a
    reserved (window) cell on the boundary run it touches (DS-25); every
    other leaf except `corridor`, `entry`, `stairwell`, `storage`, `plant`
    touches the plate boundary;
  - the stairwell leaf equals the stairwell rect and has exactly one
    non-stair door, onto a `corridor` (CORRIDOR, BANQUET) or the grand leaf
    (GRAND), never onto `banquet`;
  - every unit's first room (`entry`, else `flat`/`office`) has a door onto
    the corridor; every facade room of a unit a door onto its strip or
    `flat`; no bedroom reached only through a bedroom; back rooms open off a
    `flat`/`office`;
  - dead end past the last corridor door <= 4; no cut ends on a reserved
    cell where it meets the boundary; same `Spec` and seed -> equal plan.
  - Mutations: put the stairwell door at the rect's end -> the door test
    fails; drop the depth cut -> the aspect test fails.
- Owns `TowerPlate.java`, `TowerPlateTest`, the `FloorPlan` visibility change.
  Large.

## B2b (after B2; parallel with B3a/B3b) RACETRACK and deep plates

For plates with interior depth 23+: a second hall behind the core band,
cross halls round the band's ends, a middle band of the block plus
windowless `storage` rooms, units on the four facades (corner units reach the
ring past their corner); and, for plates deeper than 1.5 x their width, the
core block along z with the hall running from the lobby to the back.
`TowerPlate.plan` picks it when the depth allows; same test suite plus
"service rooms only in the middle band". Large and optional: until it lands,
44x44 podiums use the `entry` strip's 13+ row (a known issue to record, not a
violation).

## B3a (after B1 and B2) Interior wiring, uses, shaft, lights

- `BuildingType`: `default FloorPlanner floors() { return null; }`. New
  `interior/FloorPlanner` interface: `Planned plan(BuildContext c, int
  massIndex, Mass a, int storey, boolean[][] reserved, boolean[][] core)`
  returning a `FloorPlan` and `String[] uses`, or null (use the guillotine).
- `InteriorPass.draft`: when the type has a planner and it returns a plan,
  use its plan and uses (skip `prog.use`/`prog.refine`; roles: corridor ->
  CORRIDOR, stairwell -> STAIR, others ROOM). `program`, `band`, partitions,
  hooks, flights unchanged. Archways the template set stay archways
  (`withArches` only adds more where `prog.openPlan` allows; it allows none).
- `types/highrise/TowerFloors implements FloorPlanner`: from
  `TowerProgram.read(c)` and the block: LOBBY -> GRAND `lobby`; OPEN -> GRAND
  `open`; BANQUET -> BANQUET; TYPICAL -> CORRIDOR with the building's unit
  use and target; SUITES -> CORRIDOR with `flat`, target 14-18;
  STAIRHOUSE -> STAIRHOUSE; converting the block to plate coordinates of
  mass `a`, `entranceX` from the facade plan's main entrance, `entryStrip`
  from a constant (`ENTRY_STRIP = true`, John's question). Unit use per
  building: a draw, `flat` 60 %, `office` 40 % (assumption row). Unit target
  9-11 (`flat`) or 8-12 (`office`), drawn once per building.
- `HighriseRooms implements RoomProgram`: hooks per use (n = count of wall
  hooks; capped by candidates as today; existing kinds plus `ceiling_light`;
  **no `workstation` anywhere**):

  | Use | Hooks |
  |---|---|
  | lobby | counter x2, bench x4, table, light x2, ceiling_light grid |
  | banquet | table x6, bench x6, counter x2, ceiling_light grid |
  | open | table x4, bench x4, chest, ceiling_light grid |
  | office | table + bench per 12 cells (1-4 each), chest, light (+ grid if w + d >= 15) |
  | flat | hearth (a stove under 7.5, never a fireplace: DS-15), counter, table, bench x2, chest, light |
  | bedroom | bed, chest, light |
  | entry | chest, light |
  | kitchen | hearth, counter x2, chest, light |
  | storage | chest x2, counter, light |
  | plant | light |
  | corridor | ceiling_light every 8 cells along its length (min 1) |
  | stairwell | ceiling_light at the landing door cell only (B3a emits it) |

  `openPlan` false for all.
- **`ceiling_light` sites** (new rule in `InteriorPass.hookSites`, keyed by
  the kind, so it applies to any type that asks for it): not wall cells but
  an interior grid of pitch <= 10 in x and z, half a pitch from the walls,
  door exclusion waived (it hangs, never blocks); "grid" in the table means
  every grid point of the room, and every room with w + d >= 15 gets at
  least one at its centre. Rows of the corridor: points along its middle
  line every 8.
- `TowerShaft.emit(c)` (after `InteriorPass.run` in the type's interior
  program): per storey 0..n-1, `AIR` over the shaft's 3x3 from the storey's
  standing level through its ceiling slab (not storey 0's slab, not the top
  slab, which is the machine room's floor); a `WINDOW` box 1x2 in the
  hall-side wall at the shaft's middle x (later boxes win, IR.md "Boxes apply
  in order"); one `light` hook on the pit floor (storey 0, a corner cell of
  the 3x3 against the back wall; its `room` is the storey-0 room the doorway
  faces, since `IrValidator` only requires a known room id); one
  `ceiling_light` hook in each stairwell's landing row at the door cell; with
  `SD == 4`, the guard row's `rail` screen (2 high, x+1..x+len-2) on every
  storey.
- Mod: `HookPlacer` case `ceiling_light`: a lantern hung on an `iron_chain`
  of length clear - 3 (so the lantern sits at y+2 in a 3-clear room, y+3 in a
  5-clear room); fall back to a floor lantern only when no ceiling is found.
  Viewer needs nothing (unknown kinds get a hashed colour). Check
  `iron_chain` is the 26.3 id (it is in `vanilla-26.3-blocks.txt`).
- Tests: `HighriseProgrammeTest` (32+ V1 cases per lot size): B0's checks
  `highrise-lobby`, `floor-repeat`, `hall-access`, `stairwell-enclosed`,
  `shaft-present`, `room-window`, `room-light` pass (enable
  `HighriseChecksOutputTest` except `roof-reachable`); `fractal-cli check
  --seeds 0..199 --types highrise --lots 20x20,28x28,36x36,44x44` clean; no
  room of use `room` and no `workstation` hook in a highrise; a
  `ceiling_light` site test on a 26x10 room (grid points within 10 of each
  other and 5 of the walls). Mod: `:mod:build` (server game tests). Look at
  `floorplan` output for seeds 3 and 7 (lobby, a typical floor of each tier,
  each special, the stair house) and a cutaway gallery.
- Owns `InteriorPass.java` (extension point and the `ceiling_light` site
  rule), `FloorPlanner.java`, `BuildingType.java` (one default method),
  `TowerFloors`, `HighriseRooms`, `TowerShaft`, `HighriseType` (`floors()`,
  `rooms()`, `interior()`), mod `HookPlacer` (one case). Large.

## B3b (after B1 and 7.12 step 1; parallel with B3a) Facade bands, roof doors, roof guard

- `FacadePass`: overload `run(BuildContext c, BandFn bands,
  UnaryOperator<List<FacadePlan.Layout>> edit)` and `layout(c, bands)`;
  `BandFn` = `int band(Mass m, int storey)`; the existing `band(m, s,
  bandSize)` is the default. No behaviour change for other types (goldens of
  other types unchanged: check).
- `HighriseFacade.run(c)`: bands from `TowerProgram.band` (special storeys
  share their tier's band, so the same bays and window columns); edit: lobby
  and special storeys' `Win` height = `height(s) - 3` (at least the style's),
  same width and sill; the stair house's layouts all `BLANK`. The shaft's
  glass doorway is interior and never a facade window.
- `HighriseType.roofs()`: `RoofPass.run(c, rs)` with the style's roof rules,
  `forms {flat: 1}`, `parapet 1`; then the **roof guard**: on the top tier's
  roof (the roof the stair house opens onto), a `RAIL` box on every parapet
  cell's top (1 above the 1-high wall), unbroken round the ring; the rail
  ring stops at the stair house's walls. Setback roofs with no door keep the
  plain `parapet 1`. (`parapet 2` is the data-only fallback if the rail ring
  is too much code; never on glass or frame towers later.)
- `HighriseType.openings()`: `OpeningPass.run(c)`, then two roof doors:
  `OpeningPass.door(c, new FacadePlan.Entrance(face, pos))` on the stair
  house's north face at the landing row's middle (into the stairwell) and at
  the machine room's middle x (into `plant`); the face from `c.faces()` with
  `mass == stairhouse id`, `side NORTH`, `storey n`. They are not added to the
  `FacadePlan` (stoops and paths read those).
- Tests: windows of every storey of a tier in the same x columns, typical
  ones identical (DS-25); lobby and special windows taller than typical;
  both roof doors exist with `to = outside` on storey n; B0's
  `roof-reachable` (including the rail ring) passes; other types' golden
  digests unchanged by the `FacadePass` overload. Look at front and
  back-right gallery shots of 3 seeds: taller bands in line with the windows
  above and below, stair house with its machine room, the rail on the roof.
- Owns `FacadePass.java` (overload only), `HighriseFacade`,
  `HighriseType` (`facade()`, `roofs()`, `openings()`). Medium.

## B4 (after B3a, B3b, A2) Galleries, goldens, review

- Regenerate golden digests (all affected types) and the sample settlement
  fixture; run `scripts/perf.sh --planner-only` (record the load);
  `npm run gallery` for `highrise-*` and tower presets with cutaways at storey
  0, a typical storey and a special storey; `npm run city-gallery` for one
  metropolis; open and compare the PNGs with the previous gallery.
- Measure DS-33 glass share per facade (7.12's `glazing-share` if it exists,
  else a test helper counting window cells over wall cells per face): masonry
  and mass-timber towers in 35-50 %; if not, change A2's facade keys (bay 3
  alternative) before ticking.
- Turn B0's checks into violations in `check`/`city-check` for type
  highrise; tick 7.13 (and 7.7 if A1-A3 and A2k are in); REVIEW.md lines for
  John (lobby feel, tall palettes, keeps, mass timber in a forest city, stair
  house, entry strips).
- Architect and interior-designer review (the coordinator asks).

## R2 (later, separate) Roof hatches for small flat-roofed buildings

DS-22's "ladder and trap door in small buildings" (desert houses, flat-roofed
workshops and blocks): needs roles `ladder` and `hatch` (IR, viewer `ROLES`,
mod `BlockMapper`, facing), a ladder column against a wall of the top
storey's largest room and a trap door in the roof slab, and the same
parapet-plus-rail guard on that roof. Not part of 7.13's high-rise core; ask
the architect which types.

# Order and parallelism

```
wave 1 (now, side by side): A1, B0, B1, B2       (A2, A2k, A3 start too; their
                                                   variants.json rules wait for A1)
wave 2: B3a (B1+B2), B3b (B1 + 7.12 step 1), B2b (B2), A2/A2k/A3 finish
wave 3: B4; then A4 (John), A5 (7.14 brief), A6, R2
```

John's stairwell-depth answer should come before B1 merges (it is a constant
either way, but every test and golden moves with it). His entry-strip answer
can come any time before B4 (a constant in `TowerFloors`).

# Shared files and conflicts

- **Golden digests** (`golden/v1/*.txt`): A1, A2, A2k, A3, B1, B3a, B3b
  change output (A2k changes tall towers). Never hand-merge: after merging
  the integration branch, rerun `:planner-core:test` and copy
  `build/golden/v1/*.txt` (NOTES 4.1). `GEN_VERSION` stays 2 unless the
  coordinator decides to bump it once at B4. `SampleSettlementTest` fixture:
  regenerate with `sample-city`.
- **7.12 windows** (`claude/p7-windows`): edits every style JSON
  (`windowHeight`), `FacadePlan` window width and `FacadePass`. A2/A3 add new
  style files (no conflict) but must use 7.12's keys and semantics (whether a
  2-wide window fits a 3-bay decides A2's facade keys); B3b's `FacadePass`
  overload goes after 7.12 step 1 merges. 7.12's step 2 (glazing targets)
  measures on B4's output.
- **7.5 furniture** (F-steps): a `FurnishPass` after `hookSites` in
  `InteriorPass`; B3a edits `InteriorPass.draft` and `hookSites` (the
  `ceiling_light` site rule). Small textual risk; whoever merges second
  resolves. The furniture plan's "Which room gets what" gets the rows in the
  interior-designer's report (lobby, banquet, open, office, flat, storage,
  corridor, stairwell); `flat`'s hearth is a stove, not a fireplace.
- **7.9** checks: B0 follows the same report-first pattern; separate files.
- `HighriseType.java`: B1 (none), B3a (`floors`, `rooms`, `interior`), B3b
  (`facade`, `roofs`, `openings`): different methods, trivial merge.
- `TowerTypeTest` (A2k) and `styles/index.txt`, `docs/NOTES.md`
  (union-merged).

# Test infrastructure

- `HighriseChecks` (B0) is the shared target and stays as the design check.
- `TowerPlate.Spec` lets plate tests run without massing (B2).
- Gallery presets: `highrise-forest` (A3), `tower-tall` if needed (A2k);
  cutaway cameras at chosen storeys in the existing `highrise-*` presets
  (B4).
- `fractal-cli floorplan` is the review tool for plans (no change needed).

# Risks and known issues (with the assumption used)

- **Core length 7 on every storey.** `Core.length` takes the tallest
  storey: one 6-high floor makes every storey's stairwell 7 long. Accepted.
- **Stair-well edge** (SD 3): drops of 1-3 beside the landing row, 4-5 under a
  6-high storey (half a heart to a heart). Accepted unless John extends the
  DS-8 amendment indoors (then SD 4).
- **Unit depth on big plates** with CORRIDOR only: entry strips of 4+ on
  13+-deep bands (44x44 podiums). Known issue until B2b.
- **Deep, narrow plates** (depth > 1.5 x width) get the hall along x anyway.
  Known issue until B2b.
- **Massing gets fewer setbacks** (fits() is stricter than MIN_SIDE 10):
  skylines change; the gallery comparison in B4 shows by how much.
- **Apartments vs offices** by a per-building draw (60/40): the brief has no
  quarter kind. Assumption row in ASSUMPTIONS when B3a lands.
- **Stair house storey counts** in the IR's storey list (n + 1 storeys); DS-1
  counts storeys above ground without it, and the style pick uses
  `spec.storeys`. Diversity metrics may count it.
- **Glass share** depends on 7.12's window semantics; B4 measures before the
  item is ticked.
- **Hooks before 7.5** read only by counts (one block each on wall cells);
  no head table or stage until 7.5's sets.

# Open for John (answered 2026-10-26)

1. **Stairwell 3 or 4 deep.** Does the proposed DS-8 amendment ("railings
   that hold") cover the open well inside a stairwell? No/unanswered: 3 deep,
   open well (drops 1-3, 4-5 under 6-high floors). Yes: 4 deep with a guard
   row and a 2-high rail screen. `TowerBlock.SD`, one constant; best decided
   before B1 merges.
   *Answered 2026-10-26 (John: "default (no)"): 3 deep, open well.*
2. **Deep apartments: windowless private hallway, or fewer wider units?**
   Default: a private `entry` strip on the hall side (as real deep-plan flats
   do). Alternative: no strip, units widened until rooms are square enough
   (fewer flats per floor). `TowerFloors.ENTRY_STRIP`, one constant.
   *2026-10-26: John delegated this to the architect ("I want the architect
   to make the calls here"): architect decides, on proportions, whether it
   would look real in Minecraft and plausible real-life examples.*
3. Tall buildings outside forests: per-style tall tint over a shared default,
   or one shared urban style everywhere? Data in `variants.json` (A4); A1-A3
   ship the shared default.
   *Answered 2026-10-26: per-style tall tint over a shared default (DS-41).*
4. Glass towers in the metropolis (DS-34 still proposed): one look across
   biomes over 20 storeys, tinted only at the base? A rule either way.
   *Answered 2026-10-26: yes, one shared look (DS-41).*
5. Cherry grove mass timber: birch walls with cherry trim (assumed, A3), or
   pink cherry walls?
   *Answered 2026-10-26: birch walls with cherry trim.*

# Proposed rule (architect, for John)

Roofs reached by a door are guarded by a 1-high parapet plus a rail (2 above
the surface), see-through at eye level; scope accessible flat roofs and
terraces of all types; folds into the DS-8 amendment's `rail-height` check.
The coordinator files it in STANDARDS "Proposed by agents".
