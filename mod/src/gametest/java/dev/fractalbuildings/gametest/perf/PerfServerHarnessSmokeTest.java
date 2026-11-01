package dev.fractalbuildings.gametest.perf;

import static dev.fractalbuildings.gametest.worldgen.StartSupport.check;

import dev.fractalbuildings.gametest.worldgen.TestArea;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

/**
 * The perf harness's mock player ({@link MockPlayer}) is a real player: in the player list, a spectator, and the per-tick
 * {@link MockPlayer#flyTo} the flight uses (short hops) moves it and its chunk view. (Chunk loading around it needs a real view distance; the game-test world has 0, so that is checked by the dedicated run.)
 */
// gametest-order: 11
public class PerfServerHarnessSmokeTest {
	@GameTest(maxTicks = TestArea.TICKS)
	public void mockPlayerJoinsAndFliesLikeTheHarness(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = MockPlayer.join(level.getServer(), level, "perf-smoke", 4);
		check(level.getServer().getPlayerList().getPlayers().contains(p), "the mock player must be in the player list");
		check(p.gameMode() == GameType.SPECTATOR, "spectator");
		int[] n = {0};
		// the first hop teleports 3000 blocks, which TeleportHold holds until the 3x3 chunks at the target are FULL (up to its 60 s cap): at host
		// load 30 that plus the 60 hops passed the old 120 s budget ("not ready after 132947 ms on tick 13"), so the budget is the suite-wide wall budget
		TestArea.waitThen(helper, TestArea.WAIT_MILLIS, () -> {
			// the harness's loop: one short hop per tick along a line, from where the player already is
			double x = 3000 + n[0] * PerfSupport.ELYTRA_PER_TICK;
			if (n[0] == 0) MockPlayer.flyTo(p, 3000, 160, 3000);
			else if (!dev.fractalbuildings.mod.teleport.TeleportHold.holding(p) && p.getX() > 2990) MockPlayer.flyTo(p, x, 160, 3000);
			if (p.getX() > 2990) n[0]++;
			return n[0] >= 60;
		}, () -> {
			check(p.getX() > 3050, "the player must have flown along the line, x=" + p.getX());
			// PERFSRV r5: the chunk view follows (ChunkMap.move); with teleportTo alone it stayed where the player joined and the flight loaded nothing
			check(p.getLastSectionPos().x() == net.minecraft.core.SectionPos.blockToSectionCoord(p.getX()), "the player's chunk view must follow it: section x " + p.getLastSectionPos().x() + " at block x " + p.getX());
			p.remove(Entity.RemovalReason.DISCARDED);
		});
	}
}
