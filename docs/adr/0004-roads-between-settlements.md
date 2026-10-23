---
tags: adr
status: proposed
date: "2026-10-22"
author: researcher
approvedBy:
supersededBy:
dependsOn:
  - "[[docs/adr/0003-worldgen]]"
related:
  - "[[docs/adr/0003-worldgen]]"
research:
  - "[[docs/research/spikes/s10-intercity-roads]]"
  - "[[docs/research/spikes/s10-mod]]"
  - "[[docs/research/overlays/lost-cities-road-planning]]"
---

# 0004. Roads between settlements

Settlements are linked by a pure, region-scale road graph: the relative
neighbourhood graph over rim gaps, routed on the coarse lattice. Roads stay
dry apart from short single-span stream crossings, with no causeways or long
bridges. The mod draws them with one per-chunk feature that cuts and fills
the terrain in terraces. This ADR adds to [ADR 0003](0003-worldgen.md)
without superseding it, and amends W10: the per-chunk feature is used for
roads.

## Context

- **Design and spike.** The design is in
  [plans/intercity-roads.md](../plans/intercity-roads.md), option (a). Spike S10
  passed on determinism and cost: [planner half](../research/spikes/s10-intercity-roads.md),
  [mod half](../research/spikes/s10-mod.md). Chunk queries gave the same
  answers in shuffled orders and with cache capacity 1. 91 % of settlements
  were linked. A warm road chunk costs 0.0 ms in the planner. In the mod, a
  cold road chunk costs about 0.9 ms with coarse heights and 7.2 ms with
  exact heights. The mod's block digests matched in 4 chunk orders.
- **What the spike left open.** It read 14.5 to 18 km around a chunk, and
  that radius grows with `rmax`, the largest settlement radius (about 3
  times larger at the live maximum of 4,096). It probed exact heights per
  edge: 645 probes, or 100 to 250 ms live. It cut sheer walls 5 to 15 blocks
  high (road-2). It also crossed water: up to 96 blocks per lattice run, and
  74 to 78 % of routes had a wet station.
- **John, 2026-10-22** ([REVIEW](../REVIEW.md), "S10"): "The network looks
  great". Road-0 and road-1 "don't look right… lets not get into big bridges
  or causeways yet… either route around water or not have the roads connect
  if a body of water is blocking." He also asked whether a port town is
  possible at planning time.
- **The Phase 6 plan**, [decision 1](../plans/phase6.md) (default, waiting
  for John), says the game uses the dumps' exact height definition
  everywhere, through a per-world `ColumnMemo`. The coarse surface is off by
  12 to 16 blocks, and by 50 to 150 under Terralith and Tectonic (S2), so a
  road deck must not come from it.
- **Probe (researcher, 2026-10-22).** This is throwaway code on the S10
  planner, on branch `claude/adr-roads-probe`, never to be merged. It ran
  on the same two world dumps and adds two things. First, the dry rule:
  water is forbidden on the lattice, at most 16 wet blocks per run at 8-block
  stations, and up to 3 reroutes that ban the lattice nodes beside a wet run.
  Second, rim-gap weights with "routable witnesses" (N1). Pictures:
  [dry-12345.png](../research/spikes/s10/dry-12345.png),
  [dry-987654321.png](../research/spikes/s10/dry-987654321.png). The first
  row reproduces S10's numbers, and two runs gave byte-identical PNGs.

| graph, water rule | linked (seed 12345 / 987654321) | routes built | coarse probes per region cell |
|---|---|---|---|
| centre RNG, lattice water runs up to 96 (S10, causeways) | 91.4 / 90.7 % | 140 / 140 | 654 / 665 |
| rim-gap RNG, same water rule | 89.5 / 90.0 % | 127 / 134 | 645 / 712 |
| rim-gap RNG, dry rule | 68.4 / 74.7 % | 127 / 134 | 1,431 / 1,486 |
| rim-gap RNG, dry rule, routable witnesses (**chosen**) | 75.7 / 80.0 % | 206 / 204 | 2,475 / 2,667 |

With water forbidden only on the 64-block lattice, routes still had wet
runs of up to 152 and 168 blocks at the stations, most under 90. So the lattice alone cannot keep
a road dry: the shaped curve must be checked. Two things make these linked
shares pessimistic. The stride-64 dumps interpolate every wet sample into
a pond about 64 blocks wide. The reroute is also crude. Live terrain is
not measured.

## Options

1. **Water.** (a) Keep S10's rule. It gives causeways, which John rejected.
   (b) No crossings at all: every stream cuts a link. (c) Stay dry, and
   allow short single-span crossings. (d) Bridges and causeways. John
   deferred these.
