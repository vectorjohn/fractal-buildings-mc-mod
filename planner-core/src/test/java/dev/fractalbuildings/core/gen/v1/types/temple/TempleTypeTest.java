package dev.fractalbuildings.core.gen.v1.types.temple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.Program;
import dev.fractalbuildings.core.gen.v1.ShellGeneratorV1;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.facade.FacadePass;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Hook;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.rand.Draws;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Temple properties: door on the nave's axis, altar in the back third, nave size, tower height, diversity. */
class TempleTypeTest {
	static final int SEEDS = 120;
	static final int[][] ENVELOPES = {{13, 22}, {17, 28}, {23, 36}, {29, 42}};

	/** A temple type with the facade or the room program swapped, to show a property can fail. */
	record Variant(BuildingType base, Program facade, RoomProgram rooms) implements BuildingType {
		@Override public String id() { return base.id(); }
		@Override public TypeLimits limits() { return base.limits(); }
		@Override public Massing mass(BuildContext c) { return base.mass(c); }
		@Override public Program facade() { return facade != null ? facade : base.facade(); }
		@Override public RoomProgram rooms() { return rooms != null ? rooms : base.rooms(); }
	}

	static BuildingSpec spec(long seed, int w, int d, int storeys, int maxHeight) {
		return new BuildingSpec(2, seed, seed % 2 == 0 ? "plain" : "medieval", "temple", w, d, storeys, "auto", maxHeight, 1);
	}

	static BuildingIr gen(BuildingType t, long seed, int[] e, int maxHeight) {
		return ShellGeneratorV1.generate(spec(seed, e[0], e[1], 2 + (int) (seed % 2), maxHeight), t, StyleLibrary.get(seed % 2 == 0 ? "plain" : "medieval"));
	}

	static Volume vol(BuildingIr ir, String prefix) {
		for (Volume v : ir.volumes()) if (v.id().startsWith(prefix)) return v;
		return null;
	}

	/** The main entrance: the storey-0 door to the outside on the north face. */
	static Opening entrance(BuildingIr ir) {
		Opening found = null;
		for (Opening o : ir.openings()) {
			if (o.kind() == OpeningKind.DOOR && (o.to().equals(Opening.OUTSIDE) || o.from().equals(Opening.OUTSIDE))) {
				if (found != null) continue; // the first is the main entrance; the side doors (DS35DOORS) come after it
				found = o;
			}
		}
		return found;
	}

	/** Cases where property {@code p} fails (their descriptions). */
	interface Prop { String check(BuildingIr ir); }

	static List<String> failures(BuildingType t, Prop p) {
		List<String> bad = new ArrayList<>();
		for (int[] e : ENVELOPES)
			for (long seed = 0; seed < SEEDS; seed++) {
				BuildingIr ir = gen(t, seed, e, 300);
				String m = p.check(ir);
				if (m != null) bad.add(e[0] + "x" + e[1] + " seed " + seed + ": " + m);
			}
		return bad;
	}

	static final Prop DOOR_ON_AXIS = ir -> {
		Opening d = entrance(ir);
		Volume nave = vol(ir, "nave");
		Volume front = ir.volumes().get(0);
		int axis = nave.box().x() + nave.box().sx() / 2;
		if (d == null) return "no entrance";
		if (d.box().x() != axis) return "door x " + d.box().x() + " but axis " + axis;
		if (d.box().z() != front.box().z()) return "door z " + d.box().z() + " not on the front face " + front.box().z();
		return null;
	};

	static final Prop ALTAR_IN_BACK_THIRD = ir -> {
		int z0 = Integer.MAX_VALUE, z1 = 0;
		for (Volume v : ir.volumes()) {
			z0 = Math.min(z0, v.box().z());
			z1 = Math.max(z1, v.box().maxZ());
		}
		int need = z0 + (2 * (z1 - z0) + 2) / 3;
		int altars = 0;
		for (Hook h : ir.hooks()) {
			if (!h.kind().equals("altar")) continue;
			altars++;
			if (h.z() < need) return "altar z " + h.z() + " before the back third (from " + need + ")";
		}
		return altars == 1 ? null : altars + " altars";
	};

	@Test
	void doorIsOnTheFrontCentreAxis() {
		assertEquals(List.of(), failures(new TempleType(), DOOR_ON_AXIS));
	}

	@Test
	void doorMutantFails() {
		// the shared facade picks any bay of the front face
		List<String> bad = failures(new Variant(new TempleType(), FacadePass::run, null), DOOR_ON_AXIS);
		assertTrue(bad.size() > 50, "the default facade should miss the axis often, missed " + bad.size());
	}

	@Test
	void altarIsInTheBackThird() {
		assertEquals(List.of(), failures(new TempleType(), ALTAR_IN_BACK_THIRD));
	}

