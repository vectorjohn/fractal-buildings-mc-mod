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

- [ ] **TeleportHold cap** (2026-11-01, measured after CASCADE): TeleportHold (the wait before a teleported player is let into the world) is capped at 90 s. After QUAYLAZY (lazy quay trace) and CASCADE (cheaper planning of the neighbouring towns that the MAINSTUB "roads that lead nowhere get trimmed" check needs), measured on a quiet beebox: a cold metropolis arrival holds 33 s (was 47 s; vanilla arrival 3.5-4.5 s), the slowest cold chunk is 24.6 s (was 34.9 s). About 14 s of that chunk is still waiting on neighbour-town planning. A 15 s cap would let the player in about 18 s early, onto unfinished chunks (the whole-server freeze case). Recommendation: lower the cap 90 → 60 s now; reaching about 15 s needs rules that change output (plan questions DQ1/DQ2) or saving warmed plans with the world. Agree on 60 s? [regionperf-decision.md](review/regionperf-decision.md) section 7, "After CASCADE".
- [ ] **Cold district plan ceiling** (2026-11-01, measured): the perf row "Cold district plan, p95" (the slowest of 7 cold district plans in the planner benchmark) is 3.67 s on a quiet beebox against its 3 s ceiling. It looked like 1.15 s before only because another row happened to trace a river's quay chain first and this one reused it; tracing that chain cold is the real cost. Fix it with a QUAYLAZY follow-up (prefetch only the shoreline corridor; a plan first), or raise the ceiling to about 4.5 s? [PERFORMANCE.md](PERFORMANCE.md), [regionperf-decision.md](review/regionperf-decision.md) section 7.
- [ ] **Metro flight warmer** (2026-10-29): the region warmer starts only ~2 km out on an elytra flight (worst tick 2.2 s local, 22.5 s beebox); start it earlier, scaled by speed, now in P6 or in P7? [profile-metropolis.md](review/profile-metropolis.md), win candidate 8.
- [ ] **REGIONPERF budget** (2026-10-27, redone 2026-10-31): you rejected the 60 s cold arrival. New numbers in [regionperf-decision.md](review/regionperf-decision.md) section 7, measured on a quiet beebox, mod against vanilla: at a cold metropolis the slowest chunk takes 53 s (vanilla 0.33 s) and the 9 x 9 block 60 s (vanilla 2.6 s); the region row takes 11.6 to 14.8 s (ceiling 10 s), `/locate` fresh 5.2 s (ceiling 5 s, no vanilla baseline). To decide (section 8): the acceptable cold-arrival wait (seconds, or a multiple of vanilla), whether 0.1.0 waits for the cheaper-work options (hold only what the tile needs; quays out of the chunk pipeline), and the region-row ceiling; release-plan Q4. Update TPHOLD15 (2026-10-31): the teleport hold cap is now 90 s (John chose a longer hold until QUAYLAZY lands; 15 s was tried); landing in an unloaded chunk is safe (no void, no damage) but freezes the server tick until that chunk is generated (about 40 s on beebox); "hold only what the tile needs" is built but off by default, see the end of the regionperf thread.

# Rules (DS proposals)

- [ ] **DS-55 to DS-73 and amendments** (2026-10-29; updated 2026-10-31): accept, edit or drop each proposal still open: DS-55 and DS-57 to DS-73, and the amendments to DS-6, DS-20 (cut trees go whole), DS-25, DS-35 (the main door is the marked one; doors on the axis), DS-36, DS-45 (secondary doors near the ground), DS-47, DS-52, DS-54 (joins are level) and DS-56 (exit roads with nothing beyond). Decided since and filed: DS-56 itself, the DS-54 narrowing, DS-75, DS-76, DS-77 and the DS-45 high-rise and DS-35 small-wing amendments. [STANDARDS.md](design/STANDARDS.md), "Proposed by agents".

# Design

- [ ] **Roads first** (2026-10-31): your shower thought, worked through. Should we run the CLI prototype (S to M, recorded terrain, no game: a roads-first network next to ADR 0004's in `region.html`, with the proxy repair rate and read counts)? If so, before or after 0.1.0? Recommendation: after, and don't switch without it. [roads-first.md](research/worldgen/roads-first.md), section 5.
- [ ] **BEARDFIT** (2026-10-31): your "add stairs later" question (02.44.32). Diagnosed: Minecraft's beard (the terrain it pulls toward each building's slab) raised the ground a block, so the bottom step sank. Fix which way: (c) a late planner pass that re-reads the ground with the beard at each flight and fits the foot (recommended; amends ADR 0003, worldgen, rule D4), (d1) no beard, the planner grades lots itself (bigger, exact), or (d2) the beard plane at the floor surface (no step at the door)? And P7 (recommended: walkable, needs the ADR amendment) or P6? [ROADMAP 7.26](ROADMAP.md), [late-stairs](research/worldgen/late-stairs.md), [REVIEW reply](review/phase6/playtest-2026-10-31/REVIEW.md).
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
