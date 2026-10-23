package dev.fractalbuildings.mod.gameplay;

import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.world.WorldHooks.WorldHook;
import java.util.UUID;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.WorldGenLevel;

/** One villager at a bed (plan decision 2). The UUID derives from the world seed and the bed, so the same world always has the same villager ids. */
final class Villagers {
	private Villagers() {}

	static boolean spawn(WorldGenLevel level, WorldHook bed, long seed) {
		Villager v = EntityTypes.VILLAGER.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (v == null) return false;
		v.setPos(bed.x() + 0.5, bed.y() + 0.6, bed.z() + 0.5);
		v.setUUID(new UUID(bed.seed(seed, "villager-uuid-hi"), bed.seed(seed, "villager-uuid-lo")));
		v.setPersistenceRequired();
		v.finalizeSpawn(level, level.getCurrentDifficultyAt(v.blockPosition()), EntitySpawnReason.STRUCTURE, null);
		level.addFreshEntityWithPassengers(v);
		return true;
	}
}
