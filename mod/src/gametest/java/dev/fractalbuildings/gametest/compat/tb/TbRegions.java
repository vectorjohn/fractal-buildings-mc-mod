package dev.fractalbuildings.gametest.compat.tb;

import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import terrablender.api.Region;
import terrablender.api.RegionType;
import terrablender.api.Regions;
import terrablender.api.TerraBlenderApi;

/**
 * Registers two extra overworld regions (TerraBlender then builds one climate tree per region, three with its own default one), as
 * spike S1 did: full copies of the vanilla parameter points with one biome swapped, so they are as tie-prone as vanilla. Loaded as the
 * {@code terrablender} entrypoint of the game-test mod, which Fabric only calls when TerraBlender is present.
 */
public class TbRegions implements TerraBlenderApi {
	@Override
	public void onTerraBlenderInitialized() {
		Regions.register(new Region(Identifier.fromNamespaceAndPath("fractalbuildings-test", "b7_a"), RegionType.OVERWORLD, 10) {
			@Override public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
				addModifiedVanillaOverworldBiomes(mapper, b -> b.replaceBiome(Biomes.PLAINS, Biomes.CHERRY_GROVE));
			}
		});
		Regions.register(new Region(Identifier.fromNamespaceAndPath("fractalbuildings-test", "b7_b"), RegionType.OVERWORLD, 10) {
			@Override public void addBiomes(Registry<Biome> registry, Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> mapper) {
				addModifiedVanillaOverworldBiomes(mapper, b -> b.replaceBiome(Biomes.FOREST, Biomes.BIRCH_FOREST));
			}
		});
	}
}
