package dev.fractalbuildings.mod.probe;

import net.minecraft.SharedConstants;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;

/**
 * Ways to fill one chunk of {@link ColumnMemo} with exact heights. Every method must return
 * bit-identical values to {@link LiveTerrainProbe#column}; they differ only in cost.
 */
public interface ChunkFill {
	int[] fill(NoiseBasedChunkGenerator gen, RandomState rs, int cx, int cz);

	/** (a) 256 x {@code getBaseColumn}. */
	ChunkFill COLUMNS = (gen, rs, cx, cz) -> {
		int[] out = new int[512];
		for (int dz = 0; dz < 16; dz++) {
			for (int dx = 0; dx < 16; dx++) {
				int[] c = LiveTerrainProbe.column(gen, rs, cx * 16 + dx, cz * 16 + dz);
				out[dz * 16 + dx] = c[1];
				out[256 + dz * 16 + dx] = c[0] - c[1];
			}
		}
		return out;
	};

	/** (b) one {@code NoiseChunk} over a 16 x H x 16 volume: density sampled once per block, aquifer per block until the floor. */
	ChunkFill BATCHED = (gen, rs, cx, cz) -> Batched.fill(gen, rs, cx, cz, false);

	/**
	 * (c) (b) restricted to a y window around the sampled {@code chunk_surface_level}; a chunk whose window
	 * does not bracket every surface (air on top, floor inside) is redone as (b).
	 */
	ChunkFill WINDOWED = (gen, rs, cx, cz) -> Batched.fill(gen, rs, cx, cz, true);

	/** What the game uses: the fastest method that the parity tests show bit-identical (A1 timing table). */
	ChunkFill DEFAULT = WINDOWED;

	final class Batched {
		private Batched() {}

		/** Metres above and below the sampled surface level that the window keeps. */
		static final int MARGIN = 32;
		/** Counts windowed fills that had to fall back (read by the timing test). */
		public static final java.util.concurrent.atomic.AtomicLong FALLBACKS = new java.util.concurrent.atomic.AtomicLong();

		/** Same as the generator's private {@code createFluidPicker}; the parity test catches drift. */
		static Aquifer.FluidPicker fluidPicker(NoiseGeneratorSettings s) {
			Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
			int sea = s.seaLevel();
			Aquifer.FluidStatus seaStatus = new Aquifer.FluidStatus(sea, s.defaultFluid());
			Aquifer.FluidStatus empty = new Aquifer.FluidStatus(DimensionType.MIN_Y * 2, Blocks.AIR.defaultBlockState());
			return (x, y, z) -> SharedConstants.DEBUG_DISABLE_FLUID_GENERATION ? empty : y < Math.min(-54, sea) ? lava : seaStatus;
		}

		static int[] fill(NoiseBasedChunkGenerator gen, RandomState rs, int cx, int cz, boolean windowed) {
			NoiseGeneratorSettings settings = gen.generatorSettings().value();
			NoiseSettings ns = settings.noiseSettings().clampToHeightAccessor(LiveTerrainProbe.H);
			Aquifer.FluidPicker picker = fluidPicker(settings);
			int minY = ns.minY(), height = ns.height();
			if (windowed) {
				int[] w = window(rs, settings, picker, cx, cz, minY, height);
				if (w != null) {
					int[] out = scan(rs, settings, picker, cx, cz, w[0], w[1] - w[0] + 1, true);
					if (out != null) return out;
				}
				FALLBACKS.incrementAndGet();
			}
			return scan(rs, settings, picker, cx, cz, minY, height, false);
		}

		/** {lowY, highY} (inclusive, 8-aligned) around the chunk's sampled surface levels, or null if it would be the whole range. */
		private static int[] window(RandomState rs, NoiseGeneratorSettings settings, Aquifer.FluidPicker picker, int cx, int cz, int minY, int height) {
			var vol = new DensityVolume(1, 1, 1, cx * 16, minY, cz * 16);
			double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
			try (NoiseChunk nc = new NoiseChunk(rs, null, settings, picker, Blender.empty(), vol)) {
				var surf = nc.cachingSamplers().get(settings.noiseRouter().chunkSurfaceLevel());
				for (int dz = 0; dz < 16; dz += 5) {
					for (int dx = 0; dx < 16; dx += 5) {
						double v = surf.sampleValue(cx * 16 + dx, 0, cz * 16 + dz);
						lo = Math.min(lo, v);
						hi = Math.max(hi, v);
					}
				}
			}
			int a = Math.max(minY, Math.floorDiv((int) Math.floor(lo) - MARGIN, 8) * 8);
			int b = Math.min(minY + height - 1, Math.floorDiv((int) Math.ceil(hi) + MARGIN, 8) * 8 + 7);
			return b - a + 1 >= height ? null : new int[] {a, b};
		}

		/** Scans every column top-down; returns null in window mode when a column is not bracketed. */
		private static int[] scan(RandomState rs, NoiseGeneratorSettings settings, Aquifer.FluidPicker picker, int cx, int cz, int y0, int sizeY, boolean window) {
			int[] out = new int[512];
			DensityVolume volume = new DensityVolume(16, sizeY, 16, cx * 16, y0, cz * 16);
			BlockState defaultState = settings.defaultBlock();
			try (NoiseChunk nc = new NoiseChunk(rs, null, settings, picker, Blender.empty(), volume)) {
				Aquifer aquifer = nc.aquifer();
				var finalDensity = nc.cachingSamplers().get(settings.noiseRouter().finalDensity());
				try (var buf = finalDensity.sampleVolume(volume)) {
					for (int dz = 0; dz < 16; dz++) {
						for (int dx = 0; dx < 16; dx++) {
							int bx = cx * 16 + dx, bz = cz * 16 + dz;
							int surf = Integer.MIN_VALUE, floor = Integer.MIN_VALUE;
							for (int y = sizeY - 1; y >= 0; y--) {
								int by = volume.blockY(y);
								BlockState base = aquifer.computeSubstance(bx, by, bz, buf.get(volume.indexUnchecked(dx, y, dz)));
								BlockState st = base == null ? defaultState : base;
								if (y == sizeY - 1 && window && !st.isAir()) return null;
								if (st.isAir()) continue;
								if (surf == Integer.MIN_VALUE) surf = by + 1;
								if (st.getFluidState().isEmpty()) {
									floor = by + 1;
									break;
								}
							}
							if (window && floor == Integer.MIN_VALUE) return null;
							out[dz * 16 + dx] = floor;
							out[256 + dz * 16 + dx] = surf - floor;
						}
					}
				}
			}
			return out;
		}
	}
}
