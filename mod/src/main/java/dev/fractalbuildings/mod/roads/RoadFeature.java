package dev.fractalbuildings.mod.roads;

import com.mojang.serialization.MapCodec;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.raster.Shape;
import dev.fractalbuildings.core.road.RoadNet;
import dev.fractalbuildings.core.road.RoadSurface;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.block.BlockMapper;
import dev.fractalbuildings.mod.worldgen.FoliageClear;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Roads between settlements, drawn one chunk at a time (ADR 0004 MF1 to MF4, TC). One feature, {@code fractalbuildings:road}, with
 * an empty placement, added to every overworld biome at the top-layer step (after trees, after our districts). It writes only its own
 * chunk's columns, from the planner's pure road query ({@link WorldPlanner#roadColumns}) and the pure surface plan
 * ({@link RoadSurface}); the only thing it reads from the world is each column's own ground.
 *
 * <p><b>Ground</b> (MF3): scan down from {@code WORLD_SURFACE_WG} past air, replaceable blocks, leaves, logs, fluids and ice. Never
 * {@code MOTION_BLOCKING}, which features change. <b>Claims</b> (MF4): every block placed and the whole air volume above the new
 * top (up to the old ground or {@link RoadSurface#HEADROOM}) go to the {@link PieceLedger}, whether or not anything was there, so a
 * neighbour's later leaves are "not ours" and the digest does not depend on chunk order. <b>District</b> columns (D5) are skipped.
 */
public final class RoadFeature implements Feature {
	public static final MapCodec<RoadFeature> CODEC = MapCodec.unit(RoadFeature::new);
	public static final Identifier ID = Identifier.fromNamespaceAndPath(FractalBuildingsMod.MOD_ID, "road");

	private static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	/** How far down a ground scan goes before it gives up (a ravine or a cave mouth). */
	private static final int MAX_SCAN = 96;

	/** Counters for tests and the lane log: chunks that drew something, blocks written, time in the feature. */
	public static final AtomicLong CHUNKS = new AtomicLong(), BLOCKS = new AtomicLong(), NANOS = new AtomicLong(), SLOWEST = new AtomicLong();
	/** Every call of the feature, drawing or not, and the time they took: the cost a chunk with no road pays. */
	public static final AtomicLong CALLS = new AtomicLong(), CALL_NANOS = new AtomicLong();
	/** Test control (the S10 mutant, permanent): read the ground from MOTION_BLOCKING, leaves included. The digests must then differ. */
	public static volatile boolean MUTANT_GROUND = false;
	/** Test control (ROADCLEAR): clear as before the fix (no cell over the old ground, no hanging snow layer). The floating-snow count must then be above 0 on a snowy seed. */
	public static volatile boolean LEGACY_CLEAR = false;
	/** Test telemetry, off by default: per chunk {road columns, deck columns, terrace columns, deepest cut, 1 when a foreign structure covers part of it}, keyed by (chunk z << 32) | chunk x. */
	public static volatile boolean RECORD_STATS = false;
	public static final Map<Long, int[]> STATS = new ConcurrentHashMap<>();
	/** Test telemetry (with {@link #RECORD_STATS}): what each drawn column got, as text, to find where two chunk orders differ. */
	public static final Map<Long, String> COLUMNS = new ConcurrentHashMap<>();

	private static final java.util.concurrent.atomic.AtomicBoolean WARNED_STRUCTURES = new java.util.concurrent.atomic.AtomicBoolean();
	private static final Map<String, StyleRules> STYLES = new ConcurrentHashMap<>();

	public static void register() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, ID, CODEC);
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Decoration.TOP_LAYER_MODIFICATION,
				ResourceKey.create(Registries.PLACED_FEATURE, ID));
	}

	@Override public MapCodec<RoadFeature> codec() { return CODEC; }

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator gen, RandomSource random, BlockPos origin) {
		if (level.getLevel().dimension() != Level.OVERWORLD) return false;
		WorldPlanner planner = WorldPlanners.of(level.getLevel(), gen).orElse(null);
		if (planner == null) return false;
		return placeIn(level, planner, ChunkPos.containing(origin));
	}

	/**
	 * Everything {@link #place} does after the dimension and planner checks: the street-air passes, then the road draw. Pure in its
	 * arguments (ORDERSIM R3), so the order simulator runs the same code as the game. VillageRoads and the foreign-structure read only
	 * act on a WorldGenRegion, so they are off for any other level.
	 */
	public static boolean placeIn(WorldGenLevel level, WorldPlanner planner, ChunkPos cp) {
		// CITYQ r5: street air beside a chunk with no ground piece of ours (the settlement's outer tile border) is re-cleared from here,
		// since TilePiece runs the pass only where a ground piece is; this feature runs in every overworld chunk, after its own features
		try {
			if (planner.districtsNear(cp.x(), cp.z()) && !dev.fractalbuildings.mod.worldgen.SpillClear.hasGround(planner, cp.x(), cp.z()))
				dev.fractalbuildings.mod.worldgen.SpillClear.run(level, planner, cp.x(), cp.z());
			// r7: and natural leaves this chunk's trees put into the headroom of the neighbours' roads (before the draw: its foliage pass then takes what they held)
			if (planner.roadsEnabled()) dev.fractalbuildings.mod.worldgen.SpillClear.roadLeaves(level, planner, cp.x(), cp.z());
			// TREEIN: and the leaves, vines and trunks of trees grown after a building's pass into the air it claims (rooms, steps, door approaches)
			dev.fractalbuildings.mod.worldgen.BuildingAirClear.run(level, planner, cp.x(), cp.z());
			// STREETSNOW, TRUNKDIRT: snow hanging over a street clear; trunks (and their dirt) a neighbour tree put on a street
			dev.fractalbuildings.mod.worldgen.StreetAirFix.run(level, planner, cp.x(), cp.z());
		} catch (RuntimeException e) {
			WorldPlanners.FAILURES.incrementAndGet();
			FractalBuildingsMod.LOGGER.error("Re-clearing the street air beside chunk {} failed", cp, e);
		}
		if (!planner.roadsEnabled()) return false;
		long t0 = System.nanoTime(); // det-ok: report only
		try {
			boolean drew = draw(level, planner, cp);
			CALLS.incrementAndGet();
			CALL_NANOS.addAndGet(System.nanoTime() - t0); // det-ok: report only
			if (drew) {
				long dt = System.nanoTime() - t0; // det-ok: report only
				NANOS.addAndGet(dt);
				CHUNKS.incrementAndGet();
				SLOWEST.accumulateAndGet(dt, Math::max);
			}
			return drew;
		} catch (RuntimeException e) {
			WorldPlanners.FAILURES.incrementAndGet();
			FractalBuildingsMod.LOGGER.error("Drawing the road in chunk {} failed", cp, e);
			return false;
		}
	}


	private static boolean draw(WorldGenLevel level, WorldPlanner planner, ChunkPos cp) {
		VillageRoads.Adjusted joined = VillageRoads.adjust(level, planner, cp, planner.roadColumnsAround(cp.x(), cp.z())); // VILLROAD: bend round vanilla villages, spur to their street
		List<List<RoadNet.Col>> around = joined.around();
		if (!joined.hasWork()) return false;
		boolean[] district = planner.districtColumns(cp.x(), cp.z());
		int[][] foreign = foreignStructureTops(level, cp);
		int[] foreignTop = foreign == null ? null : foreign[0], streetFreeTop = foreign == null ? null : foreign[1];
		WorldGround ground = new WorldGround(level);
		RoadSurface.Plan plan = RoadSurface.plan(cp.x(), cp.z(), around, district, ground);
		boolean[] spurCell = VillageRoads.addSpur(plan, joined.spur(), joined.spurBlock(), district, ground);
		if (MUTANT_SPUR_YIELD) spurCell = null; // test control: the spur yields to street boxes as before VILLROAD r7 (stops at the box)
		if (foreignTop != null) { // the road yields to a foreign structure that reaches the surface here (MF2)
			for (int i = 0; i < 256; i++) {
				int top = spurCell != null && spurCell[i] ? streetFreeTop[i] : foreignTop[i]; // a spur cell crosses a street box's margin to its path (VILLROAD): only other pieces stop it
				if (plan.kind[i] != RoadSurface.NONE && top >= plan.ground[i] - FOREIGN_SLACK) {
					if (plan.kind[i] == RoadSurface.ROAD) plan.roads--; else if (plan.kind[i] == RoadSurface.DECK) plan.decks--; else plan.terraces--;
					plan.kind[i] = RoadSurface.NONE;
				}
			}
		}
		if (plan.empty()) return false;
		if (district != null || !planner.roads().settlementsNear(cp.x() * 16 + 8, cp.z() * 16 + 8, 160).isEmpty()) { // C1 r2 diagnostics: a road chunk beside a settlement
			int near = 0;
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 256; i++) {
				if (plan.kind[i] == RoadSurface.NONE) continue;
				if (near++ < 4) sb.append(' ').append(cp.getMinBlockX() + (i & 15)).append(',').append(cp.getMinBlockZ() + (i >> 4)).append(" kind ").append(plan.kind[i]).append(" surf ").append(plan.surf[i]).append(" ground ").append(plan.ground[i]).append(';');
			}
			FractalBuildingsMod.LOGGER.info("[C1] chunk {} draws {} road, {} deck, {} terrace columns beside a district (district columns: {}, -1 = no tile planned):{}", cp, plan.roads, plan.decks, plan.terraces, district == null ? -1 : countTrue(district), sb);
		}
		if (RECORD_STATS) {
			int cut = 0;
			for (int i = 0; i < 256; i++) if (plan.kind[i] == RoadSurface.TERRACE) cut = Math.max(cut, plan.ground[i] - plan.surf[i]);
			STATS.put(((long) cp.z() << 32) | (cp.x() & 0xffffffffL), new int[] {plan.roads, plan.decks, plan.terraces, cut, foreignTop == null ? 0 : 1, faceColumns(plan), plan.roads + plan.decks + plan.terraces});
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		int airId = Block.BLOCK_STATE_REGISTRY.getId(air);
		long written = 0;
		FoliageClear.Cuts cuts = new FoliageClear.Cuts("road");
		int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
		for (int dz = 0; dz < 16; dz++) {
			for (int dx = 0; dx < 16; dx++) {
				int i = dz * 16 + dx;
				byte kind = plan.kind[i];
				if (kind == RoadSurface.NONE) continue;
				int x = x0 + dx, z = z0 + dz;
				int surf = plan.surf[i], g = plan.ground[i];
				boolean wet = ground.wetAt(x, z);
				BlockState top = switch (kind) {
					case RoadSurface.ROAD -> roadBlock(level, pos.set(x, surf, z), "road", plan.block[i]);
					case RoadSurface.DECK -> roadBlock(level, pos.set(x, surf, z), "deck", plan.block[i]);
					default -> nativeTop(level, pos.set(x, surf, z));
				};
				if (RECORD_STATS) COLUMNS.put(PieceLedger.pack(x, 0, z), "kind " + kind + " surf " + surf + " ground " + g + " wet " + wet + " scan " + ground.scanTop(x, z) + " top " + top.getBlock().getDescriptionId());
				// fill under it: dirt up to the top, never into water (WR3)
				if (surf > g && !wet) {
					for (int y = g + 1; y < surf; y++) written += put(level, pos.set(x, y, z), dirt);
				}
				written += put(level, pos.set(x, surf, z), top);
				// the claimed air above: up to the old ground or the headroom, whichever is higher; foreign blocks inside it go
				// ROADCLEAR: one cell more over the old ground, since a cut leaves what grew on it (snow layer, grass, a flower) floating over the road
				int clearTo = Math.max(LEGACY_CLEAR ? g : g + 1, surf + RoadSurface.HEADROOM);
				for (int y = surf + 1; y <= clearTo; y++) {
					pos.set(x, y, z);
					PieceLedger.recordRoad(x, y, z, airId);
					// every cell, not only those up to the heightmap top: the world-surface heightmap of this stage is the terrain's (it does not
					// see the trees and plants the features grew), so a bound by it left every canopy and bush above the old ground standing over the road
					BlockState old = level.getBlockState(pos);
					if (!old.isAir() && old.getFluidState().isEmpty()) {
						cuts.note(old, x, y, z);
						level.setBlock(pos, air, WRITE_FLAGS);
						written++;
					}
				}
				// ROADCLEAR: the snow layer freeze_top_layer put on a crown (or a bush) the clear just took out sits one cell above the clear: 2113 of
				// them hung over the roads of the playtest world. Only a snow layer with nothing under it goes, so no order of neighbours changes the result.
				pos.set(x, clearTo + 1, z);
				if (!LEGACY_CLEAR && level.getBlockState(pos).is(Blocks.SNOW) && level.getBlockState(pos.below()).isAir()) {
					level.setBlock(pos, air, WRITE_FLAGS);
					written++;
				}
			}
		}
		// the trees the clear cut: leaves and logs above the clear that nothing holds any more go too
		boolean[] own = new boolean[256];
		for (int i = 0; i < 256; i++) own[i] = plan.kind[i] != RoadSurface.NONE;
		written += FoliageClear.run(level, cp.x(), cp.z(), own, plan.surf, cuts);
		BLOCKS.addAndGet(written);
		return true;
	}

	/** ADR TC: drawn columns (inside the chunk) with a 4-neighbour whose new top differs by more than 3 blocks: a face the terraces did not soften. */
	private static int faceColumns(RoadSurface.Plan p) {
		int n = 0;
		for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
			int i = z * 16 + x;
			if (p.kind[i] == RoadSurface.NONE) continue;
			int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] o : d) {
				int nx = x + o[0], nz = z + o[1];
				if (nx < 0 || nz < 0 || nx > 15 || nz > 15) continue;
				int j = nz * 16 + nx;
				int top = p.kind[j] == RoadSurface.NONE ? Integer.MIN_VALUE : p.surf[j];
				if (top != Integer.MIN_VALUE && Math.abs(top - p.surf[i]) > 3) { n++; break; }
			}
		}
		return n;
	}

	private static int countTrue(boolean[] a) {
		int n = 0;
		for (boolean b : a) if (b) n++;
		return n;
	}

	private static long put(WorldGenLevel level, BlockPos pos, BlockState s) {
		PieceLedger.recordRoad(pos.getX(), pos.getY(), pos.getZ(), Block.BLOCK_STATE_REGISTRY.getId(s));
		level.setBlock(pos, s, WRITE_FLAGS);
		return 1;
	}

	/** A structure piece whose top is this far below the ground or higher counts as reaching the surface. */
	private static final int FOREIGN_SLACK = 6;
	/** Ids of foreign structures a piece of which was seen near a road (tests print them). */
	public static final java.util.Set<String> FOREIGN_SEEN = ConcurrentHashMap.newKeySet();

	/**
	 * For each column, the top y of the highest piece of a structure that is not ours covering it (a vanilla village, a mod's ruin), or
	 * {@link Integer#MIN_VALUE}: from the structure starts this chunk references, which exist before any feature runs, in any order. The
	 * road yields where a piece reaches the surface (MF2). Null when no piece covers the chunk. A failure to read them leaves the road as is.
	 */
	/** Test control (VILLROAD): spur cells yield to every foreign piece box, street boxes too, so the spur stops at the street box's edge; the village check must fail. */
	public static volatile boolean MUTANT_SPUR_YIELD = false;

	/** The top of the foreign pieces over each column of the chunk ([0]; MIN_VALUE where none), and of those that are not village street pieces ([1]); null when there are none. */
	private static int[][] foreignStructureTops(WorldGenLevel level, ChunkPos cp) {
		if (!(level instanceof WorldGenRegion region)) return null;
		try {
			StructureManager sm = level.getLevel().structureManager().forWorldGenRegion(region);
			int[] tops = null, nonStreet = null;
			int x0 = cp.getMinBlockX(), z0 = cp.getMinBlockZ();
			var registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
			for (var ref : region.getChunk(cp.x(), cp.z()).getAllReferences().entrySet()) {
				Identifier id = registry.getKey(ref.getKey());
				if (id != null && id.getNamespace().equals(FractalBuildingsMod.MOD_ID)) continue;
				for (long packed : ref.getValue()) {
					ChunkPos sp = ChunkPos.unpack(packed);
					StructureStart start = sm.getStartForStructure(ref.getKey(), region.getChunk(sp.x(), sp.z(), ChunkStatus.STRUCTURE_STARTS));
					if (start == null || !start.isValid()) continue;
					for (StructurePiece piece : start.getPieces()) {
						BoundingBox bb = piece.getBoundingBox();
						if (bb.maxX() < x0 || bb.minX() > x0 + 15 || bb.maxZ() < z0 || bb.minZ() > z0 + 15) continue;
						if (tops == null) { tops = new int[256]; nonStreet = new int[256]; java.util.Arrays.fill(tops, Integer.MIN_VALUE); java.util.Arrays.fill(nonStreet, Integer.MIN_VALUE); }
						boolean street = piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pe && pe.getElement() instanceof net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement se && se.getTemplateLocation().getPath().contains("/streets/");
						if (RECORD_STATS) FOREIGN_SEEN.add(String.valueOf(id));
						for (int z = Math.max(bb.minZ(), z0); z <= Math.min(bb.maxZ(), z0 + 15); z++) {
							for (int x = Math.max(bb.minX(), x0); x <= Math.min(bb.maxX(), x0 + 15); x++) {
								int i = (z - z0) * 16 + (x - x0);
								tops[i] = Math.max(tops[i], bb.maxY());
								if (!street) nonStreet[i] = Math.max(nonStreet[i], bb.maxY());
							}
						}
					}
				}
			}
			return tops == null ? null : new int[][] {tops, nonStreet};
		} catch (RuntimeException e) {
			if (!WARNED_STRUCTURES.getAndSet(true)) FractalBuildingsMod.LOGGER.warn("Could not read the structures around chunk {}: roads will not yield to them", cp, e);
			return null;
		}
	}

	/** The road or deck block: the route's own (DS-30); only a column without one (an unplanned test road) falls back to the biome's style at this column, a hashed pick. */
	private static BlockState roadBlock(WorldGenLevel level, BlockPos pos, String role, String routeBlock) {
		if (routeBlock != null) return BlockMapper.state(routeBlock, Shape.NONE); // the route's one material (DS-30), never the column's biome
		String biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
		StyleRules st = STYLES.computeIfAbsent(biome, StyleLibrary::forBiome);
		List<String> ids = st.palette().get(role);
		if (ids == null || ids.isEmpty()) return (role.equals("deck") ? Blocks.OAK_PLANKS : Blocks.GRAVEL).defaultBlockState();
		int n = ids.size();
		int k = Hash64.below(Hash64.hash(0x524f4144L, role, pos.getX(), pos.getZ()), n + 1);
		return BlockMapper.state(ids.get(k == n ? 0 : k), Shape.NONE);
	}

	/**
	 * The top block of a terrace or embankment: what the biome's own ground is topped with, a pure function of the biome. The column's
	 * actual surface block would match its neighbours better, but a neighbour chunk's disks, boulders and roots may or may not have
	 * changed it by now (they run in any order), and the digest must not depend on that (D7). A hashed pick is not needed: it is one block.
	 */
	static BlockState nativeTop(WorldGenLevel level, BlockPos pos) {
		String path = level.getBiome(pos).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		if (path.contains("desert") || path.contains("beach")) return Blocks.SAND.defaultBlockState();
		if (path.contains("badlands")) return Blocks.RED_SAND.defaultBlockState();
		if (path.contains("mushroom")) return Blocks.MYCELIUM.defaultBlockState();
		if (path.contains("old_growth")) return Blocks.PODZOL.defaultBlockState();
		if (path.contains("mangrove")) return Blocks.MUD.defaultBlockState();
		if (path.contains("gravelly")) return Blocks.GRAVEL.defaultBlockState();
		if (path.contains("stony_peaks")) return Blocks.STONE.defaultBlockState();
		if (path.contains("frozen_peaks") || path.contains("jagged_peaks") || path.contains("snowy_slopes") || path.contains("grove") || path.contains("ice_spikes")) return Blocks.SNOW_BLOCK.defaultBlockState();
		return Blocks.GRASS_BLOCK.defaultBlockState();
	}

	/** A column's ground, read once and remembered for this chunk. */
	private static final class WorldGround implements RoadSurface.Ground {
		private final WorldGenLevel level;
		private final int[] top = new int[256], scan = new int[256];
		private final byte[] state = new byte[256]; // 0 unread, 1 dry, 2 wet
		private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		WorldGround(WorldGenLevel level) { this.level = level; }

		private void read(int x, int z) {
			int i = (z & 15) * 16 + (x & 15);
			if (state[i] != 0) return;
			boolean mutant = MUTANT_GROUND;
			int y = level.getHeight(mutant ? Heightmap.Types.MOTION_BLOCKING : Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
			scan[i] = y;
			boolean wet = false;
			if (!mutant) {
				int min = level.getMinY();
				for (int n = 0; n < MAX_SCAN && y > min; n++) {
					BlockState s = level.getBlockState(pos.set(x, y, z));
					if (!s.getFluidState().isEmpty() || s.is(BlockTags.ICE)) {
						wet = true;
						y--;
					} else if (s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || s.getBlock() instanceof net.minecraft.world.level.block.HugeMushroomBlock) {
						y--;
					} else {
						break;
					}
				}
			}
			top[i] = y;
			state[i] = (byte) (wet ? 2 : 1);
		}

		@Override public int topY(int x, int z) { read(x, z); return top[(z & 15) * 16 + (x & 15)]; }

		@Override public boolean wet(int x, int z) { read(x, z); return state[(z & 15) * 16 + (x & 15)] == 2; }

		boolean wetAt(int x, int z) { return wet(x, z); }

		/** The heightmap top the scan started from: blocks above the ground up to here (plants, trees) are what a clear removes. */
		int scanTop(int x, int z) { read(x, z); return scan[(z & 15) * 16 + (x & 15)]; }

	}
}
