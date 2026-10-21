package dev.fractalbuildings.core.probe;

import java.io.IOException;
import java.nio.file.Path;

/** Parses probe spec strings for the CLI and tests, e.g. {@code flat:64}, {@code recorded:path.json}. */
public final class ProbeSpecs {
	private ProbeSpecs() {}

	public record Probes(TerrainProbe terrain, BiomeProbe biome) {}

	public static final String PLAINS = "minecraft:plains";
	public static final String RIVER = "minecraft:river";

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