	@Test
	void altarMutantFails() {
		// the same room program, but the nave is the sanctuary: altars appear in the nave and the apse
		RoomProgram bad = new RoomProgram() {
			final RoomProgram p = TempleRooms.PROGRAM;
			@Override public String use(Role role, int area, Draws d) { return p.use(role, area, d); }
			@Override public String use(Role role, String kind, int area, Where where, Draws d) { return p.use(role, kind.equals("nave") ? "apse" : kind, area, where, d); }
			@Override public List<String> hooks(String use, int area, Draws d) { return p.hooks(use, area, d); }
		};
		assertFalse(failures(new Variant(new TempleType(), null, bad), ALTAR_IN_BACK_THIRD).isEmpty());
	}

	@Test
	void naveIsAnOpenHallOnTheAxisWithApseAndFrontBlockCentred() {
		for (int[] e : ENVELOPES)
			for (long seed = 0; seed < SEEDS; seed++) {
				BuildingIr ir = gen(new TempleType(), seed, e, 300);
				Volume nave = vol(ir, "nave"), apse = vol(ir, "apse"), front = ir.volumes().get(0);
				String at = e[0] + "x" + e[1] + " seed " + seed;
				assertTrue(nave.box().sx() - 2 >= 7, at + ": nave interior width " + (nave.box().sx() - 2));
				assertTrue(nave.box().sx() % 2 == 1 && apse.box().sx() % 2 == 1 && front.box().sx() % 2 == 1, at + ": odd widths");
				int axis = nave.box().x() + nave.box().sx() / 2;
				assertEquals(axis, apse.box().x() + apse.box().sx() / 2, at + ": apse on the axis");
				assertEquals(axis, front.box().x() + front.box().sx() / 2, at + ": front block on the axis");
				assertTrue(apse.box().sx() < nave.box().sx(), at + ": apse narrower than the nave");
				assertTrue(nave.box().z() > front.box().z() && apse.box().z() > nave.box().z(), at + ": front, nave, apse in order");
				// one room spanning the nave's whole height (open mass)
				long rooms = ir.rooms().stream().filter(r -> r.volume().equals(nave.id())).count();
				assertEquals(1, rooms, at + ": the nave is one room");
				assertTrue(nave.box().sy() >= 8, at + ": nave height " + nave.box().sy());
			}
	}

	@Test
	void towerRisesAboveTheNaveAndHasACore() {
		int towers = 0;
		for (int[] e : ENVELOPES)
			for (long seed = 0; seed < SEEDS; seed++) {
				BuildingIr ir = gen(new TempleType(), seed, e, 300);
				Volume tower = vol(ir, "tower"), nave = vol(ir, "nave");
				if (tower == null) continue;
				towers++;
				assertTrue(tower.box().maxY() > nave.box().maxY() + 3, e[0] + "x" + e[1] + " seed " + seed + ": tower top " + tower.box().maxY() + " nave top " + nave.box().maxY());
				assertTrue(ir.openings().stream().anyMatch(o -> o.kind() == OpeningKind.STAIR), "a tower has stairs");
			}
		assertTrue(towers >= 20, "towers occur: " + towers);
	}

	@Test
	void massingPassesItsContractUnderAnyHeightCap() {
		for (int cap = 6; cap <= 40; cap++)
			for (int[] e : ENVELOPES)
				for (long seed = 0; seed < 8; seed++) {
					BuildingSpec sp = spec(seed, e[0], e[1], 3, cap);
					BuildContext c = new BuildContext(sp, new TempleType(), StyleLibrary.get("plain"));
					Massing m = new TempleType().mass(c);
					assertEquals(List.of(), MassingCheck.check(m, e[0], e[1], cap), "cap " + cap + " " + e[0] + "x" + e[1] + " seed " + seed);
				}
	}

	@Test
	void shapesVary() {
		int n = 0, towers = 0, chapels = 0, lowApse = 0;
		TreeSet<Integer> widths = new TreeSet<>(), heights = new TreeSet<>(), naveDepths = new TreeSet<>();
		for (long seed = 0; seed < 256; seed++) {
			BuildingIr ir = gen(new TempleType(), 1000 + seed, ENVELOPES[(int) (seed % 4)], 300);
			n++;
			Volume nave = vol(ir, "nave"), apse = vol(ir, "apse");
			if (vol(ir, "tower") != null) towers++;
			if (vol(ir, "transept1") != null) chapels++;
			if (apse.box().maxY() < nave.box().maxY()) lowApse++;
			widths.add(nave.box().sx());
			heights.add(nave.box().sy());
			naveDepths.add(nave.box().sz());
		}
		System.out.println("temples " + n + " towers " + towers + " chapels " + chapels + " lowApse " + lowApse + " widths " + widths + " heights " + heights + " depths " + naveDepths.size());
		assertTrue(towers >= 43, "towers " + towers);
		assertTrue(chapels >= 64, "chapels " + chapels);
		assertTrue(lowApse >= 126, "stepped apse " + lowApse);
		assertTrue(widths.size() >= 4 && heights.size() >= 3 && naveDepths.size() >= 13, "dimension variety");
	}
}