2. **Graph.** (a) RNG over centres (S10). Across a big city, a settlement on
   the far rim can block a short link, and the witness search grows with
   `rmax`. (b) RNG over rim gaps. (c) Option (b) where a witness counts only
   if both of its links route. (d) Lost Cities' mutual k-nearest, which
   leaves hubs unlinked.
3. **Heights.** (a) Exact probes at every S10 sample: 645 per edge, 100 to
   250 ms. (b) A per-chunk window of exact probes with a memo: 7.2 ms cold,
   under 1 ms warm. It cannot decide whether an edge exists under a dry
   rule, though: water must be known along the whole route first. (c) A
   per-edge profile from the coarse surface. Cheap, but off by up to 150
   blocks under modded terrain. (d) Exact reads at the 8-block stations
   only, per edge. These feed both the dry check and the deck.
4. **Cuts.** (a) Sheer walls (S10). (b) A 1:1 slope. (c) Terraces with a
   width cap.

## Decision

**1c, 2c, 3d, 4c.**

### Network

- **N1 Graph.** Nodes are resolved settlements (`FieldRegionView.cell`). A
  joined city is one node. The weight of a pair is its rim gap,
  `g = |AB| - rA - rB`. A and B are linked when `g <= Lgap` (1,536) and no
  witness C exists. A witness has `g(A,C) < g(A,B)` and `g(C,B) < g(A,B)`,
  and **both of those links must route** (N4). Ties go by `EdgeKey`, lower
  `CellKey` first. A spanning tree of the routable pairs always survives,
  because a witness that blocked one of its edges would give a lighter tree.
  So a settlement that can reach any neighbour within `Lgap` is linked, and
  every group joined by dry land is connected. The probe agrees. An
  orphan-rescue check tried each unlinked settlement's 3 nearest non-graph
  neighbours. With centre weights it found nothing to add. With rim weights
  it found 3 settlements, and each of them touches a neighbour, which N2
  links.
- **N2 Touching pairs.** When `g < 128`, the pair gets one shared meeting
  point on the line between the centres instead of a route. Both
  settlements' approaches go to that point. A meeting point counts as a
  routed link, including when that pair is a witness. (John agreed, Q3.)
- **N3 Ports.** A port sits on the rim + 64 at the bearing to the other end,
  snapped to the 64-block lattice. If that spot is wet, on a peak or inside
  another footprint, up to 4 hashed retries move it **at most 256 blocks
  along the rim** (not ±60°). A port must also have a dry radial line to its
  own rim.
- **N4 Routing.** A* runs on the 64-block lattice from port to port. Wet
  nodes are forbidden, and so are other footprints + 32. The search band is
  the ports' box grown by `max(256, 0.3 |port to port|)`. The route is then
  string-pulled and shaped with Catmull-Rom (S10). Then the dry check below
  runs, with at most 3 deterministic reroutes. If it still fails, there is no
  edge. Routes are cached per `EdgeKey`; the lower key owns the route.
  Widths: hamlet 3, village 4, town 5, city to city 7.
