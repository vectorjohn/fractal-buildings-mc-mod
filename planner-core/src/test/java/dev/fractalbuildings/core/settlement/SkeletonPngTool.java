package dev.fractalbuildings.core.settlement;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Evidence tool: {@code env FB_PNG="IN.json;OUT.png;SCALE,..."} draws settlement plans (skipped otherwise). */
class SkeletonPngTool {
	@Test
	void draw() throws Exception {
		String spec = System.getenv("FB_PNG");
		Assumptions.assumeTrue(spec != null && !spec.isEmpty());
		for (String one : spec.split(",")) {
			String[] f = one.split(";");
			SettlementPlan p = SettlementJson.read(Files.readString(Path.of(f[0])));
			SkeletonPng.write(p, new File(f[1]), Integer.parseInt(f[2]));
		}
	}
}
