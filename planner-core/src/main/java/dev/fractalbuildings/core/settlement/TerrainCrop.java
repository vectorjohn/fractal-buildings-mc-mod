package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.probe.BiomeIds;
import java.util.List;

/**
 * The plan's coarse raster as a terrain-only {@code fb-region-dump/1} ({@code layers: []}), the background of the plan
 * view. Columns the skeleton never read (corners of a merged window) are height 0 in biome {@code the_void}.
 */
public final class TerrainCrop {
	private TerrainCrop() {}

	public static String toJson(long seed, CoarseRaster c) {
		List<String> palette = c.biomes().stream().map(BiomeIds::path).toList();
		int[] floor = c.floorY(), water = c.water(), biome = c.biome();
		StringBuilder sb = new StringBuilder(c.nx() * c.nz() * 12);
		sb.append("{\"format\":\"fb-region-dump/1\",\"seed\":").append(seed).append(",\"mc\":\"skeleton\",");
		sb.append("\"grid\":{\"x0\":").append(c.x0()).append(",\"z0\":").append(c.z0()).append(",\"step\":").append(c.step())
				.append(",\"nx\":").append(c.nx()).append(",\"nz\":").append(c.nz()).append("},\"palette\":[");
		for (int i = 0; i < palette.size(); i++) sb.append(i > 0 ? "," : "").append('"').append(palette.get(i)).append('"');
		sb.append("],\"biome\":");
		ints(sb, biome, null);
		sb.append(",\"surface\":");
		ints(sb, floor, water);
		sb.append(",\"water\":");
		ints(sb, water, null);
		sb.append(",\"layers\":[]}\n");
		return sb.toString();
	}

	private static void ints(StringBuilder sb, int[] a, int[] add) {
		sb.append('[');
		for (int i = 0; i < a.length; i++) sb.append(i > 0 ? "," : "").append(a[i] + (add == null ? 0 : add[i]));
		sb.append(']');
	}
}
