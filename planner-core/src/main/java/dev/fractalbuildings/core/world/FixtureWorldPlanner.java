package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.Briefs;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.DistrictPlan;
import dev.fractalbuildings.core.settlement.QuarterKind;
import dev.fractalbuildings.core.settlement.SampleSettlement;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.style.StyleField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A planner that serves a fixed handful of tiles from ready district plans, with no region field, no skeletons and no live terrain:
 * for mod tests that must not depend on a world's terrain (B1's {@code WorldPlanners.overrideForTests}, B5's gameplay tests). Buildings
 * and ground come from the same code as {@link WorldPlanner}'s ({@code DistrictScenes.generateLots} / {@code ground}), so a
 * piece here is what the real planner gives for the same plan. {@link #sample()} is the hand-laid town of {@link SampleSettlement}
 * (2 x 2 tiles at 0..272 x 0..288 on synthetic terrain: river, plains and desert); the tile numbers are the sample's own, not
 * {@link DistrictGrid}'s, and {@link #isStartChunk} answers for them. Not for production worlds.
 */
public final class FixtureWorldPlanner extends WorldPlanner {
	private final long seed;
	private final SettlementSite site;
	private final TerrainProbe exact;
	private final BiomeProbe biome;
	private final Map<Long, TilePlan> plans = new TreeMap<>();
	private final Map<Long, DistrictGrid.Tile> tiles = new TreeMap<>();
	private final DistrictGrid grid;

	/** @param plans filled district plans (briefs set), one per tile; their {@code district()} gives the tile numbers and rectangle */
	public FixtureWorldPlanner(long seed, SettlementSite site, List<DistrictPlan> plans, TerrainProbe exact, BiomeProbe biome) {
		this.seed = seed;
		this.site = site;
		this.exact = exact;
		this.biome = biome;
		this.grid = new DistrictGrid(seed);
		for (DistrictPlan p : plans) {
			var d = p.district();
			int cx0 = d.x() / 16, cx1 = (d.x() + d.w()) / 16, cz0 = d.z() / 16, cz1 = (d.z() + d.d()) / 16;
			DistrictGrid.Tile t = new DistrictGrid.Tile(d.ix(), d.iz(), d.x(), d.z(), d.x() + d.w(), d.z() + d.d(), cx0 + (cx1 - cx0) / 2, cz0 + (cz1 - cz0) / 2);
			TileInfo info = new TileInfo(d.ix(), d.iz(), site, t);
			this.tiles.put(key(d.ix(), d.iz()), t);
			this.plans.put(key(d.ix(), d.iz()), new TilePlan(info, p, List.of(), List.of(), true));
		}
	}

	/** The sample town ({@link SampleSettlement#SEED}): four tiles, 40 lots, a bridged main road, a lane and a market square. */
	public static FixtureWorldPlanner sample() {
		SampleSettlement.Sample s = SampleSettlement.sample();
		ProbeSpecs.Probes pr = ProbeSpecs.parse(SampleSettlement.SAMPLE_TERRAIN);
		StyleField styles = new StyleField(pr.biome(), SampleSettlement.SEED);
		List<DistrictPlan> plans = new ArrayList<>();
		for (DistrictPlan d : s.districts().values()) {
			QuarterKind q = s.settlement().quarters().get(d.district().quarter()).kind();
			plans.add(Briefs.fill(d, q, styles, pr.terrain()));
		}
		SettlementSite site = s.settlement().site();
		return new FixtureWorldPlanner(SampleSettlement.SEED, site, plans, pr.terrain(), pr.biome());
	}

	private static long key(int ix, int iz) { return ((long) ix << 32) ^ (iz & 0xffffffffL); }

	public long seed() { return seed; }

	@Override public DistrictGrid grid() { return grid; }

	@Override public DistrictGrid.Tile tileAt(int x, int z) {
		for (DistrictGrid.Tile t : tiles.values()) if (x >= t.minX() && x < t.maxX() && z >= t.minZ() && z < t.maxZ()) return t;
		return grid.tileAt(x, z);
	}

	@Override public boolean isStartChunk(int chunkX, int chunkZ) {
		for (DistrictGrid.Tile t : tiles.values()) if (t.originChunkX() == chunkX && t.originChunkZ() == chunkZ) return true;
		return false;
	}

	@Override public Optional<DistrictGrid.Tile> startTile(int chunkX, int chunkZ) {
		for (DistrictGrid.Tile t : tiles.values()) if (t.originChunkX() == chunkX && t.originChunkZ() == chunkZ) return Optional.of(t);
		return Optional.empty();
	}

	@Override public Optional<TileInfo> tile(int ix, int iz) {
		TilePlan p = plans.get(key(ix, iz));
		return p == null ? Optional.empty() : Optional.of(p.info());
	}

	@Override public TilePlan plan(int ix, int iz) {
		TilePlan p = plans.get(key(ix, iz));
		if (p == null) throw new IllegalArgumentException("no tile " + ix + "," + iz);
		return p;
	}

	@Override public List<BuildingPiece> buildings(int ix, int iz) {
		TilePlan p = plans.get(key(ix, iz));
		return p == null ? List.of() : Pieces.buildings(p, exact, biome);
	}

	@Override public List<GroundPiece> ground(int ix, int iz) {
		TilePlan p = plans.get(key(ix, iz));
		return p == null ? List.of() : Pieces.ground(p, exact);
	}

	@Override public void prefetch(int ix, int iz) {}

	@Override public void evictAll() {}

	@Override public WorldConfig config() { return WorldConfig.defaults(); }

	@Override public void warm(int blockX, int blockZ, int radius) {}

	@Override public boolean footprintNear(int x, int z, int margin) {
		double lim = (double) site.radius() + margin, ex = site.centre().x() - x, ez = site.centre().z() - z;
		return ex * ex + ez * ez <= lim * lim;
	}

	@Override public Optional<SettlementInfo> nearest(int x, int z, Tier min, int maxCells) {
		if (!site.tier().atLeast(min)) return Optional.empty();
		double d = StrictMath.hypot(site.centre().x() - x, site.centre().z() - z);
		return Optional.of(new SettlementInfo(site, d));
	}
}
