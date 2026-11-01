package dev.fractalbuildings.gametest.worldgen;

import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

/**
 * Isolation for server game tests that place settlements at their real (absolute) coordinates (TESTISO). Several tests place the same
 * town or village at the same spot (the planners and seeds are shared), and each test starts in the first tick in which its own test
 * structure's chunks are loaded, so their order differs from run to run. {@link #run} makes every such test independent of the others:
 * <ol>
 * <li>it keeps the chunks of the test's boxes loaded with a ticket and waits (one tick at a time) until each one's entities are loaded and
 * visible ({@code areEntitiesActuallyLoadedAndTicking}): an entity added to a chunk whose FULL promotion has not been processed yet lands in
 * a hidden entity section, where {@code getEntitiesOfClass} and a discard cannot see it while its UUID stays taken (the villager flake);</li>
 * <li>then, in that one tick, it clears the boxes (blocks to air, every non-player entity discarded), runs the body, and clears them again,
 * also when the body throws. A test body is synchronous, so no other test's body or world tick can run in between.</li>
 * </ol>
 * The cleared region of a box is the one {@link StartSupport#clear} and {@link StartSupport#worldHash} use: its columns, from 16 below the
 * box to 2 above it.
 */
public final class TestArea {
	private TestArea() {}

	/** The {@code maxTicks} of a test that uses {@link #run}: never the limit (see {@link #WAIT_MILLIS}). */
	public static final int TICKS = 100_000_000;
	/** How long a test waits for its chunks before it fails. */
	public static final long WAIT_MILLIS = 600_000;

	/** Ticket radius: 33 - 2 = level 31, ENTITY_TICKING for the chunk itself. The type is DRAGON: it loads and simulates, never expires and is
	 * not saved (FORCED would be written to the chunk-ticket data); only the End fight uses it otherwise. */
	private static final int RADIUS = 2;
	/** How many running tests hold a ticket on each chunk (server thread only): a shared chunk is released when the last one finishes. */
	private static final TreeMap<Long, Integer> HOLDS = new TreeMap<>();

	/** The boxes of the starts the planner makes at {@code chunk} for each structure path that starts there (the planner installed only meanwhile). */
	public static List<BoundingBox> boxes(ServerLevel level, WorldPlanner planner, ChunkPos chunk, String... structures) {
		List<BoundingBox> out = new ArrayList<>();
		WorldPlanners.overrideForTests(planner);
		try {
			for (String s : structures) {
				StructureStart st = StartSupport.start(level, s, chunk);
				if (st.isValid()) out.add(st.getBoundingBox());
			}
		} finally {
			WorldPlanners.overrideForTests(null);
		}
		return out;
	}

