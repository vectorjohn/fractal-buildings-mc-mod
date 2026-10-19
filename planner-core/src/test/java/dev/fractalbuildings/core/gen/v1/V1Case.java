package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.rand.Draws;
import dev.fractalbuildings.core.rand.Hash64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One property-test case for generator v1, described only by values the CLI accepts, so a failure
 * prints a command that rebuilds the same scene ({@link #replay}). Cases are drawn from (type,
 * seed): lot size from the type's test lots, position, front, style (every style plus "auto") and a
 * terrain spec string.
 */
public record V1Case(long seed, String type, String style, int lotW, int lotD, int atX, int atZ, Facing front, String terrain) {
	/** Recorded terrain as tests see it (cwd = planner-core) and as the replay command sees it (cwd = repo root). */
	static final String CROP = "../fixtures/terrain/s4-12345-crop32.json";
	static final int[][] CROP_SPOTS = {{-1300, -4000}, {-700, -3500}, {0, -3000}, {300, -2400}};

	private static final Map<String, ProbeSpecs.Probes> PROBES = new TreeMap<>();

	static synchronized ProbeSpecs.Probes probes(String spec) {
		return PROBES.computeIfAbsent(spec, ProbeSpecs::parse);
	}

	public GenRequest request() {
		ProbeSpecs.Probes p = probes(terrain);
		return new GenRequest(seed, new Lot("lot0", atX, atZ, lotW, lotD, front), type, style, p.terrain(), p.biome());
	}

	/** A command that regenerates this case with the invariants embedded (run from the repo root). */
	public String replay() {
		return "fractal-cli gen --type " + type + " --seed " + seed + " --lot " + lotW + "x" + lotD + " --at " + atX + "," + atZ
				+ " --front " + front.json() + " --style " + style + " --terrain " + terrain.replace("recorded:../", "recorded:") + " --check";
	}

	@Override
	public String toString() { return replay(); }

	/** The styles cases draw from: every registered style plus "auto". */
	static List<String> styles() {
		List<String> s = new ArrayList<>(StyleLibrary.ids());
		s.add(StyleLibrary.AUTO);
		return s;
	}

	/** Golden-digest styles: fixed, so adding a style (4.7) never changes the golden files. */
	static final List<String> GOLDEN_STYLES = List.of("plain", "medieval");

	/** Case number {@code seed} for a type, lot size {@code lotIdx} of its test lots, over every style. */
	public static V1Case draw(BuildingType t, int lotIdx, long seed) { return draw(t, lotIdx, seed, styles()); }

	/** As {@link #draw(BuildingType, int, long)}, choosing among {@code styles}. */
	public static V1Case draw(BuildingType t, int lotIdx, long seed, List<String> styles) {
		int[] lot = t.limits().testLots().get(lotIdx);
		Draws r = new Draws(Hash64.hash(seed, "v1-case", lotIdx, Hash64.of(t.id())));
		Facing front = Facing.values()[r.below(4)];
		String style = styles.get(r.below(styles.size()));
		lot = t.limits().oriented(lot, front);
		int x = r.range(-60, 60), z = r.range(-60, 60);
		int cx = x + lot[0] / 2, cz = z + lot[1] / 2;
		int g = t.limits().maxGrade(); // no ground steeper than 1 in g
		String terrain;
		switch (r.below(8)) {
			case 0 -> terrain = "flat:" + r.range(-50, 250);
			case 1 -> terrain = plane(r.range(40, 200), 1, 0, Math.max(g, r.range(2, 6)), cx, cz);
			case 2 -> terrain = plane(r.range(40, 200), 0, 1, Math.max(g, r.range(2, 6)), cx, cz);
			case 3 -> terrain = plane(r.range(40, 200), 1, 1, Math.max(2 * g, r.range(3, 8)), cx, cz);
			case 4 -> terrain = plane(r.range(40, 150), r.chance(1, 2) ? 1 : 0, 1, g, cx, cz);
			case 5 -> {
				int peak = r.range(4, 30);
				terrain = "hill:" + r.range(40, 120) + ":" + peak + ":" + Math.max(peak * g, r.range(8, 60));
			}
			case 6 -> {
				int wl = r.range(3, 12);
				terrain = "noise:" + seed + ":" + r.range(40, 150) + ":" + Math.max(1, Math.min(r.range(2, 8), wl / g)) + ":" + wl;
			}
			default -> {
				if (lot[0] > 120 || lot[1] > 120) { // the recorded crop is only ~2000 x 2000: big lots get noise instead
					terrain = "noise:" + seed + ":" + r.range(40, 150) + ":" + r.range(2, 6) + ":" + r.range(3, 12);
				} else {
					int[] spot = CROP_SPOTS[r.below(CROP_SPOTS.length)];
					x = spot[0] + r.range(-40, 40);
					z = spot[1] + r.range(-40, 40);
					terrain = "recorded:" + CROP;
				}
			}
		}
		return new V1Case(seed, t.id(), style, lot[0], lot[1], x, z, front, terrain);
	}

	/** {@code plane:Y0:RX:RZ:RUN} whose height at (cx, cz) is centreY. */
	static String plane(int centreY, int rx, int rz, int run, int cx, int cz) {
		int y0 = centreY - Math.floorDiv(cx * rx + cz * rz, run);
		return "plane:" + y0 + ":" + rx + ":" + rz + ":" + run;
	}
}
