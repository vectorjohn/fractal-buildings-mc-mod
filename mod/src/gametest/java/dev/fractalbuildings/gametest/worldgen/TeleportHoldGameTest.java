package dev.fractalbuildings.gametest.worldgen;

import static dev.fractalbuildings.gametest.worldgen.StartSupport.check;

import dev.fractalbuildings.mod.teleport.TeleportHold;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Server game tests of the teleport hold (TPHOLD): a long jump into cold terrain waits for FULL chunks, times out, and short hops are untouched. */
public class TeleportHoldGameTest {
	private static TeleportTransition to(ServerLevel level, double x, double z) {
		return new TeleportTransition(level, new Vec3(x, 80, z), Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING);
	}

	/** Polls each tick; when ready runs the checks and ends the test (the sequence API only retries on its own assertion type). */
	private static void waitThen(GameTestHelper helper, java.util.function.BooleanSupplier ready, Runnable verify) {
		boolean[] done = {false};
		helper.onEachTick(() -> {
			if (done[0] || !ready.getAsBoolean()) return;
			done[0] = true;
			try { verify.run(); } finally { reset(); }
			helper.succeed();
		});
	}

	private static void reset() {
		TeleportHold.testLoad = null;
		TeleportHold.testReleased = null;
		TeleportHold.timeoutMillis = 30_000;
	}

	@GameTest(maxTicks = 60000)
	public void longJumpWaitsForFullChunks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		double tx = 41000.5, tz = 41000.5;
		check(level.getChunkSource().getChunkNow(((int) tx) >> 4, ((int) tz) >> 4) == null, "the target must start cold");
		Vec3 start = p.position();
		AtomicReference<String> seen = new AtomicReference<>();
		TeleportHold.testReleased = (pl, full, timedOut) -> {
			seen.set("full=" + full + " timedOut=" + timedOut + " movedYet=" + (pl.position().distanceTo(start) > 1));
		};
		p.teleport(to(level, tx, tz));
		check(p.position().equals(start), "the player must stay put while the hold runs: " + p.position());
		check(TeleportHold.holding(p), "the teleport must be held");
		waitThen(helper, () -> seen.get() != null, () -> {
			check("full=true timedOut=false movedYet=false".equals(seen.get()), "released with " + seen.get());
			check(p.position().distanceTo(new Vec3(tx, 80, tz)) < 2, "the player must have arrived: " + p.position());
			check(!TeleportHold.holding(p), "the hold must be gone");
		});
	}

	@GameTest(maxTicks = 400)
	public void timeoutTeleportsAnyway(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		double tx = -42000.5, tz = 42000.5;
		TeleportHold.timeoutMillis = 300;
		TeleportHold.testLoad = () -> new CompletableFuture<>(); // never completes
		AtomicInteger timedOut = new AtomicInteger();
		TeleportHold.testReleased = (pl, full, to) -> { if (to) timedOut.incrementAndGet(); };
		p.teleport(to(level, tx, tz));
		TeleportHold.timeoutMillis = 30_000; // the hold captured its own deadline; do not leak 300 ms into other tests
		check(TeleportHold.holding(p), "the teleport must be held");
		waitThen(helper, () -> timedOut.get() > 0, () -> {
			check(timedOut.get() == 1, "the timeout must fire once, was " + timedOut.get());
			check(p.position().distanceTo(new Vec3(tx, 80, tz)) < 2, "the player must have been teleported anyway: " + p.position());
		});
	}

	@GameTest
	public void shortHopIsNotHeld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Vec3 start = p.position();
		Vec3 dest = start.add(20, 0, 0);
		p.teleport(to(level, dest.x, dest.z));
		check(!TeleportHold.holding(p), "a short hop must not be held");
		check(p.position().distanceTo(new Vec3(dest.x, 80, dest.z)) < 2, "the player must move at once: " + p.position());
		helper.succeed();
	}

	@GameTest
	public void disabledMeansNoHold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		TeleportHold.enabled = false;
		try {
			p.teleport(to(level, 43000.5, 43000.5));
			check(!TeleportHold.holding(p), "disabled: no hold");
			check(p.getX() > 42000, "the player must move at once");
		} finally {
			TeleportHold.enabled = true;
		}
		helper.succeed();
	}

	private static TeleportTransition rel(ServerLevel level, double dx, double dy, double dz) {
		return new TeleportTransition(level, new Vec3(dx, dy, dz), Vec3.ZERO, 0f, 0f,
			java.util.Set.of(net.minecraft.world.entity.Relative.X, net.minecraft.world.entity.Relative.Y, net.minecraft.world.entity.Relative.Z), TeleportTransition.DO_NOTHING);
	}

	@GameTest
	public void relativeShortHopIsNotHeld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setPos(30000.5, 80, 30000.5); // far from the origin, where a "~ ~5 ~" read as absolute would look like a long jump
		Vec3 start = p.position();
		p.teleport(rel(level, 0, 5, 0));
		check(!TeleportHold.holding(p), "/tp ~ ~5 ~ must not be held");
		check(p.position().distanceTo(start.add(0, 5, 0)) < 1, "the player must move at once: " + p.position());
		helper.succeed();
	}

	@GameTest(maxTicks = 60000)
	public void relativeLongJumpIsHeldOnTheRightChunks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.setPos(30000.5, 80, 30000.5);
		Vec3 start = p.position();
		Vec3 dest = start.add(10000, 0, 0);
		CompletableFuture<Void> gate = new CompletableFuture<>(); // the real FULL wait is covered by longJumpWaitsForFullChunks; here only the destination matters
		TeleportHold.testLoad = () -> gate;
		p.teleport(rel(level, 10000, 0, 0));
		TeleportHold.testLoad = null;
		check(TeleportHold.holding(p), "/tp ~10000 ~ ~ must be held");
		check(p.position().equals(start), "the player must stay put");
		check(TeleportHold.destination(p).distanceTo(dest) < 0.01, "the held destination must be the resolved one: " + TeleportHold.destination(p));
		gate.complete(null);
		waitThen(helper, () -> !TeleportHold.holding(p), () -> check(p.position().distanceTo(dest) < 2, "the player must arrive at the resolved destination: " + p.position()));
	}

	@GameTest(maxTicks = 60000)
	public void newerTeleportReplacesTheWaitingOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		CompletableFuture<Void> gate = new CompletableFuture<>();
		TeleportHold.testLoad = () -> gate;
		p.teleport(to(level, 38000.5, 38000.5));
		check(TeleportHold.holding(p), "first teleport held");
		p.teleport(to(level, -38000.5, 38500.5));
		TeleportHold.testLoad = null;
		check(TeleportHold.holding(p) && !TeleportHold.ticketHeld(p), "the second hold starts fresh");
		gate.complete(null);
		waitThen(helper, () -> !TeleportHold.holding(p), () -> check(p.getX() < -37000, "the player must land at the second destination: " + p.position()));
	}

	@GameTest
	public void deadPlayerDropsTheHold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		TeleportHold.testLoad = () -> new CompletableFuture<>();
		p.teleport(to(level, 46000.5, 46000.5));
		TeleportHold.testLoad = null;
		check(TeleportHold.holding(p), "held");
		p.setHealth(0f);
		helper.runAfterDelay(5, () -> {
			check(!TeleportHold.holding(p), "a dead player's hold must be dropped");
			check(p.getX() < 100, "and the player must not be teleported: " + p.position());
			helper.succeed();
		});
	}
}
