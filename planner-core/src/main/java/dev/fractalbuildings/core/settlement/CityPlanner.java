package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.check.SettlementChecks;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.probe.CoarseProbe;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.style.StyleField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The whole city pipeline (D0): skeleton of every member cell, then for each district the tile layout ({@link Districts}) and
 * the brief step ({@link Briefs}), and on request a district scene ({@link DistrictScenes}). The result is a pure function of the
 * source (seed, site, probes): districts are planned in id order, but each depends only on the seed and its tile, so any order
 * gives the same bytes. The CLI's {@code city} and {@code city-check} and the tests share this class.
 */
public final class CityPlanner {
	private CityPlanner() {}

	/**
	 * @param plan the settlement plan with each planned district's lot count, style weights and detail path
	 * @param districts the filled plan of every district this settlement owns, by id
	 * @param landmarks the landmarks that reserve ground in each planned district (grown into lots or not), by district id
	 * @param notes the district planner's notes, prefixed with the district id
	 * @param hub the hub column, or the site centre if no cell skeleton has one
	 * @param hubPiece columns in the street piece that holds the hub, of {@code streetColumns} in all
	 * @param strays street fragments that reach neither the hub, a skeleton road nor a lot front (counted, not violations)
	 */
	public record City(SettlementPlan plan, SortedMap<String, DistrictPlan> districts, SortedMap<String, List<Landmark>> landmarks, List<String> notes, Pt hub, int strays, int hubPiece, int streetColumns) {}

	/** Plans the skeleton and every district (not scenes). {@code coarse} feeds the skeleton, {@code exact} the layouts and briefs. */
	public static City plan(SkeletonSource src, CoarseProbe coarse, TerrainProbe exact, BiomeProbe biome, int genVersion) {
		SettlementSite site = src.site();
		List<CellSkeleton> sk = new ArrayList<>();
		for (Cell c : site.cells()) sk.add(src.skeleton(c));
		SettlementPlan plan = Skeletons.assemble(src.seed(), src.view(), site, coarse, genVersion, sk);
		StyleField styles = new StyleField(biome, src.seed());
		TreeMap<String, DistrictPlan> districts = new TreeMap<>();
		TreeMap<String, List<Landmark>> landmarks = new TreeMap<>();
		TreeMap<String, Boolean> squareReached = new TreeMap<>();
		List<String> notes = new ArrayList<>();
		List<DistrictSummary> out = new ArrayList<>();
		for (DistrictSummary d : plan.districts()) {
			Optional<Tile> tile = planTile(src, d.ix(), d.iz(), styles, exact);
			if (tile.isEmpty()) { out.add(d); continue; }
			DistrictPlan dp = tile.get().plan();
			districts.put(d.id(), dp);
			landmarks.put(d.id(), tile.get().landmarks());
			squareReached.put(d.id(), tile.get().firstPlazaReached());
			for (String n : tile.get().notes()) notes.add(d.id() + ": " + n);
			out.add(summary(d, dp));
		}
		Pt hub = site.centre();
		Cell centreCell = CellGeometry.owner(src.view().field(), hub.x(), hub.z());
		if (site.cells().contains(centreCell) && RoadNetwork.roadHub(src.skeleton(centreCell)) != null) hub = RoadNetwork.roadHub(src.skeleton(centreCell));
		// The hub square may have moved off the skeleton's hub column (exact terrain did not grade there): the hub is then the square's centre.
		for (DistrictPlan dp : districts.values()) {
			Rect t = dp.district().rect();
			if (hub.x() < t.x() || hub.x() >= t.x() + t.w() || hub.z() < t.z() || hub.z() >= t.z() + t.d() || dp.plazas().isEmpty() || !squareReached.get(dp.district().id())) continue;
			Plaza hp = dp.plazas().get(0);
			hub = new Pt(hp.x() + hp.w() / 2, hp.z() + hp.d() / 2);
			break;
		}
		if (src.network().hasLattice()) {
			List<Road> roads = new ArrayList<>();
			for (Road r : plan.roads()) if (r.kind() == RoadKind.ARTERIAL ? src.network().arterialPresent(r.id()) : src.network().reaches(r)) roads.add(r);
			plan = plan.withRoads(roads);
		}
		plan = plan.withLandmarks(placedLandmarks(plan.landmarks(), districts, landmarks));
		List<Violation> v = new ArrayList<>(plan.violations());
		SettlementChecks.CityRoads cr = SettlementChecks.cityRoads(districts.values(), hub);
		v.addAll(cr.violations());
		v.addAll(SettlementChecks.cityTileBorders(districts.values()));
		return new City(plan.withDistricts(out).withViolations(v), districts, landmarks, notes, hub, cr.strays(), cr.hubPiece(), cr.total());
	}

