package dev.fractalbuildings.core.metrics;

import dev.fractalbuildings.core.ir.PlacedBuilding;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Sameness across a batch of buildings (ROADMAP 3.11). The result does not depend on the order of
 * the input list: pairwise distances are summed in sorted order and counts are set sizes.
 * Pairwise distances cover the first {@link #MAX_PAIRWISE} buildings only (the rest still count
 * towards duplicates, signatures and entropy).
 */
public final class Diversity {
	public static final int MAX_PAIRWISE = 256;
	public static final double NEAR_DUPLICATE = 0.02;

	private Diversity() {}

	public static DiversityReport of(List<PlacedBuilding> buildings) {
		List<Descriptor> ds = new ArrayList<>();
		for (PlacedBuilding b : buildings) ds.add(Descriptor.of(b));
		return ofDescriptors(ds);
	}

	static DiversityReport ofDescriptors(List<Descriptor> ds) {
		int n = ds.size();
		// Canonical order so "the first 256" and the sum don't depend on input order.
		List<Descriptor> sorted = new ArrayList<>(ds);
		sorted.sort((a, b) -> {
			int c = Long.compare(a.voxelHash(), b.voxelHash());
			return c != 0 ? c : a.specHash().compareTo(b.specHash());
		});
		int m = Math.min(n, MAX_PAIRWISE);
		int pairs = m * (m - 1) / 2;
		double[] dist = new double[pairs];
		int k = 0, near = 0;
		for (int i = 0; i < m; i++)
			for (int j = i + 1; j < m; j++) {
				double d = jaccardDistance(sorted.get(i).voxels(), sorted.get(j).voxels());
				dist[k++] = d;
				if (d < NEAR_DUPLICATE) near++;
			}
		Arrays.sort(dist);
		double sum = 0;
		for (double d : dist) sum += d;

		TreeSet<String> specs = new TreeSet<>();
		TreeSet<Long> vox = new TreeSet<>();
		TreeSet<String> sigs = new TreeSet<>();
		Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
		for (Descriptor d : ds) {
			specs.add(d.specHash());
			vox.add(d.voxelHash());
			sigs.add(d.signature());
			for (String[] c : d.categories()) counts.computeIfAbsent(c[0], x -> new TreeMap<>()).merge(c[1], 1, Integer::sum);
		}
		Map<String, Double> ent = new LinkedHashMap<>();
		for (var e : counts.entrySet()) ent.put(e.getKey(), entropy(e.getValue().values(), n));
		return new DiversityReport(n, pairs, pairs == 0 ? 0 : sum / pairs, pairs == 0 ? 0 : (double) near / pairs,
				n - specs.size(), n - vox.size(), sigs.size(), ent);
	}

	/** 1 - |A and B| / |A or B| over sorted position arrays; 0 for two empty sets. */
	static double jaccardDistance(long[] a, long[] b) {
		int i = 0, j = 0, inter = 0;
		while (i < a.length && j < b.length) {
			if (a[i] == b[j]) { inter++; i++; j++; }
			else if (a[i] < b[j]) i++;
			else j++;
		}
		int union = a.length + b.length - inter;
		return union == 0 ? 0 : 1.0 - (double) inter / union;
	}

	static double entropy(Iterable<Integer> counts, int total) {
		double h = 0;
		for (int c : counts) {
			double p = (double) c / total;
			h -= p * StrictMath.log(p) / StrictMath.log(2);
		}
		return h == 0 ? 0 : h; // avoid -0.0
	}
}
