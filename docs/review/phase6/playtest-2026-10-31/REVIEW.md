---
status: Ready for review
author: John
commit: 75974145
seed: 1573336981619055086
---

# Playtest Notes
Playtest on 2026-10-31
Build commit: 75974145

Taken in game with `/fbnote <comment>`; the position, the planner ids and the targeted block are in each caption.


![[2026-10-31_02.35.52.jpg|800]]
I guess the view is worth it for that weird detour! No, just an odd pathing artifact.
  - `2899 141 -1608` yaw 57 pitch 49; forest; outside any settlement
  - @assistant 2026-10-31: Real, minor. A switchback on the road outside village s2_m2 (outside any settlement, so the road between settlements or the village's approach road). Not diagnosed. Filed in the ROADMAP Backlog (unscheduled) next to the road loop from 2026-10-28 (20.15.58). The proposed DS-57 "Roads don't double back" would catch it with a `road-doubles-back` check (none today).

![[2026-10-31_02.40.36.jpg|800]]
on my elytra flight I happened upon this cozy village. #hype
  - `2614 150 -1643` yaw -74 pitch 32; forest; settlement s2_m2 (village); district d16_m11
  - @assistant 2026-10-31: Added to [hype.md](../hype.md).

![[2026-10-31_02.42.59.jpg|800]]
I thought this might be an inn, but I guess not! It has an odd upstairs bedroom layout, lots of beds. Pretty cool. An eccentric architect I assume. #comment
  - `2723 101 -1623` yaw -407 pitch 20; plains; looking at stripped_birch_wood `2725 101 -1622`; settlement s2_m2 (village); district d17_m11; lot d17_m11.l0; building b0 (shop, plains, seed -536845649052431228, 2 storeys); room r6 (room, storey 1)
  - @assistant 2026-10-31: Design comment, no bug filed. A shop's upper storey is the shopkeeper's flat, and DS-60 ("every dwelling sleeps someone") puts beds in its rooms, so a big flat can get many beds. Whether that's right for a two-storey village shop goes to the interior designer as a question. A real inn is already planned: 7.22 INN, which you decided on 2026-10-27. I couldn't rebuild this building with the CLI. The village is outside the 8-block terrain dump of this world (`pt1573-metro-s8`), and the 64-block world map is too coarse: lots and buildings come out different.

![[2026-10-31_02.44.32.jpg|800]]
is this a bug in the stair placement, or just inaccurate terrain measurement? Stair just goes down below the ground.
  - `2713 95 -1627` yaw -690 pitch 32; plains; looking at birch_stairs `2712 95 -1624`; settlement s2_m2 (village); district d16_m11; lot d16_m11.l3; building b3 (workshop, plains, seed 7613073995594729505, 1 storeys)
  - @assistant 2026-10-31: Bug, but walkable: the bottom step of the workshop's flight sits a block down in the grass, with an open pit beside it, so the flight was built for ground one block lower than the world's. Not diagnosed (same CLI limit as 02.42.59). Suspects: the claimed air over every step and door approach (6.13 STEPBURY's clear volume) digging out the cell in front of the foot, or the planner's ground at the foot disagreeing with the world. It wasn't tagged and you can still walk it, so it went to the Polish backlog. A related line from 6.19 PORCHSTEP (a door in a pit below the street) is already there.

![[2026-10-31_02.50.07.jpg|800]]
another big stair in the road. This path was going so well!
  - `2632 105 -1604` yaw -3092 pitch 37; forest; settlement s2_m2 (village); district d16_m11; plaza p0 (green)
  - @assistant 2026-10-31: The village path by green p0 steps down in full blocks. Covered by 7.11 part 1 (smooth roads, DS-31: a slab before each one-block rise). Filed in the Backlog (unscheduled) as more evidence. Note that DS-31 lets dirt and gravel tracks keep full steps; the open DS-54 narrowing question in [QUESTIONS](../../../QUESTIONS.md) decides whether paths like this one count.

![[2026-10-31_03.00.46.jpg|800]]
that's a deep cut #backlog
  - `3580 98 -1284` yaw -45 pitch -17; taiga; settlement s4_m2 (metropolis); district d22_m9; street r3_m2_2.1 (main, width 9)
  - @assistant 2026-10-31: Backlog (unscheduled), as you tagged it. A main street (r3_m2_2.1, 9 wide) cut deep into the hill, with raw earth and stone faces on both sides. Covered by 7.19 FALLSTREETS (DS-46: a street stays within 2 of the ground, 4 at most; a stretch with more cut or fill gets no lots and a faced retaining wall, never a raw face). That's after the release.

![[2026-10-31_03.02.36.jpg|800]]
I came in here on a road from out of town and I can't get to any highrises because they're outside this deep cut. #backlog
  - `3779 98 -1514` yaw 14 pitch -22; snowy_taiga; settlement s4_m2 (metropolis); district d23_m10; street szm1512_3696 (street, width 5)
  - @assistant 2026-10-31: Backlog (unscheduled), as you tagged it, under 7.19 FALLSTREETS like 03.00.46. Being stuck in a cut is worse than a looks problem, so I've asked in [QUESTIONS](../../../QUESTIONS.md) whether you want a cheap P6 stopgap (a stair out of any street cut deeper than about 3 blocks) or leave it to 7.19.

![[2026-10-31_03.05.49.jpg|800]]
sunset on a clear winter day in the city #hype
  - `4100 124 -2071` yaw -91 pitch 9; snowy_taiga; settlement s4_m2 (metropolis); district d25_m14; street sx4102_m2136 (street, width 5)
  - @assistant 2026-10-31: Added to [hype.md](../hype.md).

![[2026-10-31_03.08.49.jpg|800]]
Sunset view from my penthouse room. #hype
  - `4201 280 -1105` yaw -114 pitch 29; snowy_taiga; looking at glass_pane `4202 281 -1105`; settlement s4_m2 (metropolis); district d26_m7; lot d26_m7.l2; building b2 (highrise, tall-stone, seed -5447130581950790135, 45 storeys); room r508 (flat, storey 44)
  - @assistant 2026-10-31: Added to [hype.md](../hype.md).

![[2026-10-31_03.10.40.jpg|800]]
I like the triple doors. I don't like this staircase. Seems like we should get stairs working. #backlog-p6
  - `4184 95 -1114` yaw -436 pitch 43; snowy_plains; looking at polished_andesite `4187 94 -1114`; settlement s4_m2 (metropolis); district d26_m7; lot d26_m7.l2; building b2 (highrise, tall-stone, seed -5447130581950790135, 45 storeys); street a1m7_4176 (arterial, width 11)
  - @assistant 2026-10-31: New P6 bug: **6.31 STAIRJOG**, one item for this note and the next two. I rebuilt this entrance in the planner on the terrain dump of your world. It's lot d26_m7.l2, a high-rise with the same building seed. The triple door (4189-4191, -1112) faces north and is 6 blocks above the street, with only two rows of lot in front of it. The flight comes off the door's landing along the facade for two steps, then jogs out into the row on the lot edge, turning once more after a single step. A rail post stands next to the turn. The DS-45 amendment you approved asks for turns at least 2 risers apart and no rail post in a landing cell; no check enforces it yet. The planner itself flags nothing here.

![[2026-10-31_03.12.04.jpg|800]]
I think the landing just needs a little more room. That's not enough room to fit the stairs. #backlog-p6
  - `4186 98 -1113` yaw -443 pitch 52; snowy_plains; settlement s4_m2 (metropolis); district d26_m7; lot d26_m7.l2; building b2 (highrise, tall-stone, seed -5447130581950790135, 45 storeys)
  - @assistant 2026-10-31: Same item, 6.31 STAIRJOG. The landing is only as wide as the door, and the turn partway down has a rail post beside it.

![[2026-10-31_03.14.45.jpg|800]]
starting the stairs a little further right of the door makes it at least usable. If this is a cheap fix, lets do it for now, if it's cheaper than a more expansive front/landing area. #backlog-p6
  - `4183 95 -1115` yaw -415 pitch 33; snowy_plains; settlement s4_m2 (metropolis); district d26_m7; street a1m7_4176 (arterial, width 11)
  - @assistant 2026-10-31: Same item, 6.31 STAIRJOG. Your fix is written into the item as the cheap fix to try first: start the flight one cell further along, so it runs straight down along the facade with a landing between it and the door. In the planner rebuild the row next to the wall looks long enough for that: 7 cells west of the door for 6 risers and one rest step (not tried). Setting the building back for a deeper landing is the alternative, if the cheap fix doesn't hold up.

![[2026-10-31_03.16.21.jpg|800]]
a building of this enormity would have stairs (if any) more like this. Consult architect. Highrises also have many entrances. #backlog #ideas
  - `4186 95 -1123` yaw -382 pitch 16; snowy_plains; settlement s4_m2 (metropolis); district d26_m8; street a1m7_4176 (arterial, width 11)
  - @assistant 2026-10-31: Backlog (unscheduled), and a question for the architect. If I read the shot right, you built the wide flight in the middle as a mock-up, and the generated flight is the narrow one on the right (the planner rebuild has only that one). A grand straight flight needs room in front that this lot doesn't have (two rows), so the tower would have to set back, or the stair would run into the street. On many entrances: the approved DS-35 already asks high-rises for a door every 12-20 blocks of a long frontage, but that clause isn't built yet (this tower has two doors: the triple one and one on the west side).

![[2026-10-31_03.19.49.jpg|800]]
I'm walkin on sunshine #hype
  - `3923 66 -954` yaw -188 pitch 29; forest; settlement s4_m2 (metropolis); district d24_m6
  - @assistant 2026-10-31: Added to [hype.md](../hype.md).

![[2026-10-31_03.20.42.jpg|800]]
there are pockets of some pretty amazing stuff. Surrounded by rats nests of raised roads and deep road cuts you can't get out of. #hype
  - `3913 77 -924` yaw -152 pitch 12; forest; settlement s4_m2 (metropolis); district d24_m6
  - @assistant 2026-10-31: Added to [hype.md](../hype.md). The raised roads and deep cuts around it are 7.19 FALLSTREETS (after the release).

![[2026-10-31_03.26.16.jpg|800]]
look at that grove of trees! That's a nice neighborhood. #hype
  - `-1945 131 -1242` yaw -880 pitch 29; river; settlement sm3_m2 (metropolis); district dm13_m8
  - @assistant 2026-10-31: Added to [hype.md](../hype.md).

![[2026-10-31_03.26.54.jpg|800]]
this highrise didn't install anything to tie up your horse. #hype #joke
  - `-2048 76 -1373` yaw -964 pitch 18; sunflower_plains; settlement sm3_m2 (metropolis); district dm13_m9
  - @assistant 2026-10-31: Added to [hype.md](../hype.md). Hitching posts (a fence post by the door) would make a nice small piece for 7.5 Furnishings or street furniture. Not filed.

![[2026-10-31_03.29.22.jpg|800]]
now THIS road coming out of sm3_0 is spot on. #hype
  - `-2820 114 827` yaw -681 pitch 28; meadow; outside any settlement
  - @assistant 2026-10-31: Added to [hype.md](../hype.md).

![[2026-10-31_03.33.50.jpg|800]]
these roads do some odd pathing, crisscrossing back and forth. I imagine they weren't really supposed to be near each other, but the tall mountains caused A* to path them in the same area. Ideal if we could detect and merge. #backlog #roads
  - `-2796 140 1544` yaw -408 pitch 37; birch_forest; outside any settlement
  - @assistant 2026-10-31: Backlog (unscheduled), as you tagged it. Each road between settlements is routed on its own and cached per link, so two links squeezed into the same valley by the mountains know nothing of each other, and nothing merges them. Possible fixes: make cells of an already-routed link cheaper for the router (shared corridors), or merge near-parallel stretches after routing. Either way one link's route would then depend on the others, which touches the determinism design of ADR 0004 (roads between settlements), so it's a design item for later.
