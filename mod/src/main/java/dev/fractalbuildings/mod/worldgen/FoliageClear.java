package dev.fractalbuildings.mod.worldgen;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Removes the foliage our air clear cut loose (FOLIAGE, John's playtest 2026-10-23: "leaves floating above the road").
 *
 * <p>A road or street clears the 4 blocks above its surface (and the whole column up to the old ground). A vanilla tree that stood there
 * loses its lower trunk or canopy, and what is left above hangs in the air. After the clear this pass reads the foliage around the chunk
 * and removes what vanilla itself would call detached, using vanilla's own rules:
 * <ul>
 * <li>a <b>log</b> (of a cluster that touches natural leaves, or that touches nothing solid at all, a floating remnant: beams and pillars of a building are left alone) is supported when a chain of logs (26-neighbour) reaches one that stands on something that is not foliage or air;</li>
 * <li>a <b>leaf</b> (not persistent: a hedge or a village's leaf block is left alone; and a bee nest) is supported when a supported log is within 6 steps through leaves (vanilla's distance 7 decays);</li>
 * <li>a <b>vine</b> is supported when a horizontal neighbour is supported foliage or any solid block, or the vine above it stays; cocoa
 * pods need a supported log beside them; a snow layer on a removed leaf goes with it.</li>
 * </ul>
 *
 * <p><b>Reach.</b> The support test reads {@link #REACH} blocks round the chunk (the 3 x 3 chunks of the generation region) and removes
 * every unsupported cell within {@link #MARGIN} blocks of it, so a canopy that hangs over the chunk border from a trunk cut here (or
 * the other way round) goes too. Removing outside the chunk being generated is what vanilla trees do when they grow (the region allows
 * writes one chunk out), and it is needed: a neighbour's tree can arrive <i>after</i> this chunk's pass and then be cut by its own
 * chunk's pass, leaving leaves in this chunk that only a pass that reads them can remove (found in the first test run: every orphan
 * left was a canopy spilled across a chunk border). Only foliage is ever removed, so no cell the pieces claim changes; the foliage
 * cells themselves are whatever vanilla grew, which depends on the neighbours' order, so they are not in the {@link PieceLedger} (the
 * cleared 4-block volume is).
 *
 * <p>Leaves that stay were placed non-persistent by vanilla's tree features, so the game's own decay still handles an orphan that
 * appears later.
 */
public final class FoliageClear {
	/** Test switch: when false nothing is removed (the "before" screenshots and the control that shows the test can fail). */
	public static volatile boolean ENABLED = true;
	/** Cells removed and passes run, for tests and logs. */
	public static final AtomicLong REMOVED = new AtomicLong();
	public static final AtomicLong PASSES = new AtomicLong();
	public static final AtomicLong NANOS = new AtomicLong();

	/** How far past the chunk the support test reads. */
	public static final int REACH = 13;
	/** How far past the chunk cells are removed: REACH minus 7 (a leaf is held by a log at most 6 leaf steps away). */
	public static final int MARGIN = 6;

	private static final byte AIR = 0, OTHER = 1, LOG = 2, LEAF = 3, VINE = 4, COCOA = 5, SNOW = 6, BUILT = 7; // BUILT: a solid only a builder places (planks, stairs, fences, doors, glass ...): acts as OTHER and marks a log cluster as a building's
	private static final int W = 16 + 2 * REACH;
	private static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	/** Test switch: count the foliage left in the cleared volume (surface +1..+4) of every column a pass ran for into {@link #RESIDUAL} (it should stay 0). */
	public static volatile boolean MEASURE = false;
	public static final AtomicLong RESIDUAL = new AtomicLong(), RESIDUAL_STREET = new AtomicLong(), RESIDUAL_SAMPLES = new AtomicLong();
	/** Trees that stand after a pass although the clear cut a cell of them (a crown with a notch, "sawn off": it hangs over the street above the clear). */
	public static final AtomicLong CUT_KEPT = new AtomicLong();
	/**
	 * Remove a tree whole when the clear cut it (architect review of FOLIAGE, round 3). Off: tried and reverted. A trunk removed after
	 * the fact leaves the dirt its feature put under it, in cells the pieces claim (street surface of a 2 x 2 trunk, air above a road):
	 * the order test (B2) reads those as a foreign block and fails 8 solid and 38 claimed-air cells; fixing it needs the claimed
	 * cells (PieceLedger) or the original surface block in the pass. Measured instead: {@link #CUT_KEPT}.
	 */
	public static volatile boolean WHOLE_TREES = false;

