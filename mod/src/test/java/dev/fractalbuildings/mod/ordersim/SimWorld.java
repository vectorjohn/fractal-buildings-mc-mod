package dev.fractalbuildings.mod.ordersim;

import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import it.unimi.dsi.fastutil.shorts.ShortList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * ORDERSIM S1: the chunks of a window (real {@link ProtoChunk}s, so the production passes' section reads run unchanged),
 * terrain filled from a {@link TerrainProbe} and {@link BiomeProbe}, post-process marks, promotion, and the write log.
 * Chunk coordinates are absolute; the window is {@code [cx0, cx0+nx) x [cz0, cz0+nz)}. Single threaded.
 */
public final class SimWorld {
	public static final int MIN_Y = -64, HEIGHT = 384;

	/** One block write that reached a chunk. {@code writer} is e.g. {@code feature:dark_oak@1,70,2} or {@code ours:3,4}. */
	public record Write(int x, int y, int z, BlockState old, BlockState now, int flags, String writer, String frame) {}

	/** A write or read outside the zone of the level it was made through. {@code dropped} writes never reached a chunk. */
	public record ZoneViolation(int x, int y, int z, String writer, boolean write, boolean ours) {}

	public final int cx0, cz0, nx, nz;
	public final long seed;
	private final ProtoChunk[] chunks;
	private final BiomeProbe biomes;
	private final TerrainProbe terrain;
	final List<Write> writes = new ArrayList<>();
	final List<ZoneViolation> violations = new ArrayList<>();
	/** Last writer of each written cell (provenance), keyed by packed x/y/z. */
	final Map<Long, String> lastWriter = new HashMap<>();
	/** Record a StackWalker frame (the first frame outside the sim and Minecraft) with each write. Slower. */
	public boolean audit;
	public boolean logWrites = true;
	/** Keep the last writer of each written cell (provenance); cheaper than the full log. */
	public boolean trackWriters = true;

	@SuppressWarnings("unchecked")
	public SimWorld(long seed, int cx0, int cz0, int nx, int nz, TerrainProbe terrain, BiomeProbe biomes) {
		SimBoot.boot();
		this.seed = seed;
		this.cx0 = cx0; this.cz0 = cz0; this.nx = nx; this.nz = nz;
		this.terrain = terrain;
		this.biomes = biomes;
		this.chunks = new ProtoChunk[nx * nz];
		LevelHeightAccessor lha = LevelHeightAccessor.create(MIN_Y, HEIGHT);
		for (int i = 0; i < nx; i++) {
			for (int k = 0; k < nz; k++) {
				ProtoChunk c = new ProtoChunk(new ChunkPos(cx0 + i, cz0 + k), UpgradeData.EMPTY, lha, SimBoot.containers(), null);
				fill(c);
				Heightmap.primeHeightmaps(c, ChunkStatus.FEATURES.heightmapsAfter());
				Heightmap.primeHeightmaps(c, java.util.EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG)); // the worldgen maps, from the bare terrain: features do not update them (RoadFeature's ground scan reads WORLD_SURFACE_WG)
				c.setPersistedStatus(ChunkStatus.FEATURES);
				chunks[i * nz + k] = c;
			}
		}
	}

	/** Stone, then dirt, then grass at {@code floorY - 1}; water from {@code floorY} up to {@code surfaceY - 1}. */
	private void fill(ProtoChunk c) {
		BlockState stone = Blocks.STONE.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState(),
			grass = Blocks.GRASS_BLOCK.defaultBlockState(), water = Blocks.WATER.defaultBlockState();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int x0 = c.getPos().getMinBlockX(), z0 = c.getPos().getMinBlockZ();
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = x0 + dx, z = z0 + dz;
				int floor = terrain.floorY(x, z), surf = terrain.surfaceY(x, z);
				boolean wet = surf > floor;
				for (int y = MIN_Y; y < floor; y++) {
					BlockState s = y >= floor - 1 ? (wet ? dirt : grass) : y >= floor - 4 ? dirt : stone;
					c.setBlockState(p.set(x, y, z), s, 0);
				}
				for (int y = floor; y < surf; y++) c.setBlockState(p.set(x, y, z), water, 0);
			}
		}
	}

	public boolean hasChunk(int cx, int cz) {
		return cx >= cx0 && cx < cx0 + nx && cz >= cz0 && cz < cz0 + nz;
	}

	public ProtoChunk chunk(int cx, int cz) {
		if (!hasChunk(cx, cz)) throw new IllegalStateException("chunk " + cx + "," + cz + " is outside the sim window " + cx0 + "," + cz0 + " " + nx + "x" + nz);
		return chunks[(cx - cx0) * nz + (cz - cz0)];
	}

	public BlockState get(int x, int y, int z) {
		if (y < MIN_Y || y >= MIN_Y + HEIGHT) return Blocks.VOID_AIR.defaultBlockState();
		return chunk(x >> 4, z >> 4).getBlockState(new BlockPos(x, y, z));
	}

	public Holder<Biome> biomeAt(int x, int z) { return SimBoot.biome(biomes.biome(x, z)); }

	public TerrainProbe terrain() { return terrain; }

	public List<Write> writes() { return writes; }

	public List<ZoneViolation> violations() { return violations; }

	/** Who last wrote this cell ({@code null}: nobody, it is terrain). */
	public String writerOf(int x, int y, int z) { return lastWriter.get(key(x, y, z)); }

	static long key(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y + 2048 & 0xFFF);
	}

	/** A level over the whole window, for promotion and for tests. Writers are labelled {@code label}. */
	public SimLevel wholeLevel(String label) {
		return new SimLevel(this, cx0 + nx / 2, cz0 + nz / 2, Math.max(nx, nz), Math.max(nx, nz), label, seed);
	}

	/**
	 * Replays chunk (cx,cz)'s post-process marks like {@code LevelChunk.postProcessGeneration}: each marked cell is
	 * re-shaped from its neighbours and written with flags 20 (no new marks). Fluid ticks are not run.
	 *
	 * @return the number of cells replayed
	 */
	public int promote(int cx, int cz) {
		ProtoChunk c = chunk(cx, cz);
		ShortList[] marks = c.getPostProcessing();
		SimLevel level = wholeLevel("promote:" + cx + "," + cz);
		int n = 0;
		for (int i = 0; i < marks.length; i++) {
			ShortList list = marks[i];
			if (list == null) continue;
			for (short s : list) {
				BlockPos pos = ProtoChunk.unpackOffsetCoordinates(s, c.getSectionYFromSectionIndex(i), c.getPos());
				BlockState st = c.getBlockState(pos);
				BlockState ns = Block.updateFromNeighbourShapes(st, level.proxy(), pos);
				level.proxy().setBlock(pos, ns, 20);
				n++;
			}
			list.clear();
		}
		return n;
	}
}
