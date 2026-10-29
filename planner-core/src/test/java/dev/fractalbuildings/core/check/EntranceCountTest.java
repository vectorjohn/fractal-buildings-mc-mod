package dev.fractalbuildings.core.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * DS-35 (DS35DOORS): the {@code entrance-count} invariant. Generated buildings on flat ground keep it (two or more doors for a
 * building of 2+ storeys or 80+ cells, none into a bedroom, a high-rise on two faces with a 2-wide main door, a workshop wing and
 * lean-to each with a door), and it fails for the same buildings with their secondary doors walled up (the old output).
 */
class EntranceCountTest {
	private static final String[] TYPES = {"house", "shop", "workshop", "temple", "hall", "highrise", "block"};
	private static final String[] STYLES = {"medieval", "plain"};

	private static GenRequest request(String type, long seed, int w, int d, String style, Facing front) {
		ProbeSpecs.Probes p = ProbeSpecs.parse("flat:64");
		return new GenRequest(seed, new Lot("lot0", 0, 0, w, d, front), type, style, p.terrain(), p.biome());
	}

	private static List<SceneDoc> scenes(String type, int n) {
		List<SceneDoc> out = new ArrayList<>();
		dev.fractalbuildings.core.gen.v1.BuildingType t = dev.fractalbuildings.core.gen.v1.Types.get(type);
		for (int i = 0; i < n; i++) {
			int[] lot = t.limits().testLots().get(i % t.limits().testLots().size());
			Facing front = Facing.values()[i % 4];
			lot = t.limits().oriented(lot, front);
			try {
				out.add(new GeneratorV1().generate(request(type, 100 + i, lot[0], lot[1], STYLES[i % 2], front)));
			} catch (IllegalArgumentException e) { /* lot too small for the type */ }
		}
		return out;
	}

	@Test
	void generatedBuildingsOnFlatGroundKeepTheRule() {
		int checked = 0, big = 0;
		for (String type : TYPES)
			for (SceneDoc s : scenes(type, 24)) {
				EntranceCount.Result r = EntranceCount.measure(s.buildings().get(0));
				assertEquals(List.of(), r.problems(), type + " seed " + s.seed());
				checked++;
				if (r.big() && r.applies()) big++;
			}
		assertTrue(checked > 100 && big > 80, checked + " scenes, " + big + " big");
	}

	@Test
	void housesAndWorkshopsHaveBackDoorsIntoDayRooms() {
		int houses = 0;
		for (SceneDoc s : scenes("house", 24)) {
			PlacedBuilding b = s.buildings().get(0);
			EntranceCount.Result r = EntranceCount.measure(b);
			if (!r.big()) continue;
			houses++;
			assertTrue(r.doors() >= 2, "house with " + r.doors() + " doors, seed " + s.seed());
			assertEquals(List.of(), r.bedroomDoors());
		}
		assertTrue(houses > 5, houses + " big houses");
		for (SceneDoc s : scenes("workshop", 24)) {
			EntranceCount.Result r = EntranceCount.measure(s.buildings().get(0));
			assertEquals(List.of(), r.bareVolumes(), "workshop seed " + s.seed());
		}
	}

	@Test
	void highRisesHaveTwoFacesAndAWideMainDoor() {
		int n = 0;
		for (SceneDoc s : scenes("highrise", 12)) {
			EntranceCount.Result r = EntranceCount.measure(s.buildings().get(0));
			assertTrue(r.faces() >= 2 && (r.widest() >= 2 || !r.wideRequired()), "high-rise seed " + s.seed() + ": " + r);
			n++;
		}
		assertTrue(n >= 6);
	}

