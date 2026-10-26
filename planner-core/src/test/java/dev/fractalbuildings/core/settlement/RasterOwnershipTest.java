package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.raster.PieceRaster;
import dev.fractalbuildings.core.raster.Shape;
import dev.fractalbuildings.core.raster.StyleAt;
import dev.fractalbuildings.core.style.StyleField;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Cell ownership and shapes over real gate-city districts: every cell has exactly one owner, shapes face the right way. */
class RasterOwnershipTest {
	record Cell(Role role, String id, Shape shape, String owner) {}

	static long key(int x, int y, int z) { return ((long) (x + 100000) << 40) | ((long) (y + 1000) << 24) | (z + 100000); }

	/** Cells of every piece of a scene under one clip, each tagged with its piece; fails when a cell is emitted by two pieces. */
	static Map<Long, Cell> rasterScene(SceneDoc scene, Box3 clip, int[] stats) {
		Map<Long, Cell> m = new HashMap<>();
		List<PlacedBuilding> bs = scene.buildings();
		for (int i = 0; i < bs.size(); i++) {
			String owner = "b" + i;
			PieceRaster.building(bs, i, clip, (x, y, z, r, id, sh) -> {
				Cell old = m.put(key(x, y, z), new Cell(r, id, sh, owner));
				assertNull(old, "cell " + x + "," + y + "," + z + " owned by " + owner + " and " + (old == null ? "" : old.owner()));
			});
		}
		PieceRaster.ground(scene.ground(), bs, StyleAt.nearest(bs, "plain"), clip, (x, y, z, r, id, sh) -> {
			Cell old = m.put(key(x, y, z), new Cell(r, id, sh, "ground"));
			assertNull(old, "cell " + x + "," + y + "," + z + " owned by ground and " + (old == null ? "" : old.owner()));
		});
		return m;
	}

	static Box3 hull(SceneDoc s) {
		int x0 = Integer.MAX_VALUE, y0 = x0, z0 = x0, x1 = Integer.MIN_VALUE, y1 = x1, z1 = x1;
		List<Box3> all = new ArrayList<>();
		for (Box b : s.ground()) all.add(b.box());
		for (PlacedBuilding b : s.buildings()) {
			var p = b.placement();
			for (Box bx : b.shell().boxes()) all.add(p.toWorld(bx.box()));
			for (Box bx : b.siteBoxes()) all.add(p.toWorld(bx.box()));
		}
		for (Box3 b : all) {
			x0 = Math.min(x0, b.x()); y0 = Math.min(y0, b.y()); z0 = Math.min(z0, b.z());
			x1 = Math.max(x1, b.maxX()); y1 = Math.max(y1, b.maxY()); z1 = Math.max(z1, b.maxZ());
		}
		return Box3.of(x0 - 2, y0 - 2, z0 - 2, x1 - x0 + 4, y1 - y0 + 4, z1 - z0 + 4);
	}

	static List<SceneDoc> scenes(boolean exterior, int perCity, Object[]... gates) throws Exception {
		List<SceneDoc> out = new ArrayList<>();
		for (Object[] g : gates) {
			TerrainProbe[] ex = new TerrainProbe[1];
			CityPlanner.City city = WaterfrontTest.gateCity(g, ex);
			var styles = new StyleField(dev.fractalbuildings.core.probe.RecordedProbe.read(java.nio.file.Path.of("../fixtures/terrain/" + g[0] + ".json.gz")), (Long) g[1]);
			var biome = dev.fractalbuildings.core.probe.RecordedProbe.read(java.nio.file.Path.of("../fixtures/terrain/" + g[0] + ".json.gz"));
			List<DistrictPlan> ds = new ArrayList<>(city.districts().values());
			ds.sort((a, b) -> b.lots().size() - a.lots().size());
			for (int i = 0; i < Math.min(perCity, ds.size()); i++) {
				DistrictPlan f = Briefs.fill(ds.get(i), QuarterKind.values()[ds.get(i).district().quarter()], styles, ex[0]);
				out.add(DistrictScenes.build(f, city.landmarks().getOrDefault(f.district().id(), List.of()), ex[0], biome, exterior).scene());
			}
		}
		return out;
	}

