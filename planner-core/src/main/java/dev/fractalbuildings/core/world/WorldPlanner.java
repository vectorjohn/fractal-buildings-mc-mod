package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.cache.BoundedCache;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.raster.CellSink;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.FieldRegionView;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.region.RegionPlanner;
import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CellGeometry;
import dev.fractalbuildings.core.settlement.CityPlanner;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.DistrictPlan;
import dev.fractalbuildings.core.settlement.Districts;
import dev.fractalbuildings.core.settlement.FieldSiteView;
import dev.fractalbuildings.core.settlement.FixedSiteView;
import dev.fractalbuildings.core.settlement.RegionView;
import dev.fractalbuildings.core.settlement.LotBrief;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.ExitTrim;
import dev.fractalbuildings.core.settlement.Rect;
import dev.fractalbuildings.core.settlement.SkeletonSource;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.settlement.TierPrograms;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.style.StyleField;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.Executor;

/**
 * Settlement planning for one world: thread-safe, bounded, order-independent (docs/plans/phase6.md, A2). Everything it answers is a
 * pure function of (seed, config, probes); its caches only memoise, so any call order, any number of threads and any eviction give
 * the same bytes (ADR 0003 D6). Create one per world and share it; two planners never share a cache entry.
 *
 * <p>Layers, each with its own bounded cache: the region field's join rule ({@link FieldRegionView}: which settlement a cell belongs
 * to); one {@link SkeletonSource.Direct} per settlement (its cell skeletons and road network, shared by every tile of the
 * settlement); per tile the owner ({@link #tile}), the plan ({@link #plan}), the buildings and the ground. A tile that is asked for
 * while another thread computes it waits for that result. Costs: the first question about a settlement builds the skeletons of all
 * its cells and its road network (seconds for a metropolis: {@link #warm} hides it); a tile plan then costs the district planner's
 * exact probes; buildings cost the generator.
 *
 * <p>Not done here: no terrain patch and no invariant run (they read the whole tile plus a margin); tests run the invariants.
 */
public class WorldPlanner {
	/**
	 * Entries each memo keeps (LRU). Answers never depend on them, only how often something is recomputed; tests use small values to
	 * force eviction.
	 *
	 * @param skeletons cell skeletons per settlement; {@code region} entries in each of the region view's four memos
	 */
	public record Caches(int tiles, int sources, int skeletons, int region, int plans, int buildings, int ground, long graceNanos, int hardFactor) {
		/** Entries used within this long are kept past the cap (see {@link FlightCache}): a tile's chunks all arrive within it. */
		public static final long DEFAULT_GRACE_NANOS = 120_000_000_000L;
		/** Retention never lets a memo exceed this multiple of its cap: the memory ceiling. */
		public static final int DEFAULT_HARD_FACTOR = 4;

		public Caches(int tiles, int sources, int skeletons, int region, int plans, int buildings, int ground) {
			this(tiles, sources, skeletons, region, plans, buildings, ground, DEFAULT_GRACE_NANOS, DEFAULT_HARD_FACTOR);
		}

		/** The same caps with retention off: strict LRU (tests that force eviction). */
		public Caches strict() { return new Caches(tiles, sources, skeletons, region, plans, buildings, ground, 0, 1); }

		public Caches {
			if (graceNanos < 0 || hardFactor < 1) throw new IllegalArgumentException("retention");
			if (tiles < 1 || sources < 1 || skeletons < 1 || region < 1 || plans < 1 || buildings < 1 || ground < 1) throw new IllegalArgumentException("capacity < 1");
		}

		public static final Caches DEFAULT = new Caches(4096, 64, 64, FieldRegionView.DEFAULT_CACHE, 64, 32, 32);

		/** Measured: a cached tile (plan, buildings, ground) holds about this much heap (CacheMemoryTest: 0.9 MiB; rounded up). */
		public static final long BYTES_PER_TILE = 1L << 20;

		/**
		 * Caps scaled to the heap: the hard ceiling (cap x hardFactor, per tile memo) is held to about a tenth of {@code maxHeapBytes},
		 * between the defaults' floor (32 buildings / 64 plans, ceiling 128 / 256 tiles) and twice that. Larger heaps keep more tiles
		 * warm for more simultaneous players; answers never change.
		 */
		public static Caches forHeap(long maxHeapBytes) {
			long hardTiles = Math.max(1, maxHeapBytes / 10 / BYTES_PER_TILE);
			int cap = (int) Math.max(DEFAULT.buildings(), Math.min(2L * DEFAULT.buildings(), hardTiles / DEFAULT_HARD_FACTOR));
			return new Caches(DEFAULT.tiles(), DEFAULT.sources(), DEFAULT.skeletons(), DEFAULT.region(), 2 * cap, cap, cap, DEFAULT_GRACE_NANOS, DEFAULT_HARD_FACTOR);
		}

		/** The same capacity everywhere, strict LRU. */
		public static Caches all(int n) { return new Caches(n, n, n, n, n, n, n).strict(); }
	}

	private static final int LATTICE = 32; // spacing of the columns that vote for the owner of a tile whose centre cell has no settlement

	private final long seed;
	private final WorldConfig config;
	private final WorldProbes probes;
	private final Executor workers;
	private final DistrictGrid grid;
	private final RegionField field;
	private final FieldRegionView fieldView;
	/** The intercity road graph (ADR 0004), or null when the world has no roads (config off, or a fixed site). */
	private final RoadNet roads;
	private final BoundedCache<String, List<ApproachNodes>> nodeCache;
	/** Each settlement's approaches before the link rule ({@link #approachRoads}), and each link's state ({@link #linkState}). Null with no roads. */
	private final BoundedCache<String, List<Approach>> rawApproaches;
	private final BoundedCache<RoadNet.EdgeKey, LinkState> linkStates;
	/** Which links each settlement serves ({@link #served}): a few keys per settlement. */
	private final BoundedCache<dev.fractalbuildings.core.region.CellKey, Served> servedLinks;
	/**
	 * Floor of the link-state and served-link memos whatever {@link Caches} says: their entries are a few keys each, and a miss replans a
	 * settlement's approaches (skeletons and district tiles), so with every cache at 1 each road chunk would replan both ends of its links.
	 * Answers never depend on it.
	 */
	static final int LINK_MEMO_FLOOR = 4096;
	/** The planner {@link #computeServed} plans other settlements' approaches with: this one, or one with the default caches when this one's are smaller. */
	private volatile WorldPlanner linkPlanner;
	private final RegionView view;
	private final StyleField styles;
	private final Caches caps;
	private final BoundedCache<String, SkeletonSource> sources;
	private final BoundedCache<Long, Optional<TileInfo>> tiles;
	private final FlightCache<Long, TilePlan> plans;
	/** The tile plans as the district planner made them, before 6.25 MAINSTUB cuts exit roads: what the road layer's approaches read (the cut reads the link rule, which reads these). */
	private final FlightCache<Long, TilePlan> rawPlans;
	/** Each settlement's exit-road cuts ({@link ExitTrim}), by site id. Null with no roads' planner. */
	private final BoundedCache<String, java.util.TreeMap<String, ExitTrim.Cut>> exitCuts;
	/** The cut of one exit road as the plans say, whatever its gate's link state ({@code site id/road id}). */
	private final BoundedCache<String, java.util.Optional<ExitTrim.Cut>> fullCuts;
	/** (settlement, road, last column in tile) -> whether the bounded walk proved the cut lies at or beyond it (CASCADE A1). */
	private final BoundedCache<String, Boolean> walkBeyond;
	/** {@code -Dfb.exitcut.reference=true}: the old path (the whole road cut for every tile), the oracle of ExitCutWalkTest. */
	static final boolean EXIT_CUT_REFERENCE = Boolean.getBoolean("fb.exitcut.reference");
	private final FlightCache<Long, List<BuildingPiece>> buildings;
	private final FlightCache<Long, List<GroundPiece>> grounds;

	/** How a {@link #buildings} lookup was served: a finished result, waiting for a worker, or computed by the caller. */
	public enum Lookup { DONE, INFLIGHT, INLINE }

	/** Counters hook: told how every building lookup was served. Never changes an answer; null stops it. */
	public void observeBuildings(java.util.function.Consumer<Lookup> observer) {
		if (buildings != null) buildings.observer = observer;
	}

	/** @param workers runs {@link #prefetch}; null means prefetch does nothing. A direct executor runs it on the caller. */
	public WorldPlanner(long seed, WorldConfig config, WorldProbes probes, Executor workers) {
		this(seed, config, probes, workers, Caches.DEFAULT);
	}

	public WorldPlanner(long seed, WorldConfig config, WorldProbes probes, Executor workers, Caches caps) {
		this(seed, config, probes, workers, caps, null);
	}

	/**
	 * A planner for exactly one given settlement, with no region field deciding anything: every other cell belongs to nothing (the
	 * CLI's {@code --site}). For tools and tests that plan a settlement on a recorded window too small for the region planner.
	 */
	public static WorldPlanner forSite(long seed, WorldConfig config, WorldProbes probes, Executor workers, Caches caps, SettlementSite site) {
		return new WorldPlanner(seed, config, probes, workers, caps, site);
	}

	private WorldPlanner(long seed, WorldConfig config, WorldProbes probes, Executor workers, Caches caps, SettlementSite fixed) {
		this.seed = seed;
		this.config = config;
		this.probes = probes;
		this.workers = workers;
		this.caps = caps;
		this.grid = new DistrictGrid(seed);
		this.field = new RegionField(seed, config.cellSize());
		RegionPlanner rp = new RegionPlanner(field, probes.region());
		rp.maxRadius = config.maxRadius();
		rp.baseChance *= config.frequency();
		this.fieldView = fixed != null ? null : new FieldRegionView(rp, probes.window(), caps.region()).withFanout(probes.reads().fanout());
		this.roads = fixed == null && config.intercityRoads()
				? new RoadNet(field, fieldView, probes.window(), probes.region(), probes.roadExact(), caps.region()) : null;
		this.nodeCache = roads == null ? null : new BoundedCache<>(caps.sources());
		this.rawApproaches = roads == null ? null : new BoundedCache<>(caps.sources());
		this.linkStates = roads == null ? null : new BoundedCache<>(Math.max(LINK_MEMO_FLOOR, caps.region()));
		this.servedLinks = roads == null ? null : new BoundedCache<>(Math.max(LINK_MEMO_FLOOR, caps.sources()));
		this.view = fixed != null ? new FixedSiteView(fixed, field) : new FieldSiteView(fieldView, field, roads);
		this.styles = new StyleField(probes.biome(), seed);
		this.sources = new BoundedCache<>(caps.sources(), caps.graceNanos(), caps.hardFactor(), System::nanoTime); // det-ok: retention only, never an answer
		this.tiles = new BoundedCache<>(caps.tiles());
		this.plans = new FlightCache<>(caps.plans(), caps.graceNanos(), caps.hardFactor(), System::nanoTime); // det-ok: retention only, never an answer
		this.rawPlans = new FlightCache<>(caps.plans(), caps.graceNanos(), caps.hardFactor(), System::nanoTime); // det-ok: retention only, never an answer
		this.exitCuts = new BoundedCache<>(Math.max(LINK_MEMO_FLOOR, caps.sources()));
		this.fullCuts = new BoundedCache<>(Math.max(LINK_MEMO_FLOOR, caps.sources()));
		this.walkBeyond = new BoundedCache<>(Math.max(LINK_MEMO_FLOOR, caps.sources()));
		this.buildings = new FlightCache<>(caps.buildings(), caps.graceNanos(), caps.hardFactor(), System::nanoTime); // det-ok: retention only, never an answer
		this.grounds = new FlightCache<>(caps.ground(), caps.graceNanos(), caps.hardFactor(), System::nanoTime); // det-ok: retention only, never an answer
	}

