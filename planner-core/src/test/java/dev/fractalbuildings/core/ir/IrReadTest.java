package dev.fractalbuildings.core.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class IrReadTest {
	private static String fixture() throws IOException {
		return Files.readString(Path.of("../viewer/fixtures/sample-house.json"), StandardCharsets.UTF_8);
	}

	private static String msg(String json) {
		return assertThrows(IllegalArgumentException.class, () -> IrJson.read(json)).getMessage();
	}

	private static String mutate(String from, String to) throws IOException {
		String f = fixture();
		assertTrue(f.contains(from), "fixture lacks " + from);
		return f.replace(from, to);
	}

	@Test
	void fixtureRoundTripsByteForByte() throws IOException {
		String json = fixture();
		SceneDoc d = IrJson.read(json);
		assertEquals(json, IrJson.write(d));
		assertEquals(SampleScenes.house(), d);
		assertEquals(List.of(), IrValidator.validate(d));
	}

	@Test
	void sceneWithViolationsRoundTrips() {
		SceneDoc h = SampleScenes.house();
		List<Violation> v = List.of(
				new Violation("rooms-reachable", "b0", "room r1 unreachable", List.of(Box3.of(101, 65, -39, 2, 3, 3), Box3.of(1, 2, 3, 1, 1, 1))),
				new Violation("lot-bounds", null, "scene \"level\"\nthing", List.of()));
		SceneDoc d = new SceneDoc(h.genVersion(), Long.MIN_VALUE, null, h.lots(), h.buildings(), v);
		String json = IrJson.write(d);
		SceneDoc r = IrJson.read(json);
		assertEquals(d, r);
		assertEquals(json, IrJson.write(r));
		assertEquals(Long.MIN_VALUE, r.seed());
	}

	@Test
	void seedAbove2Pow53IsExact() {
		SceneDoc h = SampleScenes.house();
		SceneDoc d = new SceneDoc(1, 9007199254740993L, h.terrain(), h.lots(), h.buildings(), List.of());
		assertEquals(9007199254740993L, IrJson.read(IrJson.write(d)).seed());
	}

	@Test
	void rejectsUnknownFormat() throws IOException {
		assertTrue(msg(mutate("fb-scene/1", "fb-scene/2")).contains("$.format"));
	}

	@Test
	void rejectsMissingField() throws IOException {
		String m = msg(mutate("\"rotation\": 0,", ""));
		assertTrue(m.contains("$.buildings[0].rotation") && m.contains("missing"), m);
	}

	@Test
	void rejectsNonIntegerNumber() throws IOException {
		String m = msg(mutate("\"rotation\": 0", "\"rotation\": 1.5"));
		assertTrue(m.contains("$.buildings[0].rotation") && m.contains("integer"), m);
		m = msg(mutate("\"width\": 7", "\"width\": 7.0"));
		assertTrue(m.contains("$.buildings[0].shell.spec.width"), m);
	}

	@Test
	void rejectsNumericSeed() throws IOException {
		String m = msg(mutate("\"seed\": \"42\"", "\"seed\": 42"));
		assertTrue(m.contains("$.seed") && m.contains("string"), m);
	}

	@Test
	void rejectsBadEnumAndNestedPath() throws IOException {
		String m = msg(mutate("\"kind\": \"door\"", "\"kind\": \"gate\""));
		assertTrue(m.contains("$.buildings[0].shell.openings[0].kind"), m);
	}

	@Test
	void rejectsSpecHashMismatch() throws IOException {
		String m = msg(mutate("\"width\": 7, \"depth\": 5", "\"width\": 8, \"depth\": 5"));
		assertTrue(m.contains("specHash"), m);
	}

	@Test
	void constructorChecksCarryPath() throws IOException {
		String m = msg(mutate("\"size\": [7, 8, 5]}", "\"size\": [7, 0, 5]}"));
		assertTrue(m.contains("$.buildings[0].shell.bounds"), m);
	}

	@Test
	void syntaxErrorsPropagate() {
		assertTrue(msg("{\"format\": ").contains("offset"));
	}
}
