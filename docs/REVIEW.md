# Things for John to review

Open items only. Resolved items, with their answers, are in
[REVIEW-ARCHIVE.md](REVIEW-ARCHIVE.md).

Judgments only a human can make: does a building read as built by a person,
does a style feel right, how a city looks in motion in-game, and bugs first
seen in real play. Automated tests and agent-reviewed galleries cover the rest.
Agents add an entry whenever a coder or verifier says "needs a human look";
John works through them whenever convenient, or at a gate.

**How to use it.** Pick any open item, look, and write what you saw on its
`Result:` line ("fine", "roofs too steep", "still happens"). Tick it `[x]` when
settled, or just tell the coordinator and it records them. Anything that needs
work becomes a ROADMAP item or a Polish backlog line. Design taste questions
John delegates go to the architect, who makes the call (proportions, would it
look real in Minecraft, plausible real-life examples).

**Cleanup** (John, 2026-10-25). When an item is resolved, an agent files the
answers (STANDARDS, ROADMAP, plans), then moves the item with its full text
and answers to the end of [REVIEW-ARCHIVE.md](REVIEW-ARCHIVE.md) (newest
last), so this file holds only items still to review. Reports an item cites
are committed under [review/reports/](review/reports/README.md), not linked
in `.claude/reports/`.

**Entry format** (append at the end of its group; this file is union-merged):

```
- [ ] **<item id>**: <what to look at, short>
  Where: <gallery path, screenshot, viewer URL/seed, or in-game steps>
  Look for: <what good and bad look like>
  Result:
```

## Open

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

## 4.10 Phase 4 gate

