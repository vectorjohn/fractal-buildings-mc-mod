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
- [ ] **4.3 roofs**: do the roofs read as built? Gable, hip, cross gables on L/T/U/courtyard, dormers, overhang, flat parapets on the setback tower.
  Where: `npm run gallery` medieval/plain presets; or `-Dfb.dumpRoofForms=viewer/gallery-data/gen` on `RoofPassTest.dumpForms`, then `gallery.html?dir=roof-<form>` (forms gable, hip, pyramid, shed, mansard, flat)
  Look for: valleys where wings meet the main roof, gable ends closed, dormers that look attached, overhang depth, whether shed/mansard are worth keeping or too blocky

- [ ] **4.2 Massing** [M]: do house wings, jetties and setbacks read as a person built it?
  Where: `npm run gallery` then `viewer/out/gallery/medieval-flat-front-left.png` (seeds 0..15); more with `gallery.html?style=medieval&terrain=flat&from=16`. Jetties: seeds 16, 27. Setbacks: seeds 2, 32. Courtyard: seed 15.
  Look for: wings clearly different in width and height from the main block; jetty overhang of 1 block that looks like an old overhanging upper floor, not a bug; roofs over wings and jetties are still plain (roof step 4.3).
  Result:

### Temple (4.6)

- [ ] **Temples read as temples, not houses or towers**
  Where: `FB_PRESETS=temple-medieval,temple-plain,temple-flat npm run gallery` (in `viewer/`) then `viewer/out/gallery/temple-*-front.png`, `-front-left.png`, `-cutaway.png`. Bell-tower variants: medieval seeds 0, 1, 2, 3, 12, 13, 15.
  Look for: a clear axis (porch or tower, tall nave, narrower apse), the door in the middle of the front, generous nave height; whether the tower variants look like a bell tower or like a tall house (windows repeat per storey today); side chapels reading as transepts.
- 4.6 tower: does it read as a pre-modern tower (watchtower, keep) or as a thin high-rise? Look at `tower-medieval` front-left and cutaway (npm run gallery, FB_PRESETS=tower-medieval); window bands are still the shared ones, no slit windows or spire.
- [ ] **4.6 Wall and gate** [T-wall]: does the gatehouse read as a town wall segment?
  Where: `npm run gallery` (`FB_PRESETS=wall-medieval,wall-plain,wall-flat`) then `viewer/out/gallery/wall-medieval-front-left.png`, `wall-flat-cutaway.png` (passage and galleries).
  Look for: gate arches and flanking towers legible from the field side; curtain walls too plain or too tall; battlements read as merlons; the wall is a flat 5-high strip beside 2-3 storey towers.
- [ ] **4.6 Workshop** [T-workshop]: does the tall open hall with the 2-wide front door, the lower living wing and the lean-to read as a smithy/workshop, not a tall cottage?
  Where: `FB_PRESETS=workshop-medieval,workshop-flat npm run gallery` then `viewer/out/gallery/workshop-medieval-front-left.png` and `-cutaway.png`.
  Look for: hall clearly taller than the wing, door wide enough to read as a cart door, work hooks (blue) inside the hall.
## 4.7 plains style (house)
  Where: `npm run gallery` with `FB_PRESETS=house-plains,house-medieval`, `viewer/out/gallery/house-plains-front-left.png` (seeds 0..15).
  Look for: does it read as a rural cottage/farmhouse, distinct from medieval (no jetties, no timber)? Is the wall mix (birch/oak/plaster) too busy? Are the brick/terracotta roofs right?
- [ ] **4.6 high-rise**: do the setback towers read as skyscrapers?
  Where: `FB_PRESETS=highrise-plain,highrise-medieval,highrise-flat npm run gallery`, `viewer/out/gallery/highrise-*-front-left.png`, seeds 0..15 (cutaway on `highrise-medieval`).
  Look for: podium visible at the street, 2-4 steps of setback up the shaft, a different crown; storeys of 4 blocks make a 60-storey tower 250 blocks tall (is that the right feel in game?). Window bands repeat evenly until the facade step (4.4) lands.
  Result:
