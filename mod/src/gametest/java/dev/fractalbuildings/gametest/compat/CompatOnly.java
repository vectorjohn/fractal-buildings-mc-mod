package dev.fractalbuildings.gametest.compat;

import dev.fractalbuildings.mod.FractalBuildingsMod;

/**
 * The matrix script runs only {@code CompatClientGameTest} ({@code -Pfb.compatOnly=true}); every other client test calls
 * {@link #skip} first and returns when it says so. Only the value {@code true} switches it on.
 */
public final class CompatOnly {
	private CompatOnly() {}

	public static boolean on() { return Boolean.getBoolean("fb.compatOnly"); }

	/** True (and logs it) when this test is to be skipped. */
	public static boolean skip(String test) {
		if (!on()) return false;
		FractalBuildingsMod.LOGGER.info("[B7] {} skipped (compatOnly)", test);
		return true;
	}
}
