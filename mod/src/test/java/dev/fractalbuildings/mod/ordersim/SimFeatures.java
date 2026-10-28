package dev.fractalbuildings.mod.ordersim;

import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.LakeVeto;
import dev.fractalbuildings.mod.worldgen.RockVeto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.data.worldgen.features.OreFeatures;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * ORDERSIM S3: the vanilla features the sim runs (the real ones, from the world lookup), the calls that place them, and the lake veto in
 * front of every lake. A {@link Call} is a feature at a place with an rng seed, so it does the same thing in every order unless the world it
 * reads differs; that difference is vanilla's own order dependence, and what the checks look at.
 *
 * <p>Each feature's step is fixed here (it is the step of the vanilla biome JSON for that feature), not read from the jar's biome files.
 */
public final class SimFeatures {
	private SimFeatures() {}

	/** A feature call: where it is placed, at which step of its chunk's decoration. The chunk is the one containing (x, z). */
	public record Call(Decoration step, String key, int x, int y, int z, long rngSeed) {
		public int chunkX() { return Math.floorDiv(x, 16); }
		public int chunkZ() { return Math.floorDiv(z, 16); }
		public BlockPos origin() { return new BlockPos(x, y, z); }
		public boolean isRock() { return key.equals("forest_rock"); }
		public boolean isLake() { return key.startsWith("lake_"); }
		public String label() { return "feature:" + key + "@" + x + "," + y + "," + z; }
	}

	private static final Map<String, ResourceKey<Feature>> KEYS = new TreeMap<>();
	private static final Map<String, Decoration> STEPS = new TreeMap<>();

	private static void reg(String name, ResourceKey<Feature> key, Decoration step) { KEYS.put(name, key); STEPS.put(name, step); }

	static {
		reg("oak", TreeFeatures.OAK, Decoration.VEGETAL_DECORATION);
		reg("fancy_oak", TreeFeatures.FANCY_OAK, Decoration.VEGETAL_DECORATION);
		reg("dark_oak", TreeFeatures.DARK_OAK, Decoration.VEGETAL_DECORATION);
		reg("birch", TreeFeatures.BIRCH, Decoration.VEGETAL_DECORATION);
		reg("spruce", TreeFeatures.SPRUCE, Decoration.VEGETAL_DECORATION);
		reg("mega_spruce", TreeFeatures.MEGA_SPRUCE, Decoration.VEGETAL_DECORATION);
		reg("huge_red_mushroom", TreeFeatures.HUGE_RED_MUSHROOM, Decoration.VEGETAL_DECORATION);
		reg("forest_rock", MiscOverworldFeatures.FOREST_ROCK, Decoration.LOCAL_MODIFICATIONS);
		reg("disk_sand", MiscOverworldFeatures.DISK_SAND, Decoration.UNDERGROUND_ORES);
		reg("disk_gravel", MiscOverworldFeatures.DISK_GRAVEL, Decoration.UNDERGROUND_ORES);
		reg("ore_dirt", OreFeatures.ORE_DIRT, Decoration.UNDERGROUND_ORES);
		reg("lake_lava", MiscOverworldFeatures.LAKE_LAVA, Decoration.LAKES);
		reg("freeze_top_layer", MiscOverworldFeatures.FREEZE_TOP_LAYER, Decoration.TOP_LAYER_MODIFICATION);
	}

	/** The pool the hashed calls draw from (sorted by name, so the draw is stable). {@code freeze_top_layer} only with {@code snowy}. */
	public static List<String> pool(boolean snowy) { return pool(snowy, java.util.Set.of()); }

	/** As above without the {@code excluded} keys (SC1 and SC5 leave out {@code forest_rock} until ROCKVETO lands: a known real order dependence, pinned in {@code OrderSimRockTest}). */
	public static List<String> pool(boolean snowy, java.util.Set<String> excluded) {
		List<String> out = new ArrayList<>();
		for (String k : KEYS.keySet()) if ((snowy || !k.equals("freeze_top_layer")) && !excluded.contains(k)) out.add(k);
		return out;
	}

	public static Decoration stepOf(String key) {
		Decoration d = STEPS.get(key);
		if (d == null) throw new IllegalArgumentException("unknown sim feature " + key);
		return d;
	}

