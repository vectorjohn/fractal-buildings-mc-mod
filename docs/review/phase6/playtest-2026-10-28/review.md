---
status: Ready for review
author: John
commit: b0288bd0b72ddb0e7b2d1f9bc2e9d194053042f7
seed: 1573336981619055086
---
# Gameplay Notes and Commentary
Playtest on 2026-10-27 and 2026-10-28

**Tagging**
I put some tags in the captions on images. Maybe I’ll try to make this more formal at some point:

* backlog-p6: P6 backlog item
* backlog: General unordered backlog item
* backlog-polish: maybe a minor or cosmetic backlog. Maybe polish goes away and becomes a specific size or priority.
* hype: images that might be nice in a gallery on a mod hosting page.
* ideas: deep thoughts (ironic) - it’s just there.
* interior: things about the interior
* walkability: things that hinder actually walking around that wouldn’t be like that in real life.

            
![[images/2026-10-27_17.46.21.jpg|800]]
<!--
<Player19> nice and flush but missing any stairs at all
Seed: [1573336981619055086]
Teleported Player19 to 361.520071, 66.000000, 335.148041
#backlog-p6
-->
  - @assistant 2026-10-29: bug 6.19 PORCHSTEP.


![[images/2026-10-27_17.47.56.jpg|800]]
<!--
<Player19> Is this an intentional road cut? The road is at a
lower grade from the dirt. That's realistic. But, with Minecraft
having 1m blocks maybe it should have steps or a slab down to
road level
Teleported Player19 to 373.808631, 66.000000, 334.089915

#backlog
-->
  - @assistant 2026-10-29: yes, an intentional road cut (terraced cuts, ADR 0004 "TC"). Steps or a slab down to the road: unscheduled backlog, part of 7.11 part 1 (smooth roads, DS-31).


![[images/2026-10-27_17.50.31.jpg|800]]
<!--
<Player19> These roads look nice. I think mostly straight in
cities is just a reality of minecraft. They're looking nice and
clear.
Teleported Player19 to 634.022763, 101.870392, 305.246398
#hype
-->
  - @assistant 2026-10-29: in [hype.md](../hype.md).


![[images/2026-10-27_18.39.40.jpg|800]]
<!--
<Player19> Yeah just generally missing stairs on porches now.
#backlog-p6
-->
  - @assistant 2026-10-29: bug 6.19 PORCHSTEP.


![[images/2026-10-27_18.40.50.jpg|800]]
<!--
<Player19> I love how the leaves have cleared the road but
still hang over it like this.
#hype
-->
  - @assistant 2026-10-29: in [hype.md](../hype.md).


![[images/2026-10-27_18.42.21.jpg|800]]
<!--
<Player19> Slabs would make roads a lot easier to walk up.
#backlog
-->
  - @assistant 2026-10-29: unscheduled backlog; it is 7.11 part 1 (DS-31 smooth roads), which the release plan suggests for the release train.


![[images/2026-10-27_18.43.43.jpg|800]]
<!--
<Player19> Dangerous door. Might want to consider safety.
Generally doing something about open stairwells like this. It is
convenient to use in game though, so maybe not worry for now.
#backlog
-->
  - @assistant 2026-10-29: unscheduled backlog (open stairwell beside a door).


![[images/2026-10-27_18.45.46.jpg|800]]
<!--
<Player19> This looks like a balcony, and is very nice. Is it
intentionally a balcony? If so we need doors. If not, well, it
looks good anyway.
#hype
-->
  - @assistant 2026-10-29: in [hype.md](../hype.md). Most likely the roof of a lower setback volume with its DS-38 parapet, not a planned balcony, so no door leads to it (not checked in the planner). Whether setback terraces should get a door is a taste question; the coordinator routes it to the architect.


![[images/2026-10-27_18.52.25.jpg|800]]
<!--
Teleported Player19 to 1049.700000, 133.000000, 915.397559
<Player19> stair too close to the drop off. You need to be
able to walk to it.
#backlog
-->
  - @assistant 2026-10-29: bug 6.21 STAIRFOOT.


![[images/2026-10-27_18.59.54.jpg|800]]
<!--
<Player19> diverse dense metropolis highrise. Looks good.
#hype
-->
  - @assistant 2026-10-29: in [hype.md](../hype.md).


