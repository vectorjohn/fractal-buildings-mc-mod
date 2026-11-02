package dev.fractalbuildings.mod.worldgen;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;

/**
 * COLDTICK (6.37): "are these chunks really there", for vanilla code on the server thread that asks {@code Level.hasChunksAt} and then
 * reads blocks. {@code hasChunksAt} is true for a chunk that only has a FULL-level ticket ({@code ServerChunkCache.hasChunk}, 26.3
 * ServerChunkCache.java:256-260), generated or not, so the read that follows generates it synchronously on the server thread. Vanilla
 * generation takes milliseconds; a cold district chunk of ours takes seconds, so the whole server stops for that long (the 9-11 s and
 * 27 s ticks of COLDARRIVAL: {@code CatSpawner.tick} every 1200 ticks, beside a player whose surroundings were still generating).
 */
public final class ReadyChunks {
	private ReadyChunks() {}

	/** True when every chunk meeting the block box {@code [x0..x1] x [z0..z1]} is loaded and FULL now ({@code getChunkNow}; never loads). */
	public static boolean allFull(ServerLevel level, int x0, int z0, int x1, int z1) {
		var cache = level.getChunkSource();
		for (int cz = SectionPos.blockToSectionCoord(z0); cz <= SectionPos.blockToSectionCoord(z1); cz++) {
			for (int cx = SectionPos.blockToSectionCoord(x0); cx <= SectionPos.blockToSectionCoord(x1); cx++) {
				if (cache.getChunkNow(cx, cz) == null) return false;
			}
		}
		return true;
	}
}