	private FoliageClear() {}

	/** The leaf and log cells a clear took out (where a tree was cut); a tree that lost one goes whole, not sawn off. */
	public static final class Cuts {
		/** Who ran the clear: "road" or "street" (test output). */
		public final String label;
		private int[] xyz = new int[48];
		private int n;

		public Cuts(String label) { this.label = label; }

		public int size() { return n; }

		/** Call with the block that is about to be replaced by air. */
		public void note(BlockState old, int x, int y, int z) {
			if (!old.is(BlockTags.LEAVES) && !old.is(BlockTags.LOGS)) return;
			if (3 * n + 3 > xyz.length) xyz = java.util.Arrays.copyOf(xyz, xyz.length * 2);
			xyz[3 * n] = x;
			xyz[3 * n + 1] = y;
			xyz[3 * n + 2] = z;
			n++;
		}
	}

	/**
	 * @param cx chunk x
	 * @param cz chunk z
	 * @param own columns of this chunk that were cleared, index {@code (z & 15) * 16 + (x & 15)} (nothing runs when none)
	 * @param surf for each own column, the y of the surface block (the clear starts above it)
	 * @return the number of blocks removed
	 */
	public static int run(WorldGenLevel level, int cx, int cz, boolean[] own, int[] surf) {
		return run(level, cx, cz, own, surf, null);
	}

