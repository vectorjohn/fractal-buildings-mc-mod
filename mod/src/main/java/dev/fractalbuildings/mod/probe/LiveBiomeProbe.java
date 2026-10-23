package dev.fractalbuildings.mod.probe;

import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.mod.compat.BiomeAliases;
import dev.fractalbuildings.mod.compat.TerraBlenderPriming;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * Biome at the surface quart of a column, as {@code TerrainDumpTool} records it: a fixed non-tie lookup
 * first (resets the RTree's thread-local "last result", ADR 0003 D3), then the climate sample at the
 * surface. Returns the full id ({@code minecraft:plains}), exactly what {@code RecordedProbe.biome}
 * gives for the same column of a dump, except that a biome from another mod is replaced by the vanilla biome it stands in for
 * ({@link BiomeAliases}), and a biome in {@code #fractalbuildings:no_settlements} by water. Under TerraBlender with several regions the
 * prime is an anchor inside the column's region ({@link TerraBlenderPriming}). Thread-safe: one climate sampler per thread.
 */
public final class LiveBiomeProbe implements BiomeProbe {
	private static final Climate.TargetPoint PRIME = Climate.target(0.0F, 0.0F, 0.5F, 0.0F, 0.0F, 0.0F);

	private final MultiNoiseBiomeSource source;
	private final ThreadLocal<Climate.Sampler> sampler;
	private final ThreadLocal<BiomeResolver> resolver;
	private final Optional<TerraBlenderPriming> terraBlender;
	private final SurfaceSource surface;

	/** Surface height of a column (the district memo or the sparse columns, so the same column is not generated twice). */
	@FunctionalInterface
	public interface SurfaceSource { int surfaceY(int x, int z); }

	public LiveBiomeProbe(NoiseBasedChunkGenerator gen, RandomState rs, SurfaceSource surface) {
		this.source = (MultiNoiseBiomeSource) gen.getBiomeSource();
		this.sampler = ThreadLocal.withInitial(() -> rs.createClimateSampler(SamplerContext.builder().enableCaches().build()));
		this.resolver = ThreadLocal.withInitial(() -> source.createResolver(rs.createClimateSampler(SamplerContext.builder().enableCaches().build())));
		this.terraBlender = TerraBlenderPriming.of(source, source.createResolver(rs.createClimateSampler(SamplerContext.EMPTY_UNCACHED)));
		this.surface = surface;
	}

	@Override public String biome(int x, int z) {
		int sy = surface.surfaceY(x, z);
		Holder<Biome> b;
		if (terraBlender.isPresent()) {
			b = terraBlender.get().lookup(resolver.get(), QuartPos.fromBlock(x), QuartPos.fromBlock(z), QuartPos.fromBlock(sy));
		} else {
			source.getNoiseBiome(PRIME);
			b = source.getNoiseBiome(sampler.get().sample(QuartPos.fromBlock(x), QuartPos.fromBlock(sy), QuartPos.fromBlock(z)));
		}
		return BiomeAliases.alias(b);
	}
}
