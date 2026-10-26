# Plan: high-rise floor programme, stairwell, roof access and tall styles

Status: plan, not started. Author: lead-coder, 2026-10-25. ROADMAP items
**7.13** (floor programme, enclosed stairwell, lift shaft, roof access) and
**7.7** (style by height, mass timber), touching **7.14** (modern city style)
and **DS-34** (high-rise looks, still proposed). Rules: [DS-1](../design/STANDARDS.md),
DS-22 (amended), DS-23..25, DS-29, DS-32, DS-33, DS-37; build notes
[notes/highrises.md](../design/notes/highrises.md); briefs
[styles/mass-timber.md](../design/styles/mass-timber.md) and
[styles/README.md](../design/styles/README.md); the architect's tall-styles
recommendation (`.claude/reports/design-tall-styles-architect.md`: a per-style
`tall` variant over a shared default).

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
  biome at the lot centre for `auto`), so a jungle high-rise has bamboo walls.

# Goal

A tower that reads as designed: a lobby at the door, typical floors that
repeat one plan per setback tier with a double-loaded corridor, 1-3 taller
special floors, one enclosed stairwell stacked from lobby to roof beside a
3x3 lift shaft, a stair house onto a flat roof, windows that stack; and walls
in high-rise materials picked from the building's real height and biome, one
wall material per building.

# Approach and key choices

## 1. Tall styles are style variants picked in `SpecPolicyV1` (7.7)

A **variant** is an ordinary style file (palette, facade, roof keys) with no
biomes. A data table `styles/variants.json` maps (base style, type, storey
count, biome, tier) to a weighted set of variant ids; `SpecPolicyV1` applies
it once, after it has drawn the storey count, and writes the variant id into
`spec.style`. Everything downstream (mod `Variant.pick`, viewer colours, the
ground `StyleAt`) already keys palettes by the style id in the IR, so the mod
and viewer need no change.

- **Why here:** the storey count is drawn in `SpecPolicyV1` (`storeys =
  r.range(lo, hi)`); massing only ever lowers it (high-rise capacity cap), so
  "spec storeys <= 20" guarantees the built tower is <= 20 storeys (timber cap
  safe) and "spec storeys >= 7" may at worst give a 6-storey building a tall
  style, which DS-1 allows. `Briefs` only knows a storey *range*, which can
  straddle 6/7 (the architect's objection to choosing there).
- **One wall material per building (P2)** comes for free: each variant's
  structural roles are single-block lists, and the per-building choice between
  variants (brick, stone, terracotta, concrete; spruce, oak, birch timber) is
  the weighted draw. No change to `Variant.pick`'s per-cell hash.
- **Either answer to John's question is a data edit:** a shared urban style is
  one default rule mapping every style to the `tall-*` family; per-style tints
  add one rule per style (`desert` -> `desert-tall`, which `extends: tall`).
  Glass towers (DS-34) are a rule with `tiers: [metropolis]`, `minStoreys: 21`;
  cherry grove is one `biomes` row.
- Variants `extends` the shared `tall` file (never a biome style), because
  `Json.merge` copies top-level keys: a variant extending `jungle` would
  inherit jungle's `biomes` and be picked by `StyleField`/`forBiome`.
- Rejected: a separate `urban` style chosen in `Briefs` (needs a rule for
  ranges that straddle 6/7; loses biome tint, against DS-1's "the local style
  may tint"); a per-building palette index in the IR (viewer, mod and
  `Variant` changes for something a style id already carries).

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
**stairwell room** is the 2-row core plus one **landing row** on the hall
side, 3 x len inside, walled on all four sides, with **one door in the middle
of the landing row's long side**, the same cell on every storey. From the
landing row a walker reaches the low-end landing (even storeys) or the
high-end landing (odd storeys) along the row; the landing row is never cut.
Checked by walking the flight code: with len = 7, h = 4, even storey s: front
row cut over x+3..x+5, x+2 is the flush top step, x, x+1, x+6 are floor; back
row x+1..x+4 rising, x free. So landing(x) -> front(x) -> back(x) -> step
x+1. Odd storeys mirror it at x+6. `Core`, `flight` and `MassingCheck` M8
stay unchanged.

- Rejected: two flights per storey (a U stair with a half landing): constant
  door end, but with 4-high storeys the half landing has no headroom without
  cutting the slab above, and it means rewriting `flight`, `StairRise` and the
  Core contract.
- Cost: the landing row runs beside the stair well; at even storeys the front
  row's cut cells are open beside it (a drop of 1-3 onto the flight below, up
  to 5 under a 6-high special floor). No damage under 4; see Design questions.

