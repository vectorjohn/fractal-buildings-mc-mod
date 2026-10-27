package dev.fractalbuildings.gametest.compat;

import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.util.Set;

/**
 * TESTLAYERS: {@code FB_LAYER=quick|full} picks how much of the client suite runs (unset means full when Gradle is run directly;
 * {@code scripts/client-test.sh} and {@code scripts/test-all.sh} default it to quick). Quick: Hello, District (B1), Gameplay (B5), Terrain (B3),
 * TeleportHold. Full adds Roads, Foliage and the compat world ({@link #FULL_ONLY}). The fly-through and perf tests stay opt-in
 * ({@code FB_FLY}, {@code FB_PERF}) in both layers. The district-order test (B2) is no client test any more: {@code scripts/order-test.sh}.
 */
public final class ClientLayers {
	private ClientLayers() {}

	/** The client tests the quick layer leaves out. */
	public static final Set<String> FULL_ONLY = Set.of("RoadsClientGameTest", "FoliageClientGameTest", "CompatClientGameTest");

	/** The layer in force: {@code quick} or {@code full}. */
	public static String layer() {
		String l = System.getenv("FB_LAYER");
		if (l == null || l.isEmpty() || l.equals("full")) return "full";
		if (l.equals("quick")) return "quick";
		throw new IllegalStateException("FB_LAYER must be quick or full, got '" + l + "'");
	}

	/** True (and logs it) when the quick layer leaves {@code test} out; FB_ONLY=<test> overrides the layer (naming a test runs it). */
	public static boolean skip(String test) {
		String only = System.getenv("FB_ONLY");
		if (only != null && !only.isEmpty()) return false; // CompatOnly.skip decides by name
		if (layer().equals("quick") && FULL_ONLY.contains(test)) {
			FractalBuildingsMod.LOGGER.info("[layers] {} skipped (FB_LAYER=quick)", test);
			return true;
		}
		return false;
	}

	/** For the compat world test, which does not go through {@link CompatOnly#skip}: layer and FB_ONLY, but never in a compat-only (matrix) run. */
	public static boolean skipCompat() {
		if (CompatOnly.on()) return false;
		String only = System.getenv("FB_ONLY");
		if (only != null && !only.isEmpty() && !only.equals("CompatClientGameTest")) {
			FractalBuildingsMod.LOGGER.info("[layers] CompatClientGameTest skipped (FB_ONLY={})", only);
			return true;
		}
		return skip("CompatClientGameTest");
	}
}
