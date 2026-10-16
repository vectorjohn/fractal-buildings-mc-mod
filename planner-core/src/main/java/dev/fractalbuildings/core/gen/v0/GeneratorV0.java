package dev.fractalbuildings.core.gen.v0;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.Generator;
import dev.fractalbuildings.core.gen.ShellSource;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.TerrainPatch;
import java.util.List;

/** SpecPolicyV0, then ShellGeneratorV0 (through a {@link ShellSource}), then InstancePassV0. */
public final class GeneratorV0 implements Generator {
	/** Part of every cache key; bump when generated output changes on purpose. */
	public static final int GEN_VERSION = 1;

	private final ShellSource shells;

	public GeneratorV0() { this(ShellGeneratorV0::generate); }

	public GeneratorV0(ShellSource shells) { this.shells = shells; }

	@Override
	public SceneDoc generate(GenRequest req) {
		BuildingSpec spec = SpecPolicyV0.spec(req);
		PlacedBuilding b = InstancePassV0.place(shells.shell(spec), req);
		var lot = req.lot();
		TerrainPatch terrain = TerrainPatch.sample(req.terrain(), lot.x() - 2, lot.z() - 2, lot.w() + 4, lot.d() + 4);
		return new SceneDoc(GEN_VERSION, req.worldSeed(), terrain, List.of(lot), List.of(b), List.of());
	}
}