	/** {@code k} calls per chunk of the {@code w x h} window at (cx0, cz0), drawn from the pool by hashing (seed, chunk, i). y is the terrain floor. */
	public static List<Call> hashed(long seed, int cx0, int cz0, int w, int h, int k, TerrainProbe terrain, boolean snowy) {
		return hashed(seed, cx0, cz0, w, h, k, terrain, snowy, java.util.Set.of());
	}

	/** As above, drawing from the pool without {@code excluded}. */
	public static List<Call> hashed(long seed, int cx0, int cz0, int w, int h, int k, TerrainProbe terrain, boolean snowy, java.util.Set<String> excluded) {
		List<String> pool = pool(snowy, excluded);
		List<Call> out = new ArrayList<>();
		for (int cz = cz0; cz < cz0 + h; cz++) {
			for (int cx = cx0; cx < cx0 + w; cx++) {
				for (int i = 0; i < k; i++) {
					long hh = Hash64.hash(seed, "ordersim", cx, cz, i);
					String key = pool.get(Hash64.below(hh, pool.size()));
					int x = cx * 16 + Hash64.below(Hash64.hash(seed, "ordersim-x", cx, cz, i), 16), z = cz * 16 + Hash64.below(Hash64.hash(seed, "ordersim-z", cx, cz, i), 16);
					int y = terrain.floorY(x, z);
					out.add(new Call(stepOf(key), key, x, y, z, Hash64.hash(seed, "ordersim-rng", x, z, Hash64.of(key))));
				}
			}
		}
		return out;
	}

	/**
	 * A scripted call: the first rng seed in 0..63 with which the feature places on the bare terrain (the 3 x 3 chunks round the call, no
	 * pieces), or null when none does. Lets a scenario put a known feature at a known spot.
	 */
	public static Call scripted(String key, int x, int y, int z, long seed, TerrainProbe terrain, BiomeProbe biomes) {
		int cx = Math.floorDiv(x, 16), cz = Math.floorDiv(z, 16);
		SimWorld w = new SimWorld(seed, cx - 1, cz - 1, 3, 3, terrain, biomes);
		for (int s = 0; s < 64; s++) {
			Call c = new Call(stepOf(key), key, x, y, z, s);
			boolean placed = place(w, null, c, new Stats());
			if (placed) return c;
			if (!w.writes().isEmpty()) w = new SimWorld(seed, cx - 1, cz - 1, 3, 3, terrain, biomes);
		}
		return null;
	}

	/** What the calls of one run did, per feature key: {attempted, placed, vetoed}. */
	public static final class Stats {
		public final Map<String, int[]> byKey = new TreeMap<>();
		int[] of(String key) { return byKey.computeIfAbsent(key, k -> new int[3]); }
		public int attempted() { return byKey.values().stream().mapToInt(a -> a[0]).sum(); }
		public int placed() { return byKey.values().stream().mapToInt(a -> a[1]).sum(); }
		public int vetoed() { return byKey.values().stream().mapToInt(a -> a[2]).sum(); }
		@Override public String toString() {
			StringBuilder b = new StringBuilder();
			byKey.forEach((k, a) -> b.append(b.length() == 0 ? "" : " ").append(k).append(':').append(a[1]).append('/').append(a[0]).append(a[2] > 0 ? "(veto " + a[2] + ")" : ""));
			return b.toString();
		}
	}

	/**
	 * Places the call through a level centred on its chunk (reads and writes inside the 3 x 3 chunks, like the generation region). A lake first
	 * asks {@link LakeVeto#vetoes(WorldPlanner, BlockPos)} when there is a planner. Returns whether the feature placed.
	 */
	public static boolean place(SimWorld world, WorldPlanner planner, Call c, Stats stats) {
		int[] st = stats.of(c.key());
		st[0]++;
		if (c.isLake() && planner != null && LakeVeto.vetoes(planner, c.origin())) { st[2]++; return false; }
		if (c.isRock() && planner != null && RockVeto.vetoes(planner, c.origin())) { st[2]++; return false; }
		SimLevel level = new SimLevel(world, c.chunkX(), c.chunkZ(), c.label(), c.rngSeed());
		boolean placed = SimBoot.feature(KEYS.get(c.key())).place(level.proxy(), null, RandomSource.create(c.rngSeed()), c.origin());
		if (placed) st[1]++;
		return placed;
	}
}
