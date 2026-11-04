package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.raster.Shape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * DS36ROOFS r2, over real gate-window tiles (every building piece of a tile, as the mod writes them):
 * <ul>
 *   <li>every roof stair's corner is what Minecraft's own rule ({@code StairBlock.getStairsShape}, 26.3 lines 121-153) computes from the whole
 *       tile's blocks, so a vanilla neighbour update finds nothing to change. The rule is written out here independently of {@code Shapes};
 *   <li>a building piece's stair seam ({@link WorldPlanner#seam}) over a chunk clip is exactly the stair cells one block outside the clip beside
 *       the piece's own stairs inside it, each as the tile's world holds it (its owner's cell).
 * </ul>
 */
class StairSeamTest {
	record Cell(Role role, String id, Shape shape, int owner) {}

	static long key(int x, int y, int z) { return ((long) (x + 100000) << 40) | ((long) (y + 1000) << 24) | (z + 100000); }

	static final Box3 ALL = new Box3(-100_000, -64, -100_000, 200_000, 400, 200_000);

	/** The tile's building cells as the world holds them: each cell from the first piece that emits it (the pieces never overlap). */
	static Map<Long, Cell> world(WorldPlanner w, List<BuildingPiece> bs) {
		Map<Long, Cell> m = new HashMap<>();
		for (int i = 0; i < bs.size(); i++) {
			int owner = i;
			w.raster(bs.get(i).ref(), ALL, (x, y, z, role, id, shape) -> assertNull(m.put(key(x, y, z), new Cell(role, id, shape, owner)), "two owners"));
		}
		return m;
	}

	/** The facing of the stair block at the cell, or null. */
	static Facing stair(Map<Long, Cell> m, int x, int y, int z) {
		Cell c = m.get(key(x, y, z));
		if (c == null || !(c.role().isRoofStair() || c.role() == Role.STAIR)) return null;
		return c.shape().facing();
	}

	/** Minecraft's getStairsShape (all our stairs are bottom halves), as a corner (null = straight). */
	static Shape.Corner minecraft(Map<Long, Cell> m, int x, int y, int z, Facing f) {
		Facing behind = stair(m, x + f.dx, y, z + f.dz);
		if (behind != null && (behind.ordinal() % 2) != (f.ordinal() % 2) && canTake(m, x, y, z, f, behind.rotate(2)))
			return behind == f.rotate(-1) ? Shape.Corner.OUTER_LEFT : Shape.Corner.OUTER_RIGHT;
		Facing front = stair(m, x - f.dx, y, z - f.dz);
		if (front != null && (front.ordinal() % 2) != (f.ordinal() % 2) && canTake(m, x, y, z, f, front))
			return front == f.rotate(-1) ? Shape.Corner.INNER_LEFT : Shape.Corner.INNER_RIGHT;
		return null;
	}

	static boolean canTake(Map<Long, Cell> m, int x, int y, int z, Facing f, Facing side) {
		Facing n = stair(m, x + side.dx, y, z + side.dz);
		return n == null || n != f;
	}

	/** The pre-r2 rule: only roof stairs of the same building count. */
	static Shape.Corner ownRoofOnly(Map<Long, Cell> m, int x, int y, int z, Facing f, int owner) {
		Map<Long, Cell> own = new HashMap<>();
		for (int[] d : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
			Cell c = m.get(key(x + d[0], y, z + d[1]));
			if (c != null && c.owner() == owner && c.role().isRoofStair()) own.put(key(x + d[0], y, z + d[1]), c);
		}
		return minecraft(own, x, y, z, f);
	}

	static List<List<BuildingPiece>> tiles(String gate, int every) {
		GateWorld g = GateWorld.load(gate);
		WorldPlanner w = g.sharedPlanner();
		List<List<BuildingPiece>> out = new ArrayList<>();
		int k = 0;
		for (TileInfo info : new TreeMap<>(WorldPlannerParityTest.planned(w, g)).values()) {
			if (k++ % every != 0) continue;
			List<BuildingPiece> bs = w.buildings(info.ix(), info.iz());
			if (!bs.isEmpty()) out.add(bs);
		}
		return out;
	}

	static final String[] GATES = {"gate-temperate-city", "gate-snow-city", "gate-jungle-savanna"};

	@Test
	void everyRoofStairHasMinecraftsShapeOverTheWholeTile() {
		int stairs = 0, corners = 0, bad = 0, byOthers = 0;
		List<String> fails = new ArrayList<>();
		for (String gate : GATES) {
			WorldPlanner w = GateWorld.load(gate).sharedPlanner();
			for (List<BuildingPiece> bs : tiles(gate, 3)) {
				Map<Long, Cell> m = world(w, bs);
				for (var e : m.entrySet()) {
					Cell c = e.getValue();
					if (!c.role().isRoofStair()) continue;
					long k = e.getKey();
					int x = (int) (k >>> 40) - 100000, y = (int) ((k >>> 24) & 0xffff) - 1000, z = (int) (k & 0xffffff) - 100000;
					Facing f = c.shape().facing();
					Shape.Corner want = minecraft(m, x, y, z, f);
					stairs++;
					if (want != null) corners++;
					if (want != ownRoofOnly(m, x, y, z, f, c.owner())) byOthers++;
					if (want != c.shape().corner()) {
						bad++;
						if (fails.size() < 10) fails.add(gate + " " + x + "," + y + "," + z + " " + c.role() + " has " + c.shape().corner() + ", Minecraft gives " + want);
					}
				}
			}
		}
		System.out.println("[stairseam] roof stairs " + stairs + ", corners " + corners + ", shape set by a stair of another building or an interior/porch stair " + byOthers + ", wrong " + bad);
		assertTrue(stairs > 5000, "roof stairs " + stairs);
		assertEquals(List.of(), fails, "roof stairs whose shape Minecraft would change (" + bad + ")");
	}

	/**
	 * ORDERSTAIR: the same for interior and porch stairs ({@link Role#STAIR} cells that are stair blocks). A porch flight that turns at a landing
	 * puts a stair turned across in front of the last step; before ORDERSTAIR the raster left that step straight while Minecraft gives an outer
	 * corner, so a vanilla tree beside it in a later neighbour chunk ({@code updateShapeAtEdge}) turned it by chunk order (order test
	 * {@code forward}, 1222,67,-9441, HEAVY14).
	 */
	@Test
	void everyInteriorAndPorchStairHasMinecraftsShapeOverTheWholeTile() {
		int stairs = 0, corners = 0, bad = 0;
		List<String> fails = new ArrayList<>();
		for (String gate : GATES) {
			WorldPlanner w = GateWorld.load(gate).sharedPlanner();
			for (List<BuildingPiece> bs : tiles(gate, 3)) {
				Map<Long, Cell> m = world(w, bs);
				for (var e : m.entrySet()) {
					Cell c = e.getValue();
					if (c.role() != Role.STAIR || c.shape().facing() == null) continue;
					long k = e.getKey();
					int x = (int) (k >>> 40) - 100000, y = (int) ((k >>> 24) & 0xffff) - 1000, z = (int) (k & 0xffffff) - 100000;
					Shape.Corner want = minecraft(m, x, y, z, c.shape().facing());
					stairs++;
					if (want != null) corners++;
					if (want != c.shape().corner()) {
						bad++;
						if (fails.size() < 10) fails.add(gate + " " + x + "," + y + "," + z + " STAIR " + c.shape().facing() + " has " + c.shape().corner() + ", Minecraft gives " + want);
					}
				}
			}
		}
		System.out.println("[stairseam] interior/porch stairs " + stairs + ", corners " + corners + ", wrong " + bad);
		assertTrue(stairs > 1000, "interior/porch stairs " + stairs);
		assertTrue(corners > 0, "no corner among the interior/porch stairs: the check would not see the ORDERSTAIR case");
		assertEquals(List.of(), fails, "interior/porch stairs whose shape Minecraft would change (" + bad + ")");
	}

	@Test
	void theSeamIsTheStairsAcrossTheClipBesideThePiecesOwnStairs() {
		int pieces = 0, seamCells = 0;
		for (String gate : GATES) {
			WorldPlanner w = GateWorld.load(gate).sharedPlanner();
			for (List<BuildingPiece> bs : tiles(gate, 4)) {
				Map<Long, Cell> m = world(w, bs);
				for (int i = 0; i < bs.size(); i++) {
					BuildingPiece b = bs.get(i);
					Box3 box = b.box();
					for (int cx = Math.floorDiv(box.x(), 16); cx <= Math.floorDiv(box.maxX() - 1, 16); cx++) {
						for (int cz = Math.floorDiv(box.z(), 16); cz <= Math.floorDiv(box.maxZ() - 1, 16); cz++) {
							int x0 = Math.max(box.x(), cx * 16), x1 = Math.min(box.maxX(), cx * 16 + 16), z0 = Math.max(box.z(), cz * 16), z1 = Math.min(box.maxZ(), cz * 16 + 16);
							Box3 clip = new Box3(x0, -64, z0, x1 - x0, 384, z1 - z0);
							TreeMap<Long, Cell> want = new TreeMap<>();
							for (var e : m.entrySet()) {
								Cell c = e.getValue();
								if (c.owner() != i || !(c.role().isRoofStair() || c.role() == Role.STAIR && c.shape().facing() != null)) continue;
								long k = e.getKey();
								int x = (int) (k >>> 40) - 100000, y = (int) ((k >>> 24) & 0xffff) - 1000, z = (int) (k & 0xffffff) - 100000;
								if (x < x0 || x >= x1 || z < z0 || z >= z1) continue;
								for (Facing f : Facing.values()) {
									int nx = x + f.dx, nz = z + f.dz;
									if (nx >= x0 && nx < x1 && nz >= z0 && nz < z1) continue;
									Cell n = m.get(key(nx, y, nz));
									if (n != null && stair(m, nx, y, nz) != null) want.put(key(nx, y, nz), n);
								}
							}
							TreeMap<Long, Cell> got = new TreeMap<>();
							w.seam(b.ref(), clip, (x, y, z, role, id, shape) -> {
								Cell n = m.get(key(x, y, z));
								assertNotNull(n, "seam cell " + x + "," + y + "," + z + " is no building's");
								assertNull(got.put(key(x, y, z), new Cell(role, id, shape, n.owner())), "seam cell twice");
							});
							assertEquals(want, got, gate + " piece " + b.ref() + " chunk " + cx + "," + cz);
							pieces++;
							seamCells += got.size();
						}
					}
				}
			}
		}
		System.out.println("[stairseam] piece-chunks " + pieces + ", seam cells " + seamCells);
		assertTrue(seamCells > 100, "seam cells " + seamCells);
	}
}
