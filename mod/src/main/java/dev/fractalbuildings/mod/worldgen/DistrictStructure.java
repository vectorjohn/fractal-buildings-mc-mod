package dev.fractalbuildings.mod.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.TileInfo;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The buildings of one district tile of a settlement of one tier: one structure start per tile, one rigid piece per building (plan B1).
 * There is one registered structure per tier, all in one structure set; exactly one matches a tile, so the pick is deterministic.
 * Start creation does a cached tile lookup; the pieces are built lazily when the start is generated.
 */
public final class DistrictStructure extends Structure {
	private static final Codec<Tier> TIER_CODEC = Codec.STRING.comapFlatMap(s -> {
		try {
			return DataResult.success(Tier.of(s));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "unknown tier '" + s + "'");
		}
	}, t -> t.json);

	public static final MapCodec<DistrictStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i), TIER_CODEC.fieldOf("tier").forGetter(s -> s.tier)).apply(i, DistrictStructure::new));

	private final Tier tier;

	public DistrictStructure(StructureSettings settings, Tier tier) {
		super(settings);
		this.tier = tier;
	}

	public Tier tier() { return tier; }

	/** The lane token of the stub this thread's last {@link #findGenerationPoint} returned, read by {@link #findValidGenerationPoint}. */
	private static final ThreadLocal<long[]> LAST_STUB = ThreadLocal.withInitial(() -> new long[2]);

	@Override
	public Optional<GenerationStub> findValidGenerationPoint(GenerationContext ctx) {
		long[] last = LAST_STUB.get();
		last[0] = 0;
		Optional<GenerationStub> stub = super.findValidGenerationPoint(ctx);
		if (stub.isEmpty() && last[0] != 0) {
			// ours was made and vanilla's biome filter dropped it (the tile gets no buildings)
			LaneStats.startRejected();
			if (LaneStats.logStarts()) logStart(ctx.chunkPos(), (int) (last[1] >> 32), (int) last[1], "biome-rejected", (System.nanoTime() - last[0]) / 1e6, -1, -1);
		}
		return stub;
	}

	private void logStart(ChunkPos cp, int ix, int iz, String outcome, double decideMs, double buildMs, int pieces) {
		FractalBuildingsMod.LOGGER.info("[LANE-START] chunk {},{} tile {},{} tier {} {} decide {} ms build {} ms pieces {} thread {}", cp.x(), cp.z(), ix, iz, tier.json, outcome,
				String.format(java.util.Locale.ROOT, "%.1f", decideMs), buildMs < 0 ? "-" : String.format(java.util.Locale.ROOT, "%.1f", buildMs), pieces, Thread.currentThread().getName());
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
		ChunkPos cp = ctx.chunkPos();
		long lane = 0;
		boolean stubbed = false;
		try {
			Optional<WorldPlanner> wp = WorldPlanners.of(ctx);
			if (wp.isEmpty()) return Optional.empty();
			WorldPlanner planner = wp.get();
			// StructureCheck (/locate, maps) calls this without the placement, so repeat the centre-chunk test
			Optional<DistrictGrid.Tile> start = planner.startTile(cp.x(), cp.z());
			if (start.isEmpty()) return Optional.empty();
			DistrictGrid.Tile t = start.get();
			lane = LaneStats.startBegin();
			Optional<TileInfo> info = planner.tile(t.ix(), t.iz());
			if (info.isEmpty() || info.get().tier() != tier) {
				if (lane != 0 && LaneStats.logStarts()) {
					// every decision without a stub: which settlement is near, so "no tile here" can be told from "the city was missed"
					int mx = (t.minX() + t.maxX()) / 2, mz = (t.minZ() + t.maxZ()) / 2;
					String near = planner.nearest(mx, mz, Tier.HAMLET, 1).map(n -> n.site().id() + " " + n.site().tier().json + " r" + n.site().radius() + " at " + (int) n.distance()).orElse("none");
					String why = info.isEmpty() ? "no-owner" : "other-tier(" + info.get().tier().json + ")";
					FractalBuildingsMod.LOGGER.info("[LANE-START] chunk {},{} tile {},{} tier {} {} decide {} ms centre {},{} nearest {} thread {}", cp.x(), cp.z(), t.ix(), t.iz(), tier.json, why,
							String.format(java.util.Locale.ROOT, "%.1f", (System.nanoTime() - lane) / 1e6), mx, mz, near, Thread.currentThread().getName());
				}
				return Optional.empty();
			}

			planner.prefetch(t.ix(), t.iz()); // the neighbours' plans, on the workers, while this start builds its own pieces
			BlockPos pos = new BlockPos(cp.getMiddleBlockX(), ctx.chunkGenerator().getSeaLevel(), cp.getMiddleBlockZ());
			final long token = lane;
			final long decided = System.nanoTime();
			if (token != 0) {
				long[] last = LAST_STUB.get();
				last[0] = token;
				last[1] = ((long) t.ix() << 32) | (t.iz() & 0xffffffffL);
			}
			stubbed = true;
			LaneStats.startStub(lane);
			return Optional.of(new GenerationStub(pos, builder -> {
				LaneStats.buildBegin(token);
				long b0 = System.nanoTime();
				int pieces = 0;
				String outcome = "built";
				try {
					for (TilePiece piece : buildingPieces(planner, t)) {
						builder.addPiece(piece);
						pieces++;
					}
				} catch (RuntimeException e) {
					outcome = "failed";
					WorldPlanners.FAILURES.incrementAndGet();
					FractalBuildingsMod.LOGGER.error("Planning the buildings of tile {},{} failed; the tile gets none", t.ix(), t.iz(), e);
				} finally {
					LaneStats.startEnd(token);
					if (token != 0 && LaneStats.logStarts()) logStart(cp, t.ix(), t.iz(), outcome, (decided - token) / 1e6, (System.nanoTime() - b0) / 1e6, pieces);
				}
			}));
		} catch (RuntimeException e) {
			WorldPlanners.FAILURES.incrementAndGet();
			FractalBuildingsMod.LOGGER.error("Deciding the district start at chunk {} failed; no settlement here", cp, e);
			return Optional.empty();
		} finally {
			if (!stubbed) LaneStats.startAbort(lane); // every path but the stub ends the decision here, so the in-progress count cannot leak
		}
	}


	/** The building pieces of a tile, in plan order (what the generation stub adds). Public so the order simulation builds the same pieces. */
	public static java.util.List<TilePiece> buildingPieces(WorldPlanner planner, DistrictGrid.Tile t) {
		java.util.ArrayList<TilePiece> out = new java.util.ArrayList<>();
		for (BuildingPiece b : planner.buildings(t.ix(), t.iz())) {
			Box3 box = b.box();
			// clipped to the tile (the ownership boundary); the bottom is the slab level: beard_thin takes its ground plane from it
			int x0 = Math.max(box.x(), t.minX()), x1 = Math.min(box.maxX(), t.maxX());
			int z0 = Math.max(box.z(), t.minZ()), z1 = Math.min(box.maxZ(), t.maxZ());
			if (x0 >= x1 || z0 >= z1) continue;
			int slab = b.building().oy();
			BoundingBox bb = new BoundingBox(x0, slab, z0, x1 - 1, Math.max(slab, box.maxY() - 1), z1 - 1);
			out.add(new BuildingStructurePiece(b.ref(), slab, bb));
		}
		return out;
	}


	@Override
	public net.minecraft.world.level.levelgen.GenerationStep.Decoration step() {
		return Mutations.is(Mutations.EARLY_STEP) ? net.minecraft.world.level.levelgen.GenerationStep.Decoration.SURFACE_STRUCTURES : super.step();
	}

	@Override
	public StructureType<?> type() { return ModWorldgen.DISTRICT_TYPE; }
}
