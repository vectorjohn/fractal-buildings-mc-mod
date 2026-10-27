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
 *
 * <p>{@code lead} (Phase 5 JT; JSON key written only when above 0): blocks the envelope extends in front of
 * the type's usual front edge (the type's front margin plus the air rights), so that upper storeys can jetty over
 * the street. Local z = 0 is then {@code lead} blocks in front of where it would be; ground masses keep the
 * usual envelope (they start at z >= {@code lead}).
 *
 * <p>{@code square} (7.17 SHOPLOW S4; JSON key written only when true): the lot is the one on its square that may carry the
 * market hall, on level ground; only the shop type reads it.
 */
public record BuildingSpec(int genVersion, long specSeed, String style, String type, int width, int depth, int storeys, String roof,
		int maxHeight, int streets, int lead, boolean square) {
	/** {@link #maxHeight} of a spec that does not carry one (genVersion 1): the whole overworld. */
	public static final int DEFAULT_MAX_HEIGHT = 384;
	public static final int STREET_FRONT = 1;
	/** Most blocks the envelope may extend in front of the type's usual front edge: its front margin plus 2 blocks of air rights. */
	public static final int MAX_LEAD = 4;

	public BuildingSpec {
		Objects.requireNonNull(style);
		Objects.requireNonNull(type);
		Objects.requireNonNull(roof);
		if (width <= 0 || depth <= 0 || storeys <= 0) {
			throw new IllegalArgumentException("spec " + width + "x" + depth + "x" + storeys);
		}
		if (maxHeight <= 0) throw new IllegalArgumentException("maxHeight " + maxHeight);
		if (streets < 1 || streets > 15 || (streets & STREET_FRONT) == 0) throw new IllegalArgumentException("streets " + streets);
		if (lead < 0 || lead > MAX_LEAD) throw new IllegalArgumentException("lead " + lead);
	}

	/** A spec that is not a market-hall lot. */
	public BuildingSpec(int genVersion, long specSeed, String style, String type, int width, int depth, int storeys, String roof,
			int maxHeight, int streets, int lead) {
		this(genVersion, specSeed, style, type, width, depth, storeys, roof, maxHeight, streets, lead, false);
	}

	/** A spec without a lead (everything before the jetty step). */
	public BuildingSpec(int genVersion, long specSeed, String style, String type, int width, int depth, int storeys, String roof,
			int maxHeight, int streets) {
		this(genVersion, specSeed, style, type, width, depth, storeys, roof, maxHeight, streets, 0);
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
