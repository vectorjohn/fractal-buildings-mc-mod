package dev.fractalbuildings.core.json;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonReaderTest {
	@Test
	void nestingAndTypes() {
		Object o = JsonReader.parse(" {\"a\":[1,-2,3.5,1e2,-1.5E-1,true,false,null],\"b\":{\"c\":{}},\"d\":[]} ");
		Map<?, ?> m = (Map<?, ?>) o;
		assertEquals(List.of("a", "b", "d"), List.copyOf(m.keySet()));
		List<?> a = (List<?>) m.get("a");
		assertEquals(1L, a.get(0));
		assertEquals(-2L, a.get(1));
		assertEquals(3.5, a.get(2));
		assertEquals(100.0, a.get(3));
		assertEquals(-0.15, (Double) a.get(4), 1e-12);
		assertEquals(Boolean.TRUE, a.get(5));
		assertEquals(Boolean.FALSE, a.get(6));
		assertNull(a.get(7));
		assertTrue(((Map<?, ?>) ((Map<?, ?>) m.get("b")).get("c")).isEmpty());
	}

	@Test
	void escapes() {
		assertEquals("a\"b\\c/d\n\t\u00e9\u263a", JsonReader.parse("\"a\\\"b\\\\c\\/d\\n\\t\\u00e9\\u263A\""));
	}

	@Test
	void errorsCarryOffset() {
		assertTrue(msg("[1,2").contains("offset 4"));
		assertTrue(msg("{\"a\" 1}").contains("offset 5"));
		assertTrue(msg("[1] x").contains("offset 4"));
		assertTrue(msg("\"abc").contains("unterminated"));
		assertTrue(msg("\"\\q\"").contains("offset 2"));
		assertTrue(msg("01x").contains("offset 1"));
		assertTrue(msg("[1.]").contains("offset 3"));
		assertTrue(msg("\"\\u12g4\"").contains("offset 5"));
		assertTrue(msg("tru").contains("offset 0"));
	}

	private static String msg(String s) {
		return assertThrows(IllegalArgumentException.class, () -> JsonReader.parse(s)).getMessage();
	}
}
