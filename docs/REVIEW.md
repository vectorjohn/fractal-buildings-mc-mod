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

## 4.10 Phase 4 gate

- [ ] **5.2 DL District layout**: do the streets, blocks and lots read like a city someone planned?
  Where: plan view `viewer/out/plan/districts-synth-city.png`, `districts-mixed-city.png`, `districts-river-town.png` (run `npx playwright test e2e/districts.spec.ts`, fixtures in `viewer/fixtures/settlement/districts/`); 3D `viewer/out/district/dl-d0_0-overview.png`, `dl-d1_0-street.png` (README there for the scene commands)
  Look for: grid streets straight through whole rows of tiles and plain streets on dense borders; organic lanes branching off the roads and crossing tile borders at ports; whole-block buildings only inside grid blocks; houses and shops with their fronts to the street; no lots over water; the hub square with its hall and temple; dead ends and thin empty ground between lots; the straight seam where lots on both sides of a tile border back onto each other
  Result:

- [ ] **House kitchen and living room (p5-house-program)**: does every house now have a kitchen and a living room, in sensible places?
  Where: `docs/review/phase5/circulation/house-medieval-plans.md` section "After house program" (seeds 7, 9), numbers in `docs/log/2026-10-21-p5-house-program.md`; gallery `house-medieval`, `house-plains`, `house-desert`.
  Look for: kitchens (hearth) beside the living room or entry; houses that now have too few bedrooms (1.7 per medieval house, was 1.8); a one-room hut that is only a bedroom; a ground floor that is a single kitchen-hall under bedrooms.
  Result:
  - @John 2026-10-21: these look much better, great even. **That said** one thing I want to re-emphasize (I mentioned it but probably it got buried), we should consider partitioning rooms in ways that don’t use doors. Or have “double doors” that are actually air not actual doors. Example, in seed 7, the entryway to living room, to kitchen - the division between kitchen and living room could be open. In real life, sometimes people used to strictly separate the kitchen. Modern houses usually let the kitchen be open to living areas. Maybe that’s the rule - other than medieval, kitchen/living area divisions can and often should be open - archways, wide “air” doors, etc. 
    - @assistant 2026-10-26: This was done after your note: 5.11 OPENPLAN (p5-openplan) opens kitchens onto the living area through an archway or a 2 to 4 wide gap, per style (plain 85 % of kitchen links, medieval 22 %), rule DS-10 in [STANDARDS.md](design/STANDARDS.md); pictures `docs/review/phase5/circulation/house-plain-cutaway-open-plan.png` and `house-medieval-cutaway-open-plan.png`. Lintels and arches over the wide openings are a Polish backlog line (5.11 OPENPLAN).

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
      - @assistant 2026-10-26: Not a stupid question. Every building's ground floor is set at the highest ground under its footprint (the S5 rule: a flat floor on a foundation, never cut into the slope), and its front steps only go down from there. A shop lot usually rises away from the street, so the floor ends up at the level of the back corner: where the ground climbs 4 or 5 blocks from the front of the lot to the back, the door is that far above the street, and a lot that narrow has no room for the landing, so it gets a plain flight. Filed as rule [DS-45](design/STANDARDS.md) "Shops sit near street grade": the door stays within 2 blocks of the street (my number); a bigger difference is taken by cutting the floor into the slope, a split level, or a walkway or terrace along the whole front; never a tall straight flight to a shop door. A coder item is scheduled: ROADMAP 7.17 SHOPLOW.

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
  - @John 2026-10-26: these railing still look like they are built *at the level* of the stairs which is too low. Or at the level of the balcony! They should be one block above that level in order to do the job of a railing. If this is handled by another item, let me know. The *amount* of railings looks much better now.
    - @assistant 2026-10-26: You're right about these pictures. They are the original 6.9 ones, where the rail stood at the tread's own level. Another item fixed it after your playtest note of 2026-10-25: RAILFIX (merged ef6569c1, [log](log/2026-10-25-p6-railfix.md)). Every rail now stands one block above the step or landing it guards. Its kerb comes up to the walking surface and the fence sits on top, so the rail top is 1.5 above your feet and you can't step onto it. `RailingTest` checks this for every rail, including corner posts and rails that step down a flight. New pictures, same three buildings: `docs/review/phase6/railfix/*-r2.png` (latest round, after the corner-post and lot-line fixes) and `*-after2.png`; compare with `docs/review/phase6/railings/*-after.png`. These are viewer pictures; I have no in-game shot of the new rails. The "balcony" in the old pictures is a stoop landing, which is now railed the same way. No real balconies exist yet (Polish backlog "Balconies", rule DS-28). The high-rise roof (P7 B3b, DS-38) has a 1-high parapet with iron bars on top, 2 above the roof. Nothing else still puts a rail at deck level, so nothing new to file.

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

