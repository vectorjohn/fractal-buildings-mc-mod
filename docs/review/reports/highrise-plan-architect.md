# highrise-plan architect answers (2026-10-25)

*Frozen copy of the agent report `.claude/reports/highrise-plan-architect.md` (untracked scratch), committed 2026-10-26 so REVIEW.md and STANDARDS.md can link it. Paths inside it to other `.claude/reports/` files are not in git.*

Mode: question. Round: first. Read at `claude/run-phase6` 46bf9c88.
Counts: 4 DS flags on the plan (DS-22, DS-25, DS-33, DS-11), plus DS-1 inheritance gaps; 1 question for John.
Looked at (docs and code, no images: nothing generated yet): `docs/plans/highrises.md` (all),
`docs/design/STANDARDS.md` (DS-1, 2, 8 + amendment, 11, 22-25, 29, 32-34), `docs/design/notes/highrises.md`,
`.claude/reports/design-tall-styles-architect.md`, `styles/*.json` palettes and roofs, `RoofPass.parapet`,
`FacadeStyle` javadoc (bay/windowWidth semantics), `Json.merge`, `TowerType`, `ClassTable` rows,
`HighriseType`/`BlockType` limits, `ShapeTable`, `vanilla-26.3-blocks.txt` (every block named below exists).

# Answers

## 1. Shaft doorway and pit

**Decision:** keep the 1x2 `WINDOW` box (glass pane) from the storey's standing level as planned; no iron
doors, no iron bars. Add one `light` hook on the pit floor (storey 0, a corner cell of the 3x3, against
the back wall), so the pit meets DS-24 by its own light, not by luck through the glass.
**Reason:** glass reads as a glazed lift door, lets light in and stops falls; an iron door that cannot open
reads as a bug (and with a button opens onto a 3x3 drop); bars read as a cage. Only the pit floor can spawn
(the rest of the shaft is an air column under a closed top slab), and DS-24 asks for block light 1+, so
one lantern there settles it deterministically.
- The doorway must not count as a facade window (DS-25/DS-33 checks: exterior faces only) nor as a `door`
  for reachability. Create (7.2) replaces the pane and removes the pit lantern.
- Alternative: a non-spawnable pit floor (bottom slab, glass); better only once a slab role exists.

## 2. Stairwell edge beside the landing row

**Decision:** accept it, no rail, for this plan. Record it as a known issue tied to the DS-8 amendment.
**Reason:** by the plan's own geometry the drops are 1, 2, 3 blocks (cells x+3..x+5 on even storeys,
mirrored on odd), and Minecraft fall damage starts above 3, so typical floors are harmless; only the
landing row under a 6-high storey has 4-5 (half a heart to one heart). A rail cannot stand in the hole
(it would take the headroom of the flight below near its top) nor in the landing row (it blocks the door,
which sits at x+3, beside the 1-block drop on every storey). The only real guard is a **4-deep stairwell**
(landing row, a guard row with a 2-high `RAIL` screen over x+1..x+len-2, open at both ends, then the
2 core rows).
- **For John:** does the proposed DS-8 amendment ("railings that hold", walkable edges) cover interior
  stair wells? If yes, switch B1's `TowerBlock` and B2's stairwell rect to 4 deep **before B1 lands**
  (it changes `fits`, the hall z and every plate test; afterwards it is a rewrite). Default if he hasn't
  answered: 3 deep as planned.

## 3. Roof guard

**Decision:** on the accessible top roof (the one the stair house opens onto), a 1-high `wall` parapet
with a 1-high `rail` (the style's `rail`, iron bars in the tall family) on top of it, unbroken round the
ring; setback roofs with no door keep the style's `parapet 1`. Same for future sky-lobby terraces.
Implementation: `HighriseType.roofs()` runs `RoofPass` with `parapet 1`, then adds the rail ring on the
same edge cells (small code; `parapetRole` stays wall).
**Reason:** total 2 above the roof surface stops a walker (jump 1.25, and he can't stand on a wall whose
top cell is the rail), meets the DS-8 amendment's 1.5, and keeps the view: eye height is 1.62, so a 2-high
solid parapet hides the city from the roof you climbed 26 storeys to reach. From the street the difference
is invisible, so looks don't decide it.
- Alternative: `parapet 2` (data only, a heavier masonry crown); acceptable for masonry looks if the coder
  must avoid code, but never on glass or frame towers later.

## 4. Lobby and special floor heights

**Decision:** 6 high (5 clear) for the lobby and every special floor, everywhere, for now. A double-height
lobby (two grid storeys, upper left open as a gallery) for towers of 30+ storeys is a later item.
**Reason:** DS-23 allows 5-7 clear for grand rooms; `Core.length` is the tallest storey + 1, so any 7-high
floor lengthens the stair core on every storey; one height keeps one core and one taller band rhythm.

## 5. Hall parallel to the street front

**Decision:** yes for every plate whose depth is at most 1.5x its width (all square test lots, most city
lots). For deeper plates (lots can be 18 wide and up to 44 deep), record as a known issue, fixed with B2b
by running the core along z (hall from the lobby to the back). On corner lots, parallel to the main
entrance's street.
**Reason:** the facade bays and the lobby read best with the hall along the front; on a deep, narrow plate
a hall along x gives units ~2x deeper than wide, which is the 44x44 problem in another shape.

## 6. Towers of 7-10 storeys

**Decision:** remap them, don't exempt them. One A1 rule `{"types": ["tower"], "minStoreys": 7}` above the
default, picking a **keep** variant that extends the **base style** with an explicit `"biomes": []` and
overrides only the palette: `keep` (stone_bricks wall, polished_andesite beam, stone_bricks trim,
cobblestone foundation) for plain, plains, medieval, snowy, taiga; `keep-desert` (cut_sandstone wall,
smooth_sandstone beam, chiseled_sandstone trim) and `keep-jungle` (mossy_stone_bricks wall, stone_bricks
beam). Three data files, no code. The stilted lookout is short and unaffected.
**Reason:** DS-1 covers "anything over 6 storeys" and every style's `beam` is a log (desert acacia,
medieval dark oak, plain oak), so even stone-walled towers break it; real 30-block watchtowers and keeps are
masonry anyway. Extending the base style keeps its small windows and tower facade (extending `tall` would
give a medieval keep a modern office facade). `Json.merge` copies top-level keys, so an explicit
`"biomes": []` overrides the parent's (the plan's reason for never extending a biome style holds only when
the variant omits `biomes`). DS-22 already exempts defensive and bell towers from the enclosed stairwell:
no stairwell or shaft for type `tower`.
- `ClassTable` gives towers 4-6 / 4-8 storeys (villages, towns) and `TypeLimits` up to 10, so this bites.

