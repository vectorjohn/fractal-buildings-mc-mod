package dev.fractalbuildings.core.settlement;

import java.util.List;

/**
 * One graded quay part: its columns, the graded level of each, which columns are steeper than the street grade, and the key that
 * puts it in the global canonical order ({@link QuayTrace#orderKey}). QUAYLAZY step 3a; {@code Districts.Build} still has its own
 * three-field record until step 3b switches it to this one.
 */
record QuayWhole(List<int[]> cols, int[] y, boolean[] steep, long[] order) {}