	static final Map<Boolean, List<SceneDoc>> cache = new HashMap<>();

	/** Full interiors (doors, stairs, rooms). */
	static List<SceneDoc> scenes() throws Exception { return scenes(false); }

	static synchronized List<SceneDoc> scenes(boolean exterior) throws Exception {
		if (!cache.containsKey(exterior)) cache.put(exterior, scenes(exterior, 3, WaterfrontTest.GATES[0], WaterfrontTest.GATES[2]));
		return cache.get(exterior);
	}

	@Test
	void everyCellHasOneOwnerWithInteriors() throws Exception { ownership(false); }

	@Test
	void everyCellHasOneOwnerExteriorOnly() throws Exception { ownership(true); }

	void ownership(boolean exterior) throws Exception {
		int roomAir = 0, cells = 0, bCells = 0, gCells = 0, bAir = 0, yielded = 0, bbOverlap = 0;
		for (SceneDoc s : scenes(exterior)) {
			Map<Long, Cell> m = rasterScene(s, hull(s), null);
			// independent expectation: buildings by VoxelGrid (the first building wins an overlap), ground by brute force
			Map<Long, Role> bSolid = new HashMap<>();
			for (PlacedBuilding b : s.buildings()) {
				var p = b.placement();
				VoxelGrid.of(b).forEach((x, y, z, r) -> {
					if (bSolid.putIfAbsent(key(p.worldX(x, z), p.worldY(y), p.worldZ(x, z)), r) != null) { /* counted below */ }
				});
			}
			Map<Long, Role> gSolid = new HashMap<>();
			for (Box b : s.ground()) {
				Box3 q = b.box();
				for (int y = q.y(); y < q.maxY(); y++) for (int z = q.z(); z < q.maxZ(); z++) for (int x = q.x(); x < q.maxX(); x++) {
					if (b.role() == Role.AIR) gSolid.remove(key(x, y, z)); else gSolid.put(key(x, y, z), b.role());
				}
			}
			for (var e : m.entrySet()) {
				Cell c = e.getValue();
				if (c.owner().equals("ground")) {
					gCells++;
					if (c.role() != Role.AIR) assertEquals(gSolid.get(e.getKey()), c.role(), "ground cell role");
					assertTrue(c.role() == Role.AIR || !bSolid.containsKey(e.getKey()), "ground emitted under a building cell");
				} else {
					bCells++;
					if (c.role() == Role.AIR) bAir++;
					else assertEquals(bSolid.get(e.getKey()), c.role(), "building cell role");
				}
			}
			for (var e : bSolid.entrySet()) {
				Cell c = m.get(e.getKey());
				assertTrue(c != null && !c.owner().equals("ground"), "building cell has no building owner");
			}
			for (var e : gSolid.entrySet()) {
				Cell c = m.get(e.getKey());
				if (c == null) fail("gap: ground cell " + e.getKey() + " has no owner");
				if (!c.owner().equals("ground")) yielded++;
			}
			for (PlacedBuilding b : s.buildings())
				for (var room : b.shell().rooms())
					for (Box3 part : room.parts()) {
						Box3 q = b.placement().toWorld(part);
						for (int y = q.y(); y < q.maxY(); y++) for (int z = q.z(); z < q.maxZ(); z++) for (int x = q.x(); x < q.maxX(); x++) {
							Cell c = m.get(key(x, y, z));
							assertTrue(c != null && !c.owner().equals("ground"), "room cell " + x + "," + y + "," + z + " of " + b.id() + " not building-owned");
							if (c.role() == Role.AIR) roomAir++;
						}
					}
			cells += m.size();
		}
		System.out.println("raster (" + (exterior ? "exterior" : "interiors") + "): " + roomAir + " room air cells counted, " + scenes(exterior).size() + " districts, " + cells + " cells (" + bCells + " building incl. " + bAir + " air clears, " + gCells + " ground), " + yielded + " ground cells yielded to buildings");
		assertTrue(bCells > 20000 && gCells > 20000, bCells + " building, " + gCells + " ground cells");
	}

