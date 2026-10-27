package dev.fractalbuildings.core.road;

import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.settlement.Tier;

/**
 * The one surface block of a road between settlements (DS-30, ROADMAP 7.10), decided once per route from its end tiers and the biome at
 * its lower-tier end, never per column: a road between two cities is cobbled deepslate ("asphalt"); every other road is a track whose
 * block (dirt path, gravel, mud bricks) is the {@code road} entry of the style of that biome. The bridge deck is the style's single
 * {@code deck} entry, except between two cities, where it is the road's own block (DS-44). Pure.
 */
public final class RoadMaterials {
	private RoadMaterials() {}

	/** What a route's columns are paved with. */
	public record Surface(String road, String deck) {}

	/**
	 * @param ta one end's tier and {@code biomeA} its biome id; {@code lo} is the lower of the two tiers, whose biome decides (a tie: the first end's)
	 */
	public static Surface of(Tier ta, String biomeA, Tier tb, String biomeB) {
		return of(ta, tb, ta.ordinal() <= tb.ordinal() ? biomeA : biomeB);
	}

	/**
	 * @param biome the biome at the lower-tier end ({@code minecraft:plains}); null reads as plain
	 */
	public static Surface of(Tier a, Tier b, String biome) {
		Tier lo = a.ordinal() <= b.ordinal() ? a : b;
		String id = biome == null ? null : dev.fractalbuildings.core.style.StyleField.styleOf(biome); // the biome vote of the style field: savanna and badlands take the desert set
		var style = StyleLibrary.get(id == null ? StyleLibrary.FALLBACK : id);
		String deck = first(style.palette().get(Role.DECK.json()), "minecraft:oak_planks");
		if (lo.atLeast(Tier.CITY)) {
			String road = first(StyleLibrary.get(StyleLibrary.FALLBACK).palette().get(Role.ROAD_MAIN.json()), "minecraft:cobbled_deepslate");
			return new Surface(road, road); // DS-44: a bridge between two cities carries the road's own surface
		}
		return new Surface(first(style.palette().get(Role.ROAD.json()), "minecraft:gravel"), deck);
	}

	private static String first(java.util.List<String> l, String dflt) { return l == null || l.isEmpty() ? dflt : l.get(0); }
}
