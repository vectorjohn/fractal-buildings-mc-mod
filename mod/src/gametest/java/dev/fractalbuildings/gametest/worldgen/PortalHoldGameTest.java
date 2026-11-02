package dev.fractalbuildings.gametest.worldgen;

import static dev.fractalbuildings.gametest.worldgen.StartSupport.check;

import dev.fractalbuildings.mod.teleport.PortalHold;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;

/**
 * 6.30 PORTALCOLD: a mob that walks into a nether portal whose nether exit area is cold is held in the portal (the server keeps ticking)
 * until the chunks vanilla's exit search reads are generated, then vanilla sends it through unchanged; one that leaves the portal ends the
 * request. A pig is used because mobs are ticked by the level (a mock player is not); the player path is the same {@code PortalHold.gate}
 * call from the same mixin ({@code PortalHoldClientGameTest}).
 */
// gametest-order: 8
public class PortalHoldGameTest {
	/** A pig standing in a fresh x-axis portal built in the test's own area; checks that its nether exit area starts cold. */
	private record Setup(ServerLevel level, ServerLevel nether, BlockPos base, Pig pig, int ex, int ez) {}

	/**
	 * Each test builds its portal 8192 * slot blocks (in x and z) from its test structure, so its nether exit area (the portal's
	 * coordinates / 8) is one no other test touches. The test structures of a batch lie close together, so the exit areas of the two tests here
	 * coincided and whichever ran second found the area warmed by the first (the "must start cold" flake). The slots must differ between the
	 * tests of this class; the client test has its own world.
	 */
	private static final int SLOT_BLOCKS = 8192;

	/**
	 * Holds the chunk of the test's own area with a ticket, waits until it is loaded and ticking, builds the portal and the pig there, then
	 * waits (one tick at a time, {@link TestArea#waitThen}) on {@code ready} of that setup and runs {@code verify}. The ticket, the portal and
	 * the pig go at the end, whatever happens; {@code atEnd} runs then too.
	 */
	private static void inOwnArea(GameTestHelper helper, int slot, Runnable atEnd, Function<Setup, BooleanSupplier> ready, Consumer<Setup> verify) {
		ServerLevel level = helper.getLevel();
		BlockPos home = helper.absolutePos(new BlockPos(1, 1, 3));
		int off = slot * SLOT_BLOCKS / 16;
		ChunkPos chunk = new ChunkPos(Math.floorDiv(home.getX(), 16) + off, Math.floorDiv(home.getZ(), 16) + off);
		level.getChunkSource().addTicketWithRadius(TicketType.DRAGON, chunk, 2);
		Setup[] made = {null};
		BooleanSupplier[] next = {null};
		boolean[] done = {false};
		Runnable cleanup = () -> {
			if (done[0]) return;
			done[0] = true;
			atEnd.run();
			level.getChunkSource().removeTicketWithRadius(TicketType.DRAGON, chunk, 2);
			if (made[0] != null) { breakPortal(made[0]); made[0].pig.discard(); }
		};
		try {
			TestArea.waitThen(helper, TestArea.WAIT_MILLIS, () -> {
				try {
					if (made[0] == null) {
						if (!level.areEntitiesActuallyLoadedAndTicking(chunk)) return false;
						made[0] = setup(level, chunk);
						next[0] = ready.apply(made[0]);
						return false; // the first tick of the wait is the next one
					}
					return next[0].getAsBoolean();
				} catch (RuntimeException e) {
					cleanup.run();
					throw e;
				}
			}, () -> {
				try {
					verify.accept(made[0]);
				} finally {
					cleanup.run();
				}
			});
		} catch (RuntimeException e) {
			cleanup.run();
			throw e;
		}
	}

