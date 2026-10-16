package dev.fractalbuildings.cli;

import dev.fractalbuildings.cli.Main.UsageException;
import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.Styles;
import dev.fractalbuildings.core.gen.v0.GeneratorV0;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.probe.RecordedProbe;

/** What gen, batch, check and bench share: building a request from option strings and running the generator. */
final class Scenes {
	private Scenes() {}

	static ProbeSpecs.Probes probes(String spec) {
		try { return ProbeSpecs.parse(spec); }
		catch (IllegalArgumentException e) { throw new UsageException(e.getMessage()); }
	}

	static String style(String s) {
		try { return Styles.require(s); }
		catch (IllegalArgumentException e) { throw new UsageException(e.getMessage()); }
	}

	/** The lot origin: {@code --at} if given, else the dump's grid centre for a recorded terrain, else 0,0. */
	static int[] origin(String at, ProbeSpecs.Probes p, int w, int d) {
		if (at != null) return Opts.at(at);
		if (p.terrain() instanceof RecordedProbe r) {
			return new int[] {r.x0() + r.nx() * r.step() / 2 - w / 2, r.z0() + r.nz() * r.step() / 2 - d / 2};
		}
		return new int[] {0, 0};
	}

	static GenRequest request(long seed, int[] lotWd, int[] at, Facing front, String style, ProbeSpecs.Probes p) {
		return new GenRequest(seed, new Lot("lot0", at[0], at[1], lotWd[0], lotWd[1], front), style, p.terrain(), p.biome());
	}

	/** Generates the scene; with {@code check}, runs the invariants against the real probe and embeds the violations. */
	static SceneDoc generate(GenRequest req, boolean check) {
		SceneDoc s;
		try { s = new GeneratorV0().generate(req); }
		catch (IllegalArgumentException e) { throw new UsageException(e.getMessage()); }
		if (!check) return s;
		return new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), s.buildings(), Invariants.check(s, req.terrain()));
	}
}
