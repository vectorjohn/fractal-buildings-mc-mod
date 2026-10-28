package dev.fractalbuildings.mod.roads;

import dev.fractalbuildings.core.road.VillageJoin;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * Ground heights around a vanilla village for grading its spur (VILLROAD r8, DS-31), without reading any chunk, so in any chunk order.
 *
 * <p>A village street is {@code terrain_matching}: its {@code GravityProcessor(WORLD_SURFACE_WG, -1)} puts each path block on the
 * column's world-surface height at placement (26.3 {@code GravityProcessor.processBlock}, lines 50-53), not at the piece's y. That height
 * is the noise terrain shaped by the village's beard ({@code terrain_adaptation: beard_thin}, rigid pieces and jigsaw junctions within 12).
 * This replays {@code NoiseBasedChunkGenerator.iterateNoiseColumn} (26.3 lines 164-208, what {@code getBaseHeight} runs) for one column,
 * but with a {@link Beardifier} built from the village's own start, as {@code Beardifier.forStructuresInChunk} does for a chunk (a superset
 * of pieces gives the same values: the kernel is zero beyond 12). Not modelled: carvers that open the surface, other structures' beards,
 * and features placed before the village (lakes); the in-game check compares these heights with the placed path blocks.
 */
public final class VillageGround {
	private VillageGround() {}

	/** The heights for one village, or null when the level's generator is not a noise generator (then the spur is not graded). */
	public static VillageJoin.Heights of(ServerLevel level, StructureStart start) {
		if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator gen)) return null;
		RandomState random = level.getChunkSource().randomState();
		NoiseGeneratorSettings settings = gen.generatorSettings().value();
		Beardifier beard = beardOf(start);
		Aquifer.FluidPicker fluids = fluidPicker(settings);
		NoiseSettings noise = settings.noiseSettings().clampToHeightAccessor(level);
		return (x, z) -> {
			OptionalInt h = surface(settings, noise, random, beard, fluids, x, z);
			return (h.isPresent() ? h.getAsInt() : level.getMinY()) - 1; // the top block: one below the heightmap value
		};
	}

	static Beardifier beardOf(StructureStart start) {
		TerrainAdjustment adj = start.getStructure().terrainAdaptation();
		if (adj == TerrainAdjustment.NONE) return Beardifier.EMPTY;
		List<Beardifier.Rigid> rigids = new ArrayList<>();
		List<JigsawJunction> junctions = new ArrayList<>();
		BoundingBox any = null;
		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof PoolElementStructurePiece pool) {
				if (pool.getElement().getProjection() == StructureTemplatePool.Projection.RIGID) {
					rigids.add(new Beardifier.Rigid(pool.getBoundingBox(), adj, pool.getGroundLevelDelta()));
					any = any == null ? pool.getBoundingBox() : BoundingBox.encapsulating(any, pool.getBoundingBox());
				}
				for (JigsawJunction j : pool.getJunctions()) {
					junctions.add(j);
					BoundingBox jb = new BoundingBox(new net.minecraft.core.BlockPos(j.getSourceX(), j.getSourceGroundY(), j.getSourceZ()));
					any = any == null ? jb : BoundingBox.encapsulating(any, jb);
				}
			} else {
				rigids.add(new Beardifier.Rigid(piece.getBoundingBox(), adj, 0));
				any = any == null ? piece.getBoundingBox() : BoundingBox.encapsulating(any, piece.getBoundingBox());
			}
		}
		return any == null ? Beardifier.EMPTY : new Beardifier(List.copyOf(rigids), List.copyOf(junctions), any.inflatedBy(24));
	}

	/** {@code NoiseBasedChunkGenerator.createFluidPicker} (private; 26.3 lines 78-90) without the debug switch. */
	static Aquifer.FluidPicker fluidPicker(NoiseGeneratorSettings settings) {
		Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
		int sea = settings.seaLevel();
		Aquifer.FluidStatus seaStatus = new Aquifer.FluidStatus(sea, settings.defaultFluid());
		return (x, y, z) -> y < Math.min(-54, sea) ? lava : seaStatus;
	}

	/** The WORLD_SURFACE_WG height (first non-air block + 1) of one column with the beard applied. */
	static OptionalInt surface(NoiseGeneratorSettings settings, NoiseSettings noise, RandomState random, Beardifier beard, Aquifer.FluidPicker fluids, int x, int z) {
		if (noise.height() <= 0) return OptionalInt.empty();
		DensityVolume volume = new DensityVolume(1, noise.height(), 1, x, noise.minY(), z);
		var opaque = Heightmap.Types.WORLD_SURFACE_WG.isOpaque();
		try (NoiseChunk chunk = new NoiseChunk(random, beard, settings, fluids, Blender.empty(), volume)) {
			Aquifer aquifer = chunk.aquifer();
			var defaultState = settings.defaultBlock();
			DensitySampler.Bound density = chunk.cachingSamplers().get(settings.noiseRouter().finalDensity());
			try (ScopedDensityBuffer buf = density.sampleVolume(chunk.volume())) {
				for (int y = volume.sizeY() - 1; y >= 0; y--) {
					int blockY = volume.blockY(y);
					var base = aquifer.computeSubstance(x, blockY, z, buf.get(volume.indexUnchecked(0, y, 0)));
					var state = base == null ? defaultState : base;
					if (opaque.test(state)) return OptionalInt.of(blockY + 1);
				}
			}
		}
		return OptionalInt.empty();
	}
}
