package dev.fractalbuildings.gametest.worldgen;

import dev.fractalbuildings.mod.worldgen.PieceLedger;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Shared helpers of the worldgen server tests: make a start by hand (as S9 did), place it chunk by chunk in a given order, read it back. */
public final class StartSupport {
	private StartSupport() {}

	public static void check(boolean ok, String msg) {
		if (!ok) throw new IllegalStateException(msg);
	}

	public static Holder.Reference<Structure> structure(ServerLevel level, String path) {
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.fromNamespaceAndPath("fractalbuildings", path)));
	}

	/** {@code Structure.generate} for the start chunk, the way {@code ChunkGenerator.tryGenerateStructure} calls it. */
	public static StructureStart start(ServerLevel level, String structurePath, ChunkPos cp) {
		Holder.Reference<Structure> holder = structure(level, structurePath);
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		RandomState rs = level.getChunkSource().randomState();
		return holder.value().generate(holder, level.dimension(), level.registryAccess(), gen, gen.getBiomeSource(),
				rs.createClimateSampler(SamplerContext.EMPTY_UNCACHED), rs, level.getStructureTemplateManager(), level.getSeed(), cp, 0, level, b -> true);
	}

	public static List<ChunkPos> chunks(StructureStart s) {
		BoundingBox b = s.getBoundingBox();
		List<ChunkPos> out = new ArrayList<>();
		for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) out.add(new ChunkPos(cx, cz));
		return out;
	}

	/** What one run placed: the merged cells the pieces claimed, and how many of them lay outside the chunk being generated. */
	public record Placed(TreeMap<Long, Integer> cells, int outsideChunk, int duplicates, TreeMap<Long, Integer> hooks) {
		public Placed(TreeMap<Long, Integer> cells, int outsideChunk, int duplicates) { this(cells, outsideChunk, duplicates, new TreeMap<>()); }

		public String digest() { return PieceLedger.digest(cells); }

		public String hookDigest() { return PieceLedger.digest(hooks); }
	}

	/** Places the start chunk by chunk in {@code order}; each chunk gets a random seeded from the chunk and {@code salt} (a piece must not read it). */
	public static Placed place(ServerLevel level, StructureStart s, List<ChunkPos> order, long salt) {
		ChunkGenerator gen = level.getChunkSource().getGenerator();
		TreeMap<Long, Integer> merged = new TreeMap<>();
		TreeMap<Long, Integer> hooks = new TreeMap<>();
		int outside = 0, dups = 0;
		for (ChunkPos c : order) {
			BoundingBox cbb = new BoundingBox(c.getMinBlockX(), level.getMinY(), c.getMinBlockZ(), c.getMaxBlockX(), level.getMaxY(), c.getMaxBlockZ());
			try (PieceLedger.Recording r = PieceLedger.start()) {
				s.placeInChunk(level, level.structureManager(), gen, RandomSource.create(c.pack() * 31 + salt), cbb, c);
				for (var e : r.states().entrySet()) {
					long k = e.getKey();
					if (PieceLedger.x(k) >> 4 != c.x() || PieceLedger.z(k) >> 4 != c.z()) outside++;
					if (merged.put(k, e.getValue()) != null) dups++;
				}
				dups += r.duplicates().size();
				hooks.putAll(r.hooks());
			}
		}
		return new Placed(merged, outside, dups, hooks);
	}

	/** Air over the start's whole box, so a second placement starts from nothing. */
	public static void clear(ServerLevel level, StructureStart s) {
		BoundingBox b = s.getBoundingBox();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = Math.max(level.getMinY(), b.minY() - 16); y <= b.maxY() + 2; y++) {
			for (int z = b.minZ(); z <= b.maxZ(); z++) for (int x = b.minX(); x <= b.maxX(); x++) {
				p.set(x, y, z);
				if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/** Hash of the blocks of the starts' boxes (and 16 below them, for foundations): pass the building start and the ground start. */
	public static long worldHash(ServerLevel level, StructureStart... starts) {
		long h = 1125899906842597L;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (StructureStart s : starts) {
			BoundingBox b = s.getBoundingBox();
			for (int y = Math.max(level.getMinY(), b.minY() - 16); y <= b.maxY() + 2; y++)
				for (int z = b.minZ(); z <= b.maxZ(); z++)
					for (int x = b.minX(); x <= b.maxX(); x++) {
						p.set(x, y, z);
						h = (h ^ Block.getId(level.getBlockState(p))) * 0x100000001B3L + (h >>> 29);
					}
		}
		return h;
	}
}
