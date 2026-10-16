package dev.fractalbuildings.core.gen;

import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;

/** Produces the shell for a spec. A cache can sit behind this interface. */
@FunctionalInterface
public interface ShellSource {
	BuildingIr shell(BuildingSpec spec);
}
