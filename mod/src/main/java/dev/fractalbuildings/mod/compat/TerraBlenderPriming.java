package dev.fractalbuildings.mod.compat;

import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

/**
 * Biome priming under TerraBlender with more than one region (spike S1, ADR 0003 D3). Each region has its own climate tree with its own
 * thread-local "last leaf", and the region is chosen per position, so one fixed priming lookup primes the wrong tree. Before a lookup we
 * look up a fixed anchor inside the target's region instead. Anchors depend on the region index and the world's region layout only: 16 candidates are scanned on a
 * fixed lattice, and the first one whose answer does not depend on which other candidate primed first wins.
 *
 * <p>TerraBlender is reached by reflection only (no compile dependency). {@link #of} is empty when it is absent, has a single region, or
 * its API is not what we expect (then the log says so and the plain fixed prime is used).
 */
public final class TerraBlenderPriming {
	private static final int QY = 16; // quart y of block y = 64, as S1
	private static final int CANDIDATES = 16;

	private final Method uniqueness;
	private final Object list;
	private final int regions;
	/** Anchor quart (x, z) per region, or null when the scan found none. */
	private final int[][] anchors;

	private TerraBlenderPriming(Method uniqueness, Object list, int regions, int[][] anchors) {
		this.uniqueness = uniqueness;
		this.list = list;
		this.regions = regions;
		this.anchors = anchors;
	}

	public int regions() { return regions; }

	public int[] anchor(int region) { return region >= 0 && region < regions ? anchors[region] : null; }

	/** The region index at a quart position (cheap). */
	public int region(int qx, int qz) {
		try {
			return (int) uniqueness.invoke(list, qx, QY, qz);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Primes the target's region tree, then looks up the target: an answer independent of earlier lookups. */
	public Holder<Biome> lookup(BiomeResolver resolver, int qx, int qz, int qy) {
		int[] a = anchor(region(qx, qz));
		if (a != null) resolver.getNoiseBiome(a[0], QY, a[1]);
		return resolver.getNoiseBiome(qx, qy, qz);
	}

	public static boolean modLoaded() { return FabricLoader.getInstance().isModLoaded("terrablender"); }

	/**
	 * @param resolver a resolver for this world's source and random state (used on the calling thread only, to find the anchors)
	 */
	public static Optional<TerraBlenderPriming> of(MultiNoiseBiomeSource source, BiomeResolver resolver) {
		if (!modLoaded()) return Optional.empty();
		try {
			Field f = MultiNoiseBiomeSource.class.getDeclaredField("parameters");
			f.setAccessible(true);
			Object either = f.get(source);
			Object left = ((com.mojang.datafixers.util.Either<?, ?>) either).left().orElse(null);
			Object pl = left != null ? left : ((Holder<?>) ((com.mojang.datafixers.util.Either<?, ?>) either).right().orElseThrow()).value();
			if (!(pl instanceof net.minecraft.world.level.biome.Climate.ParameterList<?>)) {
				// a preset holder: its parameters() is the list
				pl = pl.getClass().getMethod("parameters").invoke(pl);
			}
			Class<?> ext = Class.forName("terrablender.worldgen.IExtendedParameterList");
			if (!ext.isInstance(pl)) {
				FractalBuildingsMod.LOGGER.warn("TerraBlender is loaded but the biome parameter list is not its extended list; using the plain biome priming");
				return Optional.empty();
			}
			int trees = (int) ext.getMethod("getTreeCount").invoke(pl);
			if (trees <= 1) return Optional.empty();
			Method uniq = ext.getMethod("getUniqueness", int.class, int.class, int.class);
			TerraBlenderPriming probe = new TerraBlenderPriming(uniq, pl, trees, new int[trees][]);
			probe.findAnchors(resolver);
			StringBuilder sb = new StringBuilder();
			for (int r = 0; r < trees; r++) sb.append(r).append("=").append(probe.anchors[r] == null ? "none" : probe.anchors[r][0] + "," + probe.anchors[r][1]).append(' ');
			FractalBuildingsMod.LOGGER.info("TerraBlender with {} climate trees: region-aware biome priming, anchors (quart x,z) {}", trees, sb.toString().trim());
			return Optional.of(probe);
		} catch (ReflectiveOperationException | RuntimeException e) {
			FractalBuildingsMod.LOGGER.warn("TerraBlender is loaded but its API could not be used ({}); using the plain biome priming", e.toString());
			return Optional.empty();
		}
	}

	/** Fills {@link #anchors}; deterministic in the region index and the world's biome layout. */
	private void findAnchors(BiomeResolver resolver) {
		int ox = 0, oz = 0;
		List<List<int[]>> cand = new ArrayList<>();
		for (int r = 0; r < regions; r++) cand.add(new ArrayList<>());
		for (int i = 0; i < 80; i++) {
			for (int j = 0; j < 80; j++) {
				int qx = i * 7 + 3 + ox, qz = j * 7 + 1 + oz;
				int r = region(qx, qz);
				if (r >= 0 && r < regions && cand.get(r).size() < CANDIDATES) cand.get(r).add(new int[] {qx, qz});
			}
		}
		for (int r = 0; r < regions; r++) {
			List<int[]> c = cand.get(r);
			if (c.isEmpty()) continue;
			anchors[r] = c.get(0);
			for (int[] a : c) {
				Holder<Biome> first = null;
				boolean ok = true;
				for (int[] p : c) {
					resolver.getNoiseBiome(p[0], QY, p[1]);
					Holder<Biome> b = resolver.getNoiseBiome(a[0], QY, a[1]);
					if (first == null) first = b;
					else if (b != first) { ok = false; break; }
				}
				if (ok) { anchors[r] = a; break; }
			}
		}
	}
}