## 4. A lift shaft and a core block

Beside the stairwell, sharing its end wall: a **shaft** 3 x 3 inside, walled,
open through every slab from storey 0 to the top habitable storey (bottom slab
kept as the pit floor, top slab closes it), with a **doorway per storey** in
the hall-side wall, closed by glass (window role, 1 x 2) until Create (7.2).
The shaft is not a room. Stairwell + shaft = the **core block**: 3 deep, len +
4 long (len + 6 with the end partitions), always with its long side on the
hall, the hall parallel to the street front.

## 5. Two plate templates instead of the guillotine planner

Typical and special floors are drawn by explicit templates (`TowerPlate`)
that output an ordinary `FloorPlan` (leaves, cuts, doors, `leafAt`, corridor),
so partitions, hooks, room ids and flights in `InteriorPass` work unchanged.

- **CORRIDOR** (typical floors, suites): a hall `hw` wide (2; 3 when the
  podium interior is 30+ on both sides) along x across the whole plate,
  directly in front of the core block's landing row. Band B (street side) and
  band A (core side) are cut into units by cross partitions that avoid
  `reserved` (window) cells, so units match the facade bays. Units 1-3 rooms
  side by side, each touching the facade (DS-25: habitable rooms have a
  window). End units put their door next to the hall end, so no dead end over
  1. Cells behind the core block (band A deeper than the block) become a back
  room of the neighbouring unit.
- **GRAND** (lobby, banquet, open floor): the street band plus the hall rows
  are one grand room; band A becomes back-of-house rooms beside and behind
  the core block, each with a door onto the grand room. The stairwell and
  shaft open onto the grand room.
- **RACETRACK** (later step B2b, plates whose interior depth is 23+): a second
  hall behind the core band and cross halls round its ends, so units on a
  44x44 podium are not 16 deep. Until it lands, CORRIDOR is used everywhere.
- Plans are drawn with `c.draws("highrise/plate", massIndex, kind)`, never
  keyed by storey, so typical floors before and after a special floor are
  identical.
