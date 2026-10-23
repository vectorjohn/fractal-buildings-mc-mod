package dev.fractalbuildings.mod.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.fractalbuildings.core.world.FbSettings;
import dev.fractalbuildings.core.world.WorldConfig;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The world's pinned settings, in {@code data/fractalbuildings.dat} of the overworld's data storage. Created on first use from the
 * config file; from then on only the saved values count, so editing the file never changes an existing world (plan B6).
 * The planner, commands and everything else read {@link #settings()} from {@link #get}.
 */
public final class WorldConfigData extends SavedData {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("fractalbuildings", "config");

	public static final Codec<WorldConfigData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("cell_size").forGetter(d -> d.s.world().cellSize()),
			Codec.DOUBLE.fieldOf("frequency").forGetter(d -> d.s.world().frequency()),
			Codec.INT.fieldOf("max_radius").forGetter(d -> d.s.world().maxRadius()),
			Codec.STRING.listOf().fieldOf("styles").forGetter(d -> List.copyOf(d.s.world().styles())),
			Codec.INT.fieldOf("gen_version").forGetter(d -> d.s.world().genVersion()),
			Codec.BOOL.fieldOf("villagers").forGetter(d -> d.s.villagers()),
			Codec.BOOL.fieldOf("veto_vanilla_villages").forGetter(d -> d.s.vetoVanillaVillages()),
			// optional: a world saved before the roads existed keeps its chunks as they were (roads off); a new world takes the config file's value
			Codec.BOOL.optionalFieldOf("intercity_roads", false).forGetter(d -> d.s.world().intercityRoads())
	).apply(i, (cell, freq, radius, styles, gen, vill, veto, roads) ->
			new WorldConfigData(new FbSettings(new WorldConfig(cell, freq, radius, Set.copyOf(styles), gen, roads), vill, veto))));

	/** The saved-data type; {@code fresh} supplies the values of a new world (the config file in the game). */
	public static SavedDataType<WorldConfigData> type(Supplier<FbSettings> fresh) {
		return new SavedDataType<>(ID, () -> new WorldConfigData(fresh.get()), CODEC, DataFixTypes.SAVED_DATA_RANDOM_SEQUENCES);
	}

	public static final SavedDataType<WorldConfigData> TYPE = type(FbConfig::read);

	private final FbSettings s;

	public WorldConfigData(FbSettings s) { this.s = s; }

	public FbSettings settings() { return s; }

	/** The world's settings, pinning them from the config file if this is the first call in a new world. */
	public static WorldConfigData get(MinecraftServer server) { return server.getDataStorage().computeIfAbsent(TYPE); }
}
