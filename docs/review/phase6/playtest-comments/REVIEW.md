---
status: Ready for review
author: John
commit: e237e451
seed: 1709797029209580947
---

# Gameplay Notes and Commentary
Playtest on 2026-10-26
Build commit: e237e451

These are just some things I noticed, mostly pointing out issues during gameplay. My process was to walk around admiring the amazing work and then typing some text on the screen and taking a screenshot if I noticed something.
When I thought it might be particularly relevant, I included the seed and/or coordinates (by teleporting to ~ ~ ~), and running /seed. I still have the map at /home/agent/kaiju-survival-server (for now) if that is needed for some reason.
The images exist at 720p so that an agent can take a look if necessary.

Many items are instances of the same general problem: stairs not accessible.

Please turn these into issues to track as bug fixes or improvements (blocking an entry is a bug, adding detail would be an improvement, etc).


![[2026-10-26_18.17.09.jpg|800]]
No comment, just a beautiful night time city scape.


![[2026-10-26_19.45.26.jpg|800]]
Weird piece of glass sticking out of the wall, and sand blocking the stairs.
  - @assistant 2026-10-26: sand in the flight: bug 6.13 STEPBURY (terrain adaptation fills up toward the slab, nothing keeps the air over the steps). Glass stub: polish line GLASSSTUB, not diagnosed yet.


![[2026-10-26_19.52.00.jpg|800]]
comment: stairs completely blocked
  - @assistant 2026-10-26: bug 6.13 STEPBURY.


![[2026-10-26_19.52.17.jpg|800]]
comment: stairs don't go to top
  - @assistant 2026-10-26: bug 6.12 STEPTOP: the step in front of the door is laid as a full block, so the last stair is a block short. Reproduced in the CLI (this high-rise, lot d7_15.l0).


![[2026-10-26_19.53.20.jpg|800]]
comment: redundant lamp on the ground. This is not the
only case, I think there is also a redundant lamp on the ground in the little closet/shed on the top of highrises. There is also a hanging lamp so the one on the ground seems unnecessary. But if there was a creative purpose here, let me know.
  - @assistant 2026-10-26: no creative purpose: every high-rise room gets a standing lantern and a hung one. Polish line LAMPS (drop the standing one). The lantern on the lift-shaft floor is meant (pit light).


![[2026-10-26_19.54.17.jpg|800]]
comment: What is this room? It is is inaccessible but has a
window (this is in a highrise)
coordinates: 1266.012087, 79.000000, 2411.709601
Seed: [1709797029209580947]
  - @assistant 2026-10-26: the lift shaft (your next note); its glass doorway is the window. No issue.


![[2026-10-26_19.56.25.jpg|800]]
comment: nevermind, the previous inaccessible room is an
elevator shaft! Love it.
  - @assistant 2026-10-26: yes, the lift shaft (7.13 B3a), glass doorway on every storey. No issue.


![[2026-10-26_19.59.37.jpg|800]]
comment: facades make sense being brick. Don't interiors
usually get filled in with lighter materials? Maybe a heavy
elevator shaft but plaster walls. Architect decides
  - @assistant 2026-10-26: design question for the architect (light partitions vs brick, heavy core); the coordinator queues it.


![[2026-10-26_20.02.15.jpg|800]]
comment: not really walkable. Interior designer should be
able to look at room plans and determine what makes sense.
  - @assistant 2026-10-26: design question for the interior designer (clearance rules for furniture); the coordinator queues it.


![[2026-10-26_20.34.23.jpg|800]]
comment: Just a nice city view above the clouds.


![[2026-10-26_20.38.11.jpg|800]]
coordinates: 2721.201379, 66.937500, 2696.122407
comment: missing stairs
  - @assistant 2026-10-26: bug 6.12 STEPTOP: reproduced on this street (shop at 2725,68,2689): the landing is full blocks behind a railed kerb, and the one stair sits in the ground layer.


![[2026-10-26_20.38.28.jpg|800]]
comment: missing stairs
  - @assistant 2026-10-26: bug 6.12 STEPTOP.


![[2026-10-26_20.38.41.jpg|800]]
comment: missing stairs
  - @assistant 2026-10-26: bug 6.12 STEPTOP.


![[2026-10-26_20.41.01.jpg|800]]
comment: stair is buried. Looks like it is another case of
not going to the top.
  - @assistant 2026-10-26: bug 6.12 STEPTOP (the bottom stair sits in the ground layer, the top one is a full block).


![[2026-10-26_20.43.52.jpg|800]]
comment: foliage blocks door, stairs inaccessible. Store
front?
  - @assistant 2026-10-26: tree at the door: bugs 6.13 STEPBURY (nothing keeps the approach clear) and 6.14 TREEIN. The step: 6.12 STEPTOP. It looks like a shop front (display windows); not checked, no coordinates.


![[2026-10-26_20.44.34.jpg|800]]
comment: door on glass. The panes don't connect. We
*could* use glass blocks, or just require a frame.
  - @assistant 2026-10-26: polish line DOORGLASS; your two options go to the architect.


