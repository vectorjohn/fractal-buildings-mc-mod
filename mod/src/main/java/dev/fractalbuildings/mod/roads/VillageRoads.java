package dev.fractalbuildings.mod.roads;

import dev.fractalbuildings.core.road.RoadNet.Col;
import dev.fractalbuildings.core.road.VillageJoin;
import dev.fractalbuildings.core.road.VillageJoin.Box;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * The world half of {@link VillageJoin} (VILLROAD): finds the vanilla villages near a chunk from the structure starts, which exist
 * before any feature runs and in any generation order, and bends the planned road columns around them and adds the spur toward the
 * nearest street. The decision per village is a pure function of its pieces and the planner's columns ({@link VillageJoin#join}), cached
 * per planner; a chunk applies the results of every village that reaches it or its neighbours (the surface plan reads the 3 x 3 columns).
 */
public final class VillageRoads {
	private VillageRoads() {}

	/** How far (in chunks) the structure starts are scanned for a village that could reach the chunk: a village is at most ~6 chunks across from its start. */
	private static final int SCAN = 8;
	/** The columns the surface plan reads beyond the chunk's own 3 x 3 (terraces reach 12). */
	private static final int PLAN_REACH = 12;

	/** The chunk's columns after the bends, and the spur cells that lie in the chunk (x, z). */
	public record Adjusted(List<List<Col>> around, List<int[]> spur, int villages, List<String> spurBlock) {
		public Adjusted(List<List<Col>> around, List<int[]> spur, int villages) { this(around, spur, villages, List.of()); }

		/** Whether the chunk has anything to draw: a road column in any of the 3 x 3 lists, or a spur cell. */
		public boolean hasWork() {
			for (List<Col> l : around) if (!l.isEmpty()) return true;
			return !spur.isEmpty();
		}
	}

	public record Key(String structure, int cx, int cz) {}

	private static final Map<WorldPlanner, Map<Key, VillageJoin.Result>> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
	private static final java.util.concurrent.atomic.AtomicBoolean WARNED = new java.util.concurrent.atomic.AtomicBoolean();
	/** Test telemetry: villages joined (bent or spurred), by structure id and start chunk. */
	public static final Set<String> JOINED = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** Test control: switch the join off (the digests and the overlap checks must then show the difference). */
	public static volatile boolean OFF = false;

	/** A village the join applies to: a vanilla village structure (its start is read from the starts, so it never moves with the chunk). */
	static boolean isVillage(Identifier id) {
		return id != null && id.getNamespace().equals("minecraft") && id.getPath().startsWith("village_");
	}

	public static Adjusted adjust(WorldGenLevel level, WorldPlanner planner, ChunkPos cp, List<List<Col>> around) {
		if (OFF || !(level instanceof WorldGenRegion region)) return new Adjusted(around, List.of(), 0);
		try {
			return adjust(region, planner, cp, around);
		} catch (RuntimeException e) {
			if (!WARNED.getAndSet(true)) FractalBuildingsMod.LOGGER.warn("Could not bend the roads round the villages near chunk {}", cp, e);
			return new Adjusted(around, List.of(), 0);
		}
	}

	private static Adjusted adjust(WorldGenRegion region, WorldPlanner planner, ChunkPos cp, List<List<Col>> around) {
		if (!roadNear(planner, cp, around)) return new Adjusted(around, List.of(), 0); // no road within a spur's reach: skip the 17 x 17 start scan
		var registry = region.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		List<Found> found = scan((x, z) -> region.hasChunk(x, z) ? region.getChunk(x, z, ChunkStatus.STRUCTURE_STARTS, false) : null, registry, region.getLevel(), cp.x(), cp.z(), SCAN);
		return adjusted(found, planner, cp, around);
	}

