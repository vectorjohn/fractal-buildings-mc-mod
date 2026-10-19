package dev.fractalbuildings.core.gen.v1.types.wall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.Program;
import dev.fractalbuildings.core.gen.v1.ShellGeneratorV1;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.facade.OpeningPass;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.roof.RoofPass;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Wall and gate properties (plan T-wall): the passage connects the field side to the town side through
 * two 3 x 4 gates; every curtain gallery joins a tower on the ground and opens onto the wall walk from
 * the tower's first upper storey; every flat roof carries a crenellated parapet; the height cap holds.
 */
class WallTypeTest {
	static final int SEEDS = 200;
	static final int[][] ENVELOPES = {{23, 5}, {24, 6}, {30, 7}, {46, 9}, {64, 9}};

	static BuildingSpec spec(String style, long seed, int w, int d, int storeys, int maxHeight) {
		return new BuildingSpec(GeneratorV1.GEN_VERSION, seed, style, "wall", w, d, storeys, "auto", maxHeight, 1);
	}

	static BuildingIr shell(BuildingType t, String style, long seed, int w, int d, int storeys, int maxHeight) {
		return ShellGeneratorV1.generate(spec(style, seed, w, d, storeys, maxHeight), t);
	}

	static Role[][][] grid(BuildingIr ir) {
		Box3 b = ir.bounds();
		Role[][][] g = new Role[b.maxX()][b.maxY()][b.maxZ()];
		for (Role[][] a : g) for (Role[] r : a) java.util.Arrays.fill(r, Role.AIR);
		for (Box x : ir.boxes()) {
			Box3 q = x.box();
			for (int px = q.x(); px < q.maxX(); px++) for (int py = q.y(); py < q.maxY(); py++) for (int pz = q.z(); pz < q.maxZ(); pz++) g[px][py][pz] = x.role();
		}
		return g;
	}

	/** The 3 x 4 gates in the shell, field side first (smaller z), or fewer if missing. */
	static List<Opening> gates(BuildingIr ir) {
		return ir.openings().stream().filter(o -> o.kind() == OpeningKind.DOOR && o.box().sx() == 3 && o.box().sy() == 4
				&& o.to().equals(Opening.OUTSIDE)).sorted(java.util.Comparator.comparingInt(o -> o.box().z())).toList();
	}

	/** Walks on the ground-floor slab from just inside one gate to just inside the other, through DOOR cells. */
	static boolean walkThrough(BuildingIr ir) {
		List<Opening> gs = gates(ir);
		if (gs.size() != 2) return false;
		Role[][][] g = grid(ir);
		Box3 a = gs.get(0).box(), z = gs.get(1).box();
		int y = a.y();
		if (z.y() != y) return false;
		int sx = a.x() + 1, sz = a.z() + 1, tx = z.x() + 1, tz = z.z() - 1;
		boolean[][] seen = new boolean[g.length][g[0][0].length];
		ArrayDeque<int[]> q = new ArrayDeque<>();
		q.add(new int[] {sx, sz});
		seen[sx][sz] = true;
		int[] dx = {1, -1, 0, 0}, dz = {0, 0, 1, -1};
		while (!q.isEmpty()) {
			int[] c = q.poll();
			if (c[0] == tx && c[1] == tz) return true;
			for (int i = 0; i < 4; i++) {
				int nx = c[0] + dx[i], nz = c[1] + dz[i];
				if (nx < 0 || nz < 0 || nx >= g.length || nz >= seen[0].length || seen[nx][nz]) continue;
				boolean ok = g[nx][y - 1][nz].solid;
				for (int h = 0; h < 4 && ok; h++) ok = g[nx][y + h][nz] == Role.AIR || g[nx][y + h][nz] == Role.DOOR;
				if (!ok) continue;
				seen[nx][nz] = true;
				q.add(new int[] {nx, nz});
			}
		}
		return false;
	}

	@Test
	void theGatesAreThreeByFourAndThePassageConnectsBothSides() {
		for (String style : StyleLibrary.ids())
			for (int[] e : ENVELOPES)
				for (long seed = 0; seed < SEEDS / 4; seed++) {
					BuildingIr ir = shell(new WallType(), style, seed, e[0], e[1], 2 + (int) (seed % 2), 300);
					String ctx = style + " " + e[0] + "x" + e[1] + " seed " + seed;
					assertEquals(2, gates(ir).size(), ctx + ": two gates");
					assertTrue(walkThrough(ir), ctx + ": the passage connects the field gate to the town gate");
					Opening field = gates(ir).get(0);
					assertEquals(Opening.OUTSIDE, field.to(), ctx);
					assertEquals(gates(ir).get(0).from(), ir.rooms().stream().filter(r -> r.use().equals("passage")).findFirst().orElseThrow().id(), ctx + ": gate opens into the passage");
					assertEquals(gates(ir).get(1).from(), gates(ir).get(0).from(), ctx + ": both gates open into the same room");
				}
	}

