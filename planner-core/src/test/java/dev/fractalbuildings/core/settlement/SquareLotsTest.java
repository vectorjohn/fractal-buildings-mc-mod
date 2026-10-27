package dev.fractalbuildings.core.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.gen.v1.types.shop.ShopType;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.probe.ProbeSpecs;
import dev.fractalbuildings.core.style.StyleField;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 7.17 SHOPLOW S4 (architect A6): in each market or square plaza at most one lot is the market hall's (a shop on level ground, at least
 * {@code SQUARE_MIN_FRONT} along a front that faces the plaza across its street), on about 60 % of the plazas that have an eligible lot; none on a green (a hamlet's or
 * village's) and none anywhere else.
 */
class SquareLotsTest {
	@Test
	void oneHallLotPerSquareAndOnlyWhereEligible() {
		int squares = 0, withLot = 0, eligiblePlazas = 0, marked = 0, outside = 0;
		for (int i = 0; i < 150; i++) {
			DistrictsTest.Planned p = DistrictsTest.planned(i);
			for (Districts.Detail d : p.all()) {
				DistrictPlan f = Briefs.fill(d.plan(), QuarterKind.values()[d.plan().district().quarter()], new StyleField(ProbeSpecs.parse(p.c().spec()).biome(), p.c().seed()), p.exact());
				int inPlan = 0;
				for (LotBrief l : f.lots()) {
					if (!l.square()) continue;
					inPlan++;
					assertTrue(l.builds() && l.type().equals("shop"), l.id() + " is a " + l.type());
					assertTrue((l.front().dx != 0 ? l.d() : l.w()) >= Briefs.SQUARE_MIN_FRONT, l.id() + " front too narrow");
					List<Plaza> on = new ArrayList<>();
					for (Plaza pl : f.plazas()) if (Briefs.hallPlaza(pl) && Briefs.facesPlaza(l, new Rect(pl.x(), pl.z(), pl.w(), pl.d()))) on.add(pl);
					assertFalse(on.isEmpty(), l.id() + " is does not face a market or square plaza");
					int relief = 0, lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
					for (int z = l.z(); z < l.z() + l.d(); z++) for (int x = l.x(); x < l.x() + l.w(); x++) { int y = p.exact().floorY(x, z); lo = Math.min(lo, y); hi = Math.max(hi, y); }
					assertTrue(hi - lo <= Briefs.SQUARE_RELIEF, l.id() + " relief " + (hi - lo));
				}
				for (Plaza pl : f.plazas()) {
					if (!Briefs.hallPlaza(pl)) continue;
					squares++;
					int n = 0;
					for (LotBrief l : f.lots()) if (l.square() && Briefs.facesPlaza(l, new Rect(pl.x(), pl.z(), pl.w(), pl.d()))) n++;
					assertTrue(n <= 1, "plaza " + pl.id() + " has " + n + " hall lots");
					if (n == 1) withLot++;
				}
				marked += inPlan;
				if (d.plan().plazas().stream().noneMatch(Briefs::hallPlaza)) outside += inPlan;
				// a plan whose plazas are all greens has none
				if (f.plazas().stream().allMatch(pl -> !Briefs.hallPlaza(pl))) assertEquals(0, inPlan, "a hall lot in a plan with no market or square");
			}
		}
		System.out.println("squares " + squares + ", hall lots " + marked + ", plazas with one " + withLot);
		assertEquals(0, outside);
		assertTrue(squares > 0 && marked > 0, "no square lots over 150 synthetic sites: " + squares + " squares, " + marked);
	}

	/** Architect A6: the hall is on about 60 % of the squares that have an eligible lot (one is taken by a neighbour now and then, so the bound is loose). */
	@Test
	void aboutSixtyPercentOfTheEligibleSquaresGetAHall() {
		int eligible = 0, marked = 0;
		for (int i = 0; i < 400; i++) {
			DistrictsTest.Planned p = DistrictsTest.planned(i);
			for (Districts.Detail d : p.all()) {
				DistrictPlan f = Briefs.fill(d.plan(), QuarterKind.values()[d.plan().district().quarter()], new StyleField(ProbeSpecs.parse(p.c().spec()).biome(), p.c().seed()), p.exact());
				for (Plaza pl : f.plazas()) {
					if (!Briefs.hallPlaza(pl)) continue;
					Rect r = new Rect(pl.x(), pl.z(), pl.w(), pl.d());
					boolean any = false, has = false;
					for (LotBrief l : f.lots()) {
						any |= l.square() ? Briefs.facesPlaza(l, r) : Briefs.eligible(l, r, p.exact());
						has |= l.square() && Briefs.facesPlaza(l, r);
					}
					if (any) eligible++;
					if (has) marked++;
				}
			}
		}
		System.out.println("eligible squares " + eligible + ", with a hall " + marked);
		assertTrue(eligible >= 40, "too few eligible squares to measure: " + eligible);
		double share = (double) marked / eligible;
		assertTrue(share > 0.45 && share < 0.75, "hall share " + share + " (" + marked + " of " + eligible + ")");
	}

	@Test
	void theLotIsAPureFunctionOfThePlan() {
		for (int i : new int[] {1, 4, 7, 12, 30}) {
			DistrictsTest.Planned p = DistrictsTest.planned(i);
			for (Districts.Detail d : p.all()) {
				StyleField sf = new StyleField(ProbeSpecs.parse(p.c().spec()).biome(), p.c().seed());
				String a = DistrictJson.write(Briefs.fill(d.plan(), QuarterKind.values()[d.plan().district().quarter()], sf, p.exact()));
				String b = DistrictJson.write(Briefs.fill(d.plan(), QuarterKind.values()[d.plan().district().quarter()], sf, p.exact()));
				assertEquals(a, b);
				assertEquals(DistrictJson.write(DistrictJson.read(a)), a, "square key round-trips");
			}
		}
	}

	@Test
	void theMinimumFrontIsTheMarketEnvelopePlusTheShopMargins() {
		int side = dev.fractalbuildings.core.gen.v1.Types.get("shop").limits().side();
		assertEquals(ShopType.MARKET_MIN_WIDTH + 2 * side, Briefs.SQUARE_MIN_FRONT);
	}
}
