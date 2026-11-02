package dev.fractalbuildings.gametest.worldgen;

import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.teleport.PortalHold;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * 6.30 PORTALCOLD in a real client: a survival player walks into a nether portal whose overworld exit is the cold metropolis core of seed
 * 987654321 (the PORTALCOLD measurement's worst case: 65 s of frozen server without the hold). The trip must be held (the server keeps
 * ticking: no tick near the 60 s watchdog), then complete, and the player must arrive standing, unhurt, by the exit portal. Logs the hold
 * length and the longest server tick ({@code [PORTALHOLD]} lines).
 */
// gametest-order: 9
public class PortalHoldClientGameTest implements FabricClientGameTest {
	/** The metropolis centre of seed 987654321 (PerfSupport.METRO_X/Z); the nether portal stands at it / 8. */
	private static final int MX = 2432, MZ = -5696;
	/**
	 * Longest acceptable server tick while the trip waits and goes, ms. Unheld, one tick takes about 46 s (the perf worker); the stale supporting
	 * block after the trip (PortalHold.afterTrip) cost a further 8-15 s tick here; with both fixed the longest was 110 ms on the perf worker.
	 */
	private static final long MAX_TICK_MS = 3_000;

	private static volatile boolean armed;
	private static volatile long tickStart, maxTickNanos;
	private static volatile Thread serverThread;
	private static volatile net.minecraft.server.MinecraftServer server;
	private static volatile boolean inTick, dumped;
	private static boolean registered;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (dev.fractalbuildings.gametest.compat.CompatOnly.skip("PortalHoldClientGameTest")) return;
		if (dev.fractalbuildings.gametest.perf.PerfGate.skipOthers()) return; // B8: a perf-only run
		if (!registered) {
			registered = true;
			ServerTickEvents.START_SERVER_TICK.register(s -> { tickStart = System.nanoTime(); serverThread = Thread.currentThread(); server = s; inTick = true; dumped = false; });
			ServerTickEvents.END_SERVER_TICK.register(s -> { inTick = false; if (armed) maxTickNanos = Math.max(maxTickNanos, System.nanoTime() - tickStart); });
			Thread watch = new Thread(PortalHoldClientGameTest::watchLongTicks, "portalhold-longtick");
			watch.setDaemon(true);
			watch.start();
		}
		try (TestSingleplayerContext world = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(c -> c.setSeed("987654321"))
				.create()) {
			context.runOnClient(client -> client.options.renderDistance().set(6));
			final int nx = Math.floorDiv(MX, 8), nz = Math.floorDiv(MZ, 8), ny = 70;
			world.getServer().runOnServer(server -> {
				ServerLevel over = server.overworld();
				int[] r = PortalHold.searchChunks(MX + 8, MZ + 4, true);
				for (int cz = r[2]; cz <= r[3]; cz++) for (int cx = r[0]; cx <= r[1]; cx++)
					if (over.getChunkSource().getChunkNow(cx, cz) != null) throw new IllegalStateException("the metropolis exit area is not cold");
				ServerLevel nether = server.getLevel(Level.NETHER);
				for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) nether.getChunk((nx >> 4) + dx, (nz >> 4) + dz, ChunkStatus.FULL, true);
				buildPortal(nether, nx, ny, nz);
			});
			world.getServer().runCommand("difficulty peaceful");
			world.getServer().runCommand("gamemode survival @p");
			world.getServer().runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run tp @p %.1f %d %.1f", nx - 2 + 0.5, ny, nz + 2 + 0.5));
			for (int i = 0; i < 120 && !inNether(world); i++) context.waitTicks(5);
			if (!inNether(world)) throw new IllegalStateException("the player never reached the nether");
			context.waitTicks(60); // the dimension change's portal cooldown runs out, the view loads
			// into the portal (same dimension, 4 blocks: no teleport hold); survival waits 80 ticks in it, then PortalHold takes over
			world.getServer().runOnServer(server -> { maxTickNanos = 0; armed = true; });
			world.getServer().runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run tp @p %.1f %d %.1f", nx + 1.0, ny + 1, nz + 0.5));
			if (!inNether(world)) throw new IllegalStateException("the step into the portal left the nether");
			long t0 = System.nanoTime();
			boolean everHeld = false, arrived = false;
			String last = "";
			while ((System.nanoTime() - t0) / 1_000_000_000L < 300 && !arrived) {
				context.waitTicks(10);
				double[] v = state(world);
				everHeld |= v[6] > 0;
				arrived = v[7] > 0;
				last = describe(v, t0);
			}
			long holdMs = (System.nanoTime() - t0) / 1_000_000L;
			FractalBuildingsMod.LOGGER.info("[PORTALHOLD] trip into the cold metropolis took {} ms from stepping in (incl. 4 s survival portal wait); held={}; {}", holdMs, everHeld, PortalHold.lastStats);
			if (!arrived) throw new IllegalStateException("the portal trip never happened: " + last);
			boolean standing = false;
			for (int i = 0; i < 120 && !standing; i++) {
				context.waitTicks(10);
				double[] v = state(world);
				last = describe(v, t0);
				if (v[3] < 20) throw new IllegalStateException("the player took damage: " + last);
				standing = v[4] > 0 && v[5] > 0;
			}
			world.getServer().runOnServer(server -> armed = false);
			long maxTickMs = maxTickNanos / 1_000_000L;
			FractalBuildingsMod.LOGGER.info("[PORTALHOLD] longest server tick from stepping in to standing: {} ms; arrived: {}", maxTickMs, last);
			if (!everHeld) throw new IllegalStateException("the trip was never held (PortalHold did not engage): " + PortalHold.lastStats);
			if (!PortalHold.lastStats.contains("timedOut=false")) throw new IllegalStateException("the hold timed out: " + PortalHold.lastStats);
			if (!standing) throw new IllegalStateException("the player never ended up standing: " + last);
			if (maxTickMs > MAX_TICK_MS) throw new IllegalStateException("a server tick took " + maxTickMs + " ms (limit " + MAX_TICK_MS + ")");
			try {
				world.getConnection().waitForChunksRender();
			} catch (AssertionError e) {
				FractalBuildingsMod.LOGGER.warn("[PORTALHOLD] chunks did not finish rendering in time; taking the screenshot anyway");
			}
			context.takeScreenshot("portal-hold-arrived");
		} finally {
			armed = false;
		}
	}

	/** While armed, logs the server thread's stack once in any tick that runs over 1.5 s (what a long tick waits on). */
	private static void watchLongTicks() {
		try {
			while (true) {
				Thread.sleep(100);
				Thread st = serverThread;
				if (st == null || !armed || !inTick || dumped || System.nanoTime() - tickStart < 1_500_000_000L) continue;
				dumped = true;
				StringBuilder b = new StringBuilder();
				StackTraceElement[] tr = st.getStackTrace();
				for (int i = 0; i < Math.min(80, tr.length); i++) b.append("\n    at ").append(tr[i]);
				FractalBuildingsMod.LOGGER.info("[PORTALHOLD] long tick ({} ms so far; hold: {}), server thread:{}", (System.nanoTime() - tickStart) / 1_000_000, PortalHold.lastStats, b);
				FractalBuildingsMod.LOGGER.info("[PORTALHOLD] long tick: {}", chunksAroundPlayer());
			}
		} catch (InterruptedException ignored) { }
	}

	/** Debug, read off the server thread (racy, for the log only): the player's level and position, and ticket level / latest status of the 5 x 5 chunks round it. */
	private static String chunksAroundPlayer() {
		try {
			var p = server.getPlayerList().getPlayers().get(0);
			var level = (ServerLevel) p.level();
			int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;
			StringBuilder b = new StringBuilder(level.dimension().identifier() + " " + p.position() + " chunk " + pcx + "," + pcz + ":");
			for (int dz = -2; dz <= 2; dz++) {
				b.append("\n   ");
				for (int dx = -2; dx <= 2; dx++) {
					var h = level.getChunkSource().chunkMap.getUpdatingChunkIfPresent(net.minecraft.world.level.ChunkPos.pack(pcx + dx, pcz + dz));
					b.append(" ").append(h == null ? "-" : h.getTicketLevel() + "/" + h.getLatestStatus());
				}
			}
			return b.toString();
		} catch (RuntimeException e) {
			return "? " + e;
		}
	}

	private static boolean inNether(TestSingleplayerContext world) {
		AtomicReference<Boolean> in = new AtomicReference<>(false);
		world.getServer().runOnServer(server -> in.set(server.getPlayerList().getPlayers().get(0).level().dimension() == Level.NETHER));
		return in.get();
	}

	/** x, y, z, health, onGround, solid below, held, in the overworld. */
	private static double[] state(TestSingleplayerContext world) {
		AtomicReference<double[]> st = new AtomicReference<>();
		world.getServer().runOnServer(server -> {
			var p = server.getPlayerList().getPlayers().get(0);
			boolean over = p.level() == server.overworld();
			boolean solid = p.level().getBlockState(p.blockPosition().below()).isSolid();
			st.set(new double[] {p.getX(), p.getY(), p.getZ(), p.getHealth(), p.onGround() ? 1 : 0, solid ? 1 : 0, PortalHold.holding(p) ? 1 : 0, over ? 1 : 0});
		});
		return st.get();
	}

	private static String describe(double[] v, long t0) {
		return String.format(Locale.ROOT, "t=%ds pos=%.1f,%.1f,%.1f hp=%.1f onGround=%s solidBelow=%s held=%s overworld=%s", (System.nanoTime() - t0) / 1_000_000_000L,
			v[0], v[1], v[2], v[3], v[4] > 0, v[5] > 0, v[6] > 0, v[7] > 0);
	}

	/** An obsidian floor and frame (4 wide, 5 tall) with a 2 x 3 x-axis portal, clear air round it (as PortalColdHarness builds it). */
	private static void buildPortal(ServerLevel nether, int x, int y, int z) {
		for (int dx = -4; dx <= 4; dx++) for (int dz = -3; dz <= 4; dz++) for (int dy = -1; dy <= 6; dy++)
			nether.setBlock(new BlockPos(x + dx, y + dy, z + dz), dy == -1 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
		BlockState obs = Blocks.OBSIDIAN.defaultBlockState();
		for (int dx = -1; dx <= 2; dx++) for (int dy = 0; dy <= 4; dy++) if (dx == -1 || dx == 2 || dy == 0 || dy == 4) nether.setBlock(new BlockPos(x + dx, y + dy, z), obs, 3);
		for (int dx = 0; dx <= 1; dx++) for (int dy = 1; dy <= 3; dy++)
			nether.setBlock(new BlockPos(x + dx, y + dy, z), Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X), 18);
	}
}
