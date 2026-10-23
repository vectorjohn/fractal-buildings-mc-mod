package dev.fractalbuildings.mod.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/**
 * Full block to its stair and slab variant ({@code oak_planks} to {@code oak_stairs}/{@code oak_slab}). Rule-based
 * (vanilla naming) with the registry as the judge: a candidate counts only if the block exists. Empty when the
 * material has no such variant (terracotta, mushroom stem, logs); callers then keep the full block.
 */
public final class ShapeTable {
	private ShapeTable() {
	}

	public static Optional<String> stair(String blockId) { return variant(blockId, "_stairs"); }

	public static Optional<String> slab(String blockId) { return variant(blockId, "_slab"); }

	private static Optional<String> variant(String blockId, String suffix) {
		for (String c : candidates(blockId, suffix)) {
			Identifier id = Identifier.tryParse(c);
			if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) return Optional.of(c);
		}
		return Optional.empty();
	}

	static List<String> candidates(String blockId, String suffix) {
		String ns = "minecraft:", name = blockId;
		int colon = blockId.indexOf(':');
		if (colon >= 0) { ns = blockId.substring(0, colon + 1); name = blockId.substring(colon + 1); }
		List<String> out = new ArrayList<>();
		if (name.endsWith("_planks")) out.add(ns + name.substring(0, name.length() - "_planks".length()) + suffix);
		if (name.equals("bricks")) out.add(ns + "brick" + suffix);
		if (name.endsWith("_bricks")) out.add(ns + name.substring(0, name.length() - 1) + suffix); // bricks -> brick
		if (name.endsWith("_block")) out.add(ns + name.substring(0, name.length() - "_block".length()) + suffix);
		out.add(ns + name + suffix);
		// no own stairs in vanilla: the nearest same-looking material
		if (name.equals("cut_sandstone") || name.equals("chiseled_sandstone")) out.add(ns + "sandstone" + suffix);
		return out;
	}

	/** The block behind an id, or empty. */
	static Optional<Block> block(String id) {
		Identifier i = Identifier.tryParse(id);
		return i == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(i);
	}
}
