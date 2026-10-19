package dev.fractalbuildings.core.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** genVersion 2 keys: spec maxHeight/streets and shell hooks round-trip; v1 specs keep their old bytes. */
class HookIrTest {
	static SceneDoc withHooks(int gv, List<Hook> hooks) {
		SceneDoc s = SampleScenes.house();
		PlacedBuilding b = s.buildings().get(0);
		BuildingIr sh = b.shell();
		BuildingSpec o = sh.spec();
		BuildingSpec spec = new BuildingSpec(gv, o.specSeed(), o.style(), o.type(), o.width(), o.depth(), o.storeys(), o.roof(), 200, 5);
		BuildingIr shell = new BuildingIr(spec, sh.bounds(), sh.storeys(), sh.volumes(), sh.rooms(), sh.openings(), sh.roofs(), sh.boxes(), hooks);
		PlacedBuilding pb = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), shell, b.siteBoxes());
		return new SceneDoc(gv, s.seed(), s.terrain(), s.lots(), List.of(pb), s.violations());
	}

	@Test
	void v2KeysRoundTrip() {
		SceneDoc d = withHooks(2, List.of(new Hook("h0", "bed", "r0", 1, 1, 1, Facing.SOUTH)));
		String json = IrJson.write(d);
		assertTrue(json.contains("\"maxHeight\": 200, \"streets\": 5"), json);
		assertTrue(json.contains("{\"id\": \"h0\", \"kind\": \"bed\", \"room\": \"r0\", \"pos\": [1, 1, 1], \"facing\": \"south\"}"), json);
		assertEquals(d, IrJson.read(json));
		assertEquals(List.of(), IrValidator.validate(d));
	}

	@Test
	void hookMustNameARoomInsideBounds() {
		SceneDoc d = withHooks(2, List.of(new Hook("h0", "bed", "r9", 1, 1, 1, Facing.SOUTH), new Hook("h1", "bed", "r0", 99, 1, 1, Facing.SOUTH)));
		List<Violation> v = IrValidator.validate(d);
		assertEquals(2, v.size(), v.toString());
	}

	@Test
	void genVersion1WritesNoNewKeys() {
		String json = IrJson.write(SampleScenes.house());
		assertFalse(json.contains("maxHeight"));
		assertFalse(json.contains("hooks"));
		assertTrue(new BuildingSpec(2, 1, "plain", "house", 5, 5, 1, "auto", 100, 1).street(Facing.NORTH));
		assertFalse(new BuildingSpec(2, 1, "plain", "house", 5, 5, 1, "auto", 100, 1).street(Facing.EAST));
	}
}
