package dev.fractalbuildings.core.determinism;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.rand.Draws;
import dev.fractalbuildings.core.rand.Hash64;

/** Fixed request families: lot, front, terrain and style all derived from the case seed. */
final class Cases {
	private Cases() {}

	static GenRequest of(long seed) {
		Draws r = new Draws(Hash64.hash(seed, "determinism-case"));
		Lot lot = new Lot("lot0", r.range(-500, 500), r.range(-500, 500), r.range(9, 31), r.range(9, 31), Facing.values()[r.below(4)]);
		int base = r.range(40, 90), kx = r.range(0, 3), kz = r.range(0, 3), m = r.range(1, 6);
		TerrainProbe t = (x, z) -> base + Math.floorMod(x * kx + z * kz, m);
		return new GenRequest(seed, lot, seed % 2 == 0 ? "plain" : "medieval", t, (x, z) -> "plains");
	}
}
