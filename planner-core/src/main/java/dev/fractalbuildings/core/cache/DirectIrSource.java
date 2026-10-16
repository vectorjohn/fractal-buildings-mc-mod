package dev.fractalbuildings.core.cache;

import dev.fractalbuildings.core.gen.ShellSource;
import dev.fractalbuildings.core.gen.v0.ShellGeneratorV0;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;

/** No cache: builds the shell on every call. The reference the cached variants must match. */
public final class DirectIrSource implements IrSource {
	private final ShellSource builder;

	public DirectIrSource() { this(ShellGeneratorV0::generate); }

	public DirectIrSource(ShellSource builder) { this.builder = builder; }

	@Override
	public BuildingIr get(BuildingSpec spec) { return builder.shell(spec); }
}
