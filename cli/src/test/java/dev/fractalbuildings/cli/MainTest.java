package dev.fractalbuildings.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MainTest {
	@Test
	void parsesGenOptions() {
		var opts = Main.parseOptions(new String[] {"gen", "--seed", "42", "--lot", "20x20", "--style", "medieval"});
		assertEquals("42", opts.get("seed"));
		assertEquals("20x20", opts.get("lot"));
		assertEquals("medieval", opts.get("style"));
	}

	@Test
	void unknownCommandFails() {
		assertEquals(2, Main.run(new String[] {"nope"}));
	}
}