- Rejected: steering the guillotine planner with more reserved cells (no
  guarantee of halls, can't express "every unit off the hall"); one template
  per plate size (too many).

## 6. Facade bands follow the programme

`FacadePass.layout` gets an overload taking a band function. The high-rise
passes `TowerProgram` bands: lobby, one band per setback tier for typical
floors, one per special storey, the top storey. Within a tier the window
rhythm no longer changes, so windows stack (DS-25) and plans repeat. Special
storeys and the lobby get windows `height(s) - 3` tall (3 in a 6-high storey:
sill 1, head one below the ceiling), the "taller band" DS-32 asks for. The
stair house's faces are blank.

## 7. Roof access: a stair house mass

The core runs one storey higher, into a **stair house**: a mass of one 4-high
storey on top of the top tier, footprint exactly the stairwell plus its walls
(len + 2 by 5), parent the top tier. Its interior is the stairwell; a door in
its hall-side wall (the landing row's middle, as on every storey) opens onto
the flat roof. The top roof gets a parapet that stops a walker (see Design
questions; assumed 2 high). Small buildings (ladder and trap door) are a
separate later step (R2): they need new roles.

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
  once (every `pick` id is a loaded style with an empty `biomes`; weights >
  0); `String select(String base, String type, int storeys, String biome,
  String tier, long specSeed)`: first matching rule, weighted draw with
  `Choice.pick(Hash64.hash(specSeed, "style-variant"), rule.id, options)`
  over options in sorted id order; no match, or `base` itself a pick target of
  any rule: return `base` (never remap twice).
- `SpecPolicyV1.spec`: after `storeys` is drawn, `style =
  StyleLibrary.get(StyleVariants.select(style.id(), t.id(), storeys,
  req.biome().biome(cx, cz), null, specSeed))` (biome `null` when the request has no biome probe: rules with `biomes` then never match); the air-rights `lead` check
  below it reads the new style. (Tier is `null` until A5.)
- Ship one rule: `{"id": "tall-default", "types": ["highrise", "block"],
  "minStoreys": 7, "pick": {"tall": 1}}` and a minimal `tall.json` (`extends:
  plain`, `biomes: []`, flat roof, the DS-1-safe palette of the `tall` row in
  A2's table). A2 widens both.
- CLI: `floorplan`/`gen` print the effective style already (IR `spec.style`);
  check that `gen --style plain --type highrise` reports `tall`.
- Tests (`StyleVariantsTest`): plain highrise 10..60 storeys -> `tall`; house
  1-3 -> `plain`; a rule with `tiers` never matches `tier == null`; a rule
  with `biomes` matches only those; determinism (same spec seed, same pick;
  over 1000 seeds a 2:1 pick lands within 60-73 %); loading fails on an
  unknown pick id or a pick style with biomes (feed a bad table through a
  package-private `parse(String)`). `StyleLibraryTest.biomesMapToOneStyle`
  still passes (variants list no biomes). Mutation: drop the "first match"
  break -> the order test fails.
- Owns: `StyleVariants.java`, `variants.json` (A2/A3/A4 edit it after A1
  merges), `SpecPolicyV1.java`, `tall.json` (first version).
- Changes output of every high-rise: regenerate goldens (below). Small.

## A2 [P] Shared tall family (7.7, DS-1, DS-33)

Data plus a static test. Author with 7.12's facade keys (`windowHeight` 2,
`windowWidth` 2): start after 7.12 step 1 merges, or rebase onto it.

- `tall.json` (shared default, `extends: plain`, `biomes: []`) and four
  variants that `extend: tall`: `tall-brick`, `tall-stone`,
  `tall-terracotta`, `tall-concrete`. Facade: `bayMin 2`, `bayMax 3`,
  `sill 1`, `windowHeight 2`, `windowWidth 2`, `symmetric true`,
  `doorWidth 2`, `plinth true`, `stringCourse true`, `cornice true`,
  `corners "none"`, `timber "none"`. Roof: `forms {"flat": 1}`, `parapet 1`.
  Draft palette (one block per role; the architect reviews it in this step):

  | variant | wall | beam | trim | foundation | roof | floor | stair | door | fence, rail |
  |---|---|---|---|---|---|---|---|---|---|
  | tall | light_gray_concrete | smooth_stone | smooth_stone | polished_andesite | smooth_stone | polished_andesite | stone_bricks | dark_oak_door | iron_bars |
  | tall-brick | bricks | stone_bricks | smooth_stone | polished_andesite | smooth_stone | polished_andesite | stone_bricks | spruce_door | iron_bars |
  | tall-stone | stone_bricks | polished_andesite | smooth_stone | polished_andesite | smooth_stone | smooth_stone | stone_bricks | dark_oak_door | iron_bars |
  | tall-terracotta | white_terracotta | smooth_sandstone? (architect) | smooth_stone | polished_andesite | smooth_stone | polished_andesite | stone_bricks | spruce_door | iron_bars |
  | tall-concrete | white_concrete | light_gray_concrete | smooth_stone | polished_andesite | smooth_stone | polished_andesite | stone_bricks | dark_oak_door | iron_bars |

  `window` stays `glass_pane`. Viewer `colours` per role from the block colour
  table (`PaletteCalmTest` checks they match the blocks).
- `variants.json` default rule: `"pick": {"tall-brick": 3, "tall-stone": 2,
  "tall-terracotta": 2, "tall-concrete": 2}` (agent's weights).
- New `TallPaletteTest`: for every style that appears in any `pick`: no
  biomes; it is not a `styles` base of any rule (no chains); `wall`, `beam`,
  `roof` and `foundation` are single-block lists; every block of those roles
  is in a DS-1 whitelist (stone, brick, terracotta, concrete, quartz,
  deepslate, tuff, blackstone, sandstone, iron, copper, glass families,
  matched by id substring list kept in the test), no `_log`, `_wool`,
  `bamboo`, `_planks` except in styles whose id starts `mass-timber` (A3);
  every block exists in `vanilla-26.3-blocks.txt`.
- Gallery: `highrise-plain` and `highrise-flat` presets now show tall
  variants; look at the PNGs, compare with the previous gallery.
- Owns: `tall*.json`, `styles/index.txt` lines (union-merged), the default
  rule in `variants.json`, `TallPaletteTest`. Small.

## A3 [P] Mass-timber family (7.7, DS-1 exception, DS-29)

- `mass-timber.json` (`extends: tall`, `biomes: []`) per the brief: plank
  walls, stripped-wood frame one tone from the wall, stone or concrete base
  (`foundation` smooth_stone), flat roof, large regular windows (glass
  35-50 %: facade `bayMin 3`, `bayMax 4`, `windowWidth 2`), `door` of the
  species. Species variants extending it, one block per structural role:
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

## B0 [P] High-rise design checks (DS-22, DS-32)

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
  - `hall-access`: every room of use `living`, `office` or (suites) `living`
    has a door to a room of use `corridor`, `lobby`, `banquet` or `open`; no
    `living`/`office` reached only through another unit's room; every
    `corridor` 2+ wide (3+ when the podium interior is 30+ both ways).
  - `stairwell-enclosed`: every storey with flights has one room of use
    `stairwell`; it contains every core cell of that storey; it has exactly
    one `door` opening that is not a stair (onto `corridor`, `lobby`,
    `banquet`, `open`, or outside on the stair house), at the same x, z on
    every storey.
  - `shaft-present`: a 3x3 column of air cells, walled, from storey 0's
    standing level to the top habitable storey, with one window-role doorway
    per storey facing a `corridor`/`lobby`/grand room.
  - `roof-reachable`: a flood fill from the street doors only (the same
    walking rules as `Invariants.roomsReachable`; reuse it through a
    package-private helper rather than a copy) reaches a standing cell on the
    top roof.
- Unit tests on tiny hand-built IRs (one pass and one fail case per check),
  and a `HighriseChecksOutputTest` over 32 V1 high-rise cases that is
  `@Disabled("until B3")` and records today's counts in its message.
- `fractal-cli check` and `city-check`: report these as warnings (a
  "design" section), not violations, until B4 makes them violations.
- Owns: `HighriseChecks.java`, its tests, the CLI report lines. Doesn't
  touch generator code. Coordinate with 7.9 (DS-1/DS-25 checks) if it is in
  flight: same "design checks as warnings" pattern, separate files. Medium.

## B1 [P] Tower massing: programme, core block, stair house (7.13)

In `types/highrise/` only.

- `TowerProgram` (new): `enum Kind {LOBBY, TYPICAL, BANQUET, OPEN, SUITES,
  STAIRHOUSE}`; `static Kind[] plan(int n, int podium, int[] bandStarts, int
  crownStart, Draws r)` for habitable storeys `0..n-1` plus index n =
  STAIRHOUSE; `static TowerProgram read(BuildContext c)` (null when the
  massing is the fallback box, i.e. has no core); `boolean special(int s)`;
  `int band(int s)` (facade band key: 0 lobby, `100 + massIndex` typical,
  `200 + s` special, `TOP`).
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
- `TowerBlock` (new, building coordinates): record of the core, the side the
  shaft is on, hall width: `stairwell()` = `[k.x, k.x+len) x [k.z-1, k.z+2)`,
  `shaft()` = 3x3 at `[k.x+len+1, k.x+len+4)` (east) or `[k.x-4, k.x-1)`
  (west), z as the stairwell; `hall()` z range `[k.z-2-hw, k.z-2)`;
  `cells()` the block incl. its partitions; `boolean fits(Mass tier)`:
  band B (tier front interior row to the hall's front partition) >= 3 deep;
  `tier.maxZ() == k.z+3` (block back is the back wall) or `tier.maxZ() >=
  k.z+7` (a room of 3+ behind it); on each x side the block's end partition is the exterior
  wall itself, or 3+ interior cells lie between the wall and that partition
  (room for a unit); and not against the wall on both sides when there is a
  room behind the block (it needs a unit to open into). Core always along x (`sx = len, sz = 2`).
- `HighriseMassing.attempt`: build the podium as today; place the block in
  the podium interior: hall centred in z (`hz = pz0 + (d - hw)/2`, `k.z = hz
  + hw + 2`), or, when the podium interior is 23+ deep, the block centred in
  z (room for RACETRACK, B2b); block centred in x shifted by a draw of -2..2;
  shaft side drawn. `insets()` shrinks a side until `fits(next)` holds (as it
  does for `MIN_SIDE` today); an attempt fails if a tier can't fit. Then
  `TowerProgram.plan`, the grid with programme heights, the stair-house mass
  (`new Mass("stair" + i, "stairhouse", k.x-1, k.z-2, len+2, 5, n, n+1,
  topTier.id(), false)`), and one `Core(k.x, k.z, len, 2, 0, n+1)`.
- `HighriseType` limits unchanged (lots 20..44); a 20x20 lot gets at most
  one small setback.
- Tests (`HighriseMassingTest`, lots 20/28/36/44 x 200 seeds x maxHeight
  {120, 200, 256}): `MassingCheck` clean; one core from 0 to n+1, along x,
  2 deep, len = `Core.length`; `TowerBlock.fits` for every tier; heights:
  storey 0 = 6, stair house = 4, specials by the count rule, on candidate
  storeys, never adjacent, height 6; stair house parent = top tier, footprint
  = stairwell + walls; top + `ROOF_ROOM` <= maxHeight; `TowerProgram.read`
  of the built massing equals the `plan` the attempt used. Mutation: let
  `fits` accept a 2-deep band B -> the fits test fails. Existing
  `HighriseTypeTest` passes (update its expectations where the stair house
  adds a storey).
- Output is valid on its own (the stair house's floor is planned by the old
  planner as one `stair` room; no roof door until B3b). Owns
  `HighriseMassing.java`, `TowerProgram.java`, `TowerBlock.java`,
  `HighriseMassingTest`. Medium to large.

## B2 [P] Plate templates (geometry only)

Pure geometry with its own input record, so it needs nothing from B1.

- Make `interior/FloorPlan` and its `Leaf`, `Cut`, `Door` records public
  (constructors too). Nothing else in `interior/` changes in this step.
- New `types/highrise/TowerPlate.java`:
  `record Spec(int w, int d, int[] stairwell /* x0,z0,x1,z1 plate cells,
  half-open */, int[] shaft, int hallZ0, int hallZ1, boolean[][] reserved,
  Template template, int unitTarget, String unitUse /* "living" or
  "office" */)`; `enum Template {CORRIDOR, GRAND}`;
  `record Result(FloorPlan plan, String[] uses, int stairwell, int
  corridor)`; `static Result plan(Spec s, Draws r)`.
  - Partitions are `Cut`s: the hall's two sides (z = hallZ0-1 and hallZ1,
    full width, the latter broken by nothing: the stairwell's and shaft's
    hall-side walls are the same line), the block's ends and back, the shaft
    walls, unit cross partitions, unit inner partitions.
  - CORRIDOR: corridor leaf = hall rows full width (`use corridor`).
    Stairwell leaf = the stairwell rect (`stairwell`); its door at `(x0 +
    (x1-x0)/2, hallZ1)` facing south. Shaft cells: `leafAt = -1`, no cut
    inside (B3a emits the shaft). Unit split of each band segment
    (`[a, b)` along x): `k = max(1, round((b - a + 1) / (unitTarget + 1)))`,
    ideal cuts `a + i*(b-a+1)/k - 1`, each moved to the nearest legal x
    within +-2 (no reserved cell on the line, both sides 3+ wide), dropped if
    none. Unit door in the hall-side partition: next to the hall end for the
    first and last unit of a band (1 cell in), else the unit's middle cell.
    Inside a `living` unit: width 8+ -> living + bedroom, 12+ -> living + 2
    bedrooms (inner cuts legal as above, living gets the door and is at least
    4 wide; bedroom doors in the inner cut at the row next to the hall).
    `office` units stay one room. Back rooms behind the block: split at the
    block's middle x into one or two rooms, each joined by a door to the unit
    on its side (or both to the one side that has a unit); use `bedroom`
    (living units) or `office`.
  - GRAND: the grand leaf = band B plus the hall rows, full width (use from
    the caller: `lobby`, `banquet`, `open`); band A beside the block = one
    back room per side (use `office` for the lobby, `kitchen` for a banquet,
    `office` for an open floor) with a door onto the grand room; behind rooms
    as above.
  - Fallback: if a band is too shallow for its template, return null (caller
    falls back to the guillotine planner) and count it in a test.
- Tests (`TowerPlateTest`, synthetic plates w in 13..40, d in 12..40, blocks
  at legal positions, reserved = window centre cells every 2-4 along the
  boundary rows/columns, both templates, unit targets 6/9/12): every cell is
  in one leaf, a cut or the shaft; leaves are rectangles with min side 3
  (the corridor excepted, min side `hw`); the stairwell leaf equals the
  stairwell rect and has exactly one non-stair door, onto the corridor or
  grand leaf; every `living`/`office` leaf of a unit has a door onto the
  corridor/grand leaf, every bedroom and back room a door onto its unit's
  living/office leaf; dead end past the last corridor door <= 4; no cut ends
  on a reserved cell where it meets the boundary; every leaf except corridor
  and stairwell touches the plate boundary; same `Spec` and seed -> equal
  plan. Mutation: put the stairwell door at the rect's end -> the door test
  fails.
- Owns `TowerPlate.java`, `TowerPlateTest`, the `FloorPlan` visibility change.
  Medium to large.

## B2b (after B2; parallel with B3a/B3b) RACETRACK template

For plates with interior depth 23+: a second hall behind the core band,
cross halls round the band's ends, a middle band of the block plus
windowless service rooms (`storage`), units on the four facades (corner units
reach the ring past its corner). `TowerPlate.plan` picks it when the depth
allows; same test suite plus "service rooms only in the middle band". Large
and optional: with CORRIDOR only, 44x44 podiums have units ~16 deep (a known
issue to record, not a violation).

## B3a (after B1 and B2) Interior wiring, uses, shaft, lights

- `BuildingType`: `default FloorPlanner floors() { return null; }`. New
  `interior/FloorPlanner` interface: `Planned plan(BuildContext c, int
  massIndex, Mass a, int storey, boolean[][] reserved, boolean[][] core)`
  returning a `FloorPlan` and `String[] uses`, or null (use the guillotine).
- `InteriorPass.draft`: when the type has a planner and it returns a plan,
  use its plan and uses (skip `prog.use`/`prog.refine`; roles: corridor ->
  CORRIDOR, stairwell -> STAIR, others ROOM). `program`, `band`, partitions,
  hooks, flights unchanged.
- `types/highrise/TowerFloors implements FloorPlanner`: from
  `TowerProgram.read(c)` and the block: kind per storey -> template (LOBBY,
  BANQUET, OPEN -> GRAND with use `lobby`/`banquet`/`open`; TYPICAL ->
  CORRIDOR with the building's unit use and target; SUITES -> CORRIDOR with
  target 12 and `living`; STAIRHOUSE -> one leaf `stairwell`), converting the
  block to plate coordinates of mass `a`. Unit use per building: a draw,
  `living` 60 %, `office` 40 % (assumption; see Design questions). Unit target
  6-8 (living) or 8-12 (office), drawn once per building.
- `HighriseRooms implements RoomProgram`: hooks per use (existing kinds:
  bed, chest, table, workstation, hearth, counter, bench, light, plus new
  `ceiling_light`): lobby: counter, bench, bench, table, ceiling_light x2;
  banquet: table x4, bench x2, counter, ceiling_light x2; open/office: table
  x2, chest, ceiling_light; living: table, hearth, bench, chest, light;
  bedroom: bed, chest, light; kitchen: hearth, counter, chest, light;
  corridor: ceiling_light every ~8 cells; stairwell: none (below). Every room
  gets a light (DS-24). `openPlan` false for all (doors, no arches).
- `TowerShaft.emit(c)` (after `InteriorPass.run` in the type's interior
  program): per storey 0..n-1, `AIR` over the shaft's 3x3 from the storey's
  standing level through its ceiling slab (not storey 0's slab, not the
  roof slab); a `WINDOW` box 1x2 in the hall-side wall at the shaft's middle
  x (later boxes win, IR.md "Boxes apply in order"); and one `ceiling_light`
  hook in each stairwell's landing row at the door cell (hanging, so it
  never blocks the row).
- Mod: `HookPlacer` case `ceiling_light` = always the hanging lantern
  (fall back to a floor lantern only when no ceiling is found). Viewer needs
  nothing (unknown kinds get a hashed colour).
- Tests: `HighriseProgrammeTest` (32+ V1 cases per lot size): B0's checks
  `highrise-lobby`, `floor-repeat`, `hall-access`, `stairwell-enclosed`,
  `shaft-present` pass (enable `HighriseChecksOutputTest` except
  `roof-reachable`); `fractal-cli check --seeds 0..199 --types highrise
  --lots 20x20,28x28,36x36,44x44` clean; every room has a light hook; no
  room of use `room` in a highrise. Mod: `:mod:build` (server game tests).
  Look at `floorplan` output for seeds 3 and 7 (lobby, a typical floor of
  each tier, each special) and a cutaway gallery.
- Owns `InteriorPass.java` (extension point only), `FloorPlanner.java`,
  `BuildingType.java` (one default method), `TowerFloors`, `HighriseRooms`,
  `TowerShaft`, `HighriseType` (`floors()`, `rooms()`, `interior()`), mod
  `HookPlacer` (one case). Large.

## B3b (after B1 and 7.12 step 1; parallel with B3a) Facade bands, roof door, parapet

- `FacadePass`: overload `run(BuildContext c, BandFn bands,
  UnaryOperator<List<FacadePlan.Layout>> edit)` and `layout(c, bands)`;
  `BandFn` = `int band(Mass m, int storey)`; the existing `band(m, s,
  bandSize)` is the default. No behaviour change for other types (goldens of
  other types unchanged: check).
- `HighriseFacade.run(c)`: bands from `TowerProgram.band`; edit: lobby and
  special storeys' `Win` height = `height(s) - 3` (min the style's), the
  stair house's layouts all `BLANK`.
- `HighriseType.roofs()`: `RoofPass.run(c, rs)` with the style's roof rules
  but `forms {flat: 1}` and `parapet 2` (assumption).
- `HighriseType.openings()`: `OpeningPass.run(c)`, then the roof door:
  `OpeningPass.door(c, new FacadePlan.Entrance(face, pos))` on the stair
  house's north face at the landing row's middle (the face from `c.faces()`
  with `mass == stairhouse id`, `side NORTH`, `storey n`). It is not added to
  the `FacadePlan` (stoops and paths read those).
- Tests: windows of every typical storey of a tier identical (DS-25 within a
  band); lobby and special windows taller than typical; the roof door exists
  with `to = outside` on storey n; B0's `roof-reachable` passes; other
  types' golden digests unchanged by the `FacadePass` overload. Look at
  front and back-right gallery shots of 3 seeds: taller bands visible,
  stair house on the roof.
- Owns `FacadePass.java` (overload only), `HighriseFacade`,
  `HighriseType` (`facade()`, `roofs()`, `openings()`). Medium.

## B4 (after B3a, B3b, A2) Galleries, goldens, review

- Regenerate golden digests (all affected types) and the sample settlement
  fixture; run `scripts/perf.sh --planner-only` (record the load);
  `npm run gallery` for `highrise-*` presets with cutaways at storey 0, a
  typical storey and a special storey; `npm run city-gallery` for one
  metropolis; open and compare the PNGs with the previous gallery.
- Turn B0's checks into violations in `check`/`city-check` for type
  highrise; tick 7.13 (and 7.7 if A1-A3 are in); REVIEW.md lines for John
  (lobby feel, tall palettes, mass timber in a forest city, stair house).
- Architect and interior-designer review (the coordinator asks).

## R2 (later, separate) Roof hatches for small flat-roofed buildings

DS-22's "ladder and trap door in small buildings" (desert houses, flat-roofed
workshops and blocks, towers if the architect wants): needs roles `ladder`
and `hatch` (IR, viewer `ROLES`, mod `BlockMapper`, facing), a ladder column
against a wall of the top storey's largest room and a trap door in the roof
slab. Not part of 7.13's high-rise core; ask the architect which types.

