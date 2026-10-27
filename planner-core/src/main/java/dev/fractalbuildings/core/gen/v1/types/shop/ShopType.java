package dev.fractalbuildings.core.gen.v1.types.shop;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.Program;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.interior.FloorPlan;
import dev.fractalbuildings.core.gen.v1.interior.FloorPlanner;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.structure.Stilts;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.rand.Draws;
import java.util.List;

/**
 * A shop (step 4.6): a narrow deep lot with a main block as wide as the envelope, a 5-high ground
 * storey whose front is a shopfront (centred door, 2-3 wide display windows between piers, a trim
 * fascia above), a counter hook in the shop room (the one with the door), and living quarters upstairs (a bed and a table
 * hook). Optionally a narrower rear wing and a front jetty. Massing is {@link ShopMassing}; the
 * facade and opening programs wrap the shared passes; see {@link Shopfront}.
 */
public final class ShopType implements BuildingType {
	public static final String ID = "shop";
	/** Ground storey height (slab to slab). */
	public static final int GROUND = 5;
	/** Facade layout kind of a shopfront display window bay. */
	public static final String SHOPFRONT = "shopfront";
	/** Facade layout kind of a pier between display windows (a custom kind, so the interior pass reserves the cells behind it). */
	public static final String PIER = "pier";

	/** The shop floor (the room with the entrance) has the counter; upstairs is the default dwelling. */
	static final RoomProgram PROGRAM = new RoomProgram() {
		@Override
		public String use(Role role, int area, Draws d) { return role == Role.ENTRY ? "shop" : RoomProgram.DEFAULT.use(role, area, d); }

		@Override
		public List<String> hooks(String use, int area, Draws d) {
			// the market hall's rooms (civic, single rooms of 60 cells or more): the guild hall has a table group and a fireplace group under two lights; a store, chests and a clerk's table
			if (use.equals("hall") && area >= MARKET_ROOM_AREA) return List.of("table", "hearth", "ceiling_light", "chest", "light").subList(0, Math.min(5, 2 + area / 20));
			if (use.equals("store")) return List.of("chest", "chest", "ceiling_light", "chest", "table").subList(0, Math.min(5, 1 + area / 12));
			return use.equals("shop") ? List.of("chest", "light").subList(0, Math.min(2, area / 20)) : RoomProgram.DEFAULT.hooks(use, area, d);
		}
	};

	/** Smallest area (cells) of a room of a market hall; a shop's own passage rooms are much smaller. */
	static final int MARKET_ROOM_AREA = 40;

	/** The ground-storey form of this shop: a closed ground floor (the default), a walkway under the upper storeys on posts, or the square's market hall. */
	static final int ENCLOSED = 0, ARCADE = 1, MARKET = 2;
	/** Chance in 100 of an arcade (the rest are enclosed); a market hall is not drawn: it is the shop on a square that the brief marks ({@link BuildingSpec#square}, one per square, {@code Briefs}). */
	static final int ARCADE_PCT = 30;
	/** Narrowest envelope a market hall stands on (the stair along its back edge, the room above and the posts need it). */
	public static final int MARKET_MIN_WIDTH = 9;
	/** Kind of the single raised mass of a market hall. */
	public static final String MARKET_KIND = "market";

	static int groundMode(BuildContext c) {
		if (c.spec().square() && c.spec().width() >= MARKET_MIN_WIDTH) return MARKET;
		return c.draws("shop/ground").below(100) < ARCADE_PCT ? ARCADE : ENCLOSED;
	}

	/**
	 * Height of the open market floor under a market hall, floor to floor: 5 (4 clear above the paving) at envelope 9 and 10, 6 (5 clear) from 11
	 * (DS-23; the architect withdrew "legs 3": the lift counts from the paving slab). 0 if the envelope is too low.
	 */
	static int legHeight(BuildContext c) {
		BuildingSpec sp = c.spec();
		int lift = sp.width() >= 11 ? 6 : 5;
		return sp.maxHeight() - 2 - GROUND < lift ? 0 : lift;
	}

	// lots 7..12 wide (no side margin: shops stand shoulder to shoulder), 12..24 deep
	// shops want a level frontage: a lot whose relief exceeds 1 in SHOP_GRADE is rejected by the spec policy (as the city block's), so the display floor is at most about 3 above the street
	private static final int SHOP_GRADE = 12;
	private static final TypeLimits LIMITS = new TypeLimits(7, 11, 2, 4, 1, 1, 1, false,
			List.of(new int[] {9, 14}, new int[] {11, 17}, new int[] {14, 22}, new int[] {12, 25}), 400, SHOP_GRADE, true);

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }

	/** A market hall stands on a row of posts (an open floor for stalls, 2-3 blocks apart); the steps up to the shop floor are the site pass's front steps. */
	@Override
	public Stilts stilts(BuildContext c) {
		return c.massing().main().kind().equals(MARKET_KIND) ? new Stilts(legHeight(c), false, 3, true) : null;
	}

	/** The market hall's floors are one room each, civic over the open floor: a guild hall (the {@code hall} programme), then stores above it (I1). Other shops use the generic planner. */
	@Override
	public FloorPlanner floors() { return MARKET_FLOORS; }

	private static final FloorPlanner MARKET_FLOORS = new FloorPlanner() {
		@Override
		public Planned plan(BuildContext c, int massIndex, Mass a, int storey, boolean[][] reserved, boolean[][] core) {
			if (!a.kind().equals(MARKET_KIND)) return null;
			return new Planned(FloorPlan.single(a.sx() - 2, a.sz() - 2), new String[] {storey == 0 ? "hall" : "store"});
		}

		@Override
		public boolean newBand(BuildContext c, int massIndex, Mass a, int storey) { return a.kind().equals(MARKET_KIND) && storey == 1; }
	};

	@Override
	public Massing mass(BuildContext c) { return ShopMassing.build(c, LIMITS.maxStoreys()); }

	@Override
	public Program facade() { return Shopfront::facade; }

	@Override
	public RoomProgram rooms() { return PROGRAM; }

	@Override
	public Program interior() { return Shopfront::interior; }

	@Override
	public Program openings() { return Shopfront::openings; }
}
