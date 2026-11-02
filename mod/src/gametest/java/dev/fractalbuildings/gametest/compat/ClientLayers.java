package dev.fractalbuildings.gametest.compat;

import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;

/**
 * TESTLAYERS: {@code FB_LAYER=quick|full} picks how much of the client suite runs (unset means full when Gradle is run directly;
 * {@code scripts/client-test.sh} and {@code scripts/test-all.sh} default it to quick). Quick: Hello, District (B1), Gameplay (B5), Terrain (B3),
 * TeleportHold. Full adds Roads, Foliage, PortalHold (6.30) and the compat world ({@link #FULL_ONLY}). The fly-through and perf tests stay opt-in
 * ({@code FB_FLY}, {@code FB_PERF}) in both layers. The district-order test (B2) is no client test any more: {@code scripts/order-test.sh}.
 *
 * <p>{@code FB_ONLY=A[,B...]} runs only the named client test classes, whatever the layer. RUNGUARD: a name that is not a registered client
 * test fails the run ({@link #only}) instead of skipping every test and passing; {@code scripts/client-test.sh} makes the same check first and
 * exits 2 before the game starts.
 */
public final class ClientLayers {
	private ClientLayers() {}

	/** The client tests the quick layer leaves out. */
	public static final Set<String> FULL_ONLY = Set.of("RoadsClientGameTest", "FoliageClientGameTest", "CompatClientGameTest", "PortalHoldClientGameTest");

	/** The layer in force: {@code quick} or {@code full}. */
	public static String layer() {
		String l = System.getenv("FB_LAYER");
		if (l == null || l.isEmpty() || l.equals("full")) return "full";
		if (l.equals("quick")) return "quick";
		throw new IllegalStateException("FB_LAYER must be quick or full, got '" + l + "'");
	}

	/**
	 * The client test classes named in {@code FB_ONLY} (comma-separated simple class names), empty when it is unset. Throws when a name is not
	 * a registered client test (a {@code fabric-client-gametest} entrypoint), so a typo fails the run instead of silently running nothing.
	 */
	public static Set<String> only() {
		String raw = System.getenv("FB_ONLY");
		Set<String> names = new TreeSet<>();
		if (raw != null) for (String s : raw.split(",")) if (!s.isBlank()) names.add(s.trim());
		if (names.isEmpty()) return names;
		Set<String> known = clientTests();
		Set<String> unknown = new TreeSet<>(names);
		unknown.removeAll(known);
		if (!unknown.isEmpty())
			throw new IllegalStateException("FB_ONLY names no client test: " + String.join(", ", unknown) + " (valid: " + String.join(", ", known) + ")");
		return names;
	}

	/** Simple names of the registered client test classes, sorted. */
	public static Set<String> clientTests() {
		Set<String> s = new TreeSet<>();
		for (EntrypointContainer<Object> c : FabricLoader.getInstance().getEntrypointContainers("fabric-client-gametest", Object.class))
			s.add(c.getEntrypoint().getClass().getSimpleName());
		return s;
	}

	/** True (and logs it) when the quick layer leaves {@code test} out; FB_ONLY=<test> overrides the layer (naming a test runs it). */
	public static boolean skip(String test) {
		if (!only().isEmpty()) return false; // CompatOnly.skip decides by name
		if (layer().equals("quick") && FULL_ONLY.contains(test)) {
			FractalBuildingsMod.LOGGER.info("[layers] {} skipped (FB_LAYER=quick)", test);
			return true;
		}
		return false;
	}

	/** For the compat world test, which does not go through {@link CompatOnly#skip}: layer and FB_ONLY, but never in a compat-only (matrix) run. */
	public static boolean skipCompat() {
		if (CompatOnly.on()) return false;
		Set<String> only = only();
		if (!only.isEmpty() && !only.contains("CompatClientGameTest")) {
			FractalBuildingsMod.LOGGER.info("[layers] CompatClientGameTest skipped (FB_ONLY={})", String.join(",", only));
			return true;
		}
		return skip("CompatClientGameTest");
	}
}