# Order and parallelism

```
wave 1 (now, side by side): A1, B0, B1, B2        (A2, A3 start too; their
                                                    variants.json edit waits for A1)
wave 2: B3a (B1+B2), B3b (B1 + 7.12 step 1), B2b (B2), A2/A3 finish
wave 3: B4; then A4 (John), A5 (7.14 brief), A6, R2
```

# Shared files and conflicts

- **Golden digests** (`golden/v1/*.txt`): A1, A2, A3, B1, B3a, B3b all change
  high-rise output. Never hand-merge: after merging the integration branch,
  rerun `:planner-core:test` and copy `build/golden/v1/*.txt` (NOTES 4.1).
  `GEN_VERSION` stays 2 unless the coordinator decides to bump it once at
  B4. `SampleSettlementTest` fixture: regenerate with `sample-city`.
- **7.12 windows** (coder, `claude/p7-windows`, nothing committed at
  88219f92): edits every style JSON (`windowHeight`), `FacadePlan` window
  width and `FacadePass`. A2/A3 add new style files (no conflict) but must
  use 7.12's keys; B3b's `FacadePass` overload goes after 7.12 step 1
  merges. 7.12's step 2 (glazing targets, "high-rise facades with 7.13")
  should measure on B4's output.
- **7.5 furniture** (F-steps): a `FurnishPass` after `hookSites` in
  `InteriorPass`; B3a edits `InteriorPass.draft`. Different methods, small
  textual risk; whoever merges second resolves. The furniture plan's "Which
  room gets what" needs rows for `lobby`, `banquet`, `open`, `office`,
  `corridor`, `stairwell` (interior-designer).