	/** For subclasses that serve their own tiles ({@link FixtureWorldPlanner}); every method they do not override fails. */
	protected WorldPlanner() {
		this.seed = 0;
		this.config = null;
		this.probes = null;
		this.workers = null;
		this.caps = null;
		this.grid = null;
		this.field = null;
		this.fieldView = null;
		this.roads = null;
		this.nodeCache = null;
		this.rawApproaches = null;
		this.linkStates = null;
		this.servedLinks = null;
		this.view = null;
		this.styles = null;
		this.sources = null;
		this.tiles = null;
		this.plans = null;
		this.rawPlans = null;
		this.exitCuts = null;
		this.fullCuts = null;
		this.walkBeyond = null;
		this.buildings = null;
		this.grounds = null;
	}

	public long seed() { return seed; }

	public WorldConfig config() { return config; }

	public DistrictGrid grid() { return grid; }

	/** The district tile holding a world column (the fixture planner answers from its own tiles). */
	public DistrictGrid.Tile tileAt(int x, int z) { return grid.tileAt(x, z); }

	/** Is this chunk the centre chunk of its district tile (the structure start chunk)? Pure geometry: no probes, no caches. */
	public boolean isStartChunk(int chunkX, int chunkZ) {
		DistrictGrid.Tile t = grid.tileAt(chunkX * 16, chunkZ * 16);
		return t.originChunkX() == chunkX && t.originChunkZ() == chunkZ;
	}

	/** The tile whose centre chunk is this chunk (the tile a structure start at this chunk plans), or empty for any other chunk. No probes. */
	public Optional<DistrictGrid.Tile> startTile(int chunkX, int chunkZ) {
		DistrictGrid.Tile t = grid.tileAt(chunkX * 16, chunkZ * 16);
		return t.originChunkX() == chunkX && t.originChunkZ() == chunkZ ? Optional.of(t) : Optional.empty();
	}

	private static long key(int ix, int iz) { return ((long) ix << 32) ^ (iz & 0xffffffffL); }

	/** The settlement's source: its skeletons and road network, shared by all its tiles. */
	SkeletonSource source(SettlementSite site) { return sources.get(site.id(), id -> newSource(site)); }

	/** A new source for the settlement, sharing nothing with {@link #source}'s (its own shared values and junction field): for tests of tile independence. */
	SkeletonSource.Direct newSource(SettlementSite site) {
		SkeletonSource.Direct d = new SkeletonSource.Direct(seed, view, site, probes.coarse(), TierPrograms.of(site.tier()), caps.skeletons());
		d.withApproaches(() -> approachLines(site));
		if (roads != null) d.withExitServed((x, z) -> exitServedAt(site, x, z));
		d.withReads(probes.reads());
		return d;
	}

	private boolean decidable(Cell c) { return fieldView == null || fieldView.decidable(c.cx(), c.cz()); }

	/** The settlement of a cell, or empty when it has none or the recorded window cannot decide it. */
	Optional<SettlementSite> siteOf(Cell c) {
		if (!decidable(c)) return Optional.empty();
		return view.siteOf(c);
	}

	/**
	 * The tile's owner settlement and tier, or empty when no settlement plans the tile. The owner is the settlement of the cell that
	 * owns the tile's centre (else the one whose cells own most of the tile, {@link Districts#owns}), and the tile must be one of its
	 * planned districts (a tile with no urban column and no main road through it is not). Asking builds the owner's road network
	 * once; a tile whose cells have no settlement costs a few cached cell lookups.
	 */
	public Optional<TileInfo> tile(int ix, int iz) {
		return tiles.get(key(ix, iz), k -> computeTile(ix, iz));
	}

	private Optional<TileInfo> computeTile(int ix, int iz) {
		DistrictGrid.Tile t = grid.tile(ix, iz);
		Cell centre = CellGeometry.owner(field, (t.minX() + t.maxX()) / 2, (t.minZ() + t.maxZ()) / 2);
		TreeMap<String, SettlementSite> cand = new TreeMap<>();
		Optional<SettlementSite> own = siteOf(centre);
		if (own.isPresent()) cand.put(own.get().id(), own.get());
		else if (decidable(centre)) {
			// no settlement owns the centre: an edge tile of a settlement whose streets run through it, chosen by Districts.owns
			TreeMap<Cell, Boolean> seen = new TreeMap<>(CellGeometry::compare);
			for (int z = Math.floorDiv(t.minZ() + LATTICE - 1, LATTICE) * LATTICE; z < t.maxZ(); z += LATTICE) {
				for (int x = Math.floorDiv(t.minX() + LATTICE - 1, LATTICE) * LATTICE; x < t.maxX(); x += LATTICE) {
					Cell c = CellGeometry.owner(field, x, z);
					if (seen.put(c, true) != null) continue;
					try {
						siteOf(c).ifPresent(s -> cand.put(s.id(), s));
					} catch (RuntimeException e) {
						// a cell at the edge of a recorded window that cannot be decided: it claims nothing
					}
				}
			}
		}
		for (SettlementSite s : cand.values()) {
			SkeletonSource src = source(s);
			if (Districts.owns(src, ix, iz) && src.network().planned(ix, iz)) return Optional.of(new TileInfo(ix, iz, s, t));
		}
		return Optional.empty();
	}

	/**
	 * The plan of a tile that {@link #tile} returned: what {@code CityPlanner} gives the CLI for the district ({@code Districts.plan}
	 * then {@code Briefs.fill} with the settlement's quarters), with lots in a style the config disabled changed to {@code plain}.
	 * Throws {@link IllegalArgumentException} for any other tile.
	 */
	public TilePlan plan(int ix, int iz) {
		return plans.get(key(ix, iz), k -> trimmedPlan(ix, iz));
	}

	/** The tile's plan before the exit roads with nothing beyond their gate are cut ({@link ExitTrim}); same lots, plazas and landmarks. */
	TilePlan rawPlan(int ix, int iz) {
		return rawPlans.get(key(ix, iz), k -> computePlan(ix, iz));
	}

	private TilePlan trimmedPlan(int ix, int iz) {
		TilePlan raw = rawPlan(ix, iz);
		SettlementSite site = raw.info().site();
		SkeletonSource src = source(site);
		Rect rect = raw.plan().district().rect();
		ExitTrim.Plans lookup = (x, z) -> tile(x, z).filter(i -> i.site().id().equals(site.id())).isPresent() ? rawPlan(x, z).plan() : null;
		java.util.TreeMap<String, ExitTrim.Cut> cuts = new java.util.TreeMap<>();
		if (!ExitTrim.inCore(src, rect)) { // the tile that holds the centre is never cut: the first tile of a settlement pays nothing here
			for (String id : ExitTrim.exitRoadIds(src)) { // only the exit roads that cross this tile; the link rule and the neighbours' plans are paid when a cut can reach it
				if (!ExitTrim.isExitRoad(src, id)) continue;
				int m = ExitTrim.maxIndexIn(src, id, rect);
				if (m < 0 || ExitTrim.cutBeyond(src, id, m, raw.plan(), rect)) continue;
				if (!EXIT_CUT_REFERENCE && walkBeyond.getOnce(site.id() + "/" + id + "/" + m, k -> ExitTrim.cutBeyondWalk(src, id, m, lookup))) continue;
				ExitTrim.Cut c = fullCuts.getOnce(site.id() + "/" + id, k -> java.util.Optional.ofNullable(ExitTrim.fullCut(src, id, lookup))).orElse(null);
				if (c == null || c.index() >= m || ExitTrim.gateServed(src, id)) continue;
				cuts.put(id, c);
			}
		}
		var p = ExitTrim.apply(raw.plan(), cuts);
		return p == raw.plan() ? raw : new TilePlan(raw.info(), p, raw.landmarks(), raw.notes(), raw.firstPlazaReached());
	}

	/** The cuts of one settlement's exit roads (pure in the raw plans and the link rule; computed once per settlement). */
	java.util.TreeMap<String, ExitTrim.Cut> exitCuts(SettlementSite site) {
		return exitCuts.getOnce(site.id(), id -> ExitTrim.cuts(source(site), (ix, iz) -> tile(ix, iz).filter(t -> t.site().id().equals(site.id())).isPresent() ? rawPlan(ix, iz).plan() : null));
	}

	private TilePlan computePlan(int ix, int iz) {
		TileInfo info = tile(ix, iz).orElseThrow(() -> new IllegalArgumentException("no settlement plans tile " + ix + "," + iz));
		CityPlanner.Tile t = CityPlanner.planTile(source(info.site()), ix, iz, styles, probes.exact())
				.orElseThrow(() -> new IllegalStateException("tile " + ix + "," + iz + " is owned by " + info.settlementId() + " but not planned"));
		return new TilePlan(info, enabledStyles(t.plan()), t.landmarks(), t.notes(), t.firstPlazaReached());
	}

	private DistrictPlan enabledStyles(DistrictPlan p) {
		if (config.styles().isEmpty()) return p;
		List<LotBrief> lots = new ArrayList<>();
		boolean changed = false;
		for (LotBrief l : p.lots()) {
			if (config.styleEnabled(l.style()) || l.style().equals(StyleLibrary.FALLBACK)) { lots.add(l); continue; }
			lots.add(new LotBrief(l.id(), l.x(), l.z(), l.w(), l.d(), l.front(), l.type(), StyleLibrary.FALLBACK, l.minStoreys(), l.maxStoreys(), l.streets(), l.airRights(), l.frontY(), l.density(), l.status(), l.walk(), l.square()));
			changed = true;
		}
		return changed ? new DistrictPlan(p.genVersion(), p.seed(), p.district(), p.streets(), p.plazas(), lots, p.scene(), p.violations()) : p;
	}

	/**
	 * The buildings of the tile in lot order, from {@code GeneratorV1} with the lot briefs (a refused lot falls back to a small house or
	 * is left empty, as in a district scene). Empty for a tile no settlement plans.
	 */
	public List<BuildingPiece> buildings(int ix, int iz) {
		if (tile(ix, iz).isEmpty()) return List.of();
		return buildings.get(key(ix, iz), k -> Pieces.buildings(plan(ix, iz), probes.exact(), probes.biome()));
	}

