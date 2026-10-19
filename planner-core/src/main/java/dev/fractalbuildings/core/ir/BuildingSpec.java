package dev.fractalbuildings.core.ir;

import java.util.Objects;

/**
 * Everything a shell depends on (the pure-cache key, docs/research/pool/recommendation.md). A
 * shell generator reads only this, never the site. Width runs along local x (the front), depth
 * along local z. Adding a field is fine; it changes every spec hash, so bump {@code genVersion}.
 *
 * <p>From genVersion 2 (generator v1, docs/plans/phase4.md): {@code width} x {@code depth} is the
 * <em>envelope</em>, the part of the lot the shell may use (the shell's bounds cover exactly it);
 * {@code maxHeight} is the exclusive upper bound on local y, so the building stays under the world
 * ceiling wherever the instance pass puts it on the lot; {@code streets} is a bit mask of the local
 * sides that face a street (1 north = front, 2 east, 4 south = back, 8 west), always including the
 * front. Both keys are written to JSON only from genVersion 2, so v0 output is unchanged.
 */
public record BuildingSpec(int genVersion, long specSeed, String style, String type, int width, int depth, int storeys, String roof,
		int maxHeight, int streets) {
	/** {@link #maxHeight} of a spec that does not carry one (genVersion 1): the whole overworld. */
	public static final int DEFAULT_MAX_HEIGHT = 384;
	public static final int STREET_FRONT = 1;

	public BuildingSpec {
		Objects.requireNonNull(style);
		Objects.requireNonNull(type);
		Objects.requireNonNull(roof);
		if (width <= 0 || depth <= 0 || storeys <= 0) {
			throw new IllegalArgumentException("spec " + width + "x" + depth + "x" + storeys);
		}
		if (maxHeight <= 0) throw new IllegalArgumentException("maxHeight " + maxHeight);
		if (streets < 1 || streets > 15 || (streets & STREET_FRONT) == 0) throw new IllegalArgumentException("streets " + streets);
	}

	/** A genVersion 1 spec (no envelope keys). */
	public BuildingSpec(int genVersion, long specSeed, String style, String type, int width, int depth, int storeys, String roof) {
		this(genVersion, specSeed, style, type, width, depth, storeys, roof, DEFAULT_MAX_HEIGHT, STREET_FRONT);
	}

	/** True if this spec's JSON carries the genVersion 2 keys (spec {@code maxHeight}, {@code streets}; shell {@code hooks}). */
	public boolean v2Keys() { return genVersion >= 2; }

	/** True if local side {@code f} faces a street. */
	public boolean street(Facing f) { return (streets & (1 << f.ordinal())) != 0; }
}