	/** @param cuts the foliage cells the clear of this chunk took out (null: none known); a tree beside one goes whole */
	public static int run(WorldGenLevel level, int cx, int cz, boolean[] own, int[] surf, Cuts cuts) {
		if (!ENABLED) return 0;
		int minRef = Integer.MAX_VALUE, maxRef = Integer.MIN_VALUE;
		for (int i = 0; i < 256; i++) {
			if (!own[i]) continue;
			minRef = Math.min(minRef, surf[i]);
			maxRef = Math.max(maxRef, surf[i]);
		}
		if (minRef == Integer.MAX_VALUE) return 0;
		long t0 = System.nanoTime(); // det-ok: report only
		int x0 = cx * 16, z0 = cz * 16;
		int yLo = Math.max(level.getMinY(), minRef - 14), yHi = Math.min(level.getMaxY(), maxRef + 56);
		int H = yHi - yLo + 1;
		if (H <= 0) return 0;
		PASSES.incrementAndGet();
		byte[] kind = new byte[W * W * H];
		boolean anyFoliage = false;
		for (int dcz = -1; dcz <= 1; dcz++) {
			for (int dcx = -1; dcx <= 1; dcx++) {
				ChunkAccess chunk = level.getChunk(cx + dcx, cz + dcz);
				LevelChunkSection[] sections = chunk.getSections();
				for (int y = yLo; y <= yHi; y++) {
					int si = chunk.getSectionIndex(y);
					if (si < 0 || si >= sections.length) continue;
					LevelChunkSection sec = sections[si];
					if (sec.hasOnlyAir()) { y |= 15; continue; }
					for (int lz = 0; lz < 16; lz++) {
						for (int lx = 0; lx < 16; lx++) {
							int wx = (dcx + 1) * 16 + lx - 16 + REACH, wz = (dcz + 1) * 16 + lz - 16 + REACH;
							if (wx < 0 || wz < 0 || wx >= W || wz >= W) continue;
							BlockState s = sec.getBlockState(lx, y & 15, lz);
							if (s.isAir()) continue;
							byte k;
							if (s.is(BlockTags.LOGS)) k = treeLog(s) ? LOG : OTHER;
							else if (s.is(BlockTags.LEAVES) ? !s.getValueOrElse(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false) : s.is(Blocks.BEE_NEST)) k = LEAF;
							else if (s.is(Blocks.VINE)) k = VINE;
							else if (s.is(Blocks.COCOA)) k = COCOA;
							else if (s.is(Blocks.SNOW)) k = SNOW;
							else k = built(s) ? BUILT : OTHER;
							kind[(wz * W + wx) * H + (y - yLo)] = k;
							if (k == LOG || k == LEAF) anyFoliage = true;
						}
					}
				}
			}
		}
		if (!anyFoliage) return 0;
		boolean[] ok = new boolean[kind.length];
		markBuildingLogs(kind, ok, H);
		boolean[] forced = ok.clone(); // logs of a cluster that touches a built block: a live tree beside a building, or the building's own pillars; never removed
		// support: logs by chain from a log standing on something
		int[] dist = new int[kind.length]; // leaf distance from a supported log
		int[] queue = new int[kind.length];
		int qh = 0, qt = 0;
		for (int wz = 0; wz < W; wz++) for (int wx = 0; wx < W; wx++) {
			int base = (wz * W + wx) * H;
			for (int h = 0; h < H; h++) {
				if (kind[base + h] != LOG) continue;
				byte below = h == 0 ? OTHER : kind[base + h - 1];
				if (below == OTHER || below == BUILT || below == SNOW) { ok[base + h] = true; queue[qt++] = base + h; }
			}
		}
		while (qh < qt) {
			int c = queue[qh++];
			int h = c % H, col = c / H, wx = col % W, wz = col / W;
			for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) for (int dh = -1; dh <= 1; dh++) {
				int nx = wx + dx, nz = wz + dz, nh = h + dh;
				if (nx < 0 || nz < 0 || nx >= W || nz >= W || nh < 0 || nh >= H) continue;
				int n = (nz * W + nx) * H + nh;
				if (kind[n] == LOG && !ok[n]) { ok[n] = true; queue[qt++] = n; }
			}
		}
		int[] owner = new int[kind.length]; // the log cluster (tree) a log or a held leaf belongs to, 1 up
		int clusters = labelTrees(kind, owner, queue, H);
		// leaves: breadth-first from the supported logs through leaves, 6 steps
		qh = 0;
		qt = 0;
		for (int c = 0; c < kind.length; c++) if (ok[c] && kind[c] == LOG) queue[qt++] = c;
		int[][] face = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (qh < qt) {
			int c = queue[qh++];
			if (dist[c] >= 6) continue;
			int h = c % H, col = c / H, wx = col % W, wz = col / W;
			for (int[] f : face) {
				int nx = wx + f[0], nz = wz + f[2], nh = h + f[1];
				if (nx < 0 || nz < 0 || nx >= W || nz >= W || nh < 0 || nh >= H) continue;
				int n = (nz * W + nx) * H + nh;
				if (kind[n] == LEAF && !ok[n]) { ok[n] = true; owner[n] = owner[c]; dist[n] = dist[c] + 1; queue[qt++] = n; }
			}
		}
		// a tree the clear cut (a cut cell beside one of its logs or held leaves) goes whole: no sawn-off crown, no stub
		boolean[] cutTree = new boolean[clusters + 1];
		if (cuts != null) {
			boolean[] beside = new boolean[clusters + 1];
			for (int i = 0; i < kind.length; i++) if (forced[i] && owner[i] != 0) beside[owner[i]] = true;
			for (int i = 0; i < cuts.n; i++) {
				int wx = cuts.xyz[3 * i] - x0 + REACH, wz = cuts.xyz[3 * i + 2] - z0 + REACH, h = cuts.xyz[3 * i + 1] - yLo;
				for (int d = 0; d < 7; d++) {
					int nx = wx + (d < 6 ? FACE6[d][0] : 0), nz = wz + (d < 6 ? FACE6[d][2] : 0), nh = h + (d < 6 ? FACE6[d][1] : 0);
					if (nx < 0 || nz < 0 || nx >= W || nz >= W || nh < 0 || nh >= H) continue;
					int o = owner[(nz * W + nx) * H + nh];
					if (o != 0 && !beside[o]) cutTree[o] = true;
				}
			}
		}
		if (MEASURE) {
			boolean[] stands = new boolean[clusters + 1];
			for (int i = 0; i < kind.length; i++) if (kind[i] == LOG && ok[i] && owner[i] != 0) stands[owner[i]] = true;
			for (int o = 1; o <= clusters; o++) if (cutTree[o] && stands[o]) CUT_KEPT.incrementAndGet();
		}
		// removal: every unsupported cell within MARGIN of the chunk; vines top down so a hanging chain goes with its top
		int removed = 0;
		boolean[] gone = new boolean[kind.length];
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int wz = REACH - MARGIN; wz < REACH + 16 + MARGIN; wz++) for (int wx = REACH - MARGIN; wx < REACH + 16 + MARGIN; wx++) {
			int base = (wz * W + wx) * H;
			for (int h = H - 1; h >= 0; h--) {
				byte k = kind[base + h];
				boolean remove = false;
				if (k == LOG || k == LEAF) remove = !ok[base + h] || (WHOLE_TREES && cutTree[owner[base + h]]);
				else if (k == VINE) remove = !vineHolds(kind, ok, gone, wx, wz, h, H);
				else if (k == COCOA) remove = !cocoaHolds(kind, ok, wx, wz, h, H);
				if (!remove) continue;
				gone[base + h] = true;
				removed += erase(level, pos.set(x0 - REACH + wx, yLo + h, z0 - REACH + wz), air);
			}
		}
		// a snow layer sits one above the leaf it was removed with
		for (int wz = REACH - MARGIN; wz < REACH + 16 + MARGIN; wz++) for (int wx = REACH - MARGIN; wx < REACH + 16 + MARGIN; wx++) {
			int base = (wz * W + wx) * H;
			for (int h = 1; h < H; h++) {
				if (kind[base + h] != SNOW || !gone[base + h - 1] || gone[base + h]) continue;
				gone[base + h] = true;
				removed += erase(level, pos.set(x0 - REACH + wx, yLo + h, z0 - REACH + wz), air);
			}
		}
		if (MEASURE && cuts != null && !cuts.label.endsWith("-fix")) { // BuildingAirClear (no cuts) and the air-fix passes name columns that are not a street or road clear: not a measure of one
			int left = 0;
			for (int i = 0; i < 256; i++) {
				if (!own[i]) continue;
				for (int h = 1; h <= 4; h++) {
					BlockState st = level.getBlockState(pos.set(x0 + (i & 15), surf[i] + h, z0 + (i >> 4)));
					if (st.is(BlockTags.LEAVES) && !st.getValueOrElse(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false)) { // natural leaves only: a hedge or a street tree of ours is persistent
						left++;
						if (RESIDUAL_SAMPLES.incrementAndGet() <= 12) System.out.println("FOLIAGE residual " + (cuts == null ? "?" : cuts.label) + " " + (x0 + (i & 15)) + "," + (surf[i] + h) + "," + (z0 + (i >> 4)) + " surf " + surf[i] + " wsWG " + (level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x0 + (i & 15), z0 + (i >> 4)) - 1) + " mb " + (level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x0 + (i & 15), z0 + (i >> 4)) - 1) + " " + st.getBlock().getDescriptionId());
					}
				}
			}
			RESIDUAL.addAndGet(left);
			if (cuts != null && "street".equals(cuts.label)) RESIDUAL_STREET.addAndGet(left);
		}
		REMOVED.addAndGet(removed);
		NANOS.addAndGet(System.nanoTime() - t0); // det-ok: report only
		return removed;
	}

