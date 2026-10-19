package dev.fractalbuildings.cli;

import dev.fractalbuildings.cli.Main.UsageException;
import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.Generator;
import dev.fractalbuildings.core.gen.Styles;
import dev.fractalbuildings.core.gen.v0.GeneratorV0;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.Types;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.RecordedProbe;
import java.util.List;

/** What the commands share: generator choice, building a request from option strings, running the generator. */
final class Scenes {
	private Scenes() {}

	static final String V0 = "v0", V1 = "v1";

	/** {@code --gen v0|v1}, default v1 (Phase 4). */
	static String gen(Opts o) {
		String g = o.get("gen", V1);
		if (!g.equals(V0) && !g.equals(V1)) throw new UsageException("bad --gen '" + g + "', expected v0 or v1");
		return g;
	}

	static Generator generator(String gen) { return gen.equals(V0) ? new GeneratorV0() : new GeneratorV1(); }

	static ProbeSpecs.Probes probes(String spec) {
		try { return ProbeSpecs.parse(spec); }
		catch (IllegalArgumentException e) { throw new UsageException(e.getMessage()); }
	}

	static String style(String s, String gen) {
		try {
			if (gen.equals(V0)) return Styles.require(s);
			return StyleLibrary.AUTO.equals(s) ? s : StyleLibrary.get(s).id();
		} catch (IllegalArgumentException e) {
			throw new UsageException(e.getMessage());
		}
	}

	static List<String> styles(String gen) { return gen.equals(V0) ? Styles.ALL : StyleLibrary.ids(); }

	static String type(String t, String gen) {
		if (gen.equals(V0)) {
			if (!t.equals(GenRequest.DEFAULT_TYPE)) throw new UsageException("generator v0 only builds houses");
			return t;
		}
		try { return Types.get(t).id(); }
		catch (IllegalArgumentException e) { throw new UsageException(e.getMessage()); }
	}

	static List<String> types(String gen) { return gen.equals(V0) ? List.of(GenRequest.DEFAULT_TYPE) : Types.ids(); }

	/** The lot origin: {@code --at} if given, else the dump's grid centre for a recorded terrain, else 0,0. */
	static int[] origin(String at, ProbeSpecs.Probes p, int w, int d) {
		if (at != null) return Opts.at(at);
		if (p.terrain() instanceof RecordedProbe r) {
			return new int[] {r.x0() + r.nx() * r.step() / 2 - w / 2, r.z0() + r.nz() * r.step() / 2 - d / 2};
		}
		return new int[] {0, 0};
	}

	static GenRequest request(long seed, int[] lotWd, int[] at, Facing front, String type, String style, ProbeSpecs.Probes p) {
		return new GenRequest(seed, new Lot("lot0", at[0], at[1], lotWd[0], lotWd[1], front), type, style, p.terrain(), p.biome());
	}

	/** Generates the scene; with {@code check}, runs the invariants against the real probe and embeds the violations. */
	static SceneDoc generate(GenRequest req, boolean check, String gen) {
		SceneDoc s;
		try { s = generator(gen).generate(req); }
		catch (IllegalArgumentException e) { throw new UsageException(e.getMessage()); }
		if (!check) return s;
		return new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), s.buildings(), Invariants.check(s, req.terrain()));
	}
}
