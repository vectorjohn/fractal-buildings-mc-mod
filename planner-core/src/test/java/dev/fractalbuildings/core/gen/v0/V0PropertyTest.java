package dev.fractalbuildings.core.gen.v0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.RecordedProbe;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.rand.Draws;
import dev.fractalbuildings.core.rand.Hash64;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * 3.9b: generator v0 through {@link Invariants#check} over many seeds. Each case draws its lot,
 * front, terrain and style from the seed. Seed count: {@code -Dfb.propertySeeds=N} (default 2000).
 * Terrain stays at or above y -60 so the in-world rule is not tripped by the test terrain.
 */
class V0PropertyTest {
	static final int SEEDS = Integer.getInteger("fb.propertySeeds", 2000);
	static final int[][] LOT_SIZES = {{7, 7}, {12, 10}, {20, 20}, {31, 17}};
	static final String[] STYLES = {"plain", "medieval"};
	static final String[] TERRAINS = {"flat", "plane-x", "plane-z", "plane-xz", "hill", "bumpy", "steep-x", "steep-z", "recorded"};
	static final int[][] CROP_SPOTS = {{-1300, -4000}, {-700, -3500}, {0, -3000}, {300, -2400}};

	static RecordedProbe crop;

	static synchronized RecordedProbe crop() throws Exception {
		if (crop == null) crop = RecordedProbe.read(Path.of("../fixtures/terrain/s4-12345-crop32.json"));
		return crop;
	}

	record Case(long seed, int sizeIdx, Facing front, String terrain, String style, Lot lot, TerrainProbe probe) {
		@Override public String toString() {
			return "seed=" + seed + " terrain=" + terrain + " lot=" + lot + " style=" + style
					+ " (replay: cli gen --seed " + seed + " --lot " + lot.w() + "x" + lot.d() + " --style " + style + ")";
		}
	}

	/** Plane whose height at the lot centre is centreY, so far-away lots stay inside the world. */
	static TerrainProbe plane(int centreY, int rx, int rz, int run, int cx, int cz) {
		int y0 = centreY - SyntheticTerrain.plane(0, rx, rz, run).floorY(cx, cz);
		return SyntheticTerrain.plane(y0, rx, rz, run);
	}

	static Case draw(long seed, int sizeIdx, int frontIdx, int terrainIdx, int styleIdx) throws Exception {
		Draws r = new Draws(Hash64.hash(seed, "case-terrain"));
		int[] sz = LOT_SIZES[sizeIdx];
		String t = TERRAINS[terrainIdx];
		int lx = r.range(-600, 600), lz = r.range(-600, 600);
		TerrainProbe p;
		switch (t) {
			case "flat" -> p = SyntheticTerrain.flat(r.range(-50, 250));
			case "plane-x" -> p = plane(r.range(0, 200), 1, 0, r.range(2, 6), lx + sz[0] / 2, lz + sz[1] / 2);
			case "plane-z" -> p = plane(r.range(0, 200), 0, 1, r.range(2, 6), lx + sz[0] / 2, lz + sz[1] / 2);
			case "plane-xz" -> p = plane(r.range(0, 200), 1, 1, r.range(3, 8), lx + sz[0] / 2, lz + sz[1] / 2);
			case "hill" -> p = SyntheticTerrain.hill(lx + sz[0] / 2, lz + sz[1] / 2, r.range(4, 30), r.range(8, 40), r.range(40, 120));
			case "bumpy" -> p = SyntheticTerrain.noise(seed, r.range(40, 150), r.range(2, 8), r.range(3, 10));
			case "steep-x" -> p = plane(r.range(20, 150), 1, 0, 1, lx + sz[0] / 2, lz + sz[1] / 2);
			case "steep-z" -> p = plane(r.range(20, 150), 0, 1, 1, lx + sz[0] / 2, lz + sz[1] / 2);
			case "recorded" -> {
				int[] spot = CROP_SPOTS[r.below(CROP_SPOTS.length)];
				lx = spot[0] + r.range(-40, 40);
				lz = spot[1] + r.range(-40, 40);
				p = crop();
			}
			default -> throw new IllegalStateException(t);
		}
		Facing f = Facing.values()[frontIdx];
		Lot lot = new Lot("lot0", lx, lz, sz[0], sz[1], f);
		return new Case(seed, sizeIdx, f, t, STYLES[styleIdx], lot, p);
	}

	static Case drawn(long seed) throws Exception {
		long h = Hash64.hash(seed, "case");
		int[] d = new int[4];
		for (int i = 0; i < 4; i++) d[i] = (int) Math.floorMod(Hash64.hash(h, "case", i), 1_000_003L);
		return draw(seed, d[0] % LOT_SIZES.length, d[1] % 4, d[2] % TERRAINS.length, d[3] % STYLES.length);
	}

	static List<Violation> run(Case c) {
		SceneDoc s = new GeneratorV0().generate(new GenRequest(c.seed, c.lot, c.style, c.probe, (x, z) -> "plains"));
		return Invariants.check(s, c.probe);
	}

	static void report(List<String> failures, int cases) {
		if (!failures.isEmpty())
			fail(failures.size() + " of " + cases + " cases violate invariants; first 10:\n" + String.join("\n", failures.subList(0, Math.min(10, failures.size()))));
	}

	@Test
	void drawnCasesPassEveryInvariant() throws Exception {
		List<String> failures = new ArrayList<>();
		TreeSet<String> terrainsSeen = new TreeSet<>();
		for (long seed = 0; seed < SEEDS; seed++) {
			Case c = drawn(seed);
			terrainsSeen.add(c.terrain);
			var vs = run(c);
			if (!vs.isEmpty()) failures.add(c + " -> " + vs.stream().map(v -> v.invariant() + ":" + v.message()).toList());
		}
		if (SEEDS >= 200) assertEquals(TERRAINS.length, terrainsSeen.size(), "terrain coverage " + terrainsSeen);
		report(failures, SEEDS);
	}

	@Test
	void fullCrossProductOverFiftySeeds() throws Exception {
		List<String> failures = new ArrayList<>();
		int n = 0;
		for (long seed = 10_000; seed < 10_050; seed++)
			for (int sz = 0; sz < LOT_SIZES.length; sz++)
				for (int f = 0; f < 4; f++)
					for (int t = 0; t < TERRAINS.length; t++)
						for (int st = 0; st < STYLES.length; st++) {
							Case c = draw(seed, sz, f, t, st);
							n++;
							var vs = run(c);
							if (!vs.isEmpty()) failures.add(c + " -> " + vs.stream().map(v -> v.invariant() + ":" + v.message()).toList());
						}
		report(failures, n);
	}

	/** Can-fail proof: mutating generated output makes the matching invariant fire. */
	@Test
	void mutationsAreCaught() throws Exception {
		for (long seed = 1; seed <= 3; seed++) {
			Case c = draw(seed, 2, 0, 0, 0);
			SceneDoc s = new GeneratorV0().generate(new GenRequest(seed, c.lot, c.style, c.probe, (x, z) -> "plains"));
			assertEquals(List.of(), Invariants.check(s, c.probe));
			PlacedBuilding b = s.buildings().get(0);

			// remove all roof boxes -> roof no longer closed
			SceneDoc noRoof = withShellBoxes(s, b, bx -> bx.role() != Role.ROOF);
			assertTrue(!Invariants.check(noRoof, c.probe).isEmpty(), "no-roof mutation undetected, seed " + seed);

			// remove stairs (only meaningful if multi-storey)
			if (b.shell().storeys().size() > 1) {
				SceneDoc noStair = withShellBoxes(s, b, bx -> bx.role() != Role.STAIR);
				assertTrue(!Invariants.check(noStair, c.probe).isEmpty(), "no-stair mutation undetected, seed " + seed);
			}

			// remove doors -> rooms unreachable
			SceneDoc noDoor = withShellBoxes(s, b, bx -> bx.role() != Role.DOOR);
			assertTrue(!Invariants.check(noDoor, c.probe).isEmpty(), "no-door mutation undetected, seed " + seed);

			// lift the building two blocks -> floating or foundation off the ground
			PlacedBuilding up = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy() + 3, b.oz(), b.rotation(), b.shell(), b.siteBoxes());
			SceneDoc lifted = new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(up), s.violations());
			assertTrue(!Invariants.check(lifted, c.probe).isEmpty(), "lift mutation undetected, seed " + seed);
		}
	}

	static SceneDoc withShellBoxes(SceneDoc s, PlacedBuilding b, java.util.function.Predicate<Box> keep) {
		var sh = b.shell();
		var shell = new dev.fractalbuildings.core.ir.BuildingIr(sh.spec(), sh.bounds(), sh.storeys(), sh.volumes(), sh.rooms(),
				sh.openings(), sh.roofs(), sh.boxes().stream().filter(keep).toList());
		var nb = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), shell, b.siteBoxes().stream().filter(keep).toList());
		return new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), List.of(nb), s.violations());
	}
}