private static final int[][] FACE6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

/** Numbers the log clusters (26-connected: one tree each, usually) from 1 in {@code owner}; returns how many. {@code queue} is scratch. */
private static int labelTrees(byte[] kind, int[] owner, int[] queue, int H) {
	int id = 0;
	for (int start = 0; start < kind.length; start++) {
		if (kind[start] != LOG || owner[start] != 0) continue;
		id++;
		int qh = 0, qt = 0;
		queue[qt++] = start;
		owner[start] = id;
		while (qh < qt) {
			int c = queue[qh++];
			int h = c % H, col = c / H, wx = col % W, wz = col / W;
			for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) for (int dh = -1; dh <= 1; dh++) {
				int nx = wx + dx, nz = wz + dz, nh = h + dh;
				if (nx < 0 || nz < 0 || nx >= W || nz >= W || nh < 0 || nh >= H) continue;
				int n = (nz * W + nx) * H + nh;
				if (kind[n] == LOG && owner[n] == 0) { owner[n] = id; queue[qt++] = n; }
			}
		}
	}
	return id;
}

/** Trees grow plain logs; a stripped log or a wood block is somebody's building (the second order test lost 80 stripped pillars to a first version without this). */
	private static boolean treeLog(BlockState s) {
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
		return path.endsWith("_log") && !path.startsWith("stripped_");
	}

	/** Blocks no worldgen feature places beside a tree: a log touching one belongs to a building. */
	private static boolean built(BlockState s) {
		return s.is(BlockTags.PLANKS) || s.is(BlockTags.STAIRS) || s.is(BlockTags.SLABS) || s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS)
			|| s.is(BlockTags.DOORS) || s.is(BlockTags.TRAPDOORS) || s.is(BlockTags.WOOL) || s.is(BlockTags.IMPERMEABLE);
	}

	/**
	 * FOLIAGECLUSTER: a leafless cluster that touches nothing solid either (every 26-neighbour is air or foliage) is no beam or pillar but the
	 * floating remnant of a tree, and stays a log so the support test removes it. Deciding "building" from the leaves alone was order dependent:
	 * a leaf a neighbour chunk's pass had already cut (a pass removes within {@link #MARGIN} of its chunk, the log may lie one block further)
	 * left the log leafless, kept by the next pass, while the other order removed log and leaf together.
	 *
	 * <p>A log whose 26-connected cluster touches no natural leaf is otherwise a beam, post or pillar of someone's building (or a dead tree): it becomes an ordinary solid block here, never removed and never needing support.
	 */
	private static void markBuildingLogs(byte[] kind, boolean[] ok, int H) {
		boolean[] seen = new boolean[kind.length];
		int[] queue = new int[kind.length];
		int[][] face = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int start = 0; start < kind.length; start++) {
			if (kind[start] != LOG || seen[start]) continue;
			int qh = 0, qt = 0;
			queue[qt++] = start;
			seen[start] = true;
			boolean tree = false, built = false, touchesSolid = false;
			while (qh < qt) {
				int c = queue[qh++];
				int h = c % H, col = c / H, wx = col % W, wz = col / W;
				for (int[] f : face) {
					int nx = wx + f[0], nz = wz + f[2], nh = h + f[1];
					if (nx < 0 || nz < 0 || nx >= W || nz >= W || nh < 0 || nh >= H) continue;
					byte k = kind[(nz * W + nx) * H + nh];
					if (k == LEAF) tree = true;
					if (k == BUILT) built = true;
				}
				for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) for (int dh = -1; dh <= 1; dh++) {
					int nx = wx + dx, nz = wz + dz, nh = h + dh;
					if (nx < 0 || nz < 0 || nx >= W || nz >= W || nh < 0 || nh >= H) continue;
					int n = (nz * W + nx) * H + nh;
					if (kind[n] == LOG && !seen[n]) { seen[n] = true; queue[qt++] = n; }
					if (kind[n] == OTHER || kind[n] == BUILT) touchesSolid = true;
				}
				if (h == 0 || h == H - 1 || wx == 0 || wz == 0 || wx == W - 1 || wz == W - 1) touchesSolid = true; // at the edge of what the pass reads: unknown, so not "floating"
			}
			if (!tree && !touchesSolid) continue; // FOLIAGECLUSTER: a floating leafless remnant stays a log: the support test removes it
			if (!tree) for (int i = 0; i < qt; i++) kind[queue[i]] = OTHER;
			else if (built) for (int i = 0; i < qt; i++) ok[queue[i]] = true; // a live tree beside a fence or wall: stays a log, counts as supported
		}
	}

	/** Air, or the fluid the cell held (waterlogged leaves). */
	private static int erase(WorldGenLevel level, BlockPos pos, BlockState air) {
		BlockState old = level.getBlockState(pos);
		level.setBlock(pos, old.getFluidState().isEmpty() ? air : old.getFluidState().createLegacyBlock(), WRITE_FLAGS);
		return 1;
	}

	private static boolean holds(byte[] kind, boolean[] ok, boolean[] gone, int wx, int wz, int h, int H) {
		if (wx < 0 || wz < 0 || wx >= W || wz >= W || h < 0 || h >= H) return true; // outside the window: assume it holds
		int c = (wz * W + wx) * H + h;
		if (gone[c]) return false;
		byte k = kind[c];
		if (k == OTHER || k == BUILT) return true;
		if (k == LOG || k == LEAF) return ok[c];
		return false;
	}

	private static boolean vineHolds(byte[] kind, boolean[] ok, boolean[] gone, int wx, int wz, int h, int H) {
		if (holds(kind, ok, gone, wx + 1, wz, h, H) || holds(kind, ok, gone, wx - 1, wz, h, H) || holds(kind, ok, gone, wx, wz + 1, h, H) || holds(kind, ok, gone, wx, wz - 1, h, H)
			|| holds(kind, ok, gone, wx, wz, h + 1, H)) return true;
		int above = (wz * W + wx) * H + h + 1;
		return h + 1 < H && kind[above] == VINE && !gone[above];
	}

	private static boolean cocoaHolds(byte[] kind, boolean[] ok, int wx, int wz, int h, int H) {
		int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] o : d) {
			int nx = wx + o[0], nz = wz + o[1];
			if (nx < 0 || nz < 0 || nx >= W || nz >= W) return true;
			int c = (nz * W + nx) * H + h;
			if (kind[c] == LOG && ok[c]) return true;
		}
		return false;
	}
}
