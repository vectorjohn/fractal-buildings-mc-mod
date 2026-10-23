package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.probe.BiomeProbe;
import dev.fractalbuildings.core.raster.CellSink;
import dev.fractalbuildings.core.raster.PieceRaster;
import dev.fractalbuildings.core.raster.StyleAt;
import java.util.function.Supplier;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.DistrictScenes;
import dev.fractalbuildings.core.settlement.LotBrief;
import dev.fractalbuildings.core.settlement.Rect;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/** The pieces of a planned tile, from its plan and the exact probes: pure functions, shared by {@link WorldPlanner} and the fixture planner. */
final class Pieces {
	private Pieces() {}

	static List<BuildingPiece> buildings(TilePlan tp, TerrainProbe exact, BiomeProbe biome) {
		DistrictScenes.Lots gen = DistrictScenes.generateLots(tp.plan(), exact, biome);
		List<BuildingPiece> out = new ArrayList<>();
		for (DistrictScenes.Generated g : gen.generated()) out.add(piece(tp.info().ix(), tp.info().iz(), g));
		return out;
	}

	/** The piece of one generated lot. */
	static BuildingPiece piece(int ix, int iz, DistrictScenes.Generated g) {
		return new BuildingPiece(PieceRef.building(ix, iz, g.index()), g.brief(), g.lot(), box(g.brief(), g.building()), g.building());
	}

	/** Cells of one piece of a tile whose buildings are {@code buildings} (lot order) and whose ground comes from {@code ground}. */
	static void raster(PieceRef ref, List<BuildingPiece> buildings, Supplier<List<GroundPiece>> ground, Box3 clip, CellSink out) {
		List<PlacedBuilding> placed = new ArrayList<>();
		for (BuildingPiece b : buildings) placed.add(b.building());
		if (ref.kind() == PieceRef.Kind.BUILDING) {
			for (int i = 0; i < buildings.size(); i++) {
				if (buildings.get(i).ref().equals(ref)) { PieceRaster.building(placed, i, clip, out); return; }
			}
		} else {
			for (GroundPiece g : ground.get()) {
				if (g.ref().equals(ref)) { PieceRaster.ground(g.boxes(), placed, StyleAt.nearest(placed, StyleLibrary.FALLBACK), clip, out); return; }
			}
		}
		throw new IllegalArgumentException("no piece " + ref);
	}

	static Box3 box(LotBrief l, PlacedBuilding b) {
		Placement pl = b.placement();
		Box3 foot = pl.toWorld(b.shell().bounds());
		int x0 = Math.min(l.x(), foot.x()), z0 = Math.min(l.z(), foot.z());
		int x1 = Math.max(l.x() + l.w(), foot.maxX()), z1 = Math.max(l.z() + l.d(), foot.maxZ());
		int y0 = Math.min(b.oy(), foot.y()), y1 = foot.maxY();
		for (Box s : b.siteBoxes()) y0 = Math.min(y0, pl.toWorld(s.box()).y());
		return new Box3(x0, y0, z0, x1 - x0, y1 - y0, z1 - z0);
	}

	static List<GroundPiece> ground(TilePlan tp, TerrainProbe exact) {
		Rect t = tp.plan().district().rect();
		int cx0 = Math.floorDiv(t.x(), 16), cz0 = Math.floorDiv(t.z(), 16), width = Math.floorDiv(t.x() + t.w() - 1, 16) - cx0 + 1;
		TreeMap<Integer, List<Box>> byChunk = new TreeMap<>();
		for (Box b : DistrictScenes.ground(tp.plan(), exact).boxes()) {
			Box3 c = clip(b.box(), t.x(), t.z(), t.x() + t.w(), t.z() + t.d());
			if (c == null) continue;
			for (int cz = Math.floorDiv(c.z(), 16); cz <= Math.floorDiv(c.maxZ() - 1, 16); cz++) {
				for (int cx = Math.floorDiv(c.x(), 16); cx <= Math.floorDiv(c.maxX() - 1, 16); cx++) {
					Box3 k = clip(c, cx * 16, cz * 16, cx * 16 + 16, cz * 16 + 16);
					if (k != null) byChunk.computeIfAbsent((cz - cz0) * width + (cx - cx0), i -> new ArrayList<>()).add(new Box(k, b.role(), b.layer(), b.storey()));
				}
			}
		}
		List<GroundPiece> out = new ArrayList<>();
		for (var e : byChunk.entrySet()) {
			int cx = cx0 + e.getKey() % width, cz = cz0 + e.getKey() / width;
			int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
			for (Box b : e.getValue()) {
				Box3 k = b.box();
				x0 = Math.min(x0, k.x()); y0 = Math.min(y0, k.y()); z0 = Math.min(z0, k.z());
				x1 = Math.max(x1, k.maxX()); y1 = Math.max(y1, k.maxY()); z1 = Math.max(z1, k.maxZ());
			}
			out.add(new GroundPiece(PieceRef.ground(tp.info().ix(), tp.info().iz(), e.getKey()), cx, cz, new Box3(x0, y0, z0, x1 - x0, y1 - y0, z1 - z0), e.getValue()));
		}
		return out;
	}

	/** The part of {@code b} inside the column rectangle [x0, x1) x [z0, z1), or null. */
	static Box3 clip(Box3 b, int x0, int z0, int x1, int z1) {
		int ax = Math.max(b.x(), x0), az = Math.max(b.z(), z0), bx = Math.min(b.maxX(), x1), bz = Math.min(b.maxZ(), z1);
		return ax >= bx || az >= bz ? null : new Box3(ax, b.y(), az, bx - ax, b.sy(), bz - az);
	}
}
