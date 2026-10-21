package dev.fractalbuildings.core.probe;

/**
 * Rules over namespaced biome ids ({@code "minecraft:plains"}). Water and peak rules as spike S4's
 * {@code BiomeClass} (docs/research/spikes/s4.md); the region field and the skeleton share them.
 */
public final class BiomeIds {
	private BiomeIds() {}

	/** The id without its namespace: {@code "minecraft:river"} gives {@code "river"}. */
	public static String path(String id) {
		int c = id.indexOf(':');
		return c < 0 ? id : id.substring(c + 1);
	}

	public static boolean isWater(String id) {
		String b = path(id);
		return b.endsWith("ocean") || b.endsWith("river") || b.equals("mushroom_fields");
	}

	public static boolean isPeak(String id) {
		String b = path(id);
		return b.equals("jagged_peaks") || b.equals("frozen_peaks") || b.equals("stony_peaks");
	}
}
