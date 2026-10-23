package dev.fractalbuildings.core.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Role;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VariantTest {
	@Test
	void cellHashMatchesTheViewerRows() throws Exception {
		int n = 0;
		try (var in = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/raster/cellhash.csv"), StandardCharsets.UTF_8))) {
			in.readLine();
			for (String l; (l = in.readLine()) != null; ) {
				String[] p = l.split(",");
				assertEquals(Long.parseLong(p[3]), Variant.cellHash(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])), l);
				n++;
			}
		}
		assertEquals(50, n);
	}

	@Test
	void picksFromThePaletteAndUsesEveryEntry() {
		Set<String> seen = new HashSet<>();
		for (int x = 0; x < 30; x++) for (int z = 0; z < 5; z++) seen.add(Variant.pick("medieval", Role.WALL, x, 3, z));
		assertEquals(Set.of("minecraft:white_terracotta", "minecraft:mushroom_stem", "minecraft:smooth_sandstone"), seen);
		assertEquals(Variant.pick("medieval", Role.ROOF, 4, 5, 6), Variant.pick("medieval", Role.ROOF, 4, 5, 6));
		assertEquals("minecraft:air", Variant.pick("medieval", Role.AIR, 0, 0, 0));
		assertTrue(Variant.pick("no-such-style", Role.WALL, 0, 0, 0).startsWith("minecraft:"));
		for (Role r : Role.values()) assertTrue(Variant.pick("medieval", r, 1, 2, 3).startsWith("minecraft:"), r.toString());
	}
}
