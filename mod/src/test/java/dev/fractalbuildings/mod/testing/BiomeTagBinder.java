package dev.fractalbuildings.mod.testing;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.biome.Biome;

/**
 * Binds the vanilla biome tags (read from the jar's data pack) on the holders of a bootstrapped biome lookup, as a server
 * does, so {@code Holder.is(TagKey)} works in unit tests instead of throwing "Tags not bound" ({@code BiomeAliases} then
 * re-tested every biome on every read and memoised nothing, which the game never does). The lookup from
 * {@code VanillaRegistries.createWorldLookup()} is a wrapper, not a registry, so the holders are bound directly
 * ({@code Holder.Reference.bindTags} is package-private; reflection, test code only). Mod tags
 * ({@code fractalbuildings:no_settlements}) are not in the vanilla pack and stay empty. Idempotent.
 */
public final class BiomeTagBinder {
	private BiomeTagBinder() {}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static synchronized void bind(HolderLookup.Provider provider) {
		HolderLookup.RegistryLookup<Biome> lookup = provider.lookupOrThrow(Registries.BIOME);
		try (var rm = new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource().fullResources()))) {
			Map<TagKey<Biome>, List<Holder<Biome>>> tags = TagLoader.loadTagsForRegistry(rm, Registries.BIOME,
				(TagLoader.ElementLookup) TagLoader.ElementLookup.fromGetters(Registries.BIOME, lookup, lookup));
			Map<Holder<Biome>, Set<TagKey<Biome>>> byHolder = new IdentityHashMap<>();
			lookup.listElements().forEach(h -> byHolder.put(h, new LinkedHashSet<>()));
			tags.forEach((k, hs) -> hs.forEach(h -> byHolder.computeIfAbsent(h, x -> new LinkedHashSet<>()).add(k)));
			Method m = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
			m.setAccessible(true);
			for (var e : byHolder.entrySet()) m.invoke(e.getKey(), new ArrayList<>(e.getValue()));
		} catch (Exception e) {
			throw new IllegalStateException("could not bind biome tags", e);
		}
	}
}