- **N5 Skeleton link.** The graph reads decisions only, never skeletons. The
  skeleton therefore reads the graph: required exits face the ports, and an
  approach runs from each exit to its port (plan, "How SK exits meet the
  roads"). Approaches follow the water rule too.

### Water rule

- **WR1 Dry check on the curve.** The check reads every 8-block station on
  the centre line (HT). A station is wet when its water depth is at least 1;
  the biome does not count. Reads come from the noise-stage probe, never
  from the generated world. The lattice search also charges extra for nodes
  next to wet nodes, which keeps roads off the shore.
- **WR2 Crossings.** A wet run of at most **20 blocks** along the road is a
  crossing (John, Q1: raised from 12). The run is measured exactly: at a wet
  station, the columns between the dry stations on either side are read (at
  most 28 more reads). A crossing is one span: a flat deck at the higher
  bank's deck height, resting on the banks, with no piers. Anything bigger
  waits for ROADMAP 7.3 (Bridges). Crossings must be at least 32 blocks apart; two
  closer together count as one run. Anything longer means reroute, then
  drop the edge. A road running down a river or lake gives a long wet run, so
  John's road-0 case is ruled out by construction.
- **WR3 No fill in water, ever.** Shoulders, embankments and terraces stop
  at wet columns. Any water column under the road gets deck blocks, never
  fill. That covers water narrower than the 8-block station spacing, and
  road edges that hang over a bank.
- **WR4 Linked share.** The bar "at least 90 % of settlements linked" is
  retired (John agreed, Q2). 6.10's bars become:
  - the invariants: no road column on water outside a crossing, no fill in
    water, and no footprint crossings;
  - the N1 guarantee, checked by an orphan-rescue test that must find 0;
  - the plain linked share, reported but not barred. The probe expects
    about 75 to 80 % on the pond-heavy dumps. The probe allowed wet runs of
    up to 16 blocks, so the 20-block limit should change that little there:
    the dumps interpolate ponds about 64 blocks wide, and most wet runs on
    the shaped curves were longer than 20 (up to 152 and 168, most under
    90). Live terrain has real narrow streams, so it may gain more.
    *Estimate, not measured.*

### Reads, heights and cost

- **RC Read cap.** The road layer finds settlements through region-cell
  claims (`claimedBy`), never by scanning centres within a radius that
  includes `rmax`. A chunk resolves the cells within about 5 km of itself:
  the corridor bound of about 3 km from the ports, plus 2 cells. A
  settlement's edge list resolves the cells within `Lgap` + 2 cells of its
  own rim. Each cell resolution is memoised by the region view (D6). The
  test turns S10's scan-radius log into an assertion. *Untested:* the probe
  did not implement claim lookup.
- **HT Heights.** All heights use the game's single definition (Phase 6
  decision 1: exact, through `ColumnMemo`). The coarse surface is never
  used for a deck. D4 stands unchanged.
  - **Topology.** The lattice A* and the string-pull grade test read only
    the 64-block region lattice. S10's 16-block chord samples are dropped.
  - **Stations.** For each edge, the water and floor are read once at every
    8-block station of the shaped curve: about 100 stations for a median
    edge, 200 at p95. One read serves both the dry check (WR1) and the deck.
    The deck is the mean over ±8 stations, with a grade limit (start at 1
    in 4), stored with the route.
  - **The feature makes no probes.** S10's 645 reads per edge drop to the
    stations alone.
  - **Why per edge, not per chunk.** The dry rule must know the water along
    the whole route before the edge can exist. A per-chunk profile (3b)
    cannot decide that.
- **Cost (estimates, not measured).**
  - Station reads: 15 to 75 ms per edge, once, at S6's 0.15 to 0.38 ms per
    column. A1's batched probe may cut this.
  - The lattice search: at most 256 distinct lattice nodes per region cell,
    if a pure lattice memo shares them with the region decision. Most of the
    probe's 2,500 calls per cell are repeats. Exact reads put this at 40 to
    100 ms per cell at worst; the coarse fallback for the region layer, about
    4 ms.
  - Region prefetch (P2) can do all of it ahead of the player. A warm road
    chunk costs only its writes, about 0.3 ms.

### Mod feature and cuts

- **MF1** One `Feature`, `fractalbuildings:road`, with empty placement.
  `BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
  TOP_LAYER_MODIFICATION, key)` adds it to every overworld biome (checked on
  26.3 in S10). It runs once per chunk, ignores its `RandomSource`, and
  writes only its own chunk's columns, read from the pure query.
- **MF2 Ownership.** A road column inside a district box is drawn by that
  district (D5). Columns inside a structure piece's box that the chunk knows
  about are skipped, so the road yields to vanilla and modded structures.
  *Untested.*
- **MF3 Ground.** The feature scans down from `WORLD_SURFACE_WG` past air,
  replaceable blocks, fluids, leaves and logs. It never reads
  `MOTION_BLOCKING`; that is the S10 mutant, which the permanent test keeps.
- **MF4 Claims and digest (D7).** The ledger records every block placed,
  plus the air volume the feature claims. That volume is a pure function of
  ground, deck and terrace, and is recorded whether or not anything was
  there. The digest is SHA-256 over the sorted ledger. Foreign leaves that
  later grow into the claimed volume are not ours. The test generates fresh
  worlds in 4 or more chunk orders, plus one with the road caches at capacity 1, and reads the
  deck back.
- **TC Terraced cuts.** Each column's new top is a pure function of the
  deck, the column's distance to the centre line and its own ground. Past a
  1-block shoulder, the cut rises in steps at most 2 high with benches at
  least 2 wide. It stops where it meets the ground, or 12 blocks from the
  road edge. Any face left beyond that is accepted; 6.10 reports the share of
  columns with a face over 3 blocks. *Pending (Q4):* John judges the look
  from a screenshot that the 6.10 mod half (C1) takes in its T2 road test;
  no earlier probe builds terraces. Fill is a 1:1 embankment capped at 8
  blocks. Terraces and embankments are topped with the column's own surface
  block. On a steady grade the deck uses stairs or slabs instead of
  whole-block steps.

### Determinism rules (D1 to D7 apply)

- **RD1** Every road answer is a pure function of (seed, `genVersion`, the
  region decisions and the noise-stage probes). It never depends on
  skeletons, district plans, generated blocks, or which chunks exist. The feature reads only its own columns' ground (MF3).
