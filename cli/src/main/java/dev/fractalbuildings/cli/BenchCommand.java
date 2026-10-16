package dev.fractalbuildings.cli;

import dev.fractalbuildings.cli.Main.UsageException;
import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v0.GeneratorV0;
import dev.fractalbuildings.core.gen.v0.InstancePassV0;
import dev.fractalbuildings.core.gen.v0.ShellGeneratorV0;
import dev.fractalbuildings.core.gen.v0.SpecPolicyV0;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.TerrainPatch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** {@code bench}: single-threaded mean, p50 and p95 per building for spec, shell, instance and check. */
final class BenchCommand {
	private BenchCommand() {}

	static int run(String[] args) throws IOException {
		Set<String> names = new HashSet<>(Matrix.OPTIONS);
		names.add("n");
		Opts o = new Opts(args, names, Set.of());
		long n = o.longValue("n");
		if (n <= 0 || n > 10_000_000) throw new UsageException("bad --n");
		Matrix m = new Matrix(o);
		int count = (int) n;
		double[][] ms = new double[4][count];
		// Warm up so JIT time doesn't land in the first samples.
		for (int i = 0; i < Math.min(count, 200); i++) one(m, i, null, 0);
		for (int i = 0; i < count; i++) one(m, i, ms, i);
		System.out.printf("bench: %d buildings over %d matrix cells, single thread%n", count, m.cells.size());
		String[] stage = {"spec", "shell", "instance", "check"};
		for (int k = 0; k < 4; k++) {
			double[] a = ms[k];
			double sum = 0;
			for (double v : a) sum += v;
			Arrays.sort(a);
			System.out.printf("%-9s mean %8.3f ms  p50 %8.3f ms  p95 %8.3f ms%n", stage[k], sum / count, a[count / 2], a[(int) Math.min(count - 1, Math.ceil(count * 0.95) - 1)]);
		}
		try {
			System.out.println("load: " + Files.readString(Path.of("/proc/loadavg")).trim());
		} catch (IOException | RuntimeException e) {
			// not readable here; fine
		}
		return 0;
	}

	private static void one(Matrix m, int i, double[][] out, int slot) {
		GenRequest req = m.cells.get(i % m.cells.size()).request(i);
		long t0 = System.nanoTime();
		BuildingSpec spec = SpecPolicyV0.spec(req);
		long t1 = System.nanoTime();
		BuildingIr shell = ShellGeneratorV0.generate(spec);
		long t2 = System.nanoTime();
		PlacedBuilding b = InstancePassV0.place(shell, req);
		long t3 = System.nanoTime();
		var lot = req.lot();
		TerrainPatch terrain = TerrainPatch.sample(req.terrain(), lot.x() - 2, lot.z() - 2, lot.w() + 4, lot.d() + 4);
		SceneDoc scene = new SceneDoc(GeneratorV0.GEN_VERSION, req.worldSeed(), terrain, List.of(lot), List.of(b), List.of());
		Invariants.check(scene, req.terrain());
		long t4 = System.nanoTime();
		if (out != null) {
			out[0][slot] = (t1 - t0) / 1e6;
			out[1][slot] = (t2 - t1) / 1e6;
			out[2][slot] = (t3 - t2) / 1e6;
			out[3][slot] = (t4 - t3) / 1e6;
		}
	}
}
