---
tags: research
track: spikes
date: "2026-10-29"
author: researcher
licence: MIT
---
# Road refine: finer terrain reads, reroute, D* Lite

Spike for John's question at 20.14.21 (playtest 2026-10-28): would a finer terrain reading, or a coarse plan followed by a reroute (D* Lite), avoid the deep road cuts? Throwaway code only. It is not on the branch: the patch is `.claude/reports/research-dstar-routing-spike.patch`. Measured on recorded terrain: `pt1573-metro-s8`, plus two step-8 dumps I made along 7 intercity links, kept in scratch and not committed. Nothing was run in game.

## Question

How do routes read terrain today, and why do they cut deep? Would a finer lattice, a refine-and-reroute pass, or D* Lite help? What would each cost?

## Evidence

- **How roads read terrain today.**
  - *Intercity links* (ADR 0004 N4, WR1, HT). A* runs on the 64-block region lattice (`RoadNet.buildRoute`). A step costs 640 (896 diagonal) plus 60 per block of rise, and the search has no grade limit. The path is then string-pulled: a chord is accepted when the snapped lattice nodes along it differ by at most 16 per step (`chordOk`, `PULL_STEP`).
  - The curve is then read exactly every 8 blocks (`Profile.build`: water and floor at each station, more reads at crossing ends). Those are the "exact terrain reads for roads" John remembers. They cost 408 reads per link on average (p95 1,059; `roads` report on pt1573). The perf metropolis needs 14,587 station columns, about 14 % of the cold region row (SPARSEREADS 4.5). The reads feed two things: the dry check, which can reroute up to 3 times by banning the 4 lattice nodes round a failing station, and the deck, a mean over ±8 stations limited to 1 in 4. The S10 spike also read the chords every 16 blocks (645 reads per link), and ADR 0004 dropped that.
  - *Streets inside a settlement* are a separate system. They route on the skeleton's 32-block lattice (`CityProgram.octilinear`, else `DefaultProgram.route`). A step is forbidden above 1 in 3 between nodes, and nothing between nodes is read. `JunctionField` and `Grading` then grade them with exact reads.
