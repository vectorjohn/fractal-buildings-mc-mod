package dev.fractalbuildings.core.gen.v1.types.highrise;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Hook;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.SceneDoc;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Floor-programme measures of the design review (storage sides, store area, studios without a bed), printed; some bounds asserted. */
class HighriseStatsTest {
	static int area(Room r) { int a = 0; for (Box3 p : r.parts()) a += p.sx() * p.sz(); return a; }

	/** True if a door or arch of {@code r} leads to a room that is not a store room. */
	static boolean opensFromRoom(BuildingIr b, Room r, boolean unused) {
		for (Opening o : b.openings()) {
			if (o.kind() != OpeningKind.DOOR && o.kind() != OpeningKind.ARCH) continue;
			String x = o.from().equals(r.id()) ? o.to() : o.to().equals(r.id()) ? o.from() : null;
			Room q = x == null ? null : room(b, x);
			if (q != null && !q.use().equals("storage")) return true;
		}
		return false;
	}

	static boolean hasBed(BuildingIr b, Room r) {
		for (Hook h : b.hooks()) if (h.room().equals(r.id()) && h.kind().equals("bed")) return true;
		return false;
	}

	static Room room(BuildingIr b, String id) { for (Room r : b.rooms()) if (r.id().equals(id)) return r; return null; }

	/** Counts over lots x seeds: {corridorFloors, floorsWithAStorageOnlySide, bigStores(>25 cells), stores, flats, flatsNoBed, grandFloors, grandStoreCells, grandCells}. */
	static long[] stats(int lot, int seeds) {
		long[] n = new long[13];
		for (long seed = 0; seed < seeds; seed++) {
			SceneDoc sc = HighriseProgrammeTest.gen(lot, seed);
			BuildingIr b = HighriseProgrammeTest.ir(sc);
			Map<Integer, Map<String, int[]>> sides = new HashMap<>(); // storey -> corridorId -> {northRooms, northNonStore, southRooms, southNonStore}
			Map<String, Boolean> seen = new HashMap<>();
			for (Room r : b.rooms()) {
				if (r.use().equals("storage")) { n[3]++; if (area(r) > 36) n[2]++; }
				if (r.use().equals("flat")) {
					n[4]++;
					boolean bed = false;
					for (Hook h : b.hooks()) if (h.room().equals(r.id()) && h.kind().equals("bed")) bed = true;
					for (Opening o : b.openings()) {
						if (o.kind() != OpeningKind.DOOR && o.kind() != OpeningKind.ARCH) continue;
						String x = o.from().equals(r.id()) ? o.to() : o.to().equals(r.id()) ? o.from() : null;
						Room q = x == null ? null : room(b, x);
						if (q != null && (q.use().equals("bedroom") || q.use().equals("flat") && hasBed(b, q))) bed = true; // a bedroom, or another living room of the unit with a bed
					}
					if (!bed) n[5]++;
				}
			}
			for (Room c : b.rooms()) {
				if (!c.use().equals("corridor")) continue;
				Box3 cp = c.parts().get(0);
				boolean[] nonStoreSide = new boolean[2], anySide = new boolean[2];
				for (Opening o : b.openings()) {
					if (o.kind() != OpeningKind.DOOR && o.kind() != OpeningKind.ARCH) continue;
					String x = o.from().equals(c.id()) ? o.to() : o.to().equals(c.id()) ? o.from() : null;
					Room q = x == null ? null : room(b, x);
					if (q == null || q.use().equals("stairwell") || q.use().equals("banquet")) continue;
					int side = o.box().z() < cp.z() ? 0 : 1;
					anySide[side] = true;
					if (!q.use().equals("storage")) nonStoreSide[side] = true;
				}
				boolean typical = false;
				for (Room q : b.rooms()) if (q.storey() == c.storey() && (q.use().equals("flat") || q.use().equals("office"))) typical = true;
				if (typical) {
					n[0]++;
					boolean flats = false;
					for (Room q : b.rooms()) if (q.storey() == c.storey() && q.use().equals("flat")) flats = true;
					boolean flagged = anySide[0] && !nonStoreSide[0] || anySide[1] && !nonStoreSide[1];
					if (flats) { n[11]++; if (flagged) n[12]++; }
					if (flagged) { n[1]++; }
				}
			}
			for (Room r : b.rooms()) {
				if (r.use().equals("entry")) {
					Box3 p = r.parts().get(0);
					if (Math.min(p.sx(), p.sz()) > 3 || Math.max(p.sx(), p.sz()) > 6) n[9]++;
				}
				if (!r.use().equals("storage")) continue;
				if (!opensFromRoom(b, r, false)) {
					boolean viaStore = false;
					for (Opening o : b.openings()) {
						if (o.kind() != OpeningKind.DOOR && o.kind() != OpeningKind.ARCH) continue;
						String x = o.from().equals(r.id()) ? o.to() : o.to().equals(r.id()) ? o.from() : null;
						Room q = x == null ? null : room(b, x);
						if (q != null && q.use().equals("storage") && opensFromRoom(b, q, false)) viaStore = true;
					}
					if (!viaStore) n[10]++;
				}
			}
			Map<Integer, long[]> grand = new HashMap<>();
			for (Room r : b.rooms()) {
				boolean g = r.use().equals("lobby") || r.use().equals("banquet") || r.use().equals("open");
				if (!g) continue;
				grand.computeIfAbsent(r.storey(), k -> new long[1]);
			}
			for (int s : grand.keySet()) {
				long store = 0, all = 0;
				for (Room r : b.rooms()) if (r.storey() == s) { all += area(r); if (r.use().equals("storage")) store += area(r); }
				n[6]++;
				n[7] += store;
				n[8] += all;
			}
		}
		return n;
	}

	@Test
	void printStats() {
		long[] tot = new long[13];
		for (int lot : new int[] {20, 28, 36, 44}) {
			long[] n = stats(lot, 8);
			assertTrue(n[9] == 0, "entry rooms over 3 x 6 on lot " + lot + ": " + n[9]);
			assertTrue(n[10] * 100 <= n[3], "stores reached only through two stores on lot " + lot + ": " + n[10]);
			assertTrue(n[5] == 0, "flats without a bed or bedroom on lot " + lot + ": " + n[5]);
			assertTrue(n[2] * 100 <= 25 * n[3], "store rooms over 36 cells on lot " + lot + ": " + n[2] + " of " + n[3]);
			if (lot >= 28) assertTrue(n[12] * 100 <= 5 * n[11], "residential floors with a storage-only corridor side on lot " + lot + ": " + n[12] + " of " + n[11]);
			if (lot >= 28) assertTrue(n[1] * 100 <= 30 * n[0], "unit floors with a storage-only corridor side on lot " + lot + ": " + n[1] + " of " + n[0]);
			for (int i = 0; i < 9; i++) tot[i] += n[i];
			System.out.println("STATS lot " + lot + ": unit floors " + n[0] + ", with a storage-only corridor side " + n[1] + " (residential " + n[12] + " of " + n[11] + ")" + "; stores " + n[3] + " (" + n[2] + " over 36 cells); flats " + n[4] + " without bed " + n[5]
					+ "; entries over 3x6 " + n[9] + ", stores behind 2 stores " + n[10] + "; lobby/banquet/open floors " + n[6] + ", store cells " + n[7] + " of " + n[8]);
		}
		assertTrue(tot[0] > 0);
	}
}
