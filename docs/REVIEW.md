# Things for John to review

Judgments only a human can make: does a building read as built by a person,
does a style feel right, how a city looks in motion in-game, and bugs first
seen in real play. Automated tests and agent-reviewed galleries cover the rest.
Agents add an entry whenever a coder or verifier says "needs a human look";
John works through them whenever convenient, or at a gate.

**How to use it.** Pick any open item, look, and write what you saw on its
`Result:` line ("fine", "roofs too steep", "still happens"). Tick it `[x]` when
settled, or just tell the coordinator and it records them. Anything that needs
work becomes a ROADMAP item or a Polish backlog line. Settled items move to
"Done" at the bottom.

**Entry format** (append at the end of its group; this file is union-merged):

```
- [ ] **<item id>**: <what to look at, short>
  Where: <gallery path, screenshot, viewer URL/seed, or in-game steps>
  Look for: <what good and bad look like>
  Result:
```

## Open

- [x] **0.1a**: mod licence and author in fabric.mod.json (placeholder MIT, author John Reeves)
  Where: mod/src/main/resources/fabric.mod.json; ASSUMPTIONS row
  Look for: the licence you want for the mod (affects what we may borrow from other mods)
  Result: MIT with John as author (John, 2026-10-08)

- [x] **3.12 Phase 3 gate**: generator v0 seed gallery, invariant report and diversity report
  Where: [the pack's README](review/phase3/README.md); 16 PNGs in `docs/review/phase3/gallery/` (`<style>[-flat]-<preset>.png`: plain and medieval, noise and flat terrain, presets front-left, back-right, top, front; seeds 0-15 on a 20x20 lot), [invariants.md](review/phase3/invariants.md) (1,280,000 cases, 0 failures), [diversity.md](review/phase3/diversity.md). Start with `plain-front-left.png` and `medieval-flat-back-right.png`.
  Look for:
  1. Does v0 read as plain houses (a box, 1 to 3 storeys, a gable roof, a door, windows)? It is meant to be plain; variety comes in Phase 4. Plain and medieval differ only in colour at v0.
  2. Is the camera framing right for review (building size in the cell, the angle of the three perspective presets)?
  3. Is anything in the IR ([docs/IR.md](IR.md)) a problem for towers or city blocks (storey grid, volumes, per-volume roofs, per-storey tags)?
  4. Is the flat-shaded `top` view useful, or should it go (or get shading so roof ridges show)?
  5. The noise terrain is the same patch in every cell (one terrain spec, building at the same lot position). Fine for review, or do you want each seed on different ground?
  Agent notes: 3-storey houses show four brown bands (a floor slab per storey plus the top slab under the roof), so they can look like 4 storeys; the IR has at most 3 (spec range [1, 3], checked in the gallery IR and the code). Small houses look alike from outside (seeds 0 and 8: 7x6 vs 7x5, both 2 storeys; 64 of 256 seeds repeat another seed's shell voxels exactly, see diversity.md). The thin line in the grass of `plain-flat-top.png` seed 5 is a viewer rendering artifact.
  Result: These all look good. Couple notes below. -- @John
   1. These read as plain houses. I like the variety in size and shape. I’d like to see things like wings and stories that aren’t all the same size - like how some old houses are wider with an overhang on top. But it sounds like that kind of thing comes in phase 4.
   2. Camera framing is good. The top view might be a little too dead on, so they all just look like a plain rectangle. A slight angle from the top view might help.
   3. Looks good so far.
   4. Shading. Also sound like this relates to 2.
   5. Using the same patch is find for now.


- [x] **5.12 Open ground storeys** (John's phase 4 shop and workshop comments): do the three shop forms read as a shop (arcade: upper storeys on posts over a walkway; market hall: the whole building on a row of posts, open floor under it, steps up the front; enclosed as before) and does the workshop's covered porch (a lean-to canopy on posts in front of the hall) read as a work yard?
  Where: `docs/review/phase5/` (`shop-open-flat-front-left.png` seeds 6, 13 arcade, 5, 14 market; `shop-open-flat-back-right.png` shows the posts; `shop-open-slope-front-left.png` on a 1 in 12 slope; `workshop-open-front-left.png` seeds 1, 3, 4, 6, 7, 11, 12 have a porch)
  Look for: market hall floors that read as an empty box on sticks (no stalls yet; hooks need a room); arcades only 2 to 3 deep with one post row; porch canopy stepping up towards the wall; the shop window sitting 1 to 3 blocks above the street on a slope (lots steeper than 1 in 12 are now rejected for shops).
  Result:
    - @John 2026-10-21: These look much better and more varied.
      - I still maintain the issue brought up by another agent and I agreed, that store fronts with big glass front windows don’t make any sense at the top of a tall flight of stairs, at least without some kind of landing area, like a kind of balcony?
        - @coordinator 2026-10-21: Agreed; this is now a follow-up item (5.12 follow-up "shopfront landing", ROADMAP 5.12 note). A coder is giving lifted shopfronts a landing or balcony across the front at door level (or a shopfront set back behind a porch), so the display window opens onto a walkable deck instead of the top step. You will see it in the Phase 5 gate pack.

- [x] **5.2 SK skeleton** (cell skeletons over real terrain): do the plans read like settlements: a hub with a square and hall, main roads that follow the land and bridge rivers, arterials on tile borders in a city, gates where cells meet?
  Where: `docs/review/phase5/skeleton/` (`skeleton-river-town.png`: a town on the recorded river window; `skeleton-joined-metropolis.png`: a 5-cell metropolis on the coarse world dump); live: `viewer/fixtures/settlement/skeleton/*` through `plan.html?city=<dir>&terrain=<dir>terrain.json.gz` (see its README)
  Look for: the urban mask (layer `mask`) being too patchy on hilly ground (the 1-in-2 slope rule is strict); main roads that wander or zigzag at 45 degrees; arterial grids that look too regular or too dense in a metropolis; hub squares placed off the road they should sit on; exits that go nowhere.
  Result:
  - @John 2026-10-21: These look great, I’m excited to move forward. At some later point we might want to refine or make various types, but this looks really good as it is.

- [ ] **D1 hamlet and village (p5-d1-villages)**: do the small settlements read as a hamlet and a village?
  Where: `cd viewer && nix develop -c npm run city-gallery` with `FB_CITIES=hamlet-river,village-river,hamlet-mixed,village-mixed`, then `viewer/out/city/<name>/plan.png` and the `*-overview.png` / `*-street.png` frames.
  Look for: a hamlet is a short track with a handful of houses and a workshop each side, no square; a village has a straight main street, a cross street on about half, a green at the hub with a temple or hall facing it from the side, lanes, and the houses lining the streets. Bad: a ruler-straight street feels too artificial, the green sits oddly, a village that is all one street.
  Result:

## Done

- [x] **3.6 storey slider**: in the viewer, lowering "storeys up to N" below the top storey hides the roof together with the upper storeys (roofs are storey −1, shown only at the top setting, per the Phase 3 plan). Does that feel right, or should the roof stay or hide on its own toggle?
  - This is fine. -- @John 

- [x] **2.4 S4**: region maps in `docs/research/spikes/s4/*.png` — do city density and placement at 1024-block cells, and the size of joined cities, look like where a person would put towns?
  * Yes this looks good. People like to put cities near water features, but these cities seem to often grow large enough that they do get close to water. Villages kinda go wherever. But this looks good. -- @John
- [x] **2.5 S5**: terrain adaptation on a steep hillside in `docs/research/spikes/s5/` (`steep-thin_found-v0.jpg`, `steep-thin_found-v1.jpg`, vs `steep-none-v0.jpg`) — is a flat shelf per building with 5-12 block cut faces on the uphill side acceptable, or do we want retaining walls, terraces or level streets (which would trigger S7)?
  Where: `docs/research/spikes/s5.md`
  Look for: cut faces beside the hall and tower; grass vs bare stone on the cuts; whether stepped bays on the big hall read as built.
  Result: Stick with the S5 way. We can change it later but I think it looks great. -- @John

- [x] **2.7 S7**: `docs/research/spikes/s7/cut-all-side.jpg` and `fill-all-side.jpg` — would a cut floor/wall like this look acceptable around a city? Prefer fill, as the note suggests? Does the steep wall at the cut edge need a softer fade?
  * This looks acceptable. -- @John
- [x] **4.3 roofs**: do the roofs read as built? Gable, hip, cross gables on L/T/U/courtyard, dormers, overhang, flat parapets on the setback tower.
  Where: `npm run gallery` medieval/plain presets; or `-Dfb.dumpRoofForms=viewer/gallery-data/gen` on `RoofPassTest.dumpForms`, then `gallery.html?dir=roof-<form>` (forms gable, hip, pyramid, shed, mansard, flat)
  Look for: valleys where wings meet the main roof, gable ends closed, dormers that look attached, overhang depth, whether shed/mansard are worth keeping or too blocky

- [x] **4.2 Massing** [M]: do house wings, jetties and setbacks read as a person built it?
  Where: `npm run gallery` then `viewer/out/gallery/medieval-flat-front-left.png` (seeds 0..15); more with `gallery.html?style=medieval&terrain=flat&from=16`. Jetties: seeds 16, 27. Setbacks: seeds 2, 32. Courtyard: seed 15.
  Look for: wings clearly different in width and height from the main block; jetty overhang of 1 block that looks like an old overhanging upper floor, not a bug; roofs over wings and jetties are still plain (roof step 4.3).
  Result:
- [x] **4.6 city block**: does a block read as a row of separate houses joined by party walls around a courtyard?
  Where: `FB_PRESETS=block-medieval,block-plain-flat npm run gallery` then `viewer/out/gallery/block-medieval-front-left.png`, `-front.png`, `-top.png`, `-cutaway.png`; `block-medieval-flat` (100x80).
  Look for: unit seams visible (height steps, roof forms, window rhythm) rather than one long wall; the alley gaps (about half the blocks) read as a lane into the yard; courtyard size; doors on streets only. Known: no jetties or setbacks on units, shared storey bands run across units.

### Temple (4.6)

- [x] **Temples read as temples, not houses or towers**
  Where: `FB_PRESETS=temple-medieval,temple-plain,temple-flat npm run gallery` (in `viewer/`) then `viewer/out/gallery/temple-*-front.png`, `-front-left.png`, `-cutaway.png`. Bell-tower variants: medieval seeds 0, 1, 2, 3, 12, 13, 15.
  Look for: a clear axis (porch or tower, tall nave, narrower apse), the door in the middle of the front, generous nave height; whether the tower variants look like a bell tower or like a tall house (windows repeat per storey today); side chapels reading as transepts.
- 4.6 tower: does it read as a pre-modern tower (watchtower, keep) or as a thin high-rise? Look at `tower-medieval` front-left and cutaway (npm run gallery, FB_PRESETS=tower-medieval); window bands are still the shared ones, no slit windows or spire.

- [x] 4.6 hall: do the four sub-kinds read as a market hall, a town hall with a tower, a cathedral and a bath? Look at `hall-medieval`, `hall-flat` and `hall-big` (npm run gallery, FB_PRESETS=hall-medieval,hall-flat,hall-big); the roofs are the style's, lowered on wide spans; the arcade is glazed arches; no crossing tower, no spire.
- [x] **4.6 Wall and gate** [T-wall]: does the gatehouse read as a town wall segment?
  Where: `npm run gallery` (`FB_PRESETS=wall-medieval,wall-plain,wall-flat`) then `viewer/out/gallery/wall-medieval-front-left.png`, `wall-flat-cutaway.png` (passage and galleries).
  Look for: gate arches and flanking towers legible from the field side; curtain walls too plain or too tall; battlements read as merlons; the wall is a flat 5-high strip beside 2-3 storey towers.
- [x] **4.6 Workshop** [T-workshop]: does the tall open hall with the 2-wide front door, the lower living wing and the lean-to read as a smithy/workshop, not a tall cottage?
  Where: `FB_PRESETS=workshop-medieval,workshop-flat npm run gallery` then `viewer/out/gallery/workshop-medieval-front-left.png` and `-cutaway.png`.
  Look for: hall clearly taller than the wing, door wide enough to read as a cart door, work hooks (blue) inside the hall.
## 4.7 plains style (house)
  Where: `npm run gallery` with `FB_PRESETS=house-plains,house-medieval`, `viewer/out/gallery/house-plains-front-left.png` (seeds 0..15).
  Look for: does it read as a rural cottage/farmhouse, distinct from medieval (no jetties, no timber)? Is the wall mix (birch/oak/plaster) too busy? Are the brick/terracotta roofs right?
- [x] **4.6 high-rise**: do the setback towers read as skyscrapers?
  Where: `FB_PRESETS=highrise-plain,highrise-medieval,highrise-flat npm run gallery`, `viewer/out/gallery/highrise-*-front-left.png`, seeds 0..15 (cutaway on `highrise-medieval`).
  Look for: podium visible at the street, 2-4 steps of setback up the shaft, a different crown; storeys of 4 blocks make a 60-storey tower 250 blocks tall (is that the right feel in game?). Window bands repeat evenly until the facade step (4.4) lands.
  Result:
- [x] **4.6 city block, ground**: blocks only get built on gentle ground (steeper than 1 in 16 is rejected) until per-wing ground levels in 6.3, and need lots of at least 52x52. OK for now?
  Where: `docs/log/2026-10-19-p4-t-block.md` (round 2); `fractal-cli gen --type block --lot 64x56 --terrain plane:64:1:0:3` exits 2.
  Look for: whether worldgen block sites (Phase 6) can be picked on flat enough ground, or whether you want terraced blocks sooner.
  Result:

## 4.10 Phase 4 gate

- [x] **4.10 Phase 4 gate**: per building type, would you believe a person built this? Generator v1: nine types, seven styles.
  Result (John, 2026-10-21, in the pack README): passed; "we can move forward with phases without being perfect". His notes on house floor plans, archways and covered ground floors for shops and workshops became ROADMAP 5.11 and 5.12.
  Where: [the pack's README](review/phase4/README.md) (what to look at per type, with seeds); a subset of PNGs in `docs/review/phase4/gallery/<type>/` and the style sheet in `gallery/styles/` (full set: `npm run gallery` in `viewer/`, then `viewer/out/gallery/`); [invariants.md](review/phase4/invariants.md) (168,000 cases, 0 failures), [diversity.md](review/phase4/diversity.md).
  Look for, per type (answer here or on the type's own entry): house (4.6 house below), shop (4.6 shop below), workshop, tower, temple, wall and gate, high-rise, hall, city block (entries above). Questions collected during the phase, each also in its own entry:
  1. Facades: is the medieval half-timbering too dark or heavy? (4.4 facades below)
  2. Wall: half-timber (and windows) on a stone town wall looks odd? (4.6 Wall and gate)
  3. Tower: does a timber tower read as pre-modern? (4.6 tower; that entry has no checkbox, answer here)
  4. Workshop: tall cottage with a barn door? (4.6 Workshop)
  5. Shop: does the shopfront read as a shop? (4.6 shop below)
  6. Temple: the bell towers read as tall houses; acceptable for now? (Temples read as temples)
  7. City block only on gentle ground (up to 1 in 16) and lots of 52x52+, until per-wing ground levels in 6.3: OK? (4.6 city block, ground)
  8. City block in the plain style reads as one long banded facade: fine? (4.6 city block)
  9. Plains: wall mix too busy? (4.7 plains style) Desert walls speckled? Jungle palette reads as jungle/bamboo? (4.7 styles below)
  10. Plan, open: per-wing ground levels (ADR 0003 W5) deferred to 6.3, so deep types are tested only on ground up to their `maxGrade` (1 in 3; block 1 in 16). OK?
  11. Plan, open: jetties over the street (past the lot edge) wait for Phase 5's street rules. OK?
  12. Plan, open: `medieval` is now a style by name only (plains took its biomes; still requestable). Keep, rename, or give it biomes back?
  Agent notes: pictures looked at 2026-10-19 (README, "What to look at"). Medieval blocks and halls read best; walls and towers read most like houses (windows and timber framing on every storey); the plains, desert, taiga and snowy colour mixes look speckled at gallery scale.
  Result:
- [x] **4.6 house** [T-house]: do v1 houses read as built by a person (wings, jetties, room program)?
  Where: `docs/review/phase4/gallery/house/house-medieval-front-left.png`, `house-medieval-cutaway.png`; full set: `viewer/out/gallery/house-plain-flat-*.png`, `house-slope-front-left.png`
  Look for: living room, kitchen and entry downstairs, bedrooms upstairs (cutaway: storey 0 only), hearth and bed hooks; more single-storey houses than before on small lots (a known effect of rejecting cramped upper floors).
  Result:
- [x] **4.6 shop** [T-shop]: does the shopfront read as a shop, not a house with big windows?
  Where: `docs/review/phase4/gallery/shop/shop-medieval-front-left.png`, `shop-plain-front.png`; full set: `viewer/out/gallery/shop-flat-front-left.png`
  Look for: centred door between 2-3 wide display windows under a fascia, a taller ground storey; on slopes the shop floor sits up a flight of steps on a stone base (is that acceptable on a street?); no awning or sign yet.
  Result:
- [x] **4.4 facades** [Fa]: is the medieval half-timbering too dark or heavy?
  Where: `docs/review/phase4/gallery/house/house-medieval-front-left.png`, `gallery/block/block-medieval-front-left.png`, `gallery/tower/tower-medieval-front-left.png`
  Look for: dark posts and rails covering much of the wall; whether bays and window rhythm on tall buildings (`gallery/highrise/highrise-plain-front-left.png`) look stamped; no diagonal braces yet.
  Result:
- [x] **4.5 interiors** [I]: do the cutaway floor plans look like rooms a person would lay out?
  Where: `docs/review/phase4/gallery/{house,temple,hall,block,workshop}/*-cutaway.png`; every preset's cutaway in the full set (`viewer/out/gallery/*-cutaway.png`)
  Look for: room sizes, doors between rooms, the stair core against a wall, hook markers (furniture places). Interiors are not gameplay yet; this is about the plan only.
  Result:
- [ ] **4.7 styles** [St-desert, St-taiga, St-snowy, St-jungle]: does each new style read as its biome?
  Where: `docs/review/phase4/gallery/styles/` (a house and a large type per style; `desert-*`, `taiga-*`, `snowy-*`, `jungle-*`), and in the full set `viewer/out/gallery/tower-<style>-front-left.png`
  Look for: desert flat sandstone roofs (walls speckled by the terracotta in the mix: drop it?); taiga steep spruce roofs; snowy small windows, stone and spruce; jungle green roofs and bamboo/jungle wood (does it read as jungle?). Known: no dormers on 2/1 roofs, no verandas or raised floors in jungle.
  Result:
  - @John 2026-10-21: I think at some point we’re going to want more variation for biomes. It’s not just about the colors or the blocks used, houses may be actually designed differently in different biomes. Using different materials might mean, for example, a different style roof. Desert homes might not have gabled roofs, snowy biome homes might want to have very steep pitched roofs, etc. Also a general thought on the color / block palette, the timbers look great, I love that. The plains look is a bit splotchy, like maybe they need to be made of blocks that are more similarly colored, subtle shading instead of dark and light scatterings of blocks.
    - @assistant 2026-10-21: Today a style changes roof form and pitch as well as blocks (`fb-style/1` roof settings): desert has flat roofs with a parapet and no gables; taiga and snowy have steep 2-in-1 gables (rise 2, run 1), the others 1-in-1; jungle mostly hips with a deep 2-block overhang; snowy also has fewer windows (wider bays, blank corners). What is not there yet is structural difference per biome: desert courtyard houses and flat-roofed adobe blocks, jungle raised floors and verandas, snowy compact plans. Also known: plains walls mix birch, oak, white terracotta and smooth stone, which is the splotchiness you see.
    - @coordinator 2026-10-21: Added two ROADMAP Polish backlog lines: "plains palette: smoother, more similar wall blocks (less speckle)" and, as a later-phase idea, "biome-specific building forms beyond palette and roof pitch (desert courtyard and flat roofs, jungle raised floors and verandas, snowy steep roofs and small windows)". The plains palette is a small fix; I am scheduling it now.

- [x] **Tower watchtowers** (gate follow-up): do the stilted lookouts (spindly legs, stair up the front, small room), the top-heavy towers (shaft with a wider room overhanging on all sides) and the unglazed window holes read as watch towers?
  Where: `docs/review/phase4/gallery/tower/tower-medieval-front-left.png` (seeds 4 and 11 stilted, 6 and 10 top-heavy; seeds 1, 3, 5 have window holes)
  Look for: legs too thin or too dark; the stair (no rail) climbing the front wall; the lookout small against its legs; holes dark and unframed.
  Result:
  - @John 2026-10-21: These look good. On the point of railing, when would railings come? Could that be done as the decoration phase, when furnishings etc go in? Railings are hard in Minecraft. But we should consider railings. **Especially on stairs up to houses and shops**.
    - @coordinator 2026-10-21: Railings become ROADMAP item 6.9 "Railings on stairs, landings and balconies", alongside the mod adapter in Phase 6: a `rail` IR role on stair flights, landings and balconies, mapped to fence or wall blocks per style in the palette, with the viewer drawing it. Front steps up to houses and shops come first.

- [x] **5.11 House circulation and archways**: do house floor plans now make sense (hall or central room, wings reached through a passage not a bedroom, a bedroom never a walk-through), and do the archways read as open plan?
  Where: `docs/review/phase5/circulation/house-medieval-plans.md` (ground-floor plans, before and after), `house-medieval-cutaway-after.png`, `house-plains-cutaway-after.png`, `house-desert-cutaway-after.png`; compare `docs/review/phase4/gallery/house/house-medieval-cutaway.png`
  Look for: any remaining chain of rooms you would not build (15 % of plans keep one on purpose: too many, too few?); archways too common (40 % of doors between public rooms) or too wide (1-3); halls that are too small to be a hall.
  Result:
  - @John 2026-10-21: these are better now. One note I'd like to add is some or more balconies in all residential and commercial (highrise) buildings. This can come in a future iteration.
- [x] **JT Jetties oniover streets**: do upper storeys that reach out over the street look right (medieval houses and shops on a lot with a street brief)?
  Where: `docs/review/phase5/jetties/house-medieval-jetty-front-left.png`, `shop-medieval-jetty-front-left.png` (seeds 0..15, `--air 1` houses, `--air 2` shops); regenerate with `cd viewer && npm run gen -- house-medieval-jetty shop-medieval-jetty && FB_PRESETS=house-medieval-jetty,shop-medieval-jetty npm run gallery`.
  Look for: the overhang reads as a jetty (bressumer beam under the floor edge, not a floating box); 1 block past the lot edge looks modest, 2 on wide streets is not silly; a front jetty of 2-3 blocks from the wall is deeper than the old ones (the front wall stays at the lot edge so the yard under it is only 1 block); shop arcades never reach into the street.
  Result:
  - @John 2026-10-21: looks great. I mangled something in the bullet above, not intended 
- [x] **5.10 R2 Water bias**: do city sites now visibly prefer water, and are the two deviations from the plan acceptable?
  Where: `docs/review/phase5/region/before-12345.png` vs `after-12345.png` and `after-heat-12345.png` (same for seed 987654321); numbers in `docs/log/2026-10-21-p5-r2-water.md`
  Look for: (1) the raised water cap admitted so many coast cells that the plan's constants gave +50 % big settlements, so the boost now only picks among candidates (roll and minSuitability use the unboosted score) and coastal footprints roll at x0.5 (`Tiers.COASTAL_CHANCE`): count +9 %, is the shift strong enough? (2) "+50 % within 256 blocks of water" was unmeasurable (31 of 32 big sites already within 256), so it is measured at 64 blocks: 31 % -> 46 %. Big join circles still cover as much water as before.
  Result:
  - @John 2026-10-21: looks good, easy to change later it sounds like. 

- [ ] **5.12 SHOPLAND shopfront landings** (John's note on 5.12): does a lifted shop now read right, a landing at door level across the front instead of the window at the top of a flight?
  Where: `docs/review/phase5/shopland/` (`shop-slope-front-left.png` shops on a 1 in 9 slope: a terrace across the front, flight down at one end; `shop-flat-front-left.png` and `shop-flat-front.png` seeds 5 and 14 are market halls with a deck on posts, the open floor under still open to the street; `shop-slope-front.png` the same from the front)
  Look for: enclosed shops: a solid terrace with a tan (stair-coloured) top and no railing yet; market halls: a 2-deep deck on posts (balcony) with the flight coming down on the lot front row at one end;  windows now stand level with the landing.
- [ ] **5.2 DL District layout**: do the streets, blocks and lots read like a city someone planned?
  Where: plan view `viewer/out/plan/districts-synth-city.png`, `districts-mixed-city.png`, `districts-river-town.png` (run `npx playwright test e2e/districts.spec.ts`, fixtures in `viewer/fixtures/settlement/districts/`); 3D `viewer/out/district/dl-d0_0-overview.png`, `dl-d1_0-street.png` (README there for the scene commands)
  Look for: grid streets straight through whole rows of tiles and plain streets on dense borders; organic lanes branching off the roads and crossing tile borders at ports; whole-block buildings only inside grid blocks; houses and shops with their fronts to the street; no lots over water; the hub square with its hall and temple; dead ends and thin empty ground between lots; the straight seam where lots on both sides of a tile border back onto each other
  Result:
- [ ] **5.1 D0 City gallery**: do the nine gate cities read as the right size and shape of settlement (hub, main roads, grid core, outskirts), and do the district street views look like streets someone built?
  Where: `cd viewer && nix develop -c npm run city-gallery` (about 2 minutes), PNGs in `viewer/out/city/<preset>/` (`plan.png`, `<district>-overview.png`, `<district>-street.png`); presets in `viewer/city-presets/`.
  Look for: main roads that stop short of the hub square (known: the hub-square work should close them), houses on lanes that lead nowhere, terraces where roads cross slopes, highrise canyons in the metropolis cores.
  Result:
  - @John 2026-10-21:
    - It looks like some of the houses lost bedrooms.
    - We should try to lean towards more square-leaning rooms. E.g. it looks like seed 9 gained a super long skinny “living room” and dropped to one small bedroom.
    - Lean towards having multiple bedrooms before ever having multiple other types of rooms. As usual, not 100% of the time.
    - @coordinator 2026-10-21: Agreed. Follow-up item CIRC2 is being coded now: a room aspect limit (nearer square), a bedroom-first room program (more bedrooms before a second room of another kind, not always), and bedrooms per house measured before and after. Results go in the Phase 5 gate pack; noted under ROADMAP 5.11.

- [x] **Plains palette calmer (p5-plains-palette)**: answers John's "plains look is splotchy". Walls are now birch/bamboo planks/smooth sandstone, roof bricks/red terracotta/terracotta, foundation cobble/stone/andesite, beams oak/spruce log (no more dark brown terracotta, white terracotta, smooth stone or birch log speckle). Before/after seed gallery (front-left, seeds 0-15): `docs/review/phase5/plains-palette/house-plains-before.png` and `house-plains-after.png`. Does plains now read as one calm material with subtle shading? Medieval is untouched.
    - @John 2026-10-21: this is much better!

- [ ] **CIRC2 Squarer rooms and more bedrooms** (answer to the 5.11 review): do house plans now look like a person laid them out, with squarish rooms and more than one bedroom?
  Where: `docs/review/phase5/circulation/house-medieval-plans.md` section "After CIRC2" (seeds 9, 7), cutaways `house-{medieval,plains,desert}-cutaway-after-circ2.png` (compare `-after.png`); numbers in `docs/log/2026-10-21-p5-circ2.md`.
  Look for: rooms still too long or too many tiny ones (long wings are now cut in two, so rooms are smaller); too many or too few bedrooms (1.8 per medieval house, ground-floor spare rooms become bedrooms 85 % of the time); a house of wings with no kitchen (seed 7).
  Result:

- [ ] **House kitchen and living room (p5-house-program)**: does every house now have a kitchen and a living room, in sensible places?
  Where: `docs/review/phase5/circulation/house-medieval-plans.md` section "After house program" (seeds 7, 9), numbers in `docs/log/2026-10-21-p5-house-program.md`; gallery `house-medieval`, `house-plains`, `house-desert`.
  Look for: kitchens (hearth) beside the living room or entry; houses that now have too few bedrooms (1.7 per medieval house, was 1.8); a one-room hut that is only a bedroom; a ground floor that is a single kitchen-hall under bedrooms.
  Result:
