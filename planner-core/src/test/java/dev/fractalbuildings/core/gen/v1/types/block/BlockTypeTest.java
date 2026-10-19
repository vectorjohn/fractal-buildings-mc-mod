package dev.fractalbuildings.core.gen.v1.types.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.ShellGeneratorV1;
import dev.fractalbuildings.core.gen.v1.SpecPolicyV1;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.metrics.Diversity;
import dev.fractalbuildings.core.metrics.DiversityReport;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The city block (plan step T-block): massing statistics over many seeds and lot sizes, the door and
 * party-wall rules on the built shell, and diversity floors.
 */
class BlockTypeTest {
	static final int[][] LOTS = {{52, 52}, {52, 70}, {64, 56}, {100, 80}};
	static final BlockType TYPE = new BlockType();

	static BuildingSpec spec(String style, long seed, int lotW, int lotD, int storeys) {
		return new BuildingSpec(GeneratorV1.GEN_VERSION, seed, style, "block", lotW - 10, lotD - 10, storeys, "auto", 384, 15);
	}

	static Massing mass(String style, long seed, int lotW, int lotD) {
		BuildingSpec sp = spec(style, seed, lotW, lotD, 2 + (int) (seed % 4));
		BuildContext c = new BuildContext(sp, TYPE, StyleLibrary.get(style));
		Massing m = TYPE.mass(c);
		List<String> p = MassingCheck.check(m, sp.width(), sp.depth(), sp.maxHeight());
		assertTrue(p.isEmpty(), style + " seed " + seed + " " + lotW + "x" + lotD + ": " + p);
		return m;
	}

	static boolean[][] covered(Massing m, int w, int d) {
		boolean[][] cov = new boolean[w][d];
		for (Mass a : m.masses()) for (int x = a.x(); x < a.maxX(); x++) for (int z = a.z(); z < a.maxZ(); z++) cov[x][z] = true;
		return cov;
	}

	/** Free cells reachable from the envelope border (through free cells only): an alley into the courtyard. */
	static boolean courtyardOpensToStreet(boolean[][] cov, int w, int d) {
		boolean[][] seen = new boolean[w][d];
		java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
		for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) {
			if ((x == 0 || z == 0 || x == w - 1 || z == d - 1) && !cov[x][z]) { seen[x][z] = true; q.add(new int[] {x, z}); }
		}
		int[] dx = {1, -1, 0, 0}, dz = {0, 0, 1, -1};
		while (!q.isEmpty()) {
			int[] p = q.poll();
			for (int k = 0; k < 4; k++) {
				int x = p[0] + dx[k], z = p[1] + dz[k];
				if (x < 0 || z < 0 || x >= w || z >= d || cov[x][z] || seen[x][z]) continue;
				seen[x][z] = true;
				q.add(new int[] {x, z});
			}
		}
		int cx = w / 2, cz = d / 2; // the middle of a block is inside the courtyard
		return seen[cx][cz];
	}

	@Test
	void massingsAreUnitRingsWithCourtyardsAndVariedHeights() {
		int blocks = 0, withAlley = 0, big = 0, bigVaried = 0, sepHeights = 0, units = 0;
		for (String style : List.of("plain", "medieval"))
			for (int[] lot : LOTS)
				for (long seed = 0; seed < 150; seed++) {
					Massing m = mass(style, seed, lot[0], lot[1]);
					int w = lot[0] - 10, d = lot[1] - 10;
					blocks++;
					// every mass is an entrance (own door) and a ground mass; every unit above one storey has a core
					assertEquals(m.masses().size(), m.entrances().size());
					for (Mass a : m.masses()) {
						assertTrue(a.ground(), "unit on the ground");
						assertTrue(Math.min(a.sx(), a.sz()) >= 6 && Math.max(a.sx(), a.sz()) <= 12, "unit size " + a);
						assertTrue(a.to() >= 1 && a.to() <= 5);
						if (a.to() >= 2) {
							boolean core = false;
							for (var k : m.cores()) if (a.interior(k.x(), k.z()) && k.to() == a.to()) core = true;
							assertTrue(core, a.id() + " has its own core");
						}
					}
					// units touch only along party walls: every shared strip is a declared party wall
					for (int i = 0; i < m.masses().size(); i++)
						for (int j = i + 1; j < m.masses().size(); j++)
							if (Massing.strip(m.masses().get(i), m.masses().get(j)) != null) assertTrue(m.party(m.masses().get(i), m.masses().get(j)));
					// courtyard >= 20 % of the block (lot area)
					int court = BlockMassing.courtyard(covered(m, w, d), w, d);
					assertTrue(court >= 0.20 * lot[0] * lot[1], "courtyard " + court + " of " + lot[0] * lot[1] + " (" + style + " " + seed + " " + lot[0] + "x" + lot[1] + ")");
					if (!courtyardOpensToStreet(covered(m, w, d), w, d)) { /* closed courtyard: fine */ } else withAlley++;
					units += m.masses().size();
					if (m.masses().size() >= 8) {
						big++;
						TreeSet<Integer> hs = new TreeSet<>();
						for (Mass a : m.masses()) hs.add(a.to());
						if (hs.size() >= 3) bigVaried++;
						sepHeights += hs.size();
					}
				}
		System.out.println("block massing: " + blocks + " blocks, " + units + " units, alley " + withAlley + ", >=8 units " + big + ", >=3 heights " + bigVaried);
		assertEquals(big, blocks, "every test block has at least 8 units");
		assertEquals(big, bigVaried, ">= 3 distinct unit heights whenever there are >= 8 units");
		assertTrue(withAlley >= 0.25 * blocks && withAlley <= 0.75 * blocks, "alleys " + withAlley + " of " + blocks);
	}

	/** On the built shell: one street door per unit, on its street side; no opening in a party wall; each unit's rooms are its own. */
	@Test
	void doorsFaceTheStreetAndNeverPierceAPartyWall() {
		int checked = 0;
		for (String style : List.of("plain", "medieval"))
			for (int[] lot : new int[][] {{52, 52}, {64, 56}})
				for (long seed = 0; seed < 40; seed++) {
					BuildingSpec sp = spec(style, seed, lot[0], lot[1], 2 + (int) (seed % 4));
					BuildingIr ir = ShellGeneratorV1.generate(sp, TYPE, StyleLibrary.get(style));
					BuildContext c = new BuildContext(sp, TYPE, StyleLibrary.get(style));
					Massing m = TYPE.mass(c);
					int doors = 0;
					for (Opening o : ir.openings()) {
						if (o.kind() == OpeningKind.DOOR && Opening.OUTSIDE.equals(o.to())) {
							doors++;
							Room r = room(ir, o.from());
							Mass u = m.mass(r.volume());
							assertEquals(BlockMassing.street(u, sp.width(), sp.depth()), o.facing(), "door of " + u.id() + " faces its street");
						}
						for (int i = 0; i < m.masses().size(); i++)
							for (int j = i + 1; j < m.masses().size(); j++) {
								int[] st = Massing.strip(m.masses().get(i), m.masses().get(j));
								if (st == null) continue;
								Mass ma = m.masses().get(i), mb = m.masses().get(j); // the wall stands only where both units do; above the lower unit it is an outer wall
								int y0 = m.grid().y(Math.max(ma.from(), mb.from())), y1 = m.grid().y(Math.min(ma.to(), mb.to()));
								Box3 strip = Box3.of(st[0], y0, st[1], st[2], y1 - y0 + 1, st[3]);
								assertTrue(!intersects(strip, o.box()), "opening " + o.id() + " (" + o.kind() + ") in the party wall of " + m.masses().get(i).id() + "/" + m.masses().get(j).id());
							}
					}
					assertEquals(m.masses().size(), doors, "one street door per unit");
					checked++;
				}
		assertEquals(160, checked);
	}

	private static Room room(BuildingIr ir, String id) {
		for (Room r : ir.rooms()) if (r.id().equals(id)) return r;
		throw new AssertionError("no room " + id);
	}

	private static boolean intersects(Box3 a, Box3 b) {
		return a.x() < b.maxX() && b.x() < a.maxX() && a.y() < b.maxY() && b.y() < a.maxY() && a.z() < b.maxZ() && b.z() < a.maxZ();
	}

	/** Gentle-ground-only type: a 1:3 lot is rejected cleanly, a 1:16 lot builds with no violations. */
	@Test
	void steepLotsAreRejectedGentleOnesBuild() {
		for (long seed = 0; seed < 4; seed++) {
			final long s = seed;
			var bad = new GenRequest(s, new Lot("lot0", 0, 0, 64, 56, Facing.NORTH), "block", "plain",
					(x, z) -> 64 + Math.floorDiv(x, 3), (x, z) -> "plains");
			org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new GeneratorV1().generate(bad));
			var ok = new GenRequest(s, new Lot("lot0", 0, 0, 64, 56, Facing.NORTH), "block", "plain",
					(x, z) -> 64 + Math.floorDiv(x, 16), (x, z) -> "plains");
			var scene = new GeneratorV1().generate(ok);
			assertTrue(dev.fractalbuildings.core.check.Invariants.check(scene, ok.terrain()).isEmpty());
		}
	}

	@Test
	void sameSeedSameMassing() {
		for (long seed = 0; seed < 50; seed++) assertEquals(mass("medieval", seed, 64, 48), mass("medieval", seed, 64, 48));
	}

	/** Measured 2026-10 (256 seeds, medieval 64x56): Jaccard 0.619, near-dup 0, 239 signatures; plain 40x40: 214 signatures. Floors ~80 %. */
	@Test
	void diversityFloors() {
		for (String[] cfg : new String[][] {{"medieval", "64x56"}, {"plain", "52x52"}}) {
			int lw = Integer.parseInt(cfg[1].split("x")[0]), ld = Integer.parseInt(cfg[1].split("x")[1]);
			List<PlacedBuilding> l = new ArrayList<>();
			for (long s = 0; s < 256; s++) {
				GenRequest rq = new GenRequest(s, new Lot("lot0", 0, 0, lw, ld, Facing.NORTH), "block", cfg[0], (x, z) -> 64, (x, z) -> "plains");
				l.add(new GeneratorV1().generate(rq).buildings().get(0));
			}
			DiversityReport r = Diversity.of(l);
			System.out.println("block diversity " + cfg[0] + " " + cfg[1] + ": " + r.meanJaccardDistance() + " " + r.nearDuplicateRate() + " " + r.distinctSignatures());
			assertTrue(r.meanJaccardDistance() >= 0.50, cfg[0] + " mean distance " + r.meanJaccardDistance());
			assertTrue(r.nearDuplicateRate() <= 0.01, cfg[0] + " near-dup " + r.nearDuplicateRate());
			assertTrue(r.distinctSignatures() >= 170, cfg[0] + " signatures " + r.distinctSignatures());
		}
	}
}
