package dev.fractalbuildings.mod.gameplay;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.PieceRef;
import dev.fractalbuildings.core.world.WorldHooks;
import dev.fractalbuildings.core.world.WorldHooks.WorldHook;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.PieceLedger;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Turns the planner's furnishing hooks into blocks (plan B5). Runs in {@code postProcess} after the shell cells of the same chunk, for the
 * hooks whose cell lies in that chunk (and the piece), so every hook is placed once, by one chunk. Everything is a function of the world
 * seed and the position (the {@code random} of worldgen is never read): which bed colour, which workstation, the loot seed of a chest.
 * A bed whose head would cross a chunk edge is not placed (no piece writes outside the chunk it is generating); a hook whose cell is not
 * air, or has no floor, is skipped.
 */
public final class HookPlacer {
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final Block[] BEDS = {Blocks.BED.red(), Blocks.BED.white(), Blocks.BED.brown(), Blocks.BED.blue(), Blocks.BED.green(), Blocks.BED.yellow()};
	private static final Block[] WORKSHOP = {Blocks.SMITHING_TABLE, Blocks.STONECUTTER, Blocks.FLETCHING_TABLE, Blocks.BLAST_FURNACE, Blocks.GRINDSTONE, Blocks.CARTOGRAPHY_TABLE};
	private static final Block[] HOUSE = {Blocks.LOOM, Blocks.COMPOSTER, Blocks.CARTOGRAPHY_TABLE};
	private static final Block[] SHOP = {Blocks.BARREL, Blocks.LECTERN};
	private static final Block[] OTHER = {Blocks.LECTERN, Blocks.BARREL, Blocks.LOOM};

	/** What one call placed, for tests and the log. */
	public record Result(int beds, int workstations, int chests, int lights, int other, int villagers) {}

	private HookPlacer() {}

