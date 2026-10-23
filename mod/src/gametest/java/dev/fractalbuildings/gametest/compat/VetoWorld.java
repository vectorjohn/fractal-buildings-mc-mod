package dev.fractalbuildings.gametest.compat;

import com.mojang.datafixers.DataFixer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Spike S8's harness: the game-test server's world is flat (no villages), so this builds a vanilla NORMAL overworld generator, structure
 * state, {@link StructureCheck} and {@link StructureManager} of its own over a seed, and drives the two public entry points the game uses:
 * {@code createStructures} (generation) and {@code checkStart} (the {@code /locate} primitive). Mods loaded in the run (Terralith, ...)
 * are part of the NORMAL preset, so the same harness covers them.
 */
public record VetoWorld(ServerLevel host, RegistryAccess ra, ChunkGenerator gen, RandomState rs, ChunkGeneratorStructureState state,
		StructureCheck check, StructureManager sm, StructureTemplateManager templates, Registry<Structure> reg, long seed) {
	public static final TagKey<Structure> VILLAGE = TagKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("village"));

	public static void check(boolean ok, String msg) {
		if (!ok) throw new IllegalStateException(msg);
	}

	public record Candidate(ChunkPos chunk, Structure structure, StructurePlacement placement) {}

	public static VetoWorld create(ServerLevel host, long seed) {
		RegistryAccess ra = host.registryAccess();
		var dims = ra.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value().createWorldDimensions();
		ChunkGenerator gen = dims.get(LevelStem.OVERWORLD).orElseThrow().generator();
		var settings = ((NoiseBasedChunkGenerator) gen).generatorSettings().value();
		RandomState rs = RandomState.create(ra.lookupOrThrow(Registries.NOISE), seed, settings);
		ChunkGeneratorStructureState state = gen.createState(ra.lookupOrThrow(Registries.STRUCTURE_SET), rs, seed);
		StructureTemplateManager templates = host.getServer().getStructureTemplateManager();
		DataFixer fixer = DataFixers.getDataFixer();
		// no chunk storage: scanChunk reports nothing, so checkStart falls through to canCreateStructure
		StructureCheck check = new StructureCheck((pos, visitor) -> CompletableFuture.completedFuture(null), ra, templates,
			Level.OVERWORLD, gen, rs, host, gen.getBiomeSource(), seed, fixer);
		StructureManager sm = new StructureManager(host, new WorldOptions(seed, true, false), check);
		return new VetoWorld(host, ra, gen, rs, state, check, sm, templates, ra.lookupOrThrow(Registries.STRUCTURE), seed);
	}

	/** Candidate chunks of every village structure in +-{@code regions} placement regions around the origin. */
	public List<Candidate> villageCandidates(int regions) {
		List<Candidate> out = new ArrayList<>();
		for (Holder<Structure> h : reg.getOrThrow(VILLAGE)) {
			for (StructurePlacement p : state.getPlacementsForStructure(h)) {
				RandomSpreadStructurePlacement rsp = (RandomSpreadStructurePlacement) p;
				for (int rx = -regions; rx <= regions; rx++) {
					for (int rz = -regions; rz <= regions; rz++) {
						out.add(new Candidate(rsp.getPotentialStructureChunk(seed, rx * rsp.spacing(), rz * rsp.spacing()), h.value(), p));
					}
				}
			}
		}
		return out;
	}

	/** The /locate primitive: {@code CHUNK_LOAD_NEEDED} means canCreateStructure passed. */
	public boolean locatePresent(Candidate c) {
		return check.checkStart(c.chunk(), c.structure(), c.placement(), false) != StructureCheckResult.START_NOT_PRESENT;
	}

	/** The chunks /locate reports for the village structures (a chunk counted once). */
	public List<ChunkPos> locateSet(int regions) {
		List<ChunkPos> out = new ArrayList<>();
		for (Candidate c : villageCandidates(regions)) if (locatePresent(c) && !out.contains(c.chunk())) out.add(c.chunk());
		return out;
	}

	/** Vanilla start generation for one chunk on a throwaway ProtoChunk. */
	public ChunkAccess generateStarts(ChunkPos c) {
		ProtoChunk chunk = new ProtoChunk(c, UpgradeData.EMPTY, host, PalettedContainerFactory.create(ra), null);
		gen.createStructures(ra, state, sm, chunk, templates, Level.OVERWORLD);
		return chunk;
	}

	public boolean hasVillageStart(ChunkAccess chunk) {
		for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
			if (e.getValue().isValid() && reg.wrapAsHolder(e.getKey()).is(VILLAGE)) return true;
		}
		return false;
	}
}
