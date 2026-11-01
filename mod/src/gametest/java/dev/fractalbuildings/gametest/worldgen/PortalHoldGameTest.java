package dev.fractalbuildings.gametest.worldgen;

import static dev.fractalbuildings.gametest.worldgen.StartSupport.check;

import dev.fractalbuildings.mod.teleport.PortalHold;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
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
	/** A pig standing in a fresh x-axis portal built in the test area; checks that its nether exit area starts cold. */
	private record Setup(ServerLevel level, ServerLevel nether, BlockPos base, Pig pig, int ex, int ez) {}

	private static Setup setup(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		check(nether != null, "the test server has no nether");
		BlockPos base = helper.absolutePos(new BlockPos(1, 1, 3));
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
		Setup s = setup(helper);
		UUID id = s.pig.getUUID();
		AtomicInteger ticks = new AtomicInteger();
		String[] at5 = {null};
		TestArea.waitThen(helper, 600_000, () -> {
			if (ticks.incrementAndGet() == 5) at5[0] = "present=" + (!s.pig.isRemoved() && s.pig.level() == s.level) + " holdOpen=" + PortalHold.waitingFor(Level.NETHER, s.ex, s.ez);
			return s.pig.isRemoved();
		}, () -> {
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
		// by wall time, not ticks: in the 40-test batch the server once managed 53 ticks in 150 s (verifier, beebox job 316)
		PortalHold.testIdleTicks = 5;
		Setup s;
		try {
			s = setup(helper);
		} catch (RuntimeException e) {
			PortalHold.testIdleTicks = null;
			throw e;
		}
		AtomicInteger ticks = new AtomicInteger();
		String[] seen = {null};
		TestArea.waitThen(helper, TestArea.WAIT_MILLIS, () -> {
			int t = ticks.incrementAndGet();
			if (t == 3) {
				PortalHold.testIdleTicks = null; // the pig's request has opened by now with its 5; later requests are not affected
				seen[0] = "holdOpen=" + PortalHold.waitingFor(Level.NETHER, s.ex, s.ez) + " present=" + !s.pig.isRemoved();
				s.pig.snapTo(s.base.getX() + 2.0, s.base.getY() + 1, s.base.getZ() + 4.5, 0f, 0f); // out of the portal, 4 blocks south
			}
			return t > 3 && !PortalHold.waitingFor(Level.NETHER, s.ex, s.ez); // dropped (or never opened: the check below says which)
		}, () -> {
			breakPortal(s);
			check("holdOpen=true present=true".equals(seen[0]), "the pig must be held at first: " + seen[0]);
			check(!s.pig.isRemoved() && s.pig.level() == s.level, "the pig that walked out must stay in the overworld, removed=" + s.pig.getRemovalReason());
			check(!PortalHold.waitingFor(Level.NETHER, s.ex, s.ez), "the request must be dropped once nobody is inside");
			s.pig.discard();
		});
	}
}