	static void fail(String m) { throw new AssertionError(m); }

	@Test
	void rasterIsTheSameWhateverTheClipsAndDeterministic() throws Exception {
		for (SceneDoc s : scenes()) {
			Box3 h = hull(s);
			Map<Long, Cell> whole = rasterScene(s, h, null);
			assertEquals(whole, rasterScene(s, h, null), "second run");
			// 16 x 16 columns (chunks), in a scrambled order
			Map<Long, Cell> parts = new HashMap<>();
			List<int[]> chunks = new ArrayList<>();
			for (int cx = Math.floorDiv(h.x(), 16); cx <= Math.floorDiv(h.maxX() - 1, 16); cx++)
				for (int cz = Math.floorDiv(h.z(), 16); cz <= Math.floorDiv(h.maxZ() - 1, 16); cz++) chunks.add(new int[] {cx, cz});
			java.util.Collections.shuffle(chunks, new java.util.Random(7));
			for (int[] c : chunks) {
				Box3 clip = Box3.of(c[0] * 16, h.y(), c[1] * 16, 16, h.sy(), 16);
				for (var e : rasterScene(s, clip, null).entrySet()) assertNull(parts.put(e.getKey(), e.getValue()), "cell in two chunks");
			}
			// compare inside the hull only (chunks reach beyond it, but there are no cells there)
			assertEquals(whole.size(), parts.size());
			assertEquals(whole, parts, "chunked raster differs from the whole (shapes must not depend on the clip)");
		}
	}

	@Test
	void doorsAndStairsFaceTheRightWay() throws Exception {
		int doors = 0, upper = 0, stairs = 0, flat = 0, logs = 0;
		for (SceneDoc s : scenes()) {
			Map<Long, Cell> m = rasterScene(s, hull(s), null);
			Map<Long, Role> roles = new HashMap<>();
			m.forEach((k, c) -> roles.put(k, c.role()));
			Box3 h = hull(s);
			for (int y = h.y(); y < h.maxY(); y++) for (int z = h.z(); z < h.maxZ(); z++) for (int x = h.x(); x < h.maxX(); x++) {
				Cell c = m.get(key(x, y, z));
				if (c == null) continue;
				if (c.role() == Role.DOOR) {
					doors++;
					Shape sh = c.shape();
					assertTrue(sh.facing() != null && sh.half() != null && sh.hinge() != null, "door at " + x + "," + y + "," + z);
					Role below = roles.getOrDefault(key(x, y - 1, z), Role.AIR);
					assertEquals(below == Role.DOOR ? Shape.Half.UPPER : Shape.Half.LOWER, sh.half());
					if (sh.half() == Shape.Half.UPPER) upper++;
					// walking through: the cells in front of and behind the door are not solid (passable), the wall runs across
					Facing f = sh.facing();
					Role fr = roles.getOrDefault(key(x + f.dx, y, z + f.dz), Role.AIR), bk = roles.getOrDefault(key(x - f.dx, y, z - f.dz), Role.AIR);
					assertTrue(!fr.solid && !bk.solid, "door at " + x + "," + y + "," + z + " facing " + f + " is walled in front " + fr + " or behind " + bk);
				} else if (c.role() == Role.STAIR) {
					if (c.shape() == Shape.NONE) {
						flat++;
						// within one building's flight (CITYQ: two neighbouring shops' entry steps at different front levels can stand side by side)
						for (Facing q : Facing.values()) {
							Cell hi = m.get(key(x + q.dx, y + 1, z + q.dz));
							assertTrue(hi == null || hi.role() != Role.STAIR || !hi.owner().equals(c.owner()), "full-block step at " + x + "," + y + "," + z + " has a higher step " + q);
						}
						continue;
					}
					stairs++;
					Facing f = c.shape().facing();
					assertEquals(Shape.Half.BOTTOM, c.shape().half());
					// higher ahead, or the last step of a flight (it tops out flush with the floor and is left sideways, STAIRS): the step it climbed from is behind it
					assertTrue(roles.getOrDefault(key(x + f.dx, y + 1, z + f.dz), Role.AIR).solid || roles.getOrDefault(key(x + f.dx, y + 1, z + f.dz), Role.AIR) == Role.DOOR || roles.getOrDefault(key(x - f.dx, y - 1, z - f.dz), Role.AIR) == Role.STAIR, "stair at " + x + "," + y + "," + z + " faces " + f + " but nothing higher there");
				} else if (c.role() == Role.BEAM && c.shape().axis() != null) {
					logs++;
				}
			}
		}
		System.out.println("shapes: " + doors + " door cells (" + upper + " upper), " + stairs + " stair shapes, " + flat + " full-block steps, " + logs + " log axes");
		assertTrue(doors > 50, "doors " + doors);
	}