## 7. Tall palette draft (A2) and the stair house

**Palette decisions** (one block per role, all exist in 26.3):
- `tall-terracotta`: beam **smooth_sandstone** and trim **smooth_sandstone** (warm cream one tone from
  white_terracotta's tan; smooth_stone grey on tan reads as a mismatch of warm and cool).
- `tall-brick`: trim **smooth_sandstone** (brick with limestone dressings, the classic) instead of
  smooth_stone; rest as drafted.
- `tall`, `tall-stone`, `tall-concrete`: as drafted. They are calm (DS-2); `tall-stone` is grey on grey on
  grey, acceptable, tune after the gallery.
- Every variant (and `tall.json` from A1 on) must set **beam, trim, deck and rail** explicitly: `tall`
  extends `plain`, whose beam is `oak_log`, trim `stripped_oak_log`, deck `oak_planks`, rail `oak_fence`.
  Set `deck` smooth_stone. Extend `TallPaletteTest` to `trim` and `deck` (no logs, no planks outside
  mass timber).
- Doors in wood and floors in polished_andesite are fine for DS-1 (exempt roles); the floor of every flat
  in andesite is the interior-designer's call.
- smooth_stone has a slab but no stairs (`ShapeTable` falls back): fine today (cornices are full blocks).

**Stair house:** one box over the **whole core block** (stairwell + shaft + walls), 4 high: the stair part
as planned, the part over the shaft a machine room (shaft top slab is its floor) with its own door onto
the roof on the same north face. **Reason:** real roofs carry the lift overrun with the stair; Create's
elevator pulley (7.2) needs a room above the shaft, and a bulkhead the length of the core reads as one
deliberate crown element rather than a shed. Alternative: the plain stair box (cheaper; fine if B1 must
stay small, then add the machine room in 7.2). Blank faces are fine (not habitable); the landing-row
ceiling light lights it (DS-24).

# Plan items that would break a DS rule

- **DS-22** (accepted): the stairwell door opens "onto the hall, lobby or open floor (never into a unit or
  across a room)". On **banquet** floors the plan's GRAND template opens it straight into the banquet room,
  and `stairwell-enclosed` allows `banquet`. Fix: on BANQUET floors keep the hall rows as a `corridor`
  foyer (pre-function space) with a 2-3 wide opening into the banquet room; drop `banquet` from the
  check's allowed list. Lobby and open floors stay as planned.
- **DS-25** (accepted, "every habitable room has a window"): B2's test "every leaf touches the plate
  boundary" is weaker than the rule; a unit 3 wide between window bays can touch the wall with no window.
  Test instead: every `living`, `bedroom`, `office`, `kitchen` leaf has a `reserved` (window) cell on its
  boundary run. Also windows stack: B3b's per-special-storey bands must reuse the tier's bay x positions
  (taller windows, same columns); a new bay draw per special band breaks DS-25 at every special floor.
- **DS-33** (approved): masonry high-rises 35-50 % glass. Per `FacadeStyle`, `windowWidth 2` only applies
  in even bays of 4+, so A2's `bayMin 2, bayMax 3` gives 1x2 windows in 2-3 bays: 17-25 % on a 4-high
  storey. A3's `bayMax 4` reaches at most 25 %. Unless 7.12 changes those semantics, A2/A3 need 2x2 windows
  with 1-wide piers (bay 3) or taller windows to reach the band; measure `glazing-share` in B4.
- **DS-11** (accepted, rooms lean square): CORRIDOR units 6-8 wide in ~11-deep bands (28x28) and ~16 deep
  (44x44), split side by side into living + bedroom, give rooms 3-4 wide by 11-16 deep (aspect 3-4).
  Fix: split deep units front to back: living and bedrooms side by side on the facade half, an
  entry/bath/storage strip (windowless, non-habitable) on the hall half; test living/bedroom aspect <= 2.
  Room uses for the strip are the interior-designer's.
- **DS-1**: inherited plain roles (above, answer 7); towers (answer 6).
- Minor: the default rule lists type `block`, but `BlockType` caps at 5 storeys, so it never matches;
  harmless, keep it for later.

# Proposed rules

- Roofs reached by a door are guarded by a 1-high parapet plus a rail (2 above the surface), see-through
  at eye level / why: stops walkers, keeps the view / scope: accessible flat roofs and terraces, all
  types / enforcement: code candidate, folds into the DS-8 amendment's `rail-height`. From: answer 3.

# For John

- Does the DS-8 amendment ("railings that hold") cover interior stair wells? Yes = 4-deep stairwell with
  a guard row in B1/B2 (decide before B1 lands); no/unanswered = 3 deep, open well (drops 1-3; 4-5 under
  special floors).

Verdict: meets the standard (after the DS-22/25/33/11 fixes above are folded into the plan)
