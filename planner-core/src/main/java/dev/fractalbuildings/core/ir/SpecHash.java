package dev.fractalbuildings.core.ir;

import dev.fractalbuildings.core.json.JsonWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable 16-hex-digit digest of a {@link BuildingSpec}: SHA-256 of its compact JSON, first 8 bytes. */
public final class SpecHash {
	private SpecHash() {}

	public static String of(BuildingSpec s) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-256").digest(canonical(s).getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i] & 0xFF));
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** The spec as compact JSON, keys in IR order. */
	public static String canonical(BuildingSpec s) { return fill(JsonWriter.compactObject(), s).build(); }

	static JsonWriter fill(JsonWriter w, BuildingSpec s) {
		w.num("genVersion", s.genVersion()).str64("specSeed", s.specSeed()).text("style", s.style())
				.text("type", s.type()).num("width", s.width()).num("depth", s.depth())
				.num("storeys", s.storeys()).text("roof", s.roof());
		if (s.v2Keys()) w.num("maxHeight", s.maxHeight()).num("streets", s.streets());
		return w;
	}
}
