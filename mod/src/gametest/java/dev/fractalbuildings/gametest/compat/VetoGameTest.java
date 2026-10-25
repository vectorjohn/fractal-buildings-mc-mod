package dev.fractalbuildings.gametest.compat;

import static dev.fractalbuildings.gametest.compat.VetoWorld.check;

import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.settlement.Cell;
import dev.fractalbuildings.core.settlement.CellGeometry;
import dev.fractalbuildings.core.settlement.Ids;
import dev.fractalbuildings.core.settlement.Pt;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.WorldConfig;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.core.world.WorldProbes;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.compat.BiomeAliasRules;
import dev.fractalbuildings.mod.compat.BiomeAliases;
import dev.fractalbuildings.mod.compat.CityVeto;
import dev.fractalbuildings.mod.probe.LiveProbes;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/**
 * B7's veto (ADR 0003 W7, S8): vanilla villages are kept out of a settlement's footprint plus {@link CityVeto#MARGIN} blocks, at start
 * generation and at {@code /locate}, and still generate everywhere else. Driven through {@link VetoWorld} (a NORMAL generator of our own,
 * as S8 did). The settlement is a one-site planner ({@code WorldPlanner.forSite}) placed over a known village, so the expected set is plain
 * geometry the test computes itself.
 */
// gametest-order: 5
public class VetoGameTest {
	static final long SEED = 8_001L;
	static final int REGIONS = 14;

	static void log(String m) { System.out.println("B7: " + m); }

	/** A one-site planner over this world's generator (the probes are never asked: the veto reads the region view only). */
	static WorldPlanner siteAt(VetoWorld w, int x, int z, int radius) {
		var gen = (NoiseBasedChunkGenerator) w.gen();
		LiveProbes lp = new LiveProbes(gen, w.rs());
		WorldProbes probes = new WorldProbes(lp.region(), lp.coarse(32), lp.exact(), lp.biome());
		Cell claimant = CellGeometry.owner(new RegionField(SEED, WorldConfig.DEFAULT_CELL_SIZE), x, z);
		SettlementSite site = new SettlementSite(Ids.site(claimant.cx(), claimant.cz()), Tier.TOWN, new Pt(x, z), radius, claimant, List.of(claimant));
		return WorldPlanner.forSite(SEED, WorldConfig.defaults(), probes, null, WorldPlanner.Caches.DEFAULT, site);
	}

	static double dist(ChunkPos c, int x, int z) { return Math.hypot(c.getMiddleBlockX() - x, c.getMiddleBlockZ() - z); }

	@GameTest(maxTicks = 2400)
	public void villagesVetoedInsideTheFootprintAndKeptOutside(GameTestHelper helper) {
		WorldPlanners.setVeto(true);
		WorldPlanners.overrideForTests(null);
		try {
			VetoWorld base = VetoWorld.create(helper.getLevel(), SEED);
			// baseline: no settlement anywhere (override = a planner whose site is far away), so nothing is vetoed
			WorldPlanners.overrideForTests(siteAt(base, 900_000, 900_000, 300));
			List<ChunkPos> baseline = base.locateSet(REGIONS);
			log("baseline /locate villages: " + baseline.size());
			check(baseline.size() >= 20, "enough villages to test (" + baseline.size() + ")");
			ChunkPos va = baseline.stream().min((a, b) -> Long.compare(d2(a), d2(b))).orElseThrow();
			int cx = va.getMiddleBlockX(), cz = va.getMiddleBlockZ();
			int radius = 300;
			ChunkPos vb = baseline.stream().filter(c -> dist(c, cx, cz) > radius + CityVeto.MARGIN + 40).min((a, b) -> Double.compare(dist(a, cx, cz), dist(b, cx, cz))).orElseThrow();
			check(dist(vb, cx, cz) > 0, "a second village exists outside");

			// the settlement over va
			WorldPlanner town = siteAt(base, cx, cz, radius);
			WorldPlanners.overrideForTests(town);
			VetoWorld v = VetoWorld.create(helper.getLevel(), SEED); // a fresh StructureCheck: no stale memo of the baseline
			List<ChunkPos> expected = new ArrayList<>();
			for (ChunkPos c : baseline) if (dist(c, cx, cz) > radius + CityVeto.MARGIN) expected.add(c);
			check(expected.size() < baseline.size() && expected.size() >= baseline.size() - 12, "the footprint removes some, not all villages (" + expected.size() + " of " + baseline.size() + ")");
			check(!expected.contains(va) && expected.contains(vb), "va inside, vb outside");

			List<ChunkPos> located = v.locateSet(REGIONS);
			log("veto /locate villages: " + located.size() + " (expected " + expected.size() + ")");
			for (ChunkPos c : baseline) check(located.contains(c) == expected.contains(c), "/locate disagrees with the footprint at " + c + " (" + (int) dist(c, cx, cz) + " blocks from the centre)");

			int kept = 0, vetoedNoStart = 0, missing = 0;
			for (ChunkPos c : baseline) {
				boolean started = v.hasVillageStart(v.generateStarts(c));
				if (!expected.contains(c)) {
					check(!started, "generation: a village started at vetoed " + c);
					vetoedNoStart++;
				} else if (started) kept++;
				else missing++; // /locate says "possible"; the set picked another entry or the biome failed
			}
			log("generation: vetoed with no start " + vetoedNoStart + ", kept with a start " + kept + ", kept without (biome) " + missing);
			check(vetoedNoStart == baseline.size() - expected.size() && kept >= 10, "villages outside still generate (" + kept + ")");

			// the margin: va's neighbour vb sits just outside; grow the radius until the margin reaches it, shrink it until it does not
			double d = dist(vb, cx, cz);
			for (int delta : new int[] {-12, 12}) {
				int r = (int) d - CityVeto.MARGIN + delta;
				WorldPlanners.overrideForTests(siteAt(base, cx, cz, r));
				VetoWorld m = VetoWorld.create(helper.getLevel(), SEED);
				boolean located2 = m.locateSet(REGIONS).contains(vb);
				boolean started = m.hasVillageStart(m.generateStarts(vb));
				log("margin: radius " + r + " (village at " + (int) d + "): located " + located2 + ", started " + started);
				if (delta < 0) check(located2 && started, "a village just outside the margin generates and is located (radius " + r + ")");
				else check(!located2 && !started, "a village just inside the margin is vetoed at both (radius " + r + ")");
			}

			// the setting: with vetoVanillaVillages off nothing is vetoed
			WorldPlanners.overrideForTests(town);
			WorldPlanners.setVeto(false);
			VetoWorld off = VetoWorld.create(helper.getLevel(), SEED);
			check(off.locateSet(REGIONS).size() == baseline.size(), "veto off: /locate reports every village");
			check(off.hasVillageStart(off.generateStarts(va)), "veto off: the village at the settlement generates");
			helper.succeed();
		} finally {
			WorldPlanners.overrideForTests(null);
			WorldPlanners.setVeto(true);
		}
	}

