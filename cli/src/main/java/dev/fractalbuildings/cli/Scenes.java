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

	/**
	 * With a street brief: the lot has air rights of {@code air} blocks over the street in front (0: no brief), the street surface being
	 * the ground in the column just outside the middle of the front edge. Storeys: two or more (a street with air rights is an urban one), within the type's range.
	 */
	static GenRequest request(long seed, int[] lotWd, int[] at, Facing front, String type, String style, ProbeSpecs.Probes p, int air) {
		if (air <= 0) return request(seed, lotWd, at, front, type, style, p);
		Lot lot = new Lot("lot0", at[0], at[1], lotWd[0], lotWd[1], front);
		int cx = lot.x() + lot.w() / 2, cz = lot.z() + lot.d() / 2;
		int fx = front.dx == 0 ? cx : front.dx < 0 ? lot.x() - 1 : lot.x() + lot.w(), fz = front.dz == 0 ? cz : front.dz < 0 ? lot.z() - 1 : lot.z() + lot.d();
		dev.fractalbuildings.core.gen.Brief brief = new dev.fractalbuildings.core.gen.Brief(2, 99, 1, air, p.terrain().floorY(fx, fz));
		return new GenRequest(seed, lot, type, style, p.terrain(), p.biome(), brief);
	}

	/** As the street-brief overload, and with {@code square} the lot is the one on its square that may carry the market hall (7.17 S4; a shop on level ground). */
	static GenRequest request(long seed, int[] lotWd, int[] at, Facing front, String type, String style, ProbeSpecs.Probes p, int air, boolean square) {
		if (!square) return request(seed, lotWd, at, front, type, style, p, air);
		GenRequest base = request(seed, lotWd, at, front, type, style, p, air);
		dev.fractalbuildings.core.gen.Brief b = base.brief();
		dev.fractalbuildings.core.gen.Brief brief = b == null ? new dev.fractalbuildings.core.gen.Brief(1, 99, 1, 0, dev.fractalbuildings.core.gen.Brief.NO_FRONT_Y, null, true)
				: new dev.fractalbuildings.core.gen.Brief(b.minStoreys(), b.maxStoreys(), b.streets(), b.airRights(), b.frontY(), b.walk(), true);
		return new GenRequest(seed, base.lot(), type, style, p.terrain(), p.biome(), brief);
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