- [x] **5.12 SHOPLAND shopfront landings** (John's note on 5.12): does a lifted shop now read right, a landing at door level across the front instead of the window at the top of a flight?
  Where: `docs/review/phase5/shopland/` (`shop-slope-front-left.png` shops on a 1 in 9 slope: a terrace across the front, flight down at one end; `shop-flat-front-left.png` and `shop-flat-front.png` seeds 5 and 14 are market halls with a deck on posts, the open floor under still open to the street; `shop-slope-front.png` the same from the front)
  Look for: enclosed shops: a solid terrace with a tan (stair-coloured) top and no railing yet; market halls: a 2-deep deck on posts (balcony) with the flight coming down on the lot front row at one end;  windows now stand level with the landing.

- [ ] **5.2 DL District layout**: do the streets, blocks and lots read like a city someone planned?
  Where: plan view `viewer/out/plan/districts-synth-city.png`, `districts-mixed-city.png`, `districts-river-town.png` (run `npx playwright test e2e/districts.spec.ts`, fixtures in `viewer/fixtures/settlement/districts/`); 3D `viewer/out/district/dl-d0_0-overview.png`, `dl-d1_0-street.png` (README there for the scene commands)
  Look for: grid streets straight through whole rows of tiles and plain streets on dense borders; organic lanes branching off the roads and crossing tile borders at ports; whole-block buildings only inside grid blocks; houses and shops with their fronts to the street; no lots over water; the hub square with its hall and temple; dead ends and thin empty ground between lots; the straight seam where lots on both sides of a tile border back onto each other
  Result:

- [x] **5.1 D0 City gallery**: do the nine gate cities read as the right size and shape of settlement (hub, main roads, grid core, outskirts), and do the district street views look like streets someone built?
  Where: `cd viewer && nix develop -c npm run city-gallery` (about 2 minutes), PNGs in `viewer/out/city/<preset>/` (`plan.png`, `<district>-overview.png`, `<district>-street.png`); presets in `viewer/city-presets/`.
  Look for: main roads that stop short of the hub square (known: the hub-square work should close them), houses on lanes that lead nowhere, terraces where roads cross slopes, highrise canyons in the metropolis cores.
  Result:
  - @John 2026-10-21:
    - It looks like some of the houses lost bedrooms.
    - We should try to lean towards more square-leaning rooms. E.g. it looks like seed 9 gained a super long skinny “living room” and dropped to one small bedroom.
    - Lean towards having multiple bedrooms before ever having multiple other types of rooms. As usual, not 100% of the time.
    - @coordinator 2026-10-21: Agreed. Follow-up item CIRC2 is being coded now: a room aspect limit (nearer square), a bedroom-first room program (more bedrooms before a second room of another kind, not always), and bedrooms per house measured before and after. Results go in the Phase 5 gate pack; noted under ROADMAP 5.11.

- [x] **D2 town walls**: a walled town's plan and gatehouses.
  Where: `viewer/out/city/town-walled/plan.png` and `dm6_m24-overview.png`, `dm6_m25-overview.png` (`FB_CITIES=town-walled npm run city-gallery`).
  Look for: does a rectangular ring with crenellated gatehouses and corner towers read as a town wall; roads meeting the gate lots straight; towers too tall or too short next to the wall; the market and hall inside.
  Result:
    - @John 2026-10-21: the town-walled looks great. However, when walls connect, a mob/player should probably be able to walk between the segments. That might mean stairs or something. 

- [x] **Waterfront quays and bridges (p5-tr-terrain)**: do the promenades along lakes, rivers and the coast read as a waterfront (stone, shops facing the water), or are they too thin, too many, or running past nothing? Do the plank bridges (role `deck`) look right? Is the steep badlands town (arterials dropped on hills, a main road cut into the slope, houses on stilts) acceptable? Pictures: `docs/review/phase5/terrain/` (README there); live: `npm run city -- tr-coast tr-river tr-steep` then `npm run city-gallery`.
  - @John 2026-10-21: IMO, any building needs *some* sort of path leading to its door. This is especially true for shops, but even houses should have it. I’m seeing from tr-coast-dm47_m22-overview.png that the actual waterfront looks amazing, but behind the waterfront it looks like shops with pillars, etc, but no path at all getting to them. Actually, I can’t tell, maybe the road to the back is the access point. But there should be a door there at least. So maybe there’s nothing to do.
  - The tr-river town looks great. 

- [ ] **House kitchen and living room (p5-house-program)**: does every house now have a kitchen and a living room, in sensible places?
  Where: `docs/review/phase5/circulation/house-medieval-plans.md` section "After house program" (seeds 7, 9), numbers in `docs/log/2026-10-21-p5-house-program.md`; gallery `house-medieval`, `house-plains`, `house-desert`.
  Look for: kitchens (hearth) beside the living room or entry; houses that now have too few bedrooms (1.7 per medieval house, was 1.8); a one-room hut that is only a bedroom; a ground floor that is a single kitchen-hall under bedrooms.
  Result:
  - @John 2026-10-21: these look much better, great even. **That said** one thing I want to re-emphasize (I mentioned it but probably it got buried), we should consider partitioning rooms in ways that don’t use doors. Or have “double doors” that are actually air not actual doors. Example, in seed 7, the entryway to living room, to kitchen - the division between kitchen and living room could be open. In real life, sometimes people used to strictly separate the kitchen. Modern houses usually let the kitchen be open to living areas. Maybe that’s the rule - other than medieval, kitchen/living area divisions can and often should be open - archways, wide “air” doors, etc. 

- [x] **Downtown skyline and avenues (p5-d3-city)**: does the metropolis downtown read as a high-rise centre, and do the diagonal avenues and the walled old town look like a real city?
  Where: `docs/review/phase5/d3/` (plan with storeys colours, skyline transects, 3D frames; presets `d3-metropolis-flat`, `d3-city-walled`, `d3-city-avenues`, `gate-metropolis-temperate`, `gate-metropolis-mixed`; `cd viewer && npm run city -- d3-metropolis-flat && npm run city-gallery` regenerates them).
  Look for: towers rising towards the centre then mid-rise (10 to 19 storeys) then blocks then houses (it plateaus at 60 storeys over about 0.3 of the radius, is that too flat, too many?); diagonal avenues meeting at the hub square and bending to cross wall gates; the old town inside the wall with only 3 to 5 storey buildings; squares (with a hall) at some arterial crossings; empty park tiles in the downtown (open ground only, no trees or paths yet); the outer arterial ring.
  Result:
  - @John 2026-10-21: Notes:
    * Put on the roadmap a research topic to look into how we could integrate with some other mods. The one I’m thinking of is Create. One issue might be version compatibility, but here is the thing: These highrises are going to be basically non-navigable on foot. We can put in ladders / stairwells, sure. But they need elevators! And _with Create mod_ we could build real functioning elevator shafts. It would be amazing but that is a whole level of complexity. Maybe it’s simple for this mod though - leave a space to put an elevator shaft for now.
    * It’s looking good. metropolis-flat-plan-height.png clearly shows a much taller downtown. Even has empty sections for parks!
    * Is the skyline-transects.png showing a kind of height map toward the center? If so, I suppose I’d say the *center* should probably tend to be smaller as a proportion. Like, a smaller area of very tall buildings in the dense core with a longer tail of medium to small buildings. On the other hand, some city centers really are packed with tall buildings. Overall though, I’d like to see more “long tail” of the shorter buildings. It comes off as “oops all highrise”. 

- [ ] **IRR1 (5.13)**: do old towns and diagonal avenues read as organic, "people built this", and not as a grid with extra lanes?
  Where: `docs/review/phase5/irregular/old-town-plan.png` (synthetic city seed 1001: the hub tile and the old-town tiles round it, beside grid quarters) and `avenues-plan.png` (the D3 avenue city after, compare `docs/review/phase5/d3/city-avenues-plan.png` before); regenerate with `npm run city -- irr1-old-town d3-city-avenues`, then `FB_CITIES=irr1-old-town,d3-city-avenues CITY_GALLERY=1 npx playwright test e2e/city.spec.ts -g "plan view"`; 3D is not captured for these (plan only).
  Look for: blocks of different sizes meeting in T junctions with lanes and a small square or green now and then (good); old-town streets that stop short or run straight through everything (bad: they dead-end 14 blocks from the tile border unless an arterial is there, and tiles join mostly by ports); lots stepping along a diagonal avenue on its cut lanes rather than on the avenue itself; empty wedges beside the avenue (known: under 16 across stay empty); whether the old town should be bigger, smaller, or walled-in only.
  Result:
    - @John 2026-10-21: Yes, these look fantastic.

## STAIRGROUND (gate follow-up on steps)

- [ ] **STAIRGROUND (p5-stairground)**: answers your "I see a lot of places where the stairs out of a building don't reach the ground". The flights stopped one to two blocks above the real ground, and doors one block too high got no steps at all (an off-by-one in the step rule); a new check `steps-reach-ground` found 156 such doors in 18 scenes of the 9 gate windows and none now.
  Where: `docs/review/phase5/stairground/`: `house-slope-before.jpg` / `-after.jpg` (a house on a 1-in-6 slope: no steps before, two after), `shop-slope-before.jpg` / `-after.jpg` (a shop: one step floating above the ground before), `shop-stoop-before.jpg` / `-after.jpg` (a shopfront in the temperate gate city: the door now has a landing), `shop-desert-before.jpg` / `-after.jpg` (a flight one block short, now reaching the sand). The red cube in the "before" pictures is the invariant marker for the failing door.
  Look for: does each flight now end on the ground in front of the door (last step level with the ground, no gap under it)? Do the added steps and landings look natural, or too many on slopes? Anything still hanging in the air in the city views (tell me where), including the wall ramps between curtain sections, which this check does not cover.
  Result:
  - @John 2026-10-22: This looks much better, love it.
  - @coordinator 2026-10-22 (verifier note; re-filed here 2026-10-26 from the archived S10 item, still unanswered): narrow shops (7 wide) whose floor is 5 blocks above the street now get a plain flight instead of a landing in about half the cases. Before, the landing only fitted because the flight was one step short and floated. Is a plain flight acceptable there, or should those shops sit lower?
    - @John 2026-10-26: this may be a stupid question but WHY are shops 5 blocks above the street? Maybe it’s tricky to solve. I’d like the shops to sit lower if we can do it, they should get closer to the street or there should be a walkway along them. I can’t picture any situation where a storefront has a five meter high staircase straight up to a door. Especially not in half the cases. If we can make the storefront closer to the road, that would read more correct.

## Phase 6 plan decisions

- [ ] **Phase 6 plan: decisions for John**: work is going ahead on each default; say if you want one changed. Details and costs: [plans/phase6.md](plans/phase6.md), "Decisions for John".
  1. The game uses exact heights, as in the recorded dumps you reviewed, not ADR 0003's coarse screen. Needs a small ADR 0003 amendment at the gate.
     - @coordinator 2026-10-22: John approved the default; ADR 0003 amended ([0003-worldgen.md](adr/0003-worldgen.md), W2, P1 and D4).
  2. Villagers spawn only in hamlets and villages (one per bed, at most 10 per district). Towns and up get beds and workstations but no villagers in this release.
  3. No monster spawns inside buildings. Streets stay as dangerous as the countryside.
  4. Vanilla villages are kept everywhere except inside our settlements plus a margin.
  5. Config is fixed per world when the world is created; editing the file affects new worlds only.
  6. Updating the mod mid-world may leave a seam in a half-generated city. The README says so, and there is no old-generator support.
  7. Release: Modrinth first, `0.1.0+26.3`, alpha, MIT. **You publish**; the page name and whether the repo goes public are yours to decide.
  8. Server-only install: probably works for vanilla clients, but untested (testing it needs a dedicated server and the EULA, which is yours to accept).
  9. Terralith and Tectonic: run their jars as black boxes in compatibility tests, as the S2 and S7 spikes did (their licence forbids using their work to test AI systems; we never read their code or data). If you object, they are listed as untested.
  10. Deferred past the first release: per-wing ground levels on slopes, density-function flattening, a published settlement density function, and 6.10 unless it is ready in time.
  Result:

## Phase 6 items

- [ ] **6.9 Railings on steps and landings** (2026-10-22)
  Where: `docs/review/phase6/railings/` (`house-slope`, `shop-slope-deep`, `tower-medieval`, each `-before.png` and `-after.png`).
  Look for: do the rails read as a banister along the open side of front steps, stoops and lookout stairs; is the kerb (a block column of foundation beside the flight, with the rail on top) acceptable, or would you rather the rail stand directly on the step edge (a wider flight)? Rails drop 2 blocks below the tread before they appear (1 on a landing): is that the right threshold? No balconies or wall-walk rails yet.
  Result:

- [ ] **A5 release basics (p6-a5-release)**: the files a first release needs, for you to read before C3 uses them. `LICENSE` (MIT, "Copyright (c) 2026 John Reeves"), [CHANGELOG.md](../CHANGELOG.md), the player README draft [release/README-player.md](release/README-player.md) (describes what Phase 6 is building; C3 cuts what does not ship), and the placeholder icon `mod/src/main/resources/assets/fractalbuildings/icon.png`. Build change: `mod/build.gradle` names the jar `fractalbuildings-0.1.0+26.3.jar` and puts `LICENSE` in it.
  Result:

- [ ] **B1 first village in a real world (p6-b1-worldgen)**: a village (`sm1_0`, tile -4,4, seed 987654321, near -576,704) generated through the normal chunk pipeline, with doors seen from both sides.
  Where: `docs/review/phase6/worldgen/district-overview.png` (hill village from the south), `district-door-a.png` (door seen from inside), `district-door-b.png` (from outside); in game: new world, seed 987654321, `/locate structure fractalbuildings:village` (or `/tp -576 120 704` in spectator).
  Look for: does the door sit in its wall with the hinge on a sensible side and open to the right side; do the red roofs and the tall block read as a village; the road cuts on the slope may leave overhangs (known, terrain step B3).
  Result:

- [ ] **B5 furnished interior in a real world (p6-b5-gameplay)**: beds, chests, job sites and villagers in a hamlet or village.
  Where: `docs/review/phase6/gameplay/gameplay-interior-bed.png` (client game test); in game: new world seed 987654321, `/fb locate village`, `/tp` there, walk into a house.
  Look for: do beds, chests, crafting tables, lanterns and job-site blocks read as furnishing a room (not scattered); do the villagers stand by the beds; do the loot tables feel right (house: food and sticks, shop: emeralds, tower: arrows)?
  Result:

- [ ] **P6 B7 compat**: settlements and the vanilla-village veto under terrain mods
  Where: docs/review/phase6/compat/ (one overview per mod set, the freshly generated tile before the digest check clears it); in game, fly from a settlement to the nearest vanilla village.
  Look for: settlements readable in each set (Terralith's dense forest hides the buildings from the screenshot camera); no vanilla village within about 160 blocks of a settlement, villages present beyond. Known: compat-lithostitched_tectonic-overview.png has the camera under terrain that had not rendered yet (software rendering timeout), so the tile is not visible there; the digest and cell counts in the log are the evidence for that set.

- [ ] **B3 terrain in a real world (p6-b3-terrain)**: streets, quays, a bridge and building foundations on slopes and shores of seeds 987654321, 12345 and 1.
  Where: `docs/review/phase6/terrain/` (`987654321-slope.png` village street between houses, `12345-slope.png` street climbing a taiga hillside, `12345-shore.png` frozen shore with a tower, `1-shore.png` timber bridge with stepped approach and `1-slope.png` the deepest cut, 13 blocks); in game: new world, seed 12345, `/tp 1348 85 -34` (slope) or `/tp 1758 80 -435` (shore), spectator; seed 1, `/tp -1585 125 -1662` (the deep cut) or `/tp -1225 80 -1697` (bridge approach).
  Look for: (1) road cuts into hillsides: the cut faces (5 to 13 blocks high next to the street on seed 1, bare grey stone where the cut is deeper than the surface layer, S5's known gotcha): acceptable, or would you want retaining walls or a planner that avoids steep streets; (2) streets that end in a step where they meet another street at a different level (known issue, 2 to 20 blocks in 55 % of tiles, polish backlog 6.3); (3) the bridge approach and quay edges at the shore: do they read as built; (4) no gaps under floors or streets (the tests say none; look anyway); (5) the white patch on B1's hillside is snow from a `snowy_slopes` hill above the village, not a bug.
  Result:

- [ ] **6.10 roads between settlements, planner half (p6-610-roads)**: do the road networks read right at region scale, and do the exits and approaches of a settlement look sensible in the plan view?
  Where: `docs/review/phase6/roads/` (`roads-12345.png`, `roads-987654321.png`: whole worlds, links yellow, crossings cyan, meeting points white dots, port-tagged towns and cities white rings, dropped pairs red dashes; `roads-12345-zoom.png`; `links-city-s2_1.png`, `links-town-s3_7.png`: plan view with the approaches), compare with S10's dry pictures `docs/research/spikes/s10/dry-12345.png`. Numbers in [log/2026-10-23-p6-610-roads.md](log/2026-10-23-p6-610-roads.md). Viewer: `region.html?region=<viewer/fixtures/region/roads-12345.json.gz>`, `plan.html?city=<viewer/fixtures/settlement/roads/city-s2_1>`.
  Look for: (1) roads that hook, double back or hug a shore for no reason; (2) towns that you expect linked but are not (about 30 % of settlements on these pond-heavy dumps; the dry rule and the 20-block crossing limit are ADR 0004's, so this is only "does it read as a sparse believable network"); (3) too many port rings (34 and 31 of about 50 towns and cities: the dumps are wet; live worlds should tag fewer); (4) in the plan view, exits that face the port and approaches that look like a road leaving the town, not a stub.
  Result:

- [ ] **PALETTE Calmer building colours (p6-palette)**: answers your "walls read noisy, checkerboardy" playtest note.
  Where: `docs/review/phase6/palette/` (`house-<style>-before.png` / `-after.png` for medieval, plains, desert, jungle, snowy, taiga, and `shop-medieval`, `shop-plain`; in game: `docs/review/phase6/palette/street-after.png`, a plains street of seed 987654321 from the terrain client test; `street-bamboo-patches.png` is the intermediate run where bamboo walls showed as yellow patches; the yellow bands still visible in `street-after.png` were bamboo floor edges, since changed to birch/oak without a new game shot). Walls, roofs and foundations now use near-identical colours per style (texture variation only, e.g. sandstone + cut + smooth; stone bricks + a little cracked), with at most one rare accent (a moss block on jungle roofs). Timber framing unchanged. Log: [log/2026-10-23-p6-palette.md](log/2026-10-23-p6-palette.md).
  Look for: (1) do the walls now read as one material in game (not only in the viewer); (2) is anything now too plain or too dark (taiga roofs are all dark oak, snowy walls are all grey brick; plains walls are now birch only with a stripped-birch variant); (3) do you want the variation to be intentional patches or bands (per face, per storey) instead of per-block scatter? I left the selection per block, since calm colours make it read as texture.

## Furnishings (7.5)

- [ ] **7.5 furniture catalogue and authoring (assist-furniture, 2026-10-23)**: your "rooms look sparse" note; a design, nothing built yet.
  Where: [plans/furniture.md](plans/furniture.md) ("The catalogue": 25 pieces with sketches, "Which room gets what", "Open questions for John"); [FURNITURE-AUTHORING.md](FURNITURE-AUTHORING.md) (how you would build and hand over your own pieces); research [minecraft-furniture.md](research/aesthetics/minecraft-furniture.md).
  Look for: (1) pieces you would not build, or idioms you like better (chair: stairs with open-trapdoor arms; tables: fence + pressure plate, upside-down stairs; counters: upside-down stone stairs; sink: water cauldron with a tripwire hook tap; fireplace with a campfire and no chimney); (2) the per-style wood, stone, cloth and plant table; (3) whether the authoring route suits you: build in a creative world facing south, oak = main wood, dark oak = accent, stone bricks = stone, white/light gray = cloth colours, bedrock = wall behind, lime stained glass = keep clear, save with a structure block as `fractalbuildings:furniture/<id>`; (4) the four open questions at the end of the plan (sign vs trapdoor arms, job blocks in kitchens, chimneys, how full a room should be).
  - @John 2026-10-26: if we do this, what is the chance we could have optional mod interop, using furniture from other mods? 
    - @assistant 2026-10-26: Feasible as a later optional layer on top of 7.5, not part of it. It would take: detecting other furniture mods at runtime (Fabric Loader `isModLoaded`, no hard dependency); a per-mod mapping from our furniture roles and pieces (chair, table, lamp, shelf) to their block ids and states, used only when the mod is present; vanilla pieces as the fallback, so worlds and seeds stay valid without it. Caveats: their block ids and state properties can change between versions (each mapping needs a version check and a test), and we only reference their blocks, never copy code or assets, so licences matter mainly for that. Candidates on Fabric: Adorn (I believe MIT), Macaw's Furniture and Handcrafted (licences and Fabric support for our Minecraft version not checked). Worth a short research note before 7.5's later steps if you want it.
      - @John 2026-10-26: Great, lets put something in the backlog for a short research item on this.

- [ ] **6.10 C1 roads in game (p6-c1-roads)**: the terraced cut (ADR 0004 Q4, you asked for this screenshot), a short bridge and a road across country, in a real world (seed 987654321).
  Where: `docs/review/phase6/roads-ingame/` (`road-terrace-along.png`, `road-terrace-side.png`, `road-bridge-along.png`, `road-bridge-side.png`, `road-country-along.png`, `road-country-side.png`, README); in game: new world, seed 987654321, `/tp 3764 120 -1315` (terrace), `/tp 3420 90 -1304` (bridge), spectator mode.
  Look for: (1) terraces: 2-high steps with 2-wide grass benches and a dirt riser, first block level with the road: the look you want, or gentler (smaller steps, wider benches), or retaining walls; (2) the bridge: a plain plank span with no rails or piers, fine for 20 blocks; (3) the road beside a vanilla village: it stops at the village pieces and resumes, with a visible kink; (4) road surface mix (dirt path, gravel, coarse dirt per biome style) reads as a track.
  Result:

- [ ] **6.8 Fly-through frames (B9)**: do the settlements read as places from the air and at street level, per tier, in the game itself
  Where: [docs/review/phase6/flythrough/](review/phase6/flythrough/README.md) (14 of 90 frames; all 90 regenerate with `scripts/flythrough.sh`, about 25 minutes); seeds 987654321, 12345, 1.
  Look for: (1) the metropolis overview on 12345 (towers, plazas, streets, a river) against the one on seed 1 (two towers and some plazas in a forest: is it a city?); (2) the street frame on 987654321 city, which stands under a tree canopy (trees over a street); (3) 12345 city edge, which looks out over frozen sea (is a city in an ocean biome right?); (4) interiors (stairs, windows, a crafting table, dark ceilings); (5) hamlet and village overviews: houses along one street on a hillside.
  Result:

- [ ] **CITYQ**: small "cities" and "metropolises" become towns; city lattice reaches hilly and river-cut ground
  Where: [docs/review/phase6/cityq/](review/phase6/cityq/README.md) (before/after plan views of four fly-through sites); log [2026-10-23-p6-cityq.md](log/2026-10-23-p6-cityq.md). In game: seed 12345 around 1152,-256 (city), seed 12345 640,5312 and seed 1 -1536,4800 (now towns).
  Look for: (1) is it right that a "metropolis" of radius 276 (the compact one on 12345 you saw in B9, towers and an arterial grid) is now a town of houses and shops? The rule is the tier ladder's radius bands; the alternative is to keep dropped claimants at city at least (one line, `CellDecision.sizedAlone`); (2) the snowy coast city: more of its land is built (790 lots, was 517), but do streets climbing 1 in 2 slopes look right in game; (3) the 12345 city is coastal but not "on the sea": its east edge is open frozen sea, which is what B9's edge frame looked at.

- [ ] **FOLIAGE (floating leaves over roads)**: do roads and streets under trees read as built, with no floating leaves, trunks or vines
  Where: [docs/review/phase6/foliage/](review/phase6/foliage/README.md) (before/after for seeds 1 and 31337); in game, walk the roads of seed 1 (taiga hamlet near 320,-1728) and 31337 through forest.
  Look for: no leaf or log hanging over a road or street; shade trees at the road edge keep their crowns (it errs on clearing more, so check no crown looks sawn off); no hole in a tree at a chunk border; building logs and hedges untouched.

- [ ] **P7 A3: mass-timber high-rises (2026-10-26)**: look at `viewer/out/gallery/highrise-forest{,-oak,-pale}-*.png` (seeds 0..11; the 7-20 storey seeds are timber). Does it read as a CLT office tower, not a log cabin or Tudor frame? Corner posts and floor bands in stripped wood are one tone off the planks; windows are mostly 1 wide until the facade work (B3b). Is the band projection too deep? Cherry grove: birch tower with pink trim (`mass-timber-cherry`) or plain birch (one data edit in `variants.json`)?
  Result:

- [ ] **P7 B3b: high-rise facade bands, roof doors and roof guard (2026-10-26)**: look at `viewer/out/gallery/highrise-{plain,forest,desert}-{front,back-right,top}.png` and `highrise-cut-lobby-cutaway.png` (seeds 0..15). Windows of a tier stack straight up now, lobby and special floors have taller glass, the stair house has two doors onto a roof with a rail round it. Questions: does the one-block threshold inside each roof door read as a door sill or as an odd step; is the rail ring (iron bars on a 1-high parapet) enough, or should the roof guard be taller; do mass-timber towers with a stripped-wood column at every bay line AND a trim ring at every slab read as a timber grid or as busy (string courses are A3's choice).
  Result:
