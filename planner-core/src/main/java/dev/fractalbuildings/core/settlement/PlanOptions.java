package dev.fractalbuildings.core.settlement;

/**
 * Switches of the settlement planner that change its output (METROFILL DX). {@link #DEFAULT} is today's behaviour; anything else is
 * opt-in through {@link TierPrograms#of(Tier, PlanOptions)}.
 *
 * @param diagonals whether a city or metropolis may have 45-degree avenues and diagonal lanes (off: axis directions and 4-neighbour routes only)
 */
public record PlanOptions(boolean diagonals) {
	public static final PlanOptions DEFAULT = new PlanOptions(true);
}
