package dev.fractalbuildings.core.ir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class PlacementTest {
	private static final int SX = 5, SZ = 3, OX = 100, OZ = -40;

	@Test
	void roundTripsAndStaysInPositiveQuadrant() {
		for (int r = 0; r < 4; r++) {
			Placement p = new Placement(OX, 64, OZ, r, SX, SZ);
			int wsx = r % 2 == 0 ? SX : SZ, wsz = r % 2 == 0 ? SZ : SX;
			TreeSet<Long> seen = new TreeSet<>();
			for (int lx = 0; lx < SX; lx++) {
				for (int lz = 0; lz < SZ; lz++) {
					int wx = p.worldX(lx, lz), wz = p.worldZ(lx, lz);
					assertTrue(wx >= OX && wx < OX + wsx && wz >= OZ && wz < OZ + wsz, "r=" + r);
					assertEquals(lx, p.localX(wx, wz), "r=" + r);
					assertEquals(lz, p.localZ(wx, wz), "r=" + r);
					seen.add(((long) wx << 32) ^ (wz & 0xFFFFFFFFL));
				}
			}
			assertEquals(SX * SZ, seen.size(), "bijective, r=" + r);
			assertEquals(new Box3(OX, 64, OZ, wsx, 1, wsz), p.toWorld(new Box3(0, 0, 0, SX, 1, SZ)), "r=" + r);
		}
	}

	@Test
	void frontRowFacesTheRotatedDirection() {
		// The local front row (lz = 0) must be the world edge on the side the front faces.
		for (int r = 0; r < 4; r++) {
			Placement p = new Placement(OX, 64, OZ, r, SX, SZ);
			Facing f = p.toWorld(Facing.NORTH);
			int wx = p.worldX(2, 0), wz = p.worldZ(2, 0);
			// One step further in the facing direction leaves the footprint.
			int ox = wx + f.dx, oz = wz + f.dz;
			int lx = p.localX(ox, oz), lz = p.localZ(ox, oz);
			assertTrue(lx < 0 || lx >= SX || lz < 0 || lz >= SZ, "r=" + r + " facing " + f);
			assertEquals(-1, lz, "r=" + r);
		}
		assertEquals(Facing.EAST, new Placement(0, 0, 0, 1, SX, SZ).toWorld(Facing.NORTH));
		assertEquals(Facing.WEST, new Placement(0, 0, 0, 3, SX, SZ).toWorld(Facing.NORTH));
	}
}