	/**
	 * One planned tile: the layout of {@link Districts#plan} and the briefs of {@link Briefs#fill}.
	 *
	 * @param plan the filled district plan
	 * @param landmarks the landmarks that still reserve ground in the tile
	 * @param notes the district planner's notes (what was dropped or moved)
	 * @param firstPlazaReached the first plaza (the hub square in the hub tile) is joined to a road that reaches the hub
	 */
	public record Tile(DistrictPlan plan, List<Landmark> landmarks, List<String> notes, boolean firstPlazaReached) {}

	/**
	 * Plans one tile of the settlement {@code src} serves, exactly as {@link #plan} does for each of its districts (the shared code of
	 * the CLI and the world planner): empty when another settlement owns the tile. Uses the source's quarter field for the briefs; the
	 * result depends on (seed, site, probes, tile) alone.
	 */
	public static Optional<Tile> planTile(SkeletonSource src, int ix, int iz, StyleField styles, TerrainProbe exact) {
		Optional<Districts.Detail> det = Districts.plan(src, ix, iz, exact);
		if (det.isEmpty()) return Optional.empty();
		DistrictPlan dp = Briefs.fill(det.get().plan(), src.quarters()::at, styles, exact);
		return Optional.of(new Tile(dp, det.get().landmarks(), det.get().notes(), det.get().firstPlazaReached()));
	}

	/**
	 * The skeleton's landmarks where district detail put them, so the plan view outlines the real square, hall and temple and not
	 * the skeleton's first guess: an open square or green takes its (possibly moved) plaza's rectangle, a hall or temple the rectangle
	 * of the district's lot of that type (the one nearest the skeleton's spot); one that got neither keeps its spot. Landmarks of
	 * districts that were not planned are unchanged.
	 */
	static List<Landmark> placedLandmarks(List<Landmark> sk, SortedMap<String, DistrictPlan> districts, SortedMap<String, List<Landmark>> kept) {
		List<Landmark> out = new ArrayList<>();
		List<Landmark> used = new ArrayList<>(); // matched landmarks (identity of a record: equal fields)
		for (Landmark l : sk) {
			DistrictPlan dp = districts.get(l.district());
			Rect r = null;
			if (dp != null) {
				Landmark pick = null;
				long bd = Long.MAX_VALUE;
				for (Landmark k : kept.getOrDefault(l.district(), List.of())) {
					if (k.kind() != l.kind() || used.contains(k)) continue;
					long dx = k.x() + k.w() / 2 - (l.x() + l.w() / 2), dz = k.z() + k.d() / 2 - (l.z() + l.d() / 2), dd = dx * dx + dz * dz;
					if (dd < bd) { bd = dd; pick = k; }
				}
				if (pick != null) { used.add(pick); r = new Rect(pick.x(), pick.z(), pick.w(), pick.d()); }
				if (r == null && (l.kind() == LandmarkKind.HALL || l.kind() == LandmarkKind.TEMPLE)) {
					String type = l.kind() == LandmarkKind.HALL ? "hall" : "temple";
					long best = Long.MAX_VALUE;
					for (LotBrief lot : dp.lots()) {
						if (!lot.type().equals(type)) continue;
						long dx = lot.x() + lot.w() / 2 - (l.x() + l.w() / 2), dz = lot.z() + lot.d() / 2 - (l.z() + l.d() / 2), dd = dx * dx + dz * dz;
						if (dd < best) { best = dd; r = lot.rect(); }
					}
				}
			}
			if (r == null && dp != null && (l.kind() == LandmarkKind.SQUARE || l.kind() == LandmarkKind.GREEN || l.kind() == LandmarkKind.MARKET || l.kind() == LandmarkKind.PARK)) continue; // its plaza was dropped
			out.add(r == null ? l : new Landmark(l.id(), l.kind(), r.x(), r.z(), r.w(), r.d(), l.district()));
		}
		return out;
	}

	private static DistrictSummary summary(DistrictSummary d, DistrictPlan dp) {
		TreeMap<String, Integer> tally = new TreeMap<>();
		for (LotBrief l : dp.lots()) if (l.builds()) tally.merge(l.style(), 1, Integer::sum);
		List<StyleWeight> sw = new ArrayList<>();
		for (var e : tally.entrySet()) sw.add(new StyleWeight(e.getKey(), e.getValue()));
		return new DistrictSummary(d.id(), d.ix(), d.iz(), d.x(), d.z(), d.w(), d.d(), d.originChunkX(), d.originChunkZ(), d.quarter(), d.density(), sw, dp.lots().size(), "districts/" + d.id() + ".json");
	}

	/**
	 * {@code n} planned districts that have buildable lots, to build scenes for: the one holding the hub first, then one per quarter
	 * kind not yet shown (in a hash order of the seed and id), then the rest in that order. Sorted by id.
	 */
	public static List<String> autoScenes(City city, int n) {
		long seed = city.plan().seed();
		List<DistrictPlan> cand = new ArrayList<>();
		for (DistrictPlan p : city.districts().values()) if (p.lots().stream().anyMatch(LotBrief::builds)) cand.add(p);
		cand.sort(Comparator.comparingLong((DistrictPlan p) -> Hash64.hash(seed, "auto-scene", p.district().ix(), p.district().iz())).thenComparing(p -> p.district().id()));
		List<String> out = new ArrayList<>();
		for (DistrictPlan p : cand) {
			Rect r = p.district().rect();
			if (out.size() < n && city.hub().x() >= r.x() && city.hub().x() < r.x() + r.w() && city.hub().z() >= r.z() && city.hub().z() < r.z() + r.d()) out.add(p.district().id());
		}
		TreeSet<Integer> kinds = new TreeSet<>();
		for (String id : out) kinds.add(city.districts().get(id).district().quarter());
		for (DistrictPlan p : cand) if (out.size() < n && !out.contains(p.district().id()) && kinds.add(p.district().quarter())) out.add(p.district().id());
		for (DistrictPlan p : cand) if (out.size() < n && !out.contains(p.district().id())) out.add(p.district().id());
		out.sort(Comparator.naturalOrder());
		return out;
	}

	/** The scene of one planned district (building invariants and settlement rules run inside). */
	public static DistrictScenes.Built scene(City city, String districtId, TerrainProbe exact, BiomeProbe biome, boolean exterior) {
		DistrictPlan dp = city.districts().get(districtId);
		if (dp == null) throw new IllegalArgumentException("no planned district " + districtId);
		return DistrictScenes.build(dp, city.landmarks().get(districtId), exact, biome, exterior);
	}

	/**
	 * Every settlement violation of the city: the plan's (skeleton rules and the whole-city {@code roads-connected} and
	 * {@code tile-border-agree}, which {@link #plan} computes) and each district's, prefixed with the district id.
	 */
	public static List<Violation> check(City city) {
		List<Violation> out = new ArrayList<>(city.plan().violations());
		for (DistrictPlan p : city.districts().values()) {
			for (Violation v : p.violations()) out.add(new Violation(v.invariant(), v.building(), p.district().id() + ": " + v.message(), v.cells()));
		}
		return out;
	}
}