	/** Every chunk a box's columns touch. */
	static TreeSet<Long> chunks(List<BoundingBox> boxes) {
		TreeSet<Long> out = new TreeSet<>();
		for (BoundingBox b : boxes) for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) out.add(ChunkPos.pack(cx, cz));
		return out;
	}

	/**
	 * Runs {@code body} alone on clean {@code boxes} once their chunks are ready (see the class comment), then succeeds the test (or fails it
	 * with the body's exception). The wait is bounded by wall time ({@link #WAIT_MILLIS}), not ticks: the game-test server runs ticks back
	 * to back, so a tick budget runs out in seconds while chunks still load; give the test {@code maxTicks = TestArea.TICKS}.
	 */
	public static void run(GameTestHelper helper, List<BoundingBox> boxes, Runnable body) {
		ServerLevel level = helper.getLevel();
		TreeSet<Long> chunks = chunks(boxes);
		for (long c : chunks) {
			if (HOLDS.merge(c, 1, Integer::sum) == 1) level.getChunkSource().addTicketWithRadius(TicketType.DRAGON, ChunkPos.unpack(c), RADIUS);
		}
		long firstTick = level.getGameTime(), start = System.nanoTime();
		Throwable[] error = {null};
		// a sequence step is retried every tick while it throws GameTestAssertException; anything else must not escape it (it would
		// leave GameTestInfo's tick), so the body's own exception is kept and reported by the next step
		helper.startSequence().thenWaitUntil(() -> {
			int waiting = 0;
			for (long c : chunks) if (!level.areEntitiesActuallyLoadedAndTicking(ChunkPos.unpack(c))) waiting++;
			long waited = (System.nanoTime() - start) / 1_000_000L;
			if (waiting > 0 && waited < WAIT_MILLIS) throw helper.assertionException(Component.literal("waiting for " + waiting + " of " + chunks.size() + " chunks"));
			if (waiting > 0) {
				release(level, chunks);
				error[0] = new IllegalStateException(waiting + " of " + chunks.size() + " test-area chunks were not loaded with their entities after " + waited + " ms");
				return;
			}
			FractalBuildingsMod.LOGGER.info("[TESTISO] {} chunks ready after {} ticks, {} ms", chunks.size(), level.getGameTime() - firstTick, waited);
			try {
				clear(level, boxes);
				body.run();
			} catch (Throwable t) {
				FractalBuildingsMod.LOGGER.error("[TESTISO] test body failed", t);
				error[0] = t;
			} finally {
				try {
					clear(level, boxes);
				} finally {
					release(level, chunks);
				}
			}
		}).thenExecute(() -> {
			if (error[0] != null) throw helper.assertionException(Component.literal(String.valueOf(error[0].getMessage())));
		}).thenSucceed();
	}

	/**
	 * Waits, one tick at a time and for at most {@code maxMillis} of wall time, until {@code ready} holds, then runs {@code verify} and succeeds
	 * the test (or fails it with verify's exception, or with a timeout message). For anything that waits on wall time or on other threads:
	 * the game-test server runs ticks back to back, so a {@code maxTicks} budget can pass in milliseconds. Give such a test
	 * {@code maxTicks = TestArea.TICKS} (a sequence registers nothing per tick, unlike {@code helper.onEachTick}).
	 */
	public static void waitThen(GameTestHelper helper, long maxMillis, java.util.function.BooleanSupplier ready, Runnable verify) {
		long start = System.nanoTime();
		Throwable[] error = {null};
		helper.startSequence().thenWaitUntil(() -> {
			long waited = (System.nanoTime() - start) / 1_000_000L;
			boolean ok = ready.getAsBoolean();
			if (!ok && waited < maxMillis) throw helper.assertionException(Component.literal("waiting"));
			if (!ok) {
				error[0] = new IllegalStateException("not ready after " + waited + " ms");
				return;
			}
			try {
				verify.run();
			} catch (Throwable t) {
				FractalBuildingsMod.LOGGER.error("[TESTISO] test check failed", t);
				error[0] = t;
			}
		}).thenExecute(() -> {
			if (error[0] != null) throw helper.assertionException(Component.literal(String.valueOf(error[0].getMessage())));
		}).thenSucceed();
	}

	private static void release(ServerLevel level, TreeSet<Long> chunks) {
		for (long c : chunks) {
			if (HOLDS.merge(c, -1, Integer::sum) <= 0) {
				HOLDS.remove(c);
				level.getChunkSource().removeTicketWithRadius(TicketType.DRAGON, ChunkPos.unpack(c), RADIUS);
			}
		}
	}

	/** Air over each box's region, then every non-player entity in it discarded (after the blocks, so items a removed block drops go too). */
	public static void clear(ServerLevel level, List<BoundingBox> boxes) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (BoundingBox b : boxes) {
			int y0 = Math.max(level.getMinY(), b.minY() - 16), y1 = b.maxY() + 2;
			for (int y = y0; y <= y1; y++) for (int z = b.minZ(); z <= b.maxZ(); z++) for (int x = b.minX(); x <= b.maxX(); x++) {
				p.set(x, y, z);
				if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		for (BoundingBox b : boxes) {
			int y0 = Math.max(level.getMinY(), b.minY() - 16), y1 = b.maxY() + 2;
			AABB box = new AABB(b.minX(), y0, b.minZ(), b.maxX() + 1, y1 + 1, b.maxZ() + 1);
			for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) e.discard();
		}
	}
}