- [x] **6.8 Fly-through frames (B9)**: do the settlements read as places from the air and at street level, per tier, in the game itself
  Where: [docs/review/phase6/flythrough/](review/phase6/flythrough/README.md) (14 of 90 frames; all 90 regenerate with `scripts/flythrough.sh`, about 25 minutes); seeds 987654321, 12345, 1.
  Look for: (1) the metropolis overview on 12345 (towers, plazas, streets, a river) against the one on seed 1 (two towers and some plazas in a forest: is it a city?); (2) the street frame on 987654321 city, which stands under a tree canopy (trees over a street); (3) 12345 city edge, which looks out over frozen sea (is a city in an ocean biome right?); (4) interiors (stairs, windows, a crafting table, dark ceilings); (5) hamlet and village overviews: houses along one street on a hillside.
  Result:
  - @John 2026-10-26: notes:
    - 1-metropolis-2-overview.jpg is nothing. There is no metropolis here.
      - @assistant 2026-10-26: Agreed, and already in hand. That site (seed 1, `sm2_4` at -1536,4800) is a compact 418-block claimant that was sized as a metropolis by mistake. CITYQ (merged 0df947c6) made it a town; see [cityq/](review/phase6/cityq/README.md), `c1-metro-before.jpg` / `-after.jpg`. The frame itself was badly aimed too: rd 10 and a fixed camera south of the centre. FLY2 (in progress) picks each frame from the plan (the densest window of planned buildings, never empty lots) and adds wide sweeps at a higher render distance. These frames get replaced when it lands.
    - I’ve noticed these before but never asked, what are the big square empty pads I’ve seen in some places? One visible in 1-metropolis-2-overview.jpg
      - @assistant 2026-10-26: They are the settlement's squares, plazas and greens. The one in this frame is the hub square: 38 x 24 blocks at x -1539, z 4830, y 119 (I re-planned the site with the CLI as it was then, `city --seed 1 --site -1536,4800,metropolis,418`). The hall and temple stand beside it; they are the two big brown roofs on the right of the frame. The planner levels a plaza flat and paves it, and that is all it does today. Every kind (square, market, green, park) gets the same bare paving, with nothing on it: no well, fountain, stalls, benches, lamps or planted trees. Even a "green" is paved, not grass. So they read as empty foundations. The trees on this one seem to be the surrounding forest standing on or leaning over its edges; I didn't check which in game. It is not a lot that lost its building: squares are meant to be open. They just aren't dressed yet. Filed in the ROADMAP Polish backlog ("6.x PLAZAS").
    - 12345-city-6-edge.jpg also contains nothing in view.
      - @assistant 2026-10-26: Known. The old edge frame looked outward from the built edge, here over open frozen sea (CITYQ question 3). FLY2 drops it and aims every frame at built footprints.
    - 12345-metropolis-2-overview.jpg looks decent, but 
      - @assistant 2026-10-26: This sentence is cut off; what did you mean to add? (CITYQ also made this site, `s0_5` at 640,5312, a town, so FLY2's frames will show it as one.)
    - 12345-metropolis-2-overview.jpg looks decent, but the roads are a little wonky. There are a lot of redundant roads, and empty space. If more space was filled _between_ the roads, that would look good, but it’s too sparse.
    - 987654321-hamlet-4-interior.jpg has a staircase made out of stairs. As in, the blocks *under* the stairs are also stairs. Not sure if that was an intentional design (“wine storage”?). Looks like a mistake.
      - @coordinator 2026-10-26: a mistake, now fixed (STAIRFILL, merged da900b2b): each step was a column of stair blocks; now only the top of each column is a stair and the cells under it are the plain full block of the wall material. A test covers every type and style. Not yet seen in game (next batched heavy run). Tell us if you would rather have a stringer look (open under the flight) instead of solid blocks.
    - The rest of these look quite good.

- [ ] **CITYQ**: small "cities" and "metropolises" become towns; city lattice reaches hilly and river-cut ground
  Where: [docs/review/phase6/cityq/](review/phase6/cityq/README.md) (before/after plan views of four fly-through sites); log [2026-10-23-p6-cityq.md](log/2026-10-23-p6-cityq.md). In game: seed 12345 around 1152,-256 (city), seed 12345 640,5312 and seed 1 -1536,4800 (now towns).
  Look for: (1) is it right that a "metropolis" of radius 276 (the compact one on 12345 you saw in B9, towers and an arterial grid) is now a town of houses and shops? The rule is the tier ladder's radius bands; the alternative is to keep dropped claimants at city at least (one line, `CellDecision.sizedAlone`); (2) the snowy coast city: more of its land is built (790 lots, was 517), but do streets climbing 1 in 2 slopes look right in game; (3) the 12345 city is coastal but not "on the sea": its east edge is open frozen sea, which is what B9's edge frame looked at.

- [ ] **FOLIAGE (floating leaves over roads)**: do roads and streets under trees read as built, with no floating leaves, trunks or vines
  Where: [docs/review/phase6/foliage/](review/phase6/foliage/README.md) (before/after for seeds 1 and 31337); in game, walk the roads of seed 1 (taiga hamlet near 320,-1728) and 31337 through forest.
  Look for: no leaf or log hanging over a road or street; shade trees at the road edge keep their crowns (it errs on clearing more, so check no crown looks sawn off); no hole in a tree at a chunk border; building logs and hedges untouched.
  - @John 2026-10-26: Much improved (based on screenshots)

- [ ] **P7 A3: mass-timber high-rises (2026-10-26)**: look at `viewer/out/gallery/highrise-forest{,-oak,-pale}-*.png` (seeds 0..11; the 7-20 storey seeds are timber). Does it read as a CLT office tower, not a log cabin or Tudor frame? Corner posts and floor bands in stripped wood are one tone off the planks; windows are mostly 1 wide until the facade work (B3b). Is the band projection too deep? Cherry grove: birch tower with pink trim (`mass-timber-cherry`) or plain birch (one data edit in `variants.json`)?
  Result:

- [ ] **P7 B3b: high-rise facade bands, roof doors and roof guard (2026-10-26)**: look at `viewer/out/gallery/highrise-{plain,forest,desert}-{front,back-right,top}.png` and `highrise-cut-lobby-cutaway.png` (seeds 0..15). Windows of a tier stack straight up now, lobby and special floors have taller glass, the stair house has two doors onto a roof with a rail round it. Questions: does the one-block threshold inside each roof door read as a door sill or as an odd step; is the rail ring (iron bars on a 1-high parapet) enough, or should the roof guard be taller; do mass-timber towers with a stripped-wood column at every bay line AND a trim ring at every slab read as a timber grid or as busy (string courses are A3's choice).
  Result:

## Basements (7.18)

- [ ] **Basements (2026-10-26)**: your question "do we have a plan for basements?" Nothing existed; now filed as ROADMAP 7.18 BASEMENT (steps B0-B4, not started). Work goes ahead on the defaults below; say if you want one changed.
  Where: ROADMAP 7.18; the architect's answer [basements-architect](review/reports/basements-architect.md) (who gets one, shares, depths, access, worldgen limits) and the interior-designer's [basements-interior](review/reports/basements-interior.md) (rooms, furniture, light).
  Look for: (a) hidden cellars, or walk-outs and high-rise levels only? The architect recommends cellars (a cellar under a taiga cabin is a discovery that makes a house feel lived in; shares keep it occasional); walk-outs-only avoids the beard, sealing and water-table work for houses. (b) A single skull in a wall niche in temple crypts: yes or no? (c) Finished basements (rec room, high-rise B1 club room): now or later? The interior-designer suggests later. (d) Proposed rules for STANDARDS: basements are service space only (storage, cellar, plant, lockers, laundry, crypt, vault; the desert sardab excepted) and don't count for DS-12; basements are lit throughout (a lantern per room, two over 12 cells wide, corridor lights every 8 or less); "Basements are reachable, lit, dry and sensible" (stacked stair or hatch, no habitable room without an areaway or walk-out window, none below nearby water, high-rise stairwell and shaft continue down); storage rooms may fill to about 50 % (amends DS-13's 40 %). Parking is out of scope (transit stations later, 7.1).
  Result:
- [ ] **SHOPLOW S2 (2026-10-26): shops, houses and workshops now sit in the slope**: does a shop read as standing at the street with the hill behind it, and does the areaway (a one-wide paved well round the building, a corridor to the street when earth stands against the door, a paved forecourt with a railed edge on fill) look built rather than dug? Houses on steep ground dig in up to a 5-block cut with shorter flights that break at landings (no straight run over 3 risers).
  Where: `docs/review/phase7/shoplow-s2/` (`shop-slope-deep`, `house-slope`, `workshop-slope`, each `-before.png` and `-after.png`; `hill-town-dm9_28-after.png`); in the viewer `cd viewer && npm run gallery` (FB_PRESETS=shop-slope-deep,house-slope,workshop-slope) and `cutaway` for the well.
  Look for: (a) the cut face against the street (street side of the lot): fine, or does it need the architect's retaining wall (2-cell margins, later)? (b) forecourt causeway on fill: rails and stone facing read right? (c) market halls on posts are unchanged until S4. Known gap: no wall torches in the wells yet (no light role).

## Playtest 2026-10-26 design answers

The architect and interior designer answered the design questions from your playtest notes (2026-10-27). Reports: [playtest1026-qs-architect](review/reports/playtest1026-qs-architect.md) and [playtest1026-qs-interior](review/reports/playtest1026-qs-interior.md). Filed as ROADMAP 6.18 ROOMWALK, 7.22 INN, notes on 6.15 and 6.17, and polish lines PARTITIONS, SWITCHBACK, DORMER, DOORGLASS, GLASSSTUB, WORKSHOP and LAMPS. The proposed rules are in [STANDARDS.md](design/STANDARDS.md), "Proposed by agents". Work goes ahead on the defaults below; say if you want one changed.

- [ ] **Tunnels ever? (playtest 2026-10-26)**: the architect recommends that roads never tunnel for now (proposed DS-50). 6.17 ROADCLEAR then clears thin overburden to an open cut, and where the cut would be too deep the road goes round the hill or the link is dropped. If you want tunnels later, they become their own item, with portals, a lining, headroom of 4 and lights every 8 blocks. Never an unlined bore like [21.37.41](review/phase6/playtest-comments/2026-10-26_21.37.41.jpg).
  Where: [architect report](review/reports/playtest1026-qs-architect.md), question 2; ROADMAP 6.17.
  Result:

- [ ] **Door frame or glass blocks (playtest 2026-10-26)**: the architect chose a solid frame round every door in a glazed wall: a jamb on each side and a head in the trim or frame role, so the panes butt a solid block and connect. The glass-block option was the one you placed in [20.50.01](review/phase6/playtest-comments/2026-10-26_20.50.01.jpg). It reads thicker and brighter than the panes beside it, and the door would hang on glass. Say if you preferred that look.
  Where: [architect report](review/reports/playtest1026-qs-architect.md), question 7; ROADMAP polish lines DOORGLASS and GLASSSTUB.
  Result:

- [ ] **Inn type or bigger hamlet house (playtest 2026-10-26)**: about half of the hamlets on a road between settlements (3+ lots, away from both ends) get one inn. Should that be a real `inn` building type (taproom with bar and fire, kitchen, 3-5 guest rooms upstairs, stable yard on the road), which both reviewers recommend? The cheaper option is the largest hamlet house with a taproom front room and more bedrooms. That reads as a house, with no bar and no stable yard. Filed as ROADMAP 7.22 INN, pending this answer.
  Where: [architect report](review/reports/playtest1026-qs-architect.md), question 5 (siting); [interior report](review/reports/playtest1026-qs-interior.md), question 2 (rooms).
  Result:

- [ ] **Proposed rules from the playtest answers (2026-10-27)**: accept, edit or reject each one. The full text is in [STANDARDS.md](design/STANDARDS.md), "Proposed by agents".
  - P1, **DS-49 Partitions lighter than the shell** (architect): the core, party walls and chimneys are masonry. Other interior walls are pale plaster, or planks in wood styles. Desert, adobe and stone keep masonry inside. From your brick corridor, 19.59.37. [Report](review/reports/playtest1026-qs-architect.md) question 1.
  - P2, **DS-50 Roads don't tunnel (for now)** (architect): see "Tunnels ever?" above. [Report](review/reports/playtest1026-qs-architect.md) question 2.
  - P3, **DS-45 amendment, run and turn sizes** (architect): exterior straight runs of at least 2 risers, turns at least 2 risers apart, level landings W x W (quarter) and 2W x W (half), an L preferred to a U, headroom 3. From your switchback, 21.38.53. [Report](review/reports/playtest1026-qs-architect.md) question 3.
  - P4, **DS-51 Glass ends on something solid** (architect): see "Door frame or glass blocks" above. It also covers the lone pane in 19.45.26. [Report](review/reports/playtest1026-qs-architect.md) question 7.
  - P5, **DS-52 Roads step down one rank at a time** (architect): a link becomes the main street at the gate, and width drops by at most 2 at a join, with a taper otherwise. From your tiny path meeting a huge road, 21.32.46. [Report](review/reports/playtest1026-qs-architect.md) question 6.
  - **DS-48 Rooms are walkable** (interior designer): one connected lane joins the doors, stairs and every piece's use cell, 1 wide in houses and 2 wide in halls and shops. Beds go head to the wall. A room that is too small loses optional pieces first, then becomes a smaller use. From your unwalkable room, 20.02.15. ROADMAP 6.18 ROOMWALK builds the check. [Report](review/reports/playtest1026-qs-interior.md) question 1.
  - **DS-35 amendment, workshop doors** (interior designer): a workshop's lean-to and its living wing each get their own exterior person door. From 21.27.38. [Report](review/reports/playtest1026-qs-interior.md) question 3.
## SHOPLOW market halls (7.17 S4)

- [ ] **7.17 S4: market halls (2026-10-26)**: look at `viewer/out/gallery/shop-market-medieval-{front-left,front,cutaway}.png` and `shop-market-plain-{front,top}.png` (seeds 0..15, all are market halls). The hall stands on posts over a paved open floor 4 clear (5 clear from width 11), with 2-4 stalls (canopy, trestle or cloth rack, wares per style) and chain lanterns; a 1-wide stair runs up inside along the back edge through a hole in the first floor; no door and no shopfront; ONE hall storey (guild hall, table and fireplace groups, two lights) under the roof. Questions (in game, round 2): do the stalls read as stalls (counter and canopy are trim blocks, no wool; wares are single blocks); do the lantern chains and the stalls leave the 3-wide walk feeling open; are the knee-brace blocks at the post heads and the pads a good arcade or just clutter; does the stair at the back edge read from the square. Halls are rare (one per square at most, only where a shop lot faces the square).
  Where: `viewer/out/gallery/shop-market-*.png`; in a town with a market plaza (city-gallery) look for a tall shop facing the square.
  Result:

## ROADJOIN long crossings (6.15, 2026-10-27)

- [ ] **6.15 ROADJOIN: an approach road with no dry route and no crossing of 20 or less (2026-10-27)**: 3 of 18 approaches (playtest metropolis k2_1_0 at 3200,1696, your 21.31.49 spot, ~65 blocks over the river; playtest k2_1_1; w12345 k2_1_2) need a deck longer than DS-19's 20. Option 1: drop that approach and its link side (keeps DS-19, but the road stops short of the town as in 21.19.18). Option 2 (architect recommends, after a real search for another exit): allow a long crossing deck at town-tier-and-up gates, in the road's own material, until 7.3 Bridges. Either way a run ALONG water (not across) is never built. Proposed rule: crossings >20 wait for 7.3; runs along water never built.
  Where: [architect review](review/reports/README.md) `.claude/reports/p6-roadjoin-s1-architect.md`; top view `/tmp/claude-1500/-home-agent-fractal-buildings-mc-mod--claude-worktrees-run-phase6/15ae8f6c-c641-422d-898b-11dc647485a4/scratchpad/rj/approach-3201-1683.png`.
  Result:
- [ ] **7.20 METROFILL R1: quays end at the settlement's own waterfront tiles (2026-10-27)**: acceptable at settlement edges? (from the R1 merge commit 4516ce4b)
  Result:

## Steps, roads and rooms (2026-10-27)

- [ ] **6.12 STEPTOP: stairs at the door (2026-10-27)**: a step beside a door now turns to face the door, and a landing cell with a lower stair beside it stays a stair (merged f5d24090). Does a stair turned to face the door, and a stair on the landing edge, look right?
  Where: in game, world seed 1709797029209580947, walk up to the door at 1266,79,2411 (desert high-rise, the playtest spot) and at 2725,68,2689; verifier's report `.claude/reports/p6-steptop-verify.md`.
  Look for: a flight you walk straight up into the door without a jump; on a 2-wide landing, one stair cell beside full blocks (reads as a step, or as a mistake?).
  Result:
- [ ] **7.10 ROADS1: city arterial surface (2026-10-27)**: which block reads as a city arterial next to the light grey concrete streets? The architect recommends `gray_concrete` (it reads as tarmac; `cobbled_deepslate`, today's choice, reads as cobbled setts; `polished_deepslate` as square tiles).
  Where: `.claude/reports/p7-roads1-roadlook-evidence/66dd2efc0b-20261027T103023Z/mod/build/flythrough/987654321-asphalt-5-asphalt-joint.jpg` (cobbled deepslate left, polished deepslate middle, gray concrete right, each meeting light grey concrete); also `987654321-asphalt-1-asphalt-overview.jpg` there; architect's re-review `.claude/reports/p7-roads1-architect.md`, suggestion 2.
  Look for: which of the three you'd call a road a city paves; does it sit well against the light grey streets? Doesn't block the item.
  Result:
- [ ] **6.18 ROOMWALK: a bed into the middle of a small bedroom (2026-10-27)**: in a 3x3 bedroom the bed lies with its head on one wall and its foot in the room's centre cell. It is walkable, but is it acceptable, or should the room be re-planned (bigger bedroom, or the bed along a wall)?
  Where: `fractal-cli floorplan --seed 25 --lot 16x12 --style medieval --type house` (the 3x3 bedroom); interior designer's review `.claude/reports/p6-roomwalk-interior.md` (beds across 3-wide rooms, 43 of 125).
  Look for: does a bed reaching into the centre of a 3x3 room read as a bedroom, or as cramped and wrong?
  Result:
