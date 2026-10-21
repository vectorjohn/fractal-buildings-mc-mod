package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.region.RegionField;
import dev.fractalbuildings.core.region.Tiers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/**
 * Cell skeletons (plan step SK, ADR 0003 P1 level one). {@link #build} is a pure function of
 * (seed, region view, cell, coarse probe, district grid): cells can be built in any order, with a fresh probe
 * and no shared state, and the neighbours still agree on every gate. {@link #assemble} merges a settlement's cell
 * skeletons into the {@code fb-settlement/1} plan.
 *
 * <p>The skeleton reads only the coarse lattice, never sets a floor height and tolerates heights that are off by
 * 20. A cell's roads keep to the lattice columns it owns (nearest region site), plus the gate columns on its
 * border, which both neighbours derive from a hash of the cell pair.
 */
public final class Skeletons {
	private Skeletons() {}

	/** Window margin around the footprint, blocks. */
	public static final int MARGIN = 64;
	/** Shortest shared edge that gets a gate. */
	public static final int MIN_GATE_EDGE = 64;
	public static final int GATE_TRIES = 5;

	public static CellSkeleton build(long seed, RegionView view, Cell cell, CoarseProbe probe, DistrictGrid grid) {
		return build(seed, view, cell, probe, grid, TierPrograms.of(view.siteOf(cell).orElseThrow(() -> new IllegalArgumentException("cell " + cell + " belongs to no settlement")).tier()));
	}

	public static CellSkeleton build(long seed, RegionView view, Cell cell, CoarseProbe probe, DistrictGrid grid, TierProgram program) {
		SettlementSite site = view.siteOf(cell).orElseThrow(() -> new IllegalArgumentException("cell " + cell + " belongs to no settlement"));
		RegionField field = view.field();
		CoarseTerrain terrain = new CoarseTerrain(probe);
		int step = terrain.step;
		DensityField density = new DensityField(seed, site, terrain, grid);
		QuarterField quarters = new QuarterField(seed, site, terrain);
		List<Pt> poly = CellGeometry.polygon(field, cell);

		// 1. gates on the edges shared with the settlement's other cells
		List<Gate> cellGates = new ArrayList<>();
		for (Cell o : sorted(site.cells())) {
			if (o.equals(cell)) continue;
			Pt g = gateNode(seed, field, terrain, cell, o);
			if (g == null) continue;
			Cell lo = CellGeometry.compare(cell, o) <= 0 ? cell : o, hi = lo == cell ? o : cell;
			cellGates.add(new Gate("g?", GateKind.CELL, g.x(), g.z(), List.of(lo, hi), null));
		}

		// 2. window: polygon box clipped to the footprint square, widened to hold the gates
		int reach = density.maxRadius() + MARGIN;
		int minX = Math.max(minOf(poly, true), site.centre().x() - reach), maxX = Math.min(maxOf(poly, true), site.centre().x() + reach);
		int minZ = Math.max(minOf(poly, false), site.centre().z() - reach), maxZ = Math.min(maxOf(poly, false), site.centre().z() + reach);
		if (minX > maxX || minZ > maxZ) {
			minX = maxX = field.siteX(cell.cx(), cell.cz());
			minZ = maxZ = field.siteZ(cell.cx(), cell.cz());
		}
		for (Gate g : cellGates) {
			minX = Math.min(minX, g.x());
			maxX = Math.max(maxX, g.x());
			minZ = Math.min(minZ, g.z());
			maxZ = Math.max(maxZ, g.z());
		}
		int x0 = Math.floorDiv(minX, step) * step, z0 = Math.floorDiv(minZ, step) * step;
		int nx = Math.floorDiv(maxX - x0, step) + 1, nz = Math.floorDiv(maxZ - z0, step) + 1;
		SkeletonContext c = new SkeletonContext(seed, site, cell, field, terrain, grid, density, quarters, poly, x0, z0, nx, nz);

		// 3. ownership and the pre-reachability urban test
		for (int j = 0; j < nz; j++) {
			for (int i = 0; i < nx; i++) {
				int x = x0 + i * step, z = z0 + j * step, k = j * nx + i;
				c.owned[k] = CellGeometry.owns(field, cell, x, z);
				c.candidate[k] = c.owned[k] && density.urban(x, z);
			}
		}
		Pt hub = program.hub(c);

		// 4. mask: urban where reachable from the hub, bridging water runs up to 40 blocks
		boolean[] reached = hub == null ? new boolean[nx * nz] : reach(c, hub);
		int[] floor = new int[nx * nz], water = new int[nx * nz], biome = new int[nx * nz], dens = new int[nx * nz], quarter = new int[nx * nz];
		List<String> palette = new ArrayList<>();
		for (int j = 0; j < nz; j++) {
			for (int i = 0; i < nx; i++) {
				int x = x0 + i * step, z = z0 + j * step, k = j * nx + i;
				floor[k] = probe.floorY(x, z);
				water[k] = probe.waterDepth(x, z);
				String b = probe.biome(x, z);
				int bi = palette.indexOf(b);
				if (bi < 0) { palette.add(b); bi = palette.size() - 1; }
				biome[k] = bi;
				quarter[k] = -1;
				if (!c.owned[k]) continue;
				if (probe.water(x, z)) c.mask[k] = CoarseRaster.WATER;
				else if (reached[k]) {
					int d = density.at(x, z);
					QuarterKind q = quarters.at(x, z, d);
					quarter[k] = q.ordinal();
					if (q == QuarterKind.PARK) c.mask[k] = CoarseRaster.OPEN;
					else { c.mask[k] = CoarseRaster.URBAN; dens[k] = d; }
				} else if (!c.candidate[k] && density.inside(x, z) && !terrain.water(x, z)) c.mask[k] = CoarseRaster.STEEP;
			}
		}

		// 5. roads and gates: cell gates first, then exits
		List<Pt> targets = new ArrayList<>();
		for (Gate g : cellGates) targets.add(new Pt(g.x(), g.z()));
		List<Pt> exits = program.exits(c, hub);
		targets.addAll(exits);
		List<TierProgram.Route> routes = hub == null ? List.of() : program.mainRoads(c, hub, targets);
		List<Road> roads = new ArrayList<>();
		List<Gate> gates = new ArrayList<>();
		int n = 0;
		for (int t = 0; t < targets.size(); t++) {
			TierProgram.Route r = t < routes.size() ? routes.get(t) : null;
			String roadId = null;
			if (r != null && r.points().size() >= 2) {
				roadId = Ids.road(cell.cx(), cell.cz(), n++);
				roads.add(new Road(roadId, program.mainKind(site.tier()), program.mainWidth(site.tier()), r.points(), r.bridges()));
			}
			if (t < cellGates.size()) {
				Gate g = cellGates.get(t);
				gates.add(new Gate(g.id(), g.kind(), g.x(), g.z(), g.cells(), roadId));
			} else if (roadId != null) {
				Pt e = targets.get(t);
				gates.add(new Gate("g?", GateKind.EXIT, e.x(), e.z(), List.of(), roadId));
			}
		}
		roads.addAll(arterialParts(c));

		// 6. landmarks, districts
		List<Landmark> landmarks = hub == null || !c.holdsCentre() ? List.of() : program.landmarks(c, hub);
		List<Wall> walls = hub == null ? List.of() : program.walls(c, hub);
		List<DistrictSummary> districts = districts(c, hub);

		CoarseRaster raster = new CoarseRaster(x0, z0, step, nx, nz, floor, water, biome, palette, c.mask, dens, quarter);
		return new CellSkeleton(cell, new CellOutline(cell.cx(), cell.cz(), new Pt(field.siteX(cell.cx(), cell.cz()), field.siteZ(cell.cx(), cell.cz())), poly),
				raster, hub, roads, gates, landmarks, districts, walls);
	}

	private static List<Cell> sorted(List<Cell> cells) {
		List<Cell> out = new ArrayList<>(cells);
		out.sort(CellGeometry::compare);
		return out;
	}

	private static int minOf(List<Pt> p, boolean x) {
		int m = Integer.MAX_VALUE;
		for (Pt q : p) m = Math.min(m, x ? q.x() : q.z());
		return m;
	}

	private static int maxOf(List<Pt> p, boolean x) {
		int m = Integer.MIN_VALUE;
		for (Pt q : p) m = Math.max(m, x ? q.x() : q.z());
		return m;
	}

	/** The gate column between two cells, or null when they share no edge or every try lands on water or a peak (slopes are the road's business). */
	static Pt gateNode(long seed, RegionField field, CoarseTerrain terrain, Cell a, Cell b) {
		CellGeometry.Edge e = CellGeometry.sharedEdge(field, a, b);
		if (e == null || e.length() < MIN_GATE_EDGE) return null;
		Cell lo = CellGeometry.compare(a, b) <= 0 ? a : b, hi = lo == a ? b : a;
		for (int k = 0; k < GATE_TRIES; k++) {
			double u = Tiers.unit(Hash64.hash(seed, "cell-gate", lo.cx(), lo.cz(), hi.cx(), hi.cz(), k));
			double t = 0.25 + 0.5 * u;
			int x = terrain.snap((int) StrictMath.round(e.x(t))), z = terrain.snap((int) StrictMath.round(e.z(t)));
			if (!terrain.water(x, z) && !terrain.peak(x, z)) return new Pt(x, z);
		}
		return null;
	}

	/** Lattice columns the cell owns that connect to the hub over urban columns, bridging water runs of at most 40 blocks. */
	private static boolean[] reach(SkeletonContext c, Pt hub) {
		int maxRun = Arterials.MAX_BRIDGE / c.step, runs = maxRun + 1;
		boolean[] seen = new boolean[c.nx * c.nz * runs];
		boolean[] reached = new boolean[c.nx * c.nz];
		int[] queue = new int[seen.length];
		int head = 0, tail = 0;
		int hi = (hub.x() - c.x0) / c.step, hj = (hub.z() - c.z0) / c.step;
		int start = (hj * c.nx + hi) * runs;
		seen[start] = true;
		queue[tail++] = start;
		reached[hj * c.nx + hi] = true;
		while (head < tail) {
			int s = queue[head++], run = s % runs, cell = s / runs, i = cell % c.nx, j = cell / c.nx;
			for (int dj = -1; dj <= 1; dj++) {
				for (int di = -1; di <= 1; di++) {
					if (di == 0 && dj == 0) continue;
					int ni = i + di, nj = j + dj;
					if (ni < 0 || nj < 0 || ni >= c.nx || nj >= c.nz) continue;
					int k = nj * c.nx + ni;
					if (!c.owned[k]) continue;
					int x = c.x0 + ni * c.step, z = c.z0 + nj * c.step;
					int nrun;
					if (c.candidate[k]) nrun = 0;
					else if (c.terrain.water(x, z) && run + 1 <= maxRun) nrun = run + 1;
					else continue;
					int ns = k * runs + nrun;
					if (seen[ns]) continue;
					seen[ns] = true;
					queue[tail++] = ns;
					if (nrun == 0) reached[k] = true;
				}
			}
		}
		return reached;
	}

	/** The parts of this settlement's arterial segments that the cell owns, as roads with the segment's id. */
	private static List<Road> arterialParts(SkeletonContext c) {
		List<Road> out = new ArrayList<>();
		if (!c.site.tier().atLeast(Tier.CITY)) return out;
		Arterials art = c.density.arterials();
		int x1 = c.x0 + (c.nx - 1) * c.step, z1 = c.z0 + (c.nz - 1) * c.step;
		for (DistrictGrid.Segment s : art.segmentsIn(c.x0, c.z0, x1, z1)) {
			if (!art.is(s)) continue;
			boolean alongZ = s.axis() == DistrictGrid.AXIS_X;
			int from = Integer.MAX_VALUE, to = Integer.MIN_VALUE;
			for (int q = s.from(); q <= s.to(); q++) {
				if (CellGeometry.owns(c.field, c.cell, alongZ ? s.fixed() : q, alongZ ? q : s.fixed())) {
					from = Math.min(from, q);
					to = Math.max(to, q);
				}
			}
			if (from > to) continue;
			List<Pt> pts = art.points(s, from, to);
			if (pts.size() < 2) continue;
			List<Boolean> wet = new ArrayList<>();
			for (Pt p : pts) wet.add(c.terrain.water(p.x(), p.z()));
			out.add(new Road(Ids.arterial(s.axis(), s.line(), s.from()), RoadKind.ARTERIAL, art.width(), pts, DefaultProgram.bridgeRanges(wet)));
		}
		return out;
	}

	/** Tiles whose centre this cell owns (plus the hub tile) that hold an urban column. */
	private static List<DistrictSummary> districts(SkeletonContext c, Pt hub) {
		TreeMap<String, DistrictSummary> out = new TreeMap<>();
		int reach = c.density.maxRadius() + MARGIN + 2 * 192;
		int minX = Math.max(c.x0, c.site.centre().x() - reach), maxX = Math.min(c.x0 + (c.nx - 1) * c.step, c.site.centre().x() + reach);
		int minZ = Math.max(c.z0, c.site.centre().z() - reach), maxZ = Math.min(c.z0 + (c.nz - 1) * c.step, c.site.centre().z() + reach);
		if (minX > maxX || minZ > maxZ) return List.of();
		DistrictGrid.TileId a = c.grid.tileOf(minX - 192, minZ - 192), b = c.grid.tileOf(maxX + 192, maxZ + 192);
		DistrictGrid.TileId hubTile = hub == null ? null : c.grid.tileOf(hub.x(), hub.z());
		for (int iz = a.iz(); iz <= b.iz(); iz++) {
			for (int ix = a.ix(); ix <= b.ix(); ix++) {
				DistrictGrid.Tile t = c.grid.tile(ix, iz);
				boolean isHub = hubTile != null && hubTile.ix() == ix && hubTile.iz() == iz;
				if (!isHub && !CellGeometry.owns(c.field, c.cell, (t.minX() + t.maxX()) / 2, (t.minZ() + t.maxZ()) / 2)) continue;
				DistrictSummary d = summary(c, t);
				if (d != null) out.put(d.id(), d);
			}
		}
		return new ArrayList<>(out.values());
	}

	/** Quarter (mode), density (max) over the tile's urban lattice columns; null when it holds none. */
	static DistrictSummary summary(SkeletonContext c, DistrictGrid.Tile t) {
		int[] kinds = new int[QuarterKind.values().length];
		int max = 0, count = 0;
		for (int z = Math.floorDiv(t.minZ() + c.step - 1, c.step) * c.step; z < t.maxZ(); z += c.step) {
			for (int x = Math.floorDiv(t.minX() + c.step - 1, c.step) * c.step; x < t.maxX(); x += c.step) {
				if (!c.density.urban(x, z)) continue;
				int d = c.density.at(x, z);
				count++;
				max = Math.max(max, d);
				kinds[c.quarters.at(x, z, d).ordinal()]++;
			}
		}
		if (count == 0) return null;
		int q = 0;
		for (int k = 1; k < kinds.length; k++) if (kinds[k] > kinds[q]) q = k;
		return new DistrictSummary(Ids.district(t.ix(), t.iz()), t.ix(), t.iz(), t.minX(), t.minZ(), t.widthX(), t.widthZ(), t.minX() / 16, t.minZ() / 16,
				q, max, List.of(), 0, null);
	}

	// ---- assembling a settlement

	/** Builds every member cell and merges them. */
	public static SettlementPlan plan(long seed, RegionView view, SettlementSite site, CoarseProbe probe, int genVersion) {
		return plan(seed, view, site, probe, genVersion, TierPrograms.of(site.tier()));
	}

	public static SettlementPlan plan(long seed, RegionView view, SettlementSite site, CoarseProbe probe, int genVersion, TierProgram program) {
		DistrictGrid grid = new DistrictGrid(seed);
		List<CellSkeleton> skeletons = new ArrayList<>();
		for (Cell cell : sorted(site.cells())) skeletons.add(build(seed, view, cell, probe, grid, program));
		return assemble(seed, view, site, probe, genVersion, skeletons);
	}

	/** Merges the cell skeletons of one settlement (any order) into its plan; skeleton rules are checked into {@code violations}. */
	public static SettlementPlan assemble(long seed, RegionView view, SettlementSite site, CoarseProbe probe, int genVersion, List<CellSkeleton> cells) {
		RegionField field = view.field();
		List<CellSkeleton> sk = new ArrayList<>(cells);
		sk.sort((a, b) -> CellGeometry.compare(a.cell(), b.cell()));
		int step = probe.stride();

		// raster: union window; each column from the cell that owns it
		int minI = Integer.MAX_VALUE, minJ = Integer.MAX_VALUE, maxI = Integer.MIN_VALUE, maxJ = Integer.MIN_VALUE;
		for (CellSkeleton s : sk) {
			CoarseRaster r = s.coarse();
			minI = Math.min(minI, Math.floorDiv(r.x0(), step));
			minJ = Math.min(minJ, Math.floorDiv(r.z0(), step));
			maxI = Math.max(maxI, Math.floorDiv(r.x0(), step) + r.nx() - 1);
			maxJ = Math.max(maxJ, Math.floorDiv(r.z0(), step) + r.nz() - 1);
		}
		int x0 = minI * step, z0 = minJ * step, nx = maxI - minI + 1, nz = maxJ - minJ + 1;
		int[] floor = new int[nx * nz], water = new int[nx * nz], biome = new int[nx * nz], mask = new int[nx * nz], dens = new int[nx * nz], quarter = new int[nx * nz];
		List<String> palette = new ArrayList<>();
		TreeMap<Cell, CellSkeleton> byCell = new TreeMap<>(CellGeometry::compare);
		for (CellSkeleton s : sk) byCell.put(s.cell(), s);
		for (int j = 0; j < nz; j++) {
			for (int i = 0; i < nx; i++) {
				int x = x0 + i * step, z = z0 + j * step, k = j * nx + i;
				CellSkeleton owner = byCell.get(CellGeometry.owner(field, x, z));
				CellSkeleton src = owner != null && covers(owner.coarse(), x, z) ? owner : null;
				if (src == null) for (CellSkeleton s : sk) if (covers(s.coarse(), x, z)) { src = s; break; }
				quarter[k] = -1;
				if (src == null) {
					biome[k] = palette(palette, "minecraft:the_void");
					continue;
				}
				CoarseRaster r = src.coarse();
				int si = (x - r.x0()) / step, sj = (z - r.z0()) / step, sk0 = r.index(si, sj);
				floor[k] = r.floorY()[sk0];
				water[k] = r.water()[sk0];
				biome[k] = palette(palette, r.biomes().get(r.biome()[sk0]));
				if (src == owner) {
					mask[k] = r.mask()[sk0];
					dens[k] = r.density()[sk0];
					quarter[k] = r.quarter()[sk0];
				}
			}
		}
		CoarseRaster raster = new CoarseRaster(x0, z0, step, nx, nz, floor, water, biome, palette, mask, dens, quarter);

		List<Quarter> quarters = new ArrayList<>();
		for (QuarterKind q : QuarterKind.values()) quarters.add(new Quarter(q.ordinal(), q));
		List<CellOutline> outlines = new ArrayList<>();
		TreeMap<String, DistrictSummary> districts = new TreeMap<>();
		for (CellSkeleton s : sk) {
			outlines.add(s.outline());
			for (DistrictSummary d : s.districts()) districts.putIfAbsent(d.id(), d);
		}
		List<DistrictSummary> dl = new ArrayList<>(districts.values());
		dl.sort(Comparator.comparingInt(DistrictSummary::iz).thenComparingInt(DistrictSummary::ix));

		// roads: each cell's own, then arterial parts joined per segment id
		List<Road> roads = new ArrayList<>();
		TreeMap<String, List<Road>> parts = new TreeMap<>();
		for (CellSkeleton s : sk) {
			for (Road r : s.roads()) {
				if (r.kind() == RoadKind.ARTERIAL) parts.computeIfAbsent(r.id(), x -> new ArrayList<>()).add(r);
				else roads.add(r);
			}
		}
		for (var e : parts.entrySet()) roads.add(joinArterial(e.getKey(), e.getValue()));

		// gates: cell gates are listed by both cells; number by position
		TreeMap<String, Gate> gates = new TreeMap<>();
		for (CellSkeleton s : sk) {
			for (Gate g : s.gates()) {
				String key = String.format("%010d:%010d:%d", g.x() + 1_000_000_000L, g.z() + 1_000_000_000L, g.kind().ordinal());
				Gate prev = gates.get(key);
				if (prev == null) gates.put(key, g);
				else if (prev.road() == null && g.road() != null) gates.put(key, g);
			}
		}
		List<Gate> gl = new ArrayList<>();
		for (Gate g : gates.values()) gl.add(new Gate("g" + gl.size(), g.kind(), g.x(), g.z(), g.cells(), g.road()));

		List<Landmark> marks = new ArrayList<>();
		for (CellSkeleton s : sk) marks.addAll(s.landmarks());
		marks.sort(Comparator.comparing(Landmark::kind).thenComparingInt(Landmark::x).thenComparingInt(Landmark::z));
		List<Landmark> ml = new ArrayList<>();
		for (Landmark m : marks) ml.add(new Landmark("l" + ml.size(), m.kind(), m.x(), m.z(), m.w(), m.d(), m.district()));
		List<Wall> walls = new ArrayList<>();
		for (CellSkeleton s : sk) walls.addAll(s.walls());

		List<Violation> violations = new ArrayList<>();
		violations.addAll(SettlementChecks.skeletonInCell(sk, field));
		violations.addAll(SettlementChecks.cellGateAgree(sk));
		return new SettlementPlan(genVersion, seed, site, new Rect(x0, z0, nx * step, nz * step), raster, quarters, outlines, dl, roads, gl, ml, walls, violations);
	}

	private static boolean covers(CoarseRaster r, int x, int z) {
		int i = Math.floorDiv(x - r.x0(), r.step()), j = Math.floorDiv(z - r.z0(), r.step());
		return i >= 0 && j >= 0 && i < r.nx() && j < r.nz();
	}

	private static int palette(List<String> palette, String b) {
		int i = palette.indexOf(b);
		if (i >= 0) return i;
		palette.add(b);
		return palette.size() - 1;
	}

	/** Parts of one arterial segment, one per cell, are laid end to end along the segment. */
	private static Road joinArterial(String id, List<Road> parts) {
		boolean alongZ = parts.get(0).points().get(0).x() == parts.get(0).points().get(parts.get(0).points().size() - 1).x();
		List<Road> p = new ArrayList<>(parts);
		p.sort(Comparator.comparingInt(r -> alongZ ? r.points().get(0).z() : r.points().get(0).x()));
		List<Pt> pts = new ArrayList<>();
		List<int[]> bridges = new ArrayList<>();
		for (Road r : p) {
			int off = pts.size();
			pts.addAll(r.points());
			for (int[] b : r.bridges()) {
				int[] nb = {b[0] + off, b[1] + off};
				if (!bridges.isEmpty() && bridges.get(bridges.size() - 1)[1] >= nb[0] - 1) bridges.get(bridges.size() - 1)[1] = Math.max(bridges.get(bridges.size() - 1)[1], nb[1]);
				else bridges.add(nb);
			}
		}
		Road first = p.get(0);
		return new Road(id, first.kind(), first.width(), pts, bridges);
	}
}
