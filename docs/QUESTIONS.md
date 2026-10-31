# Open questions for John

Item codes (MAINSTUB, PARREADS, ...) are looked up in [GLOSSARY.md](GLOSSARY.md).

Every decision waiting for you, one line each, newest first under each heading. Answer on the line (`-- @John ...`) or in the linked place; the coordinator records the answer there and removes the line. Agents add a line here for any question that needs your decision, wherever the details live (REVIEW.md keeps the "look at this" items).

# Release (0.1.0)

- [ ] **Release scope** (2026-10-29): is the P6 checklist the whole release scope, with nothing added? See [release-plan.md](review/release-plan.md) section 3, Q1.
- [ ] **PORCHSTEP, DOORBANK, STAIRFOOT** (2026-10-29): release blockers, or any of them to P7? (All three are merged now.) [release-plan.md](review/release-plan.md) Q2.
- [ ] **Long crossings at approaches** (2026-10-29): for 6.15, drop the approach, or allow a long deck at town tier and up until 7.3? Architect: allow the deck. [release-plan.md](review/release-plan.md) Q3, [REVIEW 6.15 ROADJOIN](REVIEW.md), [architect](review/roadjoin/p6-roadjoin-s1-architect.md).
- [ ] **Gate kit seeds** (2026-10-29): do your two played seeds count toward "three seeds", or do all three run fresh on the release candidate? Note: the fresh kit seed has no metropolis within 4 km. [release-plan.md](review/release-plan.md) Q5, [gate kit](review/phase6/gate-kit/README.md).
- [ ] **P7 features** (2026-10-29): which 3-5 features make up P7? [release-plan.md](review/release-plan.md) Q6.
- [ ] **Backlog section** (2026-10-29): may the other Phase 7 items move to a new "Backlog (unscheduled)" section, where `#backlog` notes also go? [release-plan.md](review/release-plan.md) Q7.
- [ ] **Patch releases** (2026-10-29): at most weekly after a passing heavy run, cut from the run branch, you publish. Yes? [release-plan.md](review/release-plan.md) Q8.
- [ ] **Publishing** (2026-10-29): which platform (default Modrinth), what project name, and does the source go public? [release-plan.md](review/release-plan.md) Q9; [REVIEW Phase 6 plan decisions](REVIEW.md) item 7.

# Perf

- [ ] **PORTALCOLD** (2026-10-30, measured): a first nether-portal trip into an unvisited city freezes the whole server inside `getPortalDestination` (it generates 25x25 chunks on the server thread) before vanilla's loading screen opens. Measured on beebox: wild 12.7 s, town 36.6 s, metropolis core 65.3 s (over the 60 s dedicated-server watchdog). The exit portal damaged nothing in either sample (plaza, forest floor). Options: (a) accept (not defensible for metropolises); (b) warm ahead while a player stands in a portal (can't finish in vanilla's 4 s wait); (c) hold the portal trip with a message, hooking `getPortalDestination` and loading the chunks off the server thread. **Recommendation: (c).** P6 bug or P7? [ROADMAP 6.30](ROADMAP.md), [findings](research/spikes/portalcold.md) (lands with PORTALCOLD-M).
- [ ] **Metro flight warmer** (2026-10-29): the region warmer starts only ~2 km out on an elytra flight (worst tick 2.2 s local, 22.5 s beebox); start it earlier, scaled by speed, now in P6 or in P7? [profile-metropolis.md](review/profile-metropolis.md), win candidate 8.
- [ ] **REGIONPERF budget** (2026-10-27, redone 2026-10-31): you rejected the 60 s cold arrival. New numbers in [regionperf-decision.md](review/regionperf-decision.md) section 7, measured on a quiet beebox, mod against vanilla: at a cold metropolis the slowest chunk takes 53 s (vanilla 0.33 s) and the 9 x 9 block 60 s (vanilla 2.6 s); the region row takes 11.6 to 14.8 s (ceiling 10 s), `/locate` fresh 5.2 s (ceiling 5 s, no vanilla baseline). To decide (section 8): the acceptable cold-arrival wait (seconds, or a multiple of vanilla), whether 0.1.0 waits for the cheaper-work options (hold only what the tile needs; quays out of the chunk pipeline), and the region-row ceiling; release-plan Q4.

# Rules (DS proposals)

