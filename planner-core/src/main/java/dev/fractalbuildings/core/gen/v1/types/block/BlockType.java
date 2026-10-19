package dev.fractalbuildings.core.gen.v1.types.block;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.Program;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import java.util.List;

/**
 * A city block (step 4.6 T-block): the lot is a whole street block, filled by a perimeter ring of
 * joined row units around a courtyard ({@link BlockMassing}). Every unit has its own street door
 * ({@link BlockFacade}) and, above one storey, its own stair core.
 */
public final class BlockType implements BuildingType {
	public static final String ID = "block";

	// margin 5 on every side: front steps (plan step S) wind along the margin down a slope, past the other doors
	private static final TypeLimits LIMITS = new TypeLimits(42, 42, 2, 5, 5, 5, 5, true,
			List.of(new int[] {52, 52}, new int[] {64, 56}, new int[] {100, 80}, new int[] {52, 70}), 60, 16);

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }

	@Override
	public Massing mass(BuildContext c) { return BlockMassing.build(c, LIMITS.maxStoreys()); }

	@Override
	public Program facade() { return BlockFacade::run; }
}
