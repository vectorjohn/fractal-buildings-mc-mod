package dev.fractalbuildings.mod.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * The live side of {@link BiomeAliasRules}: reads a biome holder's tags, memoised per biome id (a world has a few hundred at most).
 * Where tags are not bound (plain unit tests that bootstrap the registries without a server) no tag matches and nothing is memoised,
 * so a vanilla biome is itself.
 */
public final class BiomeAliases {
	private BiomeAliases() {}

	private static final Map<String, String> MEMO = new ConcurrentHashMap<>();

	public static String alias(Holder<Biome> biome) {
		String id = biome.unwrapKey().map(k -> k.identifier().toString()).orElse("?");
		String hit = MEMO.get(id);
		if (hit != null) return hit;
		boolean[] unbound = {false};
		String a = BiomeAliasRules.alias(id, tag -> {
			try {
				return biome.is(TagKey.create(Registries.BIOME, Identifier.parse(tag)));
			} catch (IllegalStateException e) { // "Tags not bound"
				unbound[0] = true;
				return false;
			}
		});
		if (!unbound[0]) MEMO.put(id, a);
		return a;
	}

	/** Forget the memo (a new world may have other tags after a datapack change). */
	public static void clear() { MEMO.clear(); }
}
