package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.cache.BoundedCache;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.raster.CellSink;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.region.FieldRegionView;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.region.RegionPlanner;
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
	public record Caches(int tiles, int sources, int skeletons, int region, int plans, int buildings, int ground) {
		public Caches {
			if (tiles < 1 || sources < 1 || skeletons < 1 || region < 1 || plans < 1 || buildings < 1 || ground < 1) throw new IllegalArgumentException("capacity < 1");
		}

		public static final Caches DEFAULT = new Caches(4096, 64, 64, FieldRegionView.DEFAULT_CACHE, 64, 32, 32);

		/** The same capacity everywhere. */
		public static Caches all(int n) { return new Caches(n, n, n, n, n, n, n); }
	}

	private static final int LATTICE = 32; // spacing of the columns that vote for the owner of a tile whose centre cell has no settlement

	private final long seed;
	private final WorldConfig config;
	private final WorldProbes probes;
	private final Executor workers;
	private final DistrictGrid grid;
	private final RegionField field;
	private final FieldRegionView fieldView;
	private final RegionView view;
	private final StyleField styles;
	private final Caches caps;
	private final BoundedCache<String, SkeletonSource> sources;
	private final BoundedCache<Long, Optional<TileInfo>> tiles;
	private final FlightCache<Long, TilePlan> plans;
	private final FlightCache<Long, List<BuildingPiece>> buildings;
	private final FlightCache<Long, List<GroundPiece>> grounds;

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
		this.view = fixed != null ? new FixedSiteView(fixed, field) : new FieldSiteView(fieldView, field);
		this.styles = new StyleField(probes.biome(), seed);
		this.sources = new BoundedCache<>(caps.sources());
		this.tiles = new BoundedCache<>(caps.tiles());
		this.plans = new FlightCache<>(caps.plans());
		this.buildings = new FlightCache<>(caps.buildings());
		this.grounds = new FlightCache<>(caps.ground());
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
	private Optional<SettlementSite> siteOf(Cell c) {
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
			lots.add(new LotBrief(l.id(), l.x(), l.z(), l.w(), l.d(), l.front(), l.type(), StyleLibrary.FALLBACK, l.minStoreys(), l.maxStoreys(), l.streets(), l.airRights(), l.frontY(), l.density(), l.status(), l.walk()));
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
	 * Asks the workers to compute the buildings and ground of this tile and the plans of its eight neighbours. Returns at once and never
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
							if (self) { buildings(x, z); ground(x, z); }
							else plan(x, z);
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
	 * Resolves the region cells within {@code radius} blocks of the point and builds the road network of every settlement found there,
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
		for (SettlementSite site : found.values()) source(site).network();
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

	/** Drops every memo (memory pressure, tests). Answers do not change; the next questions recompute. */
	public void evictAll() {
		sources.clear();
		tiles.clear();
		plans.clear();
		buildings.clear();
		grounds.clear();
	}
}
