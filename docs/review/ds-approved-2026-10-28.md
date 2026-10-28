# Rules approved 2026-10-28: enforcement today

John approved twelve rules and amendments in
[STANDARDS.md](../design/STANDARDS.md) on 2026-10-28. They have moved from
"Proposed by agents" to their own sections (amendments straight after the
rule they amend). Accepted DS-45 had also been left under "Proposed"; it moved
to "Access and circulation" with its amendment. The table below gives each
rule's Enforcement line in short and what code exists in `claude/run-phase6`
at bcc4cf6a. "None, needs an item" means no check or generator code was found
for it and no ROADMAP item covers it. Assistant, 2026-10-28, from a grep of
`planner-core/`, `mod/`, ROADMAP and the run ledger; nothing was run.

| Rule | Section | Enforcement (short) | Code today |
|---|---|---|---|
| DS-34 High-rises come in several looks | Building and facade | Guideline. Candidates: the look recorded in the IR, `glazing-share` per look (DS-33), DS-1's `highrise-materials`, rooms lit and lobby furnished in glass towers | None for looks: no look is chosen or recorded today. `highrise-materials` is planned in ROADMAP 7.9, not built. The run ledger lists DS34LOOKS as a ready item, but it has no ROADMAP line yet. Biome looks wait for John's research |
| DS-35 More entrances | Building and facade | Code candidate `entrance-count` (2+ exterior doors at 2+ storeys or 80+ cells; high-rises on two faces, main door 2+ wide; doors into a hall, kitchen, lobby or shop floor) | None: `Massing.entrances()` gives one door per mass, and no `entrance-count` check exists. The ledger lists DS35DOORS as ready; no ROADMAP line yet |
| DS-35 amendment: workshop doors | Building and facade | Folds into `entrance-count` | None. The Polish backlog line "WORKSHOP lean-to" (ROADMAP, 6.x playtest) describes the fix; it could go into DS35DOORS |
| DS-36 Smooth pitched roofs | Building and facade | Code candidates `roof-smooth` (every exposed top cell of a pitched roof is a stair or slab) and a style test (every `roof` palette entry has stairs and a slab) | None, needs an item: `RoofPass` emits full `ROOF` cells (the rule says every roof in game is stepped). The Polish backlog line "DORMER stairs" depends on it |
| DS-8 amendment: railings that hold | Access and circulation | Code `rail-height` (a rail's y is at least the guarded surface's y + 1; every open edge railed), extend `RailingTest` | Mostly done: RAILFIX (ROADMAP 6.9 notes) put rails one above the surface; `RailingTest` checks it (`railsStandOneBlockAboveTheSurfaceAndLeaveNoGap`, `aRailTopIsNotStandable`). There is no named `rail-height` invariant in `check`. City bridges have no rails: ROADMAP 7.15 |
| DS-8 amendment: flights, corners and the lot line | Access and circulation | Code, extend `RailingTest`: no standable surface within 0.6 below a rail top, corner posts, flights inside the lot | Done: LOTRAIL and MINLOT (ROADMAP 6.9 notes) with `RailingTest`, `LotRailTest`, `SpecPolicyV1.railRoomOk` and the `LOT_GRADE` check |
| DS-38 Guarded roofs | Access and circulation | Code candidate, folds into `rail-height`: every edge cell of a door-reached roof has a wall with a rail above it | High-rises only: `TowerRoof.guard` (ROADMAP 7.13 B3b), tested in `HighriseFacadeTest`. There is no general check and no code for other types' door-reached roofs or terraces. A `rail-height` item could cover it |
| DS-45 amendment: run and turn sizes | Access and circulation | Code candidate: extend the planned `flight-landings` check (`MIN_RUN = 2`) | Partly done: `flight-landings` exists as a design warning (`DoorChecks`, `MAX_RISERS = 3`, `FlightRunsTest`; ROADMAP 7.17 SHOPLOW). There is no `MIN_RUN`, turn spacing, landing size or headroom check. The Polish backlog line "SWITCHBACK" describes the work |
| DS-40 Settlement tier follows built lots | Settlements | Code candidate (planner, after district planning) | None, needs an item. CITYQ's tier-follows-radius (6.8) is the nearest code |
| DS-50 Roads don't tunnel (for now) | Settlements | Code candidate `road-open-sky` (no solid block above a road cell up to the sky, outside bridges and covers) | None for the check. 6.17 ROADCLEAR (done) found no tunnel-making cause in the playtest world, and the architect's fix direction (an open cut, or route round) is in its notes. `road-open-sky` needs an item |
| DS-30 amendment: bamboo only on footbridges | Settlements | Folds into `road-one-material` (DS-30) plus a width check for bamboo decks | No check: `road-one-material` is planned in ROADMAP 7.10, not built. The rule's "None today" text is out of date: the jungle `deck` role is now `jungle_planks` only, so no bamboo bridges are generated. The rule holds because no bamboo bridges are made |
| DS-52 Roads step down one rank at a time | Settlements | Code candidate in `city-check` (width at each gate and junction), folding into the planned `road-hierarchy` | None for widths. `road-hierarchy` exists (`FillStats`, ROADMAP 7.20 METROFILL), but it measures the arterial and main share of street length, not widths at joins. The design is written in ROADMAP 6.15 ROADJOIN ("Width at the join") |

## Needs an item

- DS-36 roofs: generator plus the `roof-smooth` check and the palette test.
- DS-40: a tier re-check after district planning.
- DS-50: a `road-open-sky` check (city-check and the `roads` report, or the mod).
- DS-52: a width check at gates and junctions in `city-check` (the generator
  side is in 6.15 ROADJOIN).
- DS-34 and DS-35 (with the workshop amendment): they have ledger names
  (DS34LOOKS, DS35DOORS) but no ROADMAP lines.
- Smaller additions to existing work: a `rail-height` invariant covering DS-8
  and DS-38 for every type; `MIN_RUN` and the landing rules in `flight-landings`
  (SWITCHBACK).
