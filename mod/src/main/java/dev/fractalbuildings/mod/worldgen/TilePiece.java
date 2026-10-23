package dev.fractalbuildings.mod.worldgen;

import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.world.PieceRef;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.block.BlockMapper;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * One piece of a district tile: a building or one chunk of the tile's ground. It saves only its reference ({@link PieceRef}: tile, kind,
 * index), the slab level and the generator version (about 30 bytes), and writes by asking the world's {@link WorldPlanner} for its cells in
 * the chunk being generated. {@code postProcess} ignores its {@code random} argument (ADR 0003 D1): the blocks are a pure function of the
 * seed, the config and the position, so chunks can generate in any order, on any thread, and after a reload.
 */
public abstract class TilePiece extends StructurePiece {
	private static final Map<String, Boolean> WARNED = new ConcurrentHashMap<>();

	/** Clients see the block; no neighbour reactions (the raster already chose every shape, and a reaction would make the result depend on the order of writes). */
	private static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private final PieceRef ref;
	private final int slabY;
	private final int genVersion;

	protected TilePiece(StructurePieceType type, PieceRef ref, int slabY, BoundingBox box) {
		super(type, 0, box);
		this.ref = ref;
		this.slabY = slabY;
		this.genVersion = GeneratorV1.GEN_VERSION;
	}

	protected TilePiece(StructurePieceType type, CompoundTag tag) {
		super(type, tag);
		this.ref = new PieceRef(tag.getIntOr("kind", 0) == 0 ? PieceRef.Kind.BUILDING : PieceRef.Kind.GROUND, tag.getIntOr("ix", 0), tag.getIntOr("iz", 0), tag.getIntOr("index", 0));
		this.slabY = tag.getIntOr("y", 0);
		this.genVersion = tag.getIntOr("gv", 0);
	}

	public PieceRef ref() { return ref; }

	public int slabY() { return slabY; }

	public int genVersion() { return genVersion; }

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putInt("kind", ref.kind() == PieceRef.Kind.BUILDING ? 0 : 1);
		tag.putInt("ix", ref.ix());
		tag.putInt("iz", ref.iz());
		tag.putInt("index", ref.index());
		tag.putInt("y", slabY);
		tag.putInt("gv", genVersion);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random, BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
		// `random` is used only by the RANDOM mutation switch: everything else derives from the planner (seed + position).
		ServerLevel sl = level.getLevel();
		Optional<WorldPlanner> planner = WorldPlanners.of(sl, generator);
		if (planner.isEmpty()) return;
		if (genVersion != GeneratorV1.GEN_VERSION && WARNED.put("gv" + genVersion, true) == null) {
			FractalBuildingsMod.LOGGER.warn("A district piece was saved by generator v{}, this is v{}: not-yet-generated chunks of old cities may show a seam", genVersion, GeneratorV1.GEN_VERSION);
		}
		boolean noChunkClip = Mutations.is(Mutations.NO_CHUNK_CLIP);
		int x0 = Math.max(noChunkClip ? boundingBox.minX() : chunkBB.minX(), boundingBox.minX());
		int x1 = Math.min(noChunkClip ? boundingBox.maxX() : chunkBB.maxX(), boundingBox.maxX());
		int z0 = Math.max(noChunkClip ? boundingBox.minZ() : chunkBB.minZ(), boundingBox.minZ());
		int z1 = Math.min(noChunkClip ? boundingBox.maxZ() : chunkBB.maxZ(), boundingBox.maxZ());
		if (x0 > x1 || z0 > z1) return;
		int minY = level.getMinY(), maxY = level.getMaxY();
		Box3 clip = new Box3(x0, minY, z0, x1 - x0 + 1, maxY - minY + 1, z1 - z0 + 1);
		boolean mixRandom = Mutations.is(Mutations.RANDOM);
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		try {
			planner.get().raster(ref, clip, (x, y, z, role, blockId, shape) -> {
				BlockState state = BlockMapper.state(role, blockId, shape);
				if (mixRandom && random.nextInt(4) == 0) state = Blocks.STONE.defaultBlockState();
				pos.set(x, y, z);
				PieceLedger.record(x, y, z, Block.BLOCK_STATE_REGISTRY.getId(state));
				// a cell is only written when it changes; a claimed air cell over air costs a read, not a write
				if (level.getBlockState(pos) != state) level.setBlock(pos, state, WRITE_FLAGS);
			});
		} catch (IllegalArgumentException e) {
			if (WARNED.put("missing" + ref, true) == null) FractalBuildingsMod.LOGGER.warn("District piece {} no longer exists in its tile (saved by generator v{}, config changed?): nothing placed", ref, genVersion);
		}
	}
}
