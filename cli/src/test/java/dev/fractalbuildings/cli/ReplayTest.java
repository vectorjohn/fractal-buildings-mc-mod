package dev.fractalbuildings.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.gen.v1.Types;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.rand.Draws;
import dev.fractalbuildings.core.rand.Hash64;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The replay contract of the property tests (Phase 3 known issue): a failing case prints a
 * {@code fractal-cli gen ... --check} command made only of flags the CLI takes (type, seed, lot,
 * {@code --at}, {@code --front}, style, {@code --terrain}); run through {@link Main#run} it must give
 * exactly the scene the core generates for the same parameters, violations embedded. The commands
 * here have the shape {@code V1Case.replay()} prints (the case drawing is mirrored, since test
 * classes of planner-core aren't visible to this module).
 */
class ReplayTest {
	/** Recorded terrain: the replay command is run from the repo root, this test from cli/. */
	private static final String CROP_FROM_ROOT = "recorded:fixtures/terrain/s4-12345-crop32.json";

	private record Case(String type, long seed, int w, int d, int x, int z, Facing front, String style, String terrain) {
		String replay() {
			return "fractal-cli gen --type " + type + " --seed " + seed + " --lot " + w + "x" + d + " --at " + x + "," + z
					+ " --front " + front.json() + " --style " + style + " --terrain " + terrain + " --check";
		}
	}

	private static List<Case> cases(int n) {
		List<BuildingType> types = Types.all();
		List<String> styles = new ArrayList<>(StyleLibrary.ids());
		styles.add(StyleLibrary.AUTO);
		List<Case> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Draws r = new Draws(Hash64.hash(i, "replay-test"));
			BuildingType t = types.get(i % types.size());
			int[] lot = t.limits().testLots().get(r.below(t.limits().testLots().size()));
			String terrain = switch (i % 5) {
				case 0 -> "flat:" + r.range(-50, 250);
				case 1 -> "plane:" + r.range(40, 200) + ":1:0:2";
				case 2 -> "hill:" + r.range(40, 120) + ":" + r.range(4, 30) + ":" + r.range(30, 60);
				case 3 -> "noise:" + i + ":" + r.range(40, 150) + ":3:12";
				default -> CROP_FROM_ROOT;
			};
			if (t.limits().maxGrade() > 3 && !terrain.startsWith("flat:")) terrain = "flat:64"; // gentle-ground types reject steep lots
			int x = r.range(-60, 60), z = r.range(-60, 60);
			if (terrain.startsWith("recorded:")) { x = -700 + r.range(-40, 40); z = -3500 + r.range(-40, 40); }
			Facing front = Facing.values()[r.below(4)];
			int[] dims = t.limits().oriented(lot, front); // a long thin type turns its lot to fit the front
			out.add(new Case(t.id(), i * 7L, dims[0], dims[1], x, z, front, styles.get(r.below(styles.size())), terrain));
		}
		return out;
	}

	private static String[] argv(Case c) {
		String cmd = c.replay();
		String[] a = cmd.substring("fractal-cli ".length()).split(" ");
		for (int i = 0; i < a.length; i++) if (a[i].startsWith("recorded:fixtures/")) a[i] = "recorded:../" + a[i].substring("recorded:".length());
		return a;
	}

	private static String sha(String s) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
	}

	private static String core(Case c) {
		String terrain = c.terrain().startsWith("recorded:fixtures/") ? "recorded:../" + c.terrain().substring("recorded:".length()) : c.terrain();
		ProbeSpecs.Probes p = ProbeSpecs.parse(terrain);
		GenRequest rq = new GenRequest(c.seed(), new Lot("lot0", c.x(), c.z(), c.w(), c.d(), c.front()), c.type(), c.style(), p.terrain(), p.biome());
		SceneDoc s = new GeneratorV1().generate(rq);
		return IrJson.write(new SceneDoc(s.genVersion(), s.seed(), s.terrain(), s.lots(), s.buildings(), Invariants.check(s, rq.terrain())));
	}

	private static String[] run(String... args) {
		PrintStream so = System.out, se = System.err;
		ByteArrayOutputStream out = new ByteArrayOutputStream(), err = new ByteArrayOutputStream();
		System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
		try {
			int code = Main.run(args);
			return new String[] {Integer.toString(code), out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8)};
		} finally {
			System.setOut(so);
			System.setErr(se);
		}
	}

	@Test
	void replayCommandsReproduceTheCoreScene() throws Exception {
		List<Case> cs = cases(20);
		for (Case c : cs) {
			String[] r = run(argv(c));
			assertEquals("0", r[0], c.replay() + "\n" + r[2]);
			assertEquals(sha(core(c)), sha(r[1]), "digest differs for " + c.replay());
		}
	}

	@Test
	void theReplayCommandCarriesEveryFlagTheCaseNeeds() {
		Case c = cases(5).get(4); // recorded terrain at a lot position away from the dump's centre
		String cmd = c.replay();
		for (String flag : new String[] {"--type", "--seed", "--lot", "--at", "--front", "--style", "--terrain", "--check"}) assertTrue(cmd.contains(flag), flag);
		// dropping --at changes the scene (the position is part of the case, so the flag is not decoration)
		String[] full = argv(c);
		List<String> noAt = new ArrayList<>(List.of(full));
		int i = noAt.indexOf("--at");
		noAt.remove(i);
		noAt.remove(i);
		assertTrue(!run(full)[1].equals(run(noAt.toArray(new String[0]))[1]));
	}

	@Test
	void aLotWithNoRoomUnderTheCeilingFailsCleanly() {
		String[] r = run("gen", "--seed", "1", "--lot", "12x10", "--terrain", "flat:316", "--check");
		assertEquals("2", r[0]);
		assertTrue(r[2].contains("ceiling"), r[2]);
		assertEquals("0", run("gen", "--seed", "1", "--lot", "12x10", "--terrain", "flat:300", "--check")[0]);
	}
}
