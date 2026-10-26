package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.v1.facade.FacadePass;
import dev.fractalbuildings.core.gen.v1.facade.FacadePlan;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.style.FacadeStyle;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Facade layout rules (4.4): bays, bands, entrances, decor placement. Runs every {@link TestMassings} fixture. */
class FacadeLayoutTest {
	/** A fixture type that records the facade plan and the faces it was built from. */
	static final class Capture implements BuildingType {
		final TestMassings.FixtureType base;
		FacadePlan plan;
		List<Face> faces;

		Capture(Massing m) { base = new TestMassings.FixtureType(m); }

		@Override public String id() { return base.id(); }

		@Override public TypeLimits limits() { return base.limits(); }

		@Override public Massing mass(BuildContext c) { return base.mass(c); }

		@Override public Program facade() { return c -> { FacadePass.run(c); plan = c.facadePlan(); faces = c.faces(); }; }
	}

	record Built(BuildingIr ir, Capture cap) {}

	static Built build(Massing m, long seed, StyleRules style) {
		Capture cap = new Capture(m);
		BuildingIr ir = ShellGeneratorV1.generate(TestMassings.spec(seed, style.id()), cap, style);
		return new Built(ir, cap);
	}

	static StyleRules withFacade(StyleRules s, FacadeStyle f) {
		return new StyleRules(s.id(), s.biomes(), s.massing(), s.roof(), f, s.interior(), s.palette(), s.colours());
	}

	/** A mirror-symmetric tiling of len exists: some bay count in range is odd, or len is even. */
	static boolean mirrorExists(int len, int min, int max) {
		if (len % 2 == 0) return len >= min;
		for (int n = (len + max - 1) / max; n <= len / min; n++) if (n % 2 == 1) return true;
		return false;
	}

	static final List<String> STYLES = List.of("plain", "medieval");

	interface Each { void run(String fixture, String style, long seed, Built b); }

	static void forAll(Each e) {
		for (Map.Entry<String, Massing> f : TestMassings.all().entrySet())
			for (String style : STYLES)
				for (long seed = 0; seed < 6; seed++) e.run(f.getKey(), style, seed, build(f.getValue(), seed, StyleLibrary.get(style)));
	}

	@Test
	void baysSumToTheRunAndStayInRange() {
		forAll((fx, style, seed, b) -> {
			FacadeStyle st = StyleLibrary.get(style).facade();
			for (FacadePlan.Layout l : b.cap().plan.layouts()) {
				int sum = 0;
				for (int w : l.bays()) {
					sum += w;
					assertTrue(w >= st.bayMin() && w <= st.bayMax(), fx + " " + style + " bay " + w);
				}
				assertEquals(l.face().length() - 2, sum, fx + " " + style + " seed " + seed + " " + l.face());
				assertEquals(l.face().a() + 1, l.start());
			}
		});
	}

	@Test
	void symmetricStylesMirrorEveryFace() {
		forAll((fx, style, seed, b) -> {
			if (!StyleLibrary.get(style).facade().symmetric()) return;
			for (FacadePlan.Layout l : b.cap().plan.layouts()) {
				int n = l.bays().length;
				if (!mirrorExists(l.face().length() - 2, 2, 3)) continue; // e.g. 5 = 3 + 2 only
				for (int i = 0; i < n / 2; i++) assertEquals(l.bays()[i], l.bays()[n - 1 - i], fx + " seed " + seed + " " + l.face() + " bays " + java.util.Arrays.toString(l.bays()));
			}
		});
	}

