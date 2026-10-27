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
	/** The approach roads of each settlement (exit to port), by chunk: the road layer draws them, the districts do not. Null with no roads. */
	private final BoundedCache<String, TreeMap<Long, List<RoadNet.Col>>> approaches;
	private final RegionView view;
	private final StyleField styles;
	private final Caches caps;
	private final BoundedCache<String, SkeletonSource> sources;
	private final BoundedCache<Long, Optional<TileInfo>> tiles;
	private final FlightCache<Long, TilePlan> plans;
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
		this.fieldView = fixed != null ? null : new FieldRegionView(rp, probes.window(), caps.region());
		this.roads = fixed == null && config.intercityRoads()
				? new RoadNet(field, fieldView, probes.window(), probes.region(), probes.roadExact(), caps.region()) : null;
		this.approaches = roads == null ? null : new BoundedCache<>(caps.sources());
		this.view = fixed != null ? new FixedSiteView(fixed, field) : new FieldSiteView(fieldView, field, roads);
		this.styles = new StyleField(probes.biome(), seed);
		this.sources = new BoundedCache<>(caps.sources(), caps.graceNanos(), caps.hardFactor(), System::nanoTime); // det-ok: retention only, never an answer
		this.tiles = new BoundedCache<>(caps.tiles());
		this.plans = new FlightCache<>(caps.plans(), caps.graceNanos(), caps.hardFactor(), System::nanoTime); // det-ok: retention only, never an answer
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
		this.approaches = null;
		this.view = null;
		this.styles = null;
		this.sources = null;
		this.tiles = null;
		this.plans = null;
		this.buildings = null;
		this.grounds = null;
	}

	public long seed() { return seed; }

	public WorldConfig config() { return config; }

	public DistrictGrid grid() { return grid; }

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
	private SkeletonSource source(SettlementSite site) {
		return sources.get(site.id(), id -> new SkeletonSource.Direct(seed, view, site, probes.coarse(), TierPrograms.of(site.tier()), caps.skeletons()));
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
		return plans.get(key(ix, iz), k -> computePlan(ix, iz));
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
	 * Resolves the region cells within {@code radius} blocks of the point and builds the street network and the junction field of every settlement found there and the intercity road edges near those cells,
	 * on the calling thread. Never changes any answer; call it from a worker ahead of travellers.
	 */
	public void warm(int blockX, int blockZ, int radius) {
		TreeMap<String, SettlementSite> found = new TreeMap<>();
		int s = config.cellSize();
		for (int cz = Math.floorDiv(blockZ - radius, s); cz <= Math.floorDiv(blockZ + radius, s); cz++) {
			for (int cx = Math.floorDiv(blockX - radius, s); cx <= Math.floorDiv(blockX + radius, s); cx++) {
				siteOf(new Cell(cx, cz)).ifPresent(site -> found.put(site.id(), site));
			}
		}
		for (SettlementSite site : found.values()) {
			source(site).network();
			source(site).junctions(probes.exact()); // JUNCTION: the settlement's junction field reads its whole streets' terrain once, ahead of the first tile
		}
		if (roads != null) {
			for (int cz = Math.floorDiv(blockZ - radius, s); cz <= Math.floorDiv(blockZ + radius, s); cz++) {
				for (int cx = Math.floorDiv(blockX - radius, s); cx <= Math.floorDiv(blockX + radius, s); cx++) roads.edgesNearCell(new dev.fractalbuildings.core.region.CellKey(cx, cz));
			}
		}
	}

	// ---- intercity roads (ADR 0004) ----

	/** Whether this world draws roads between settlements (config on, and not a fixed-site planner). */
	public boolean roadsEnabled() { return roads != null; }

	/** The road graph, or null when {@link #roadsEnabled} is false. */
	public RoadNet roads() { return roads; }

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
				List<RoadNet.Col> r = roads.query(cx, cz);
				if (near.isEmpty()) { out.add(r); continue; }
				TreeMap<Long, RoadNet.Col> merged = new TreeMap<>();
				for (RoadNet.Col c : r) merged.put(((long) c.z() << 32) ^ (c.x() & 0xffffffffL), c);
				long ck = ((long) (cz + (1 << 30)) << 32) | (long) (cx + (1 << 30));
				for (RoadNet.Settlement st : near) {
					Optional<SettlementSite> site = view.siteOf(new Cell(st.owner().cx(), st.owner().cz()));
					if (site.isEmpty()) continue;
					List<RoadNet.Col> a = approachesOf(site.get(), st).get(ck);
					if (a != null) for (RoadNet.Col c : a) merged.putIfAbsent(((long) c.z() << 32) ^ (c.x() & 0xffffffffL), c);
				}
				List<RoadNet.Col> all = new ArrayList<>(merged.values());
				all.sort((p, q) -> p.z() != q.z() ? Integer.compare(p.z(), q.z()) : Integer.compare(p.x(), q.x()));
				out.add(all);
			}
		}
		return out;
	}

	/** The columns of every approach of the settlement, by chunk (key as {@link RoadNet}'s chunk keys). Built once from its skeletons. */
	private TreeMap<Long, List<RoadNet.Col>> approachesOf(SettlementSite site, RoadNet.Settlement owner) {
		return approaches.get(site.id(), id -> {
			TreeMap<Long, List<RoadNet.Col>> out = new TreeMap<>();
			for (Approach a : approachRoads(site, owner)) {
				for (var en : a.byChunk().entrySet()) out.computeIfAbsent(en.getKey(), k -> new ArrayList<>()).addAll(en.getValue());
			}
			return out;
		});
	}

	/** One approach road as drawn: its port (null when none matches) and its columns by chunk. */
	record Approach(dev.fractalbuildings.core.settlement.Road road, dev.fractalbuildings.core.road.RoadPort port, TreeMap<Long, List<RoadNet.Col>> byChunk) {
		List<RoadNet.Col> columns() {
			List<RoadNet.Col> all = new ArrayList<>();
			for (List<RoadNet.Col> l : byChunk.values()) all.addAll(l);
			return all;
		}
	}

	/**
	 * Every approach of the settlement (exit to port, from its cell skeletons), each with its deck: the averaged exact surface at 8-block
	 * stations, the end at the port pinned to the road it joins (a graph route's deck, or the exact surface at a meeting point), so the
	 * two meet. Pure and shared by every chunk that draws it.
	 */
	List<Approach> approachRoads(SettlementSite site, RoadNet.Settlement owner) {
		List<Approach> out = new ArrayList<>();
		SkeletonSource src = source(site);
		List<dev.fractalbuildings.core.road.RoadPort> ports = roads.portsOf(owner);
		for (Cell cell : site.cells()) {
			for (dev.fractalbuildings.core.settlement.Road r : src.skeleton(cell).approaches()) {
				dev.fractalbuildings.core.settlement.Pt end = r.points().get(r.points().size() - 1);
				dev.fractalbuildings.core.road.RoadPort port = null;
				for (dev.fractalbuildings.core.road.RoadPort p : ports) if (port == null && Math.abs(p.x() - end.x()) <= 32 && Math.abs(p.z() - end.z()) <= 32) port = p;
				List<int[]> nodes = new ArrayList<>();
				for (dev.fractalbuildings.core.settlement.Pt p : r.points()) nodes.add(new int[] {p.x(), p.z()});
				// the skeleton ends on its coarse lattice; the road it joins starts exactly at the port (a meeting point is off the lattice)
				if (port != null && (port.x() != end.x() || port.z() != end.z())) nodes.add(new int[] {port.x(), port.z()});
				double[][] st = dev.fractalbuildings.core.road.Curve.stations(nodes);
				if (st.length < 2) continue;
				OptionalInt pin = OptionalInt.empty();
				if (port != null) pin = port.meeting() ? OptionalInt.of(probes.roadExact().surfaceY(port.x(), port.z())) : roads.endDeck(owner, port.other());
				int[] deck = dev.fractalbuildings.core.road.Profile.approachDeck(st, probes.roadExact(), pin);
				out.add(new Approach(r, port, RoadNet.raster(st, deck, new boolean[st.length], r.width())));
			}
		}
		return out;
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
		buildings.clear();
		grounds.clear();
		if (roads != null) roads.evictAll();
	}
}
