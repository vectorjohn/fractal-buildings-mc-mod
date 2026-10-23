# Plan: roads between settlements

Status: spike S10 done; the decisions are drafted in [ADR 0004](../adr/0004-roads-between-settlements.md) (proposed 2026-10-22, waiting for John), which overrides this plan where they differ: no causeways or long bridges (crossings up to 12 blocks), rim-gap graph with routable witnesses, the read cap, deck heights from exact reads at 8-block stations, and terraced cuts. Author: lead-coder, 2026-10-21.
Asked by John in [REVIEW.md](../REVIEW.md) (D1 hamlet and village). On the ROADMAP since 2026-10-22: S10 (approved by John, running) and item 6.10.

# The problem

Today every settlement is an island. Inside one joined settlement, cells meet at hashed
gates (MC1/MC2), and each settlement has 1 to 8 exit roads (SK), but the exits stop at the
urban edge ([phase5.md](phase5.md), "There are no roads between separate settlements").
A metropolis with towns 1 to 3 km away and no road to any of them reads as wrong. We want
a sparse, believable network: every settlement reaches its neighbours, roads follow the
land, bridge short water, and arrive at a town's exits.

# Constraints

- **Determinism (ADR 0003 D1, D6).** Chunks generate independently, in any order, on any
  thread. "Is there road in this chunk, where, at what height" must be a pure function of
  (seed, position), with caches that only memoise.
- **Bounded reads.** A question about one chunk may read only a bounded neighbourhood.
  Inter-settlement roads are 1 to 3 km long, so whatever a chunk reads must be cached per
  region cell or per edge, never recomputed per chunk.
- **The region field and join rule (ADR 0003 R1, R2).** Nodes are *resolved* settlements
  (`FieldRegionView.cell`): a joined city is one node (its owner cell), with centre and
  radius. About 30 % of cells settle (R1 log: 149 of 501 on seed 12345), many cells are
  sea. Resolution already reads about 3 x maxRadius for large tiers; that cost is paid by
  cities anyway and is cached.
- **Structure reach (W1, structure-pipeline note).** A structure start writes only within
  about 8 chunks (±128 blocks) of its start chunk; anything beyond is silently dropped. A
  3 km road cannot be one start.
- **Probe costs** ([scale.md](../review/phase5/scale.md), S6): coarse probe about 17 us,
  exact height 0.15 to 0.38 ms through `getFirstFreeHeight` (6 to 28 us via the sampled
  surface level), and region decisions about 11 ms per cell live. Starts, references and
  features share one serial worldgen lane per dimension (ADR 0003), so per-chunk cost on
  that lane must stay in single milliseconds.
- **Ownership (D5).** Inside a settlement a road block belongs to the district that owns
  the column. Outside, nobody owns anything yet.

# Candidate designs

**(a) A region-scale road graph, routed per edge on the coarse lattice** (recommended).
- *Graph:* the relative neighbourhood graph (RNG) over resolved settlement centres: A and
  B are linked when no third settlement C is closer to both of them than they are to each
  other, and the gap `|AB| - rA - rB` is at most `Lgap` (start at 1,536). The RNG is
  symmetric (both ends compute the same answer from the same sites), planar, and contains
  the minimum spanning tree, so every settlement on a landmass within `Lgap` of another is
  connected. That avoids Lost Cities' known failure, where mutual k-nearest selection
  leaves hubs unconnected ([lost-cities-road-planning.md](../research/overlays/lost-cities-road-planning.md)).
  The deciding read is bounded: settlements within `|AB|` of A or B.
- *Ports:* each edge end gets a port on the circle of radius `rA + 64` at the bearing to B,
  snapped to the 64-lattice; water or a peak → up to 4 hashed tries along the circle, as SK
  gates do. Pure, from decisions only.
