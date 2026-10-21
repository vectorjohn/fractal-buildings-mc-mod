package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.Types;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.IrJson;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.style.StyleField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class DistrictScenesTest {
	static final SampleSettlement.Sample SAMPLE = SampleSettlement.sample();
	static final ProbeSpecs.Probes PR = ProbeSpecs.parse(SampleSettlement.SAMPLE_TERRAIN);

	static QuarterKind quarterOf(DistrictPlan d) { return SAMPLE.settlement().quarters().get(d.district().quarter()).kind(); }

	static DistrictPlan filled(DistrictPlan d) {
		return Briefs.fill(d, quarterOf(d), new StyleField(PR.biome(), SampleSettlement.SEED), PR.terrain());
	}

	static DistrictScenes.Built built(DistrictPlan d, boolean exterior) {
		return DistrictScenes.build(filled(d), SAMPLE.settlement().landmarks(), PR.terrain(), PR.biome(), exterior);
	}

	@Test
	void sampleDistrictsBuildWithNoViolations() {
		int buildings = 0;
		for (DistrictPlan d : SAMPLE.districts().values()) {
			DistrictScenes.Built b = built(d, false);
			assertTrue(b.scene().violations().isEmpty(), d.district().id() + " building violations: " + b.scene().violations());
			assertTrue(b.plan().violations().isEmpty(), d.district().id() + " settlement violations: " + b.plan().violations());
			assertTrue(b.fallbacks().isEmpty(), "fallbacks " + b.fallbacks());
			buildings += b.scene().buildings().size();
			assertFalse(b.scene().ground().isEmpty());
			assertTrue(b.scene().terrain().waters() != null);
		}
		assertTrue(buildings > 30, "buildings " + buildings);
	}

	@Test
	void storeysStayWithinTheBriefAndTheType() {
		for (DistrictPlan d : SAMPLE.districts().values()) {
			DistrictPlan f = filled(d);
			DistrictScenes.Built b = DistrictScenes.build(f, List.of(), PR.terrain(), PR.biome(), true);
			Map<String, LotBrief> byId = new TreeMap<>();
			for (LotBrief l : f.lots()) byId.put(l.id(), l);
			for (PlacedBuilding pb : b.scene().buildings()) {
				LotBrief l = byId.get(pb.lots().get(0));
				TypeLimits lim = Types.get(l.type()).limits();
				int s = pb.shell().spec().storeys();
				assertTrue(s >= Math.max(l.minStoreys(), lim.minStoreys()) && s <= Math.min(l.maxStoreys(), lim.maxStoreys()),
						l.id() + " " + l.type() + " storeys " + s + " outside " + l.minStoreys() + ".." + l.maxStoreys());
			}
		}
	}

	@Test
	void stylesFollowTheBiomeSide() {
		int west = 0, westPlains = 0, east = 0, eastDesert = 0;
		String plains = StyleField.styleOf("minecraft:plains"), desert = StyleField.styleOf("minecraft:desert");
		for (DistrictPlan d : SAMPLE.districts().values()) {
			for (LotBrief l : filled(d).lots()) {
				if (l.x() + l.w() / 2 < 160) { west++; if (l.style().equals(plains)) westPlains++; }
				else { east++; if (l.style().equals(desert)) eastDesert++; }
			}
		}
		System.out.println("style split: plains " + westPlains + "/" + west + ", desert " + eastDesert + "/" + east);
		assertTrue(west > 10 && east > 10);
		assertTrue(westPlains * 100 > west * 80, westPlains + "/" + west + " plains-style lots west of the split");
		assertTrue(eastDesert * 100 > east * 80, eastDesert + "/" + east + " desert-style lots east of the split");
	}

	@Test
	void sameFillTwiceAndAnyOrderGiveIdenticalScenes() {
		List<DistrictPlan> ds = new ArrayList<>(SAMPLE.districts().values());
		Map<String, String> first = new TreeMap<>();
		for (DistrictPlan d : ds) first.put(d.district().id(), IrJson.write(built(d, false).scene()));
		Collections.reverse(ds);
		for (DistrictPlan d : ds) {
			assertEquals(first.get(d.district().id()), IrJson.write(built(d, false).scene()), d.district().id());
			assertEquals(DistrictJson.write(filled(d)), DistrictJson.write(filled(d)));
		}
	}

	@Test
	void frontYAndStreetMaskComeFromTheStreets() {
		DistrictPlan d = filled(SAMPLE.districts().get("d0_0"));
		int withFront = 0;
		for (LotBrief l : d.lots()) {
			assertEquals(1, l.streets() & 1, "front bit " + l.id());
			if (l.frontY() == Brief.NO_FRONT_Y) continue;
			withFront++;
			// the sample's lots front the y = 140 main road (surface y = ground at the road) and the north-south street
			Street main = d.streets().get(0);
			int near = Briefs.nearest(main, l.x() + l.w() / 2, 140)[1];
			if (Math.abs(l.z() + (l.front() == Facing.SOUTH ? l.d() : 0) - 140) < 8) assertEquals(near, l.frontY(), l.id());
		}
		assertTrue(withFront >= d.lots().size() / 2, "lots with a frontY: " + withFront + "/" + d.lots().size());
	}

	@Test
	void exteriorDropsInteriorsButKeepsTheShell() {
		DistrictPlan d = SAMPLE.districts().get("d0_0");
		SceneDoc full = built(d, false).scene(), ext = built(d, true).scene();
		assertEquals(full.buildings().size(), ext.buildings().size());
		assertTrue(ext.violations().isEmpty(), "" + ext.violations());
		assertTrue(dev.fractalbuildings.core.ir.IrValidator.validate(ext).isEmpty(), "stripped scene must stay valid");
		for (PlacedBuilding b : ext.buildings()) {
			assertTrue(b.shell().rooms().isEmpty() && b.shell().hooks().isEmpty());
			assertTrue(b.shell().boxes().stream().noneMatch(x -> x.layer() == Layer.INTERIOR));
		}
		assertTrue(full.buildings().stream().anyMatch(b -> !b.shell().rooms().isEmpty()));
		assertTrue(IrJson.write(ext).length() < IrJson.write(full).length());
	}

	@Test
	void groundBoxesCoverRoadsAndPlazas() {
		SceneDoc s = built(SAMPLE.districts().get("d1_0"), true).scene(); // the market square tile
		assertTrue(s.ground().stream().anyMatch(b -> b.role() == Role.PAVING));
		assertTrue(s.ground().stream().anyMatch(b -> b.role() == Role.ROAD));
	}

	/** A hillside the lots' types cannot all take: the fill re-picks or empties, the scene never throws. */
	@Test
	void steepLotsAreRepickedOrEmptiedAndNeverThrow() {
		ProbeSpecs.Probes hill = ProbeSpecs.parse("plane:64:1:0:1"); // rises 1 per block along x
		Street road = new Street("s0", RoadKind.STREET, 5, List.of(new Pt(0, 20), new Pt(127, 20)), new int[] {64, 120});
		List<LotBrief> lots = new ArrayList<>();
		String[] types = {"block", "house", "shop", "workshop", "tower", "highrise"};
		for (int i = 0; i < types.length; i++) {
			lots.add(new LotBrief("l" + i, 8 + i * 20, 24, 18, 18, Facing.NORTH, types[i], "plain", 1, 3, 1, 0, Brief.NO_FRONT_Y, 60, LotBrief.BUILD));
		}
		DistrictPlan plan = new DistrictPlan(2, 7, new DistrictInfo("d0_0", 0, 0, 0, 0, 128, 128, 0, 60), List.of(road), List.of(), lots, null, List.of());
		DistrictPlan f = Briefs.fill(plan, QuarterKind.RESIDENTIAL, new StyleField(hill.biome(), 7), hill.terrain());
		int empty = 0;
		for (LotBrief l : f.lots()) {
			if (!l.builds()) { empty++; assertTrue(l.status().startsWith("empty:"), l.status()); }
		}
		assertTrue(empty > 0, "a 1:1 slope must empty some lots: " + f.lots().stream().map(l -> l.type() + "/" + l.status()).toList());
		DistrictScenes.Built b = DistrictScenes.build(f, List.of(), hill.terrain(), hill.biome(), true);
		assertTrue(b.fallbacks().isEmpty(), "the fill screens what the spec policy rejects: " + b.fallbacks());
		// the unfilled plan has lots the policy rejects: the scene falls back instead of throwing
		DistrictScenes.Built raw = DistrictScenes.build(plan, List.of(), hill.terrain(), hill.biome(), true);
		assertFalse(raw.fallbacks().isEmpty());
		for (LotBrief l : raw.plan().lots()) assertTrue(l.builds() ? l.type().equals("house") || !raw.fallbacks().contains(l.id()) : l.status().equals("empty:gen-failed"));
	}

	/** Writes the built sample district scenes for the viewer: viewer/out/district/<id>.json (full) and <id>-ext.json (exterior). */
	@Test
	void writeScenesForTheViewer() throws java.io.IOException {
		java.nio.file.Path dir = java.nio.file.Path.of("../viewer/out/district");
		java.nio.file.Files.createDirectories(dir);
		for (DistrictPlan d : SAMPLE.districts().values()) {
			java.nio.file.Files.writeString(dir.resolve(d.district().id() + ".json"), IrJson.write(built(d, false).scene()));
			java.nio.file.Files.writeString(dir.resolve(d.district().id() + "-ext.json"), IrJson.write(built(d, true).scene()));
		}
	}

	/** Top-down picture of the four built sample districts (build/district-scenes/sample-plan.png): terrain shade, water, road and paving, lots (red = built, grey outline = empty). */
	@Test
	void writeTopDownPicture() throws java.io.IOException {
		int scale = 3, w = 272, h = 288;
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w * scale, h * scale, java.awt.image.BufferedImage.TYPE_INT_RGB);
		for (DistrictPlan d : SAMPLE.districts().values()) {
			DistrictScenes.Built b = built(d, true);
			dev.fractalbuildings.core.ir.TerrainPatch t = b.scene().terrain();
			Rect r = d.district().rect();
			int[][] col = new int[r.w()][r.d()];
			for (int z = 0; z < r.d(); z++) for (int x = 0; x < r.w(); x++) {
				int wx = r.x() + x, wz = r.z() + z, g = 40 + Math.min(120, Math.max(0, t.floorY(wx, wz) - 50) * 6);
				col[x][z] = t.waterDepth(wx, wz) > 0 ? 0x2060c0 : (g << 8) | (g / 3 << 16);
			}
			for (dev.fractalbuildings.core.ir.Box gb : b.scene().ground()) {
				int c = switch (gb.role()) { case ROAD -> 0xb08050; case PAVING -> 0xc8c8c8; default -> -1; };
				if (c < 0) continue;
				var bx = gb.box();
				for (int z = bx.z(); z < bx.maxZ(); z++) for (int x = bx.x(); x < bx.maxX(); x++)
					if (x >= r.x() && x < r.x() + r.w() && z >= r.z() && z < r.z() + r.d()) col[x - r.x()][z - r.z()] = c;
			}
			for (LotBrief l : b.plan().lots()) {
				for (int z = l.z(); z < l.z() + l.d(); z++) for (int x = l.x(); x < l.x() + l.w(); x++) {
					boolean edge = x == l.x() || z == l.z() || x == l.x() + l.w() - 1 || z == l.z() + l.d() - 1;
					if (edge || l.builds()) col[x - r.x()][z - r.z()] = edge ? (l.builds() ? 0xd03030 : 0x808080) : 0x802020;
				}
			}
			for (int z = 0; z < r.d(); z++) for (int x = 0; x < r.w(); x++)
				for (int sz = 0; sz < scale; sz++) for (int sx = 0; sx < scale; sx++) img.setRGB((r.x() + x) * scale + sx, (r.z() + z) * scale + sz, col[x][z]);
		}
		java.nio.file.Path out = java.nio.file.Path.of("build/district-scenes/sample-plan.png");
		java.nio.file.Files.createDirectories(out.getParent());
		javax.imageio.ImageIO.write(img, "png", out.toFile());
	}
}
