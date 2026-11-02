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
		if (ClientLayers.skip(test)) return true; // TESTLAYERS: FB_LAYER=quick leaves the slow tests out
		// B9: FB_FLY=only runs the fly-through alone; every other client test steps aside
		// run just the named client tests, e.g. FB_ONLY=RoadsClientGameTest or FB_ONLY=A,B; an unknown name throws (RUNGUARD)
		java.util.Set<String> only = ClientLayers.only();
		if (!only.isEmpty() && !only.contains(test)) {
			FractalBuildingsMod.LOGGER.info("[B9] {} skipped (FB_ONLY={})", test, String.join(",", only));
			return true;
		}
		if (!on()) {
			if (!test.equals("FlyThroughClientGameTest") && dev.fractalbuildings.gametest.flythrough.FlyThroughClientGameTest.skipOthers()) {
				FractalBuildingsMod.LOGGER.info("[B9] {} skipped (FB_FLY=only)", test);
				return true;
			}
			return false;
		}
		FractalBuildingsMod.LOGGER.info("[B7] {} skipped (compatOnly)", test);
		return true;
	}
}
