package dev.fractalbuildings.core.world;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.json.JsonReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** CFGEXAMPLE: the commented file the mod writes on first start stays in step with {@link FbSettings}. */
class FbSettingsExampleTest {
	/** The player-facing example, read from the repo (tests run in planner-core/). */
	static String exampleText() {
		try {
			return java.nio.file.Files.readString(java.nio.file.Path.of("../docs/release/fractalbuildings.example.json"));
		} catch (java.io.IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> obj(String json) { return (Map<String, Object>) JsonReader.parse(json); }

	@Test
	void exampleParsesToDefaultsWithoutWarnings() {
		List<String> w = new ArrayList<>();
		assertEquals(FbSettings.defaults(), FbSettings.parse(exampleText(), w::add));
		assertEquals(List.of(), w);
	}

	@Test
	void exampleHasExactlyTheKeysToJsonWritesEachWithANote() {
		Map<String, Object> ex = obj(exampleText());
		TreeSet<String> real = new TreeSet<>(), noted = new TreeSet<>(), keys = new TreeSet<>();
		for (String k : obj(FbSettings.defaults().toJson()).keySet()) real.add(k);
		for (String k : ex.keySet()) {
			if (k.startsWith("// ")) noted.add(k.substring(3));
			else keys.add(k);
		}
		assertEquals(real, keys, "example keys vs toJson keys");
		noted.remove("about");
		assertEquals(real, noted, "every key needs a '// <key>' note");
		for (String k : real) assertFalse(((String) ex.get("// " + k)).isBlank(), k);
	}

	@Test
	void styleNoteListsEveryStyle() {
		String note = (String) obj(exampleText()).get("// styles");
		String list = note.substring(note.indexOf("Styles:") + 7);
		TreeSet<String> listed = new TreeSet<>();
		for (String t : list.split("[ ,.]+")) if (!t.isEmpty() && !t.equals("and") && !t.equals("the") && !t.equals("variants")) listed.add(t);
		assertEquals(new TreeSet<>(StyleLibrary.ids()), listed);
	}
}
