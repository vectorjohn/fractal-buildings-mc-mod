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

	/** Design warnings (not violations): per check id, how many were reported and in how many cases. */
	private final Map<String, Integer> warnings = new java.util.TreeMap<>();
	private final List<String> warningCases = new ArrayList<>();
	private final Map<String, String> firstWarning = new java.util.TreeMap<>();
	private int highriseCases;
	private final Map<String, Integer> warningBuildings = new java.util.TreeMap<>();

	/** Records the design warnings of one case; { applicable} says whether the case has a building the checks apply to. */
	public void addWarnings(String label, boolean applicable, List<Violation> found) {
		if (applicable) highriseCases++;
		for (String id : new java.util.TreeSet<>(found.stream().map(Violation::invariant).toList())) warningBuildings.merge(id, 1, Integer::sum);
		boolean any = false;
		for (Violation v : found) {
			warnings.merge(v.invariant(), 1, Integer::sum);
			firstWarning.putIfAbsent(v.invariant(), label + ": " + v.message());
			any = true;
		}
		if (any) warningCases.add(label);
	}

	public int warningCount(String id) { return warnings.getOrDefault(id, 0); }
	public int warningCases() { return warningCases.size(); }

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
		if (highriseCases > 0 || !warnings.isEmpty()) {
			sb.append("\n## Design warnings (not violations)\n\nHigh-rise design checks (DS-22, DS-32): ").append(highriseCases).append(" cases with a high-rise, ").append(warningCases.size()).append(" cases with warnings (door-rise beyond the planner's limit, flight-landings: DS-45).\n\n| check | cases failing | warnings |\n|---|---|---|\n");
			List<String> ids = new ArrayList<>(HighriseChecks.IDS);
			ids.add("door-rise");
			ids.add("flight-landings");
			for (String id : ids) sb.append("| ").append(id).append(" | ").append(warningBuildings.getOrDefault(id, 0)).append(" | ").append(warningCount(id)).append(" |\n");
			sb.append("\n");
			for (var e : firstWarning.entrySet()) sb.append("- first ").append(e.getKey()).append(" `").append(e.getValue()).append("`\n");
		}
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
