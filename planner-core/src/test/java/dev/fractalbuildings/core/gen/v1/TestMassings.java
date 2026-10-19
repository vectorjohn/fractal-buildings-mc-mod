package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.model.Core;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.TerrainPatch;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-made massings covering every shape the massing contract allows (plan "Test fixtures"): roofs,
 * facades and interiors are developed and property-tested against these in parallel with the massing
 * step. {@link #shell} and {@link #scene} run one through the whole v1 pipeline with the current
 * passes. Envelope 32 x 32 unless noted; flat ground.
 */
public final class TestMassings {
	private TestMassings() {}

	public static final int ENVELOPE = 32;

	static Mass g(String id, String kind, int x, int z, int sx, int sz, int storeys) { return Mass.ground(id, kind, x, z, sx, sz, storeys); }

	static Mass up(String id, String kind, int x, int z, int sx, int sz, int from, int to, String parent) {
		return new Mass(id, kind, x, z, sx, sz, from, to, parent, false);
	}

	static StoreyGrid grid(int n) { return StoreyGrid.of(n, 4, 4); }

	/** All fixtures by name, in a fixed order. */
	public static Map<String, Massing> all() {
		Map<String, Massing> m = new LinkedHashMap<>();
		m.put("box1", new Massing(grid(1), List.of(g("main0", "main", 4, 4, 9, 7, 1)), List.of()));
		m.put("box3", new Massing(grid(3), List.of(g("main0", "main", 4, 4, 9, 7, 3)), List.of(new Core(5, 8, 5, 2, 0, 3))));
		m.put("ground5", new Massing(StoreyGrid.of(2, 5, 4), List.of(g("main0", "main", 4, 4, 10, 8, 2)), List.of(new Core(5, 9, 6, 2, 0, 2))));
		m.put("L", new Massing(grid(2), List.of(
				g("main0", "main", 2, 2, 12, 8, 2),
				g("wing1", "wing", 2, 9, 6, 9, 1)), List.of(new Core(8, 7, 5, 2, 0, 2))));
		m.put("T", new Massing(grid(2), List.of(
				g("main0", "main", 2, 2, 18, 7, 2),
				g("wing1", "wing", 8, 8, 6, 10, 2)), List.of(new Core(3, 6, 5, 2, 0, 2))));
		m.put("U", new Massing(grid(1), List.of(
				g("main0", "main", 2, 2, 18, 6, 1),
				g("wing1", "wing", 2, 7, 6, 11, 1),
				g("wing2", "wing", 14, 7, 6, 11, 1)), List.of()));
		m.put("courtyard", new Massing(grid(2), List.of(
				g("main0", "main", 2, 2, 20, 6, 2),
				g("wing1", "wing", 2, 7, 6, 15, 2),
				g("wing2", "wing", 16, 7, 6, 15, 2),
				g("wing3", "wing", 7, 16, 10, 6, 2)), List.of(new Core(9, 5, 5, 2, 0, 2))));
		m.put("jetty", new Massing(grid(3), List.of(
				g("main0", "main", 4, 4, 9, 7, 1),
				up("jetty1", "jetty", 3, 3, 11, 9, 1, 3, "main0")), List.of(new Core(5, 8, 5, 2, 0, 3))));
		m.put("setback", new Massing(grid(10), List.of(
				g("podium0", "podium", 2, 2, 20, 20, 2),
				up("shaft1", "shaft", 6, 6, 12, 12, 2, 8, "podium0"),
				up("crown2", "crown", 8, 8, 8, 8, 8, 10, "shaft1")), List.of(new Core(9, 10, 5, 2, 0, 10))));
		m.put("hall", new Massing(grid(2), List.of(
				new Mass("hall0", "hall", 2, 2, 16, 12, 0, 2, null, true),
				g("annex1", "annex", 17, 4, 9, 8, 2)), List.of(new Core(18, 9, 5, 2, 0, 2))));
		m.put("tallwing", new Massing(grid(3), List.of(
				g("main0", "main", 2, 2, 10, 7, 1),
				g("tower1", "tower", 11, 2, 7, 7, 3)), List.of(new Core(12, 6, 5, 2, 0, 3))));
		m.put("row", new Massing(grid(2), List.of(
				g("main0", "unit", 2, 2, 8, 8, 2),
				g("unit1", "unit", 9, 2, 8, 8, 2),
				g("unit2", "unit", 16, 2, 8, 8, 2)),
				List.of(new Core(3, 7, 5, 2, 0, 2), new Core(10, 7, 5, 2, 0, 2), new Core(17, 7, 5, 2, 0, 2)),
				List.of("main0", "unit1", "unit2"),
				List.of(new Massing.Party("main0", "unit1"), new Massing.Party("unit1", "unit2"))));
		// appended by the massing step (4.2): storeys that differ both ways, wings of other heights
		m.put("jettyStep", new Massing(grid(3), List.of(
				g("main0", "main", 5, 5, 9, 7, 1),
				up("jetty1", "jetty", 4, 4, 11, 9, 1, 2, "main0"),
				up("jetty2", "jetty", 3, 3, 13, 11, 2, 3, "jetty1")), List.of(new Core(6, 9, 5, 2, 0, 3))));
		m.put("setbackWing", new Massing(grid(3), List.of(
				g("main0", "main", 3, 3, 12, 8, 2),
				g("wing1", "wing", 3, 10, 6, 8, 1),
				up("setback2", "setback", 4, 4, 10, 6, 2, 3, "main0")), List.of(new Core(5, 7, 5, 2, 0, 3))));
		m.put("tallWingCore", new Massing(grid(3), List.of(
				g("main0", "main", 2, 2, 10, 7, 2),
				g("wing1", "wing", 11, 3, 8, 6, 3)), List.of(new Core(3, 6, 5, 2, 0, 2), new Core(12, 6, 5, 2, 1, 3))));
		m.put("tall50", new Massing(grid(50), List.of(g("podium0", "podium", 2, 2, 28, 28, 50)), List.of(new Core(12, 14, 5, 2, 0, 50))));
		return m;
	}

	/** A type that returns a fixed massing (id "fixture"). */
	public record FixtureType(Massing massing) implements BuildingType {
		public static final TypeLimits LIMITS = new TypeLimits(5, 5, 1, 99, 1, 1, 1, false, List.of(), 1, 1);

		@Override public String id() { return "fixture"; }

		@Override public TypeLimits limits() { return LIMITS; }

		@Override public Massing mass(BuildContext c) { return massing; }
	}

	public static BuildingSpec spec(long seed, String style) {
		return new BuildingSpec(GeneratorV1.GEN_VERSION, seed, style, "fixture", ENVELOPE, ENVELOPE, 1, "auto", 384, 1);
	}

	public static BuildingIr shell(Massing m, long seed, String style) { return ShellGeneratorV1.generate(spec(seed, style), new FixtureType(m)); }

	/** The fixture placed on a lot of envelope + 1 margin at (0, 0), front north, on the given ground. */
	public static SceneDoc scene(Massing m, long seed, String style, TerrainProbe t) {
		BuildingIr shell = shell(m, seed, style);
		Lot lot = new Lot("lot0", 0, 0, ENVELOPE + 2, ENVELOPE + 2, Facing.NORTH);
		GenRequest req = new GenRequest(seed, lot, "fixture", style, t, (x, z) -> "minecraft:plains");
		PlacedBuilding b = InstancePassV1.place(shell, req, FixtureType.LIMITS);
		TerrainPatch patch = TerrainPatch.sample(t, lot.x() - 2, lot.z() - 2, lot.w() + 4, lot.d() + 4);
		return new SceneDoc(GeneratorV1.GEN_VERSION, seed, patch, List.of(lot), List.of(b), List.of());
	}
}
