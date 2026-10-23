package dev.fractalbuildings.mod.compat;

import java.util.List;

/**
 * Which vanilla biome a non-vanilla biome stands in for (plan B7). The core only knows vanilla biome paths ({@code region/biome-classes.json});
 * biomes from Terralith, TerraBlender regions or any other mod are mapped by the convention tags the mod authors already set, so the
 * city likelihood and the style lists work unchanged. No Minecraft types here: the live lookup is {@link BiomeAliases}.
 */
public final class BiomeAliasRules {
	private BiomeAliasRules() {}

	/** Answers "does this biome carry the tag?" for a tag id such as {@code c:is_desert} or {@code fractalbuildings:no_settlements}. */
	@FunctionalInterface
	public interface Tags { boolean has(String tag); }

	public static final String NO_SETTLEMENTS = "fractalbuildings:no_settlements";
	/** A biome of the core's multiplier-0 class (water): nothing is built there. */
	public static final String BANNED_STAND_IN = "minecraft:ocean";
	public static final String FALLBACK = "minecraft:plains";

	/** Convention tag to stand-in, first match wins (water and mountains before climate, so a frozen river is a river). */
	static final List<String[]> CONVENTION = List.of(
		new String[] {"c:is_ocean", "minecraft:ocean"},
		new String[] {"c:is_river", "minecraft:river"},
		new String[] {"c:is_mountain", "minecraft:windswept_hills"},
		new String[] {"c:is_desert", "minecraft:desert"},
		new String[] {"c:is_jungle", "minecraft:jungle"},
		new String[] {"c:is_savanna", "minecraft:savanna"},
		new String[] {"c:is_badlands", "minecraft:badlands"},
		new String[] {"c:is_snowy", "minecraft:snowy_plains"},
		new String[] {"c:is_taiga", "minecraft:taiga"});

	/**
	 * The biome id the core should see. A biome in {@code #fractalbuildings:no_settlements} is always banned (any namespace); another
	 * {@code minecraft:} biome is itself; a foreign biome maps by convention tag, else to plains.
	 */
	public static String alias(String id, Tags tags) {
		if (tags.has(NO_SETTLEMENTS)) return BANNED_STAND_IN;
		if (id.startsWith("minecraft:")) return id;
		for (String[] c : CONVENTION) if (tags.has(c[0])) return c[1];
		return FALLBACK;
	}
}
