package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.Generator;
import dev.fractalbuildings.core.gen.ShellSource;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.TerrainPatch;
import java.util.List;

/** Generator v1 (Phase 4): SpecPolicyV1, then ShellGeneratorV1 (through a {@link ShellSource}), then InstancePassV1. */
public final class GeneratorV1 implements Generator {
	/**
	 * Part of every cache key. v0 is 1; v1 starts at 2. During Phase 4 it stays 2 while the passes
	 * change (no IR is cached outside a process yet; the golden digests catch unintended changes);
	 * from Phase 6 on it is bumped with every intended output change (plan "Golden digests").
	 */
	public static final int GEN_VERSION = 2;

	private final ShellSource shells;

	public GeneratorV1() { this(ShellGeneratorV1::generate); }

	public GeneratorV1(ShellSource shells) { this.shells = shells; }

	@Override
	public SceneDoc generate(GenRequest req) {
		BuildingSpec spec = SpecPolicyV1.spec(req);
		PlacedBuilding b = InstancePassV1.place(shells.shell(spec), req);
		var lot = req.lot();
		TerrainPatch terrain = TerrainPatch.sample(req.terrain(), lot.x() - 2, lot.z() - 2, lot.w() + 4, lot.d() + 4);
		return new SceneDoc(GEN_VERSION, req.worldSeed(), terrain, List.of(lot), List.of(b), List.of());
	}
}