![[images/2026-10-27_19.02.02.jpg|800]]
<!--
Teleported Player19 to -2453.128429, 126.102351,
-1312.819695
<Player19> Dizzying in a way! We might want to stamp out floors
but allow for variations in the room furniture. That said, real
apartments can be like that.
#hype #ideas
-->
  - @assistant 2026-10-29: in [hype.md](../hype.md). The idea (one floor plan per tower, furniture varies) is the subject of 7.13 (DS-32 high-rise floor programme).


![[images/2026-10-27_19.02.53.jpg|800]]
<!--
<Player19> Mass timber! In places where lumber is plentiful,
modern materials allow for wooden highrises.
Teleported Player19 to -2455.905151, 130.219911,
-1283.414678
#hype
-->
  - @assistant 2026-10-29: in [hype.md](../hype.md). Mass timber is 7.7.


![[images/2026-10-27_19.03.47.jpg|800]]
<!--
Nice view with water features.
#hype
-->
  - @assistant 2026-10-29: in [hype.md](../hype.md).


![[images/2026-10-27_19.03.48_2.jpg|800]]
<!--
Admiring the river through a canyon of highrises and a small bridge.
#hype
-->
  - @assistant 2026-10-29: in [hype.md](../hype.md).


![[images/2026-10-27_19.03.48.jpg|800]]
<!--
View of interesting water features in dense metropolis.
#hype
-->
  - @assistant 2026-10-29: in [hype.md](../hype.md).



![[images/2026-10-28_20.02.18.jpg|800]]
<!--
<Player443> This is a difficult piece of terrain to go through,
but we shouldn't have roads to nowhere. Especially with such a
highly engineered bridge leading to it.
<Player443> build b0288bd0b
Teleported Player443 to 3542.440747, 116.915983,
-2186.641596
Seed: [1573336981619055086]
#backlog
-->
  - @assistant 2026-10-29: bug 6.22 DEADPORT (the `dead-end-port` case of 6.15).


![[images/2026-10-28_20.04.35.jpg|800]]
<!--
XYZ: 3565.776 / 95.91598 / -2196.192
Nice bridge, but it’s missing those supports at the bottom.
#backlog
-->
  - @assistant 2026-10-29: unscheduled backlog; piers and abutments are 7.3 Bridges. Today a crossing is one span with no piers by design (ADR 0004 WR2).
    - @John 2026-10-29: ok yes, no piers by design but I can see tiny little pier stumps there. Something tried to make piers but didn’t connect them. Still an acceptable bug though.


![[images/2026-10-28_20.05.40.jpg|800]]
<!--
XYZ: 3650.400 / 137.15465 / -1970.248   
Just curious what caused that long road away and then back to the village on the right. I think I would expect the road to cross right there. But is this a result of limiting the length of crossings?
#backlog
-->
  - @assistant 2026-10-29: unscheduled backlog. Probably yes: a crossing may be at most 20 blocks wet (ADR 0004 WR2, DS-19), and the router also charges extra next to water, so a wider river is routed round or the link is dropped. Not checked for this road.


![[images/2026-10-28_20.07.31.jpg|800]]
<!--
XYZ: 3809.715 / 126.28532 / -1752.938 
In cases like this where we have to fill in for junctions, we should do pillars for support instead. Make it look deliberate. I mean, that’s what people would actually do in this situation (if they wanted a road to go up there).

Also, what is that grid-like pattern of melted snow on the road?
#backlog
-->
  - @assistant 2026-10-29: unscheduled backlog; streets on falling ground are 7.19 FALLSTREETS (DS-46), evidence added there. The snow grid: cause unknown, not diagnosed.



![[images/2026-10-28_20.08.51.jpg|800]]
<!--
XYZ: 3886.260 / 110.16033 / -1585.554 

Do we save, post generation, any debugging info about what kind of buildings we made at a position? We could save a “parallel world file” that is just a DB of coordinates to structures, so that we could know from coordinates what a building is. But that might be overkill if there is a better way. I don’t want to move heaven and earth to answer that question, but “what am I looking at” might be useful debug info?

Just curious what this building is, I like it. 
#hype
-->
  - @assistant 2026-10-29: in [hype.md](../hype.md). Yes, for singleplayer: `/fbnote <comment>` (FBNOTE, [PLAYTEST.md](../../../PLAYTEST.md)) saves a screenshot plus a JSON with the planner's settlement, lot, building and room at the targeted block. Nothing is saved after generation; the planner answers from the seed and position at the time.


![[images/2026-10-28_20.10.04.jpg|800]]
<!--
XYZ: 3858.736 / 93.00000 / -1557.948     

