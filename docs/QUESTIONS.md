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
  - @John 2026-10-31: lets do c. P6 bug. A hard crash is a problem.
- [ ] **Metro flight warmer** (2026-10-29): the region warmer starts only ~2 km out on an elytra flight (worst tick 2.2 s local, 22.5 s beebox); start it earlier, scaled by speed, now in P6 or in P7? [profile-metropolis.md](review/profile-metropolis.md), win candidate 8.
- [ ] **REGIONPERF budget** (2026-10-27, redone 2026-10-31): you rejected the 60 s cold arrival. New numbers in [regionperf-decision.md](review/regionperf-decision.md) section 7, measured on a quiet beebox, mod against vanilla: at a cold metropolis the slowest chunk takes 53 s (vanilla 0.33 s) and the 9 x 9 block 60 s (vanilla 2.6 s); the region row takes 11.6 to 14.8 s (ceiling 10 s), `/locate` fresh 5.2 s (ceiling 5 s, no vanilla baseline). To decide (section 8): the acceptable cold-arrival wait (seconds, or a multiple of vanilla), whether 0.1.0 waits for the cheaper-work options (hold only what the tile needs; quays out of the chunk pipeline), and the region-row ceiling; release-plan Q4.

# Rules (DS proposals)

- [ ] **DS-55 to DS-73 and amendments** (2026-10-29; updated 2026-10-31): accept, edit or drop each proposal still open: DS-55 and DS-57 to DS-73, and the amendments to DS-6, DS-20 (cut trees go whole), DS-25, DS-35 (the main door is the marked one; doors on the axis), DS-36, DS-45 (secondary doors near the ground), DS-47, DS-52, DS-54 (joins are level) and DS-56 (exit roads with nothing beyond). Decided since and filed: DS-56 itself, the DS-54 narrowing, DS-75, DS-76, DS-77 and the DS-45 high-rise and DS-35 small-wing amendments. [STANDARDS.md](design/STANDARDS.md), "Proposed by agents".

# Design

- [ ] **PORCHSTEP main step** (2026-10-29): should the main door get a 3-wide step to mark it? [REVIEW 6.19 PORCHSTEP](REVIEW.md), [picture](review/p6-porchstep/house-door-step.png).
  - @John 2026-10-31: yes
- [ ] **LEAFORDER trees** (2026-10-29): a few more crowns by walls and over streets at chunk lines, fewer big trees against buildings: acceptable? [REVIEW 6.28](REVIEW.md), [picture](review/p6-leaforder/street-after.png).
  - @John 2026-10-31: yes
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
