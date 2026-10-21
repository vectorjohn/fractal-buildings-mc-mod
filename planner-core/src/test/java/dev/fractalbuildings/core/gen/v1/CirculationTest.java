package dev.fractalbuildings.core.gen.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.ir.SceneDoc;
import dev.fractalbuildings.core.metrics.Circulation;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Room;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Item 5.11: rooms reachable only through two or more other rooms ({@link Circulation}). The
 * measurement prints with {@code -Dfb.circulation=N} (cases per type and style).
 */
class CirculationTest {
	/** {rooms, deep rooms, buildings with a deep room, buildings} over n houses of a style. */
	static long[] measure(String type, String style, int n) {
		BuildingType t = Types.get(type);
		int lots = t.limits().testLots().size();
		long[] sum = new long[5];
		long[][] per = IntStream.range(0, n).parallel().mapToObj(i -> {
			GenRequest rq = V1Case.draw(t, i % lots, i / lots, List.of(style)).request();
			SceneDoc s = new GeneratorV1().generate(rq);
			long[] r = new long[5];
			s.buildings().forEach(b -> {
				Circulation.Result c = Circulation.measure(b.shell());
				r[0] += c.rooms();
				r[1] += c.deep();
				r[2] += c.deep() > 0 ? 1 : 0;
				r[3]++;
				r[4] += c.deepStrict();
			});
			return r;
		}).toArray(long[][]::new);
		for (long[] r : per) for (int k = 0; k < 5; k++) sum[k] += r[k];
		return sum;
	}

	/** Houses of one style over the property lots. */
	static List<BuildingIr> houses(String style, int n) {
		BuildingType t = Types.get("house");
		int lots = t.limits().testLots().size();
		return IntStream.range(0, n).parallel()
				.mapToObj(i -> new GeneratorV1().generate(V1Case.draw(t, i % lots, i / lots, List.of(style)).request()).buildings().get(0).shell())
				.toList();
	}

	@Test
	void fewHouseRoomsSitBehindTwoOtherRooms() {
		// 1500 houses each, before 5.11: plain 1.5 %, medieval 4.9 %, desert 5.2 % of rooms; after: 0.14 %, 0.26 %, 0.22 %
		for (String style : List.of("plain", "medieval", "desert")) {
			int rooms = 0, deep = 0;
			for (BuildingIr b : houses(style, 400)) {
				Circulation.Result r = Circulation.measure(b);
				rooms += r.rooms();
				deep += r.deep();
			}
			assertTrue(deep * 100.0 / rooms < 1.0, style + ": " + deep + " of " + rooms + " rooms sit behind two or more ordinary rooms");
		}
	}

	@Test
	void someWalkThroughChainsRemainAsOddness() {
		int chained = 0;
		for (BuildingIr b : houses("medieval", 400)) if (Circulation.measure(b).deepStrict() > 0) chained++;
		assertTrue(chained >= 40, "walk-through layouts should still happen sometimes, saw " + chained);
	}

	static Role roleAt(BuildingIr ir, int x, int y, int z) {
		Role r = Role.AIR;
		for (Box b : ir.boxes()) if (b.box().contains(x, y, z)) r = b.role();
		return r;
	}

	@Test
	void archwaysJoinPublicRoomsAndAreDoorlessHoles() {
		int arches = 0, wide = 0, lintels = 0;
		for (BuildingIr b : houses("medieval", 300)) {
			Map<String, String> use = new TreeMap<>();
			for (Room r : b.rooms()) use.put(r.id(), r.use());
			for (Opening o : b.openings()) {
				if (o.kind() != OpeningKind.ARCH) continue;
				arches++;
				String at = o.id() + " " + o.box();
				assertTrue(RoomProgram.DEFAULT.openPlan(use.get(o.from())) && RoomProgram.DEFAULT.openPlan(use.get(o.to())), at + " joins " + use.get(o.from()) + " and " + use.get(o.to()));
				assertEquals(2, o.box().sy(), at);
				assertTrue(o.box().sx() == 1 || o.box().sz() == 1, at);
				if (o.box().sx() * o.box().sz() > 1) wide++;
				for (int x = o.box().x(); x < o.box().maxX(); x++)
					for (int z = o.box().z(); z < o.box().maxZ(); z++) {
						assertEquals(Role.AIR, roleAt(b, x, o.box().y(), z), at + " is not open at the foot");
						assertEquals(Role.AIR, roleAt(b, x, o.box().y() + 1, z), at + " is not open at the head");
						if (o.box().sx() * o.box().sz() > 1 && roleAt(b, x, o.box().y() + 2, z) == Role.TRIM) lintels++;
					}
			}
		}
		assertTrue(arches >= 20, "archways should be common enough to see, saw " + arches);
		assertTrue(wide >= 5, "some archways are wider than one cell, saw " + wide);
		assertTrue(lintels >= 5, "wide archways get a lintel, saw " + lintels);
	}

	@Test
	void sameSeedSameCirculation() {
		List<BuildingIr> a = houses("medieval", 40), b = houses("medieval", 40);
		for (int i = 0; i < a.size(); i++) assertEquals(a.get(i).openings(), b.get(i).openings(), "house " + i);
	}

	@Test
	void report() {
		int n = Integer.getInteger("fb.circulation", 0);
		if (n == 0) return;
		for (String type : List.of("house", "shop", "workshop", "hall", "temple", "tower", "block", "highrise"))
			for (String style : List.of("plain", "medieval", "plains", "desert", "snowy")) {
				long[] r = measure(type, style, n);
				System.out.printf("CIRC %s %s buildings=%d rooms=%d deep=%d (%.2f%%) withDeep=%d (%.1f%%) strictDeep=%d (%.2f%%)%n", type, style, r[3], r[0], r[1], 100.0 * r[1] / r[0], r[2], 100.0 * r[2] / r[3], r[4], 100.0 * r[4] / r[0]);
			}
	}
}
