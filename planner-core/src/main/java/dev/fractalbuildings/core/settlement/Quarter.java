package dev.fractalbuildings.core.settlement;

import java.util.Objects;

/** A quarter: {@code id} is its index in the plan's quarter list. */
public record Quarter(int id, QuarterKind kind) {
	public Quarter { Objects.requireNonNull(kind); }
}
