package dev.fractalbuildings.mod.ordersim;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * ORDERSIM S3: the report, in the style of the order test's log lines. One line per run (scenario, order, cells, ledger digest,
 * missing/extra/wrong, duplicates, solid and air against the raster, the spill histogram, the cross-order diff, contract violations, ms), then
 * the first bad cells with their neighbours and last writers. Goes to stdout and to {@code build/ordersim/<scenario>.report} (the cwd of
 * {@code :mod:test} is {@code mod/}), which verifiers read.
 */
public final class SimReport {
	private SimReport() {}

	public static final Path DIR = Path.of("build", "ordersim");

	public static String line(SimRun r) {
		return "[ordersim] " + r.scenario + " " + r.order + " ledger=" + r.ledgerCells + "/" + r.refCells + " " + abbreviate(r.ledgerDigest) + " missing=" + r.missing + " extra=" + r.extra + " wrong=" + r.wrong + " dup=" + r.duplicates
			+ " solid=" + r.solidBad + "/" + r.solid + " unknown=" + r.solidUnknown + " air=" + r.airBad + "/" + r.air + " unknown=" + r.airUnknown
			+ " diff=" + (r.crossDiff < 0 ? "base" : r.crossDiff) + " contracts=" + (r.contractViolations < 0 ? "-" : r.contractViolations) + " idem=" + (r.idempotenceChanges < 0 ? "-" : r.idempotenceChanges)
			+ " zone=" + r.oursZoneViolations + "+" + r.featureZoneViolations + " promoted=" + r.promoted + " features=" + r.features.placed() + "/" + r.features.attempted() + " vetoed=" + r.features.vetoed() + " " + r.ms + " ms";
	}

	private static String abbreviate(String d) { return d.length() > 8 ? d.substring(0, 8) : d; }

	public static String text(OrderSim.Result res) {
		StringBuilder b = new StringBuilder();
		Scenario s = res.scenario;
		b.append("[ordersim] scenario ").append(s.id).append(" inner ").append(s.cx).append(',').append(s.cz).append(' ').append(s.n).append('x').append(s.n).append(" chunks, ").append(s.calls.size()).append(" feature calls, ")
			.append(s.plan().pieceCount).append(" pieces, plan ").append(res.planMs).append(" ms, ").append(res.runs.size()).append(" orders in ").append(res.totalMs).append(" ms, loadavg ").append(load()).append('\n');
		for (SimRun r : res.runs) b.append(line(r)).append('\n');
		for (SimRun r : res.runs) {
			if (!r.histogram.isEmpty()) b.append("[ordersim] ").append(r.order).append(" spill ").append(r.histogram).append('\n');
		}
		b.append("[ordersim] features (").append(res.runs.get(0).order).append(") ").append(res.runs.get(0).features).append('\n');
		if (!res.runs.get(0).branchWrites.isEmpty()) b.append("[ordersim] writes by pass (").append(res.runs.get(0).order).append(") ").append(res.runs.get(0).branchWrites).append('\n');
		List<String> failed = res.failed();
		b.append(failed.isEmpty() ? "[ordersim] PASS\n" : "[ordersim] FAIL\n");
		for (String f : failed) b.append("  ").append(f).append('\n');
		for (SimRun r : res.runs) {
			for (String d : r.badCells) b.append("  ").append(r.order).append(": ").append(d).append('\n');
			for (String d : r.contractDetails) b.append("  ").append(r.order).append(": C5 ").append(d).append('\n');
			for (String d : r.diffCells) b.append("  ").append(r.order).append(": C4 ").append(s.spaced ? "" : "(report only) ").append(d).append('\n');
			for (String d : r.errors) b.append("  ").append(r.order).append(": C1 ").append(d).append('\n');
		}
		return b.toString();
	}

	/** Prints the report and writes it to {@code build/ordersim/<scenario>.report}. */
	public static String write(OrderSim.Result res) {
		String t = text(res);
		System.out.print(t);
		try {
			Files.createDirectories(DIR);
			Files.writeString(DIR.resolve(res.scenario.id + ".report"), t);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return t;
	}

	static String load() {
		try {
			return Files.readString(Path.of("/proc/loadavg")).split(" ")[0];
		} catch (IOException e) {
			return "?";
		}
	}
}
