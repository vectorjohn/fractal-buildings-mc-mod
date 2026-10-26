package dev.fractalbuildings.core.terrain;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SampleScenes;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.TerrainPatch;
import java.util.ArrayList;
import java.util.List;

/**
 * A building dug into a hill (shoplow S1): the sample house, its slab 3 below the surrounding ground, with a one-wide
 * ring around the footprint cut down to the slab (an {@code AIR} box from one below the slab to the surface, {@code PAVING}
 * at its bottom: the areaway). Shared by {@code SiteGroundTest} and, as JSON ({@code build/cut-fixture.json}), the viewer's
 * tests (S1v) and the cut-claim check (S5a).
 */
public final class CutFixture {
	private CutFixture() {}

	/** How far the surface stands above the slab. */
	public static final int DEPTH = 3;

	/** The scene; {@code ring} false leaves the site boxes empty (the building then stands in a pit with no light well). */
	public static SceneDoc scene(boolean ring) {
		SceneDoc s = SampleScenes.house();
		PlacedBuilding b = s.buildings().get(0);
		int surface = b.oy() + DEPTH;
		TerrainPatch t = s.terrain();
		int[] floorY = new int[t.nx() * t.nz()];
		java.util.Arrays.fill(floorY, surface);
		TerrainPatch flat = new TerrainPatch(t.x0(), t.z0(), t.nx(), t.nz(), floorY);
		List<Box> site = new ArrayList<>();
		if (ring) {
			int w = b.shell().bounds().maxX(), d = b.shell().bounds().maxZ(), h = DEPTH + 1;
			for (Box3 r : new Box3[] {Box3.of(-1, -1, -1, w + 2, h, 1), Box3.of(-1, -1, d, w + 2, h, 1), Box3.of(-1, -1, 0, 1, h, d), Box3.of(w, -1, 0, 1, h, d)}) {
				site.add(new Box(r, Role.AIR, Layer.FOUNDATION, Box.NO_STOREY));
				site.add(new Box(Box3.of(r.x(), -1, r.z(), r.sx(), 1, r.sz()), Role.PAVING, Layer.FOUNDATION, Box.NO_STOREY));
			}
		}
		PlacedBuilding nb = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), b.shell(), site);
		return new SceneDoc(s.genVersion(), s.seed(), flat, s.lots(), List.of(nb), List.of());
	}

	/** Writes the scene (with the ring) as IR JSON for the viewer. */
	public static void main(String[] args) throws Exception {
		java.nio.file.Files.writeString(java.nio.file.Path.of(args.length > 0 ? args[0] : "build/cut-fixture.json"), IrJson.write(scene(true)));
	}
}
