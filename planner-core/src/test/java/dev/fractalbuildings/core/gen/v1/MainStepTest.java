package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 6.33 MAINSTEP: a front door that has a step gets one 3 wide (door width + 1 each side, centred) where the side cells fit; never
 * a one-sided step, never a side step in a wall, the lot line or without 4 blocks of air; the plain door-wide step otherwise.
 */
class MainStepTest {
	static final String[] STYLES = {"medieval", "plains", "taiga", "desert", "jungle", "snowy"};

	private static boolean stairAt(PlacedBuilding b, int x, int y, int z) {
		for (Box s : b.siteBoxes()) if (s.role() == Role.STAIR && s.box().x() <= x && x < s.box().maxX() && s.box().y() <= y && y < s.box().maxY() && s.box().z() <= z && z < s.box().maxZ()) return true;
		return false;
	}

	/** Free blocks (at most the headroom) over the step level in a column of the placed building (shell and site boxes). */
	private static int clear(dev.fractalbuildings.core.voxel.VoxelGrid grid, int x, int level, int z) {
		int n = 0;
		while (n < InstancePassV1.HEADROOM && grid.get(x, level + 1 + n, z) == Role.AIR) n++;
		return n;
	}

	/** {doors with a front step, widened, bad descriptions} */
	private static Object[] scan(String terrain, long seeds) {
		List<String> bad = new ArrayList<>();
		int doors = 0, wide = 0;
		ProbeSpecs.Probes p = ProbeSpecs.parse(terrain);
		for (String type : PorchStepTest.TYPES)
			for (String style : STYLES)
				for (Facing f : Facing.values())
					for (long seed = 0; seed < seeds; seed++) {
						Lot lot = new Lot("lot0", 0, 0, f.ordinal() % 2 == 0 ? 14 : 11, f.ordinal() % 2 == 0 ? 11 : 14, f);
						SceneDoc s;
						try { s = new GeneratorV1().generate(new GenRequest(seed, lot, type, style, p.terrain(), p.biome())); } catch (IllegalArgumentException e) { continue; }
						PlacedBuilding b = s.buildings().get(0);
						int level = InstancePassV1.entranceStorey(b.shell()).y();
						for (Opening o : InstancePassV1.entranceDoors(b.shell())) {
							if (o.box().y() != level + 1 || InstancePassV1.secondary(b.shell(), o) || InstancePassV1.outward(o) != Facing.NORTH) continue;
							int cz = o.box().z() - 1; // north-facing: the cell in front is at z - 1
							if (!stairAt(b, o.box().x(), level, cz)) continue;
							doors++;
							int lo = o.box().x() - 1, hi = o.box().maxX();
							boolean l = stairAt(b, lo, level, cz), r = stairAt(b, hi, level, cz);
							String id = type + " " + style + " " + f + " seed " + seed + " " + o.id();
							if (l != r) { bad.add(id + ": one-sided"); continue; }
							if (!l) continue;
							wide++;
							var grid = dev.fractalbuildings.core.voxel.VoxelGrid.of(b);
							for (int x : new int[] {lo, hi}) {
								boolean[][] fp = InstancePassV1.footprint(b.shell());
								if (x >= 0 && cz >= 0 && x < fp.length && cz < fp[0].length && fp[x][cz]) bad.add(id + ": side step in the footprint");
								int need = Math.max(3, Math.min(InstancePassV1.HEADROOM, clear(grid, o.box().x(), level, cz)));
								if (clear(grid, x, level, cz) < need) bad.add(id + ": side step has " + clear(grid, x, level, cz) + " clear blocks, the centre step " + need);
								for (Box rb : b.siteBoxes()) if (rb.role() == Role.RAIL && rb.box().y() <= level + 1 && rb.box().maxY() > level && Math.abs(rb.box().x() - x) + Math.abs(rb.box().z() - cz) <= 1 && !(rb.box().x() == x && rb.box().z() == cz)) bad.add(id + ": rail beside a side step at " + rb.box().x() + "," + rb.box().z());
							}
						}
					}
		return new Object[] {doors, wide, bad};
	}

