package dev.fractalbuildings.mod.ordersim;

import dev.fractalbuildings.mod.worldgen.PieceLedger;
import dev.fractalbuildings.mod.worldgen.SpillKinds;
import java.util.TreeMap;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ORDERSIM S3: the checks. They are the order test's own criteria on the cells our pieces claim, plus the sim's additions: the ledger
 * equals the core's reference raster (C2), the world equals it except what {@link SpillKinds} accepts (C3), the claimed cells are the same
 * in every order (C4, strict only in {@code spaced} scenarios), every write of our code obeys its contract (C5), a second sweep of our
 * step changes nothing (C6).
 */
public final class SimCompare {
	private SimCompare() {}

	private static final int MAX_DETAILS = 10;

	static String name(BlockState s) { return SpillKinds.name(s); }

	/** C2 and C3 for one finished run; also stores {@code run.have}. */
	static void claimed(Scenario s, SimWorld world, PieceLedger.Recording rec, SimRun run) {
		Scenario.Plan p = s.plan();
		TreeMap<Long, Integer> ledger = new TreeMap<>();
		for (var e : rec.states().entrySet()) if (s.inInner(PieceLedger.x(e.getKey()), PieceLedger.z(e.getKey()))) ledger.put(e.getKey(), e.getValue());
		run.ledgerCells = ledger.size();
		run.refCells = p.innerKeys.length;
		for (long k : p.innerKeys) {
			Integer got = ledger.get(k);
			if (got == null) run.missing++; else if (got != p.ref.get(k)) run.wrong++;
		}
		TreeMap<Long, Integer> roads = new TreeMap<>(); // the road feature's cells (they have no reference raster: SC4 checks them against the ledger and across orders)
		for (long k : rec.roadCells()) if (ledger.containsKey(k) && !p.ref.containsKey(k)) roads.put(k, ledger.get(k));
		for (Long k : ledger.keySet()) if (!p.ref.containsKey((long) k) && !roads.containsKey(k)) run.extra++;
		run.roadCells = roads.size();
		run.roadDigest = PieceLedger.digest(roads);
		run.roadStates = roads;
		run.duplicates = (int) rec.duplicates().stream().filter(k -> s.inInner(PieceLedger.x(k), PieceLedger.z(k))).count();
		run.ledgerDigest = PieceLedger.digest(ledger);

		var hooks = rec.hooks();
		BlockGetter level = world.wholeLevel("check").proxy();
		run.have = new int[p.innerKeys.length];
		for (int i = 0; i < p.innerKeys.length; i++) {
			long k = p.innerKeys[i];
			int x = PieceLedger.x(k), y = PieceLedger.y(k), z = PieceLedger.z(k);
			BlockState have = world.get(x, y, z);
			run.have[i] = Block.BLOCK_STATE_REGISTRY.getId(have);
			if (hooks.containsKey(k)) continue; // furniture over claimed air: read back on its own in the order test
			BlockState want = Block.BLOCK_STATE_REGISTRY.byId(p.ref.get(k));
			if (want.isAir()) {
				run.air++;
				if (have.isAir()) continue;
				run.airBad++;
				String kind = SpillKinds.airKind(have, world.get(x, y + 1, z), world.get(x, y + 2, z));
				if (kind.equals("unknown") && SpillKinds.trunkBaseBeside(have, level, x, y, z)) kind = "spill";
				run.histogram.merge("air->" + name(have) + " [" + kind + "]", 1, Integer::sum);
				if (kind.equals("unknown")) { run.airUnknown++; detail(run.badCells, "air", world, x, y, z, want, have); }
			} else {
				run.solid++;
				if (have == want) continue;
				run.solidBad++;
				String kind = SpillKinds.solidKind(want, have, world.get(x, y + 1, z), world.get(x, y + 2, z), p.refRoad.contains(k));
				if (kind.equals("unknown") && SpillKinds.lakeBarrier(have, level, x, y, z)) kind = "lake";
				if (kind.equals("unknown") && SpillKinds.trunkBaseBeside(have, level, x, y, z)) kind = "spill";
				run.histogram.merge(name(want) + "->" + name(have) + " [" + kind + "]", 1, Integer::sum);
				if (kind.equals("unknown")) { run.solidUnknown++; detail(run.badCells, "solid", world, x, y, z, want, have); }
			}
		}
		roadCells(world, level, roads, run);
	}

