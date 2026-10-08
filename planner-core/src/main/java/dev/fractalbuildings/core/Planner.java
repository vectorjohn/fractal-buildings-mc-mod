package dev.fractalbuildings.core;

import java.util.List;

/** Entry point of the pure-Java planner core. */
public final class Planner {
	private Planner() {}

	public static String version() {
		return "0.1.0";
	}

	/**
	 * Smoke-path generator: a hard-coded 5x5x5 box (floor layer, walls, roof layer). Ignores the
	 * seed for now; it exists so the CLI -> JSON -> viewer path can be exercised end to end.
	 */
	public static SmokeIr smokeBox(long seed) {
		return new SmokeIr(
				SmokeIr.VERSION,
				new int[] {5, 5, 5},
				List.of(
						new SmokeIr.Box(new int[] {0, 0, 0}, new int[] {5, 1, 5}, "floor"),
						new SmokeIr.Box(new int[] {0, 1, 0}, new int[] {5, 3, 5}, "wall"),
						new SmokeIr.Box(new int[] {0, 4, 0}, new int[] {5, 1, 5}, "roof")));
	}
}
