package dev.fractalbuildings.core.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JsonWriterTest {
	@Test
	void escaping() {
		assertEquals("\"a\\\"b\\\\c\\nd\\te\\u0001\"", JsonWriter.quote("a\"b\\c\nd\te\u0001"));
	}

	@Test
	void numbersAndLongsAsStrings() {
		assertEquals("{\"a\": -5, \"s\": \"-9223372036854775808\", \"t\": null}",
				JsonWriter.object().num("a", -5).str64("s", Long.MIN_VALUE).textOrNull("t", null).build());
		assertEquals("\"9223372036854775807\"", JsonWriter.str(Long.MAX_VALUE));
		assertEquals("[-1, 0, 2]", JsonWriter.ints(-1, 0, 2));
	}

	@Test
	void compactHasNoSpaces() {
		assertEquals("{\"a\":1,\"b\":\"x\"}", JsonWriter.compactObject().num("a", 1).text("b", "x").build());
	}

	@Test
	void keysKeepCallOrder() {
		assertEquals("{\"z\": 1, \"a\": 2}", JsonWriter.object().num("z", 1).num("a", 2).build());
	}
}
