package dev.fractalbuildings.core.gen.v1.roof;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** DS-36 static style test: every {@code roof} palette entry of a style that lays pitched roofs has stairs and a slab (docs/design/blocks.md). */
class RoofPaletteTest {
	static final Path BLOCKS = Files.exists(Path.of("../docs/design/blocks.md")) ? Path.of("../docs/design/blocks.md") : Path.of("docs/design/blocks.md");

	/** Base block ids (no namespace) that have both a stair and a slab, read from the generated block reference. */
	static Set<String> withStairsAndSlab() throws IOException {
		Set<String> ok = new HashSet<>();
		for (String line : Files.readAllLines(BLOCKS)) {
			if (!line.startsWith("| ")) continue;
			String[] cells = line.split("\\|");
			if (cells.length < 3) continue;
			for (String entry : cells[2].split(";")) {
				String e = entry.trim();
				int sp = e.indexOf(' ');
				int open = e.indexOf('(');
				if (sp < 0 || open < 0) continue;
				String vars = e.substring(open + 1, e.lastIndexOf(')'));
				boolean woodSet = vars.contains("wood set") && !vars.contains("-stairs") && !vars.contains("-slab");
				if (woodSet || (vars.contains("stairs") && vars.contains("slab"))) ok.add(e.substring(0, sp));
			}
		}
		return ok;
	}

	static boolean pitched(StyleRules s) {
		boolean form = false;
		for (var f : s.roof().forms().entrySet()) if (f.getValue() > 0 && !f.getKey().equals("flat")) form = true;
		return form && s.roof().rise() > 0;
	}

	@Test
	void everyPitchedRoofMaterialHasStairsAndASlab() throws IOException {
		Set<String> ok = withStairsAndSlab();
		assertTrue(ok.contains("bricks") && ok.contains("spruce_planks"), "the block reference parsed: " + ok.size());
		assertFalse(ok.contains("terracotta") || ok.contains("moss_block"), "terracotta and moss have neither");
		List<String> bad = new ArrayList<>();
		int styles = 0;
		for (String id : StyleLibrary.ids()) {
			StyleRules s = StyleLibrary.get(id);
			if (!pitched(s)) continue;
			styles++;
			for (String block : s.palette().get("roof")) if (!ok.contains(block.replace("minecraft:", ""))) bad.add(id + ": " + block);
		}
		assertTrue(styles >= 5, "styles with pitched roofs: " + styles);
		assertTrue(bad.isEmpty(), "roof materials with no stairs or no slab: " + bad);
	}
}
