package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CityPlanner;
import dev.fractalbuildings.core.settlement.DistrictJson;
import dev.fractalbuildings.core.settlement.SettlementJson;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.SkeletonSource;
import dev.fractalbuildings.core.settlement.TerrainCrop;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Throwaway (opt-in, {@code -Dfb.mainstubout=DIR}): writes the plan of a settlement as the WORLD plans it (link rule included) to
 * {@code DIR/<label>-after/} and the same plan with no exit road cut to {@code DIR/<label>-before/}, in the layout of {@code fractal-cli city}
 * (settlement.json, districts/, terrain.json.gz) so the viewer's plan page and the city gallery can draw them.
 */
class MainStubExport {
	@Test
	void export() throws Exception {
		String out = System.getProperty("fb.mainstubout");
		Assumptions.assumeTrue(out != null, "opt-in");
		Path dir = Path.of(out);
		one(dir, "pt1573-s4_m2", JoinWorld::pt1573, new Cell(4, -2));
		Supplier<WorldPlanner> w12345 = () -> WorldPlannerRegionTest.planner(WorldConfig.defaults(), WorldPlanner.Caches.DEFAULT);
		one(dir, "w12345-s2_1", w12345, new Cell(2, 1));
		one(dir, "w12345-s6_6", w12345, new Cell(6, 6));
	}

	static void one(Path dir, String label, Supplier<WorldPlanner> mk, Cell cell) throws Exception {
		for (boolean after : new boolean[] {false, true}) {
			WorldPlanner w = mk.get();
			SettlementSite site = w.siteOf(cell).orElseThrow();
			SkeletonSource.Direct src = (SkeletonSource.Direct) w.source(site);
			if (!after) src.withExitServed((x, z) -> true); // nothing is cut
			var p = w.probes();
			var city = CityPlanner.plan(src, p.coarse(), p.exact(), p.biome(), GeneratorV1.GEN_VERSION);
			Path d = dir.resolve(label + (after ? "-after" : "-before"));
			Files.createDirectories(d.resolve("districts"));
			for (var e : city.districts().entrySet()) Files.writeString(d.resolve("districts/" + e.getKey() + ".json"), DistrictJson.write(e.getValue()));
			Files.writeString(d.resolve("settlement.json"), SettlementJson.write(city.plan()));
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			try (GZIPOutputStream gz = new GZIPOutputStream(bos)) { gz.write(TerrainCrop.toJson(w.seed(), city.plan().coarse()).getBytes(StandardCharsets.UTF_8)); }
			Files.write(d.resolve("terrain.json.gz"), bos.toByteArray());
			System.out.println("wrote " + d + " roads " + city.plan().roads().size() + " gates " + city.plan().gates().size() + " violations " + city.plan().violations().size());
		}
	}
}
