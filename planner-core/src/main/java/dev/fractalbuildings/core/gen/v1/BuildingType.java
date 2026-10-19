package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.gen.v1.facade.FacadePass;
import dev.fractalbuildings.core.gen.v1.facade.OpeningPass;
import dev.fractalbuildings.core.gen.v1.interior.InteriorPass;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.roof.RoofPass;
import dev.fractalbuildings.core.gen.v1.structure.StructurePass;

/**
 * A building type (house, shop, tower, high-rise...): the extension point of 4.6. A type owns its
 * massing program and may replace any later pass with its own {@link Program} (usually a wrapper
 * that calls the shared pass with different parameters, then adds its own boxes). Registered in
 * {@link Types}. One type per package under {@code gen/v1/types/}.
 */
public interface BuildingType {
	/** The spec's {@code type} string. */
	String id();

	/** Lot fit, storey range and test matrix for the policy and the property tests. */
	TypeLimits limits();

	/** Massing inside the envelope; must pass {@code MassingCheck}. Reads spec, style and draws only. */
	Massing mass(BuildContext c);

	default Program structure() { return StructurePass::run; }

	default Program roofs() { return RoofPass::run; }

	/** Must call {@link BuildContext#setFacadePlan}. */
	default Program facade() { return FacadePass::run; }

	default Program interior() { return InteriorPass::run; }

	default Program openings() { return OpeningPass::run; }

	/** Room uses and furnishing hooks for the interior pass (4.5). */
	default RoomProgram rooms() { return RoomProgram.DEFAULT; }
}