- **RD2** Edges are symmetric and owned by the lower key. Comparisons are
  strict, with hashed retries from (seed, both cells, purpose). Angles and
  curves use `StrictMath` (D2).
- **RD3** The graph, routes, profiles and rasters are D6 pure caches. The
  permanent `RoadNetTest` uses shuffled orders, capacity 1, and a mutant
  that depends on cache state.
- **RD4** Where two edges overlap, the lower `EdgeKey` wins. Inside a
  district box, the district wins.

### Deferred

Bridges longer than 20 blocks, causeways, piers, ferries and port links, and
roads through a third settlement. Each needs its own design and review.
Big bridges are ROADMAP 7.3 (Bridges), behind a world setting (John,
2026-10-23).
Lost Cities' railway planner and 7.1 transit could later share the links.

**Port towns (John's question).** The timing works for a port's *site*, but
not as a reaction to roads. Cells decide before, and independently of, the
road graph. A blocked road therefore cannot create a port: that would be a
cycle. The field can tag ports itself, though. R2/5.10 already biases town
sites (0.3) and city and metropolis sites (0.6) toward water within 768
blocks. A `port` tag (a centre near large water) can come from the same
water search at no extra cost. Biasing villages too only means changing
constants (and bumping `genVersion`). Nudging the *tier* up at a coastal
site is possible, but it makes every claimant's radius depend on probes,
which widens the join rule's reads (R2). Nudge the site, not the tier. Ferries or
bridges between ports would come later, as a separate pure layer over
port-tagged nodes.

## Consequences

- Roads look like roads: no causeways, and no road down a river. The price
  is fewer links. Lake-heavy worlds will have unlinked settlements, about
  20 to 25 % on the dumps, and wide rivers become borders between road
  networks. Routable witnesses win back 5 to 7 points over the plain graph,
  and cost 50 to 60 % more routes.
- **For 6.10:** a planner half with N1 to N4, WR1 to WR4, RC and HT, plus
  RoadNetTest, on Phase 6 A1's live probes and `ColumnMemo`. A mod half with MF1 to MF4 and TC, plus the T2 road test (C1). It
  will report wet-run histograms and links lost to rivers on live terrain.
- **The case for crossings now:** links grow from about 75 % to 91 %, and
  rivers stop acting as borders. **What would change this ADR:**
  - live linked share under 70 %, or John finding unlinked towns odd:
    bring bridges (7.3) forward;
  - station reads over 100 ms per edge live: read the dry check every 16 blocks, and build the deck per chunk (3b);
  - per-cell routing cost over 100 ms live: drop routable witnesses (N1
    falls back to plain rim-gap RNG);
  - the claim lookup failing to bound reads: graph nodes per region cell
    for joined cities.
- **Questions for John:**
  1. Crossings: a flat span of at most 12 blocks over a stream, or no
     crossings at all for now?
  2. Is it fine to drop the 90 % linked bar in favour of the land guarantee
     (WR4)? On lake-heavy worlds, a fifth to a quarter of settlements would
     have no road.
  3. Should touching settlements (rims under 128 apart) share a meeting
     point instead of a road (N2)?
  4. Terraces (steps at most 2 high, benches 2 wide, up to 12 blocks out):
     is this the look you want, or something gentler? 6.10 will bring a
     screenshot.
  5. Port towns later: a `port` tag from the region field, plus a water
     bias for villages. Should we plan that, or wait for bridges?
  6. HT assumes Phase 6 decision 1 (exact heights in the game). If the
     region layer falls back to the coarse screen, roads still read exact
     heights at their stations, because a deck cannot sit on a surface that
     may be 150 blocks off. Is that acceptable?
- **Answers (John, 2026-10-23):**
  - In a comment on this ADR: "1. Make the span 24, then yes. 2. yes
    3. yes 4. I’ll wait for the screenshot".
  - In chat the same day, which **supersedes the 24 for question 1**: "So,
    I think no "very big" bridges for now. Lets go up a little more on a
    small bridge, 20 blocks, and call it good for now. I want something on
    the roadmap past initial integration where we start addressing the
    Bridge Problem (I don't think it's a really big problem, but I do want
    them to look good)." Big bridges must "look like bridges. Well designed,
    not necessarily starting/ending at the same elevation, abutments,
    pillars, suspension, all of it. I don't want 50 block slabs", and a
    setting should "allow/disallow big bridges".
  - Recorded: 1. single-span crossings up to 20 blocks (WR2); bigger
    bridges are ROADMAP 7.3. 2. The 90 % bar is dropped (WR4). 3. Touching
    settlements share a meeting point (N2). 4. Pending the terrace
    screenshot from the 6.10 mod half (TC). 5. **Open.** 6. **Open.**