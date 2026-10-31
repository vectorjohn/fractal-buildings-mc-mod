---
tags: research
track: worldgen
date: "2026-10-31"
author: researcher
licence: proprietary
---

# Late stairs: fitting flights to the ground the world really has

## What it is

John's question on playtest note 02.44.32 (workshop b3, lot d16_m11.l3, village s2_m2, seed 1573336981619055086, 2713 95 -1627): the planner places a building before the terrain is "really" known, so can stairs be fitted later, lazily, once all the terrain is known? This note covers how flights meet the ground today, a diagnosis of this case, and the options.

## Question

What does the planner's "exact" ground include, which later stages move the real ground at a flight's foot, and can a later fit stay deterministic?

## Evidence

**Today (code read):** the planner reads `getBaseColumn` heights (`LiveTerrainProbe.column`, memoised by `ColumnMemo`, the same definition as the dumps). These include noise and aquifers, and leave out the beardifier, carvers and features (NOTES 1.4). ADR 0003 (worldgen) D4: "the plan never re-probes after the beard". `InstancePassV1.steps` searches flights over that ground (plus our own dig-in cuts, `SiteGround`). The last step ends on the planned ground. `headroom` claims air above every tread, the cell where the flight ends and each door-front cell, starting one above the standing level (3 cells at the playtest commit, 4 since 6.32 DOORAIR). Later stages:

| Stage | Moves the ground? | Pure per position? | Handled by |
|---|---|---|---|
| Beard (`beard_thin`, our piece box bottom = slab, `DistrictStructure.buildingPieces`) | yes: fills below the slab plane, carves above it, within about 4-6 blocks (NOTES "SHOPLOW plan") | yes, computable without chunks (`VillageGround`) | nothing: **the cause** |
| Surface rules | no, swaps top blocks (icebergs are the D4 exception; badlands spires not re-checked) | yes | n/a |
| Carvers | can open a hole (rare at the surface) | yes, per chunk | not modelled |
| Features (trees, boulders, lakes) | bury or block, from any neighbour, in any order | no | claims and clears (6.13 STEPBURY, 6.14 TREEIN, `LakeVeto`, `RockVeto`, `TreeClip`) |
| Our claimed air and street cuts | only what the planner put there | yes | the planner knows them |

**This case (throwaway `:mod:test`, not merged, at the playtest commit 75974145):** it used a live planner over vanilla noise with no server (as `EmptySettlementLiveTest`). The beard was the vanilla `Beardifier` built from the building pieces of the 3 x 3 tiles, read through `VillageGround.surface`. The rebuild gives workshop b3 with slab and floor at 95 and the door at 2712,96,-1623 facing north. Its flight is a birch stair at 2712,95,-1624 and an oak stair at 2712,94,-1625, ending on noise ground at -1626 with standing level 94, and air claimed there at 95-97. Noise floor: 94 on every column of x 2705-2719, z -1632..-1622. Beard floor: **95 on all of them**. John's feet were at y 95, which matches the beard, not the noise. So the oak stair replaced a grass block. Its back half is flush with the grass and its front half sits half a block lower, walled by grass on three sides (the screenshot shows the west wall). That notch is the "pit": nothing dug it. The door needed one riser and got two.

**Census (same test, step columns outside the shell; lowest step y minus beard top solid):** village s2_m2: 29 buildings, 76 columns: 66 on grade, 9 one block down, 1 deeper (9 buildings). Metropolis s4_m2, 3 x 3 tiles round d26_m7: 103 buildings, 368 columns: 258 on grade, 33 one down, 77 deeper (27 buildings). The deeper ones include dug-in areaways that are meant to be there; I didn't separate them. The beard lifted the ground by 4 or more on 101 of the metropolis columns.

## Result

- (a) **Read placed blocks at piece time**: depends on chunk order (neighbours' features, flights crossing chunk lines), and the IR no longer matches the world. ADR 0003 D1 rules it out.
- (b) **A late, plan-only fill** (foundation under every step down to the planned ground): deterministic and cheap, but it only fixes ground that ends up *lower* than planned. Here the ground is higher.
- (c) **Widen the planner's terrain model**: surface rules and carvers add almost nothing, and features can't be modelled purely. The beard is the one stage worth adding: **c' = BEARDFIT**, a second, late pass. Once a tile's placements (slab, lot, footprint) and its neighbours' are known, re-read each flight's cells through a beard-aware probe: exact in the game, approximated in the CLI and viewer. Then drop treads at or below that ground, start the claimed air there, and add foundation where it is lower. The inputs don't depend on flights (the piece boxes are lot, footprint and slab), so there is no cycle.
- (d) **Minecraft's own way** is the beard, and the beard is the cause. Vanilla houses put the door at the box bottom so the beard brings the ground to the door, and they have no flights. d1: `terrain_adaptation: none` for buildings plus planner-owned cut and fill per lot: exact, and the CLI matches the game, but it rewrites ADR 0003 W5 and changes looks. d2: put the beard plane at the floor surface (slab + 1): the ground comes up flush with the floor, so there is no plinth step, and it is still inexact where the noise ground is far from the plane.

## Licence

Our own code, plus Minecraft 26.3 read decompiled (Mojang, proprietary): studied, not copied.

## What to borrow

- `VillageGround.surface` + `Beardifier(rigids, junctions, box)`: beard-shaped heights without chunks (NOTES "VILLROAD r8"); the probe pattern above for a census.

## What to avoid

- Any world read in piece code to "see" the ground; fitting only in the mod (the IR, viewer and checks would lie).

## Verdict

Recommend (c') BEARDFIT: deterministic, fixes this case, M, and amends ADR 0003 D4 ("re-probe after the beard, for flight cells only"). Choose (d1) instead if a census of fences, paths and hooks shows the beard misplaces much more than flights.

**Sources / what I couldn't verify:** code: `LiveTerrainProbe`, `ColumnMemo`, `ChunkFill`, `DistrictStructure`, `TilePiece`, `BuildingAirClear`, `StairSeam`, `InstancePassV1.steps/headroom`, the structure JSONs (`beard_thin`), and NOTES on `Beardifier` (decompiled lines). Not checked: the world itself (no in-game run; the screenshot and the feet y agree), the cost of a beard column, the badlands surface claim, vanilla or other mods' beard pieces near lots (vetoed in cities, W7), and how well a CLI beard approximation would match.
