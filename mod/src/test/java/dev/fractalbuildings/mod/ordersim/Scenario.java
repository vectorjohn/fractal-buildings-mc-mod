package dev.fractalbuildings.mod.ordersim;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.GroundPiece;
import dev.fractalbuildings.core.world.PieceRef;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.block.BlockMapper;
import dev.fractalbuildings.mod.worldgen.DistrictStructure;
import dev.fractalbuildings.mod.worldgen.GroundStructure;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import dev.fractalbuildings.mod.worldgen.TilePiece;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

/**
 * ORDERSIM S3: what one sim scenario is: a planned settlement, an inner window of n x n chunks whose claimed cells are compared, the feature
 * calls, and (built once, order independent) the pieces each chunk places and the reference raster.
 *
 * <p>Windows: the <em>inner</em> window {@code [cx, cx + n)}; the <em>decorated</em> window one chunk wider all round (every chunk that can
 * write an inner cell, since a chunk writes within one chunk of itself); the <em>sim</em> window one more (what those chunks can read).
 */
public final class Scenario {
	public final String id;
	public final WorldPlanner planner;
	public final long seed;
	public final TerrainProbe terrain;
	public final BiomeProbe biome;
	public final int cx, cz, n;
	public final List<SimFeatures.Call> calls;
	/** Scripted features that cannot touch each other: C4 (cross-order equality of claimed cells) is strict. */
	public final boolean spaced;
	/** Test-only extra work in a chunk's top-layer step (after its pieces, before the road step), e.g. a planted order dependence. */
	public interface Extra { void run(SimWorld world, int cx, int cz); }

	public final List<Extra> extras;
	private Plan plan;

	public Scenario(String id, WorldPlanner planner, long seed, TerrainProbe terrain, BiomeProbe biome, int cx, int cz, int n, List<SimFeatures.Call> calls, boolean spaced) {
		this(id, planner, seed, terrain, biome, cx, cz, n, calls, spaced, List.of());
	}

	public Scenario(String id, WorldPlanner planner, long seed, TerrainProbe terrain, BiomeProbe biome, int cx, int cz, int n, List<SimFeatures.Call> calls, boolean spaced, List<Extra> extras) {
		this.extras = List.copyOf(extras);
		this.id = id; this.planner = planner; this.seed = seed; this.terrain = terrain; this.biome = biome;
		this.cx = cx; this.cz = cz; this.n = n; this.calls = List.copyOf(calls); this.spaced = spaced;
	}

	/** The same scenario with extra work added (the plan is built again). */
	public Scenario withExtras(String newId, boolean newSpaced, Extra... more) {
		List<Extra> l = new ArrayList<>(extras);
		l.addAll(List.of(more));
		return new Scenario(newId, planner, seed, terrain, biome, cx, cz, n, calls, newSpaced, l);
	}

	/** A scenario on a gate site: hashed calls (k per decorated chunk) plus the scripted ones. */
	public static Scenario city(String id, SimSites site, WorldPlanner planner, int cx, int cz, int n, int hashedK, boolean snowy, List<SimFeatures.Call> scripted, boolean spaced) {
		return city(id, site, planner, cx, cz, n, hashedK, snowy, scripted, spaced, java.util.Set.of());
	}

	/** As above, the hashed pool without the {@code excluded} feature keys. */
	public static Scenario city(String id, SimSites site, WorldPlanner planner, int cx, int cz, int n, int hashedK, boolean snowy, List<SimFeatures.Call> scripted, boolean spaced, java.util.Set<String> excluded) {
		List<SimFeatures.Call> calls = new ArrayList<>(scripted);
		if (hashedK > 0) calls.addAll(SimFeatures.hashed(site.seed(), cx - 1, cz - 1, n + 2, n + 2, hashedK, site.probes().exact(), snowy, excluded));
		return new Scenario(id, planner, site.seed(), site.probes().exact(), site.probes().biome(), cx, cz, n, calls, spaced);
	}

	public int decX0() { return cx - 1; }
	public int decZ0() { return cz - 1; }
	public int decN() { return n + 2; }
	public int simX0() { return cx - 2; }
	public int simZ0() { return cz - 2; }
	public int simN() { return n + 4; }

