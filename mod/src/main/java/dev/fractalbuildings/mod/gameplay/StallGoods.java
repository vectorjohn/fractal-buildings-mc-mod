package dev.fractalbuildings.mod.gameplay;

import java.util.List;
import java.util.Set;

/**
 * The wares on a market hall's stalls (the planner's {@code goods} hooks, 7.17 S4 round 2; interior designer {@code market-stalls-interior}):
 * one block per hook, from a list per style, all standing on their own (no crops, no entities) and none a villager's job block, so a hall
 * never claims a trade its stall does not sell. Pure ids, so the lists are testable without Minecraft.
 */
final class StallGoods {
	private StallGoods() {}

	/** Job blocks a stall never holds (the interior designer's list, plus the ones that would draw a villager to a market hall's floor). */
	static final Set<String> NEVER = Set.of("lectern", "brewing_stand", "cartography_table", "blast_furnace", "smoker", "grindstone",
			"smithing_table", "stonecutter", "fletching_table", "barrel", "composter", "loom", "cauldron");

	private static final List<String> COMMON = List.of("hay_block", "pumpkin", "melon");

	/** Block ids (without namespace) for a style's stalls. */
	static List<String> of(String style) {
		return switch (style) {
			case "medieval" -> List.of("hay_block", "pumpkin", "melon", "anvil", "iron_bars");
			case "plains" -> List.of("hay_block", "pumpkin", "melon", "cake");
			case "taiga" -> List.of("dried_kelp_block", "brown_mushroom_block", "red_mushroom_block", "hay_block");
			case "snowy" -> List.of("packed_ice", "white_candle", "hay_block", "pumpkin");
			case "desert" -> List.of("decorated_pot", "orange_terracotta", "yellow_terracotta", "melon", "hay_block");
			case "jungle" -> List.of("melon", "bamboo_block", "stripped_bamboo_block", "hay_block");
			case "plain" -> List.of("hay_block", "pumpkin", "bookshelf", "chiseled_bookshelf");
			default -> COMMON;
		};
	}

	/** The k-th pick (k from a hash) for a style. */
	static String pick(String style, long hash) {
		List<String> l = of(style);
		return l.get((int) Math.floorMod(hash, (long) l.size()));
	}
}