- [ ] **DS-75 and DS-12: do basement rooms count?** (2026-10-31): DS-12 "Bedrooms first" says a house gets more bedrooms before a second room of another kind. Filed reading: basement storage, cellar, workshop and utility rooms are NOT "other rooms" for that rule (a 2-bedroom house may have a basement with a cellar, storeroom and workshop without breaking DS-12); a basement bedroom DOES count as a bedroom (and a basement kitchen as a kitchen). Confirm or correct. Also the shares (shop variants ~1 in 10; basement bedroom 1 in 20, 1 in 8 modern/city; kitchen or wet bar 1 in 30, 1 in 10 modern/city) are agent picks in ASSUMPTIONS.md. [STANDARDS.md](design/STANDARDS.md) DS-75, DS-77.
- [ ] **DS-55 to DS-74 and amendments** (2026-10-29; DS-75 accepted 2026-10-31): accept, edit or drop each proposal (incl. DS-20, DS-25, DS-35, DS-36, DS-45, DS-47, DS-52, DS-54, DS-6 amendments). [STANDARDS.md](design/STANDARDS.md), "Proposed by agents".
- [ ] **DS-56 roads end at something** (2026-10-29): trim stubs to the last junction in P6, a kept stub ends at a ferry landing (also covers MAINSTUB, DEADPORT)? [architect](review/playtest1028-qs/architect.md), [STANDARDS.md](design/STANDARDS.md).
- [ ] **DS-54 narrowing** (2026-10-29): DS-54 is accepted; also narrow it to routes (dirt and gravel tracks keep DS-31's full-block steps), or leave it? [REVIEW playtest 2026-10-28](REVIEW.md), [architect](review/playtest1028-qs/architect.md).
- [ ] **DS-20 leaves over roads** (2026-10-29): are the floating leaves and trunk at 20.16.21 and 20.18.20 P6 bugs, or backlog? 6.29 ROADLEAFORDER (merged since) may cover them. [playtest 2026-10-28](review/phase6/playtest-2026-10-28/review.md).
- [x] **DS-76 links that share a valley share a road** (2026-10-31): two roads between settlements don't run side by side within about 16 blocks for more than about 96 blocks or cross more than once; where their routes meet they share one road and part at a junction (your note 03.33.50). Accept? Nothing else needed from you now (architect). Measured first by a report-only `links-braid` check; the fix is ROADMAP 7.25 LINKBRAID (road links braiding), which needs an ADR 0004 (roads between settlements) amendment to N4/RD4. [architect answer](review/playtest1031-qs/architect.md) Q2, [STANDARDS.md](design/STANDARDS.md), [playtest 2026-10-31](review/phase6/playtest-2026-10-31/REVIEW.md).
  - @John 2026-10-31: Yes accept
- [x] **DS-45 amendment: high-rise entrances** (2026-10-31): a high-rise's or city block's main door rises at most 2; a rise of 3-6 takes a straight flight square to the facade, at least door width + 2 wide, inside a forecourt; more than that is not built: the lobby goes at street level and the base cuts into the slope (your note 03.16.21). Accept? Work item: ROADMAP 7.24 HRENTRY (high-rise entrances). [architect answer](review/playtest1031-qs/architect.md) Q1, [STANDARDS.md](design/STANDARDS.md), [playtest 2026-10-31](review/phase6/playtest-2026-10-31/REVIEW.md).
  - @John 2026-10-31: accept!
- [ ] **DS-77 a shop sleeps one household** (2026-10-31): a shop's upper storeys are one flat: kitchen-living room by the stair, 2-3 bedrooms, then stock rooms; beds at most 3 (two-storey shop), 4 (any village shop), 5 (town shop of 3+ storeys); no bedroom reached only through another. Accept, or keep the many-bed flat (your 02.42.59 "eccentric architect") as a rare town-only variant? Work item: ROADMAP 7.23 SHOPFLAT (shop room programme). [interior answer](review/playtest1031-qs/interior.md), [STANDARDS.md](design/STANDARDS.md), [playtest 2026-10-31](review/phase6/playtest-2026-10-31/REVIEW.md).
  - @John 2026-10-31: I’d like to keep things like this on some kind of dice roll. I don’t want to rule out the weirdness, and I have concerns about DS rules being too strict and prescriptive to the point that they dictate one way to do things. I like DS-77 as written so long as it is based on chance, where we still sometimes get the eccentric architect.
- [ ] **DS-55 to DS-75 and amendments** (2026-10-29): accept, edit or drop each proposal (incl. DS-20, DS-25, DS-35, DS-36, DS-45, DS-47, DS-52, DS-54, DS-6 amendments). [STANDARDS.md](design/STANDARDS.md), "Proposed by agents".
- [ ] **DS-75 basements and DS-12** (2026-10-29): do basement rooms count for DS-12, and are bedrooms or kitchens ever allowed below grade? Rec (interior designer): no and no. [STANDARDS.md](design/STANDARDS.md) DS-75, [basements-interior](review/reports/basements-interior.md).
  - @John 2026-10-31: too prescriptive. Sometimes basements have bedrooms and kitchens *especially* in modern cities. I think it’s more like, basement rooms should prioritize basement uses, with some chance (low) of having a bedroom. Houses vary a lot! My friend had a sort of split level where the basement did in fact have a kitchen (maybe more of a wet bar) and bath and bedroom. That was less of a cellar. Yes basements can have bedrooms, no it shouldn’t be the norm.
- [x] **DS-56 roads end at something** (2026-10-29): trim stubs to the last junction in P6, a kept stub ends at a ferry landing (also covers MAINSTUB, DEADPORT)? [architect](review/playtest1028-qs/architect.md), [STANDARDS.md](design/STANDARDS.md).
  - @John 2026-10-31: Yes, approved.
- [x] **DS-54 narrowing** (2026-10-29): DS-54 is accepted; also narrow it to routes (dirt and gravel tracks keep DS-31's full-block steps), or leave it? [REVIEW playtest 2026-10-28](REVIEW.md), [architect](review/playtest1028-qs/architect.md).
  - @John 2026-10-31: approved
- [x] **DS-20 leaves over roads** (2026-10-29): are the floating leaves and trunk at 20.16.21 and 20.18.20 P6 bugs, or backlog? 6.29 ROADLEAFORDER (merged since) may cover them. [playtest 2026-10-28](review/phase6/playtest-2026-10-28/review.md).
  - @John 2026-10-31: backlog
- [x] **DS-35 small workshop wing** (2026-10-28): amend DS-35 so a wing too small for its own door gives the hall a person door, or require a bigger wing? Rec (interior designer): amend. [interior review](review/phase6/ds35doors/p6-ds35doors-interior.md).
  - @John 2026-10-31: take interior designer’s rec here.

# Design

- [x] **High-rise lobby at street level** (2026-10-31): put a high-rise's lobby at street level by default (no front stair), and build your wide straight flight (03.16.21) only where the slope can't be absorbed? Rec (architect): yes, lobby at street level by default (the tower dug into a slope); your flight only where 3-6 risers remain, in a forecourt. This decides whether high-rise lots get a 3-row forecourt in P7. [architect answer](review/playtest1031-qs/architect.md) Q1, ROADMAP 7.24 HRENTRY (high-rise entrances), [playtest 2026-10-31](review/phase6/playtest-2026-10-31/REVIEW.md).
  - @John 2026-10-31: yes
- [x] **Door step air** (2026-10-29): should a door step claim 4 air cells above it, like a street, so no neighbour's tree crown sits 3 above it? Optional architect question from FOLIAGEFAIL. [log](log/2026-10-29-p6-foliagefail.md).
  - @John 2026-10-31: yes
- [ ] **PORCHSTEP main step** (2026-10-29): should the main door get a 3-wide step to mark it? [REVIEW 6.19 PORCHSTEP](REVIEW.md), [picture](review/p6-porchstep/house-door-step.png).
- [ ] **LEAFORDER trees** (2026-10-29): a few more crowns by walls and over streets at chunk lines, fewer big trees against buildings: acceptable? [REVIEW 6.28](REVIEW.md), [picture](review/p6-leaforder/street-after.png).
- [ ] **Flat junction pad** (2026-10-29): the deep main-road cut at 20.14.21 comes from a flat junction pad on overlapping main roads; fix it as a P6 bug or as a 7.19 line? Spike says fix first. [road-refine](research/spikes/road-refine.md).
- [ ] **Road refine pass** (2026-10-29): add the main-road refine pass (T = 8) to 7.x, after the pad fix? Spike recommends it. [road-refine](research/spikes/road-refine.md).
- [ ] **Unlit house rooms** (2026-10-28): make "a light in every habitable room" (DS-24) its own item now? [REVIEW](REVIEW.md), [interior review](review/phase6/ds35doors/p6-ds35doors-interior.md).
- [ ] **DSROADS lane joins** (2026-10-28): close DSROADS with "never a lane" as a known issue on 6.15, or hold it? Rec (architect): known issue. [architect](review/dsroads/p6-dsroads-architect.md).
- [ ] **Snow on street edges** (2026-10-28): snow layers on some snowy-city street edges, by chunk order: acceptable, or always clear? [ORDERSIM](review/ordersim/p6-ordersim-s4a.md).
- [ ] **BRIDGEENDS rails** (2026-10-27): add rails to the stone abutments in BRIDGEENDS, or widen 7.15 to "decks and abutments" and merge now? Rec (architect): widen 7.15. [architect](review/bridgeends/p7-bridgeends-architect.md).
- [ ] **Lakes near settlements** (2026-10-27): keep vetoing vanilla lakes that touch our pieces, or allow them and seal? The road veto has no upper bound. [REVIEW ORDERFAIL](REVIEW.md).
- [ ] **Bed in a 3x3 bedroom** (2026-10-27): a bed reaching the centre cell: acceptable, or re-plan the room? [interior](review/roomwalk/p6-roomwalk-interior.md).
- [ ] **City arterial surface** (2026-10-27): which block for arterials? Rec (architect): `gray_concrete`. [architect](review/roads1/p7-roads1-architect.md).
- [ ] **Quays end at own tiles** (2026-10-27): METROFILL R1 quays stop at the settlement's own waterfront tiles: acceptable at edges? [REVIEW 7.20](REVIEW.md).
- [ ] **Cherry grove towers** (2026-10-26): birch tower with pink trim or plain birch? [REVIEW P7 A3](REVIEW.md).
- [ ] **Dropped city claimants** (2026-10-23): a small "metropolis" now becomes a town; keep that, or keep dropped claimants at city at least? [cityq](review/phase6/cityq/README.md).
- [ ] **Wall colour variation** (2026-10-23): per-block scatter (today), or intentional patches or bands? [REVIEW PALETTE](REVIEW.md) question 3.

# Process

- [ ] **Workflow proposal** (2026-10-27): answer the 10 points under "What John decides" (merge queue, goldens, heavy batches, reaping, status board, reference worlds). [workflow-proposal.md](review/workflow-proposal.md).
