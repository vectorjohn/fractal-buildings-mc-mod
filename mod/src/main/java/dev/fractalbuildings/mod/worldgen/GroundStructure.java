package dev.fractalbuildings.mod.worldgen;

import com.mojang.serialization.MapCodec;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.world.GroundPiece;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The streets, plazas and grading of one district tile, in a structure of its own with {@code terrain_adaptation: none}: a sloping street
 * must not have the terrain around it flattened to its lowest point (beard_thin takes one ground plane per piece box, plan B1). One piece per
 * chunk that has any ground; the core makes ground and buildings own disjoint cells, so the two structures never write the same block.
 */
public final class GroundStructure extends Structure {
	public static final MapCodec<GroundStructure> CODEC = Structure.simpleCodec(GroundStructure::new);

	public GroundStructure(StructureSettings settings) { super(settings); }

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
		ChunkPos cp = ctx.chunkPos();
		try {
			Optional<WorldPlanner> wp = WorldPlanners.of(ctx);
			if (wp.isEmpty()) return Optional.empty();
			WorldPlanner planner = wp.get();
			Optional<DistrictGrid.Tile> start = planner.startTile(cp.x(), cp.z());
			if (start.isEmpty()) return Optional.empty();
			DistrictGrid.Tile t = start.get();
			if (planner.tile(t.ix(), t.iz()).isEmpty()) return Optional.empty();
			BlockPos pos = new BlockPos(cp.getMiddleBlockX(), ctx.chunkGenerator().getSeaLevel(), cp.getMiddleBlockZ());
			return Optional.of(new GenerationStub(pos, builder -> {
				try {
					for (TilePiece piece : groundPieces(planner, t)) builder.addPiece(piece);
				} catch (RuntimeException e) {
					WorldPlanners.FAILURES.incrementAndGet();
					FractalBuildingsMod.LOGGER.error("Planning the ground of tile {},{} failed; the tile gets none", t.ix(), t.iz(), e);
				}
			}));
		} catch (RuntimeException e) {
			WorldPlanners.FAILURES.incrementAndGet();
			FractalBuildingsMod.LOGGER.error("Deciding the ground start at chunk {} failed", cp, e);
			return Optional.empty();
		}
	}

	/** The ground pieces of a tile, in plan order (what the generation stub adds). Public so the order simulation builds the same pieces. */
	public static java.util.List<TilePiece> groundPieces(WorldPlanner planner, DistrictGrid.Tile t) {
		java.util.ArrayList<TilePiece> out = new java.util.ArrayList<>();
		for (GroundPiece g : planner.ground(t.ix(), t.iz())) {
			Box3 b = g.box();
			out.add(new GroundStructurePiece(g.ref(), new BoundingBox(b.x(), b.y(), b.z(), b.maxX() - 1, b.maxY() - 1, b.maxZ() - 1)));
		}
		return out;
	}

	@Override
	public net.minecraft.world.level.levelgen.GenerationStep.Decoration step() {
		return Mutations.is(Mutations.EARLY_STEP) ? net.minecraft.world.level.levelgen.GenerationStep.Decoration.SURFACE_STRUCTURES : super.step();
	}

	@Override
	public StructureType<?> type() { return ModWorldgen.GROUND_TYPE; }
}
