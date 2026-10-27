package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.raster.PieceRaster;
import dev.fractalbuildings.core.raster.StyleAt;
import dev.fractalbuildings.core.road.RoadMaterials;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** DS-30 / ROADMAP 7.10: one surface material per road, chosen by tier, road kind and biome, never per cell or per nearest building. */
class RoadMaterialTest {
	@Test
	void roleByTierAndKind() {
		for (Tier t : new Tier[] {Tier.HAMLET, Tier.VILLAGE})
			for (RoadKind k : new RoadKind[] {RoadKind.MAIN, RoadKind.STREET, RoadKind.LANE, RoadKind.PATH}) assertEquals(Role.ROAD, DistrictScenes.roadRole(t, k), t + " " + k);
		for (RoadKind k : new RoadKind[] {RoadKind.MAIN, RoadKind.STREET, RoadKind.LANE}) assertEquals(Role.ROAD_TOWN, DistrictScenes.roadRole(Tier.TOWN, k));
		assertEquals(Role.ROAD, DistrictScenes.roadRole(Tier.TOWN, RoadKind.PATH));
		for (Tier t : new Tier[] {Tier.CITY, Tier.METROPOLIS}) {
			assertEquals(Role.ROAD_MAIN, DistrictScenes.roadRole(t, RoadKind.ARTERIAL));
			assertEquals(Role.ROAD_MAIN, DistrictScenes.roadRole(t, RoadKind.MAIN));
			assertEquals(Role.ROAD_CITY, DistrictScenes.roadRole(t, RoadKind.STREET));
			assertEquals(Role.ROAD_CITY, DistrictScenes.roadRole(t, RoadKind.LANE));
		}
		for (Tier t : Tier.values()) assertEquals(Role.PAVING, DistrictScenes.roadRole(t, RoadKind.QUAY));
	}

	@Test
	void roadsBetweenSettlementsHaveOneBlockByEndTiersAndBiome() {
		assertEquals("minecraft:cobbled_deepslate", RoadMaterials.of(Tier.CITY, Tier.METROPOLIS, "minecraft:desert").road());
		assertEquals("minecraft:dirt_path", RoadMaterials.of(Tier.CITY, Tier.VILLAGE, "minecraft:plains").road(), "below city it is a track");
		assertEquals("minecraft:dirt_path", RoadMaterials.of(Tier.TOWN, Tier.VILLAGE, "minecraft:plains").road());
		assertEquals("minecraft:gravel", RoadMaterials.of(Tier.VILLAGE, Tier.VILLAGE, "minecraft:snowy_plains").road());
		assertEquals("minecraft:mud_bricks", RoadMaterials.of(Tier.HAMLET, Tier.TOWN, "minecraft:desert").road());
		assertEquals(RoadMaterials.of(Tier.VILLAGE, Tier.CITY, "minecraft:desert"), RoadMaterials.of(Tier.CITY, Tier.VILLAGE, "minecraft:desert"), "the order of the ends does not matter");
	}

	/** The biome that decides a route is the lower tier's, whichever end it is and whatever the other end's biome is (the ends differ here, unlike above). */
	@Test
	void theLowerTiersBiomeDecidesTheRoute() {
		for (boolean flip : new boolean[] {false, true}) {
			String desertRoad = flip ? RoadMaterials.of(Tier.TOWN, "minecraft:plains", Tier.VILLAGE, "minecraft:desert").road() : RoadMaterials.of(Tier.VILLAGE, "minecraft:desert", Tier.TOWN, "minecraft:plains").road();
			assertEquals("minecraft:mud_bricks", desertRoad, "village in the desert, town on the plains: the village's biome, flip " + flip);
			String plainsRoad = flip ? RoadMaterials.of(Tier.TOWN, "minecraft:desert", Tier.VILLAGE, "minecraft:plains").road() : RoadMaterials.of(Tier.VILLAGE, "minecraft:plains", Tier.TOWN, "minecraft:desert").road();
			assertEquals("minecraft:dirt_path", plainsRoad, "village on the plains, town in the desert: the village's biome, flip " + flip);
		}
		assertEquals("minecraft:gravel", RoadMaterials.of(Tier.HAMLET, "minecraft:snowy_plains", Tier.METROPOLIS, "minecraft:desert").road());
		assertEquals("minecraft:mud_bricks", RoadMaterials.of(Tier.HAMLET, "minecraft:savanna", Tier.CITY, "minecraft:plains").road(), "savanna takes the desert road set");
		assertEquals("minecraft:mud_bricks", RoadMaterials.of(Tier.VILLAGE, "minecraft:badlands", Tier.TOWN, "minecraft:plains").road());
	}