	@Test
	void tileIsSymmetricForAnyLengthWhenAskedAndNeverLeavesTheRange() {
		for (int len = 2; len <= 40; len++)
			for (int target = 2; target <= 4; target++)
				for (int cw = 0; cw < 2; cw++) {
					int[] w = FacadePass.tile(len, target, true, cw == 1, false, 2, 4, false, false, new Draws(len));
					if (w == null) continue;
					int sum = 0;
					for (int x : w) { sum += x; assertTrue(x >= 2 && x <= 4); }
					assertEquals(len, sum);
					boolean mirrored = true;
					for (int i = 0; i < w.length / 2; i++) mirrored &= w[i] == w[w.length - 1 - i];
					// the only unmirrored case is when no mirrored count exists (odd length, even counts only)
					if (!mirrored) assertTrue(len % 2 == 1 && (len + 3) / 4 >= 1, "len " + len);
				}
	}

	@Test
	void sameBandAndExtentSameLayoutDifferentBandsDiffer() {
		forAll((fx, style, seed, b) -> {
			FacadeStyle st = StyleLibrary.get(style).facade();
			var m = b.cap().base.massing();
			for (FacadePlan.Layout l : b.cap().plan.layouts())
				for (FacadePlan.Layout o : b.cap().plan.layouts()) {
					Face f = l.face(), g = o.face();
					if (!f.mass().equals(g.mass()) || f.side() != g.side() || f.a() != g.a() || f.b() != g.b() || f.storey() >= g.storey()) continue;
					if (FacadePass.band(m.mass(f.mass()), f.storey(), st.bandSize()) != FacadePass.band(m.mass(g.mass()), g.storey(), st.bandSize())) continue;
					assertArrayEquals(l.bays(), o.bays(), fx + " " + style + " seed " + seed + ": one band, one rhythm " + f + " " + g);
					assertEquals(l.win(), o.win());
					// door bays aside, the kinds agree too
					for (int i = 0; i < l.bays().length; i++)
						if (f.storey() > 0) assertEquals(l.kinds().get(i), o.kinds().get(i));
				}
		});
	}

	@Test
	void windowsLineUpAcrossAllStoreysOfABand() {
		// cut windows, by (mass, side, band, run), as positions along the face: equal for every storey of the band
		for (String style : STYLES)
			for (long seed = 0; seed < 8; seed++) {
				Built b = build(TestMassings.all().get("setback"), seed, StyleLibrary.get(style));
				FacadeStyle st = StyleLibrary.get(style).facade();
				Map<String, String> seen = new java.util.TreeMap<>();
				for (FacadePlan.Layout l : b.cap().plan.layouts()) {
					Face f = l.face();
					if (!f.mass().equals("shaft1")) continue;
					String key = f.side() + " band " + FacadePass.band(b.cap().base.massing().mass("shaft1"), f.storey(), st.bandSize());
					List<Integer> pos = new ArrayList<>();
					for (int i = 0; i < l.bays().length; i++) if (l.kinds().get(i).equals(FacadePlan.WINDOW)) for (int k = 0; k < l.windowWidth(i); k++) pos.add(l.windowStart(i) + k);
					String v = pos.toString();
					String old = seen.putIfAbsent(key, v);
					if (old != null) assertEquals(old, v, style + " seed " + seed + " " + key);
				}
			}
	}

	@Test
	void tallFacadesChangeRhythmBetweenBands() {
		// setback: shaft1 is 6 storeys (2..7); with bandSize 3 it has 3 bands per side; nearly every side shows >= 2 distinct layouts
		// (not all: 7.12 widened the bay range to 2..4 with 2-wide windows, and different drawn variants can tile a short face the same way)
		int sides = 0, same = 0;
		for (String style : STYLES)
			for (long seed = 0; seed < 12; seed++) {
				Built b = build(TestMassings.all().get("setback"), seed, StyleLibrary.get(style));
				for (var side : dev.fractalbuildings.core.ir.Facing.values()) {
					TreeSet<String> distinct = new TreeSet<>();
					for (FacadePlan.Layout l : b.cap().plan.layouts())
						if (l.face().mass().equals("shaft1") && l.face().side() == side) distinct.add(java.util.Arrays.toString(l.bays()) + l.win());
					sides++;
					if (distinct.size() < 2) same++;
				}
			}
		assertTrue(same * 24 <= sides, same + " of " + sides + " sides show a single layout on every band");
	}