- **7.9** checks: B0 follows the same report-first pattern; separate files.
- `HighriseType.java`: B1 (none), B3a (`floors`, `rooms`, `interior`), B3b
  (`facade`, `roofs`, `openings`): different methods, trivial merge.
- `styles/index.txt` and `docs/NOTES.md` are union-merged.

# Test infrastructure

- `HighriseChecks` (B0) is the shared target and stays as the design check.
- `TowerPlate.Spec` lets plate tests run without massing (B2).
- Gallery presets: `highrise-forest` (A3); cutaway cameras at chosen storeys
  in the existing `highrise-*` presets (B4).
- `fractal-cli floorplan` is the review tool for plans (no change needed).

# Risks and open questions (with the assumption used)

- **Core length 7 on every storey.** `Core.length` takes the tallest
  storey: one 6-high floor makes every storey's stairwell 3x7. Accepted
  (notes/highrises.md offered this or two flights); typical flights leave 3
  spare landing cells.
- **Fall into the stair well** from the landing row (1-3 blocks; up to 5 under
  a 6-high storey). Assumed acceptable for now; architect question.
- **Spawning in the shaft pit** (dark, 3x3, sealed by glass). Assumed
  acceptable until Create (7.2); architect question.
- **Unit depth on big plates** with CORRIDOR only (~16 on a 44x44 podium).
  Known issue until B2b.
