package dev.fractalbuildings.core.region;

import dev.fractalbuildings.core.rand.Hash64;

/** A synthetic world for region tests: hashed biome patches of 640 blocks, oceans with depth, a few peaks. */
final class RegionTestWorld implements RegionProbe {
	private static final String[] BIOMES = {
		"minecraft:plains", "minecraft:plains", "minecraft:forest", "minecraft:desert", "minecraft:taiga", "minecraft:savanna",
		"minecraft:swamp", "minecraft:ocean", "minecraft:deep_ocean", "minecraft:river", "minecraft:jagged_peaks", "minecraft:badlands",
		"minecraft:snowy_plains", "minecraft:jungle"};
	final long seed;

	RegionTestWorld(long seed) { this.seed = seed; }

	@Override public String biome(int x, int z) {
		long h = Hash64.hash(seed, "test/biome", Math.floorDiv(x, 640), Math.floorDiv(z, 640));
		return BIOMES[Hash64.below(h, BIOMES.length)];
	}

	@Override public int floorY(int x, int z) {
		String b = biome(x, z);
		int jitter = Hash64.below(Hash64.hash(seed, "test/h", Math.floorDiv(x, 64), Math.floorDiv(z, 64)), 6);
		if (BiomeClass.isPeak(b)) return 150 + jitter;
		return (BiomeClass.isWater(b) ? 40 : 64) + jitter;
	}

	@Override public int waterDepth(int x, int z) {
		return BiomeClass.isWater(biome(x, z)) ? 30 : 0;
	}
}
