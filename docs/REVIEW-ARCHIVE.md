# Review archive

Resolved items from [REVIEW.md](REVIEW.md), kept with their full text and
John's answers. Agents move an item here once it is settled and filed
(John, 2026-10-25); newest last. The first move (2026-10-26) took every
ticked item and the old "Done" list, grouped under their old headings.
Where an answer became a rule, ROADMAP item or plan change, the agent's
`@assistant` reply under it says where.

## Earlier Done list (Phases 2-4)

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

## Early items

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

- [x] **Whole-city roads (p5-roads)**: are the hall and temple placements, and the cities with no unreachable buildings, believable? Is it acceptable that arterial blocks with no link to a main road stay empty (snow city lost 27 lots)?
  Where: `docs/review/phase5/roads/` (plan views of gate-snow-city, gate-temperate-city, gate-metropolis-temperate); `npm run city -- gate-snow-city` then the plan view for any other gate city; numbers in `docs/log/2026-10-21-p5-roads.md`.
  Look for: hall and temple labels on lots beside or near the square (some now a street away from it); yellow arterials without lots at the edges; road-only tiles at the edge of a town (a main road now continues through them).
  Result:
  - @John 2026-10-21: the snow city roads seem to have some problems. If I understand the image right, it looks like there are sections of arterial that are just detached from the rest of the network entirely. That can’t be allowed (until we get to “themed” cities, like an abandoned city (not planned) but even then there would be a path where the old road used to be). Would there be other paths that fill in the blanks? The roads look very well connected and pretty amazing in gate-metropolis-temperate-plan.png. Those are great. gate-temperate-city-plan.png looks pretty good, like a slightly smaller city. Overall, it seems like there needs to be a few more smaller roads off of arterials. There seems to be a lot of arterials with dense city and then _nothing_ past that. Also, on the temperate city I’m noticing there seems to be arterials with highrises (good, love it) and then nothing past that. Cities never end in highrises, there are lower density areas at the edge and suburbs, etc. I can imagine cases where a city has a very fast rise from the edge to highrises, but it probably shouldn’t _start_ with highrises at the edge of a city.
    - Also, I want to put on the roadmap something I don’t think we’ve included - subways. Modern cities could have subways (and above ground railroads) and since Minecraft has minecarts it even works with vanilla. I can imagine an amazing network of subway stops in dense metropolises. I want to include that in the city planning in the near future. Not sure where it should fit in.
    - I like the placement of halls and temples, etc.
    - @coordinator 2026-10-21: Agreed, detached roads should not happen. A coder is on it now (item ROADS2): link every arterial chain to the network with connector roads (or drop pieces that cannot be linked), add a city-check rule so a detached road fails, and add minor streets off arterials and main roads so the built-up area tapers instead of stopping dead. Before/after plans of the snow and temperate cities will go in docs/review/phase5/roads2/. Subways and surface railways: I will add them to the ROADMAP as a new item with a suggested place (after the Phase 6 integration, as a transit layer in city planning: stations in dense districts, lines on arterials, minecart rails underground) for you to confirm.
      - @John 2026-10-22: Thanks! I just want to confirm that this got on the roadmap, I don’t see it at the moment. Also I noticed some existing research items on [[docs/research/overlays/lost-cities-road-planning]] might be of value, not sure.
        - @coordinator 2026-10-22: It is on now: a new "Phase 7: World-level features (later)" section in [ROADMAP.md](ROADMAP.md), item 7.1 "Transit: subways and surface railways" (stations in dense districts, lines along arterials and main roads, minecart rails underground, surface lines later between settlements along 6.10's roads). It sits after the first release; tell us if you want it earlier. The Lost Cities notes help, mostly for the intercity roads: [lost-cities-road-planning.md](research/overlays/lost-cities-road-planning.md) describes its working intercity highway graph (bounded search, one owner per link, per-chunk lookup) and its failures (unlinked hubs, grid-like L routes), which our plan's design avoids; a "Related research" section in [intercity-roads.md](plans/intercity-roads.md) now says what we take from it. For transit, the same note gives the shape for lines (global hashed lines clipped to the city), and it shows Lost Cities already has railways; its railway planner isn't researched yet, so 7.1 starts with a note on it.

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

- [x] **4.7 styles** [St-desert, St-taiga, St-snowy, St-jungle]: does each new style read as its biome?
  Where: `docs/review/phase4/gallery/styles/` (a house and a large type per style; `desert-*`, `taiga-*`, `snowy-*`, `jungle-*`), and in the full set `viewer/out/gallery/tower-<style>-front-left.png`
  Look for: desert flat sandstone roofs (walls speckled by the terracotta in the mix: drop it?); taiga steep spruce roofs; snowy small windows, stone and spruce; jungle green roofs and bamboo/jungle wood (does it read as jungle?). Known: no dormers on 2/1 roofs, no verandas or raised floors in jungle.
  Result:
  - @John 2026-10-21: I think at some point we’re going to want more variation for biomes. It’s not just about the colors or the blocks used, houses may be actually designed differently in different biomes. Using different materials might mean, for example, a different style roof. Desert homes might not have gabled roofs, snowy biome homes might want to have very steep pitched roofs, etc. Also a general thought on the color / block palette, the timbers look great, I love that. The plains look is a bit splotchy, like maybe they need to be made of blocks that are more similarly colored, subtle shading instead of dark and light scatterings of blocks.
    - @assistant 2026-10-21: Today a style changes roof form and pitch as well as blocks (`fb-style/1` roof settings): desert has flat roofs with a parapet and no gables; taiga and snowy have steep 2-in-1 gables (rise 2, run 1), the others 1-in-1; jungle mostly hips with a deep 2-block overhang; snowy also has fewer windows (wider bays, blank corners). What is not there yet is structural difference per biome: desert courtyard houses and flat-roofed adobe blocks, jungle raised floors and verandas, snowy compact plans. Also known: plains walls mix birch, oak, white terracotta and smooth stone, which is the splotchiness you see.
    - @coordinator 2026-10-21: Added two ROADMAP Polish backlog lines: "plains palette: smoother, more similar wall blocks (less speckle)" and, as a later-phase idea, "biome-specific building forms beyond palette and roof pitch (desert courtyard and flat roofs, jungle raised floors and verandas, snowy steep roofs and small windows)". The plains palette is a small fix; I am scheduling it now.
      -
      - @John 2026-10-21: sounds good, approved.

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
    - @coordinator 2026-10-21: Noted for a later iteration: added to the ROADMAP Polish backlog ("balconies on residential and commercial buildings, incl. high-rises"). The shopfront deck on market halls (SHOPLAND) is the only balcony-like element today.

- [x] **JT Jetties over streets**: do upper storeys that reach out over the street look right (medieval houses and shops on a lot with a street brief)?
  Where: `docs/review/phase5/jetties/house-medieval-jetty-front-left.png`, `shop-medieval-jetty-front-left.png` (seeds 0..15, `--air 1` houses, `--air 2` shops); regenerate with `cd viewer && npm run gen -- house-medieval-jetty shop-medieval-jetty && FB_PRESETS=house-medieval-jetty,shop-medieval-jetty npm run gallery`.
  Look for: the overhang reads as a jetty (bressumer beam under the floor edge, not a floating box); 1 block past the lot edge looks modest, 2 on wide streets is not silly; a front jetty of 2-3 blocks from the wall is deeper than the old ones (the front wall stays at the lot edge so the yard under it is only 1 block); shop arcades never reach into the street.
  Result:
  - @John 2026-10-21: looks great. I mangled something in the bullet above, not intended 
    - @coordinator 2026-10-21: Fixed ("oniover" → "over").

- [x] **5.10 R2 Water bias**: do city sites now visibly prefer water, and are the two deviations from the plan acceptable?
  Where: `docs/review/phase5/region/before-12345.png` vs `after-12345.png` and `after-heat-12345.png` (same for seed 987654321); numbers in `docs/log/2026-10-21-p5-r2-water.md`
  Look for: (1) the raised water cap admitted so many coast cells that the plan's constants gave +50 % big settlements, so the boost now only picks among candidates (roll and minSuitability use the unboosted score) and coastal footprints roll at x0.5 (`Tiers.COASTAL_CHANCE`): count +9 %, is the shift strong enough? (2) "+50 % within 256 blocks of water" was unmeasurable (31 of 32 big sites already within 256), so it is measured at 64 blocks: 31 % -> 46 %. Big join circles still cover as much water as before.
  Result:
  - @John 2026-10-21: looks good, easy to change later it sounds like. 

- [x] **Plains palette calmer (p5-plains-palette)**: answers John's "plains look is splotchy". Walls are now birch/bamboo planks/smooth sandstone, roof bricks/red terracotta/terracotta, foundation cobble/stone/andesite, beams oak/spruce log (no more dark brown terracotta, white terracotta, smooth stone or birch log speckle). Before/after seed gallery (front-left, seeds 0-15): `docs/review/phase5/plains-palette/house-plains-before.png` and `house-plains-after.png`. Does plains now read as one calm material with subtle shading? Medieval is untouched.
    - @John 2026-10-21: this is much better!

- [x] **CIRC2 Squarer rooms and more bedrooms** (answer to the 5.11 review): do house plans now look like a person laid them out, with squarish rooms and more than one bedroom?
  Where: `docs/review/phase5/circulation/house-medieval-plans.md` section "After CIRC2" (seeds 9, 7), cutaways `house-{medieval,plains,desert}-cutaway-after-circ2.png` (compare `-after.png`); numbers in `docs/log/2026-10-21-p5-circ2.md`.
  Look for: rooms still too long or too many tiny ones (long wings are now cut in two, so rooms are smaller); too many or too few bedrooms (1.8 per medieval house, ground-floor spare rooms become bedrooms 85 % of the time); a house of wings with no kitchen (seed 7).
  Result:
  - @John 2026-10-21: it’s an improvement. S9 is still weird, but weird is ok. I can make up a story why that house has a tiny bedroom and tons of living room space.

## Phase 5 gate

- [x] **Phase 5 gate**: cities in at least five biome mixes (one straddling a border, one larger than 512 x 512, one with a high-rise downtown); would a person have built each town there?
  Where: [the pack's README](review/phase5/README.md) (per city: plan, zoomed district with lots, skyline colours, fly-through frames, built lots by type and style); pictures in `docs/review/phase5/cities/<preset>/` (86 JPEGs; regenerate with `npm run city`, `node scripts/gate-pack-scenes.mjs` and `GATE_PACK=1 npx playwright test e2e/gate-pack.spec.ts` in `viewer/`); [city-check.md](review/phase5/city-check.md) (9 gate windows, 200 synthetic sites, both world maps, no rule ignored: 0 violations); [scale.md](review/phase5/scale.md); region maps before and after the water bias in `docs/review/phase5/region/`.
  Look for: does each biome mix read right (temperate/taiga, desert/badlands, snow, jungle/savanna, coast), does the desert/jungle border blend, does the metropolis downtown read as a downtown, do the hamlet, village and walled town read as such. Questions collected during the phase, each also in its own entry above where one exists:
  1. Villages too gridded (one straight street, houses lined up)? (D1 hamlet and village)
  2. Walls: every wall lot is a gatehouse, and no gate city has a wall ring; want curtain-wall segments and more walls? (D2 town walls)
  3. Downtown: 60-storey plateau over about 0.3 of the radius, almost no city blocks (2 of 2355 lots)? (Downtown skyline and avenues)
  4. Old town round the hub in every city (a city's mid-rise centre becomes old town)? (IRR1)
  5. Lots along diagonal avenues face cut lanes, not the avenue; small wedges stay empty? (IRR1)
  6. Arterial blocks with no main-road link stay empty (snow city reads as a ribbon, 103 lots)? (Whole-city roads)
  7. Hub squares moved up to 160 blocks or shrunk; hall and temple sometimes a street away? (Whole-city roads)
  8. Quays and plank bridges read as a waterfront? (Waterfront quays and bridges)
  9. Bedrooms per house down about 0.14 after the kitchen rule; seed 9's living room exactly 2:1? (House kitchen and living room; CIRC2)
  10. Savanna takes the desert style; keep or give it its own later?
  11. 5.9 scale check: probe bar (under 1,000 exact probes per district) not met, 4,900 to 8,900 now; close the phase with 5.9 unticked and carry it to 6.5 / 6.7?
  12. Build and flake changes this phase (README, "What changed"): `mod/build.gradle` JUnit deps and test heap, `cli/build.gradle` test inputs, `viewer/vite.config.ts` page inputs, `viewer/package.json` scripts `city` and `city-gallery`, `flake.nix` `SB_SPACE_IGNORE`. OK?
  Agent notes: pictures looked at 2026-10-21. Plans read as settlements on their terrain (cores on dry land between water, main roads following shores); the metropolis downtown is one block of equal 60-storey towers; desert walls look speckled; the village is one street. The 3D frames paint a whole district in its first building's palette, so mixed districts cannot be judged in 3D (use the plan and the counts).
  Result:
      - @John 2026-10-22: @coordinator I approve the gate, and am ready to move on. I added one more comment that I’d like addressed but I trust it can be done, and then please move on to the next phase.

- [x] **OPENPLAN open kitchens** (John's note on 5.11, 2026-10-21): look at `docs/review/phase5/circulation/house-plain-cutaway-open-plan.png` and `house-medieval-cutaway-open-plan.png` and the "After open plan" plans in `house-medieval-plans.md`. Do plain/plains/jungle houses read as open plan (kitchen and living joined by a 2-4 wide gap), and medieval ones as separate rooms with a door? Is the share (medieval 22 %, plain 83 % of kitchen links) about right, and is the lintel over wide gaps fine?
    - @John 2026-10-22: These look great. I like the lintels. If an appropriate block exists in the right material, half blocks might look best for lintels. slabs. And/or upside down stairs for arches. This might be a future refinement.
      - @coordinator 2026-10-22: Agreed, a future refinement: it is in the ROADMAP Polish backlog (5.11 OPENPLAN line: top-half slab lintels where the material has a slab, upside-down stairs for arches). It needs slab and stair shapes in the block mapping, which comes with Phase 6's adapter (6.1).

## ROADS2 (John's roads feedback)

- [x] **ROADS2 (p5-roads2)**: is every road now part of one network, and does the built-up area thin out towards the edge instead of ending at the arterials?
  Where: `docs/review/phase5/roads2/` (`gate-snow-city`, `gate-temperate-city`, `gate-metropolis-temperate`, each `-before.png` and `-after.png`); `cd viewer && nix develop -c npm run city-gallery` with `FB_CITIES=gate-snow-city,gate-temperate-city` for the rest; any other gate city's `plan.png`.
  Look for: no yellow or grey road piece lying on its own (the snow city's loose arterials are gone and its streets reach the main roads); streets with houses and workshops past the highrise belt, no highrises in the outer quarter; whether the 5-wide streets on every tile border (130 to 190 blocks apart, a rectangular frame, houses along it) read as a city's minor roads or as too regular; towns got the same frame (about twice the lots on flat ground, the hilly river town 124 → 165 lots (corrected by the verifier), organic port lanes 27 → 14 and quays 10 → 4): keep, or give towns their old organic look and a cheaper attach rule?; waterfront quays are fewer (only those a street can reach).
  Result:
    - @John 2026-10-22: Looks much better.

- [x] **Wall walk and curtain walls (p5-wallwalk)**: answers your D2 note ("a mob/player should be able to walk between the segments"). Wall lots with no road are now plain curtain walls with one crenellated wall walk on top; gatehouses stand only where a main road crosses the ring; the four corners are corner towers (bastions).
  Where: `docs/review/phase5/wallwalk/` (`plan.png`, `dm6_m24-overview.png`, `dm6_m25-overview.png`, `dm6_m24-street.png` is the view from the walk); `FB_CITIES=town-walled npm run city-gallery` regenerates them. Test: `WallWalkTest` walks the ring block by block on flat towns.
  Look for: a continuous crenellated top along the whole ring (does it read as one wall, not boxes in a row?); gates only at the roads (the town-walled plan has 6); corner towers taller than the wall; the doorways from a tower out onto the walk. How you get up: the stair inside each gatehouse tower and corner tower leads to a doorway onto the walk (one block step up, with a step block inside); the wall galleries themselves have a guard door on the town side but no stair. Known: on sloping ground neighbouring segments stand at their own levels, so the walk has drops of more than a block between them (flat ground is fine). Is a stair or ramp at every join worth doing, or are the towers enough?
  Result:
    - @John 2026-10-22: The thing that I think is needed is if we have a wall with a walkable area on top, with crenellation for defense, there has to be some way to get up there. Looking at [[dm6_m25-overview.png]], that section in the middle looks like it is inaccessible. I don’t know, but _I don’t think_ humans would build a wall like that. It’s like making a room with no doors. So, if it’s a ladder to get up the really tall size differences, or stairs that get you to the top from the outside, or a ladder from the inside, or stairs between segments, either way. It should be navigable. #important I have also noticed **another issue that I don’t want lost**, I see a lot of places where the stairs out of a building don’t reach the ground. Is that an issue for a later part of the roadmap to refine?
      - @coordinator 2026-10-22: Not waiting for later: a coder is fixing it now as item STAIRGROUND, a check that fails when a building's outside stairs end above the ground, plus the fix so every flight reaches the ground. Before and after pictures for you will go in `docs/review/phase5/stairground/`. Your wall-access point is covered by WALLSLOPE, as you said below.

- [x] **Wall walk on slopes (p5-wallslope)**: the wall now follows hills: each lot stands at its level in a ring-wide profile and the lower curtain has a stair ramp up to its higher neighbour or a tower doorway.
  Where: `docs/review/phase5/wallwalk/slope-d7_m11-overview.png` and `slope-d7_m11-along.png` (the west wall of a river town, seed 541); `FB_CITIES=town-walled-hills npm run city-gallery` (also `npm run city -- town-walled-hills`) regenerates them.
  Look for: does the stepped wall with green ramps read as a wall climbing a hill, or as blocks in a row? Ramps up to 10 blocks high between parapets that are 2 high; corner towers and gatehouses standing high on foundations on steep ground (up to ~20 blocks); a few doors walled up where a tower stands too high to reach. Is the ramp step limit (10) too generous?
  Result:
    - @John 2026-10-22: I think I see it and it looks good. I don’t think the screenshots clearly show the stairs between wall sections, but I see in slope-d7_m11-overview.png that must be stairs in the foreground (going to nowhere in the image)? If so, then this works for me, and my concerns up above in “wall walk” are addressed.

## Skyline (John's D3 feedback)

- [x] **SKYLINE (p5-skyline)**: is the metropolis still "oops all highrise", or does it read as a small tall core with a long tail of mid-rise and low buildings?
  Where: `docs/review/phase5/d3/` (`metropolis-flat-plan-height.png`, `gate-metropolis-temperate-plan-height.png`, `gate-metropolis-mixed-plan-height.png` and the `-before` of each; `skyline-transects.png` and `-before`; the per-band tables `skyline-stats-*.txt`). Regenerate: `cd viewer && npm run city -- d3-metropolis-flat gate-metropolis-temperate`, then `FB_CITIES=... CITY_GALLERY=1 npx playwright test e2e/city.spec.ts`, and `node scripts/city-stats.mjs <preset>`.
  Look for: the red and violet (20 storeys and up) now only a few per cent of the lots, in a compact patch round the hub with a few much taller towers, not a flat plateau; an orange mid-rise belt round it; low yellow and cream beyond. Whether the core is now too small (the share of 20-plus lots fell from 8 to 15 per cent to 3 to 4; the tallest are 45 and up on 0.1 to 0.2 per cent of lots); whether a packed centre (core up to 0.48 of the radius, a minority of cities) is wanted more often; whether the 3-to-4 storey shops filling the mid band read as downtown; the numbers to change are `DensityField` (core fraction 0.18 + 0.30 u^2, shoulder 0.80) and `ClassTable.lotStoreys` (power 2.5).
  Not fixed: city blocks (5-storey perimeter blocks) are still almost absent (12 and 0 in the two presets): it is the grid-street rings the block lots need, not the weights (log `docs/log/2026-10-21-p5-skyline.md`).
  Result:
    - @John 2026-10-22: This looks much better!

## ROADS3 (gate follow-ups on roads)

- [x] **ROADS3 (p5-roads3)**: do roads now meet the town gates, and do the mid-density grid quarters read better with perimeter blocks?
  Where: `docs/review/phase5/roads3/`: `steep-gate-street-before.png` / `-after.png` (the north gate of a walled old town on steep noise, seed 2 cell -2,1, from the street; before, the road ran 4 blocks under the gate door; now it climbs to the passage floor) and `steep-gate-overview-after.png`; `d3-metropolis-flat-plan-before.png` / `-after.png` and `d3-city-walled-plan-before.png` / `-after.png` (red = city blocks); `gate-metropolis-temperate-plan-before.png` / `-after.png` (squares and arterials back: 23 → 29 landmarks, 137 → 164 roads). Regenerate: `cd viewer && npm run city -- roads3-steep-gate d3-metropolis-flat` then `CITY_GALLERY=1 FB_CITIES=roads3-steep-gate,d3-metropolis-flat npx playwright test e2e/city.spec.ts`.
  Look for: the road ramping up (or down) to a gate on a hillside: is a 1-in-3 ramp with up to ~15 blocks of fill or cut in front of the gate acceptable, or should the gatehouse sit lower instead? On flat ground the 50-70 density belt is now about 40 % 3-5 storey perimeter blocks (d3-metropolis-flat 12 → 64 blocks, walled city 2 → 22): too many, or the mid-size tail you wanted? On the real gate windows blocks stay rare (4 in all 9 cities): of 846 candidate street blocks only 60 are dry and gentle enough for a block at all (water and hills), so that is the terrain, not the street rings.
  Result:
    - @John 2026-10-22: Yes, this looks good.

## Furnishings (7.5)

- [x] **JUNCTION streets meet at one height (p6-junction)**: every street that meets or crosses another now does so at one level (flat over the shared ground); the minor street ramps to it at 1 in 3, and a lane that cannot is not drawn.
  Where: `docs/review/phase6/junction/` (`temperate-a`, `temperate-corner`, `badlands`, each `-before.png` and `-after.png`; the table in its README says where); in game: seed 987654321, `/tp 990 100 -9394` or `/tp 6905 125 -6695`, spectator.
  Look for: do the junctions read as built (a flat crossing, streets ramping to it); are the flat pads at every crossing of the street lattice acceptable; on steep ground the junction is cut deep into the slope (badlands): acceptable, or would you rather such streets end with steps; a few lanes and stretches of promenade near steep junctions are gone (the drop rule): do you miss any?
  Result:
  - @assistant 2026-10-26: ticked by John 2026-10-26 with no comment; archived as accepted.

- [x] **STAIRS (your high-rise stair jump)**: interior stairs now reach the next floor with no one-block jump.
  Where: `docs/review/phase6/stairs/` (`highrise-before.png` / `highrise-after.png`, `house-before.png` / `house-after.png`: the same flight from above, roof and upper storeys hidden; the after flights have one more step at floor level). In game: any stair core of a high-rise or house, walk up from floor N and see whether you arrive on floor N+1 without jumping.
  Look for: the last step must be level with the floor above (you step off it sideways onto the landing, not straight ahead); the stair should still read as a stair, not a block; no head bump on the new last step.
  Result:
  - @John 2026-10-26: looks good
    - @assistant 2026-10-26: Filed: STAIRS (ROADMAP 6.11) accepted, no follow-up.

## Styles and standards (Phase 7)

- [x] **Tall styles (architect, 2026-10-25)**: the architect's review of the mass-timber and savanna briefs, DS-1 and DS-29, and ROADMAP 7.7 (verdict: needs John; 0 violations, 4 should-fix, all applied to the briefs). Full review: [design-tall-styles-architect.md](review/reports/design-tall-styles-architect.md).
      - @John 2026-10-26: this path is inaccessible. Don't we merge most reports into the SB branch?
        - @assistant 2026-10-26: No: `.claude/reports/` is untracked agent scratch, never committed, so neither git nor the SilverBullet branch has it. Proposal (done now): when REVIEW.md or STANDARDS.md cites a report, a frozen copy is committed under [review/reports/](review/reports/README.md) and linked with a relative link. The reports this file and STANDARDS.md cited are copied there and their links fixed (this one: [design-tall-styles-architect.md](review/reports/design-tall-styles-architect.md)).
  Where: [styles/mass-timber.md](design/styles/mass-timber.md) (biome-to-wood table, frame one tone from the wall), [styles/savanna.md](design/styles/savanna.md) (compounds marked later, Sahel pilasters and beam ends), [STANDARDS.md](design/STANDARDS.md) DS-1 and DS-29, ROADMAP 7.7.
  Questions for you:
  1. Tall buildings outside forests: a per-style `tall` variant that inherits a shared plain default (recommended: keeps each biome's tint, as DS-1 already says; a style that defines none gets the shared look), or one shared urban style for every lot over 6 storeys (one palette to tune, but every downtown looks alike)?
  2. Later, over about 20 storeys in a metropolis core: one shared glass-dominant tower look for every biome, tinted only at the base?
  3. Cherry grove mass timber: birch walls with cherry trim (recommended), or pink cherry towers?
  Proposed amendments to accepted rules (not applied; yours to accept):
  - DS-1: name the roles it governs (`wall`, `beam`/frame, `roof` cladding; `trim`, `door`, `floor`, `fence`/`rail` and interiors exempt), say that mass-timber under 7 storeys needs no exception, and that storeys are counted above ground.
  - DS-29: give "clearly larger" a number (P3 below) so 7.8 can check it.
  Proposed new rules (one line each):
  - P1 Tall buildings keep their style's tint: over 6 storeys a building uses its style's `tall` variant (mass-timber in forested styles, within DS-1); the shared default only where a style defines none.
  - P2 One wall material per building face: the wall role is one block or a declared pattern, picked per building, not hashed per block; slight variation only within one family and tone.
  - P3 DS-29 measure: in a city-check run, lots of 1-3 storeys with a wood wall are at least 50 % in forested biomes and at most 20 % in open ones (agent's numbers).
  Proposed block notes change: in [blocks-notes.md](design/blocks-notes.md), jungle planks High-rise `N` to `timber` (they are as tame as oak), then rerun `scripts/design-blocks.sh`; gives jungles a plank option besides bamboo.
  Result:
  - @John 2026-10-26: approved all suggestions. For P2 if you're saying building faces can all be different, I don't think so. Ask faces the same unless it makes sense from an architecture POV to have different materials on different faces. Roof an obvious exception, those are often different.
    - @assistant 2026-10-26: Filed. Q1 per-style `tall` variant over a shared default, Q2 shared glass tower over 20 storeys, Q3 birch walls with cherry trim: new rule DS-41. DS-1 and DS-29 amended as proposed (Amended 2026-10-26). P2 as DS-42 in your wording (one wall material on every face unless an architectural reason calls for different ones; the roof is the exception), P3 as DS-43. Jungle planks High-rise is now `timber` in blocks-notes.md (block reference regenerated). ROADMAP 7.7 notes the answers.

- [x] **High-rise plan questions (2026-10-25)**: two open choices in the high-rise plan, each one constant, both with a default the coders follow until you answer. Sources: [plans/highrises.md](plans/highrises.md) ("Open for John"), [highrise-plan-architect.md](review/reports/highrise-plan-architect.md) (answer 2), [highrise-plan-interior.md](review/reports/highrise-plan-interior.md) (Q3, "For John").
  1. Stairwell 3 or 4 deep: does the proposed DS-8 amendment ("railings that hold") cover the open well inside an indoor stairwell? Default (no or unanswered): 3 deep, open well, drops 1-3 (4-5 under 6-high storeys). Yes: 4 deep with a guard row and a 2-high rail screen. Decided in `TowerBlock.SD`; answer before plan step B1 merges (afterwards it is a rewrite).
     - @John 2026-10-26: default (no)
  2. Deep flats: windowless private entry hall on the corridor side (default, as real deep-plan flats do), or fewer, wider units with no hall? Decided in `TowerFloors.ENTRY_STRIP` (step B2/B3a).
  The plan's other three questions (tall styles outside forests, glass towers, cherry grove mass timber) are the "Tall styles" entry above.
  Result:
  - @John 2026-10-26: I want the **architect to make the calls** here. Consider proportions, would this look (Minecraft) real? Are we making something with some plausible real life examples, etc. 
    - @assistant 2026-10-26: Filed. Your "default (no)" sits under question 1: stairwell stays 3 deep with an open well. This answer is under Result and question 2 (deep flats: private entry hall or fewer wider units) is the only other one, so it goes to the architect: architect decides (proportions, would it look real in Minecraft, plausible real-life examples). Recorded in [plans/highrises.md](plans/highrises.md) "Open for John".

- [x] **Open questions from reviews (2026-10-25 evening)**: three questions the architect's reviews left for you; none holds back the item it came from.
  1. Plain block flats: the upper floors of plain/plains city blocks and shops (flats and rooms over shops) now get floor-to-ceiling 3-tall windows at sill 0, with no balcony or rail outside. Keep them, or use 1x2/2x2 at sill +1 (head at the ceiling) until DS-28 balconies arrive? The architect finds them right on halls and high-rises, acceptable as French windows on a terrace, but in a flat or bedroom they look like a missing balcony. Look at [p7-windows-block-plain-front.png](review/reports/p7-windows-block-plain-front.png) (seed 0, top left). Source: [p7-windows-architect.md](review/reports/p7-windows-architect.md) ("For John"); proposed rule DS-39 in [STANDARDS.md](design/STANDARDS.md).
     - @John 2026-10-26: approved 
       - @assistant 2026-10-26: Filed as approving the architect's suggestion: flats and rooms over shops (upper floors of plain/plains blocks and shops) get 1x2 or 2x2 windows at sill +1, head at the ceiling, until DS-28 balconies or a rail stand outside. Written into DS-39, which you also approved (now accepted). The code change is a follow-up under ROADMAP 7.12.
  2. Woodland inside cities: may city and metropolis districts clear trees from block interiors and unbuilt plots, so the core reads as town and not forest (extends DS-20's "err towards clearing")? Recommended: yes for city and metropolis, keep woods in villages and hamlets, keep shade trees at the edge. A new item extending FOLIAGE. Look at the fly-through frames 1-city-2-overview and 12345-city-2-overview. Source: [p6-cityq-architect.md](review/reports/p6-cityq-architect.md) ("For John").
     - @John 2026-10-25: no I didn't think so! If you clear the foliage it just looks empty. Foliage looks like deliberate tree cover. At a later time we may want to make more manucured parks but for now the leftover plants kind of look like parks. Unless you mean floating tree pieces. We could “thin” foliage if it's easy. Say a random chance to remove any given tree, but then you have to find trees. Let's not thin unless it's easy.
       - @assistant 2026-10-26: Filed: no clearing of woodland in cities; thinning only if cheap, as a Polish backlog line.
  3. Slope in the region screen: should settlement siting prefer flat ground, so big settlements (downtowns) avoid mountains? It changes which settlements exist in every existing world seed (all join goldens move), so it would be a scheduled item. Recommended: yes. Look at 12345-metropolis-2-overview (a lone house on the peak, towers at its foot). Source: [p6-cityq-architect.md](review/reports/p6-cityq-architect.md) ("For John").
  Result:
     - @John 2026-10-25: yes
       - @assistant 2026-10-26: Filed as ROADMAP 7.16 (region-screen slope term), a scheduled item since it reshuffles settlements and moves the join goldens.

## S10 roads between settlements (spike)

- [x] **S10 spike pictures**: do the roads between settlements look like roads you want? The spike passes only if you like them (plan [intercity-roads.md](plans/intercity-roads.md), "The spike (S10)").
  Where: region maps `docs/research/spikes/s10/roads-12345.png`, `roads-987654321.png`, `roads-12345-zoom.png` (findings: [s10-intercity-roads.md](research/spikes/s10-intercity-roads.md)); in-game shots `docs/research/spikes/s10/s10-road-0.png`, `s10-road-1.png`, `s10-road-2.png` (findings: [s10-mod.md](research/spikes/s10-mod.md)).
  Look for: how sparse the network is (each settlement links to its natural neighbours; about 91 % of settlements on land are linked, islands are not); a few hooks and U-turns; many short bridges, because the recorded worlds are full of ponds; road ends stopping 64 blocks short of each town (the town's own exit roads will meet them in 6.10). In game: a causeway across a lake (road-0, road-1), and a trench cut into a hill with sheer 5 to 15 block walls and whole-block steps in the deck (road-2). Is that trench an acceptable start, with terraced cuttings later?
  Result:
  - @coordinator 2026-10-22 (verifier note): narrow shops (7 wide) whose floor is 5 blocks above the street now get a plain flight instead of a landing in about half the cases. Before, the landing only fitted because the flight was one step short and floated. Is a plain flight acceptable there, or should those shops sit lower?
  - @John 2026-10-22: The network looks great, that’s on point. I feel like road-0 and 1 causeway don’t look right. road-0 looks more like someone built a road straight down the middle of a river. I would say lets not get into big bridges or causeways yet. I do want to do that at some point but it feels hard to get right. So we should either route around water or not have the roads connect if a body of water is blocking. A medieval solution could be a settlement at the water for a “port town” but I don’t know if the timing during planning works for that.
    - @researcher 2026-10-22: Drafted as [ADR 0004](adr/0004-roads-between-settlements.md) (proposed; yours to approve). The network stays the same. Roads must be dry: no causeways, no fill in water. A road may cross a stream on a single flat span of at most 12 blocks, with no piers. A road running down a river (road-0) is ruled out. If water blocks a link, the router tries to go round; if that fails, there is no road. A probe on the world dumps puts the linked share at about 75 to 80 % (was 91 %). Pictures: [dry-12345.png](research/spikes/s10/dry-12345.png), [dry-987654321.png](research/spikes/s10/dry-987654321.png). The 90 % bar is replaced by a guarantee: a settlement that can reach any neighbour over land is linked. Cuts become terraces instead of sheer walls. On port towns: the timing works if the region field picks them from water near the site. Town and city sites are already pulled toward water, and a "port" tag costs nothing extra. A port can't be created because a road was blocked, since that would be a cycle in the planning. Questions for you are at the end of the ADR's "Consequences".
      - @coordinator 2026-10-23: Your answers are recorded in [ADR 0004](adr/0004-roads-between-settlements.md), "Answers (John, 2026-10-23)", quoting your comment and the chat. 1: single-span stream crossings up to 20 blocks (your 20 from chat replaces the 24); bigger bridges are a new ROADMAP item, 7.3 Bridges (abutments, piers, arches or suspension, decks not level end to end, no long slabs, a world setting to allow or disallow them). 2: the 90 % linked bar is dropped. 3: touching settlements share a meeting point. 4: waits for the terrace screenshot from 6.10's mod half. World generation profiles (medieval versus modern) are under "Open questions and risks" in the ROADMAP. Still open: 5 (port tags now or later) and 6 (exact heights for roads even if the region layer falls back to the coarse screen).
      - @coordinator 2026-10-23: [ADR 0004](adr/0004-roads-between-settlements.md) is accepted (approved by you, 2026-10-23); your final comment and chat answers are in its "Answers" list. What changed: 5: a new rule N6, the region field's water search tags settlements near large water as `port`, with no village bias and no tier nudge; port features (docks, piers, harbour buildings) are a new ROADMAP item, 7.4 Port towns. 6: roads keep exact heights at their stations even if the region layer falls back to the coarse screen (HT). 4: the terrace rule stands; you still get the terrace screenshot from 6.10's mod half, and it does not hold up the ADR. 1 to 3 as recorded before (20-block single spans, 90 % bar dropped, meeting points). Under 6.10, the ROADMAP notes the acceptance and that the planner half now includes the port tag.

## Phase 5 settlement items (moved 2026-10-26, John ticked them)

- [x] **D1 hamlet and village (p5-d1-villages)**: do the small settlements read as a hamlet and a village?
  Where: `cd viewer && nix develop -c npm run city-gallery` with `FB_CITIES=hamlet-river,village-river,hamlet-mixed,village-mixed`, then `viewer/out/city/<name>/plan.png` and the `*-overview.png` / `*-street.png` frames.
  Look for: a hamlet is a short track with a handful of houses and a workshop each side, no square; a village has a straight main street, a cross street on about half, a green at the hub with a temple or hall facing it from the side, lanes, and the houses lining the streets. Bad: a ruler-straight street feels too artificial, the green sits oddly, a village that is all one street.
  Result:
  - @John 2026-10-21: notes:
    - #important do we have anything planned to connect cities with roads of some kind or another? This might be a missed detail. Not absolutely required, but a metropolis with cities nearby that aren’t connected by a road kind of makes no sense. I don’t know what we could do in the planning to connect them though, this is a question for a coder or a spike to consider. **Unless I missed it and this was already accounted for**.
      - @lead-coder 2026-10-21: You didn't miss it: it isn't planned. Phase 5 stopped the exit roads at the town edge on purpose. I propose a road graph at region scale. Each settlement links to its natural neighbours (a "relative neighbourhood graph" over settlement centres: both ends compute the same links, and no settlement on a landmass is left out). Each link is routed once with the same terrain-aware A* the main roads use, on the coarse 64-block lattice, and the towns' exits are aimed at those roads. In the game the roads would be a per-chunk feature, not structure pieces, because a road is far longer than one structure start can reach. Next step: a spike (S10) to check that it stays deterministic, is cheap per chunk and looks right on the recorded world dumps; then an item in Phase 6 (or 7). Details, alternatives and costs: [intercity-roads.md](plans/intercity-roads.md).
        - @John 2026-10-22: @coordinator I see the intercity-roads doc and mention of S10, is this actually on the roadmap (the spike I mean)? If not, I approve it going on the roadmap and starting any time. Where the intercity-roads.md suggests slotting it in looks good.
          - @coordinator 2026-10-22: It wasn't on the ROADMAP yet; it is now, where the plan suggests. Phase 6 in [ROADMAP.md](ROADMAP.md) has "S10 Spike: roads between settlements" (noting your approval) and item "6.10 Roads between settlements", linking [intercity-roads.md](plans/intercity-roads.md). Two spike coders started today, one per half (planner on the world dumps, mod feature in a noise world); results come back as a findings note with a region PNG and a client screenshot for you.
    - I think hamlets need more houses than this. Not much, I get that they want to be small. The one in the review is ok as one random example, so long as the range allows for larger ones.
    - The example has a ruler straight road.
  - @assistant 2026-10-26: Filed. Roads between settlements: S10 and ROADMAP 6.10 (ADR 0004). More houses per hamlet and roads that are not ruler-straight: rule [DS-21](design/STANDARDS.md); no item built it yet (hamlets are still 3 to 8 buildings on one straight street, `StraightProgram`), so it is now a Polish backlog line "5.1 D1 (DS-21)".

- [x] **5.12 SHOPLAND shopfront landings** (John's note on 5.12): does a lifted shop now read right, a landing at door level across the front instead of the window at the top of a flight?
  Where: `docs/review/phase5/shopland/` (`shop-slope-front-left.png` shops on a 1 in 9 slope: a terrace across the front, flight down at one end; `shop-flat-front-left.png` and `shop-flat-front.png` seeds 5 and 14 are market halls with a deck on posts, the open floor under still open to the street; `shop-slope-front.png` the same from the front)
  Look for: enclosed shops: a solid terrace with a tan (stair-coloured) top and no railing yet; market halls: a 2-deep deck on posts (balcony) with the flight coming down on the lot front row at one end;  windows now stand level with the landing.
  - @assistant 2026-10-26: No comment from John here; the later question about narrow shops getting a plain flight is answered under STAIRGROUND (rule DS-45, ROADMAP 7.17 SHOPLOW).

- [x] **5.1 D0 City gallery**: do the nine gate cities read as the right size and shape of settlement (hub, main roads, grid core, outskirts), and do the district street views look like streets someone built?
  Where: `cd viewer && nix develop -c npm run city-gallery` (about 2 minutes), PNGs in `viewer/out/city/<preset>/` (`plan.png`, `<district>-overview.png`, `<district>-street.png`); presets in `viewer/city-presets/`.
  Look for: main roads that stop short of the hub square (known: the hub-square work should close them), houses on lanes that lead nowhere, terraces where roads cross slopes, highrise canyons in the metropolis cores.
  Result:
  - @John 2026-10-21:
    - It looks like some of the houses lost bedrooms.
    - We should try to lean towards more square-leaning rooms. E.g. it looks like seed 9 gained a super long skinny “living room” and dropped to one small bedroom.
    - Lean towards having multiple bedrooms before ever having multiple other types of rooms. As usual, not 100% of the time.
    - @coordinator 2026-10-21: Agreed. Follow-up item CIRC2 is being coded now: a room aspect limit (nearer square), a bedroom-first room program (more bedrooms before a second room of another kind, not always), and bedrooms per house measured before and after. Results go in the Phase 5 gate pack; noted under ROADMAP 5.11.
  - @assistant 2026-10-26: Filed: CIRC2 (room aspect limit, bedroom-first program) under ROADMAP 5.11; leftovers in the Polish backlog line "5.11 CIRC".

- [x] **D2 town walls**: a walled town's plan and gatehouses.
  Where: `viewer/out/city/town-walled/plan.png` and `dm6_m24-overview.png`, `dm6_m25-overview.png` (`FB_CITIES=town-walled npm run city-gallery`).
  Look for: does a rectangular ring with crenellated gatehouses and corner towers read as a town wall; roads meeting the gate lots straight; towers too tall or too short next to the wall; the market and hall inside.
  Result:
    - @John 2026-10-21: the town-walled looks great. However, when walls connect, a mob/player should probably be able to walk between the segments. That might mean stairs or something. 
  - @assistant 2026-10-26: Done by p5-wallwalk (curtain walls and one continuous wall walk joining every ring piece) and p5-wallslope (ramps at the joins on slopes); the gaps left where a plaza drops a ring piece are noted under ROADMAP Phase 5 ("Wall walk").

- [x] **Waterfront quays and bridges (p5-tr-terrain)**: do the promenades along lakes, rivers and the coast read as a waterfront (stone, shops facing the water), or are they too thin, too many, or running past nothing? Do the plank bridges (role `deck`) look right? Is the steep badlands town (arterials dropped on hills, a main road cut into the slope, houses on stilts) acceptable? Pictures: `docs/review/phase5/terrain/` (README there); live: `npm run city -- tr-coast tr-river tr-steep` then `npm run city-gallery`.
  - @John 2026-10-21: IMO, any building needs *some* sort of path leading to its door. This is especially true for shops, but even houses should have it. I’m seeing from tr-coast-dm47_m22-overview.png that the actual waterfront looks amazing, but behind the waterfront it looks like shops with pillars, etc, but no path at all getting to them. Actually, I can’t tell, maybe the road to the back is the access point. But there should be a door there at least. So maybe there’s nothing to do.
  - The tr-river town looks great. 
  - @assistant 2026-10-26: Filed as rule [DS-6](design/STANDARDS.md) (a path to every door; `entrance-reachable` checks door to ground inside the lot only). Street-to-door is not checked anywhere, so it is now a Polish backlog line "5.3 TR (DS-6)", naming the tr-coast back-row shops.

- [x] **Downtown skyline and avenues (p5-d3-city)**: does the metropolis downtown read as a high-rise centre, and do the diagonal avenues and the walled old town look like a real city?
  Where: `docs/review/phase5/d3/` (plan with storeys colours, skyline transects, 3D frames; presets `d3-metropolis-flat`, `d3-city-walled`, `d3-city-avenues`, `gate-metropolis-temperate`, `gate-metropolis-mixed`; `cd viewer && npm run city -- d3-metropolis-flat && npm run city-gallery` regenerates them).
  Look for: towers rising towards the centre then mid-rise (10 to 19 storeys) then blocks then houses (it plateaus at 60 storeys over about 0.3 of the radius, is that too flat, too many?); diagonal avenues meeting at the hub square and bending to cross wall gates; the old town inside the wall with only 3 to 5 storey buildings; squares (with a hall) at some arterial crossings; empty park tiles in the downtown (open ground only, no trees or paths yet); the outer arterial ring.
  Result:
  - @John 2026-10-21: Notes:
    * Put on the roadmap a research topic to look into how we could integrate with some other mods. The one I’m thinking of is Create. One issue might be version compatibility, but here is the thing: These highrises are going to be basically non-navigable on foot. We can put in ladders / stairwells, sure. But they need elevators! And _with Create mod_ we could build real functioning elevator shafts. It would be amazing but that is a whole level of complexity. Maybe it’s simple for this mod though - leave a space to put an elevator shaft for now.
    * It’s looking good. metropolis-flat-plan-height.png clearly shows a much taller downtown. Even has empty sections for parks!
    * Is the skyline-transects.png showing a kind of height map toward the center? If so, I suppose I’d say the *center* should probably tend to be smaller as a proportion. Like, a smaller area of very tall buildings in the dense core with a longer tail of medium to small buildings. On the other hand, some city centers really are packed with tall buildings. Overall though, I’d like to see more “long tail” of the shorter buildings. It comes off as “oops all highrise”. 
  - @assistant 2026-10-26: Filed: Create and elevator shafts as ROADMAP 7.2 (research) and the 3x3 lift shaft in 7.13; the long tail of shorter buildings by p5-skyline (archived item "SKYLINE", John: "This looks much better!").