- **Massing gets fewer setbacks** (fits() is stricter than MIN_SIDE 10):
  skylines change; the gallery comparison in B4 shows by how much.
- **Towers of 7-10 storeys** (type `tower`) are not remapped (rules list
  `highrise`, `block`): DS-1 still applies to them. Architect question.
- **Apartments vs offices** by a per-building draw (60/40): the brief has no
  quarter kind. Assumption row in ASSUMPTIONS when B3a lands.
- **Stair house storey counts** in the IR's storey list (n + 1 storeys); DS-1
  counts storeys above ground without it, and the style pick uses
  `spec.storeys`, so nothing depends on it. Diversity metrics may count it.

# Design questions (for the coordinator to route)

Architect:
1. Shaft doorway closure until Create: a 1x2 glass pane (assumed), iron bars,
   or a pair of iron doors (unopenable without redstone)? Spawn-proof the pit
   (glass or slab floor, a light)?
2. Stair-well edge beside the landing row (drops 1-5): accept, or a rail
   (which costs the landing row a cell and needs a wider stairwell)?
3. Accessible roof guard: parapet 2 high (assumed) or 1 wall plus a rail?
   Same question for future sky-lobby terraces.
4. Lobby and special floors 6 high (5 clear) everywhere; a double-height
   lobby over 20 storeys left for later. OK?
5. Hall always parallel to the street front, core block on the back side of
   it: acceptable simplification for square lots?
6. Towers (type `tower`, 7-10 storeys) under DS-1: give them a stone variant,
   or exempt defensive and bell towers?
7. The tall palette draft in A2 (and `tall-terracotta`'s frame block); stair
   house: plain box, or a plant-room box over the shaft too?

Interior-designer:
1. Lobby back-of-house rooms (assumed `office` both sides): mail room,
   security, storage?
2. Banquet back band: `kitchen` one side, what on the other (storage,
   cloakroom)? Head table or stage as which hooks until 7.5?
3. Apartment sizes: living + bedroom from 8 wide, + 2 bedrooms from 12; units
   6-8 wide; back rooms behind the core as an extra bedroom. Office units one
   room 8-12 wide.
4. Hook lists per use in B3a (above): enough to read as lobby / banquet /
   office / flat before 7.5's sets?

# Questions for John

1. Tall buildings outside forests: per-style tall tint over a shared default
   (architect's recommendation), or one shared urban style everywhere? Either
   is a data edit in `variants.json` (A4); A1-A3 ship the shared default.
2. Glass towers in the metropolis (DS-34 still proposed): one look across
   biomes over 20 storeys, tinted only at the base? A rule either way.
3. Cherry grove mass timber: birch walls with cherry trim (assumed, A3), or
   pink cherry walls?
