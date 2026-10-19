package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Faces;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.ir.Facing;
import java.util.List;
import org.junit.jupiter.api.Test;

class FacesTest {
	static final Massing L = TestMassings.all().get("L");

	static String runs(List<Face> fs) {
		StringBuilder sb = new StringBuilder();
		for (Face f : fs) sb.append('[').append(f.a()).append(',').append(f.b()).append(')').append(f.convexA() ? 'c' : 'j').append(f.convexB() ? 'c' : 'j');
		return sb.toString();
	}

	@Test
	void sharedStripIsNotAFaceAndRunsSplitAtTheJunction() {
		// main0 [2,14)x[2,10) two storeys; wing1 [2,8)x[9,18) one storey; strip x 2..7 at z 9
		assertEquals("[8,14)jc", runs(Faces.of(L, "main0", 0, Facing.SOUTH)));
		assertEquals("[2,14)cc", runs(Faces.of(L, "main0", 1, Facing.SOUTH)), "storey 1: the wing is not there");
		assertEquals("", runs(Faces.of(L, "wing1", 0, Facing.NORTH)), "wing front is the shared strip");
		assertEquals("[9,18)cc", runs(Faces.of(L, "wing1", 0, Facing.WEST)));
		assertEquals("[2,10)cc", runs(Faces.of(L, "main0", 0, Facing.WEST)));
	}

	@Test
	void faceGeometry() {
		Face f = Faces.of(L, "main0", 1, Facing.SOUTH).get(0);
		assertEquals(9, f.plane());
		assertEquals(5, f.y0());
		assertEquals(3, f.height());
		assertEquals(10, f.outsideZ(4));
		assertEquals(8, f.insideZ(4));
		assertEquals(4, f.x(4));
		Face w = Faces.of(L, "wing1", 0, Facing.WEST).get(0);
		assertEquals(1, w.outsideX(12));
		assertEquals(3, w.insideX(12));
		assertEquals(12, w.z(12));
	}

	@Test
	void courtyardInnerFacesAreExterior() {
		Massing c = TestMassings.all().get("courtyard");
		assertEquals("[8,16)jj", runs(Faces.of(c, "main0", 0, Facing.SOUTH)), "courtyard side of the front range");
		assertEquals("[8,16)jj", runs(Faces.of(c, "wing3", 1, Facing.NORTH)));
	}

	@Test
	void openMassRowsAndJetties() {
		Massing h = TestMassings.all().get("hall");
		Face lower = Faces.of(h, "hall0", 0, Facing.NORTH).get(0), upper = Faces.of(h, "hall0", 1, Facing.NORTH).get(0);
		assertEquals(1, lower.y0());
		assertEquals(4, upper.y0(), "an open mass has no slab between its rows");
		assertEquals(3, lower.height());
		assertEquals(4, upper.height());
		Massing j = TestMassings.all().get("jetty");
		Face ground = Faces.of(j, "main0", 0, Facing.NORTH).get(0), over = Faces.of(j, "jetty1", 1, Facing.NORTH).get(0);
		assertEquals(4, ground.plane());
		assertEquals(3, over.plane(), "the jetty's wall stands one block further out");
		assertTrue(ground.convexA() && ground.convexB());
		assertFalse(Faces.of(j).isEmpty());
	}
}