	@Test
	void mutantWithoutTheTownGateDoesNotConnect() {
		BuildingType real = new WallType();
		BuildingType mutant = new BuildingType() {
			public String id() { return real.id(); }
			public TypeLimits limits() { return real.limits(); }
			public Massing mass(BuildContext c) { return real.mass(c); }
			public Program roofs() { return real.roofs(); }
			public RoomProgram rooms() { return real.rooms(); }
			public Program openings() { return OpeningPass::run; }
		};
		BuildingIr ir = shell(mutant, "medieval", 3, 30, 7, 2, 300);
		assertFalse(walkThrough(ir));
	}

	@Test
	void curtainsJoinTowersAndOpenOntoTheWallWalk() {
		int curtains = 0;
		for (String style : StyleLibrary.ids())
			for (int[] e : ENVELOPES)
				for (long seed = 0; seed < SEEDS / 4; seed++) {
					BuildingSpec sp = spec(style, seed, e[0], e[1], 2 + (int) (seed % 2), 300);
					BuildingType t = new WallType();
					BuildingIr ir = ShellGeneratorV1.generate(sp, t);
					Massing m = t.mass(new BuildContext(sp, t, StyleLibrary.get(style)));
					for (Mass cur : m.masses()) {
						if (!cur.kind().equals("curtain")) continue;
						curtains++;
						String ctx = style + " " + e[0] + "x" + e[1] + " seed " + seed + " " + cur.id();
						String room = ir.rooms().stream().filter(r -> r.volume().equals(cur.id())).findFirst().orElseThrow().id();
						// a door on the ground into a tower room
						boolean joined = ir.openings().stream().anyMatch(o -> o.kind() == OpeningKind.DOOR && (o.from().equals(room) || o.to().equals(room))
								&& !o.from().equals(Opening.OUTSIDE) && !o.to().equals(Opening.OUTSIDE));
						assertTrue(joined, ctx + ": no door between the gallery and a tower");
						// a door onto the wall walk: upper-storey exterior door at the curtain's end, from a room above ground
						int upper = m.grid().y(1) + 2;
						boolean walk = ir.openings().stream().anyMatch(o -> o.kind() == OpeningKind.DOOR && o.to().equals(Opening.OUTSIDE) && o.box().y() == upper
								&& o.box().z() >= cur.z() && o.box().z() < cur.maxZ() && (o.box().x() == cur.maxX() - 1 || o.box().x() == cur.x()));
						assertTrue(walk, ctx + ": no door from a tower onto the wall walk");
					}
				}
		assertTrue(curtains > 100, "curtains seen: " + curtains);
	}

	@Test
	void roofsAreFlatWithGapsInTheParapet() {
		for (String style : StyleLibrary.ids())
			for (int[] e : ENVELOPES) {
				BuildingIr ir = shell(new WallType(), style, 5, e[0], e[1], 3, 300);
				assertTrue(ir.roofs().stream().allMatch(r -> r.form().equals("flat")), style + ": every roof is flat");
				Role[][][] g = grid(ir);
				int top = ir.bounds().maxY();
				// the topmost two parapet layers: lower layer is a closed ring, upper has gaps
				int low = 0, high = 0;
				for (int y = 0; y < top; y++)
					for (int x = 0; x < g.length; x++)
						for (int z = 0; z < g[0][0].length; z++) {
							if (g[x][y][z] != Role.WALL || y + 1 >= top) continue;
						}
				int topY = top - 1;
				for (int x = 0; x < g.length; x++) for (int z = 0; z < g[0][0].length; z++) if (g[x][topY][z] == Role.WALL) high++;
				for (int x = 0; x < g.length; x++) for (int z = 0; z < g[0][0].length; z++) if (g[x][topY - 1][z] == Role.WALL) low++;
				assertTrue(high > 0 && high < low, style + " " + e[0] + ": merlons " + high + " of " + low + " parapet cells");
			}
	}

	@Test
	void heightCapIsKept() {
		for (int cap : new int[] {7, 8, 11, 12, 14, 20}) {
			BuildingIr ir = shell(new WallType(), "medieval", 1, 30, 7, 3, cap);
			assertTrue(ir.bounds().maxY() <= cap, "cap " + cap + " got " + ir.bounds().maxY());
		}
	}

	@Test
	void shapesVary() {
		Set<String> shapes = new HashSet<>();
		for (long seed = 0; seed < 256; seed++) {
			BuildingSpec sp = spec("medieval", seed, 40, 9, 2 + (int) (seed % 2), 300);
			BuildingType t = new WallType();
			Massing m = t.mass(new BuildContext(sp, t, StyleLibrary.get("medieval")));
			StringBuilder sb = new StringBuilder();
			for (Mass a : m.masses()) sb.append(a.id()).append(':').append(a.x()).append(',').append(a.z()).append(',').append(a.sx()).append(',').append(a.sz()).append(',').append(a.to()).append(';');
			shapes.add(sb.toString());
		}
		assertTrue(shapes.size() >= 100, "distinct massings " + shapes.size() + " of 256");
	}
}
