package dev.fractalbuildings.cli;

import dev.fractalbuildings.core.settlement.SampleSettlement;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

/** {@code sample-city}: writes the hand-laid sample settlement, or with {@code --big N} a synthetic N x N grid for viewer performance tests. */
final class SampleCityCommand {
	private SampleCityCommand() {}

	static int run(String[] args) throws IOException {
		Opts o = new Opts(args, Set.of("out-dir", "big"), Set.of());
		Path dir = Path.of(o.require("out-dir"));
		SampleSettlement.Sample s;
		if (o.has("big")) {
			int n;
			try { n = Integer.parseInt(o.get("big", null)); } catch (NumberFormatException e) { throw new Main.UsageException("bad --big '" + o.get("big", null) + "'"); }
			if (n < 128 || n > 16384) throw new Main.UsageException("--big must be 128..16384");
			s = SampleSettlement.big(n);
		} else {
			s = SampleSettlement.sample();
		}
		s.write(dir);
		System.err.println("wrote " + s.files().size() + " files to " + dir);
		return 0;
	}
}