	@Test
	void oneDoorPerEntranceAndNothingBlocksIt() {
		forAll((fx, style, seed, b) -> {
			FacadePlan plan = b.cap().plan;
			int expected = b.cap().base.massing().entrances().size();
			assertEquals(expected, plan.entrances().size());
			long doors = b.ir().openings().stream().filter(o -> o.kind() == OpeningKind.DOOR && o.to().equals(Opening.OUTSIDE)).count();
			assertEquals(expected, doors, fx + " " + style + " seed " + seed);
			long doorBays = plan.layouts().stream().flatMap(l -> l.kinds().stream()).filter(FacadePlan.DOOR::equals).count();
			assertTrue(doorBays <= expected);
			Roles r = new Roles(b.ir());
			for (Opening o : b.ir().openings()) {
				if (!o.to().equals(Opening.OUTSIDE) || (o.kind() != OpeningKind.DOOR && o.kind() != OpeningKind.WINDOW)) continue;
				var x = o.box();
				int dx = o.facing().dx, dz = o.facing().dz;
				for (int i = x.x(); i < x.maxX(); i++)
					for (int k = x.z(); k < x.maxZ(); k++)
						for (int y = x.y(); y < x.maxY(); y++) assertEquals(Role.AIR, r.get(i + dx, y, k + dz), fx + " " + style + " seed " + seed + ": cell in front of the opening");
			}
		});
	}

	@Test
	void decorStaysInsideTheEnvelopeAndNeverCoversAnOpening() {
		forAll((fx, style, seed, b) -> {
			Roles r = new Roles(b.ir());
			for (Box box : b.ir().boxes()) {
				var q = box.box();
				assertTrue(q.x() >= 0 && q.z() >= 0 && q.maxX() <= TestMassings.ENVELOPE && q.maxZ() <= TestMassings.ENVELOPE, fx + " " + style + " " + box);
			}
			for (Opening o : b.ir().openings()) {
				if (o.kind() != OpeningKind.WINDOW && !(o.kind() == OpeningKind.DOOR && o.to().equals(Opening.OUTSIDE))) continue;
				var x = o.box();
				Role want = o.kind() == OpeningKind.WINDOW ? Role.WINDOW : Role.DOOR;
				for (int i = x.x(); i < x.maxX(); i++)
					for (int k = x.z(); k < x.maxZ(); k++)
						for (int y = x.y(); y < x.maxY(); y++) assertEquals(want, r.get(i, y, k), fx + " " + style + " seed " + seed + " opening " + o.id());
			}
		});
	}

	@Test
	void plainHasNoDecorAndMedievalDoes() {
		for (Massing m : TestMassings.all().values()) {
			BuildingIr plain = TestMassings.shell(m, 1, "plain");
			assertFalse(plain.boxes().stream().anyMatch(b -> b.layer() != dev.fractalbuildings.core.ir.Layer.INTERIOR && (b.role() == Role.TRIM || b.role() == Role.BEAM || b.role() == Role.FOUNDATION)), "plain: all facade decor off (an interior archway lintel is no facade decor)");
		}
		BuildingIr med = TestMassings.shell(TestMassings.all().get("jetty"), 1, "medieval");
		for (Role role : List.of(Role.FOUNDATION, Role.TRIM, Role.BEAM)) assertTrue(med.boxes().stream().anyMatch(b -> b.role() == role), "medieval has " + role);
		long sills = med.boxes().stream().filter(b -> b.role() == Role.TRIM && b.box().sx() * b.box().sz() == 1 && b.box().sy() == 1).count();
		assertTrue(sills > 0);
	}

