package dev.fractalbuildings.mod.teleport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link PortalSearch#chunks} against a re-run of the loops of vanilla's {@code PortalForcer.createPortal} (26.3, lines 52-153): every block
 * column the spiral visits, every block {@code canHostFrame} reads at a column (width -1..2 along the portal direction, offset -1..1 across),
 * and the neighbours of every frame block it may write. The chunk box of those blocks must equal the formula's, for every block offset in
 * a chunk and both portal axes.
 */
class PortalSearchTest {
	/** The chunk box of all blocks vanilla may read or update for an exit at (x, z): {minCx, maxCx, minCz, maxCz}. */
	private static int[] vanilla(int x, int z, boolean axisX) {
		int dirX = axisX ? 1 : 0, dirZ = axisX ? 0 : 1; // Direction.get(POSITIVE, axis)
		int cwX = axisX ? 0 : -1, cwZ = axisX ? 1 : 0; // EAST.getClockWise() = SOUTH, SOUTH.getClockWise() = WEST
		int[] box = {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE};
		// BlockPos.spiralAround(origin, 16, EAST, SOUTH) covers the 33 x 33 square (checked by enumerating it in the PORTALCOLD log)
		for (int sx = -PortalSearch.SPIRAL; sx <= PortalSearch.SPIRAL; sx++) {
			for (int sz = -PortalSearch.SPIRAL; sz <= PortalSearch.SPIRAL; sz++) {
				int cx = x + sx, cz = z + sz;
				add(box, cx, cz); // getHeight / the column's own getBlockState
				for (int offset = -1; offset <= 1; offset++) {
					for (int width = -1; width < 3; width++) { // canHostFrame reads
						int bx = cx + dirX * width + cwX * offset, bz = cz + dirZ * width + cwZ * offset;
						add(box, bx, bz);
					}
				}
				for (int width = -1; width < 3; width++) { // frame writes at a chosen column, and their six neighbours
					int bx = cx + dirX * width, bz = cz + dirZ * width;
					add(box, bx + 1, bz); add(box, bx - 1, bz); add(box, bx, bz + 1); add(box, bx, bz - 1);
				}
			}
		}
		return new int[] {Math.floorDiv(box[0], 16), Math.floorDiv(box[1], 16), Math.floorDiv(box[2], 16), Math.floorDiv(box[3], 16)};
	}

	private static void add(int[] box, int x, int z) {
		box[0] = Math.min(box[0], x); box[1] = Math.max(box[1], x);
		box[2] = Math.min(box[2], z); box[3] = Math.max(box[3], z);
	}

	@Test
	void matchesVanillaReadsAtEveryOffset() {
		for (boolean axisX : new boolean[] {true, false}) {
			for (int ox = 0; ox < 16; ox++) {
				for (int oz = 0; oz < 16; oz++) {
					int x = 16 * 80 + ox, z = -16 * 600 + oz; // a positive and a negative chunk coordinate
					assertArrayEquals(vanilla(x, z, axisX), PortalSearch.chunks(x, z, axisX), "offset " + ox + "," + oz + " axisX=" + axisX);
				}
			}
		}
	}

	@Test
	void townExitIsThreeByThree() {
		// PORTALCOLD town: the nether walker stood at x+0.5 of block 160, so the exit is 1284, -9596 (offset 4, 4): 625 chunks loaded = 3 x 3 FULL
		int[] r = PortalSearch.chunks(1284, -9596, true);
		assertEquals(3, r[1] - r[0] + 1);
		assertEquals(3, r[3] - r[2] + 1);
	}
}