	/** Whether any planned road column lies within {@link VillageJoin#SPUR_MAX} + the band of the chunk (a spur starts at a road column); the 3 x 3 lists are already in hand. */
	static boolean roadNear(WorldPlanner planner, ChunkPos cp, List<List<Col>> around) {
		for (List<Col> l : around) if (!l.isEmpty()) return true;
		for (int dz = -4; dz <= 4; dz++) for (int dx = -4; dx <= 4; dx++) {
			if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) continue;
			if (!planner.roadColumns(cp.x() + dx, cp.z() + dz).isEmpty()) return true;
		}
		return false;
	}

	/** A village start: its structure id and start chunk (the identity of the village), the start, and its pieces as the join reads them. */
	public record Found(Key key, StructureStart start, VillageJoin.Village village, VillageJoin.Heights heights) {}

	/** A start's village and heights, built once per start (the start object belongs to one world, so it never serves another). */
	private record Derived(VillageJoin.Village village, VillageJoin.Heights heights) {}

	private static final Map<StructureStart, Derived> DERIVED = Collections.synchronizedMap(new WeakHashMap<>());

	/** Test control (VILLROAD r8): no ground heights, so the spur is laid on the ground ungraded, as in r7. */
	public static volatile boolean UNGRADED = false;
	/** Test control (VILLROAD r8): the chunks draw only each spur's centre line, stopping 2 cells short of its end (no band). */
	public static volatile boolean MUTANT_SPUR_SHORT = false;

	/** The vanilla village starts in the chunks within {@code radius} of (cx, cz), sorted by (structure, start chunk). */
	public static List<Found> scan(java.util.function.BiFunction<Integer, Integer, ChunkAccess> chunks, net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.Structure> registry, ServerLevel level, int cx0, int cz0, int radius) {
		List<Found> found = new ArrayList<>();
		for (int dz = -radius; dz <= radius; dz++) {
			for (int dx = -radius; dx <= radius; dx++) {
				int cx = cx0 + dx, cz = cz0 + dz;
				ChunkAccess chunk = chunks.apply(cx, cz);
				if (chunk == null) continue;
				var starts = chunk.getAllStarts();
				if (starts.isEmpty()) continue;
				for (var e : starts.entrySet()) {
					StructureStart s = e.getValue();
					Identifier id = registry.getKey(e.getKey());
					if (!isVillage(id) || s == null || !s.isValid()) continue;
					Derived d = DERIVED.computeIfAbsent(s, st -> new Derived(village(st, level.getServer().getStructureTemplateManager()), VillageGround.of(level, st)));
					found.add(new Found(new Key(id.toString(), cx, cz), s, d.village(), d.heights()));
				}
			}
		}
		found.sort((a, b) -> {
			int c = a.key().structure().compareTo(b.key().structure());
			if (c != 0) return c;
			return a.key().cx() != b.key().cx() ? Integer.compare(a.key().cx(), b.key().cx()) : Integer.compare(a.key().cz(), b.key().cz());
		});
		return found;
	}

	/** The join of one village, cached per planner (a pure function, so eviction changes nothing). */
	public static VillageJoin.Result resultOf(WorldPlanner planner, Found f) {
		if (UNGRADED) return f.village().pieces().isEmpty() ? VillageJoin.Result.NONE : VillageJoin.join(f.village(), window(planner, VillageJoin.influence(f.village())), null); // the control, never cached
		Map<Key, VillageJoin.Result> cache = CACHE.computeIfAbsent(planner, p -> Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
			@Override protected boolean removeEldestEntry(Map.Entry<Key, VillageJoin.Result> e) { return size() > 64; }
		}));
		VillageJoin.Result r = cache.get(f.key());
		if (r == null) {
			VillageJoin.Village v = f.village();
			r = v.pieces().isEmpty() ? VillageJoin.Result.NONE : VillageJoin.join(v, window(planner, VillageJoin.influence(v)), f.heights());
			cache.put(f.key(), r);
		}
		return r;
	}

	/** The chunk's 3 x 3 column lists with the joins of every village in {@code found} that reaches them applied, and the spur cells in the chunk. */
	public static Adjusted adjusted(List<Found> found, WorldPlanner planner, ChunkPos cp, List<List<Col>> around) {
		Box reach = new Box(cp.getMinBlockX() - 16 - PLAN_REACH, cp.getMinBlockZ() - 16 - PLAN_REACH, cp.getMinBlockX() + 31 + PLAN_REACH, cp.getMinBlockZ() + 31 + PLAN_REACH);
		List<List<Col>> out = around;
		List<int[]> spur = new ArrayList<>();
		List<String> spurBlock = new ArrayList<>();
		int n = 0;
		for (Found f : found) {
			VillageJoin.Village v = f.village();
			if (v.pieces().isEmpty() || !VillageJoin.influence(v).meets(reach)) continue;
			VillageJoin.Result r = resultOf(planner, f);
			if (r.isEmpty()) continue;
			n++;
			if (RoadFeature.RECORD_STATS) JOINED.add(f.key().structure() + " " + f.key().cx() + "," + f.key().cz());
			out = apply(out, cp, r);
			String sb = streetBlock(f.key().structure());
			List<int[]> cells = r.spur();
			if (MUTANT_SPUR_SHORT) { // the control: a bare centre line that stops 2 cells short of the street
				cells = new ArrayList<>();
				for (List<int[]> line : r.lines()) cells.addAll(line.subList(0, Math.max(0, line.size() - 2)));
			}
			for (int[] c : cells) if ((c[0] >> 4) == cp.x() && (c[1] >> 4) == cp.z()) { spur.add(c); spurBlock.add(sb); }
		}
		return new Adjusted(out, spur, n, spurBlock);
	}

	/** The block of a village's streets: the path block of its street templates (dirt_path; smooth_sandstone in a desert village). */
	static String streetBlock(String structure) {
		return structure.endsWith("desert") ? "minecraft:smooth_sandstone" : "minecraft:dirt_path";
	}

	/** The blocks a vanilla street template lays its path in (plains, savanna, snowy, taiga: dirt_path; desert: smooth_sandstone). */
	public static final Set<String> PATH_BLOCKS = Set.of("minecraft:dirt_path", "minecraft:smooth_sandstone");

	/**
	 * Lays the spur cells of the chunk into its plan: road at each cell's graded height (cut or filled to it; on the ground when ungraded), where nothing else is drawn, no water, no
	 * district. Returns which plan cells are spur cells (null when none): they may lie in a street piece's box, outside its path, and
	 * the feature's yield to foreign pieces must not take them back there.
	 */
	public static boolean[] addSpur(dev.fractalbuildings.core.road.RoadSurface.Plan plan, List<int[]> spur, List<String> spurBlock, boolean[] district, dev.fractalbuildings.core.road.RoadSurface.Ground ground) {
		boolean[] mask = null;
		for (int si = 0; si < spur.size(); si++) {
			int[] c = spur.get(si);
			int i = (c[1] & 15) * 16 + (c[0] & 15);
			if (district != null && district[i]) continue;
			if (plan.kind[i] == dev.fractalbuildings.core.road.RoadSurface.ROAD || plan.kind[i] == dev.fractalbuildings.core.road.RoadSurface.DECK) continue;
			if (ground.wet(c[0], c[1])) continue;
			if (plan.kind[i] == dev.fractalbuildings.core.road.RoadSurface.TERRACE) plan.terraces--;
			int g = ground.topY(c[0], c[1]);
			plan.kind[i] = dev.fractalbuildings.core.road.RoadSurface.ROAD;
			plan.surf[i] = c.length > 2 && c[2] != Integer.MIN_VALUE ? c[2] : g; // the graded surface (cut or fill to it, DS-31), else the ground
			plan.ground[i] = g;
			plan.block[i] = si < spurBlock.size() ? spurBlock.get(si) : null; // the village street's own material (null: the biome style)
			plan.roads++;
			if (mask == null) mask = new boolean[256];
			mask[i] = true;
		}
		return mask;
	}

	/**
	 * The pieces of a village as boxes, sorted, the street pieces among them (templates under {@code .../streets/}), and the cells of
	 * the streets' path blocks ({@link #PATH_BLOCKS} in the street templates, placed by each piece's position and rotation). The templates
	 * are static game data and the pieces are fixed at STRUCTURE_STARTS, so the cells are the same whichever chunk asks, in any order;
	 * the street processors turn 10-20% of the path to grass at random and planks over water, which the cells still count as street.
	 * {@code templates} null: no path cells (the spur then aims at the street boxes).
	 */
	public static VillageJoin.Village village(StructureStart start, StructureTemplateManager templates) {
		List<Box> pieces = new ArrayList<>(), streets = new ArrayList<>();
		java.util.TreeMap<Long, int[]> paths = new java.util.TreeMap<>();
		for (StructurePiece p : start.getPieces()) {
			var bb = p.getBoundingBox();
			Box b = new Box(bb.minX(), bb.minZ(), bb.maxX(), bb.maxZ());
			pieces.add(b);
			if (!(p instanceof PoolElementStructurePiece pe && pe.getElement() instanceof SinglePoolElement se && se.getTemplateLocation().getPath().contains("/streets/"))) continue;
			streets.add(b);
			if (templates == null) continue;
			var settings = new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(pe.getRotation());
			for (int[] rel : templatePaths(templates, se.getTemplateLocation())) {
				net.minecraft.core.BlockPos w = net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.calculateRelativePosition(settings, new net.minecraft.core.BlockPos(rel[0], 0, rel[1])).offset(pe.getPosition());
				if (b.contains(w.getX(), w.getZ())) paths.put(((long) w.getZ() << 32) + (w.getX() + (1L << 31)), new int[] {w.getX(), w.getZ()}); // (z, x) order
			}
		}
		java.util.Comparator<Box> order = (a, b) -> a.minZ() != b.minZ() ? Integer.compare(a.minZ(), b.minZ()) : a.minX() != b.minX() ? Integer.compare(a.minX(), b.minX())
			: a.maxZ() != b.maxZ() ? Integer.compare(a.maxZ(), b.maxZ()) : Integer.compare(a.maxX(), b.maxX());
		pieces.sort(order);
		streets.sort(order);
		return new VillageJoin.Village(pieces, streets, List.copyOf(paths.values()));
	}

	public static VillageJoin.Village village(StructureStart start) { return village(start, null); }

	private static final Map<Identifier, List<int[]>> TEMPLATE_PATHS = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * The template-relative (x, z) cells of a street template's path blocks, read once per template. Read through {@code save()} rather
	 * than {@code filterBlocks}, whose per-palette cache is a plain HashMap that worldgen threads already fill concurrently.
	 */
	static List<int[]> templatePaths(StructureTemplateManager templates, Identifier id) {
		return TEMPLATE_PATHS.computeIfAbsent(id, k -> {
			net.minecraft.nbt.CompoundTag tag = templates.getOrCreate(k).save(new net.minecraft.nbt.CompoundTag());
			net.minecraft.nbt.ListTag palette = tag.getListOrEmpty("palette");
			if (palette.isEmpty()) palette = tag.getListOrEmpty("palettes").getListOrEmpty(0);
			boolean[] isPath = new boolean[palette.size()];
			for (int i = 0; i < palette.size(); i++) { net.minecraft.nbt.CompoundTag st = palette.getCompoundOrEmpty(i); isPath[i] = PATH_BLOCKS.contains(st.getStringOr("id", st.getStringOr("Name", ""))); } // 26.3 NbtUtils.writeBlockState writes "id" (older data: "Name")
			java.util.TreeMap<Long, int[]> cells = new java.util.TreeMap<>();
			net.minecraft.nbt.ListTag blocks = tag.getListOrEmpty("blocks");
			for (int i = 0; i < blocks.size(); i++) {
				net.minecraft.nbt.CompoundTag bt = blocks.getCompoundOrEmpty(i);
				int s = bt.getIntOr("state", -1);
				if (s < 0 || s >= isPath.length || !isPath[s]) continue;
				net.minecraft.nbt.ListTag pos = bt.getListOrEmpty("pos");
				int x = pos.getIntOr(0, 0), z = pos.getIntOr(2, 0);
				cells.put(((long) z << 32) + x, new int[] {x, z});
			}
			return List.copyOf(cells.values());
		});
	}

	/** The road columns of every chunk the box meets, the planner's own (before any bend). */
	private static List<Col> window(WorldPlanner planner, Box b) {
		List<Col> cols = new ArrayList<>();
		for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) cols.addAll(planner.roadColumns(cx, cz));
		return cols;
	}

	/** The 3 x 3 lists of the chunk with a village's removals and additions applied; each list stays sorted by (z, x). */
	private static List<List<Col>> apply(List<List<Col>> around, ChunkPos cp, VillageJoin.Result r) {
		List<List<Col>> out = new ArrayList<>(9);
		Set<Long> removed = r.removed();
		for (int i = 0; i < 9; i++) {
			int cx = cp.x() + (i % 3) - 1, cz = cp.z() + (i / 3) - 1;
			List<Col> add = new ArrayList<>();
			for (Col c : r.added()) if ((c.x() >> 4) == cx && (c.z() >> 4) == cz) add.add(c);
			boolean drops = false;
			if (!removed.isEmpty()) for (Col c : around.get(i)) if (removed.contains(VillageJoin.key(c.x(), c.z()))) { drops = true; break; }
			if (!drops && add.isEmpty()) { out.add(around.get(i)); continue; }
			Set<Long> have = new HashSet<>();
			List<Col> l = new ArrayList<>();
			for (Col c : around.get(i)) if (!removed.contains(VillageJoin.key(c.x(), c.z()))) { l.add(c); have.add(VillageJoin.key(c.x(), c.z())); }
			for (Col c : add) if (have.add(VillageJoin.key(c.x(), c.z()))) l.add(c);
			l.sort((p, q) -> p.z() != q.z() ? Integer.compare(p.z(), q.z()) : Integer.compare(p.x(), q.x()));
			out.add(l);
		}
		return out;
	}
}
