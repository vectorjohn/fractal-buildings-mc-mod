package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Roof;
import dev.fractalbuildings.core.ir.SceneDoc;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The jungle style (4.7): vanilla ids, the jungle-family biomes, hip roofs with a 2-block overhang. */
class JungleStyleTest {
	static Set<String> lines(String resource) throws IOException {
		try (var in = JungleStyleTest.class.getResourceAsStream("/" + resource)) {
			return new HashSet<>(List.of(new String(in.readAllBytes()).split("\n")));
		}
	}

	@Test
	void idsAreVanillaAndBiomesAreTheJungleFamily() throws IOException {
		Set<String> blocks = lines("vanilla-26.3-blocks.txt"), biomes = lines("vanilla-26.3-biomes.txt");
		StyleRules j = StyleLibrary.get("jungle");
		j.palette().forEach((role, ids) -> {
			assertTrue(ids.size() >= 1 && ids.size() <= 5, role);
			for (String b : ids) assertTrue(blocks.contains(b), role + ": unknown block " + b);
		});
		for (String role : List.of("foundation", "floor", "wall", "trim", "beam")) // the roof is one block (DS36ROOFS r2: a per-cell mix read as speckle)
			assertTrue(j.palette().get(role).size() >= 3, "gradient for " + role);
		for (String b : j.biomes()) assertTrue(biomes.contains(b), "unknown biome " + b);
		for (String b : List.of("minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle"))
			assertEquals("jungle", StyleLibrary.forBiome(b).id(), b);
		// no biome is claimed by another style as well
		for (String id : StyleLibrary.ids())
			if (!id.equals("jungle")) for (String b : StyleLibrary.get(id).biomes()) assertTrue(!j.biomes().contains(b), b + " also in " + id);
	}

	/** Chebyshev distance of the farthest roof column that is chained to the walls by roof columns, beyond the wall columns. */
	static int overhangOf(PlacedBuilding b) {
		var bo = b.shell().bounds();
		int w = bo.sx(), d = bo.sz();
		boolean[][] wall = new boolean[w][d], roof = new boolean[w][d];
		for (Box x : b.shell().boxes()) {
			if (x.role() == Role.AIR) continue;
			boolean r = x.layer() == Layer.ROOF && x.role().isRoof();
			var q = x.box();
			for (int i = q.x(); i < q.maxX(); i++)
				for (int k = q.z(); k < q.maxZ(); k++) {
					int xi = i - bo.x(), zk = k - bo.z();
					if (xi < 0 || zk < 0 || xi >= w || zk >= d) continue;
					if (x.layer() == Layer.SHELL || x.layer() == Layer.FOUNDATION) wall[xi][zk] = true;
					else if (r) roof[xi][zk] = true;
				}
		}
		int best = 0;
		for (int x = 0; x < w; x++)
			for (int z = 0; z < d; z++) {
				if (wall[x][z] || !roof[x][z]) continue;
				int dist = Integer.MAX_VALUE;
				for (int i = 0; i < w; i++) for (int k = 0; k < d; k++) if (wall[i][k]) dist = Math.min(dist, Math.max(Math.abs(i - x), Math.abs(k - z)));
				if (dist != Integer.MAX_VALUE) best = Math.max(best, dist);
			}
		return best;
	}

	@Test
	void roofsAreHipAndOverhangTwoOnMostBuildings() {
		int roofs = 0, hip = 0, buildings = 0, deep = 0;
		for (long seed = 0; seed < 150; seed++) {
			V1Case c = new V1Case(seed, "house", "jungle", 20, 20, 0, 0, Facing.NORTH, "flat:64");
			GenRequest rq = c.request();
			SceneDoc sc = new GeneratorV1().generate(rq);
			assertEquals(List.of(), Invariants.check(sc, rq.terrain()), c.replay());
			for (PlacedBuilding b : sc.buildings()) {
				boolean pitched = false;
				for (Roof r : b.shell().roofs()) {
					roofs++;
					if (r.form().equals("hip")) hip++;
					if (!r.form().equals("flat")) pitched = true;
				}
				if (!pitched) continue;
				buildings++;
				if (overhangOf(b) >= 2) deep++;
			}
		}
		assertTrue(buildings > 100, "pitched buildings " + buildings);
		assertTrue(deep >= 0.8 * buildings, "overhang 2 on " + deep + "/" + buildings);
		assertTrue(hip >= 0.5 * roofs, "hip roofs " + hip + "/" + roofs);
	}
}
