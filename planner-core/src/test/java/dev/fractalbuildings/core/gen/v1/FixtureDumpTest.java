package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.SyntheticTerrain;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Writes every {@link TestMassings} fixture through the current pipeline as a viewer batch
 * ({@code seed-N.json} + {@code index.json}, N = fixture number in {@link TestMassings#all} order), so
 * pass owners can look at their pass on every massing shape before the massing step lands:
 * {@code ./gradlew :planner-core:test --tests '*FixtureDumpTest*' -Dfb.dumpFixtures=$PWD/viewer/gallery-data/gen/fixtures-medieval -Dfb.dumpStyle=medieval}
 * then {@code gallery.html?dir=fixtures-medieval} under {@code npm run dev:gen} (or the gallery capture).
 */
class FixtureDumpTest {
	@Test
	@EnabledIfSystemProperty(named = "fb.dumpFixtures", matches = ".+")
	void dump() throws Exception {
		Path dir = Path.of(System.getProperty("fb.dumpFixtures"));
		String style = System.getProperty("fb.dumpStyle", "medieval");
		Files.createDirectories(dir);
		List<String> seeds = new ArrayList<>();
		int i = 0;
		for (Map.Entry<String, Massing> e : TestMassings.all().entrySet()) {
			SceneDoc s = TestMassings.scene(e.getValue(), i, style, SyntheticTerrain.plane(64, 1, 0, 4));
			s = new SceneDoc(s.genVersion(), i, s.terrain(), s.lots(), s.buildings(), Invariants.check(s));
			Files.writeString(dir.resolve("seed-" + i + ".json"), IrJson.write(s));
			seeds.add(Integer.toString(i));
			i++;
		}
		Files.writeString(dir.resolve("index.json"), "{\"type\": \"fixture\", \"style\": \"" + style + "\", \"names\": \""
				+ String.join(",", TestMassings.all().keySet()) + "\", \"seeds\": [" + String.join(", ", seeds) + "]}\n");
	}
}
