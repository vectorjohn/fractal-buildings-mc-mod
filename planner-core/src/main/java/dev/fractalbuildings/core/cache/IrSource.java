package dev.fractalbuildings.core.cache;

import dev.fractalbuildings.core.gen.ShellSource;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;

/** Source of shells. Contract: {@code get(spec)} returns an equal shell whether or not it was cached or prefetched. */
public interface IrSource extends ShellSource {
	BuildingIr get(BuildingSpec spec);

	/** Hint that {@code spec} will be asked for soon. Never changes what {@link #get} returns. */
	default void prefetch(BuildingSpec spec) {}

	@Override
	default BuildingIr shell(BuildingSpec spec) { return get(spec); }
}
