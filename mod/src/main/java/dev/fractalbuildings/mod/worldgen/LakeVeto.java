package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * Keeps vanilla lakes out of our pieces (ORDERFAIL, 2026-10-27). {@code LakeFeature.place} (26.3 bytecode: {@code origin.offset(-8, -4,
 * -8)}, a 16 x 8 x 16 grid) writes lava, cave air and its stone barrier over anything outside {@code #features_cannot_replace} and has
 * no structure check. A lake whose origin chunk is decorated after a neighbour chunk holding our pieces overwrites them; one decorated
 * before is overwritten by them (and its shape test reads them). So the gate city s1_m10 had lava and cave air in a building in 5 of
 * the order test's 9 orders and its walls in the other 4. The mixin asks {@link #vetoes} first: a lake whose grid (plus one block) can
 * meet a cell our districts or roads own is not placed, in any order. A pure function of the lake's origin and the world's planner.
 */
public final class LakeVeto {
	/** Lakes not placed, for tests and logs. */
	public static final AtomicLong VETOED = new AtomicLong();
	private static final AtomicLong LOGGED = new AtomicLong();

	private LakeVeto() {}

	/** The cells a lake at {@code origin} can write or read: its 16 x 8 x 16 grid grown by one block. */
	public static Box3 box(BlockPos origin) {
		return new Box3(origin.getX() - 9, origin.getY() - 5, origin.getZ() - 9, 18, 10, 18);
	}

	/** True when the lake at {@code origin} must not be placed. Failures place the lake (vanilla) and count in {@link WorldPlanners#FAILURES}. */
	public static boolean vetoes(WorldGenLevel level, ChunkGenerator gen, BlockPos origin) {
		if (Mutations.is(Mutations.LAKES) || level.getLevel().dimension() != Level.OVERWORLD) return false;
		Optional<WorldPlanner> planner = WorldPlanners.of(level.getLevel(), gen);
		if (planner.isEmpty()) return false;
		try {
			if (!planner.get().claimsNear(box(origin))) return false;
		} catch (RuntimeException e) {
			WorldPlanners.FAILURES.incrementAndGet();
			FractalBuildingsMod.LOGGER.error("Could not tell whether the lake at {} meets a settlement or road; placing it", origin, e);
			return false;
		}
		VETOED.incrementAndGet();
		if (LOGGED.incrementAndGet() <= 20) FractalBuildingsMod.LOGGER.info("[lake-veto] no lake at {}: it would meet our pieces", origin);
		return true;
	}
}