	/**
	 * The ground boxes of every street and plaza of the tile ({@code Grading}), clipped to the tile and grouped into one piece per
	 * chunk that holds any, in chunk order. Empty for a tile no settlement plans.
	 */
	public List<GroundPiece> ground(int ix, int iz) {
		if (tile(ix, iz).isEmpty()) return List.of();
		return grounds.get(key(ix, iz), k -> Pieces.ground(plan(ix, iz), probes.exact()));
	}

	/**
	 * The cells of one piece inside {@code clip} (world coordinates; the caller clips to the chunk it writes), as roles, block ids and
	 * shapes in (y, z, x) order. Every cell has exactly one owner piece: a building owns what its boxes touch (air clears included), the
	 * tile's ground owns the rest of its boxes ({@code PieceRaster}); so the pieces of a tile rastered over any partition of space write
	 * each cell once, in any order. Pure: the same call gives the same cells on any thread, after any eviction. Throws
	 * {@link IllegalArgumentException} when the tile has no such piece (a saved piece of another generator or config).
	 */
	public void raster(PieceRef piece, Box3 clip, CellSink out) {
		Pieces.raster(piece, buildings(piece.ix(), piece.iz()), () -> ground(piece.ix(), piece.iz()), clip, out);
	}

	/**
	 * The stair cells one block outside {@code clip} beside the stairs this piece has inside it, each as its owner piece rasters it
	 * (DS36ROOFS r2). The mod writes them across the chunk border so that no stair of this chunk is ever reshaped by Minecraft against a
	 * neighbour that is not there yet. Nothing for a ground piece; throws {@link IllegalArgumentException} like {@link #raster}.
	 */
	public void seam(PieceRef piece, Box3 clip, CellSink out) {
		Pieces.seam(piece, buildings(piece.ix(), piece.iz()), clip, out);
	}

	/**
	 * The cells among {@code cells} (world positions) that are stairs of a building of tile {@code (ix, iz)}, each as its owner piece rasters it:
	 * {@link #seam} for a caller that already has the piece's own cells (the mod collects the neighbours of its border stairs while it writes).
	 */
	public void stairsAt(int ix, int iz, List<int[]> cells, CellSink out) {
		Pieces.stairsAt(buildings(ix, iz), cells, out);
	}

	/** A cell that becomes a stair block in the world: a roof stair, or an interior or porch stair with a facing. */
	public static boolean stairCell(dev.fractalbuildings.core.ir.Role role, dev.fractalbuildings.core.raster.Shape shape) { return Pieces.stair(role, shape); }