	/** The same building with every exterior ground door but its first walled up (what the generator gave before DS35DOORS). */
	private static PlacedBuilding oneDoor(PlacedBuilding b) {
		BuildingIr sh = b.shell();
		List<Opening> openings = new ArrayList<>(sh.openings());
		List<Box> boxes = new ArrayList<>(sh.boxes());
		boolean first = true;
		for (Opening o : sh.openings()) {
			if (o.kind() != OpeningKind.DOOR || o.to().equals(Opening.OUTSIDE) == o.from().equals(Opening.OUTSIDE) || o.box().y() > 3) continue;
			if (first) { first = false; continue; }
			openings.remove(o);
			boxes.add(new Box(o.box(), Role.WALL, dev.fractalbuildings.core.ir.Layer.SHELL, 0));
		}
		BuildingIr sealed = new BuildingIr(sh.spec(), sh.bounds(), sh.storeys(), sh.volumes(), sh.rooms(), openings, sh.roofs(), boxes, sh.hooks());
		return new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), sealed, b.siteBoxes());
	}

	/** Control: the check fails on the old output (one door per building) for every type it covers. */
	@Test
	void failsWhenSecondaryDoorsAreWalledUp() {
		for (String type : TYPES) {
			int failed = 0, big = 0;
			for (SceneDoc s : scenes(type, 24)) {
				PlacedBuilding b = s.buildings().get(0);
				EntranceCount.Result now = EntranceCount.measure(b);
				if (!now.big() || !now.applies()) continue;
				big++;
				if (type.equals("block") && now.doors() < 2) continue; // a one-unit block has its single door already
				if (!EntranceCount.measure(oneDoor(b)).problems().isEmpty()) failed++;
			}
			assertTrue(big > 0, type);
			assertTrue(failed >= big * 0.8, type + ": only " + failed + " of " + big + " failed with the extra doors walled up");
		}
	}

	/** Control: a door into a bedroom is reported. */
	@Test
	void flagsADoorIntoABedroom() {
		SceneDoc s = scenes("house", 24).stream().filter(x -> EntranceCount.measure(x.buildings().get(0)).big()).findFirst().orElseThrow();
		PlacedBuilding b = s.buildings().get(0);
		BuildingIr sh = b.shell();
		List<Room> rooms = new ArrayList<>();
		for (Room r : sh.rooms()) rooms.add(new Room(r.id(), r.storey(), r.volume(), r.parts(), "bedroom"));
		BuildingIr changed = new BuildingIr(sh.spec(), sh.bounds(), sh.storeys(), sh.volumes(), rooms, sh.openings(), sh.roofs(), sh.boxes(), sh.hooks());
		PlacedBuilding bad = new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), changed, b.siteBoxes());
		assertFalse(EntranceCount.measure(bad).problems().isEmpty());
	}

	private static long beds(PlacedBuilding b) {
		return b.shell().hooks().stream().filter(h -> h.kind().equals("bed")).count();
	}

	/** Round 2: a back door never takes the dwelling's only bed, and a lean-to store has its hung light. */
	@Test
	void dwellingsKeepABedAndStoresAreLit() {
		int houses = 0, workshops = 0, stores = 0, bedless = 0, wings = 0, wingless = 0;
		for (SceneDoc s : scenes("house", 40)) {
			PlacedBuilding b = s.buildings().get(0);
			if (!EntranceCount.measure(b).big()) continue;
			houses++;
			if (beds(b) == 0) bedless++; // some small houses had no bed before DS35DOORS too (interior review: 5-7 of 30)
		}
		for (SceneDoc s : scenes("workshop", 40)) {
			PlacedBuilding b = s.buildings().get(0);
			workshops++;
			if (b.shell().volumes().stream().anyMatch(v -> v.id().startsWith("wing"))) { wings++; if (beds(b) == 0) { wingless++; System.out.println("NOBED seed " + s.seed()); } }
			for (Room r : b.shell().rooms()) {
				if (!r.use().equals("store")) continue;
				stores++;
				assertTrue(b.shell().hooks().stream().anyMatch(h -> h.room().equals(r.id()) && h.kind().equals("ceiling_light")), "unlit store, seed " + s.seed());
			}
		}
		assertTrue(houses > 10 && workshops > 20 && stores > 0, houses + " " + workshops + " " + stores);
		System.out.println("BEDS houses " + bedless + "/" + houses + " workshops " + wingless + "/" + wings);
		assertTrue(wingless * 5 <= wings * 2, wingless + " of " + wings + " workshops with a wing have no bed");
		assertTrue(bedless * 5 <= houses, bedless + " of " + houses + " big houses have no bed");
	}

	/**
	 * Round 3 (verifier): every house and workshop that has a bed without its secondary doors keeps one with them (the back door never takes the only
	 * bed, {@code HouseRooms.sleepSafeBack}); the same request is generated with {@link dev.fractalbuildings.core.gen.v1.facade.ExtraDoors#OFF} and
	 * without, on level and sloping ground. Without the bed rule 8 of 134 lose it (all workshops; mutation check, round 3).
	 */
	@Test
	void secondaryDoorsNeverTakeTheOnlyBed() {
		int compared = 0, lost = 0;
		List<String> where = new ArrayList<>();
		for (String terrain : new String[] {"flat:64", "plane:64:1:0:4"})
			for (String type : new String[] {"house", "workshop"}) {
				dev.fractalbuildings.core.gen.v1.BuildingType t = dev.fractalbuildings.core.gen.v1.Types.get(type);
				for (int i = 0; i < 48; i++) {
					int[] lot = t.limits().testLots().get(i % t.limits().testLots().size());
					Facing front = Facing.values()[i % 4];
					lot = t.limits().oriented(lot, front);
					ProbeSpecs.Probes p = ProbeSpecs.parse(terrain);
					GenRequest req = new GenRequest(300 + i, new Lot("lot0", 0, 0, lot[0], lot[1], front), type, STYLES[i % 2], p.terrain(), p.biome());
					long off;
					dev.fractalbuildings.core.gen.v1.facade.ExtraDoors.OFF.set(true);
					try { off = beds(new GeneratorV1().generate(req).buildings().get(0)); } catch (IllegalArgumentException e) { continue; } finally { dev.fractalbuildings.core.gen.v1.facade.ExtraDoors.OFF.remove(); }
					if (off == 0) continue;
					compared++;
					if (beds(new GeneratorV1().generate(req).buildings().get(0)) == 0) { lost++; where.add(type + " " + terrain + " seed " + req.worldSeed()); }
				}
			}
		System.out.println("BEDS lost " + lost + " of " + compared + ": " + where);
		assertTrue(compared > 100, compared + " compared");
		assertEquals(List.of(), where, lost + " of " + compared + " dwellings lost their only bed to a secondary door");
	}

	/**
	 * Round 3 (architect): a high-rise's wide main door sits on the axis of its front wall as drawn, corner to corner: 2 leaves on an even front, 3 on an
	 * odd one (round 2 took the parity of the envelope, one column wider than the wall, and 23 of 32 doors sat half a block off).
	 */
	@Test
	void highRiseMainDoorIsOnTheFrontAxis() {
		int wide = 0;
		List<String> off = new ArrayList<>();
		for (String style : new String[] {"tall", "tall-brick", "tall-terracotta"})
			for (int[] lot : new int[][] {{24, 24}, {25, 25}, {30, 30}, {28, 28}})
				for (int seed = 0; seed < 8; seed++) {
					GenRequest req = request("highrise", seed, lot[0], lot[1], style, Facing.NORTH);
					PlacedBuilding b;
					try { b = new GeneratorV1().generate(req).buildings().get(0); } catch (IllegalArgumentException e) { continue; }
					BuildingIr sh = b.shell();
					Opening main = null;
					for (Opening o : sh.openings())
						if (o.kind() == OpeningKind.DOOR && o.to().equals(Opening.OUTSIDE) && o.facing() == Facing.NORTH && (main == null || o.box().sx() > main.box().sx())) main = o;
					if (main == null || main.box().sx() < 2) { off.add("narrow " + style + " " + lot[0] + " s" + seed + " w=" + (main == null ? 0 : main.box().sx()) + " spec " + sh.spec().width() + "x" + sh.spec().depth()); continue; }
					wide++;
					Opening door = main;
					dev.fractalbuildings.core.ir.Volume v = sh.volumes().stream().filter(x -> x.box().y() == 0 && x.box().z() == door.box().z() && door.box().x() >= x.box().x() && door.box().maxX() <= x.box().maxX()).findFirst().orElseThrow();
					int len = v.box().sx(); // the drawn front wall, corner cell to corner cell
					if (2 * door.box().x() + door.box().sx() != 2 * v.box().x() + len || door.box().sx() != (len % 2 == 0 ? 2 : 3))
						off.add(style + " " + lot[0] + "x" + lot[1] + " s" + seed + ": door x=" + door.box().x() + " w=" + door.box().sx() + " on a wall x=" + v.box().x() + ".." + (v.box().maxX() - 1));
				}
		System.out.println("AXIS " + wide + " wide main doors, off the axis: " + off);
		assertTrue(wide >= 60, wide + " wide main doors");
		assertTrue(off.size() * 10 <= wide, off.size() + " of " + wide + " off the axis: " + off);
	}
}
