package dev.fractalbuildings.mod.block;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.raster.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockMapperTest {
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	/** Every (style, role-key, id) in every palette. */
	static List<String[]> entries() {
		List<String[]> out = new ArrayList<>();
		for (String sid : StyleLibrary.ids())
			for (Map.Entry<String, List<String>> e : StyleLibrary.get(sid).palette().entrySet())
				for (String id : e.getValue()) out.add(new String[] {sid, e.getKey(), id});
		return out;
	}

	static String prop(BlockState s, String name) {
		for (var p : s.getProperties()) if (p.getName().equals(name)) return s.getValue(p).toString();
		return null;
	}

	@Test
	void everyPaletteIdIsARealNonAirBlock() {
		List<String> bad = new ArrayList<>();
		for (String[] e : entries()) {
			var b = ShapeTable.block(e[2]);
			if (b.isEmpty() || b.get() == Blocks.AIR) bad.add(e[0] + "." + e[1] + ": " + e[2]);
		}
		assertTrue(bad.isEmpty(), "unresolvable palette ids: " + bad);
		assertTrue(entries().size() > 100);
	}

	@Test
	void districtRolesArePresentInEveryStyle() {
		for (String sid : StyleLibrary.ids())
			for (String role : new String[] {"road", "paving", "ground", "deck"})
				assertFalse(StyleLibrary.get(sid).palette().getOrDefault(role, List.of()).isEmpty(), sid + " lacks " + role);
	}

	@Test
	void everyStyleRoleShapeResolvesToAValidState() {
		int n = 0;
		for (String[] e : entries()) {
			String id = e[2];
			Role role = roleOf(e[1]);
			List<Shape> shapes = new ArrayList<>(List.of(Shape.NONE));
			if (role == Role.DOOR) for (Facing f : Facing.values()) for (Shape.Half h : new Shape.Half[] {Shape.Half.LOWER, Shape.Half.UPPER}) for (Shape.Hinge hg : Shape.Hinge.values()) shapes.add(Shape.door(f, h, hg));
			if (role == Role.WINDOW || role == Role.FENCE || role == Role.RAIL) for (int c = 0; c < 16; c++) shapes.add(Shape.connected(c));
			if (role == Role.BEAM || role == Role.TRIM) for (Shape.Axis3 a : Shape.Axis3.values()) shapes.add(Shape.log(a));
			if (role == Role.STAIR || role == Role.ROOF) for (Facing f : Facing.values()) for (Shape.Half h : new Shape.Half[] {Shape.Half.BOTTOM, Shape.Half.TOP}) shapes.add(new Shape(f, h, null, null, 0));
			for (Shape sh : shapes) {
				BlockState s = BlockMapper.state(role, id, sh);
				assertNotNull(s);
				assertFalse(s.isAir(), e[0] + "." + e[1] + " " + id + " " + sh);
				n++;
			}
		}
		assertTrue(n > 500, "ran " + n);
	}

	static Role roleOf(String key) {
		for (Role r : Role.values()) if (r.json().equals(key)) return r;
		return null; // "rail" and other non-Role palette keys
	}

	@Test
	void stairAndRoofIdsHaveStairAndSlabVariants() {
		TreeSet<String> missing = new TreeSet<>();
		for (String[] e : entries()) {
			if (!e[1].equals("stair") && !e[1].equals("roof")) continue;
			if (ShapeTable.stair(e[2]).isEmpty()) missing.add("stair:" + e[2]);
			if (ShapeTable.slab(e[2]).isEmpty()) missing.add("slab:" + e[2]);
		}
		// Vanilla has no stairs or slabs of these: they stay full blocks (a new entry here needs a decision).
		var full = new TreeSet<String>();
		for (String b : new String[] {"brown_terracotta", "moss_block", "red_terracotta", "snow_block", "terracotta"}) { full.add("stair:minecraft:" + b); full.add("slab:minecraft:" + b); }
		assertEquals(full, missing);
	}

	@Test
	void stairsFaceAndHalf() {
		for (Facing f : Facing.values()) for (Shape.Half hh : new Shape.Half[] {Shape.Half.BOTTOM, Shape.Half.TOP}) {
			String h = hh.name().toLowerCase();
			BlockState s = BlockMapper.state(Role.STAIR, "minecraft:oak_planks", new Shape(f, hh, null, null, 0));
			assertSame(Blocks.OAK_STAIRS, s.getBlock());
			assertEquals(f.json(), prop(s, "facing"));
			assertEquals(h, prop(s, "half"));
		}
		// no higher neighbour: stays a full block
		assertSame(Blocks.OAK_PLANKS, BlockMapper.state(Role.STAIR, "minecraft:oak_planks", Shape.NONE).getBlock());
		assertSame(Blocks.STONE_BRICK_STAIRS, BlockMapper.state(Role.STAIR, "minecraft:stone_bricks", Shape.stair(Facing.EAST)).getBlock());
		assertSame(Blocks.BRICK_STAIRS, BlockMapper.state(Role.STAIR, "minecraft:bricks", Shape.stair(Facing.EAST)).getBlock());
	}

	@Test
	void doorsFaceHalfHinge() {
		for (Facing f : Facing.values()) for (Shape.Half hh : new Shape.Half[] {Shape.Half.LOWER, Shape.Half.UPPER}) for (Shape.Hinge hgg : Shape.Hinge.values()) {
			String h = hh.name().toLowerCase(), hg = hgg.name().toLowerCase();
			BlockState s = BlockMapper.state(Role.DOOR, "minecraft:spruce_door", Shape.door(f, hh, hgg));
			assertSame(Blocks.SPRUCE_DOOR, s.getBlock());
			assertEquals(f.json(), prop(s, "facing"));
			assertEquals(h, prop(s, "half"));
			assertEquals(hg, prop(s, "hinge"));
			assertEquals("false", prop(s, "open"));
		}
	}

	@Test
	void panesFencesWallsConnect() {
		int mask = Shape.N | Shape.W;
		for (String id : new String[] {"minecraft:glass_pane", "minecraft:oak_fence"}) {
			BlockState s = BlockMapper.state(id, Shape.connected(mask));
			assertEquals("true", prop(s, "north"));
			assertEquals("false", prop(s, "east"));
			assertEquals("false", prop(s, "south"));
			assertEquals("true", prop(s, "west"));
		}
		BlockState w = BlockMapper.state("minecraft:sandstone_wall", Shape.connected(Shape.E | Shape.W));
		assertEquals("low", prop(w, "east"));
		assertEquals("none", prop(w, "north"));
		assertEquals("false", prop(w, "up"));
		assertEquals("true", prop(BlockMapper.state("minecraft:sandstone_wall", Shape.connected(Shape.N)), "up"));
	}

	@Test
	void logsTakeAxisAndSlabsTakeType() {
		for (Shape.Axis3 ax : Shape.Axis3.values())
			assertEquals(ax.name().toLowerCase(), prop(BlockMapper.state("minecraft:dark_oak_log", Shape.log(ax)), "axis"));
		assertEquals("top", prop(BlockMapper.state("minecraft:oak_slab", new Shape(null, Shape.Half.TOP, null, null, 0)), "type"));
	}

	/**
	 * Pins the door convention: A3's door facing is the walking direction (Minecraft's placement facing). Vanilla puts a
	 * closed door's panel on the edge nearest the placer, i.e. opposite the facing: facing NORTH is the south edge.
	 */
	@Test
	void doorFacingIsVanillaPlacementFacing() {
		var south = BlockMapper.state(Role.DOOR, "minecraft:oak_door", Shape.door(Facing.NORTH, Shape.Half.LOWER, Shape.Hinge.LEFT));
		var box = south.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).bounds();
		assertEquals(1.0, box.maxZ, 1e-9);
		assertTrue(box.minZ > 0.75, "panel hugs the south edge, got minZ " + box.minZ);
		var west = BlockMapper.state(Role.DOOR, "minecraft:oak_door", Shape.door(Facing.EAST, Shape.Half.LOWER, Shape.Hinge.LEFT));
		assertEquals(0.0, west.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO).bounds().minX, 1e-9);
	}

	@Test
	void railsConnectThroughTheMask() {
		for (String sid : StyleLibrary.ids()) {
			String id = StyleLibrary.get(sid).palette().get("rail").get(0);
			BlockState s = BlockMapper.state(Role.RAIL, id, Shape.connected(Shape.E | Shape.S));
			assertNotEquals("false", prop(s, "east"), sid);
			assertNotEquals("false", prop(s, "south"), sid);
			assertTrue(prop(s, "north").equals("false") || prop(s, "north").equals("none"), sid);
			assertTrue(prop(s, "west").equals("false") || prop(s, "west").equals("none"), sid);
		}
	}

	@Test
	void unknownIdBecomesStoneNotACrash() {
		assertSame(Blocks.STONE, BlockMapper.state("minecraft:no_such_block", Shape.NONE).getBlock());
		assertSame(Blocks.STONE, BlockMapper.state("not an id!", Shape.NONE).getBlock());
	}
}
