package dev.fractalbuildings.mod.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

/**
 * "Is this chunk the centre chunk of its district tile?" (ADR 0003 W1), pure geometry with no probes. A {@code RandomSpreadStructurePlacement}
 * subclass with spacing {@link DistrictGrid#PERIOD} so {@code /locate}'s sector walk accepts it: sector {@code k} holds tile {@code k}, because
 * tile lines sit at chunk {@code 10k + o}, {@code o} in [-1, 1]. The JSON has the common placement fields and a salt; spacing and separation
 * are fixed here (the tile grid is the spacing).
 */
public final class DistrictPlacement extends RandomSpreadStructurePlacement {
	@SuppressWarnings("unchecked")
	public static final MapCodec<RandomSpreadStructurePlacement> CODEC = (MapCodec<RandomSpreadStructurePlacement>) (MapCodec<?>)
			RecordCodecBuilder.<DistrictPlacement>mapCodec(i -> placementCodec(i)
					.apply(i, (off, method, freq, salt, excl) -> new DistrictPlacement(off, method, freq, salt, excl)));

	private static final Map<Long, DistrictGrid> GRIDS = new ConcurrentHashMap<>();

	public DistrictPlacement(Vec3i locateOffset, FrequencyReductionMethod method, float frequency, int salt, Optional<ExclusionZone> exclusion) {
		super(locateOffset, method, frequency, salt, exclusion, DistrictGrid.PERIOD, 0, RandomSpreadType.LINEAR);
	}

	private static DistrictGrid grid(long seed) {
		if (GRIDS.size() > 16) GRIDS.clear(); // a handful of worlds at most; the grid is cheap to rebuild
		return GRIDS.computeIfAbsent(seed, DistrictGrid::new);
	}

	/** The centre chunk of the tile of sector {@code (sourceX / 10, sourceZ / 10)}. */
	@Override
	public ChunkPos getPotentialStructureChunk(long seed, int sourceX, int sourceZ) {
		DistrictGrid.Tile t = grid(seed).tile(Math.floorDiv(sourceX, DistrictGrid.PERIOD), Math.floorDiv(sourceZ, DistrictGrid.PERIOD));
		return new ChunkPos(t.originChunkX(), t.originChunkZ());
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int sourceX, int sourceZ) {
		return isStartChunk(state.getLevelSeed(), sourceX, sourceZ);
	}

	/** Is the chunk the centre chunk of its tile? */
	static boolean isStartChunk(long seed, int chunkX, int chunkZ) {
		DistrictGrid.Tile t = grid(seed).tileAt(chunkX * 16, chunkZ * 16);
		return t.originChunkX() == chunkX && t.originChunkZ() == chunkZ;
	}

	@Override
	public MapCodec<RandomSpreadStructurePlacement> codec() {
		return CODEC;
	}
}
