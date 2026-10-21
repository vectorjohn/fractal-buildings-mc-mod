package dev.fractalbuildings.core.terrain;

import dev.fractalbuildings.core.ir.Box3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Merges per-column items into few boxes: runs along x, then runs of equal runs along z. Deterministic. */
final class BoxMerge {
	private BoxMerge() {}

	record Item(GroundBox.Kind kind, int x, int y, int z, int h) {}

	private record Run(GroundBox.Kind kind, int y, int h, int x, int len, int z, int dz) {}

	static List<GroundBox> merge(List<Item> items) {
		ArrayList<Item> sorted = new ArrayList<>(items);
		sorted.sort(Comparator.comparingInt((Item i) -> i.kind().ordinal()).thenComparingInt(Item::y)
				.thenComparingInt(Item::h).thenComparingInt(Item::z).thenComparingInt(Item::x));
		ArrayList<Run> runs = new ArrayList<>();
		for (Item it : sorted) {
			if (!runs.isEmpty()) {
				Run last = runs.get(runs.size() - 1);
				if (last.kind() == it.kind() && last.y() == it.y() && last.h() == it.h() && last.z() == it.z()
						&& last.x() + last.len() == it.x()) {
					runs.set(runs.size() - 1, new Run(last.kind(), last.y(), last.h(), last.x(), last.len() + 1, last.z(), 1));
					continue;
				}
				if (last.kind() == it.kind() && last.y() == it.y() && last.h() == it.h() && last.z() == it.z()
						&& last.x() + last.len() > it.x()) {
					continue; // duplicate cell
				}
			}
			runs.add(new Run(it.kind(), it.y(), it.h(), it.x(), 1, it.z(), 1));
		}
		runs.sort(Comparator.comparingInt((Run r) -> r.kind().ordinal()).thenComparingInt(Run::y)
				.thenComparingInt(Run::h).thenComparingInt(Run::x).thenComparingInt(Run::len).thenComparingInt(Run::z));
		ArrayList<Run> merged = new ArrayList<>();
		for (Run r : runs) {
			if (!merged.isEmpty()) {
				Run last = merged.get(merged.size() - 1);
				if (last.kind() == r.kind() && last.y() == r.y() && last.h() == r.h() && last.x() == r.x()
						&& last.len() == r.len() && last.z() + last.dz() == r.z()) {
					merged.set(merged.size() - 1, new Run(last.kind(), last.y(), last.h(), last.x(), last.len(), last.z(), last.dz() + 1));
					continue;
				}
			}
			merged.add(r);
		}
		ArrayList<GroundBox> out = new ArrayList<>();
		for (Run r : merged) out.add(new GroundBox(Box3.of(r.x(), r.y(), r.z(), r.len(), r.h(), r.dz()), r.kind()));
		return out;
	}
}
