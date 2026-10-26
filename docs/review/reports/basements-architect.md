# Basements architect answer (2026-10-26)

Mode: question. Round: first.
Looked at (text only, no pictures needed for a design question): `docs/ROADMAP.md` (7.17 SHOPLOW, the 3.9a known issue), `docs/design/STANDARDS.md` (DS-5, 6, 8, 22, 23, 24, 25, 33, 35, 45), `docs/design/styles/*.md` (stilts, plinths), `docs/plans/highrises.md` (core, shaft pit, machine room, back-of-house), `docs/IR.md` (storeys), `.claude/reports/shoplow-architect.md`, `.claude/reports/proc-floorplan.md`, `docs/research/spikes/s5.md` and `s7.md` (beard_thin, aquifers), `mod/.../DistrictStructure.java` and `GroundStructure.java` (beard comments), and a grep of the room uses in planner-core (`storage`, `plant`, `kitchen`, `workshop` exist). Nothing generated or rendered.

## Question

John: do we have a plan for basements? High-rises might get several levels (boilers and the like), houses sometimes, maybe depending on climate; parking is "a can of worms". (1) What the docs say now; (2) which types, tiers and climates get them, how many levels, and parking; (3) how they show outside and how you get in; (4) worldgen and lighting limits; a ROADMAP item.

## (1) What exists now

Nothing is planned. A grep of the ROADMAP, the plans, the design docs, IR.md, NOTES, ASSUMPTIONS and the reports finds only three mentions:

- ROADMAP 3.9a known issue: "`no-gap-below` flags buried floors (basements will need a rule change)".
- `proc-floorplan` report: "basement (no negative-y storeys seen, labelled if present but untested)".
- The Lost Cities research note: that mod has "cellars" per floor.

The IR numbers storeys from 0, and the first storey's `y` is 0 (IR.md, invariants). SHOPLOW's spec, step 4, leaves "a lower-ground storey with a back door" as a later item, and step 3 (bank buildings) already makes storey 0 partly buried. The high-rise plan puts the shaft pit and back-of-house on the lobby storey and the plant room on the roof.

## Recommendation

Treat a basement as **one or more storeys below the entrance storey**. They come in three kinds, set by the ground around them:

- **Buried cellar**: earth on all sides.
- **Half-sunk** (lower ground floor): about half its height below the ground, lit by an areaway.
- **Walk-out**: buried at the front, open on the low side of a falling lot.

This is the same idea as SHOPLOW's cut-in and bank buildings, turned downward. Build the IR and mod support once and every type uses it.

### Who gets one, how often, how deep (agent's numbers)

Shares are per building of that type and style in towns. Halve them in hamlets and villages, except the cold styles.