	/** C3 for the road feature's cells: the world holds what the ledger says, or what {@link SpillKinds} accepts there (no ledger cell is ever "unknown"). */
	private static void roadCells(SimWorld world, BlockGetter level, TreeMap<Long, Integer> roads, SimRun run) {
		for (var e : roads.entrySet()) {
			long k = e.getKey();
			int x = PieceLedger.x(k), y = PieceLedger.y(k), z = PieceLedger.z(k);
			BlockState want = Block.BLOCK_STATE_REGISTRY.byId(e.getValue()), have = world.get(x, y, z);
			if (want.isAir()) {
				if (have.isAir()) continue;
				run.roadBad++;
				String kind = SpillKinds.airKind(have, world.get(x, y + 1, z), world.get(x, y + 2, z));
				if (kind.equals("unknown") && SpillKinds.trunkBaseBeside(have, level, x, y, z)) kind = "spill";
				run.histogram.merge("road-air->" + name(have) + " [" + kind + "]", 1, Integer::sum);
				if (kind.equals("unknown")) { run.roadUnknown++; detail(run.badCells, "road air", world, x, y, z, want, have); }
			} else {
				run.roadSolid++;
				if (have == want) continue;
				run.roadBad++;
				String kind = SpillKinds.solidKind(want, have, world.get(x, y + 1, z), world.get(x, y + 2, z), true);
				if (kind.equals("unknown") && SpillKinds.lakeBarrier(have, level, x, y, z)) kind = "lake";
				if (kind.equals("unknown") && SpillKinds.trunkBaseBeside(have, level, x, y, z)) kind = "spill";
				run.histogram.merge("road-" + name(want) + "->" + name(have) + " [" + kind + "]", 1, Integer::sum);
				if (kind.equals("unknown")) { run.roadUnknown++; detail(run.badCells, "road solid", world, x, y, z, want, have); }
			}
		}
	}

	private static void detail(java.util.List<String> out, String what, SimWorld world, int x, int y, int z, BlockState want, BlockState have) {
		if (out.size() >= MAX_DETAILS) return;
		out.add("unknown " + what + " " + x + "," + y + "," + z + " want " + name(want) + " has " + name(have) + "; above " + name(world.get(x, y + 1, z)) + ", below " + name(world.get(x, y - 1, z))
			+ ", N " + name(world.get(x, y, z - 1)) + " E " + name(world.get(x + 1, y, z)) + " S " + name(world.get(x, y, z + 1)) + " W " + name(world.get(x - 1, y, z)) + "; last writer " + world.writerOf(x, y, z));
	}

	/** C4 (and the C2 digest): the claimed cells against the baseline order's. Report-only unless the scenario is {@code spaced}. */
	static void crossOrder(Scenario s, SimRun base, SimRun run) {
		Scenario.Plan p = s.plan();
		run.digestDiffers = !run.ledgerDigest.equals(base.ledgerDigest);
		run.roadDigestDiffers = !run.roadDigest.equals(base.roadDigest);
		int diff = 0;
		for (int i = 0; i < run.have.length; i++) {
			if (run.have[i] == base.have[i]) continue;
			diff++;
			if (run.diffCells.size() < MAX_DETAILS) {
				long k = p.innerKeys[i];
				int x = PieceLedger.x(k), y = PieceLedger.y(k), z = PieceLedger.z(k);
				run.diffCells.add(x + "," + y + "," + z + ": " + name(Block.BLOCK_STATE_REGISTRY.byId(base.have[i])) + " in " + base.order + " (last writer " + base.world.writerOf(x, y, z) + "), "
					+ name(Block.BLOCK_STATE_REGISTRY.byId(run.have[i])) + " in " + run.order + " (last writer " + run.world.writerOf(x, y, z) + ")");
			}
		}
		run.crossDiff = diff;
	}

	/**
	 * C5: every write of our code, by the code that made it. Needs a run with {@code logWrites} and {@code audit}. The rules are the pass's
	 * contract (what it may write, and where); they hold in every order, so one run is enough.
	 */
	static void contracts(Scenario s, SimWorld world, SimRun run) {
		Scenario.Plan p = s.plan();
		int bad = 0;
		for (SimWorld.Write w : world.writes()) {
			if (!w.writer().startsWith("ours:")) continue;
			String frame = w.frame() == null ? "?" : w.frame();
			String cm = frame.contains(":") ? frame.substring(0, frame.lastIndexOf(':')) : frame;
			int dot = cm.lastIndexOf('.');
			String method = dot < 0 ? cm : cm.substring(dot + 1), cls = dot < 0 ? cm : cm.substring(cm.lastIndexOf('.', dot - 1) + 1, dot);
			run.branchWrites.merge(cls + "." + (method.startsWith("lambda$") ? "write" : method), 1, Integer::sum);
			run.branchWrites.merge(cls + "." + (method.startsWith("lambda$") ? "write" : method) + "/" + oldKind(w.old()), 1, Integer::sum); // liveness by what was removed (S4a)
			String why = violation(p, w, cls, method);
			if (why != null) {
				bad++;
				if (run.contractDetails.size() < MAX_DETAILS) run.contractDetails.add(cls + "." + method + " " + why + " at " + w.x() + "," + w.y() + "," + w.z() + " " + name(w.old()) + " -> " + name(w.now()) + " flags " + w.flags() + " (" + w.writer() + ")");
			}
		}
		run.contractViolations = bad;
	}