- **John's cut is a metropolis main road, not an intercity road.** It is `r3_m2_2` (MAIN, 9 wide); no intercity or approach column lies within 3 chunks. At 3600,-1264 the ground is 133 and the road is at 108: a 25-block cut. Two causes:
  - **Main cause: a junction pad pins a long stretch flat.** `r3_m2_2` and `r3_m2_3` are both routed from the hub, and they share about 400 blocks of columns. `JunctionField` treats that whole overlap as one pad and pins it flat at level 108 (ground median 114). So the road cuts 10 to 17 blocks everywhere on it, and 25 at the hump. The two deepest cuts in the metropolis come from the same pattern: `r4_m2_3` and `r4_m2_4` share about 150 blocks pinned at 94 under ground up to 142, a 47-block cut.
    - @John 2026-10-31: I don't know what you mean. What is pinned flat? The whole city’s roads? I need some kind of diagram to visualize. Ascii if it's that simple, but an image (linked) or even gif animation or some othet animating tool would help.
      - @assistant 2026-10-31: Not the whole city's roads: only the stretch where two main roads lie on top of each other. Everywhere else a road follows the ground as before.
        - **What a junction pad normally is.** Where two streets cross, the cells they share must have one surface, or one street would meet the other with a step. So the planner (`JunctionField`, 6.3 JUNCTION) takes the few blocks of each street whose 9-wide footprint touches the other street (the *pad*) and holds them at one height, the *junction level*. Each street then slopes away from the pad at most 1 in 3. A normal pad is short, about one road width:

          ```
                    |   |
                    |   |        === the pad: the blocks where the two
               -----+===+-----       footprints touch, about 9 x 9,
                    |   |            all at one height
                    |   |
          ```

        - **Why two roads become one giant pad.** `r3_m2_2` and `r3_m2_3` start at the same hub (3808,-1536, the hub of the city's western cells) and the route planner sends both down the same columns for about 420 blocks before they part. The rule "every block where one street's footprint touches the other's is pad" was written for crossings, so here *every* block of that shared stretch qualifies, and the whole 420-block stretch becomes one pad with one level. Plan view (north up, not to scale):

          ```
           r3_m2_3 goes on                              * hub (3808,-1536)
           north-west                                  ##
                 \                                    ##
                  \  they part                       ##
                   ##########                      ##     ## = both roads on
             loop  #        ####                 ##            the same columns:
            r3_m2_2           ####             ##              one pad, held
            turns               ####         ##                at y = 108
            south                 ####     ##
            and east                ####  ##
                                      #X#   <- X: the hump at 3600,-1264
                                                   (ground 133, road 108)
          ```

        - **Why that stretch sits at one height whatever the hills do.** A pad is flat by design, so all 420 blocks share one level. The planner starts from the median ground under the pad (114), then moves it so neighbouring crossings stay within a 1-in-3 climb of each other; here the outcome is a compromise with the hub crossing just before it (ground 85), which ends at 109 while this pad ends at 108. The ground under the stretch is mostly 112 to 133 (lower near its far end), so the road is dug into it. Side profile along the shared stretch:

          ```
           y
          133 |                      /\          <- hump: ground 133
          125 |               __/\__/  \
          115 |  ___/\_______/      |   \__       ground (median 114)
          108 |  =================================  road: one flat level, 108
          100 |                     |         \__
              +---------------------|--------------> blocks along r3_m2_2
                 |<-- shared pad, about 420 blocks -->|
                 cut = ground - road: 5 to 17 most of the way, 25 at the hump
          ```

        - **The real numbers** (planner output on the recorded pt1573 terrain; road levels from `JunctionField`, ground from the exact terrain). [Side profile of r3_m2_2](../../review/road-refine/profile-r3_m2_2.png): the yellow band is the shared pad, grey bands are ordinary crossing pads a few blocks wide, red is cut. After the pad ends the road follows the ground down again. [Plan of the two routes](../../review/road-refine/plan-r3_m2_2.png), overlap in yellow. The 47-block cut is the same pattern on `r4_m2_3` and `r4_m2_4` ([profile](../../review/road-refine/profile-r4_m2_3.png), [plan](../../review/road-refine/plan-r4_m2_3.png)): they share two long stretches, one from the hub held at 92 (over ground near 65, so fill) and one held at 94 while the ground rises to 141; past its end the road can climb only 1 in 3, so the cut runs on past the pad.
        - **The fix the spike recommends**: grade the shared stretch once, as one street that follows the ground, instead of treating it as a crossing; or plan the two roads as one that forks where they part. Only the ends where they meet other streets would stay flat.
  - Minor cause: a hump the lattice can't see. Both 32-lattice nodes read 118, and the ground between them peaks at 133.
- **Main roads, with a refine pass** (all 22 main roads of the pt1573 metropolis; cut is ground minus graded level, counted per column):

| Variant | columns cut > 8 | > 12 | `r3_m2_2` max | extra exact columns / chunks off final roads | A* runs |
|---|---|---|---|---|---|
| today | 892 | 645 | 25 | 0 | 22 |
| refine, relief > 8 | 614 (-31 %) | 535 | 11 | 1,643 / 211 | 41 |
| refine, relief > 4 | 837 | 717 | 11 | 2,330 / 566 | 82 |
| sample every edge A* tries ("all", relief > 8) | same routes as refine | | | 23,277 / 8,310 | 23 |

  How the refine pass works: sample 3 points on the 8-grid inside each 32-block step of the chosen route. Where the relief against the straight line between the nodes exceeds T, charge 120 × relief (the climb the lattice missed) and replan. Repeat at most 8 times. The worst cut stays 47, because that one is the flat pad.
- **Intercity links on step-8 terrain** (7 links, 7.2 km, ports and routes as in the game):
  - Today: 53 of 887 stations cut more than 8, the worst 34. Both 34-block cuts are climbs the lattice did see: the path climbs 33 and 56 blocks in a single lattice step. A 56-block climb costs only about 5 steps' worth, so A* takes it. The 1-in-4 deck cannot follow it, and the rest becomes a cut.
  - Smoothing is not the cause: a ±2-station deck cuts deeper (the worst cuts of the 7 links sum to 172, against 140 today).
  - **A cut check in the existing reroute loop** treats a station cut deeper than T like a wet station (ban, at most 3 reroutes, keep the best). At T = 8, stations cut more than 8 drop from 53 to 41, and more than 12 from 27 to 22. The worst cut stays 34. Station reads go up 2.2x (3,191 to 6,962) because every reroute re-reads its whole curve.
  - A grade-aware A* cost (+400 per block of rise over 1 in 4 per step, no extra reads) took stations cut more than 12 from 27 to 17, but it changed which links exist and made some routes worse. Not conclusive.
- **What A* reruns cost.** On memoised costs: 4 to 25 µs per intercity link (a band of 13 × 15 nodes), about 1 ms per main road. Live, a single terrain column costs 0.3 to 0.55 ms (ADR 0003 W2). The search costs almost nothing next to the reads.

## Result

- **D* Lite: not worth it.** It is Koenig & Likhachev, AAAI 2002 ([paper](http://idm-lab.org/bib/abstracts/papers/aaai02b.pdf); the linked [Sollimann/Dstar-lite-pathplanner](https://github.com/Sollimann/Dstar-lite-pathplanner) is Python, MIT, Kristoffer Rakstad Solberg 2020). It searches backwards from the goal and keeps g/rhs values, so a robot that keeps moving can replan from where it now stands after many small cost changes.
  - Our routes are planned once, from a fixed start, with at most 3 to 8 cost updates, on graphs of a few hundred nodes.
  - What D* Lite saves is search time, which here is microseconds. It saves no terrain reads: both it and a rerun A* read only nodes they have not seen, and those reads are memoised either way.
  - It is pure enough in our serial loop. Its tie-breaking depends on the order of the updates, though, which is harder to check against a fresh plan than a rerun A*.
  - The pattern John describes (plan coarse, find the obstacle, update costs, replan) is right. A plain A* rerun on a TreeMap of penalties does it; the dry-check loop already works that way.
- **A finer intercity lattice (32 or 16): not worth it.** The deep intercity cuts are climbs the lattice saw. A 16-block lattice reads about 16x the nodes of each link's band, about 3,000 single columns (≈ 1 s, *model*), against about 400 station reads today.
- **Refine and reroute: worth it for main roads, a modest gain on intercity.**
  - For main roads it reads about 7 % of the columns that sampling every edge would. On this metropolis it finds the same routes.
  - It is a pure function of (seed, exact probe): bounded loop, sorted penalty map, samples on the 8-grid. So step-8 dumps replay it exactly, and chunk order cannot matter.
  - It does break one rule: the skeleton would read line samples between its 32-block nodes. That needs a short note on ADR 0003 P1, and a probe built for line samples (`CoarseProbe` refuses columns off the lattice).
- **Recommendation.**
  1. **First, and outside this spike: fix the flat pad.** When two whole streets overlap along their length, grade the overlap once as a single street instead of pinning it flat. Another way is to plan main roads as a tree that forks where they part. This is the cut in John's photo and the deepest ones in the metropolis. It fits 7.19 FALLSTREETS ("junction heights from the ground first"), or a new small bug item.
  2. **In 7.x: the main-road refine pass with T = 8**, after (1), then re-measured. It adds about 211 chunk fills per cold metropolis (≈ 0.4 s single-threaded, *model*); the other chunks it touches are ones the junction field fills anyway.
  3. **Intercity: a cut check plus a grade-aware cost, low priority.** First measure on more links. A real ridge needs a long detour, a switchback or a tunnel (DS-50: no tunnels for now).

**Against this.** The strongest case for a finer lattice is that it is simpler: one number to change, no loop. Here, though, "sample every edge" gave exactly the routes the refine pass found, at 14x the reads. I would change my mind if a wider corpus (the 9 gate windows, more worlds) showed deep main-road cuts dominated by humps the lattice misses rather than by pads, or showed refine routes getting much longer. D* Lite becomes interesting only if routing turns per-chunk and lazy, with many incremental updates.

**Sources / what I couldn't verify:** Code read: `RoadNet`, `Profile`, `Curve`, `LatticePath`, `DefaultProgram`, `CityProgram`, `CoarseTerrain`, `CoarseProbe`, `JunctionField`, `Grading`, ADR 0003 and 0004, PERFORMANCE "Region layer row", `review/sparse-reads.md`. Limits:
- One metropolis and 7 intercity links in one world. The dumps interpolate bilinearly between 8-block samples.
- Cut is counted at centre-line columns only, and not checked in game.
- Every cost in seconds is a model built from unit costs measured earlier, not timed here.
- I took the paper's claims from the repo README and what I know of the paper; the PDF would not render here.