Pretty big miss on the street level doors. The door is a few blocks below road level and 1 block of space in front of it. A second story entrance would be acceptable, with an appropriate attachment to the road if that’s doable. Otherwise, just don’t put doors underground.
#backlog-p6
-->
  - @assistant 2026-10-29: bug 6.20 DOORBANK.



![[images/2026-10-28_20.14.21.jpg|800]]
<!--
XYZ: 3629.969 / 160.86814 / -1223.211

I’m just curious what leads to this really deep cut. Is the planner missing information that could help to route around tall hills like this? This might be a reason to do tunnels sonner. Just a question, and likely work to put in the backlog for a later phase.
#backlog #question
-->
  - @assistant 2026-10-29: unscheduled backlog. The router does see the hills, coarsely: A* runs on a 64-block lattice of exact heights and charges 60 per block of rise against 640 per straight lattice step, so a 10-block climb costs about one extra step. A hill narrower than 64 blocks between lattice nodes is invisible to it, and string-pulling then straightens the line across it; the cut is what is left. Tunnels are ruled out for now (DS-50).
    - @John 2026-10-29: fascinating. So, what are the implications of getting a finer reading? Like, 64 blocks is enough to miss mountains at MC scales. If it was 8 I’m sure that would get it. I seem to remember there being a stage where we were doing many exact terrain reads for roads, which are costly. So that must be separate from this 64 block number. I just wonder if we could get away with a little more detail or quickly make the plan at 64 blocks, then take a finer reading (still very course, like sampling along the path of the road) to look for missed obstacles. Then reroute. I played years ago with an algorithm called D*Lite (https://github.com/Sollimann/Dstar-lite-pathplanner is one example implementation but it also links the paper). I have been wondering if it would come in handy because we kind of have the problem that we are route planning `A*` but we’re kind of blind and need to re-route cheaply with imperfect knowledge, which I think is the main point of Dstar lite. Might be worth a short research spike into feasibility and if it would be useful, unless someone can already say it won’t.


![[images/2026-10-28_20.15.58.jpg|800]]
<!--
XYZ: 3462.839 / 139.36247 / -1329.511

Just kinda some road nonsense. Weird loop. Was it trying to do a little town? I could look at it as a pulloff for a scenic viewpoint.
#backlog
-->
  - @assistant 2026-10-29: unscheduled backlog (cause not diagnosed).


![[images/2026-10-28_20.16.21.jpg|800]]
<!--
XYZ: 3488.033 / 98.00000 / -1346.978

Still some floating foliage above a road.
#backlog
-->
  - @assistant 2026-10-29: unscheduled backlog. This breaks DS-20 (no floating foliage over roads, accepted); the coordinator asks whether it should be a P6 bug.


![[images/2026-10-28_20.17.47.jpg|800]]
<!--
XYZ: 3438.795 / 100.11650 / -1373.534

We have stairs but a full block drop to the road. Is this an intentional “curb”? I would like as much as possible for the “built environment” to be walkable by the player.
#backlog #walkability
-->
  - @assistant 2026-10-29: filed with bug 6.19 PORCHSTEP as evidence: a flight whose foot is a block above the road breaks DS-4 (steps reach the ground) and probably shares PORCHSTEP's cause. Not intentional: there is no kerb rule for lot flights.


![[images/2026-10-28_20.18.20.jpg|800]]
<!--
XYZ: 3450.243 / 108.74150 / -1389.871

Something tells me that tree didn’t just grow there naturally (a floating tree above a road).
#backlog #floating
-->
  - @assistant 2026-10-29: unscheduled backlog. Breaks DS-20, like 20.16.21; same question to you.


![[images/2026-10-28_20.25.33.jpg|800]]
<!--
XYZ: 3386.487 / 147.36650 / -1316.840

Long, light gray road/path to nowhere. It’s a nice crossing though.
#polish-backlog
-->
  - @assistant 2026-10-29: Polish backlog.


![[images/2026-10-28_20.27.11.jpg|800]]
<!--
XYZ: 3600.018 / 168.67658 / -1229.758

Couple things: 1. Maybe a “rural” path out of town doesn’t have to be so straight. Bezier curves or something? Or some more natural curves? This sharp corner is a little jarring. #backlog
-->
  - @assistant 2026-10-29: unscheduled backlog. Roads between settlements are already shaped with Catmull-Rom curves through the lattice path (ADR 0004 N4); a sharp corner means two lattice legs met at a steep angle, and the curve tension can be raised.


![[images/2026-10-28_20.31.24.jpg|800]]
<!--
XYZ: 3600.018 / 168.67658 / -1229.758

2. It was mentioned that we sample the noise function for roads along their paths. Could we do a *very course and cheap* sample of a wide area of terrain between cities and use it as a rough height map to aid in routing a road to avoid winding over mountains like this? Maybe we already are?
  #backlog 
-->
  - @assistant 2026-10-29: unscheduled backlog. We already do this: see the answer at 20.14.21 (a 64-block lattice of heights, slope charged in the cost). Finer sampling or a heavier slope weight are the knobs; 7.16 adds a slope term to settlement siting.


![[images/2026-10-28_20.31.58.jpg|800]]
<!--
XYZ: 3600.018 / 168.67658 / -1229.758
@coordinator can I get a high level step by step big picture description of how we plan roads? Or just point me to the file where I missed it. 
-->
  - @assistant 2026-10-29: the step-by-step is [ADR 0004](../../../adr/0004-roads-between-settlements.md), "Decision": (1) settlements are nodes, a pair is linked when their rims are within 1,536 blocks and no third settlement sits between them (N1); touching pairs share a meeting point (N2); (2) each link gets a port on each rim facing the other end (N3); (3) A* on a 64-block lattice between ports, slope and shore cost, water forbidden (N4); (4) string-pull, Catmull-Rom curve, then a dry check every 8 blocks with at most 3 reroutes; wet runs up to 20 blocks become one-span crossings, longer means no road (WR1, WR2); (5) the deck height is smoothed over the stations with a grade limit (HT); (6) inside the settlement, streets come from the skeleton and the approach joins a street exit to the port (N5, 6.15 ROADJOIN); (7) in game one feature per chunk draws its columns with terraced cuts and embankments (MF, TC).
    - @John 2026-10-29: TODO to myself, no action needed.


![[images/2026-10-28_20.33.42.jpg|800]]
<!--
XYZ: 3660.891 / 103.34835 / -1148.418

I really like this road along the river at the base of a mountain. We have these all over where I live. The road could look a little more organic (curve). I think despite Minecraft’s blocky nature, a curved road could look pretty good.
#backlog-polish
-->
  - @assistant 2026-10-29: Polish backlog.


![[images/2026-10-28_20.35.42.jpg|800]]
<!--
XYZ: 3836.612 / 74.00000 / -1217.826

I think we want to consider some lots having buildings NOT right up against the road. Like a small front yard or parking strip. Especially in rural areas like this was taken. I think it would be on a neighborhood-by-neighborhood basis - not a universal rule, and not a random chance per house, but some areas have yards, some build up close to the road. 
#backlog
-->
  - @assistant 2026-10-29: unscheduled backlog.


![[images/2026-10-28_20.36.04.jpg|800]]
<!--
XYZ: 3824.873 / 76.29433 / -1215.022
Still missing stairs into the house.
#backlog-p6
-->
  - @assistant 2026-10-29: bug 6.19 PORCHSTEP (evidence added).


![[images/2026-10-28_20.36.35.jpg|800]]
<!--
XYZ: 3824.873 / 76.29433 / -1215.022
**Rule proposal:** the built environment should be walkable by the player without jumping.
-->
  - @assistant 2026-10-29: filed as a proposed rule, DS-54 "The built environment is walkable without jumping" in [STANDARDS.md](../../../design/STANDARDS.md) ("Proposed" section). It would make every one-block step on a road, stoop or kerb a violation, so it waits for you to accept it or narrow its scope.


![[images/2026-10-28_20.37.46.jpg|800]]
<!--
XYZ: 3831.993 / 74.99143 / -1223.178
The top of this hill on the road doesn’t look right. Too steep because it’s only one block, it looks like a wall.
#backlog-polish
-->
  - @assistant 2026-10-29: Polish backlog.


![[images/2026-10-28_20.42.25.jpg|800]]
<!--
XYZ: 3881.772 / 105.65226 / -1241.223
Two roads to nowhere including a pretty large one. We should get architectural advice on how we should end roads realistically (this might have already been researched).
#backlog
-->
  - @assistant 2026-10-29: unscheduled backlog; also listed under 6.22 DEADPORT to check for the same cause. Ending roads: no research note on road ends yet; the coordinator can ask the architect.


![[images/2026-10-28_20.43.12.jpg|800]]
<!--
XYZ: 3963.393 / 167.52159 / -1185.807
Maybe that lower road was meant to meet up with this city on the left, but couldn’t cross this terrain so the planner just stopped?
Either way, it looks like an intentional, arterial road that abruptly just stops with nothing around it. And an odd smaller road crossing it pointlessly. More road cleanup.
#backlog
-->
  - @assistant 2026-10-29: unscheduled backlog; also listed under 6.22 DEADPORT, same as 20.42.25.


![[images/2026-10-28_20.44.19.jpg|800]]
<!--
XYZ: 4007.161 / 214.77160 / -1085.215

I love how these biomes meet with the snow half way on the house.
#hype
-->
  - @assistant 2026-10-29: in [hype.md](../hype.md).


![[images/2026-10-28_20.50.53.jpg|800]]
<!--
XYZ: 3922.421 / 79.00000 / -885.281

Picturesque. Too bad I left debug on.

#hype
-->
  - @assistant 2026-10-29: in [hype.md](../hype.md).


![[images/2026-10-28_20.52.10.jpg|800]]
<!--
XYZ: 3924.877 / 63.00000 / -960.322

Overgrown shrubbery! (tree blocking the entrance). It’s a bug, but I could live with it.
#backlog-polish
-->
  - @assistant 2026-10-29: Polish backlog.


![[images/2026-10-28_21.00.05.jpg|800]]
<!--
XYZ: 4337.044 / 86.49280 / -981.819

I recognize that stair steps for roofs won’t be possible to make this steep angle (2:1), so this is an exception among others to stepped roofs. 
Also, Frank Lloyd Wright style roofs could be interesting. #backlog
-->
  - @assistant 2026-10-29: unscheduled backlog.


![[images/2026-10-28_21.01.30.jpg|800]]
<!--
XYZ: 4492.645 / 68.15874 / -950.794

Is this a deliberate dock or happy accident? A dock should have pillars supporting it though, so not quite right either way.

#backlog-polish
-->
  - @assistant 2026-10-29: Polish backlog. Not a dock: there are no docks yet (7.4 Port towns). It looks like a crossing deck or quay edge that ends in open water; not diagnosed.


![[images/2026-10-28_21.02.13.jpg|800]]
<!--
<Player443> Ice skating at sunset in the city

Ice skating at sunset in the city. #hype
-->
  - @assistant 2026-10-29: in [hype.md](../hype.md).


![[images/2026-10-28_21.02.41.jpg|800]]
<!--
XYZ: 4385.777 / 92.50510 / -1083.634

What is this square pad?
-->
  - @assistant 2026-10-29: not checked in the planner. It looks like a block of city streets with no buildings on it (snow on the empty cells, street lines between), which happens when every lot of a block is left empty (for example `empty:bank` on steep ground). `/fbnote` aimed at it would name the settlement and lot.


![[images/2026-10-28_21.04.02.jpg|800]]
<!--
XYZ: 4364.931 / 99.61336 / -1172.680

What exactly is causing this whole section of road to be so high off the ground? IRL, bad terrain might call for some of this kind of engineering, but I don’t see why it would be like this here.
#backlog
-->
  - @assistant 2026-10-29: unscheduled backlog. Most likely the junction field: each junction gets one height and every street must stay within the grade limit between junctions, so on falling ground whole streets are held up on fill. 7.19 FALLSTREETS (DS-46) fixes that; evidence added there.


![[images/2026-10-28_21.10.57.jpg|800]]
<!--
XYZ: 4199.331 / 144.53514 / -1298.468

A lot of these buildings effectively have an attic. Give them attic access like trapdoors, ladders, and in some cases stairs. This one is enormous! It would make the places feel more real.
#backlog #interior
-->
  - @assistant 2026-10-29: unscheduled backlog (attic access).


![[images/2026-10-28_21.12.44.jpg|800]]
<!--
XYZ: 4203.195 / 80.10019 / -1617.034
We could call this a warehouse! It’s even in a warehousey location. I could see having districts full of more industrial style construction.
#backlog
-->
  - @assistant 2026-10-29: unscheduled backlog (industrial districts).


![[images/2026-10-28_21.13.10.jpg|800]]
<!--
XYZ: 4241.344 / 102.97695 / -1630.917

<Player443> Another maze of bizarrely raised roads. I don’t know if they were deliberate, but they almost look like a raised road like exist in some real cities. Except for being solid all the way down. If instead they had some realistically placed piers that could look really good.
That said, this is a jumble and would still need straightening out.
#backlog
-->
  - @assistant 2026-10-29: unscheduled backlog; evidence added to 7.19 FALLSTREETS (piers instead of solid fill is the same note as 20.07.31).

