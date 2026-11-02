package dev.fractalbuildings.gametest.worldgen;

import static dev.fractalbuildings.gametest.worldgen.StartSupport.check;

import dev.fractalbuildings.mod.teleport.TeleportHold;
import dev.fractalbuildings.mod.worldgen.ReadyChunks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.level.ChunkPos;

/**
 * COLDTICK (6.37): vanilla's cat spawner must not generate a chunk on the server thread. A chunk with a FULL-level ticket passes
 * {@code hasChunksAt} while it is still generating; before the fix {@code CatSpawner.tick} then read a block there and waited for the
 * whole generation (the 9-27 s ticks of COLDARRIVAL). Here a player stands in the middle of a cold, freshly ticketed area and the cat
 * spawner runs many times inside one server-thread call: nothing may become FULL during it (the FULL step needs the server thread, so only
 * a synchronous wait could finish it).
 */
// gametest-order: 8
public class ColdSpawnerGameTest {
	private static final int RADIUS = 4; // FULL ticket level 4 chunks around the centre: covers the spawner's reach (31 + 10 blocks)
	private static final int REACH = 48;

	@GameTest(maxTicks = TestArea.TICKS)
	public void catSpawnerDoesNotGenerateChunks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int x = 44800, z = -44800;
		ChunkPos centre = ChunkPos.containing(new net.minecraft.core.BlockPos(x, 0, z));
		check(level.getChunkSource().getChunkNow(centre.x(), centre.z()) == null, "the area must start cold");
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		p.snapTo(x + 0.5, 90, z + 0.5, 0f, 0f);
		level.getChunkSource().addTicketWithRadius(TeleportHold.TICKET, centre, RADIUS);
		// the ticket's holders appear on a later tick; then, in that same tick, the area passes hasChunksAt but is not generated yet
		TestArea.waitThen(helper, 600_000, () -> level.hasChunksAt(x - REACH, z - REACH, x + REACH, z + REACH), () -> {
			try {
				int[] before = fullCount(level, x, z);
				check(before[0] < before[1], "the area must still be generating when the spawner runs (FULL " + before[0] + "/" + before[1] + ")");
				check(!ReadyChunks.allFull(level, x - REACH, z - REACH, x + REACH, z + REACH), "hasChunksAt is true before the chunks are FULL");
				long t0 = System.nanoTime();
				// a fresh spawner fires on its first tick; it picks a random player of the level (other tests' mock players too), so try often
				for (int i = 0; i < 200; i++) new CatSpawner().tick(level, true);
				long ms = (System.nanoTime() - t0) / 1_000_000L;
				int[] after = fullCount(level, x, z);
				check(after[0] == before[0], "the cat spawner generated chunks on the server thread: FULL " + before[0] + " -> " + after[0] + " in " + ms + " ms");
			} finally {
				level.getChunkSource().removeTicketWithRadius(TeleportHold.TICKET, centre, RADIUS);
				p.discard();
			}
		});
	}

	/** {FULL chunks, all chunks} within the spawner's reach of (x, z). */
	private static int[] fullCount(ServerLevel level, int x, int z) {
		int full = 0, all = 0;
		for (int cz = (z - REACH) >> 4; cz <= (z + REACH) >> 4; cz++) for (int cx = (x - REACH) >> 4; cx <= (x + REACH) >> 4; cx++) {
			all++;
			if (level.getChunkSource().getChunkNow(cx, cz) != null) full++;
		}
		return new int[] { full, all };
	}
}