	/** The loot table of a building type's chests. */
	public static ResourceKey<LootTable> lootTable(String buildingType) {
		String t = switch (buildingType) {
			case "house", "shop", "workshop", "temple", "hall", "tower" -> buildingType;
			case "curtain", "bastion" -> "tower";
			default -> "house"; // block, highrise: homes
		};
		return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("fractalbuildings", "chests/" + t));
	}

	/**
	 * Places the hooks of {@code ref}'s building that lie in {@code [x0..x1] x [z0..z1]} (the chunk clipped to the piece).
	 * {@code tileOf} is the planner; {@code villagers} says whether this world's config and the settlement's tier allow villagers.
	 */
	public static Result place(WorldGenLevel level, WorldPlanner planner, PieceRef ref, int x0, int x1, int z0, int z1, boolean villagersAllowed) {
		BuildingPiece piece = null;
		List<BuildingPiece> tile = planner.buildings(ref.ix(), ref.iz());
		for (BuildingPiece b : tile) if (b.ref().equals(ref)) piece = b;
		if (piece == null) return new Result(0, 0, 0, 0, 0, 0);
		long seed = level.getSeed();
		Set<Long> villagerBeds = Set.of();
		if (villagersAllowed) {
			Tier tier = planner.tile(ref.ix(), ref.iz()).map(t -> t.tier()).orElse(Tier.CITY);
			if (WorldHooks.hasVillagers(tier)) villagerBeds = WorldHooks.villagerBeds(tile, seed, WorldHooks.MAX_VILLAGERS_PER_DISTRICT);
		}
		int beds = 0, work = 0, chests = 0, lights = 0, other = 0, vill = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (WorldHook h : WorldHooks.of(piece)) {
			if (h.kind().equals("bed")) {
				int r = bed(level, planner, ref, h, x0, x1, z0, z1, seed, villagerBeds);
				if (r > 0) beds++;
				if (r > 1) vill++;
				continue;
			}
			if (h.x() < x0 || h.x() > x1 || h.z() < z0 || h.z() > z1) continue;
			pos.set(h.x(), h.y(), h.z());
			if (!level.getBlockState(pos).isAir()) continue;
			if (level.getBlockState(pos.below()).isAir()) continue;
			BlockPos at = pos.immutable();
			Direction f = Direction.valueOf(h.facing().name());
			switch (h.kind()) {
				case "chest" -> {
					set(level, at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, f));
					BlockEntity be = level.getBlockEntity(at);
					if (be instanceof RandomizableContainerBlockEntity c) c.setLootTable(lootTable(h.buildingType()), RandomSource.create(h.seed(seed, "loot")).nextLong());
					chests++;
				}
				case "workstation" -> { set(level, at, facing(workstation(h, seed), f)); work++; }
				case "table" -> { set(level, at, Blocks.CRAFTING_TABLE.defaultBlockState()); other++; }
				case "hearth" -> { set(level, at, facing(Blocks.FURNACE.defaultBlockState(), f)); other++; }
				case "altar" -> { set(level, at, Blocks.ENCHANTING_TABLE.defaultBlockState()); other++; }
				case "counter" -> { set(level, at, facing(Blocks.BARREL.defaultBlockState(), Direction.UP)); other++; }
				case "bench" -> { set(level, at, stool(h)); other++; }
				case "light" -> { light(level, at, h, seed); lights++; }
				default -> { /* unknown kinds from a newer generator: nothing */ }
			}
		}
		return new Result(beds, work, chests, lights, other, vill);
	}

	/** Is the cell air in the planner's raster of this building (not the world: the other half of a bed may lie in a chunk that has not generated)? */
	private static boolean rasterAir(WorldPlanner planner, PieceRef ref, int x, int y, int z) {
		boolean[] air = {false};
		try {
			planner.raster(ref, new Box3(x, y, z, 1, 1, 1), (cx, cy, cz, role, id, shape) -> air[0] = role == Role.AIR);
		} catch (IllegalArgumentException e) {
			return false;
		}
		return air[0];
	}

	/**
	 * Places the half of a bed that lies in this chunk (foot at the hook, head toward its facing). Both halves decide from the raster, so the two
	 * chunks agree whatever their order; the villager comes from the chunk of the foot. Returns 0 nothing placed here, 1 a half, 2 a half and a villager.
	 */
	private static int bed(WorldGenLevel level, WorldPlanner planner, PieceRef ref, WorldHook h, int x0, int x1, int z0, int z1, long seed, Set<Long> villagerBeds) {
		Direction f = Direction.valueOf(h.facing().name());
		int hx = h.headX(), hz = h.headZ();
		boolean footHere = h.x() >= x0 && h.x() <= x1 && h.z() >= z0 && h.z() <= z1;
		boolean headHere = hx >= x0 && hx <= x1 && hz >= z0 && hz <= z1;
		if (!footHere && !headHere) return 0;
		if (!rasterAir(planner, ref, h.x(), h.y(), h.z()) || !rasterAir(planner, ref, hx, h.y(), hz)) return 0;
		Block bed = BEDS[Hash64.below(h.seed(seed, "bed"), BEDS.length)];
		if (footHere) set(level, new BlockPos(h.x(), h.y(), h.z()), bed.defaultBlockState().setValue(BedBlock.FACING, f).setValue(BedBlock.PART, BedPart.FOOT));
		if (headHere) set(level, new BlockPos(hx, h.y(), hz), bed.defaultBlockState().setValue(BedBlock.FACING, f).setValue(BedBlock.PART, BedPart.HEAD));
		if (footHere && villagerBeds.contains(WorldHooks.key(h.x(), h.y(), h.z())) && Villagers.spawn(level, h, seed)) return 2;
		return 1;
	}

	private static void set(WorldGenLevel level, BlockPos p, BlockState s) {
		level.setBlock(p, s, FLAGS);
		PieceLedger.override(p.getX(), p.getY(), p.getZ(), Block.BLOCK_STATE_REGISTRY.getId(s));
	}

	/** A bench is a spruce slab seat; plain and with no facing. */
	private static BlockState stool(WorldHook h) { return Blocks.SPRUCE_SLAB.defaultBlockState(); }

	private static BlockState workstation(WorldHook h, long seed) {
		Block[] set = switch (h.buildingType()) {
			case "workshop" -> WORKSHOP;
			case "shop" -> SHOP;
			case "house" -> HOUSE;
			case "temple" -> null;
			default -> OTHER;
		};
		if (set == null) return Blocks.BREWING_STAND.defaultBlockState();
		return set[Hash64.below(h.seed(seed, "work"), set.length)].defaultBlockState();
	}

	private static BlockState facing(Block b, Direction f) { return facing(b.defaultBlockState(), f); }

	private static BlockState facing(BlockState s, Direction f) {
		if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && f.getAxis().isHorizontal()) return s.setValue(BlockStateProperties.HORIZONTAL_FACING, f);
		if (s.hasProperty(BlockStateProperties.FACING)) return s.setValue(BlockStateProperties.FACING, f);
		return s;
	}

	/** A lantern hung from the ceiling when there is one within three cells above (half of them), else standing on the floor. */
	private static void light(WorldGenLevel level, BlockPos at, WorldHook h, long seed) {
		BlockState lantern = Blocks.LANTERN.defaultBlockState();
		if (Hash64.below(h.seed(seed, "light"), 2) == 0) {
			for (int dy = 1; dy <= 3; dy++) {
				BlockPos ceiling = at.above(dy + 1);
				BlockPos cell = at.above(dy);
				if (!level.getBlockState(cell).isAir()) break;
				if (level.getBlockState(ceiling).isFaceSturdy(level, ceiling, Direction.DOWN)) {
					set(level, cell, lantern.setValue(LanternBlock.HANGING, true));
					return;
				}
			}
		}
		set(level, at, lantern);
	}
}