	/** What a write replaced, for the liveness counts: leaf, log, snow, vine, air or other. */
	static String oldKind(BlockState old) {
		if (old.isAir()) return "air";
		if (old.is(BlockTags.LEAVES)) return "leaf";
		if (old.is(BlockTags.LOGS)) return "log";
		if (old.is(Blocks.SNOW)) return "snow";
		if (old.is(Blocks.VINE)) return "vine";
		return "other";
	}

	private static String violation(Scenario.Plan p, SimWorld.Write w, String cls, String method) {
		if ((w.flags() & Block.UPDATE_KNOWN_SHAPE) == 0) return "without UPDATE_KNOWN_SHAPE";
		long k = PieceLedger.pack(w.x(), w.y(), w.z());
		boolean planned = p.ref.containsKey(k);
		boolean plannedAir = planned && Block.BLOCK_STATE_REGISTRY.byId(p.ref.get(k)).isAir();
		String lab = w.writer();
		String[] cc = lab.substring(lab.lastIndexOf(':') + 1).split(",");
		int dcx = Integer.parseInt(cc[0]), dcz = Integer.parseInt(cc[1]);
		BlockState old = w.old(), now = w.now();
		switch (cls) {
			case "TilePiece":
				return Math.floorDiv(w.x(), 16) == dcx && Math.floorDiv(w.z(), 16) == dcz ? null : "wrote outside its chunk " + dcx + "," + dcz;
			case "SpillClear":
				if (!now.isAir()) return "wrote a block, not air";
				if (method.equals("run")) {
					if (old.is(BlockTags.LOGS)) return "cleared a log";
					if (!plannedAir) return "cleared a cell outside planned air";
				} else if (!old.is(BlockTags.LEAVES)) return "cleared " + name(old) + ", not a leaf";
				return null;
			case "BuildingAirClear":
				if (!now.isAir()) return "wrote a block, not air";
				if (!(old.is(BlockTags.LEAVES) || old.is(BlockTags.LOGS) || old.is(Blocks.VINE))) return "cleared " + name(old);
				return plannedAir ? null : "cleared a cell outside planned air";
			case "StreetAirFix":
				if (method.equals("restoreBase")) {
					boolean dirt = old.is(Blocks.DIRT) || old.is(Blocks.PODZOL) || old.is(Blocks.COARSE_DIRT) || old.is(Blocks.ROOTED_DIRT);
					if (!dirt) return "restored over " + name(old) + ", not a dirt block";
					if (!planned || p.ref.get(k) != Block.BLOCK_STATE_REGISTRY.getId(now)) return "restored a block the plan does not give";
					return null;
				}
				if (!now.isAir()) return "wrote a block, not air";
				if (old.is(BlockTags.LOGS)) return plannedAir ? null : "removed a log outside planned air";
				return old.is(Blocks.SNOW) ? null : "removed " + name(old);
			case "FoliageClear":
				return now.isAir() || !now.getFluidState().isEmpty() ? null : "wrote " + name(now);
			case "RoadFeature":
				return Math.floorDiv(w.x(), 16) == dcx && Math.floorDiv(w.z(), 16) == dcz ? null : "wrote outside its chunk " + dcx + "," + dcz;
			case "HookPlacer":
				return null;
			case "StairSeam": { // DS36ROOFS r2: only the owner's own stair, one block across the border of its chunk
				if (!(now.getBlock() instanceof net.minecraft.world.level.block.StairBlock)) return "wrote " + name(now) + ", not a stair";
				if (!planned || p.ref.get(k) != Block.BLOCK_STATE_REGISTRY.getId(now)) return "wrote a stair the plan does not give there";
				int ox = Math.max(dcx * 16 - w.x(), w.x() - (dcx * 16 + 15)), oz = Math.max(dcz * 16 - w.z(), w.z() - (dcz * 16 + 15));
				return ox <= 1 && oz <= 1 && (ox == 1) != (oz == 1) ? null : "wrote " + ox + "," + oz + " blocks outside its chunk " + dcx + "," + dcz;
			}
			default:
				return "write not attributed to a known pass (frame " + w.frame() + ")";
		}
	}
}
