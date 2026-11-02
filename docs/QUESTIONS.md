# Open questions for John

Item codes (MAINSTUB, PARREADS, ...) are looked up in [GLOSSARY.md](GLOSSARY.md).

Every decision waiting for you, one line each, newest first under each heading. Answer on the line (`-- @John ...`) or in the linked place; the coordinator records the answer there and removes the line. Agents add a line here for any question that needs your decision, wherever the details live (REVIEW.md keeps the "look at this" items).

# Release (0.1.0)

- [ ] **Server-only wording** (2026-11-02, from the release status audit): Phase 6 plan decision 8. The SRVONLY spike (server-only install test) found that a client with only Fabric Loader, and not our mod, joins a modded server fine; may the player README say "clients don't need the mod" (today it says "this is untested")? [release-plan.md](review/release-plan.md) "Phase 6 plan decisions 7 and 8", [server-only spike](research/spikes/server-only.md).
- [ ] **Release basics** (2026-11-02, from the release status audit; files since 2026-10-22): read the LICENSE (MIT), the CHANGELOG draft, the [player README](release/README-player.md) and the placeholder icon before C3 (cutting the release) uses them. [REVIEW](REVIEW.md) "A5 release basics".
- [ ] **Release scope** (2026-10-29): is the P6 checklist the whole release scope, with nothing added? See [release-plan.md](review/release-plan.md) section 3, Q1.
- [ ] **P7 features** (2026-10-29): which 3-5 features make up P7? [release-plan.md](review/release-plan.md) Q6.
- [ ] **Backlog section** (2026-10-29): may the other Phase 7 items move to a new "Backlog (unscheduled)" section, where `#backlog` notes also go? [release-plan.md](review/release-plan.md) Q7.

# Perf


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

- [ ] **The perf worker exclusive jobs** (2026-11-01): the perf worker now runs 3 jobs at once (`ts -S 3` since NixOS generation 6, 2026-10-27), so it is no longer quiet by nature. Perf measurements need it empty for the whole run; QUIETHOLE (quiet check over the whole timed window) now detects overlaps and PERFROUTE makes our router wait for an empty perf worker, but nothing can stop another job being queued during a perf run. Would you add an exclusive option to the perf worker's `job` wrapper (task-spooler `ts -N 3`, the job takes all 3 slots) so perf runs can reserve the box? That change lives on the perf worker, outside this repo. [CLAUDE.md](../CLAUDE.md) "Parallel work".
- [ ] **Workflow proposal** (2026-10-27): answer the 10 points under "What John decides" (merge queue, goldens, heavy batches, reaping, status board, reference worlds). [workflow-proposal.md](review/workflow-proposal.md).
