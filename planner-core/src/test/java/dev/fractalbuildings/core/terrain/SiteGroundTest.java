package dev.fractalbuildings.core.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.check.Invariants;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.TerrainProbe;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class SiteGroundTest {
	private static TreeSet<String> ids(List<Violation> vs) {
		TreeSet<String> t = new TreeSet<>();
		for (Violation v : vs) t.add(v.invariant());
		return t;
	}

	@Test
	void aBuildingDugIntoAHillPassesEveryInvariant() {
		assertEquals(List.of(), Invariants.check(CutFixture.scene(true)).stream().map(v -> v.invariant() + ": " + v.message()).toList());
	}

	/** The rule itself, column by column (the mutation check: with the raw probe the first assertion below is what the invariants would see, and no-gap-below fires). */
	@Test
	void effectiveGroundPerColumn() {
		SceneDoc s = CutFixture.scene(true);
		PlacedBuilding b = s.buildings().get(0);
		TerrainProbe raw = (x, z) -> s.terrain().floorY(x, z);
		TerrainProbe wrapped = SiteGround.probe(b, raw);
		int fx = b.placement().worldX(2, 2), fz = b.placement().worldZ(2, 2);
		assertEquals(b.oy() + CutFixture.DEPTH, raw.floorY(fx, fz), "raw ground is 3 above the slab");
		assertEquals(b.oy(), wrapped.floorY(fx, fz), "footprint: the slab is the ground");
		int rx = b.placement().worldX(-1, 2), rz = b.placement().worldZ(-1, 2);
		assertEquals(b.oy(), wrapped.floorY(rx, rz), "ring: the paved cut floor");
		int ox = b.placement().worldX(-2, 2), oz = b.placement().worldZ(-2, 2);
		assertEquals(b.oy() + CutFixture.DEPTH, wrapped.floorY(ox, oz), "beyond the ring: natural ground");
	}

	/** Without the ring the footprint rule alone still makes the slab the ground, but the walls' windows and the door open into earth. */
	@Test
	void noRingStillPassesNoGapBelow() {
		TreeSet<String> got = ids(Invariants.check(CutFixture.scene(false)));
		assertTrue(!got.contains("no-gap-below") && !got.contains("no-floating"), got.toString());
	}
}
