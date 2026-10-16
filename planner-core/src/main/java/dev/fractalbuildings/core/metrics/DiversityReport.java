package dev.fractalbuildings.core.metrics;

import dev.fractalbuildings.core.json.JsonWriter;
import java.util.Locale;
import java.util.Map;

/**
 * Result of {@link Diversity#of}. Entropies are in bits, keyed by descriptor name (insertion
 * order is the fixed descriptor order). {@code pairs} is the number of pairs the distance covers.
 */
public record DiversityReport(
		int count,
		int pairs,
		double meanJaccardDistance,
		double nearDuplicateRate,
		int duplicatesBySpecHash,
		int duplicatesByVoxelHash,
		int distinctSignatures,
		Map<String, Double> entropyBits) {

	/** Repeats are counted as buildings minus distinct values. */
	public String toMarkdown() {
		StringBuilder sb = new StringBuilder("# Diversity report\n\n");
		sb.append("| metric | value |\n|---|---|\n");
		sb.append("| buildings | ").append(count).append(" |\n");
		sb.append("| pairs compared | ").append(pairs).append(" |\n");
		sb.append("| mean pairwise Jaccard distance | ").append(f(meanJaccardDistance)).append(" |\n");
		sb.append("| near-duplicate pair rate (< 0.02) | ").append(f(nearDuplicateRate)).append(" |\n");
		sb.append("| repeats by spec hash | ").append(duplicatesBySpecHash).append(" |\n");
		sb.append("| repeats by voxel-set hash | ").append(duplicatesByVoxelHash).append(" |\n");
		sb.append("| distinct (w, d, storeys, rooms) signatures | ").append(distinctSignatures).append(" |\n");
		sb.append("\n| descriptor | entropy (bits) |\n|---|---|\n");
		for (var e : entropyBits.entrySet()) sb.append("| ").append(e.getKey()).append(" | ").append(f(e.getValue())).append(" |\n");
		return sb.toString();
	}

	public String toJson() {
		JsonWriter e = JsonWriter.object();
		for (var en : entropyBits.entrySet()) e.raw(en.getKey(), f(en.getValue()));
		return JsonWriter.object().num("count", count).num("pairs", pairs)
				.raw("meanJaccardDistance", f(meanJaccardDistance)).raw("nearDuplicateRate", f(nearDuplicateRate))
				.num("duplicatesBySpecHash", duplicatesBySpecHash).num("duplicatesByVoxelHash", duplicatesByVoxelHash)
				.num("distinctSignatures", distinctSignatures).raw("entropyBits", e.build()).build();
	}

	private static String f(double v) { return String.format(Locale.ROOT, "%.4f", v); }
}