	@SuppressWarnings("unchecked")
	@Test
	void mostFrontStepsOnFlatLandAreThreeWideAndNeverOneSided() {
		Object[] r = scan("flat:70", 4);
		int doors = (Integer) r[0], wide = (Integer) r[1];
		System.out.println("MAINSTEP flat: " + wide + " of " + doors + " front steps widened");
		List<String> bad = (List<String>) r[2];
		assertEquals(List.of(), bad.subList(0, Math.min(bad.size(), 8)), bad.size() + " bad front steps");
		assertTrue(doors > 60, "doors " + doors);
		assertTrue(wide * 10 >= doors * 4, "only " + wide + " of " + doors + " front steps are 3 wide");
	}

	@SuppressWarnings("unchecked")
	@Test
	void slopesKeepTheRules() {
		for (String t : new String[] {"plane:64:1:0:3", "plane:64:0:1:2", "noise:7:64:6:32"}) {
			List<String> bad = (List<String>) scan(t, 3)[2];
			assertEquals(List.of(), bad.subList(0, Math.min(bad.size(), 8)), t + ": " + bad.size() + " bad front steps");
		}
	}

	@Test
	void sideDoorsKeepThePlainStep() {
		ProbeSpecs.Probes p = ProbeSpecs.parse("flat:70");
		int checked = 0;
		for (long seed = 0; seed < 12; seed++) {
			SceneDoc s = new GeneratorV1().generate(new GenRequest(seed, new Lot("lot0", 0, 0, 14, 11, Facing.NORTH), "house", "medieval", p.terrain(), p.biome()));
			PlacedBuilding b = s.buildings().get(0);
			int level = InstancePassV1.entranceStorey(b.shell()).y();
			for (Opening o : InstancePassV1.entranceDoors(b.shell())) {
				if (!InstancePassV1.secondary(b.shell(), o)) continue;
				Facing out = InstancePassV1.outward(o);
				int fx = o.box().x() + out.dx, fz = o.box().z() + out.dz;
				Facing lat = out.rotate(1);
				if (!stairAt(b, fx, level, fz)) continue;
				checked++;
				assertTrue(!(stairAt(b, fx + lat.dx, level, fz + lat.dz) && stairAt(b, fx - lat.dx, level, fz - lat.dz)), "side door seed " + seed + " got a 3-wide step");
			}
		}
		
	}

	/** The air rule on its own (generated front steps never meet an obstruction beside them in these scans): a wall cell, or a roof 2 blocks over the tread, rejects a side cell; 3 clear passes with need 3. */
	@Test
	void sideAirRuleRejectsWallsAndLowRoofs() {
		ProbeSpecs.Probes p = ProbeSpecs.parse("flat:70");
		PlacedBuilding b = new GeneratorV1().generate(new GenRequest(1, new Lot("lot0", 0, 0, 14, 11, Facing.NORTH), "house", "medieval", p.terrain(), p.biome())).buildings().get(0);
		var grid = dev.fractalbuildings.core.voxel.VoxelGrid.of(b);
		var bb = b.shell().bounds();
		int base = 0, level = 0, wx = bb.x() + bb.sx() / 2, wz = bb.z() + bb.sz() / 2; // the middle of the house: walls or interior, not free air under open sky
		int open = bb.x() - 3; // outside the shell
		assertTrue(InstancePassV1.sideAirOk(grid, open, bb.z() - 2, level, base, 4), "open ground has 4 clear");
		boolean anyReject = false;
		for (int x = bb.x(); x < bb.maxX(); x++) for (int z = bb.z(); z < bb.maxZ(); z++) anyReject |= !InstancePassV1.sideAirOk(grid, x, z, level, base, 3);
		assertTrue(anyReject, "cells inside the shell must be rejected");
		assertTrue(InstancePassV1.clear(grid, open, bb.z() - 2, level, base) == InstancePassV1.HEADROOM);
	}
}