	/** Top-down pictures of the rastered districts (build/raster/*.png): left role colours, right owners with door and stair facings. */
	@Test
	void writeOwnershipPictures() throws Exception {
		java.nio.file.Path dir = java.nio.file.Path.of("build/raster");
		java.nio.file.Files.createDirectories(dir);
		int n = 0;
		for (SceneDoc s : scenes()) {
			Box3 h = hull(s);
			Map<Long, Cell> m = rasterScene(s, h, null);
			int sc = 3, w = h.sx(), d = h.sz();
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w * sc * 2 + 4, d * sc, java.awt.image.BufferedImage.TYPE_INT_RGB);
			int[] topY = new int[w * d];
			Cell[] top = new Cell[w * d];
			java.util.Arrays.fill(topY, Integer.MIN_VALUE);
			for (var e : m.entrySet()) {
				long k = e.getKey();
				int x = (int) (k >> 40) - 100000, y = (int) ((k >> 24) & 0xffff) - 1000, z = (int) (k & 0xffffff) - 100000;
				if (e.getValue().role() == Role.AIR) continue;
				int i = (z - h.z()) * w + (x - h.x());
				if (y > topY[i]) { topY[i] = y; top[i] = e.getValue(); }
			}
			for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) {
				Cell c = top[z * w + x];
				int a = 0x1b1d22, b = a;
				if (c != null) {
					a = switch (c.role()) {
						case ROAD -> 0xb09a6e; case PAVING -> 0xc8c8c8; case GROUND -> 0x6b4f33; case DECK -> 0x9a7b4f; case ROOF -> 0xb04a3a;
						case WALL -> 0xe8e0c8; case WINDOW -> 0x66aaff; case DOOR -> 0xff2020; case STAIR -> 0xffff00; case FOUNDATION -> 0x777777;
						case BEAM, TRIM -> 0x5a3b1e; case FENCE -> 0xff9900; case FLOOR -> 0xa07040; default -> 0xff00ff; };
					int hsh = c.owner().hashCode() * 0x9E3779B1;
					b = c.owner().equals("ground") ? 0x303840 : 0x404040 | ((hsh >>> 8) & 0xbfbfbf) | 0x303030;
				}
				for (int py = 0; py < sc; py++) for (int px = 0; px < sc; px++) { img.setRGB(x * sc + px, z * sc + py, a); img.setRGB(w * sc + 4 + x * sc + px, z * sc + py, b); }
				if (c != null && c.shape().facing() != null && (c.role() == Role.DOOR || c.role() == Role.STAIR)) {
					Facing f = c.shape().facing();
					img.setRGB(w * sc + 4 + x * sc + 1 + f.dx, z * sc + 1 + f.dz, c.role() == Role.DOOR ? 0xff0000 : 0xffff00);
					img.setRGB(w * sc + 4 + x * sc + 1, z * sc + 1, 0xffffff);
				}
			}
			javax.imageio.ImageIO.write(img, "png", dir.resolve("district-" + n++ + ".png").toFile());
		}
	}
}