| Type | Share | Levels and height | What it is |
|---|---|---|---|
| House, snowy / taiga | 60 % / 50 % | 1, 4 floor-to-floor | root and storage cellar (frost line: in cold climates the foundation goes deep anyway, so the cellar costs little; Scandinavia, Russia, New England) |
| House, plains / plain | 35 % / 30 % | 1 | cellar; on plains farmhouses, a storm cellar with an outside hatch |
| House, medieval | 40 % in towns, 15 % in villages | 1, may be under half the footprint | vaulted cellar (Winchelsea, Paris, Prague) |
| House, desert | 20 % | 1 | cool store (the Persian *sardab*); hot dry climates did dig down for the cold |
| House, savanna | 10 % | 1 | small store under part of the house |
| House, jungle / swamp / mangrove | **0** | none | high water table; the style raises houses instead (stilts, DS-45 variant) |
| House, mass-timber, other tall styles | as their base style | 1 | |
| Shop, medieval town and city | 25 % | 1 | **undercroft**: a vaulted store or a second trade, often with its own street door down steps (Chester Rows, Winchester, Southampton) |
| Shop, other styles | 20 % | 1 | storeroom under the shop, stair from the back room |
| Workshop | 0 % buried | walk-out only | goods need a cart door at grade. On a falling lot, the lower storey is a walk-out work floor |
| Hall (town+) | 50 % | 1, 4-5 clear | civic undercroft (York Merchant Adventurers', London Guildhall crypt) |
| Temple (town+) | 40 % | 1, under the chancel or apse end only | crypt reached by a stair at each side of the chancel |
| Tower, defensive | 30 % | 1 | storeroom or cistern reached **only by a hatch and ladder** from the storey above (keeps did this; DS-22 allows a ladder in small buildings) |
| Tower, bell / wall | 0 | | |
| Block (city, metropolis) | 60 % | 1 | storage, bins, plant |
| High-rise 7-12 storeys | 100 % | 1 | storage and plant |
| High-rise 13-25 | 100 % | 2 | B1 storage and back-of-house, B2 plant (boilers, pumps, tanks) |
| High-rise 26+ | 100 % | 3 (4 for metropolis glass towers over 40) | B1 back-of-house and loading, B2-B3 plant and cistern |

- **Heights.** A cellar or storage level is 4 floor-to-floor (3 clear, DS-23's minimum; it keeps stair geometry identical). A plant level is 6 (5 clear: real plant rooms are tall, and boilers and pipes need the room). Depth cap: 4 for every type except high-rises; for high-rises 4 + 6 + 6 (+6) = 16 to 22 below grade.
- **Water table.** No basement where its floor would be below the surface of water within about 12 blocks: rivers, coast, lakes and swamp (in effect, sea level wherever the lot is near water). Houses beside rivers and the coast then go without, which is true of real floodplain towns. A walk-out on a falling lot to a river may keep its lower storey when that storey's floor is above the water.
- **Parking: skip it, and don't plan it.** Minecraft has no cars. A garage is a large, low, empty, dark slab with nothing to furnish. It is the worst DS-24 case (a light grid over hundreds of cells) and needs 1:8 ramps (32+ blocks long per level). It would read as a void. The Minecraft analogue is a **metro or minecart station** under the metropolis core, which belongs to transit (ROADMAP 7.2 Create, later), not to buildings. A metropolis tower's B1 may get a **loading bay** with a cart door at grade on a back street on sloping lots (a walk-out), which gives the service read without the ramp.

### Inside: which rooms (boundary with the interior-designer)

Floor layout is mine; room contents are theirs.

- **Fully buried storeys hold only non-habitable uses**: `storage`, `plant`, a cellar (wine, root), a crypt, an undercroft hall or store.
- **Habitable rooms** (a kitchen, a servants' room, a bedroom, a flat) go below grade only in a half-sunk storey (window onto an areaway) or on the open side of a walk-out. That keeps DS-25's "every habitable room has a window".
- The boiler, cauldrons, pipes, wine racks and tombs are the interior-designer's call.

## (3) How basements show and how you get in

**Inside stair: the default.** The cellar flight sits directly under the main stair, stacked in the same well (DS-5, and the stacking rule in the circulation checklist). It is entered from the hall or kitchen, never from a bedroom.

- One-storey cottages and defensive towers use a floor trapdoor with a ladder.
- High-rises: the stairwell and the lift shaft continue to the lowest basement, with one stairwell door per basement storey onto a basement corridor. The **shaft pit moves to the lowest basement**. The lobby stays the entrance storey. This extends DS-22's "stacked from the lobby to the top" downward.

**Outside access**, per style:

- **Bulkhead hatch** (plains, taiga, snowy, plain farmhouses): a 2-wide flight down along a side or back wall, under open trapdoors set as the sloped hatch, ending at a cellar door. Railed or walled on its open sides (DS-8).
- **Undercroft street door** (medieval town shops and halls): a short flight down from the pavement, inside the lot's front margin, beside the main door. That makes a second entrance (DS-35). Railed.
- **Areaway** (city town houses and blocks in plain, mass-timber and modern styles: Georgian London, the Amsterdam *souterrain*, the brownstone): a railed sunken strip 1-2 wide along the front, steps down from the pavement, a service door and windows of a half-sunk storey.
  - It pairs naturally with a raised ground floor at rise 2-3. That is within DS-45's 3-riser plain flight for houses, but shops may not do it (rise <= 2).
  - It needs a front margin of 2 or more.
- **Walk-out** (any style that allows basements, plus workshops, on lots falling away from the street by a storey or more): the plinth SHOPLOW leaves on falling lots becomes a lower storey. It has windows and a door on the low side and a path to them (DS-6).

**From the outside**, a buried cellar shows as at most a taller plinth course. The half-sunk storey shows as small windows low in the plinth (1x1 or 1x2 accents per DS-33) over the areaway.

- No window or door may sit below the outside ground (SHOPLOW spec step 2 already says this).
- A window into an areaway needs the areaway floor at least 1 below its sill.

## (4) Constraints

**Worldgen.**

- **Caves.** Structure pieces are written after carving, so walls and floors overwrite cave air. A cave beside a basement stays outside a solid wall. Inside a large cheese cave (common below y 50) a high-rise's lower basements may stand exposed in the void. I can accept that. The planner cannot see caves (its probes give height and water only), and should not try.
- **Air inside.** Every basement interior cell must be written as air explicitly, otherwise stone and dirt stay in the rooms.
- **Seal the box.** Every face that touches terrain (walls and floor) must be full solid blocks, so aquifer water and cave fluids next to it never get an open face. Our writer does no neighbour updates, so a single gap floods later, when a player breaks a block nearby. Areaways and walk-out openings face open ground only.
- **Lava.** Overworld lava aquifers sit near y -54 and below, and the 16-22 cap from a settlement at y 63+ stays above y 40. Lava lakes near the surface are rare. Rule: the basement floor stays at y 40 or above (agent's number). Settlements on low coasts already get no basement through the water-table rule.
- **Beard (must be verified).** `beard_thin` takes its ground plane from the bottom of the piece box (`DistrictStructure` comment). If the building piece box drops to the basement floor, the beardifier would carve or flatten the terrain around the house down to the basement floor, and the house would sit in a pit. The basement must leave the beard plane at the entrance slab: a separate piece with `terrain_adaptation: none`, or a box split. The coder checks this in game early. This is the item's main technical risk.
- **Invariants.** The ROADMAP 3.9a `no-gap-below` invariant needs its rule change. IR storeys start at index 0 and y 0 today, so the IR needs either negative storey indices or an `entrance` storey field (the coder's call; IR.md documents it). Also needed: `shell-closed` for buried walls, `stair-rise` and `rooms-reachable` across the basement flight, and the floor-plan command labelling basement storeys.

**Light (DS-24).**

- No daylight reaches a buried storey, so it is lit by placed sources only. Every walkable cell needs block light 1 or more, the stair foot and the hatch landing included.
- Rule of thumb: one lantern at the foot of every basement stair. Beyond that, a light on a grid of pitch at most 10, as the high-rise plan already uses for ceilings.
- An areaway or bulkhead well gets a lamp by its door.
- Basements are the case where DS-24 most needs the light-propagation check it lists as a code candidate. The item should land that check, at least for below-grade storeys.

## Alternative

**Walk-outs only, no buried cellars.** Lower storeys appear only where a falling lot exposes them (SHOPLOW step 4 extended), plus high-rise plant levels.

- Gains: no beard, sealing or water-table work for houses, and every below-grade room is visible and lit by day.
- Better if the beard check shows buried pieces are costly, or if John judges hidden cellars not worth the work because a player rarely finds them.
- My choice is still to do cellars. Finding a cellar under a taiga cabin is exactly the kind of discovery that makes a generated house feel inhabited, and the shares above keep it occasional.

## Rule?

Yes, a proposed rule:

**DS-x Basements are reachable, lit, dry and sensible.** A storey below the entrance storey:

- is reached by an inside stair stacked with the main stair (a hatch and ladder in one-room buildings and defensive towers), or by an outside hatch, areaway or walk-out door that meets DS-4, DS-6 and DS-8;
- is lit per DS-24;
- holds no habitable room unless that room has a window onto an areaway or the open side of a walk-out;
- is never built where its floor would lie below nearby water;
- in high-rises, has the stairwell and the lift shaft continue to the lowest basement level (extends DS-22).

**Why:** John 2026-10-26, "houses also should get basements sometimes ... highrises to have multiple levels". A dark or flooded cellar is a spawner or a bug. **Scope:** all types that take basements (the table above is guidance, not rule). **Enforcement:** code candidates: `rooms-reachable` and `stair-rise` across below-grade storeys, the DS-24 light check on below-grade cells, `basement-dry` (floor y not below the water surface within 12), and `window-per-room` for habitable below-grade rooms.

No parking rule is needed. Recording "no car parks; transit stations later" in ASSUMPTIONS is enough.

## Recommended ROADMAP item

- [ ] **7.x BASEMENT: storeys below the entrance (cellars, undercrofts, walk-outs, high-rise plant levels)** (John 2026-10-26, chat; architect's answer `.claude/reports/basements-architect.md`, interior-designer's for contents). Buildings may have storeys below the entrance storey, of three kinds: buried (cellar), half-sunk (areaway) and walk-out (falling lot). Shares, depths and uses per type, style and tier are in the architect's table. No parking. Steps:
  - **B0 IR and mod**: below-grade storeys in the IR (negative index or an entrance-storey field; IR.md); invariants (`no-gap-below` rule change from 3.9a, `shell-closed` and `stair-rise` for buried storeys); floorplan and viewer labels. In the mod: basement interiors written as air, terrain faces sealed with solid blocks, and **the beard plane stays at the entrance slab** (verify in game first: biggest risk); basement floor y >= 40.
  - **B1 house and shop cellars**: one level by style share. Stair stacked under the main stair (hatch and ladder in one-storey cottages). Uses `storage` / cellar only. One light at the stair foot plus a grid. Skipped where the floor would be below water within 12 blocks. DS-24 light check for below-grade cells.
  - **B2 walk-outs**: on lots falling away from the street by 4 or more, the SHOPLOW plinth becomes a lower storey with windows and a door on the low side and a path (DS-6). Workshops get a cart door there.
  - **B3 high-rises and city blocks**: 1 / 2 / 3 levels at 7-12 / 13-25 / 26+ storeys (4 for metropolis glass towers over 40), 4-high storage and 6-high plant levels. Stairwell and shaft continue down, one stairwell door per level, and the shaft pit moves to the lowest level. Back-of-house and plant programme per the interior-designer.
  - **B4 later, polish**: hall undercrofts, temple crypts, medieval undercroft shops with a street door, plains bulkhead hatches, city areaways with half-sunk storeys.

  Depends on SHOPLOW 7.17 (the frontY floor reference, the cut and retaining machinery, bank buildings), and for B3 on the high-rise plan B1-B2 (core block, plates). M-L. B0 needs a lead-coder plan (IR change plus the beard question).
