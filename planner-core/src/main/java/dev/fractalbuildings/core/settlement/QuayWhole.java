package dev.fractalbuildings.core.settlement;

import java.util.List;

/**
 * One graded quay part: its columns, the graded level of each, which columns are steeper than the street grade, and the key that
 * puts it in the global canonical order ({@link QuayTrace#orderKey}; empty for the reference trace's parts,
 * {@code Districts.Build.NO_ORDER}). Both the lazy trace ({@link QuayTrace}) and {@code Districts.Build} use this record.
 */
record QuayWhole(List<int[]> cols, int[] y, boolean[] steep, long[] order) {}
