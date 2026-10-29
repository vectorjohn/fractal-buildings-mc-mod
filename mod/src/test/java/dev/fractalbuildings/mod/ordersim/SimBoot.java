package dev.fractalbuildings.mod.ordersim;

import com.mojang.serialization.Lifecycle;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * ORDERSIM S1: one-time (per JVM) engine-free boot for the chunk-order simulator: vanilla Bootstrap, the block and fluid tags
 * bound (Bootstrap leaves them empty, so {@code BlockTags.LOGS} would be false for every log), the world lookup (biomes and
 * the configured features), and the {@link PalettedContainerFactory} the sim's {@code ProtoChunk}s need. About 3 s, once.
 */
public final class SimBoot {
	private static boolean booted;
	private static HolderLookup.Provider lookup;
	private static PalettedContainerFactory containers;

	private SimBoot() {}

	/** Idempotent and thread safe. */
	public static synchronized void boot() {
		if (booted) return;
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		bindTags(Registries.BLOCK, BuiltInRegistries.BLOCK);
		bindTags(Registries.FLUID, BuiltInRegistries.FLUID);
		lookup = VanillaRegistries.createWorldLookup();
		dev.fractalbuildings.mod.testing.BiomeTagBinder.bind(lookup);
		HolderLookup.RegistryLookup<Biome> biomes = lookup.lookupOrThrow(Registries.BIOME);
		IdMapper<Holder<Biome>> ids = new IdMapper<>();
		biomes.listElements().sorted((a, b) -> a.key().identifier().compareTo(b.key().identifier())).forEach(ids::add);
		Holder<Biome> plains = biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
		containers = new PalettedContainerFactory(Strategy.createForBlockStates(Block.BLOCK_STATE_REGISTRY), Blocks.AIR.defaultBlockState(), null,
			Strategy.createForBiomes(ids), plains, null);
		booted = true;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void bindTags(ResourceKey<? extends Registry<?>> key, Registry<?> registry) {
		try (var rm = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource().fullResources()))) {
			Map tags = TagLoader.loadTagsForRegistry(rm, (ResourceKey) key, (TagLoader.ElementLookup) TagLoader.ElementLookup.fromFrozenRegistry(registry));
			registry.prepareTagReload(new TagLoader.LoadResult(key, tags)).apply();
		} catch (Exception e) {
			throw new IllegalStateException("could not bind tags for " + key, e);
		}
	}

	public static HolderLookup.Provider lookup() { boot(); return lookup; }

	public static PalettedContainerFactory containers() { boot(); return containers; }

	/** A biome by id, with or without the {@code minecraft:} prefix ("plains", "minecraft:snowy_plains"). */
	public static Holder<Biome> biome(String id) {
		boot();
		Identifier i = Identifier.parse(id.contains(":") ? id : "minecraft:" + id);
		return lookup.lookupOrThrow(Registries.BIOME).getOrThrow(ResourceKey.create(Registries.BIOME, i));
	}

	/** A configured feature (a tree, a lake, ...) by key, e.g. {@code TreeFeatures.DARK_OAK}. */
	public static Feature feature(ResourceKey<Feature> key) {
		boot();
		return lookup.lookupOrThrow(Registries.FEATURE).getOrThrow(key).value();
	}
}
