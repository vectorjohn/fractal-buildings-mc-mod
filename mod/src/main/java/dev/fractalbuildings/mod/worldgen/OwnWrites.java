package dev.fractalbuildings.mod.worldgen;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADLEAFORDER: the blocks the vanilla tree features of the chunk being decorated wrote, with the state each cell held before them.
 *
 * <p>{@link FoliageClear#own} decides which of a chunk's own trees our pieces and roads cut, and puts back what was there before the
 * tree, from these records alone (and the plan): never from foliage other chunks grew, which is there or not by chunk order. The game fills
 * it from mixins ({@code ChunkGenerator.applyBiomeDecoration} starts a chunk, {@code PlacedFeature.place} and {@code TreeFeature.place}
 * mark vanilla features, {@code WorldGenRegion.setBlock} records); the order simulator calls the same methods.
 *
 * <p>Recorded: every write inside a tree feature (logs, leaves, the dirt under a trunk, vines, cocoa, bee nests, leaf litter) and, from
 * any other vanilla feature, a write of foliage (leaves, logs, vines, cocoa) or of a snow layer. Not recorded: our own code ({@link #ours}),
 * structures, and anything outside a decoration. Per thread: a chunk is decorated on one thread from start to end.
 */
public final class OwnWrites {
	private OwnWrites() {}

	/** One recorded cell: what it held before the first write of this chunk's features, and what the last such write put there. */
	public record Cell(BlockState before, BlockState written) {}

	private static final class Ctx {
		long chunk = Long.MIN_VALUE;
		int feature, tree, ours;
		final Long2ObjectLinkedOpenHashMap<Cell> cells = new Long2ObjectLinkedOpenHashMap<>();
	}

	private static final ThreadLocal<Ctx> CTX = ThreadLocal.withInitial(Ctx::new);

	public static long key(int cx, int cz) { return ((long) cx << 32) ^ (cz & 0xffffffffL); }

	/** A chunk's decoration starts: forget the last one. */
	public static void begin(int cx, int cz) {
		Ctx c = CTX.get();
		c.chunk = key(cx, cz);
		c.feature = 0;
		c.tree = 0;
		c.ours = 0;
		c.cells.clear();
	}

	/** Drops the records (after the pass that used them). */
	public static void end() {
		Ctx c = CTX.get();
		c.chunk = Long.MIN_VALUE;
		c.cells.clear();
	}

	public static void enterFeature() { CTX.get().feature++; }
	public static void exitFeature() { Ctx c = CTX.get(); if (c.feature > 0) c.feature--; }
	public static void enterTree() { CTX.get().tree++; }
	public static void exitTree() { Ctx c = CTX.get(); if (c.tree > 0) c.tree--; }

	/** Our own code runs (pieces, the road feature's passes): nothing it writes is recorded. Use with try-with-resources. */
	public static Ours ours() {
		Ctx c = CTX.get();
		c.ours++;
		return new Ours(c);
	}

	/** The scope of {@link #ours}. */
	public static final class Ours implements AutoCloseable {
		private final Ctx c;
		private Ours(Ctx c) { this.c = c; }
		@Override public void close() { if (c.ours > 0) c.ours--; }
	}

	/** Whether a write now would be recorded (a cheap test for the mixin before it reads the old state). */
	public static boolean recording() {
		Ctx c = CTX.get();
		return c.chunk != Long.MIN_VALUE && c.feature > 0 && c.ours == 0;
	}

	/**
	 * The {@code WorldGenRegion.setBlock} hook: false (and nothing read) when {@code pos} is outside the region's write zone. Vanilla only
	 * logs such a write ("Detected setBlock in a far chunk") and returns false, but reading the cell there throws ("Requested chunk
	 * unavailable during world generation") and would kill the chunk (ROADLEAFORDER r2, verifier). Otherwise records the write when
	 * {@link #recording()} and returns true.
	 */
	public static boolean onSetBlock(BlockPos pos, BlockState now, java.util.function.Predicate<BlockPos> inWriteZone, java.util.function.Function<BlockPos, BlockState> read) {
		if (!inWriteZone.test(pos)) return false;
		if (recording()) record(pos, read.apply(pos), now);
		return true;
	}

	/** A block write by whatever runs now; recorded when it is a vanilla feature's (see the class note). */
	public static void record(BlockPos pos, BlockState before, BlockState now) {
		Ctx c = CTX.get();
		if (c.chunk == Long.MIN_VALUE || c.feature == 0 || c.ours > 0) return;
		if (!foliage(now) && !now.is(Blocks.SNOW) && !(c.tree > 0 && treeGround(now))) return; // not air: a tree reshaping a neighbour (a bed it breaks) is no part of it
		long k = pos.asLong();
		Cell old = c.cells.get(k);
		if (old == null && before.getBlock() == now.getBlock()) return; // a new state of a block that was there (another tree's leaf distance, a fence's connections): not this chunk's
		c.cells.put(k, new Cell(old == null ? before : old.before(), now));
	}

	/** The records of chunk (cx, cz), or null when this thread is not decorating it (another dimension, a chunk generated before the mod). */
	public static Long2ObjectLinkedOpenHashMap<Cell> of(int cx, int cz) {
		Ctx c = CTX.get();
		return c.chunk == key(cx, cz) ? c.cells : null;
	}

	/** What a tree puts on the ground: the dirt under a trunk, podzol, roots, leaf litter. */
	static boolean treeGround(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(Blocks.LEAF_LITTER) || s.is(Blocks.MANGROVE_ROOTS) || s.is(Blocks.HANGING_ROOTS) || s.is(Blocks.MOSS_CARPET);
	}

	static boolean foliage(BlockState s) {
		return s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.is(Blocks.VINE) || s.is(Blocks.COCOA) || s.is(Blocks.BEE_NEST);
	}
}
