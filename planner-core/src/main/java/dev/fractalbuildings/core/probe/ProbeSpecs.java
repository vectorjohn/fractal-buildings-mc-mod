package dev.fractalbuildings.core.probe;

import java.io.IOException;
import java.nio.file.Path;

/** Parses probe spec strings for the CLI and tests, e.g. {@code flat:64}, {@code recorded:path.json}. */
public final class ProbeSpecs {
	private ProbeSpecs() {}

	public record Probes(TerrainProbe terrain, BiomeProbe biome) {}

	public static final String PLAINS = "minecraft:plains";

	public static Probes parse(String spec) {
		if (spec == null || spec.isEmpty()) throw bad(spec, "empty spec");
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
