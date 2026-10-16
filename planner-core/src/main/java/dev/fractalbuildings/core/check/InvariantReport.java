package dev.fractalbuildings.core.check;

import dev.fractalbuildings.core.ir.Violation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Accumulates (case label, violations) over many scenes and summarises them. */
public final class InvariantReport {
	private record Failure(String label, Violation violation) {}

	private int cases;
	private final List<Failure> failures = new ArrayList<>();
	private final Map<Invariant, Integer> counts = new EnumMap<>(Invariant.class);
	private int failingCases;

	public void add(String label, List<Violation> violations) {
		cases++;
		if (!violations.isEmpty()) failingCases++;
		for (Violation v : violations) {
			failures.add(new Failure(label, v));
			for (Invariant i : Invariant.values()) if (i.id.equals(v.invariant())) counts.merge(i, 1, Integer::sum);
		}
	}

	public int cases() { return cases; }
	public int failingCases() { return failingCases; }
	public int violationCount() { return failures.size(); }
	public int count(Invariant i) { return counts.getOrDefault(i, 0); }

	public String toMarkdown() {
		StringBuilder sb = new StringBuilder();
		sb.append("# Invariant report\n\n");
		sb.append("Cases checked: ").append(cases).append(", failing: ").append(failingCases).append("\n\n");
		sb.append("| invariant | violations |\n|---|---|\n");
		for (Invariant i : Invariant.values()) sb.append("| ").append(i.id).append(" | ").append(count(i)).append(" |\n");
		if (!failures.isEmpty()) {
			sb.append("\nFirst failing cases:\n\n");
			int seen = 0;
			String last = null;
			for (Failure f : failures) {
				if (f.label().equals(last)) continue; // one line per case, its first violation
				if (seen++ == 20) break;
				last = f.label();
				sb.append("- `").append(f.label()).append("` ").append(f.violation().invariant()).append(": ")
						.append(f.violation().message()).append('\n');
			}
		}
		return sb.toString();
	}
}
