# 5.11 Interior circulation (coder)

Branch `claude/p5-circulation` (base `claude/run-phase5`). John's Phase 4 gate feedback: some houses
(medieval seed 7) have rooms you reach only by walking through a chain of other rooms; wants a hall or
central room, some oddness, and open connections (archways, open plan).

## What changed (all in the interior pass; `interior/FloorPlan`, `InteriorPass`, `RoomProgram`)

- **Hub door graph.** The door set of a floor used to be a random spanning tree (plus loops). Now,
  in 85 % of multi-room plans, every room hangs off a *hub* by shortest paths: the corridor if
  there is one, else the best-connected room, strongly preferring the room you arrive in (the entry
  room, else the room where a strip door from another mass opens, else the stair room), so rooms
  are reached through the hub, not each other. 15 % of plans (`WALK_THROUGH_PERCENT`, seeded) keep
  the old random tree: the walk-through layouts John wants "in moderation". Loops (`loopPercent`) stay.
- **Better plans, not just better doors.** A floor is planned up to 8 times (`ATTEMPTS`), the first
  at the style's room size, later ones with bigger rooms (`SCALE` 100..450 %); the first plan
  with no room three or more doors from the arrival room wins, else the least bad. Rooms beside
  a corridor are cut across it so each has a door onto it (highrise floors had rows of rooms
  behind rooms).
- **Room purpose drives adjacency.** New `RoomProgram.Role.THROUGH`: the room where another mass
  opens in (a strip door) on a floor with more than one room is a passage, not someone's room:
  house `hall` (small) / `living` (big) on the ground floor, `landing` above, default program `hall`.
  So a wing is no longer reached through a bedroom. Bedrooms stay behind a hall or landing.
- **Open connections.** New IR opening kind `arch` (additive; `docs/IR.md`): a doorless opening
  1-3 cells wide and 2 high carved from the partition, with a trim lintel when 2-3 wide and the storey
  is tall enough. 40 % (`ARCH_PERCENT`) of doors between two public rooms (`RoomProgram.openPlan`:
  entry, hall, living, kitchen, landing, dining, porch; never bedrooms) become arches (`FloorPlan.withArches`).
  Piers stay at both ends, no stair-core side, no other door within a cell. Hooks keep clear of the
  whole span. Rooms and cuts are unchanged, so reachability is too.
- **Viewer:** `OPENING_KINDS` accepts `arch` (before this the parser rejected an IR that has one).
  The viewer draws voxels, not openings: an arch is an air hole with a `trim` lintel (a door is the
  orange `door` role), so it reads differently from a door in the cutaway.

## Metric (`core/metrics/Circulation`, `CirculationTest`)

Per storey you arrive behind an outside door or at the top of a stair flight; every door or arch out of
a room costs 1 unless it is the arrival room or circulation space (entry, corridor, stair, hall,
landing, porch, nave). A room with cost >= 2 sits behind two or more ordinary rooms. `deepStrict`
counts every room, the arrival room included (the literal reading). Measure:
`-Dfb.circulation=N` on `CirculationTest` prints per type and style. 1500 buildings per cell, lots from
the type's test lots, seeds 0..1499. Before = base 9c5aab7 (plus only the metric class).

| house | before: rooms behind >=2 rooms (houses with one) | after | strict before | strict after |
|---|---|---|---|---|
| plain | 1.51 % (3.1 %) | 0.12 % (0.3 %) | 10.1 % | 8.7 % |
| medieval | 4.88 % (9.8 %) | 0.20 % (0.5 %) | 19.1 % | 17.1 % |
| plains | 1.87 % (3.7 %) | 0.14 % (0.4 %) | 11.6 % | 10.2 % |
| desert | 5.17 % (12.5 %) | 0.18 % (0.6 %) | 20.7 % | 19.2 % |
| snowy | 3.08 % (6.5 %) | 0.27 % (0.8 %) | 14.9 % | 12.0 % |

So the thing John saw (chains of ordinary rooms) drops ~10-25x. The strict count barely moves:
rooms reached through the entry room and a hub are still "behind two rooms" literally; that is the
point (entry, hall, bedroom), and the 15 % walk-through plans keep real chains.
Other types, deep rooms (before, after): highrise 42 % -> 3.4 % (corridor rooms cut across), block
0.1-0.2 % -> 0.01-0.03 %, hall 1.0-1.8 % -> 0-0.03 %, workshop 0.03-0.15 % -> 0, temple ~0, tower 0. Shops:
before 8.8-11.9 % (pre-5.12 shop, base 9c5aab7); after merging 5.12 shops are 2.0-3.2 %: the shop room is
the arrival, a back room and the stair behind it remain a legitimate shop -> back room -> stair chain.

## Verification

- `:planner-core:test` after merging `claude/run-phase5` (5.12 included): 334 tests, 0 failing, goldens
  regenerated (see below). `V1PropertyTest` (all nine types x lots x seeds incl. rooms-reachable, room-graph,
  entrance-reachable) passes at scale 1; at `-Dfb.propertyScale=6` one block case fails
  (`fractal-cli gen --type block --seed 89 --lot 100x80 --at 31,-51 --front south --style plain --terrain plane:78:1:1:16 --check`:
  entrance-reachable) and **fails identically on the base commit** (not from this item).
- `CirculationTest`: house deep-room share < 1 % (plain, medieval, desert) and >= 40 of 400 houses keep
  a literal chain; archways: public rooms only, 2 high, air at foot and head, lintels on wide ones, >= 20
  seen; same seed, same openings. `DeterminismLintTest` passes. Viewer: `ir.test.ts` accepts `arch`, rejects `gate`.
- Mutations: `WALK_THROUGH_PERCENT = 100` fails the deep-room test; `ARCH_PERCENT = 0` fails the archway test.
- Galleries (`FB_PRESETS=house-medieval,house-plains,house-desert npm run gallery`, 0 violations, all cells
  non-empty); after-images and before/after ground-floor plans in `docs/review/phase5/circulation/`.
  Compared with the Phase 4 cutaway: footprints and massing are identical (the pass does not touch them);
  inside, fewer and larger rooms on row plans, halls and larger living rooms where a wing joins, arches
  between living, kitchen and entry. Seed 7: the row of four rooms (bedroom, kitchen, living, entry
  with a wing off the bedroom) is now three rooms (living, kitchen, living) with an arch, the wing
  reached from the living room.

## GEN_VERSION and goldens

Not bumped (stays 2, per `docs/plans/phase4.md`: nothing caches IR outside a process before Phase 6).
Goldens regenerated for block, hall, highrise, house, shop, temple, workshop (copied from
`planner-core/build/golden/v1/`); tower and wall unchanged. Shop and workshop goldens also moved with 5.12:
on conflict I took ours, then regenerated after the merge.

## Surprises / decisions

- The first metric version counted the arrival room and hubs as rooms; the hub change then barely moved
  it (entry -> hub -> room is literally two rooms). Kept both numbers, and defined the primary one around
  circulation space and the arrival room.
- A strip join room is the arrival of its own mass only if the mass has no entrance; otherwise wings
  looked "close" to the planner when they are really behind the main block.
- Remaining chains are mostly across masses (strip door positions are fixed before the plan), by design 15 % of plans.
- Cheap to change: `WALK_THROUGH_PERCENT` (FloorPlan), `ARCH_PERCENT` (InteriorPass), `SCALE`, `RoomProgram.openPlan`.
