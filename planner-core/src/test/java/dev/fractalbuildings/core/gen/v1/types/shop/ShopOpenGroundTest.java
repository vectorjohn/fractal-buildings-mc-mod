package dev.fractalbuildings.core.gen.v1.types.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.GenRequest;
import dev.fractalbuildings.core.gen.v1.SpecPolicyV1;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Lot;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * 5.12: shops sometimes have open ground at street level, an arcade (the upper storeys carried over
 * a walkway on posts), and are built only on a level frontage; the market hall (the whole building on a row of posts, the floor
 * under it open) is the one shop per square that the brief marks ({@link MarketHallTest}, 7.17 S4). The geometry rules are checked
 * here per variant; the invariants (no-floating, entrance-reachable...) by V1PropertyTest, which draws all of them.
 */
class ShopOpenGroundTest {
	enum Form { ENCLOSED, ARCADE, MARKET }

	static Form form(BuildingIr b) {
		if (b.volumes().stream().anyMatch(v -> v.id().startsWith("leg"))) return Form.MARKET;
		if (b.volumes().stream().anyMatch(v -> v.id().startsWith("post"))) return Form.ARCADE;
		return Form.ENCLOSED;
	}

	/** Problems with the open ground of one shop, empty if it follows the rules of its form. */
	static List<String> problems(BuildingIr b) {
		List<String> p = new ArrayList<>();
		Opening door = null;
		for (Opening o : b.openings()) if (o.kind() == OpeningKind.DOOR && Opening.OUTSIDE.equals(o.to())) door = o;
		if (door == null) return List.of("no entrance");
		switch (form(b)) {
			case ARCADE -> {
				List<Volume> posts = b.volumes().stream().filter(v -> v.id().startsWith("post")).toList();
				if (posts.size() < 2) p.add("an arcade with " + posts.size() + " posts");
				int front = b.volumes().stream().filter(v -> v.id().startsWith("jetty")).mapToInt(v -> v.box().z()).min().orElse(-1);
				if (front != 0) p.add("no jetty at the front of the envelope");
				int doorZ = door.box().z();
				if (doorZ < 2) p.add("walkway only " + doorZ + " deep");
				for (Volume v : posts) {
					if (v.box().z() != front) p.add(v.id() + " is not on the front edge");
					if (v.box().x() >= door.box().x() - 1 && v.box().x() <= door.box().maxX()) p.add(v.id() + " stands in front of the door");
					if (v.box().sy() != b.storeys().get(1).y()) p.add(v.id() + " does not reach the floor above");
				}
			}
			case MARKET -> p.addAll(MarketHallTest.problems(b)); // the market hall's own rules (S4)
			case ENCLOSED -> { }
		}
		return p;
	}

	@Test
	void everyFormFollowsItsRulesAndTheMixHasAllThree() {
		int[] count = new int[3];
		int n = 0;
		for (String style : ShopTypeTest.STYLES)
			for (int[] e : ShopTypeTest.ENVELOPES)
				for (long seed = 0; seed < 512; seed++) {
					BuildingIr b = ShopTypeTest.shop(style, seed, e[0], e[1]);
					List<String> p = problems(b);
					assertTrue(p.isEmpty(), style + " seed " + seed + " " + e[0] + "x" + e[1] + ": " + p);
					count[form(b).ordinal()]++;
					n++;
				}
		System.out.println(String.format(Locale.ROOT, "shop forms over %d: enclosed %d, arcade %d, market %d", n, count[0], count[1], count[2]));
		assertTrue(count[0] >= 0.55 * n, "enclosed " + count[0] + "/" + n);
		assertTrue(count[1] >= 0.15 * n, "arcades " + count[1] + "/" + n);
		assertEquals(0, count[2], "a market hall on a shop that is not the square's"); // only a square lot is one (S4)
	}

	@Test
	void theChecksCanFail() {
		BuildingIr arcade = null;
		for (long seed = 0; arcade == null && seed < 200; seed++) {
			BuildingIr b = ShopTypeTest.shop("medieval", seed, 10, 20);
			if (form(b) == Form.ARCADE) arcade = b;
		}
		assertTrue(arcade != null && problems(arcade).isEmpty());
		// mutant: an arcade without its posts is one with none
		List<Volume> noPosts = arcade.volumes().stream().filter(v -> !v.id().startsWith("post")).toList();
		assertEquals(Form.ENCLOSED, form(new BuildingIr(arcade.spec(), arcade.bounds(), arcade.storeys(), noPosts, arcade.rooms(), arcade.openings(), arcade.roofs(), arcade.boxes(), arcade.hooks())));
	}

	private static GenRequest onSlope(int run) {
		ProbeSpecs.Probes p = ProbeSpecs.parse("plane:64:0:1:" + run); // rises towards the back of a north-facing lot
		return new GenRequest(1, new Lot("lot0", 0, 0, 11, 20, Facing.NORTH), "shop", "plain", p.terrain(), p.biome());
	}

	@Test
	void shopsOnlyGoOnGentleGround() {
		SpecPolicyV1.spec(onSlope(100)); // 1 in 100: fine
		SpecPolicyV1.spec(onSlope(12)); // 1 in 12 over 20: a step or two at the door
		SpecPolicyV1.spec(onSlope(4)); // 1 in 4 (relief 5): fine since SHOPLOW loosened SHOP_GRADE (the site pass digs the shop in)
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SpecPolicyV1.spec(onSlope(2))); // relief 10 over 11 x 20
		assertTrue(e.getMessage().contains("too steep for a shop"), e.getMessage());
	}
}
