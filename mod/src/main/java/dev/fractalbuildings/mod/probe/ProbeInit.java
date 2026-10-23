package dev.fractalbuildings.mod.probe;

import dev.fractalbuildings.mod.FractalBuildingsMod;

/** Registration point for the live probes (A1). Probes are built per world by the worldgen step (B1); nothing to hook yet. */
public final class ProbeInit {
	private ProbeInit() {}

	public static void register() {
		FractalBuildingsMod.LOGGER.info("Live probes ready (exact heights via ChunkFill, memo of {} chunks)", LiveProbes.DEFAULT_CHUNKS);
	}
}