	/**
	 * Asks the workers to compute the buildings and ground of this tile and the plans and ground of its eight neighbours. Returns at once and never
	 * changes any answer: a later call for a tile a worker is computing waits for it, one a worker has not started computes it itself.
	 */
	public void prefetch(int ix, int iz) {
		if (workers == null) return;
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				int x = ix + dx, z = iz + dz;
				boolean self = dx == 0 && dz == 0;
				try {
					workers.execute(() -> {
						try {
							if (tile(x, z).isEmpty()) return;
							if (Thread.currentThread().isInterrupted()) return; // the pool is stopping (Workers.shutdown): do not start a multi-second step
							if (self) { buildings(x, z); ground(x, z); }
							else { plan(x, z); ground(x, z); } // CITYQ: the mod's spill clear reads the neighbours' ground near the shared border
						} catch (RuntimeException ignored) {
							// a failing prefetch fails again, visibly, in the call that needs the tile
						}
					});
				} catch (RuntimeException rejected) {
					return; // the pool is shutting down
				}
			}
		}
	}

	/**
	 * METROFILL R1: the quay runs of every settlement warm() would find, traced once (a settlement-wide value) ahead of its first waterfront tile.
	 * The whole trace: the mod no longer calls it (QUAYLAZY: {@link #warmQuaysAt} and the tile's own plan trace what a player reaches); tests, the CLI and perf rows do.
	 * Split from {@link #warm} (PERF row "Region layer + road network", regionperf): on a metropolis it is about 3x the exact probes of the rest of the region
	 * layer, and the first waterfront tile pays it anyway. Never changes any answer.
	 */
	public void warmQuays(int blockX, int blockZ, int radius) {
		TreeMap<String, SettlementSite> found = new TreeMap<>();
		int s = config.cellSize();
		for (int cz = Math.floorDiv(blockZ - radius, s); cz <= Math.floorDiv(blockZ + radius, s); cz++) {
			for (int cx = Math.floorDiv(blockX - radius, s); cx <= Math.floorDiv(blockX + radius, s); cx++) {
				siteOf(new Cell(cx, cz)).ifPresent(site -> found.put(site.id(), site));
			}
		}
		for (SettlementSite site : found.values()) dev.fractalbuildings.core.settlement.Districts.warmQuays(source(site), probes.exact());
	}

	/**
	 * The planned tile within {@code tiles} tiles of the one at the point that has quay runs and lies nearest to it (ties: smaller tile key), as
	 * {@code {ix, iz}}, or null. Traces the quays it looks at (the lazy trace). For the perf rows and tests.
	 */
	public int[] nearestQuayTile(int blockX, int blockZ, int tiles) {
		DistrictGrid.Tile c = grid.tileAt(blockX, blockZ);
		int[] best = null;
		long bestD = Long.MAX_VALUE;
		for (int dz = -tiles; dz <= tiles; dz++) {
			for (int dx = -tiles; dx <= tiles; dx++) {
				long d = (long) dx * dx + (long) dz * dz;
				if (d > bestD) continue;
				int ix = c.ix() + dx, iz = c.iz() + dz;
				Optional<TileInfo> info = tile(ix, iz);
				if (info.isEmpty() || dev.fractalbuildings.core.settlement.Districts.quayRuns(source(info.get().site()), ix, iz, probes.exact()).isEmpty()) continue;
				if (d < bestD || best == null || key(ix, iz) < key(best[0], best[1])) { bestD = d; best = new int[] {ix, iz}; }
			}
		}
		return best;
	}

	/**
	 * QUAYLAZY: the lazy quays of the tile at the point, through its owning settlement (nothing when no settlement plans the tile): only the quay
	 * clusters near that tile are traced and graded, not the whole city's. What {@link #plan} pays first for a waterfront tile. Never changes any answer.
	 */
	public void warmQuaysAt(int blockX, int blockZ) {
		DistrictGrid.Tile t = grid.tileAt(blockX, blockZ);
		tile(t.ix(), t.iz()).ifPresent(i -> dev.fractalbuildings.core.settlement.Districts.warmQuaysTile(source(i.site()), t.ix(), t.iz(), probes.exact()));
	}

	/**
	 * Resolves the region cells within {@code radius} blocks of the point and builds the street network and the junction field of every settlement found there and the intercity road edges near those cells,
	 * on the calling thread. Never changes any answer; call it from a worker ahead of travellers.
	 */
	public void warm(int blockX, int blockZ, int radius) { warm(blockX, blockZ, radius, false); }

	/**
	 * {@link #warm}, and with {@code quays} also {@link #warmQuays}: each settlement's quay trace right after its junction field, beside the
	 * road links where the probes run in parallel (PARREADS: the warmer's whole job, so a waterfront city's trace is done as early as it can be,
	 * on the warmer's pool, before a chunk's structure start has to run it on a worldgen thread the server may be waiting for).
	 */
	public void warm(int blockX, int blockZ, int radius, boolean quays) { warm(blockX, blockZ, radius, quays, false); }

	/**
	 * The settlements in the order {@link #warm} visits them: by (distance of the rim from the point, id), the one the point is in or
	 * nearest to first (CASCADE A4). Only the order of cache fills depends on it, never an answer.
	 */
	static List<SettlementSite> warmOrder(java.util.Collection<SettlementSite> sites, int blockX, int blockZ) {
		List<SettlementSite> out = new ArrayList<>(sites);
		out.sort(java.util.Comparator.<SettlementSite>comparingDouble(st -> StrictMath.hypot((double) st.centre().x() - blockX, (double) st.centre().z() - blockZ) - st.radius()).thenComparing(SettlementSite::id));
		return out;
	}

	/** The settlements an intercity link of the site's graph node joins it to, in edge order (none without roads or when the graph does not know the site). */
	public List<SettlementSite> linkNeighbours(SettlementSite site) {
		if (roads == null) return List.of();
		RoadNet.Settlement node = graphNode(site);
		if (node == null) return List.of();
		List<SettlementSite> out = new ArrayList<>();
		for (RoadNet.EdgeKey e : roads.edgesOf(node)) {
			dev.fractalbuildings.core.region.CellKey other = e.a().equals(node.owner()) ? e.b() : e.a();
			siteOf(new Cell(other.cx(), other.cz())).ifPresent(out::add);
		}
		return out;
	}

	/**
	 * {@link #warm(int, int, int, boolean)}; with {@code destinationOnly} (a teleport hold waits for this) only the settlement nearest the
	 * point and the ones it links to are warmed, the rest is left to a later full warm. Changes no answer either way.
	 */
	public void warm(int blockX, int blockZ, int radius, boolean quays, boolean destinationOnly) {
		TreeMap<String, SettlementSite> foundById = new TreeMap<>();
		int s = config.cellSize();
		if (fieldView != null) { // PARREADS: the cells' own resolutions in parallel where the probes can
			List<dev.fractalbuildings.core.region.CellKey> own = new ArrayList<>();
			for (int cz = Math.floorDiv(blockZ - radius, s); cz <= Math.floorDiv(blockZ + radius, s); cz++) {
				for (int cx = Math.floorDiv(blockX - radius, s); cx <= Math.floorDiv(blockX + radius, s); cx++) if (decidable(new Cell(cx, cz))) own.add(new dev.fractalbuildings.core.region.CellKey(cx, cz));
			}
			fieldView.resolveAll(own);
		}
		for (int cz = Math.floorDiv(blockZ - radius, s); cz <= Math.floorDiv(blockZ + radius, s); cz++) {
			for (int cx = Math.floorDiv(blockX - radius, s); cx <= Math.floorDiv(blockX + radius, s); cx++) {
				siteOf(new Cell(cx, cz)).ifPresent(site -> foundById.put(site.id(), site));
			}
		}
		List<SettlementSite> found = warmOrder(foundById.values(), blockX, blockZ);
		if (destinationOnly && !found.isEmpty()) { // the destination and what it links to
			TreeMap<String, SettlementSite> keep = new TreeMap<>();
			keep.put(found.get(0).id(), found.get(0));
			for (SettlementSite n : linkNeighbours(found.get(0))) keep.put(n.id(), n);
			found = warmOrder(keep.values(), blockX, blockZ);
		}
		List<dev.fractalbuildings.core.region.CellKey> cells = new ArrayList<>();
		if (roads != null) {
			if (destinationOnly) { // the point's cell and the cells of the settlements kept
				java.util.TreeSet<dev.fractalbuildings.core.region.CellKey> own = new java.util.TreeSet<>();
				own.add(new dev.fractalbuildings.core.region.CellKey(Math.floorDiv(blockX, s), Math.floorDiv(blockZ, s)));
				for (SettlementSite st : found) own.add(new dev.fractalbuildings.core.region.CellKey(st.claimant().cx(), st.claimant().cz()));
				cells.addAll(own);
			} else {
				for (int cz = Math.floorDiv(blockZ - radius, s); cz <= Math.floorDiv(blockZ + radius, s); cz++) {
					for (int cx = Math.floorDiv(blockX - radius, s); cx <= Math.floorDiv(blockX + radius, s); cx++) cells.add(new dev.fractalbuildings.core.region.CellKey(cx, cz));
				}
			}
		}
		final List<SettlementSite> order = found;
		// PARREADS: the junction fields (chunk reads) and the road links (lattice and station reads) are independent: side by side where the
		// probes can, each cell's links in parallel too; serially, in this order, otherwise
		probes.reads().fanout().each(2, part -> {
			if (part == 0) {
				for (SettlementSite site : order) {
					SkeletonSource src = source(site);
					List<Cell> member = new ArrayList<>(site.cells());
					if (member.size() > 1) probes.reads().fanout().each(member.size(), i -> src.skeleton(member.get(i))); // the network's cell skeletons (coarse reads) side by side
					src.network();
					source(site).junctions(probes.exact()); // JUNCTION: the settlement's junction field reads its whole streets' terrain once, ahead of the first tile
				}
				if (quays) for (SettlementSite site : order) dev.fractalbuildings.core.settlement.Districts.warmQuays(source(site), probes.exact());
			} else {
				probes.reads().fanout().each(cells.size(), i -> roads.edgesNearCell(cells.get(i)));
			}
		});
	}

	// ---- intercity roads (ADR 0004) ----

	/** Whether this world draws roads between settlements (config on, and not a fixed-site planner). */
	public boolean roadsEnabled() { return roads != null; }

	/** The road graph, or null when {@link #roadsEnabled} is false. */
	public RoadNet roads() { return roads; }

	/** The probes this planner reads (tests: ROADJOIN harness). */
	WorldProbes probes() { return probes; }

	/** The natural ground at a column: the exact probe's floor (first free block above the noise terrain, water ignored), before any feature or piece. LEAFORDER's tree room check. */
	public int naturalFloorY(int x, int z) { return probes.exact().floorY(x, z); }

	/** Water over the natural floor at (x, z) (the exact probe), 0 when dry. */
	public int naturalWaterDepth(int x, int z) { return probes.exact().waterDepth(x, z); }

	/**
	 * The road columns inside a chunk, sorted by (z, x), where two roads meet the lower edge key's column (RD4). Empty when the world has
	 * no roads. Pure: the same answer in any call order, on any thread, after any eviction. A cold region cell costs its routes (exact
	 * reads at the stations); {@link #warm} hides that.
	 */
	public List<RoadNet.Col> roadColumns(int chunkX, int chunkZ) {
		return roads == null ? List.of() : roadColumnsAround(chunkX, chunkZ).get(4);
	}

	/** Approach roads lie within the rim + the port offset + a column's reach of a settlement; a 3 x 3 block of chunks adds the chunk spacing. */
	private static final int APPROACH_REACH = RoadNet.PORT_OFFSET + 64;

	/**
	 * {@link #roadColumns} of the 3 x 3 chunks around a chunk, index {@code (dz + 1) * 3 + (dx + 1)} (entry 4 is the chunk itself): the
	 * intercity roads of the graph, and near a settlement the approaches that join them to its exits. Where two roads share a column the
	 * graph's wins (RD4), then the lower settlement id. The surface plan of a chunk needs its neighbours' columns for the shoulders.
	 */
	public List<List<RoadNet.Col>> roadColumnsAround(int chunkX, int chunkZ) {
		List<List<RoadNet.Col>> out = new ArrayList<>(9);
		List<RoadNet.Settlement> near = roads == null ? List.of() : roads.settlementsNear(chunkX * 16 + 8, chunkZ * 16 + 8, APPROACH_REACH);
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (roads == null) { out.add(List.of()); continue; }
				int cx = chunkX + dx, cz = chunkZ + dz;
				List<RoadNet.Col> r = roads.query(cx, cz, e -> linkState(e) != LinkState.DROPPED); // 6.22: a dropped link draws nothing
				if (near.isEmpty()) { out.add(r); continue; }
				TreeMap<Long, RoadNet.Col> merged = new TreeMap<>();
				for (RoadNet.Col c : r) merged.put(((long) c.z() << 32) ^ (c.x() & 0xffffffffL), c);
				long ck = ((long) (cz + (1 << 30)) << 32) | (long) (cx + (1 << 30));
				for (RoadNet.Settlement st : near) {
					Optional<SettlementSite> site = view.siteOf(new Cell(st.owner().cx(), st.owner().cz()));
					if (site.isEmpty()) continue;
					for (Approach ap : drawnApproaches(site.get(), st)) for (RoadNet.Col c : ap.byChunk().getOrDefault(ck, List.of())) merged.putIfAbsent(((long) c.z() << 32) ^ (c.x() & 0xffffffffL), c);
				}
				List<RoadNet.Col> all = new ArrayList<>(merged.values());
				all.sort((p, q) -> p.z() != q.z() ? Integer.compare(p.z(), q.z()) : Integer.compare(p.x(), q.x()));
				out.add(all);
			}
		}
		return out;
	}

	/** One approach road as drawn: its port (null when none matches) and its columns by chunk. */
	record Approach(dev.fractalbuildings.core.settlement.Road road, dev.fractalbuildings.core.road.RoadPort port, TreeMap<Long, List<RoadNet.Col>> byChunk, int steepJoins, int wetFailures, int alongRuns, int portExcess) {
		List<RoadNet.Col> columns() {
			List<RoadNet.Col> all = new ArrayList<>();
			for (List<RoadNet.Col> l : byChunk.values()) all.addAll(l);
			return all;
		}
	}

	/** How far from a street's centre line the exit itself may lie and still take the street's level, blocks (the gate stands at the street end). */
	private static final int EXIT_REACH = 6;

	/**
	 * The surface level (first free y) of the district street or plaza of {@code site} at column (x, z), or empty when none of the tiles
	 * around it that the site plans has one there: the street whose centre line passes nearest, within {@code width / 2 + 1 + extra}
	 * (ties by street id), else a plaza containing the column. ROADJOIN: the approach meets the street it joins.
	 */
	OptionalInt streetLevel(SettlementSite site, int x, int z, int extra) { return streetLevel(site, x, z, extra, 0); }

	/** As above; a plaza also counts when the column lies within {@code plazaExtra} of its rectangle. */
	OptionalInt streetLevel(SettlementSite site, int x, int z, int extra, int plazaExtra) {
		var home = grid.tileOf(x, z);
		int bestD = Integer.MAX_VALUE, bestY = 0;
		String bestId = "";
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				int ix = home.ix() + dx, iz = home.iz() + dz;
				Optional<TileInfo> info = tile(ix, iz);
				if (info.isEmpty() || !info.get().site().id().equals(site.id())) continue;
				for (var s : rawPlan(ix, iz).plan().streets()) {
					int[] hit = s.nearest(x, z);
					if (hit[0] > s.width() / 2 + 1 + extra) continue;
					if (hit[0] < bestD || (hit[0] == bestD && s.id().compareTo(bestId) < 0)) { bestD = hit[0]; bestY = hit[1]; bestId = s.id(); }
				}
			}
		}
		if (bestD != Integer.MAX_VALUE) return OptionalInt.of(bestY);
		for (int dz = -1; dz <= 1; dz++) {
			for (int dx = -1; dx <= 1; dx++) {
				int ix = home.ix() + dx, iz = home.iz() + dz;
				Optional<TileInfo> info = tile(ix, iz);
				if (info.isEmpty() || !info.get().site().id().equals(site.id())) continue;
				for (var p : rawPlan(ix, iz).plan().plazas()) {
					if (x >= p.x() - plazaExtra && x < p.x() + p.w() + plazaExtra && z >= p.z() - plazaExtra && z < p.z() + p.d() + plazaExtra) return OptionalInt.of(p.y());
				}
			}
		}
		return OptionalInt.empty();
	}

	/**
	 * Every approach of the settlement (exit to port, from its cell skeletons), each with its deck: the averaged exact surface at 8-block
	 * stations, the end at the port pinned to the road it joins (a graph route's deck, or the exact surface at a meeting point), so the
	 * two meet. Pure and shared by every chunk that draws it.
	 */
	/**
	 * One approach's drawn centre line, geometry only: the skeleton's corners, or the corners of the dry reroute that replaced them
	 * ({@link #approachNodes}), plus the exact port as last node. {@code nodes} are what {@code Curve.stations} turns into the road, so the
	 * district planner (lots off the approaches, ROADJOIN step 4) reads the same curve the road layer draws.
	 */
	public record ApproachNodes(dev.fractalbuildings.core.settlement.Road road, dev.fractalbuildings.core.road.RoadPort port, List<int[]> nodes) {}

	/**
	 * The nodes of every approach of the settlement, after the water rule's reroute (ROADJOIN step 2). Reads no district plan (it must not:
	 * the districts will read this), only exact water and surface reads at the approach's stations and the graph road's end deck. Cached.
	 */
	public List<ApproachNodes> approachNodes(SettlementSite site) {
		if (roads == null) return List.of();
		return nodeCache.get(site.id(), id -> {
			RoadNet.Settlement owner = null;
			for (Cell c : site.cells()) {
				var s = roads.settlement(new dev.fractalbuildings.core.region.CellKey(c.cx(), c.cz()));
				if (s.isPresent()) { owner = s.get(); break; }
			}
			return owner == null ? List.of() : computeApproachNodes(site, owner);
		});
	}

	/** {@link #approachNodes} as the district planner reads them (the {@code SkeletonSource.approaches()} of this settlement). */
	public List<SkeletonSource.ApproachLine> approachLines(SettlementSite site) {
		return approachNodes(site).stream().map(an -> new SkeletonSource.ApproachLine(an.nodes(), an.road().width())).toList();
	}

	private List<ApproachNodes> computeApproachNodes(SettlementSite site, RoadNet.Settlement owner) {
		List<ApproachNodes> out = new ArrayList<>();
		SkeletonSource src = source(site);
		List<dev.fractalbuildings.core.road.RoadPort> ports = roads.portsOf(owner);
		boolean air = owner.tier().atLeast(dev.fractalbuildings.core.settlement.Tier.TOWN);
		List<int[]> exits = new ArrayList<>();
		for (Cell cell : site.cells()) for (dev.fractalbuildings.core.settlement.Road r : src.skeleton(cell).approaches()) exits.add(new int[] {r.points().get(0).x(), r.points().get(0).z()});
		for (Cell cell : site.cells()) {
			for (dev.fractalbuildings.core.settlement.Road r : src.skeleton(cell).approaches()) {
				dev.fractalbuildings.core.settlement.Pt end = r.points().get(r.points().size() - 1);
				dev.fractalbuildings.core.road.RoadPort port = null;
				for (dev.fractalbuildings.core.road.RoadPort p : ports) if (port == null && Math.abs(p.x() - end.x()) <= 32 && Math.abs(p.z() - end.z()) <= 32) port = p;
				List<int[]> corners = new ArrayList<>();
				for (dev.fractalbuildings.core.settlement.Pt p : r.points()) corners.add(new int[] {p.x(), p.z()});
				NodeChoice best = routeNodes(owner, port, corners, r.width(), air, exits, null);
				if (best != null) out.add(new ApproachNodes(r, port, best.nodes())); // a run over MAX_DECK_RUN is dropped: the link ends at its port (6.16 DEADMAIN)
			}
		}
		return out;
	}

	/** The nodes an approach is drawn along (6.15 step 2), or null: a run along water over MAX_DECK_RUN on every attempt. */
	record NodeChoice(List<int[]> nodes, int wetFailures) {}

	/**
	 * The approach's nodes from {@code corners} (exit first) to the port: the corners themselves, or the best of up to {@link RoadNet#MAX_REROUTES}
	 * dry lattice reroutes round the crossings over {@code Profile.MAX_CROSSING} the exact reads found (fewest long runs, then wet failures).
	 * Null when every attempt has a run over {@code MAX_DECK_RUN}. {@code urban}: lattice columns the reroute pays extra to cross (null: none).
	 */
	private NodeChoice routeNodes(RoadNet.Settlement owner, dev.fractalbuildings.core.road.RoadPort port, List<int[]> corners, int width, boolean air, List<int[]> exits, java.util.Set<Long> urban) {
		OptionalInt pin = portDeck(owner, port);
		List<int[]> bestNodes = null;
		long bestFail = Long.MAX_VALUE;
		int bestLong = 0;
		java.util.TreeSet<Long> banned = new java.util.TreeSet<>();
		for (int attempt = 0; ; attempt++) {
			List<int[]> nodes = withPort(corners, port);
			double[][] st = dev.fractalbuildings.core.road.Curve.stations(nodes);
			if (st.length < 2) break;
			java.util.TreeMap<Integer, Integer> pins = new java.util.TreeMap<>();
			if (pin.isPresent()) pins.put(st.length - 1, pin.getAsInt());
			var prof = dev.fractalbuildings.core.road.Profile.approach(st, probes.roadExact(), pins, width, air);
			long score = prof.longRuns() * 1000L + prof.wetFailures();
			if (score < bestFail) { bestFail = score; bestNodes = nodes; bestLong = prof.longRuns(); }
			if (prof.wetFailures() == 0 || attempt >= RoadNet.MAX_REROUTES) break;
			// ADR 0004 N5: steer round the water the exact reads found, as a graph route does (the skeleton saw lattice nodes only)
			dev.fractalbuildings.core.road.Profile.Crossing bad = null;
			for (var c : prof.crossings()) if (c.length() > dev.fractalbuildings.core.road.Profile.MAX_CROSSING && (bad == null || c.length() > bad.length())) bad = c;
			if (bad == null) break;
			int mid = (bad.first() + bad.last()) / 2;
			int gx = Math.floorDiv((int) Math.floor(st[mid][0]), LATTICE) * LATTICE, gz = Math.floorDiv((int) Math.floor(st[mid][1]), LATTICE) * LATTICE;
			int[] from = corners.get(0), to = corners.get(corners.size() - 1);
			boolean added = false;
			for (int[] n : new int[][] {{gx, gz}, {gx + LATTICE, gz}, {gx, gz + LATTICE}, {gx + LATTICE, gz + LATTICE}}) {
				if ((n[0] == from[0] && n[1] == from[1]) || (n[0] == to[0] && n[1] == to[1])) continue;
				added |= banned.add(((long) n[1] << 32) ^ (n[0] & 0xffffffffL));
			}
			if (!added) break;
			List<int[]> re = rerouteApproach(from, to, banned, exits, urban);
			if (re.isEmpty()) break;
			corners = re;
		}
		return bestNodes != null && bestLong == 0 ? new NodeChoice(bestNodes, (int) bestFail) : null;
	}

	/** The skeleton ends on its coarse lattice; the road it joins starts exactly at the port (a meeting point is off the lattice). */
	private static List<int[]> withPort(List<int[]> corners, dev.fractalbuildings.core.road.RoadPort port) {
		List<int[]> nodes = new ArrayList<>(corners);
		int[] last = corners.get(corners.size() - 1);
		if (port != null && (port.x() != last[0] || port.z() != last[1])) nodes.add(new int[] {port.x(), port.z()});
		return nodes;
	}

	private OptionalInt portDeck(RoadNet.Settlement owner, dev.fractalbuildings.core.road.RoadPort port) {
		if (port == null) return OptionalInt.empty();
		return port.meeting() ? OptionalInt.of(probes.roadExact().surfaceY(port.x(), port.z())) : roads.endDeck(owner, port.other());
	}

	/** Drawn columns over water whose deck is not above the water, outside the graph road's end (the 4 stations round the port, which is its contract). */
	private int wetLowColumns(TreeMap<Long, List<RoadNet.Col>> byChunk, dev.fractalbuildings.core.road.RoadPort port) {
		int bad = 0;
		var t = probes.roadExact();
		for (List<RoadNet.Col> l : byChunk.values()) for (RoadNet.Col c : l) {
			if (port != null && Math.max(Math.abs(c.x() - port.x()), Math.abs(c.z() - port.z())) <= 4 * (int) dev.fractalbuildings.core.road.Curve.STATION) continue;
			if (t.waterDepth(c.x(), c.z()) > 0 && c.y() <= t.surfaceY(c.x(), c.z())) bad++;
		}
		return bad;
	}

	// ---- the link rule (6.22 DEADPORT; ADR 0004 WR2) ----

	/** The road graph's settlement that a site is (its first member cell the graph knows), or null. */
	private RoadNet.Settlement graphNode(SettlementSite site) {
		for (Cell c : site.cells()) {
			var s = roads.settlement(new dev.fractalbuildings.core.region.CellKey(c.cx(), c.cz()));
			if (s.isPresent()) return s.get();
		}
		return null;
	}

	private static RoadNet.EdgeKey edge(dev.fractalbuildings.core.region.CellKey p, dev.fractalbuildings.core.region.CellKey q) {
		return p.compareTo(q) < 0 ? new RoadNet.EdgeKey(p, q) : new RoadNet.EdgeKey(q, p);
	}

	/**
	 * What the road layer does with one link of the graph once both settlements have tried to join it (6.22 DEADPORT). A settlement
	 * <i>serves</i> a link when one of its approaches, from the skeleton's exit or from another exit gate (step a), reaches the link's port
	 * and keeps the water rule.
	 */
	public enum LinkState {
		/** Both ends serve it: the road and both approaches are drawn. */
		LIVE,
		/** An end does not serve it and nothing needs it (step b, ADR 0004 WR2 "drop the edge"): neither the road nor any approach to it is drawn. */
		DROPPED,
		/**
		 * One end does not serve it, but dropping it would orphan the settlement that does (that settlement has no other link both of whose
		 * ends serve it): the road and the serving end's approach stay, and the other port is a dead end (step c, waits for John).
		 */
		DEAD_END
	}

	/**
	 * The state of a link of the graph. A pure function of the two settlements' approaches (and, for a one-sided link, of the serving
	 * settlement's other links): it reads approaches, which read skeletons and district plans, never another link's state, so it does not
	 * depend on which settlement or chunk asks first. Links of a world without roads are {@link LinkState#LIVE}.
	 */
	public LinkState linkState(RoadNet.EdgeKey e) {
		if (roads == null) return LinkState.LIVE;
		return linkStates.get(e, k -> {
			boolean sa = served(k.a(), k.b()), sb = served(k.b(), k.a());
			if (sa && sb) return LinkState.LIVE;
			if (!sa && !sb) return LinkState.DROPPED;
			var far = roads.settlement(sa ? k.a() : k.b());
			if (far.isEmpty()) return LinkState.DROPPED;
			for (RoadNet.EdgeKey f : roads.edgesOf(far.get())) {
				if (!f.equals(k) && served(f.a(), f.b()) && served(f.b(), f.a())) return LinkState.DROPPED;
			}
			return LinkState.DEAD_END;
		});
	}

	/** Does the settlement {@code owner} have an approach to its port of the link to {@code other}? A settlement this planner cannot plan counts as serving it (no change). */
	private boolean served(dev.fractalbuildings.core.region.CellKey owner, dev.fractalbuildings.core.region.CellKey other) {
		Served sv = servedLinks.get(owner, this::computeServed);
		return sv.unknown() || sv.others().contains(other);
	}

	/** The far ends of the links a settlement's approaches reach ({@code unknown}: it cannot be planned here, so it serves them all). */
	private record Served(boolean unknown, java.util.TreeSet<dev.fractalbuildings.core.region.CellKey> others) {}

	/**
	 * The planner the link rule plans settlements' approaches with. Deciding one link plans the approaches (skeletons, district tiles) of
	 * both its settlements and of the serving one's neighbours; with caches far below the defaults (tests that force eviction, all at 1)
	 * each of those would replan every tile many times over, so they run on a second planner of the same world with the default caches.
	 * Every answer is a pure function of (seed, config, probes), so which planner computes it never changes it; the order argument of
	 * {@link #linkState} is untouched. With caches at or above the defaults (the game: {@link Caches#forHeap}) it is this planner.
	 */
	private WorldPlanner linkPlanner() {
		Caches d = Caches.DEFAULT;
		if (caps.tiles() >= d.tiles() && caps.sources() >= d.sources() && caps.skeletons() >= d.skeletons() && caps.region() >= d.region() && caps.plans() >= d.plans()) return this;
		WorldPlanner p = linkPlanner;
		if (p == null) {
			synchronized (this) {
				if ((p = linkPlanner) == null) linkPlanner = p = new WorldPlanner(seed, config, probes, null, Caches.DEFAULT, null);
			}
		}
		return p;
	}

	private Served computeServed(dev.fractalbuildings.core.region.CellKey owner) {
		WorldPlanner lp = linkPlanner();
		var s = lp.roads.settlement(owner);
		if (s.isEmpty()) return new Served(true, new java.util.TreeSet<>());
		Optional<SettlementSite> site = lp.siteOf(new Cell(owner.cx(), owner.cz()));
		if (site.isEmpty()) return new Served(true, new java.util.TreeSet<>());
		List<Approach> raw;
		try {
			raw = lp.approachRoads(site.get(), s.get());
		} catch (IllegalArgumentException e) {
			// a recorded window (tests, tools) that does not hold the whole settlement: unknown, so the link is not dropped for it. A world
			// with no window reads every column, so there this never hides anything.
			if (probes.window() == null) throw e;
			return new Served(true, new java.util.TreeSet<>());
		}
		java.util.TreeSet<dev.fractalbuildings.core.region.CellKey> out = new java.util.TreeSet<>();
		for (Approach a : raw) if (a.port() != null) out.add(a.port().other());
		return new Served(false, out);
	}

	/**
	 * The ports of the settlement's graph roads that no approach joins and whose road is still drawn: a road that ends in the open short of
	 * the town. Since 6.22 only the links kept because dropping them would orphan the far settlement ({@link LinkState#DEAD_END}, case c).
	 */
	public List<dev.fractalbuildings.core.road.RoadPort> deadEndPorts(SettlementSite site) {
		List<dev.fractalbuildings.core.road.RoadPort> out = new ArrayList<>();
		if (roads == null) return out;
		RoadNet.Settlement owner = graphNode(site);
		if (owner == null) return out;
		List<Approach> raw = approachRoads(site, owner);
		for (var p : roads.portsOf(owner)) {
			boolean joined = false;
			for (Approach a : raw) if (p.equals(a.port())) joined = true;
			if (!joined && linkState(edge(owner.owner(), p.other())) != LinkState.DROPPED) out.add(p);
		}
		return out;
	}

	/** The ports of the settlement whose link the road layer drops (step b): neither its road nor any approach to it is drawn. */
	public List<dev.fractalbuildings.core.road.RoadPort> droppedPorts(SettlementSite site) {
		List<dev.fractalbuildings.core.road.RoadPort> out = new ArrayList<>();
		if (roads == null) return out;
		RoadNet.Settlement owner = graphNode(site);
		if (owner == null) return out;
		for (var p : roads.portsOf(owner)) if (linkState(edge(owner.owner(), p.other())) == LinkState.DROPPED) out.add(p);
		return out;
	}

	/** Does a drawn approach of the settlement leave from the exit gate at (x, z)? (6.25 MAINSTUB; a site the graph does not know serves none.) */
	private boolean exitServedAt(SettlementSite site, int x, int z) {
		RoadNet.Settlement owner = graphNode(site);
		if (owner == null) return false;
		for (Approach a : drawnApproaches(site, owner)) {
			var p = a.road().points().get(0);
			if (Math.abs(p.x() - x) <= 2 && Math.abs(p.z() - z) <= 2) return true;
		}
		return false;
	}

	/** The approaches the road layer draws: {@link #approachRoads} without those to a dropped link. */
	List<Approach> drawnApproaches(SettlementSite site, RoadNet.Settlement owner) {
		List<Approach> out = new ArrayList<>();
		for (Approach a : approachRoads(site, owner)) {
			if (a.port() != null && linkState(edge(owner.owner(), a.port().other())) == LinkState.DROPPED) continue;
			out.add(a);
		}
		return out;
	}

	/**
	 * Every approach of the settlement that keeps the water rule, before the link rule ({@link #drawnApproaches} is what the road layer
	 * draws): those from the skeleton's exits ({@link #approachNodes}), then, for each port none of them joins, one from another exit gate
	 * (6.22 DEADPORT step a, {@link #alternateApproach}). Cached; pure.
	 */
	List<Approach> approachRoads(SettlementSite site, RoadNet.Settlement owner) {
		return rawApproaches.getOnce(site.id(), id -> {
			List<Approach> out = new ArrayList<>();
			boolean air = owner.tier().atLeast(dev.fractalbuildings.core.settlement.Tier.TOWN);
			for (ApproachNodes an : approachNodes(site)) {
				Approach a = drawApproach(site, owner, an, air);
				if (a != null) out.add(a);
			}
			List<int[]> used = new ArrayList<>();
			for (Approach a : out) used.add(new int[] {a.road().points().get(0).x(), a.road().points().get(0).z()});
			List<dev.fractalbuildings.core.road.RoadPort> ports = roads.portsOf(owner);
			for (int i = 0; i < ports.size(); i++) {
				var p = ports.get(i);
				if (out.stream().anyMatch(a -> p.equals(a.port()))) continue;
				Approach alt = alternateApproach(site, owner, p, i, used, air);
				if (alt == null) continue;
				out.add(alt);
				used.add(new int[] {alt.road().points().get(0).x(), alt.road().points().get(0).z()});
			}
			return List.copyOf(out);
		});
	}

	/** Exit gates tried for a port no skeleton approach joins (nearest first). */
	static final int ALT_TRIES = 4;
	/** Farthest an exit gate may lie from the port it is tried for, blocks, or the settlement's radius when larger (a big city's urban edge lies far inside its rim): beyond, the approach would be a road of its own round the town. */
	static final int ALT_REACH = 768;
	/** Extra cost of a reroute step through the urban footprint, in plain steps (as {@code LinkExits.URBAN_PENALTY}). */
	private static final long URBAN_PENALTY = 30;
	/** An alternative may cross lots only this close to its exit gate (the gate's own frontage); beyond, its columns would be the lots' (D5). */
	private static final int ALT_GATE_ZONE = 16;

	/**
	 * Step a of 6.22 DEADPORT: an approach to {@code port} from another of the settlement's exit gates, when no skeleton approach reached it
	 * (no exit faced it, its run along water was over {@code MAX_DECK_RUN}, or it had a deck on the water beside the gate). The gates
	 * ({@code GateKind.EXIT} with a road) within {@link #ALT_REACH} (or the radius) of the port, nearest first, at most {@link #ALT_TRIES}, not one another
	 * approach leaves from; each is routed like a skeleton approach's reroute (dry lattice nodes, grade limit, the town's urban columns
	 * dear) and drawn with the same pins and water rule; one whose columns lie on a lot beyond its gate is refused (the district plan did not
	 * keep the corridor free: plans read only {@link #approachNodes}, so this step never changes a district). The one with the fewest wet
	 * failures wins, ties to the nearer gate. Null when none qualifies.
	 */
	private Approach alternateApproach(SettlementSite site, RoadNet.Settlement owner, dev.fractalbuildings.core.road.RoadPort port, int portIndex, List<int[]> used, boolean air) {
		SkeletonSource src = source(site);
		java.util.Set<Long> urban = new java.util.TreeSet<>();
		List<dev.fractalbuildings.core.settlement.Gate> gates = new ArrayList<>();
		List<Cell> gateCells = new ArrayList<>();
		for (Cell cell : site.cells()) {
			var sk = src.skeleton(cell);
			var cr = sk.coarse();
			int[] mask = cr.mask();
			for (int j = 0; j < cr.nz(); j++) for (int i = 0; i < cr.nx(); i++) {
				if (mask[j * cr.nx() + i] != dev.fractalbuildings.core.settlement.CoarseRaster.URBAN) continue;
				int x = cr.x0() + i * cr.step(), z = cr.z0() + j * cr.step();
				urban.add(((long) z << 32) ^ (x & 0xffffffffL));
			}
			for (var g : sk.gates()) if (g.kind() == dev.fractalbuildings.core.settlement.GateKind.EXIT && g.road() != null) { gates.add(g); gateCells.add(cell); }
		}
		int step = probes.coarse().stride();
		int[] to = {Math.floorDiv(port.x() + step / 2, step) * step, Math.floorDiv(port.z() + step / 2, step) * step};
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < gates.size(); i++) {
			var g = gates.get(i);
			boolean taken = false;
			for (int[] u : used) taken |= u[0] == g.x() && u[1] == g.z();
			for (int k = 0; k < i; k++) taken |= gates.get(k).x() == g.x() && gates.get(k).z() == g.z(); // one try per gate position
			if (taken || StrictMath.hypot(g.x() - port.x(), g.z() - port.z()) > Math.max(ALT_REACH, site.radius())) continue;
			if (Math.floorMod(g.x(), step) != 0 || Math.floorMod(g.z(), step) != 0) continue; // a gate off the lattice (a trimmed bridge end): the reroute walks the lattice
			var tid = grid.tileOf(g.x(), g.z());
			Optional<TileInfo> ti = tile(tid.ix(), tid.iz());
			if (ti.isEmpty() || !ti.get().site().id().equals(site.id())) continue; // the street must be there to meet (ROADJOIN step 3)
			order.add(i);
		}
		order.sort((p, q) -> {
			var a = gates.get(p);
			var b = gates.get(q);
			long da = (long) (a.x() - port.x()) * (a.x() - port.x()) + (long) (a.z() - port.z()) * (a.z() - port.z());
			long db = (long) (b.x() - port.x()) * (b.x() - port.x()) + (long) (b.z() - port.z()) * (b.z() - port.z());
			if (da != db) return Long.compare(da, db);
			return a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z());
		});
		// keep clear of the gates other approaches leave from, as the skeleton approaches' reroutes do (the other exit gates are plain street ends)
		List<int[]> exits = new ArrayList<>(used);
		for (Cell cell : site.cells()) for (var r : src.skeleton(cell).approaches()) exits.add(new int[] {r.points().get(0).x(), r.points().get(0).z()});
		Approach best = null;
		for (int n = 0; n < order.size() && n < ALT_TRIES; n++) {
			var g = gates.get(order.get(n));
			int[] from = {g.x(), g.z()};
			if (from[0] == to[0] && from[1] == to[1]) continue;
			List<int[]> corners = rerouteApproach(from, to, java.util.Set.of(), exits, urban);
			if (corners.isEmpty()) continue;
			NodeChoice nc = routeNodes(owner, port, corners, port.width(), air, exits, urban);
			if (nc == null) continue;
			Cell gc = gateCells.get(order.get(n));
			List<dev.fractalbuildings.core.settlement.Pt> pts = new ArrayList<>();
			for (int[] c : nc.nodes()) {
				if (c[0] == port.x() && c[1] == port.z() && !pts.isEmpty()) break; // the exact port is a node, not a corner
				pts.add(new dev.fractalbuildings.core.settlement.Pt(c[0], c[1]));
			}
			if (pts.size() < 2) pts.add(new dev.fractalbuildings.core.settlement.Pt(port.x(), port.z()));
			var road = new dev.fractalbuildings.core.settlement.Road(dev.fractalbuildings.core.settlement.Ids.approach(gc.cx(), gc.cz(), ALT_ID + portIndex),
				port.width() <= 3 ? dev.fractalbuildings.core.settlement.RoadKind.PATH : dev.fractalbuildings.core.settlement.RoadKind.MAIN, port.width(), pts, List.of());
			Approach a = drawApproach(site, owner, new ApproachNodes(road, port, nc.nodes()), air);
			if (a == null || onLots(a, from)) continue;
			if (best == null || a.wetFailures() < best.wetFailures()) best = a;
			if (best.wetFailures() == 0) break;
		}
		return best;
	}

	/** Approach ids from another exit gate are {@code k<cx>_<cz>_<ALT_ID + port index>}: apart from a cell's own (one per port it serves). */
	static final int ALT_ID = 90;

	/** Does a drawn column of the approach, farther than {@link #ALT_GATE_ZONE} from its gate, lie on a lot of a tile its settlement plans? */
	private boolean onLots(Approach a, int[] gate) {
		java.util.TreeMap<Long, List<LotBrief>> lots = new java.util.TreeMap<>();
		for (RoadNet.Col c : a.columns()) {
			if (Math.max(Math.abs(c.x() - gate[0]), Math.abs(c.z() - gate[1])) <= ALT_GATE_ZONE) continue;
			var id = grid.tileOf(c.x(), c.z());
			List<LotBrief> l = lots.computeIfAbsent(key(id.ix(), id.iz()), k -> tile(id.ix(), id.iz()).isPresent() ? rawPlan(id.ix(), id.iz()).plan().lots() : List.of());
			for (LotBrief b : l) if (c.x() >= b.x() && c.x() < b.x() + b.w() && c.z() >= b.z() && c.z() < b.z() + b.d()) return true;
		}
		return false;
	}

	/** One approach as drawn (street pins at the exit and wherever it meets a street, the port pin, the water rule), or null when it breaks WR2/WR4. */
	private Approach drawApproach(SettlementSite site, RoadNet.Settlement owner, ApproachNodes an, boolean air) {
		var r = an.road();
		double[][] st = dev.fractalbuildings.core.road.Curve.stations(an.nodes());
		if (st.length < 2) return null;
		java.util.TreeMap<Integer, Integer> pins = new java.util.TreeMap<>();
		// the exit end meets the district street (ROADJOIN: surfaces within a step); the port end is the graph road's contract
		int lastRun = -1;
		for (int k = 0; k < st.length - 1 && k < 24; k++) {
			OptionalInt lv = streetLevel(site, (int) Math.round(st[k][0]), (int) Math.round(st[k][1]), k == 0 ? EXIT_REACH : 0);
			if (lv.isEmpty()) { if (k == 0) pins.clear(); break; }
			pins.put(k, lv.getAsInt());
			lastRun = k;
		}
		OptionalInt pin = portDeck(owner, an.port());
		// every other street the road crosses or runs beside (a crossing, a street end): the surfaces meet there too (architect, ROADJOIN "joins are level").
		// A level the grade limit cannot reach from the pin before it, or that would put the port pin out of reach, is left alone.
		if (!pins.isEmpty()) {
			int prevK = pins.lastKey(), prevY = pins.get(prevK), half = r.width() / 2 + (int) dev.fractalbuildings.core.road.Curve.STATION / 2;
			for (int k = lastRun + 1; k < st.length - 1; k++) {
				OptionalInt lv = streetLevel(site, (int) Math.round(st[k][0]), (int) Math.round(st[k][1]), half, half);
				if (lv.isEmpty()) continue;
				int y = lv.getAsInt();
				if (Math.abs(y - prevY) > dev.fractalbuildings.core.road.Profile.MAX_STEP * (k - prevK)) continue;
				if (pin.isPresent() && Math.abs(y - pin.getAsInt()) > dev.fractalbuildings.core.road.Profile.MAX_STEP * (st.length - 1 - k)) continue;
				pins.put(k, y);
				prevK = k;
				prevY = y;
			}
		}
		if (pin.isPresent()) pins.put(st.length - 1, pin.getAsInt());
		var prof = dev.fractalbuildings.core.road.Profile.approach(st, probes.roadExact(), pins, r.width(), air);
		var byChunk = RoadNet.raster(st, prof.deck(), prof.span(), r.width());
		// ADR 0004 WR2/WR4: no road column on water outside a deck above it. An approach that still has one (a stream on a slope beside the
		// gate: the street cannot rise to the water, the water cannot fall to the street) is not built, and its port is a dead end (deadEndPorts).
		if (wetLowColumns(byChunk, an.port()) > 0) return null;
		return new Approach(r, an.port(), byChunk, prof.steepJoins(), prof.wetFailures(), prof.alongRuns(), prof.portExcess());
	}

	/**
	 * A dry lattice path (corners only) from an approach's exit to its port's lattice point, avoiding the banned nodes: the cost of
	 * {@code LinkExits.approach} (water forbidden at nodes, a grade limit) on the coarse probe, inside the box of the two grown by 128.
	 * Empty when none exists. The skeleton may read lattice columns only (ADR 0003 D4), which is all this does.
	 */
	private List<int[]> rerouteApproach(int[] from, int[] to, java.util.Set<Long> banned, List<int[]> exits, java.util.Set<Long> urban) {
		var coarse = probes.coarse();
		int step = coarse.stride();
		int grow = 128;
		var bounds = new dev.fractalbuildings.core.terrain.LatticePath.Bounds(
			Math.floorDiv(Math.min(from[0], to[0]) - grow, step) * step, Math.floorDiv(Math.min(from[1], to[1]) - grow, step) * step,
			Math.floorDiv(Math.max(from[0], to[0]) + grow, step) * step, Math.floorDiv(Math.max(from[1], to[1]) + grow, step) * step);
		var cost = new dev.fractalbuildings.core.terrain.LatticePath.Cost() {
			@Override public long step(int x, int z, int nx, int nz) {
				if (banned.contains(((long) nz << 32) ^ (nx & 0xffffffffL)) || coarse.water(nx, nz)) return FORBIDDEN;
				// keep clear of the settlement's other gates: an approach touches a street only at its own exit (architect, ROADJOIN)
				for (int[] e : exits) if ((nx != to[0] || nz != to[1]) && (e[0] != from[0] || e[1] != from[1]) && Math.max(Math.abs(nx - e[0]), Math.abs(nz - e[1])) <= 33) return FORBIDDEN;
				boolean diag = x != nx && z != nz;
				int dy = Math.abs(coarse.floorY(nx, nz) - coarse.floorY(x, z));
				int len = diag ? step * 14 / 10 : step;
				if (dy * 3 > len) return FORBIDDEN;
				// an approach leaves town (ROADJOIN step 4, as LinkExits.approach): crossing the urban footprint is a last resort
				long town = urban != null && urban.contains(((long) nz << 32) ^ (nx & 0xffffffffL)) ? URBAN_PENALTY * step : 0;
				return (diag ? 14L : 10L) * step + 60L * dy + town;
			}
		};
		List<dev.fractalbuildings.core.terrain.LatticePath.Pt> path;
		try {
			path = dev.fractalbuildings.core.terrain.LatticePath.find(new dev.fractalbuildings.core.terrain.LatticePath.Pt(from[0], from[1]),
				new dev.fractalbuildings.core.terrain.LatticePath.Pt(to[0], to[1]), cost, bounds, step, -1, dev.fractalbuildings.core.terrain.LatticePath.DEFAULT_MAX_EXPANSIONS, false);
		} catch (IllegalArgumentException e) {
			return List.of();
		}
		if (path.size() < 2) return List.of();
		List<int[]> pts = new ArrayList<>();
		for (int i = 0; i < path.size(); i++) {
			boolean corner = i == 0 || i == path.size() - 1
				|| Integer.signum(path.get(i).x() - path.get(i - 1).x()) != Integer.signum(path.get(i + 1).x() - path.get(i).x())
				|| Integer.signum(path.get(i).z() - path.get(i - 1).z()) != Integer.signum(path.get(i + 1).z() - path.get(i).z());
			if (corner) pts.add(new int[] {path.get(i).x(), path.get(i).z()});
		}
		return pts;
	}

	/** A road column may lie in a district's piece only near a settlement: its rim + the port offset + one tile, plus the chunk. */
	private static final int DISTRICT_REACH = RoadNet.PORT_OFFSET + 160 + 32;

	/** Can a settlement's district reach chunk (cx, cz)? False only when the road net knows of no settlement within {@link #DISTRICT_REACH}: a cheap test for passes that run in every chunk. */
	public boolean districtsNear(int chunkX, int chunkZ) {
		return roads == null || !roads.settlementsNear(chunkX * 16 + 8, chunkZ * 16 + 8, DISTRICT_REACH).isEmpty();
	}

	/**
	 * The columns of a chunk that a district draws (D5: the district wins where a road and a district meet): the xz extent of every
	 * ground box of the chunk's tile, every building piece's box and every lot of its plan. Null when no district claims any column of the chunk: no settlement
	 * within {@link #DISTRICT_REACH} of it, or no tile planned there. Index {@code (z & 15) * 16 + (x & 15)}.
	 */
	public boolean[] districtColumns(int chunkX, int chunkZ) {
		if (roads == null) return null;
		if (roads.settlementsNear(chunkX * 16 + 8, chunkZ * 16 + 8, DISTRICT_REACH).isEmpty()) return null;
		DistrictGrid.TileId id = grid.tileOf(chunkX * 16 + 8, chunkZ * 16 + 8);
		if (tile(id.ix(), id.iz()).isEmpty()) return null;
		boolean[] mask = new boolean[256];
		boolean[] any = {false};
		int x0 = chunkX * 16, z0 = chunkZ * 16;
		for (GroundPiece g : ground(id.ix(), id.iz())) {
			if (g.chunkX() != chunkX || g.chunkZ() != chunkZ) continue;
			for (dev.fractalbuildings.core.ir.Box b : g.boxes()) mark(mask, any, x0, z0, b.box().x(), b.box().z(), b.box().maxX(), b.box().maxZ());
		}
		for (BuildingPiece b : buildings(id.ix(), id.iz())) { // shells overhang the street (air rights): the piece's box, not just the lot
			if (b.box().x() < x0 + 16 && b.box().maxX() > x0 && b.box().z() < z0 + 16 && b.box().maxZ() > z0) mark(mask, any, x0, z0, b.box().x(), b.box().z(), b.box().maxX(), b.box().maxZ());
		}
		for (LotBrief l : plan(id.ix(), id.iz()).plan().lots()) mark(mask, any, x0, z0, l.x(), l.z(), l.x() + l.w(), l.z() + l.d());
		return any[0] ? mask : null;
	}

	/** How far below a road column's deck its fill and the ground it reads can reach (the embankment and the scanned ground of {@code RoadSurface}). */
	static final int ROAD_DEPTH = 24;

	/** What a building piece writes into a world: its box and the deep foundation {@code Pieces.deepen} adds under it (not in {@link BuildingPiece#box}). */
	private static Box3 rastered(Box3 b) {
		return new Box3(b.x(), b.y() - Pieces.FOUNDATION_BELOW, b.z(), b.sx(), b.sy() + Pieces.FOUNDATION_BELOW, b.sz());
	}

	/**
	 * Could a piece of ours own a cell inside {@code box}, or read the world there? True when a building piece's box or a ground
	 * piece's box of a planned tile intersects it, or when a road column lies within
	 * {@link dev.fractalbuildings.core.road.RoadSurface#CUT_REACH} + 1 blocks of it (its shoulders and terraces) and the box reaches
	 * higher than {@link #ROAD_DEPTH} under the deck (a road reads its ground and fills and clears down and up to it). Pure, like every
	 * answer here.
	 *
	 * <p>For vanilla features that write across a chunk border and have no structure check, such as {@code LakeFeature} (ORDERFAIL): a
	 * lake from a neighbour chunk decorated after ours overwrites our pieces, one decorated before is overwritten by them, so the world
	 * would depend on chunk order. The mod cancels such a feature when this answers true for its box, a function of position only.
	 */
	public boolean claimsNear(Box3 box) {
		int cx0 = Math.floorDiv(box.x(), 16), cx1 = Math.floorDiv(box.maxX() - 1, 16);
		int cz0 = Math.floorDiv(box.z(), 16), cz1 = Math.floorDiv(box.maxZ() - 1, 16);
		java.util.TreeSet<Long> seen = new java.util.TreeSet<>();
		for (int cz = cz0; cz <= cz1; cz++) {
			for (int cx = cx0; cx <= cx1; cx++) {
				if (!districtsNear(cx, cz)) continue;
				DistrictGrid.TileId id = grid.tileOf(cx * 16 + 8, cz * 16 + 8);
				if (!seen.add(key(id.ix(), id.iz())) || tile(id.ix(), id.iz()).isEmpty()) continue;
				for (BuildingPiece b : buildings(id.ix(), id.iz())) if (rastered(b.box()).intersects(box)) return true;
				for (GroundPiece g : ground(id.ix(), id.iz())) if (g.box().intersects(box)) return true;
			}
		}
		if (roads == null) return false;
		int reach = dev.fractalbuildings.core.road.RoadSurface.CUT_REACH + 1;
		int rx0 = box.x() - reach, rx1 = box.maxX() - 1 + reach, rz0 = box.z() - reach, rz1 = box.maxZ() - 1 + reach;
		for (int cz = Math.floorDiv(rz0, 16); cz <= Math.floorDiv(rz1, 16); cz++) {
			for (int cx = Math.floorDiv(rx0, 16); cx <= Math.floorDiv(rx1, 16); cx++) {
				for (RoadNet.Col c : roadColumns(cx, cz)) {
					if (c.x() < rx0 || c.x() > rx1 || c.z() < rz0 || c.z() > rz1) continue;
					if (box.maxY() - 1 >= c.y() - ROAD_DEPTH) return true;
				}
			}
		}
		return false;
	}

	/**
	 * The cells of {@code box} that {@link #claimsNear} counts, cell by cell: inside a building piece's rastered box or a ground piece's
	 * box of a planned tile, or (roads) in a column within {@code CUT_REACH + 1} blocks of a road column (in x and in z) and no lower
	 * than {@link #ROAD_DEPTH} under its deck. {@code claimRegion(box).isEmpty() == !claimsNear(box)}. Pure.
	 *
	 * <p>LEAFORDER: a vanilla tree's room check ({@code TreeFeature.getMaxFreeTreeHeight}) reads cells that our pieces of a neighbour
	 * chunk may already have written or not yet; the mod's {@code TreeClip} answers these cells from the natural terrain instead.
	 */
	public ClaimRegion claimRegion(Box3 box) {
		List<Box3> boxes = new ArrayList<>(), buildingBoxes = new ArrayList<>();
		int cx0 = Math.floorDiv(box.x(), 16), cx1 = Math.floorDiv(box.maxX() - 1, 16);
		int cz0 = Math.floorDiv(box.z(), 16), cz1 = Math.floorDiv(box.maxZ() - 1, 16);
		java.util.TreeSet<Long> seen = new java.util.TreeSet<>();
		for (int cz = cz0; cz <= cz1; cz++) {
			for (int cx = cx0; cx <= cx1; cx++) {
				if (!districtsNear(cx, cz)) continue;
				DistrictGrid.TileId id = grid.tileOf(cx * 16 + 8, cz * 16 + 8);
				if (!seen.add(key(id.ix(), id.iz())) || tile(id.ix(), id.iz()).isEmpty()) continue;
				for (BuildingPiece b : buildings(id.ix(), id.iz())) { Box3 r = rastered(b.box()); if (r.intersects(box)) { boxes.add(r); buildingBoxes.add(r); } }
				for (GroundPiece g : ground(id.ix(), id.iz())) if (g.box().intersects(box)) boxes.add(g.box());
			}
		}
		int[] roadMinY = null;
		if (roads != null) {
			int reach = dev.fractalbuildings.core.road.RoadSurface.CUT_REACH + 1;
			int rx0 = box.x() - reach, rx1 = box.maxX() - 1 + reach, rz0 = box.z() - reach, rz1 = box.maxZ() - 1 + reach;
			// a tree's room (5 wide) plus the reach fits in the 3 x 3 chunks round its centre chunk: one roadColumnsAround instead of one per chunk
			int mx = Math.floorDiv(box.x() + box.sx() / 2, 16), mz = Math.floorDiv(box.z() + box.sz() / 2, 16);
			List<List<RoadNet.Col>> around = Math.floorDiv(rx0, 16) >= mx - 1 && Math.floorDiv(rx1, 16) <= mx + 1 && Math.floorDiv(rz0, 16) >= mz - 1 && Math.floorDiv(rz1, 16) <= mz + 1 ? roadColumnsAround(mx, mz) : null;
			for (int cz = Math.floorDiv(rz0, 16); cz <= Math.floorDiv(rz1, 16); cz++) {
				for (int cx = Math.floorDiv(rx0, 16); cx <= Math.floorDiv(rx1, 16); cx++) {
					for (RoadNet.Col c : around != null ? around.get((cz - mz + 1) * 3 + (cx - mx + 1)) : roadColumns(cx, cz)) {
						if (c.x() < rx0 || c.x() > rx1 || c.z() < rz0 || c.z() > rz1) continue;
						int floor = c.y() - ROAD_DEPTH;
						if (box.maxY() - 1 < floor) continue;
						if (roadMinY == null) { roadMinY = new int[box.sx() * box.sz()]; java.util.Arrays.fill(roadMinY, Integer.MAX_VALUE); }
						for (int z = Math.max(box.z(), c.z() - reach); z <= Math.min(box.maxZ() - 1, c.z() + reach); z++) {
							for (int x = Math.max(box.x(), c.x() - reach); x <= Math.min(box.maxX() - 1, c.x() + reach); x++) {
								int i = (z - box.z()) * box.sx() + (x - box.x());
								roadMinY[i] = Math.min(roadMinY[i], floor);
							}
						}
					}
				}
			}
		}
		return new ClaimRegion(box, List.copyOf(boxes), List.copyOf(buildingBoxes), roadMinY);
	}

	/** What {@link #claimRegion} found: the piece boxes meeting the query box and, per column of it, the lowest y a road may claim from. */
	public static final class ClaimRegion {
		private final Box3 box;
		private final List<Box3> boxes, buildingBoxes;
		private final int[] roadMinY;

		ClaimRegion(Box3 box, List<Box3> boxes, List<Box3> buildingBoxes, int[] roadMinY) { this.box = box; this.boxes = boxes; this.buildingBoxes = buildingBoxes; this.roadMinY = roadMinY; }

		/** No cell of the query box may be claimed. */
		public boolean isEmpty() { return boxes.isEmpty() && roadMinY == null; }

		/** Whether a piece or road of ours may claim (or read) the cell. A cell outside the query box: false. */
		public boolean contains(int x, int y, int z) {
			if (!box.contains(x, y, z)) return false;
			for (Box3 b : boxes) if (b.contains(x, y, z)) return true;
			return roadMinY != null && y >= roadMinY[(z - box.z()) * box.sx() + (x - box.x())];
		}

		/** Whether the cell is inside a building piece's rastered box (its shell, air and deep foundation). A cell outside the query box: false. */
		public boolean containsBuilding(int x, int y, int z) {
			if (!box.contains(x, y, z)) return false;
			for (Box3 b : buildingBoxes) if (b.contains(x, y, z)) return true;
			return false;
		}
	}

	private static void mark(boolean[] mask, boolean[] any, int x0, int z0, int ax, int az, int bx, int bz) {
		for (int z = Math.max(az, z0); z < Math.min(bz, z0 + 16); z++) {
			for (int x = Math.max(ax, x0); x < Math.min(bx, x0 + 16); x++) {
				mask[(z - z0) * 16 + (x - x0)] = true;
				any[0] = true;
			}
		}
	}

	/**
	 * The nearest settlement of at least {@code min} tier whose cell lies within {@code maxCells} cells of the point's cell, by distance
	 * from the point to its centre (ties to the lower id). Reads the region field only.
	 */
	public Optional<SettlementInfo> nearest(int x, int z, Tier min, int maxCells) {
		Cell c0 = CellGeometry.owner(field, x, z);
		SettlementInfo best = null;
		for (int dz = -maxCells; dz <= maxCells; dz++) {
			for (int dx = -maxCells; dx <= maxCells; dx++) {
				Optional<SettlementSite> o = siteOf(new Cell(c0.cx() + dx, c0.cz() + dz));
				if (o.isEmpty() || !o.get().tier().atLeast(min)) continue;
				SettlementSite site = o.get();
				double d = StrictMath.hypot(site.centre().x() - x, site.centre().z() - z);
				if (best == null || d < best.distance() || (d == best.distance() && site.id().compareTo(best.site().id()) < 0)) best = new SettlementInfo(site, d);
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * Does the block (x, z) lie within {@code margin} blocks of any settlement's footprint (the disc of its radius around its centre)?
	 * This is the vanilla-structure veto's question (plan B7): it reads the region field only (no districts, no road networks) over the
	 * cells a settlement of at most {@code maxRadius} could reach from, so it is a pure function of (seed, config, point, margin).
	 * Only settlements decided by the field count, as everywhere else.
	 */
	public boolean footprintNear(int x, int z, int margin) {
		Cell c0 = CellGeometry.owner(field, x, z);
		int reach = Math.floorDiv(config.maxRadius() + margin, config.cellSize()) + 2;
		for (int dz = -reach; dz <= reach; dz++) {
			for (int dx = -reach; dx <= reach; dx++) {
				Optional<SettlementSite> o = siteOf(new Cell(c0.cx() + dx, c0.cz() + dz));
				if (o.isEmpty()) continue;
				SettlementSite site = o.get();
				double lim = (double) site.radius() + margin;
				double ex = site.centre().x() - x, ez = site.centre().z() - z;
				if (ex * ex + ez * ez <= lim * lim) return true;
			}
		}
		return false;
	}

	/** Drops every memo (memory pressure, tests). Answers do not change; the next questions recompute. */
	public void evictAll() {
		sources.clear();
		tiles.clear();
		plans.clear();
		rawPlans.clear();
		buildings.clear();
		grounds.clear();
		if (roads != null) roads.evictAll();
		// the approach and link-state memos (nodeCache, rawApproaches, linkStates, servedLinks) are kept, as before 6.22: rebuilding them replans settlements, and their own LRU caps bound them
	}
}