	private static Setup setup(ServerLevel level, ChunkPos chunk) {
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		check(nether != null, "the test server has no nether");
		int bx = chunk.getMinBlockX() + 4, bz = chunk.getMinBlockZ() + 4; // the frame (4 wide) and the pig 5 south stay inside this one chunk
		BlockPos base = new BlockPos(bx, level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz), bz);
		int ex = Math.floorDiv(base.getX() + 2, 8), ez = Math.floorDiv(base.getZ(), 8); // the pig stands at x + 2.0, z + 0.5
		int[] r = PortalHold.searchChunks(ex, ez, true);
		boolean cold = false;
		for (int cz = r[2]; cz <= r[3]; cz++) for (int cx = r[0]; cx <= r[1]; cx++) cold |= nether.getChunkSource().getChunkNow(cx, cz) == null;
		check(cold, "the nether exit area round " + ex + "," + ez + " must start cold");
		for (int dx = 0; dx <= 3; dx++) for (int dy = 0; dy <= 4; dy++) { // a frame 4 wide and 5 tall round a 2 x 3 portal
			boolean frame = dx == 0 || dx == 3 || dy == 0 || dy == 4;
			level.setBlock(base.offset(dx, dy, 0), frame ? Blocks.OBSIDIAN.defaultBlockState()
				: Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X), 2 | 16);
		}
		Pig pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
		check(pig != null, "no pig");
		pig.setNoAi(true);
		pig.snapTo(base.getX() + 2.0, base.getY() + 1, base.getZ() + 0.5, 0f, 0f);
		level.addFreshEntity(pig);
		return new Setup(level, nether, base, pig, ex, ez);
	}

	private static void breakPortal(Setup s) {
		for (int dx = 0; dx <= 3; dx++) for (int dy = 0; dy <= 4; dy++) s.level.setBlock(s.base.offset(dx, dy, 0), Blocks.AIR.defaultBlockState(), 2 | 16);
	}

	@GameTest(maxTicks = TestArea.TICKS)
	public void coldExitHoldsThenGoes(GameTestHelper helper) {
		AtomicInteger ticks = new AtomicInteger();
		String[] at5 = {null};
		inOwnArea(helper, 1, () -> {}, s -> () -> {
			if (ticks.incrementAndGet() == 5) at5[0] = "present=" + (!s.pig.isRemoved() && s.pig.level() == s.level) + " holdOpen=" + PortalHold.waitingFor(Level.NETHER, s.ex, s.ez);
			return s.pig.isRemoved();
		}, s -> {
			UUID id = s.pig.getUUID();
			breakPortal(s);
			check("present=true holdOpen=true".equals(at5[0]), "5 ticks in, the pig must still be in the overworld with a hold open: " + at5[0] + " (went after " + ticks.get() + " ticks)");
			check(s.pig.getRemovalReason() == Entity.RemovalReason.CHANGED_DIMENSION, "the pig left by " + s.pig.getRemovalReason());
			// exit identity: the createPortal chunks must be held one status short of FULL (ticket level 34), so Level.hasChunk stays false for
			// them as in a cold world and vanilla picks the unheld exit (a FULL hold can move it; the verifier's PREPARED = FULL mutant passed before)
			check(PortalHold.lastStats.contains("portals=0") && PortalHold.lastStats.contains("status=minecraft:initialize_light") && PortalHold.lastStats.contains("timedOut=false"),
				"the hold must prepare createPortal's chunks at initialize_light, not FULL: " + PortalHold.lastStats);
			Entity there = s.nether.getEntity(id);
			check(there != null, "the pig must be in the nether");
			check(Math.abs(there.getX() - s.ex) < 20 && Math.abs(there.getZ() - s.ez) < 20, "the pig arrived far from the exit " + s.ex + "," + s.ez + ": " + there.position());
			// the trip went through vanilla's createPortal: a portal block stands by the arrival point
			BlockPos at = there.blockPosition();
			boolean portal = false;
			for (BlockPos p : BlockPos.betweenClosed(at.offset(-3, -3, -3), at.offset(3, 3, 3))) portal |= s.nether.getBlockState(p).is(Blocks.NETHER_PORTAL);
			check(portal, "no exit portal by the arrival point " + at.toShortString());
		});
	}

	@GameTest(maxTicks = TestArea.TICKS)
	public void leavingThePortalDropsTheRequest(GameTestHelper helper) {
		// the request opened for this pig gives up after 5 idle ticks instead of 100 (it keeps the value it opened with), and the wait is bounded
		// by wall time, not ticks: in the 40-test batch the server once managed 53 ticks in 150 s (verifier, perf worker job 316)
		PortalHold.testIdleTicks = 5;
		AtomicInteger ticks = new AtomicInteger();
		String[] seen = {null};
		inOwnArea(helper, 2, () -> PortalHold.testIdleTicks = null, s -> () -> {
			int t = ticks.incrementAndGet();
			if (t == 3) {
				PortalHold.testIdleTicks = null; // the pig's request has opened by now with its 5; later requests are not affected
				seen[0] = "holdOpen=" + PortalHold.waitingFor(Level.NETHER, s.ex, s.ez) + " present=" + !s.pig.isRemoved();
				s.pig.snapTo(s.base.getX() + 2.0, s.base.getY() + 1, s.base.getZ() + 4.5, 0f, 0f); // out of the portal, 4 blocks south
			}
			return t > 3 && !PortalHold.waitingFor(Level.NETHER, s.ex, s.ez); // dropped (or never opened: the check below says which)
		}, s -> {
			check("holdOpen=true present=true".equals(seen[0]), "the pig must be held at first: " + seen[0]);
			check(!s.pig.isRemoved() && s.pig.level() == s.level, "the pig that walked out must stay in the overworld, removed=" + s.pig.getRemovalReason());
			check(!PortalHold.waitingFor(Level.NETHER, s.ex, s.ez), "the request must be dropped once nobody is inside");
		});
	}

	/** John 2026-11-02: past the stuck-generation limit the trip is cancelled, never sent into chunks that are not ready. */
	@GameTest
	public void defaultLimitIsPinned(GameTestHelper helper) {
		check(PortalHold.DEFAULT_MAX_HOLD_MILLIS == 60_000L, "default stuck-generation limit " + PortalHold.DEFAULT_MAX_HOLD_MILLIS);
		helper.succeed();
	}

	@GameTest(maxTicks = TestArea.TICKS)
	public void stuckHoldAbortsTheTrip(GameTestHelper helper) {
		// limit 0 for this test's own exit only (the gametests of a batch run concurrently, so no global limit): the request is already past
		// its limit at its first check, while the chunks are certainly not ready (the POI chunks are requested at that very call)
		AtomicInteger ticks = new AtomicInteger(), aborted = new AtomicInteger(), abortedAt = new AtomicInteger(-1);
		AtomicReference<UUID> pigId = new AtomicReference<>();
		int[] exit = {Integer.MIN_VALUE, Integer.MIN_VALUE};
		PortalHold.testLimit = p -> Math.abs(p.getX() - exit[0]) <= 16 && Math.abs(p.getZ() - exit[1]) <= 16 ? 0 : -1;
		PortalHold.testAborted = e -> { if (e.getUUID().equals(pigId.get()) && aborted.incrementAndGet() == 1) abortedAt.set(ticks.get()); };
		Runnable restore = () -> { PortalHold.testLimit = null; PortalHold.testAborted = null; };
		String[] seen = {null};
		inOwnArea(helper, 3, restore, s -> {
			pigId.set(s.pig.getUUID());
			exit[0] = s.ex;
			exit[1] = s.ez;
			return () -> {
				int t = ticks.incrementAndGet();
				if (abortedAt.get() < 0) return false;
				if (t == abortedAt.get() + 1) seen[0] = "present=" + (!s.pig.isRemoved() && s.pig.level() == s.level) + " holdOpen=" + PortalHold.waitingFor(Level.NETHER, s.ex, s.ez);
				return t >= abortedAt.get() + 40; // the pig keeps standing in the portal for 40 more ticks
			};
		}, s -> {
			UUID id = s.pig.getUUID();
			check("present=true holdOpen=false".equals(seen[0]), "right after the abort the pig must be in the overworld with no request open: " + seen[0]);
			check(!s.pig.isRemoved() && s.pig.level() == s.level, "the pig must not have gone through: " + s.pig.getRemovalReason());
			check(!PortalHold.waitingFor(Level.NETHER, s.ex, s.ez), "no new request may open while the pig stays in the portal");
			check(s.nether.getEntity(id) == null, "the pig must not be in the nether");
		});
	}
}
