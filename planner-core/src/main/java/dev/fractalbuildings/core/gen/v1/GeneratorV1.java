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
		TypeLimits lim = Types.get(req.type()).limits();
		BuildingSpec spec = SpecPolicyV1.spec(req, lim);
		InstancePassV1.Placed placed = InstancePassV1.placeChecked(shells.shell(spec), req, lim);
		// DS-8: a flight or landing that needs a rail on the lot line cannot have one (a rail stays in the lot), so set the building back
		// one cell from the front first, then from every side, and keep the first placement whose flights are all railed
		if (!placed.guarded() && !NO_SETBACK.contains(req.type())) {
			for (int step = 0; step < SETBACKS.length && !placed.guarded(); step++) {
				int f = SETBACKS[step][0], s = SETBACKS[step][1];
				TypeLimits back = new TypeLimits(lim.minWidth(), lim.minDepth(), lim.minStoreys(), lim.maxStoreys(), lim.side() + s, lim.front() + f, lim.back() + s, lim.allStreets(), lim.testLots(), lim.propertySeeds(), lim.maxGrade(), lim.stoop());
				try {
					BuildingSpec s2 = SpecPolicyV1.spec(req, back);
					InstancePassV1.Placed p2 = InstancePassV1.placeChecked(shells.shell(s2), req, back);
					if (p2.guarded()) { placed = p2; spec = s2; lim = back; }
				} catch (IllegalArgumentException e) { /* no room for a setback: keep the placement as it was */ }
			}
		}
		PlacedBuilding b = placed.building();
		var lot = req.lot();
		TerrainPatch terrain = TerrainPatch.sample(req.terrain(), lot.x() - 2, lot.z() - 2, lot.w() + 4, lot.d() + 4);
		return new SceneDoc(GEN_VERSION, req.worldSeed(), terrain, List.of(spec.lead() > 0 ? lot.withAir(spec.lead() - lim.front(), req.brief().frontY()) : lot), List.of(b), List.of());
	}

	/** Extra margin tried in turn: {front, sides and back}. */
	private static final int[][] SETBACKS = {{1, 0}, {1, 1}, {2, 1}, {2, 2}};

	/** Town-wall types stand on the ring's walk and keep their margins. */
	private static final java.util.Set<String> NO_SETBACK = java.util.Set.of("wall", "curtain", "bastion");
}
