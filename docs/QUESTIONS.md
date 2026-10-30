# Open questions for John

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

- [ ] **PORTALCOLD** (2026-10-30): a first nether-portal trip into an unvisited city plans it on the server thread before vanilla's loading screen opens (`PortalForcer.createPortal` needs FULL chunks). Inferred, not measured: a silent freeze of seconds in a town, possibly past the 60 s watchdog in a cold metropolis. After measuring, pick: (a) accept, (b) warm the scaled overworld exit point while a player stands in a portal, (c) hold the portal transition like TPHOLD. Measurement item PORTALCOLD-M in progress. [ROADMAP 6.30](ROADMAP.md), [teleport-loading-screen.md](research/worldgen/teleport-loading-screen.md).
- [ ] **Metro flight warmer** (2026-10-29): the region warmer starts only ~2 km out on an elytra flight (worst tick 2.2 s local, 22.5 s beebox); start it earlier, scaled by speed, now in P6 or in P7? [profile-metropolis.md](review/profile-metropolis.md), win candidate 8.
- [ ] **REGIONPERF budget** (2026-10-27): what cold-arrival wait is acceptable for 0.1.0, and does the `/locate` ceiling move? Rec: 60 s cold arrival, `/locate` stays 5 s. [regionperf-decision.md](review/regionperf-decision.md) section 7; release-plan Q4.

# Rules (DS proposals)

- [ ] **DS-55 to DS-75 and amendments** (2026-10-29): accept, edit or drop each proposal (incl. DS-20, DS-25, DS-35, DS-36, DS-45, DS-47, DS-52, DS-54, DS-6 amendments). [STANDARDS.md](design/STANDARDS.md), "Proposed by agents".
- [ ] **DS-75 basements and DS-12** (2026-10-29): do basement rooms count for DS-12, and are bedrooms or kitchens ever allowed below grade? Rec (interior designer): no and no. [STANDARDS.md](design/STANDARDS.md) DS-75, [basements-interior](review/reports/basements-interior.md).
- [ ] **DS-56 roads end at something** (2026-10-29): trim stubs to the last junction in P6, a kept stub ends at a ferry landing (also covers MAINSTUB, DEADPORT)? [architect](review/playtest1028-qs/architect.md), [STANDARDS.md](design/STANDARDS.md).
- [ ] **DS-54 narrowing** (2026-10-29): DS-54 is accepted; also narrow it to routes (dirt and gravel tracks keep DS-31's full-block steps), or leave it? [REVIEW playtest 2026-10-28](REVIEW.md), [architect](review/playtest1028-qs/architect.md).
- [ ] **DS-20 leaves over roads** (2026-10-29): are the floating leaves and trunk at 20.16.21 and 20.18.20 P6 bugs, or backlog? 6.29 ROADLEAFORDER (merged since) may cover them. [playtest 2026-10-28](review/phase6/playtest-2026-10-28/review.md).
- [ ] **DS-35 small workshop wing** (2026-10-28): amend DS-35 so a wing too small for its own door gives the hall a person door, or require a bigger wing? Rec (interior designer): amend. [interior review](review/phase6/ds35doors/p6-ds35doors-interior.md).

# Design

- [ ] **Door step air** (2026-10-29): should a door step claim 4 air cells above it, like a street, so no neighbour's tree crown sits 3 above it? Optional architect question from FOLIAGEFAIL. [log](log/2026-10-29-p6-foliagefail.md).
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
