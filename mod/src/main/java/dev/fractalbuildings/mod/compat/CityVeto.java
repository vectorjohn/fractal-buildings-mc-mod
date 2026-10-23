package dev.fractalbuildings.mod.compat;

import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The vanilla-structure veto (ADR 0003 W7, spike S8, plan B7). One predicate shared by the two mixins: a structure in
 * {@code #fractalbuildings:excluded_from_cities} may not start in a chunk whose middle lies within {@link #MARGIN} blocks of a
 * settlement's footprint. A pure function of (structure, chunk, world seed and config), so it is deterministic and order independent;
 * both the generation hook and the {@code /locate} hook ask it, so they agree. Off when the world's pinned {@code vetoVanillaVillages}
 * is false, and for generators without a planner.
 *
 * <p>The margin must cover the structure's reach beyond its start chunk: vanilla villages 80 blocks, jigsaw structures 128 + 12, plus
 * the half chunk between the start chunk's middle and its edge. 160 covers all of them.
 */
public final class CityVeto {
	public static final TagKey<Structure> EXCLUDED =
		TagKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("fractalbuildings", "excluded_from_cities"));
	public static final int MARGIN = 160;

	private CityVeto() {}

	/** True when a start of this structure at this chunk must not exist. */
	public static boolean vetoes(Holder<Structure> structure, ChunkPos pos, ChunkGenerator gen, RandomState rs, long seed) {
		if (!structure.is(EXCLUDED) || !WorldPlanners.vetoEnabled()) return false;
		Optional<WorldPlanner> planner = WorldPlanners.find(rs, gen, seed);
		return planner.isPresent() && planner.get().footprintNear(pos.getMiddleBlockX(), pos.getMiddleBlockZ(), MARGIN);
	}
}