	public boolean inInner(int x, int z) { return x >= cx * 16 && x < (cx + n) * 16 && z >= cz * 16 && z < (cz + n) * 16; }

	/** Built on first use and shared by every order (a pure function of the planner). */
	public synchronized Plan plan() {
		if (plan == null) plan = new Plan(this);
		return plan;
	}

	/** The order independent part: pieces per chunk, calls per chunk, and the reference raster of every piece over the sim window. */
	public static final class Plan {
		public final Map<Long, List<TilePiece>> pieces = new HashMap<>();
		public final Map<Long, List<SimFeatures.Call>> calls = new HashMap<>();
		/** Reference state id of every cell the pieces claim in the sim window (air included), key {@link PieceLedger#pack}. */
		public final Long2IntOpenHashMap ref = new Long2IntOpenHashMap();
		/** Cells whose role is a street, plaza, quay or deck surface. */
		public final LongOpenHashSet refRoad = new LongOpenHashSet();
		/** The reference keys inside the inner window, sorted. */
		public final long[] innerKeys;
		public final int pieceCount;
		public final long ms;

		Plan(Scenario s) {
			long t0 = System.nanoTime();
			WorldPlanner planner = s.planner;
			DistrictGrid g = planner.grid();
			int x0 = s.simX0() * 16, z0 = s.simZ0() * 16, x1 = x0 + s.simN() * 16 - 1, z1 = z0 + s.simN() * 16 - 1;
			DistrictGrid.TileId ta = g.tileOf(x0, z0), tb = g.tileOf(x1, z1);
			Box3 clip = new Box3(x0, SimWorld.MIN_Y, z0, s.simN() * 16, SimWorld.HEIGHT, s.simN() * 16);
			int count = 0;
			for (int iz = ta.iz(); iz <= tb.iz(); iz++) {
				for (int ix = ta.ix(); ix <= tb.ix(); ix++) {
					var info = planner.tile(ix, iz);
					if (info.isEmpty()) continue;
					DistrictGrid.Tile tile = info.get().tile();
					List<TilePiece> ps = new ArrayList<>(DistrictStructure.buildingPieces(planner, tile));
					ps.addAll(GroundStructure.groundPieces(planner, tile));
					for (TilePiece p : ps) {
						count++;
						var bb = p.getBoundingBox();
						for (int dz = s.decZ0(); dz < s.decZ0() + s.decN(); dz++) {
							for (int dx = s.decX0(); dx < s.decX0() + s.decN(); dx++) {
								if (bb.intersects(dx * 16, dz * 16, dx * 16 + 15, dz * 16 + 15)) pieces.computeIfAbsent(ChunkPos.pack(dx, dz), k -> new ArrayList<>()).add(p);
							}
						}
					}
					for (BuildingPiece b : planner.buildings(ix, iz)) rasterRef(planner, b.ref(), clip);
					for (GroundPiece gp : planner.ground(ix, iz)) rasterRef(planner, gp.ref(), clip);
				}
			}
			pieceCount = count;
			for (SimFeatures.Call c : s.calls) calls.computeIfAbsent(ChunkPos.pack(c.chunkX(), c.chunkZ()), k -> new ArrayList<>()).add(c);
			for (List<SimFeatures.Call> l : calls.values()) l.sort(java.util.Comparator.comparingInt(c -> c.step().ordinal())); // stable: ties keep the scenario's order
			long[] inner = new long[ref.size()];
			int ni = 0;
			for (long k : ref.keySet()) if (s.inInner(PieceLedger.x(k), PieceLedger.z(k))) inner[ni++] = k;
			innerKeys = Arrays.copyOf(inner, ni);
			Arrays.sort(innerKeys);
			ms = (System.nanoTime() - t0) / 1_000_000;
		}

		private void rasterRef(WorldPlanner planner, PieceRef r, Box3 clip) {
			planner.raster(r, clip, (x, y, z, role, id, shape) -> {
				long k = PieceLedger.pack(x, y, z);
				ref.put(k, Block.BLOCK_STATE_REGISTRY.getId(BlockMapper.state(role, id, shape)));
				if (role.isRoad() || role == Role.PAVING || role == Role.DECK) refRoad.add(k);
			});
		}
	}
}