![[2026-10-26_20.49.13.jpg|800]]
comment: Another case of door not attaching to glass.
  - @assistant 2026-10-26: polish line DOORGLASS.


![[2026-10-26_20.50.01.jpg|800]]
comment: Possible solution? Glass blocks only to fill in that
gap?
  - @assistant 2026-10-26: noted as an option in DOORGLASS.


![[2026-10-26_21.05.45.jpg|800]]
comment: I love these dormers! Try stairs like on the left (I
added those)
  - @assistant 2026-10-26: polish line DORMER stairs; dormer roofs from stairs like yours go to the architect as a question.


![[2026-10-26_21.06.41.jpg|800]]



![[2026-10-26_21.07.06.jpg|800]]
comment: Beautiful Cityscape


![[2026-10-26_21.10.29.jpg|800]]



![[2026-10-26_21.10.37.jpg|800]]
Saved screenshot as 2026-10-26_21.10.29.jpg|800
comment: Leaves in the house!
  - @assistant 2026-10-26: bug 6.14 TREEIN: trees from neighbouring chunks grow into building air after the buildings are placed.


![[2026-10-26_21.12.13.jpg|800]]
comment: The road doesn't connect
Seed: [1709797029209580947]
coordinates: 3267.868662, 120.984962, 2008.891760
  - @assistant 2026-10-26: bug 6.16 DEADMAIN: reproduced in the CLI, a metropolis main road ends at 3264,1984 on a bridge segment, 48 blocks from the nearest street.


![[2026-10-26_21.17.41.jpg|800]]
comment: can we do anything about this floating snow and
foliage above a road between settlements?
  - @assistant 2026-10-26: bug 6.17 ROADCLEAR (cause not confirmed: late tree crowns from neighbour chunks, or the road's clear stopping below the real terrain).


![[2026-10-26_21.19.18.jpg|800]]
comment: A tiny town with a road that was clearly going
here, but the road stops well short.
  - @assistant 2026-10-26: bug 6.15 ROADJOIN.


![[2026-10-26_21.26.27.jpg|800]]
comment: A little spot where roads didn't meet up
Seed: [1709797029209580947]
coordinates: 3623.131891, 74.501760, 1443.828721
  - @assistant 2026-10-26: bug 6.15 ROADJOIN: this is the hamlet's exit gate (3616,1440 in the CLI plan); the link road starts about 100 blocks away and nothing plans one height for the join.


![[2026-10-26_21.27.38.jpg|800]]
comment: Workshop needs a back door to the "lean to"
  - @assistant 2026-10-26: polish line WORKSHOP lean-to door.


![[2026-10-26_21.29.09.jpg|800]]
comment: Maybe a tiny hamlet like this on a road between
cities should have an inn bias? Turns out there is 1 bed in
that tiny house.
  - @assistant 2026-10-26: design question (architect and interior designer): an inn bias for small hamlets on roads between settlements; the coordinator queues it.


![[2026-10-26_21.29.47.jpg|800]]
comment: Maybe a tiny hamlet like this on a road between
cities should have an inn bias? Turns out there is 1 bed in
each of those tiny houses. Maybe two hermits with detached
workshops?
  - @assistant 2026-10-26: same design question as above.


![[2026-10-26_21.31.49.jpg|800]]
comment: bridge on road between settlements, don't meet up
here
coordinates: 3201.745461, 69.993137, 1683.876599
  - @assistant 2026-10-26: bug 6.15 ROADJOIN.
Seed: [1709797029209580947]


![[2026-10-26_21.32.46.jpg|800]]
comment: The transition from tiny path to this huge road is
pretty abrupt. Maybe the huge road just shouldn't be that big
for that town?
  - @assistant 2026-10-26: design question for the architect (how a road narrows into a small town); the coordinator queues it.


![[2026-10-26_21.35.56.jpg|800]]
comment: Bridge going right into a berm. Wall of dirt blocks,
but there is a road it was meant to connect to
  - @assistant 2026-10-26: bug 6.15 ROADJOIN. Your screenshot 21.36.39 ("Sorta looks like a landslide") is not in these notes; filed there too.



![[2026-10-26_21.37.41.jpg|800]]
comment: A tunnel! Is it by design? A tunnel should be lined
with concrete or bricks (actually, get architecture advice)
  - @assistant 2026-10-26: not by design: bug 6.17 ROADCLEAR (the road's clear stops below the hill). Lining, if we keep tunnels: design question for the architect.
  

![[2026-10-26_21.38.53.jpg|800]]
comment: This elaborate staircase looks pretty good but
it's hard to walk up it. Probably needs to be more generous on
the switchbacks.
  - @assistant 2026-10-26: polish line SWITCHBACK, design question for the architect (minimum straight run between turns); 6.12 STEPTOP may be part of why it is hard to walk.

    
![[2026-10-26_21.42.16.jpg|800]]



![[2026-10-26_21.43.02.jpg|800]]
comment: HOA had a "pink houses" rule? (I just liked this, the sunrise made the white blocks look pink).


![[2026-10-26_21.45.19.jpg|800]]
comment: Ocean front property is at a premium!