	@Test
	void doubleDoorsWhereTheStyleAsksAndTheCellsAreFree() {
		StyleRules s = StyleLibrary.get("medieval");
		FacadeStyle f = s.facade();
		StyleRules wide = withFacade(s, new FacadeStyle(3, 4, f.sill(), f.windowHeight(), 2, true, f.bandSize(), false, false, 2, true, f.plinth(), f.sills(), f.lintels(),
				f.shutters(), f.stringCourse(), f.cornice(), f.bressumer(), f.corners(), f.timber(), f.unglazed(), f.tallGlass()));
		int wideDoors = 0;
		for (Massing m : TestMassings.all().values())
			for (long seed = 0; seed < 8; seed++) {
				Built b = build(m, seed, wide);
				for (Opening o : b.ir().openings())
					if (o.kind() == OpeningKind.DOOR && o.to().equals(Opening.OUTSIDE) && (o.box().sx() == 2 || o.box().sz() == 2)) wideDoors++;
			}
		assertTrue(wideDoors > 0, "some double doors");
	}

	@Test
	void decoratedStylesPassTheInvariantsOnEveryFixture() {
		// FixturePipelineTest covers plain and medieval; this adds a style with every decor kind on and corner quoins
		StyleRules s = StyleLibrary.get("medieval");
		FacadeStyle f = s.facade();
		StyleRules all = withFacade(s, new FacadeStyle(2, 4, 1, 2, 2, true, 2, true, true, 2, true, true, true, true, true, true, true, true, "quoins", "all", false, false));
		for (var e : TestMassings.all().entrySet())
			for (long seed = 0; seed < 5; seed++) {
				BuildingIr ir = ShellGeneratorV1.generate(TestMassings.spec(seed, "medieval"), new TestMassings.FixtureType(e.getValue()), all);
				assertNotNull(ir);
				var scene = scene(ir, seed);
				var v = Invariants.check(scene);
				assertTrue(v.isEmpty(), e.getKey() + " seed " + seed + ": " + (v.isEmpty() ? "" : v.get(0).invariant() + " " + v.get(0).message() + " " + v.get(0).cells()));
			}
	}

	static dev.fractalbuildings.core.ir.SceneDoc scene(BuildingIr shell, long seed) {
		var t = dev.fractalbuildings.core.probe.SyntheticTerrain.plane(64, 1, 1, 3);
		var lot = new dev.fractalbuildings.core.ir.Lot("lot0", 0, 0, TestMassings.ENVELOPE + 2, TestMassings.ENVELOPE + 2, dev.fractalbuildings.core.ir.Facing.NORTH);
		var req = new dev.fractalbuildings.core.gen.GenRequest(seed, lot, "fixture", "medieval", t, (x, z) -> "minecraft:plains");
		var b = InstancePassV1.place(shell, req, TestMassings.FixtureType.LIMITS);
		var patch = dev.fractalbuildings.core.ir.TerrainPatch.sample(t, lot.x() - 2, lot.z() - 2, lot.w() + 4, lot.d() + 4);
		return new dev.fractalbuildings.core.ir.SceneDoc(GeneratorV1.GEN_VERSION, seed, patch, List.of(lot), List.of(b), List.of());
	}

	/** Final role per local cell of a shell (boxes applied in order). */
	static final class Roles {
		final Role[][][] g;

		Roles(BuildingIr ir) {
			int w = ir.bounds().maxX(), d = ir.bounds().maxZ(), h = ir.bounds().maxY();
			g = new Role[w][h][d];
			for (Role[][] a : g) for (Role[] b : a) java.util.Arrays.fill(b, Role.AIR);
			for (Box b : ir.boxes()) {
				var q = b.box();
				for (int x = q.x(); x < q.maxX(); x++) for (int y = q.y(); y < q.maxY(); y++) for (int z = q.z(); z < q.maxZ(); z++) g[x][y][z] = b.role();
			}
		}

		Role get(int x, int y, int z) { return x < 0 || y < 0 || z < 0 || x >= g.length || y >= g[0].length || z >= g[0][0].length ? Role.AIR : g[x][y][z]; }
	}
}
