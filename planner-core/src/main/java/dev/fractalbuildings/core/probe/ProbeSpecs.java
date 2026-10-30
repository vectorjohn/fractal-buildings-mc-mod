package dev.fractalbuildings.core.probe;

import java.io.IOException;
import java.nio.file.Path;

/** Parses probe spec strings for the CLI and tests, e.g. {@code flat:64}, {@code recorded:path.json}. */
public final class ProbeSpecs {
	private ProbeSpecs() {}

	public record Probes(TerrainProbe terrain, BiomeProbe biome) {}

	public static final String PLAINS = "minecraft:plains";
	public static final String RIVER = "minecraft:river";

	/** The skeleton's coarse lattice (the CLI and the game read {@link CoarseProbe} at this stride). */
	public static final int SKELETON_LATTICE = 32;

	/**
	 * 6.24 EMPTYCITY: the step of a recorded dump coarser than the skeleton lattice, else 0. On such a dump (the 64-block world maps) the
	 * skeleton's 32-block columns and the exact terrain are interpolated between samples 64 apart: a river sample floods 64 blocks around
	 * it and a lone low sample becomes a pit no road can leave, so a settlement can come out with no street or building that the game
	 * builds in full (seed 3141592653589793238 city sm1_0, five hamlets and villages; `EmptySettlementLiveTest` in the mod plans them on
	 * live terrain). Tools that report {@code settlement-empty} (or other planning results) from such a dump say so.
	 */
	public static int coarseDumpStep(TerrainProbe t) {
		return t instanceof RecordedProbe r && r.step() > SKELETON_LATTICE ? r.step() : 0;
	}

	/** The note tools print with a result from a dump of {@link #coarseDumpStep} {@code step}. */
	public static String coarseDumpNote(int step) {
		return "terrain step " + step + " is coarser than the " + SKELETON_LATTICE + "-block skeleton lattice: hubs, main roads and lots are planned on interpolated ground, so an empty settlement here (settlement-empty, roads-connected at the hub) is most likely a dump artefact; plan it on a dump of step " + SKELETON_LATTICE + " or finer (TerrainDumpTool) or on live terrain before reporting it (6.24 EMPTYCITY)";
	}

	/**
	 * A base terrain spec, optionally followed by modifiers joined with {@code +}: {@code river:<x0>:<width>:<depth>} (a
	 * north-south river over x in [x0, x0+width): floor lowered by depth, filled with water, biome river) and
	 * {@code biomes:split:<x>:<a>:<b>} (biome a west of x, b from x east; ids without a namespace get minecraft:).
	 */
	public static Probes parse(String spec) {
		if (spec == null || spec.isEmpty()) throw bad(spec, "empty spec");
		String[] parts = spec.split("\\+", -1);
		Probes p = parseBase(parts[0]);
		for (int i = 1; i < parts.length; i++) p = modify(spec, p, parts[i]);
		return p;
	}

	private static Probes modify(String spec, Probes p, String part) {
		int c = part.indexOf(':');
		String kind = c < 0 ? part : part.substring(0, c), rest = c < 0 ? "" : part.substring(c + 1);
		switch (kind) {
			case "river" -> {
				int[] a = ints(spec, rest.split(":", -1));
				need(spec, a, 3, "river:<x0>:<width>:<depth>");
				if (a[1] <= 0 || a[2] <= 0) throw bad(spec, "river width and depth must be positive");
				int x0 = a[0], x1 = a[0] + a[1], depth = a[2];
				TerrainProbe base = p.terrain();
				BiomeProbe bb = p.biome();
				TerrainProbe t = new TerrainProbe() {
					@Override public int floorY(int x, int z) { return base.floorY(x, z) - (x >= x0 && x < x1 ? depth : 0); }
					@Override public int waterDepth(int x, int z) { return base.waterDepth(x, z) + (x >= x0 && x < x1 ? depth : 0); }
				};
				return new Probes(t, (x, z) -> x >= x0 && x < x1 ? RIVER : bb.biome(x, z));
			}
			case "biomes" -> {
				String[] f = rest.split(":", -1);
				if (f.length != 4 || !f[0].equals("split")) throw bad(spec, "expected biomes:split:<x>:<a>:<b>");
				int x = ints(spec, new String[] {f[1]})[0];
				return new Probes(p.terrain(), SyntheticBiomes.halfPlaneX(x, ns(f[2]), ns(f[3])));
			}
			default -> throw bad(spec, "unknown modifier '" + kind + "' (river, biomes:split)");
		}
	}

	private static String ns(String id) { return id.indexOf(':') >= 0 ? id : "minecraft:" + id; }

	private static Probes parseBase(String spec) {
		int c = spec.indexOf(':');
		String kind = c < 0 ? spec : spec.substring(0, c);
		String rest = c < 0 ? "" : spec.substring(c + 1);
		if (kind.equals("recorded")) {
			if (rest.isEmpty()) throw bad(spec, "recorded needs a path");
			try {
				RecordedProbe r = RecordedProbe.read(Path.of(rest));
				return new Probes(r, r);
			} catch (IOException e) {
				throw bad(spec, "cannot read " + rest + ": " + e.getMessage());
			}
		}
		int[] a = rest.isEmpty() ? new int[0] : ints(spec, rest.split(":", -1));
		TerrainProbe t;
		switch (kind) {
			case "flat" -> { need(spec, a, 1, "flat:<y>"); t = SyntheticTerrain.flat(a[0]); }
			case "plane" -> {
				need(spec, a, 4, "plane:<y0>:<riseX>:<riseZ>:<run>");
				if (a[3] <= 0) throw bad(spec, "run must be positive");
				t = SyntheticTerrain.plane(a[0], a[1], a[2], a[3]);
			}
			case "hill" -> {
				need(spec, a, 3, "hill:<baseY>:<peak>:<radius>");
				if (a[2] <= 0) throw bad(spec, "radius must be positive");
				t = SyntheticTerrain.hill(0, 0, a[1], a[2], a[0]);
			}
			case "noise" -> {
				need(spec, a, 4, "noise:<seed>:<baseY>:<amplitude>:<wavelength>");
				if (a[2] < 0 || a[3] <= 0) throw bad(spec, "amplitude must be >= 0 and wavelength positive");
				t = SyntheticTerrain.noise(a[0], a[1], a[2], a[3]);
			}
			default -> throw bad(spec, "unknown kind '" + kind + "' (flat, plane, hill, noise, recorded)");
		}
		return new Probes(t, SyntheticBiomes.constant(PLAINS));
	}

	private static void need(String spec, int[] a, int n, String form) {
		if (a.length != n) throw bad(spec, "expected " + form);
	}

	private static int[] ints(String spec, String[] parts) {
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			try { out[i] = Integer.parseInt(parts[i]); }
			catch (NumberFormatException e) { throw bad(spec, "not an integer: '" + parts[i] + "'"); }
		}
		return out;
	}

	private static IllegalArgumentException bad(String spec, String why) {
		return new IllegalArgumentException("bad probe spec '" + spec + "': " + why);
	}
}