	static long d2(ChunkPos c) { return (long) c.x() * c.x() + (long) c.z() * c.z(); }

	/**
	 * The real region planner (no override) against its own footprint, over the same village candidates: the veto and the independent
	 * {@code footprintNear} agree, whatever mix of settlements and villages the seed gives; also the cost per candidate.
	 */
	@GameTest(maxTicks = 2400)
	public void realPlannerVetoAgreesWithItsFootprint(GameTestHelper helper) {
		WorldPlanners.setVeto(false);
		WorldPlanners.overrideForTests(null);
		try {
			VetoWorld off = VetoWorld.create(helper.getLevel(), SEED);
			List<ChunkPos> baseline = off.locateSet(REGIONS);
			WorldPlanners.setVeto(true);
			VetoWorld on = VetoWorld.create(helper.getLevel(), SEED);
			WorldPlanner planner = WorldPlanners.find(on.rs(), on.gen(), SEED).orElseThrow();
			long t0 = System.nanoTime();
			int inside = 0;
			List<ChunkPos> expected = new ArrayList<>();
			for (ChunkPos c : baseline) {
				if (planner.footprintNear(c.getMiddleBlockX(), c.getMiddleBlockZ(), CityVeto.MARGIN)) inside++;
				else expected.add(c);
			}
			long ms = (System.nanoTime() - t0) / 1_000_000;
			List<ChunkPos> located = on.locateSet(REGIONS);
			log("real planner: " + baseline.size() + " villages, " + inside + " inside a footprint+margin, " + located.size() + " located; footprint scan " + ms + " ms for " + baseline.size() + " candidates (cold)");
			check(located.size() == expected.size(), "/locate set = baseline minus footprints (" + located.size() + " vs " + expected.size() + ")");
			for (ChunkPos c : baseline) check(located.contains(c) == expected.contains(c), "disagreement at " + c);
			helper.succeed();
		} finally {
			WorldPlanners.setVeto(true);
		}
	}

	// ------------------------------------------------------------------------------------------------------------------ biomes

	@GameTest
	public void noSettlementsTagIsHonouredAndForeignBiomesAreAliased(GameTestHelper helper) {
		var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
		// the test pack's #fractalbuildings:no_settlements holds pale_garden
		var pale = biomes.getOrThrow(Biomes.PALE_GARDEN);
		check(BiomeAliases.alias(pale).equals(BiomeAliasRules.BANNED_STAND_IN), "a biome in #fractalbuildings:no_settlements is banned: " + BiomeAliases.alias(pale));
		var plains = biomes.getOrThrow(Biomes.PLAINS);
		check(BiomeAliases.alias(plains).equals("minecraft:plains"), "other vanilla biomes are themselves");
		// every biome of the registry resolves to a vanilla id the core knows; log the foreign ones
		int foreign = 0;
		java.util.TreeMap<String, Integer> hist = new java.util.TreeMap<>();
		for (var h : biomes.listElements().toList()) {
			String id = h.key().identifier().toString();
			String a = BiomeAliases.alias(h);
			check(a.startsWith("minecraft:"), id + " -> " + a);
			if (!id.startsWith("minecraft:")) { foreign++; hist.merge(a, 1, Integer::sum); }
		}
		log("biome aliases: " + foreign + " foreign biomes in the registry " + hist);
		FractalBuildingsMod.LOGGER.info("[B7] foreign biomes aliased: {} {}", foreign, hist);
		helper.succeed();
	}
}
