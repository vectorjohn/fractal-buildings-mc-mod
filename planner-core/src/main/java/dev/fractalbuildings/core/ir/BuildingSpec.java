package dev.fractalbuildings.core.ir;

import java.util.Objects;

/**
 * Everything a shell depends on (the pure-cache key, docs/research/pool/recommendation.md). A
 * shell generator reads only this, never the site. Width runs along local x (the front), depth
 * along local z. Adding a field is fine; it changes every spec hash, so bump {@code genVersion}.
 */
public record BuildingSpec(int genVersion, long specSeed, String style, String type, int width, int depth, int storeys, String roof) {
	public BuildingSpec {
		Objects.requireNonNull(style);
		Objects.requireNonNull(type);
		Objects.requireNonNull(roof);
		if (width <= 0 || depth <= 0 || storeys <= 0) {
			throw new IllegalArgumentException("spec " + width + "x" + depth + "x" + storeys);
		}
	}
}