	/** DS-44: a bridge between two cities (and in a city district) carries the road's own block; below city it keeps the style's plank. */
	@Test
	void cityBridgeDecksCarryTheRoad() {
		var cc = RoadMaterials.of(Tier.CITY, "minecraft:taiga", Tier.METROPOLIS, "minecraft:desert");
		assertEquals(cc.road(), cc.deck());
		assertEquals("minecraft:spruce_planks", RoadMaterials.of(Tier.TOWN, "minecraft:taiga", Tier.CITY, "minecraft:plains").deck());
		for (var k : dev.fractalbuildings.core.terrain.GroundBox.Kind.values()) {
			boolean deckKind = k == dev.fractalbuildings.core.terrain.GroundBox.Kind.DECK;
			assertEquals(deckKind ? Role.ROAD_MAIN : k.role() == Role.ROAD ? Role.ROAD_MAIN : k.role(), DistrictScenes.boxRole(k, Role.ROAD_MAIN, true), "city " + k);
			assertEquals(k.role() == Role.ROAD ? Role.ROAD_MAIN : k.role(), DistrictScenes.boxRole(k, Role.ROAD_MAIN, false), "below city " + k);
		}
		assertEquals(Role.DECK, DistrictScenes.boxRole(dev.fractalbuildings.core.terrain.GroundBox.Kind.PIER, Role.ROAD_MAIN, true), "piers stay deck");
	}

	/** Every style that can vote for a tile's road style has a deck that is one plank species or the road's own block (DS-30); the styles with a stone deck are the tower styles, which do not vote. */
	@Test
	void everyVotingStyleHasAPlankOrRoadDeck() {
		for (String id : dev.fractalbuildings.core.gen.v1.style.StyleLibrary.ids()) {
			var pal = dev.fractalbuildings.core.gen.v1.style.StyleLibrary.get(id).palette();
			var deck = pal.get(Role.DECK.json());
			assertTrue(deck != null && deck.size() == 1, id + " deck " + deck);
			if (dev.fractalbuildings.core.raster.StyleAt.towerStyle(id)) continue;
			assertTrue(deck.get(0).endsWith("_planks") || deck.equals(pal.get(Role.ROAD.json())), id + " deck " + deck);
		}
		for (String id : new String[] {"tall", "tall-stone", "tall-brick", "tall-concrete", "tall-terracotta", "mass-timber", "mass-timber-oak", "keep", "keep-desert", "keep-jungle"})
			assertTrue(dev.fractalbuildings.core.raster.StyleAt.towerStyle(id), id);
		for (String id : new String[] {"plain", "plains", "medieval", "taiga", "snowy", "jungle", "desert"})
			assertTrue(!dev.fractalbuildings.core.raster.StyleAt.towerStyle(id), id);
	}

	/** DS-30 and DS-2: paving (plazas, quays) and every road entry is one block per style; desert town streets are smooth sandstone. */
	@Test
	void pavingAndRoadEntriesAreSingleBlocks() {
		for (String id : dev.fractalbuildings.core.gen.v1.style.StyleLibrary.ids()) {
			var pal = dev.fractalbuildings.core.gen.v1.style.StyleLibrary.get(id).palette();
			for (Role r : new Role[] {Role.PAVING, Role.ROAD, Role.ROAD_TOWN, Role.ROAD_MAIN, Role.ROAD_CITY}) {
				var l = pal.get(r.json());
				assertTrue(l == null || l.size() == 1, id + " " + r + " " + l);
			}
		}
		assertEquals(java.util.List.of("minecraft:smooth_sandstone"), dev.fractalbuildings.core.gen.v1.style.StyleLibrary.forBiome("minecraft:desert").palette().get(Role.ROAD_TOWN.json()));
		assertEquals(java.util.List.of("minecraft:smooth_sandstone"), dev.fractalbuildings.core.gen.v1.style.StyleLibrary.get(dev.fractalbuildings.core.style.StyleField.styleOf("minecraft:savanna")).palette().get(Role.ROAD_TOWN.json()));
	}

	/** Every ground cell of a road role in a built district is one block, per role and per scene, in every tier and for every sample district. */
	@Test
	void everyDistrictSurfaceIsOneBlockPerRole() {
		int roads = 0;
		for (Tier tier : new Tier[] {Tier.VILLAGE, Tier.TOWN, Tier.CITY}) {
			for (DistrictPlan d : DistrictScenesTest.SAMPLE.districts().values()) {
				var scene = DistrictScenes.build(DistrictScenesTest.filled(d), DistrictScenesTest.SAMPLE.settlement().landmarks(), DistrictScenesTest.PR.terrain(), DistrictScenesTest.PR.biome(), true, tier).scene();
				Box3 clip = RasterOwnershipTest.hull(scene);
				Map<Role, TreeSet<String>> ids = new EnumMap<>(Role.class);
				PieceRaster.ground(scene.ground(), scene.buildings(), StyleAt.nearest(scene.buildings(), "plain"), clip, (x, y, z, r, id, sh) -> {
					if (r.isCarriageway() || r == Role.PAVING) ids.computeIfAbsent(r, k -> new TreeSet<>()).add(id);
				});
				for (var e : ids.entrySet()) {
					assertEquals(1, e.getValue().size(), tier + " " + d.district().id() + " " + e.getKey() + " mixes " + e.getValue());
					if (e.getKey() != Role.DECK) roads++;
				}
				if (tier == Tier.CITY) assertTrue(!ids.containsKey(Role.ROAD_TOWN), "a city has no town streets");
				if (tier == Tier.VILLAGE) assertTrue(ids.keySet().stream().noneMatch(r -> r.isRoad() && r != Role.ROAD), "a village has tracks only: " + ids.keySet());
			}
		}
		assertTrue(roads > 10, "roads checked " + roads);
	}
}