- *Route:* the existing deterministic `LatticePath` A* on the 64-block `RegionProbe`
  lattice from port to port, inside a band (the ports' box grown by `max(256, 0.3 |AB|)`),
  with the default slope and water costs. Other settlements' footprints (+32) are
  forbidden; water runs up to 96 are bridges. No path → no edge (islands stay unlinked).
  Owner: the lower `CellKey`; one cached route per edge.
- *Shape and height:* string-pull the lattice path (drop nodes while the straight chord
  stays within a slope limit), then a centripetal Catmull-Rom curve through the rest.
  Heights at stations every 8 blocks along the curve from the exact probe, then a
  grade-limited smoothing over a fixed window of stations (±8), so any chunk computes the
  profile from stations in its own reach only.
- *Chunk query:* region cell of the chunk → cached list of edges whose band meets that
  cell (built once per cell from settlements within `2 maxRadius + Lgap + band`). The
  chunk then tests its 256 columns against at most a few curves.
- *Cost estimate (to measure):* per edge about 600 to 2,000 coarse nodes, 10 to 35 ms
  live; about 0.36 edges per cell (30 % settled, mean RNG degree about 2.4), so a few ms
  per cell on top of the 11 ms decision. Per road chunk about 20 to 40 exact probes:
  3 to 15 ms with `getFirstFreeHeight`, under 1 ms with the sampled surface level.

**(b) Border gates on every crossed cell edge, routed per cell.** Take (a)'s graph, but
route each edge as a chain of region cells (those the straight segment A-B crosses), with
a hashed gate on every shared Voronoi edge (SK's gate code) and an A* inside each cell
between consecutive gates. A chunk then needs only its own cell's routes, a smaller read
than (a). Costs: the same graph is still needed (local rules alone give roads to nowhere),
roads are forced through hashed points (a gate on the wrong side of a ridge or lake makes
detours), and a lake bigger than a cell breaks the chain. More code than (a) for a read
saving (a)'s per-cell cache already gives.

**(c) Straight hashed highways on a coarse grid.** Arterials scaled up: jittered lines
every 2 to 3 km, each segment on by a hash when settlements lie near both ends, plus a
short spur from each settlement to the nearest line. Pointwise, nearly free, no graph.
But the roads ignore terrain (straight over mountains and through lakes), don't go to the
towns, and read as a world grid, the thing the Lost Cities note says to avoid. Rejected
unless (a) proves too dear.

# How SK exits meet the roads

The graph is computed from decisions only, never from skeletons, so the skeleton can read
it without a cycle. `TierProgram.exits` gains *required exits*: one per incident edge, at
the urban-edge column nearest the bearing to that edge's port (the existing `pick`), and
the main road then continues from the exit to the port (an "approach", a `LatticePath`
outside the mask, part of the skeleton). Hashed exits fill up to the tier's count as now; a
settlement with no edges keeps today's behaviour. Ownership: a road column inside a
district box is drawn by that district (D5, unchanged); every other road column, approach
or inter-settlement, is drawn by the road layer below. Width and surface by the lower tier
of the two ends (hamlet track 3, village 4, town and up 5, city to city 7).

# What the mod needs

- **A per-chunk feature, not structure pieces.** A road is long and thin; as structures it
  would need a start on every tile it crosses (geometry-only `isPlacementChunk` on every
  tile in the world, then `findGenerationPoint` asking the network), pieces, references
  and saves, for nothing a road needs (no `/locate`). A feature has no reach limit and
  writes its own chunk from the pure query, which is exactly what the network gives. It
  costs the beard: the feature does its own cut and fill (clear up to 4 above the deck,
  fill up to 3 below with a 1:1 shoulder, columns in its chunk only, all pure). This is
  ADR 0003 W10's feature, used for roads only, so it needs an ADR (0004, or an amendment)
  for John at the gate.
- **Registration (checked):** Fabric API 0.162.0+26.3 ships `fabric-biome-api-v1`
  20.1.0 with `BiomeModifications.addFeature(Predicate<BiomeSelectionContext>,
  GenerationStep.Decoration, ResourceKey<PlacedFeature>)` and
  `BiomeSelectors.foundInOverworld()` (javap on the jar in `~/.gradle`). Step
  `top_layer_modification`, as W4 (after trees; per step, structures are placed before
  features, so cities are already there).
- **Known risks, for the spike:** leaves of trees decorated later in a neighbour chunk can
  still overhang (accepted, like D5's "our air is not ours"); vanilla villages and other
  structures on the route (extend W7's veto along the corridor, or skip columns inside
  structure boxes); bridges longer than 40 (deck and piers, pure per span).

# The spike (S10)

**Question:** can any chunk answer "which inter-settlement road columns lie in me, and at
what deck height" from a bounded, cached read of the region field, byte-identical in any
order and with evicting caches, at a cost the serial lane can carry, and do the roads
look like roads on real terrain?

Two halves, which can run in parallel:

1. **Planner (no Minecraft):** graph, ports, routes and profiles on the recorded world
   dumps `fixtures/terrain/world-12345-s64.json.gz` and `world-987654321-s64.json.gz`
   (24,576 blocks square, stride 64), heights from `w12345-river-s8` where fine data
   exists. Evidence: a region-scale PNG of settlements and roads, opened and looked at;
   numbers: share of settlements linked, unroutable edges, edge lengths, routes crossing
   another footprint (must be 0), per-edge time and probe count, read radius actually
   used; determinism: chunk queries in shuffled order and with cache capacity 1 against
   capacity 8192 give identical columns and heights.
2. **Mod:** a feature drawing a 3-wide gravel road from a hard-coded polyline fixture
   (the planner half can supply it later), with cut and fill, in a noise world: the D7
   digest of our road blocks identical in 3+ chunk orders, a client screenshot, and the
   per-chunk cost on the lane (heavy lock, loadavg recorded).

Pass: both determinism checks hold; at least 90 % of settlements on land linked;
per-road-chunk cost under 5 ms with the sampled surface level; John likes the PNG and the
screenshot. Fail on cost → try (b)'s per-cell routing; fail on looks → tune costs before
switching to (c).

# Where it fits

Not a Phase 5 item (Phase 5 is done and at its gate). Proposed: **spike S10 now**, beside
the start of Phase 6 (it depends on no open gate; it only reads Phase 5 code). Then a
**Phase 6 item, "6.10 Roads between settlements"**: the planner graph and routes (core),
the SK required exits and approaches, a road layer in the region map and plan view, and
the mod feature. Its mod half needs 6.1 (palette roles) and 6.2 (registration); the
planner half needs neither. If Phase 6 is to stay lean for the first release, it moves to
a Phase 7 with the other world-level features. The coordinator decides; the ROADMAP is
not edited here.

# Related research

Added 2026-10-22 (assistant), after John pointed at the Lost Cities notes.

- [lost-cities-road-planning.md](../research/overlays/lost-cities-road-planning.md):
  Lost Cities' `INTERCITY_NETWORK_V1` is the closest working prior art (MIT). Useful here:
  its graph is built from decisions only, with a bounded search (±2 planning cells) and a
  canonical edge owner (the smaller key), as in (a); a chunk asks only the routes owned by
  nearby cells, like (a)'s per-cell edge cache; the highway level is derived from the two
  ends' levels (average), a simple default for our port heights; and its plan doc's
  determinism checklist (stable hash ties, quantised scores, caches never change results,
  bounded neighbourhoods) is a ready review list for S10. Its failures are what (a)
  avoids: mutual selection leaves hubs unlinked (the RNG contains the spanning tree),
  axis-aligned L routes read as a grid (A* plus curves), and hub decisions persisted in
  the save (ours are pure). Its 11.0.2 changelog is mostly highway-planning performance
  fixes, a warning to measure per-edge cost early, as S10 does.
- [lost-cities-city-placement.md](../research/overlays/lost-cities-city-placement.md):
  less direct. Its "city potential" for remote hubs comes from a cheap approximation,
  never from the full building plan, which avoids recursion: the same rule as the graph
  here reading decisions, never skeletons. Its city centres ignore the world seed, a
  reminder to hash (seed, position, purpose) for ports and route tie-breaks.
- Not researched: Lost Cities' railway planner (its highways bend round railway
  corridors). Worth a note before ROADMAP 7.1 (transit), which could share 6.10's links.

# Open questions (with the assumption if nobody answers)

- Should hamlets join the network, or only villages and up? *Assume all tiers; hamlets get
  a track 3 wide.*
- `Lgap`: 1,536 blocks. *Assume it; the spike reports the length histogram.*
- Roads through a third settlement are forbidden (they route round it). *Assume so; letting
  them through needs the town's skeleton to accept a through road, a later refinement.*
- Edges over sea: none (no ferries, no long bridges). *Assume so.*
