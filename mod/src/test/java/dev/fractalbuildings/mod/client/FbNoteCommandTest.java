package dev.fractalbuildings.mod.client;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;

/** FBNOTE: with the flag off the command is not registered; with it on, {@code /fbnote} takes the rest of the line as the comment. */
class FbNoteCommandTest {
	@Test
	void absentWhenOff() {
		CommandDispatcher<FabricClientCommandSource> d = new CommandDispatcher<>();
		FbNoteClient.register(d, false);
		assertNull(d.getRoot().getChild("fbnote"));
		assertFalse(d.getRoot().getChildren().stream().anyMatch(n -> n.getName().contains("fbnote")));
	}

	@Test
	void presentWhenOnAndTakesTheWholeLine() throws Exception {
		CommandDispatcher<FabricClientCommandSource> d = new CommandDispatcher<>();
		FbNoteClient.register(d, true);
		assertNotNull(d.getRoot().getChild("fbnote"));
		ParseResults<FabricClientCommandSource> p = d.parse("fbnote stairs don't go to top, 2721 67 2696", null);
		assertTrue(p.getReader().canRead() == false && p.getExceptions().isEmpty(), "parsed fully");
		assertEquals("stairs don't go to top, 2721 67 2696", p.getContext().getArguments().get("comment").getResult());
		// a bare /fbnote has no executable node
		assertNull(d.parse("fbnote", null).getContext().getCommand());
	}

	@Test
	void parsesSeedReply() {
		assertEquals(987654321L, FbNoteClient.parseSeed("Seed: [987654321]"));
		assertEquals(-4172144997902289642L, FbNoteClient.parseSeed("Seed: [-4172144997902289642]"));
		assertEquals(0L, FbNoteClient.parseSeed("Startwert: [0]"));
		assertNull(FbNoteClient.parseSeed("Unknown or incomplete command"));
		assertNull(FbNoteClient.parseSeed("Seed: [99999999999999999999]"));
		assertNull(FbNoteClient.parseSeed(null));
	}
}
