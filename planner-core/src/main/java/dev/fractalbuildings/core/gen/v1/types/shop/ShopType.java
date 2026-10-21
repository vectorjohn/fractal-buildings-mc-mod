package dev.fractalbuildings.core.gen.v1.types.shop;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.Program;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
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
			return use.equals("shop") ? List.of("chest", "light").subList(0, Math.min(2, area / 20)) : RoomProgram.DEFAULT.hooks(use, area, d);
		}
	};

	/** The ground-storey form of this shop, drawn per seed: a closed ground floor (the default), a walkway under the upper storeys on posts, or a market hall. */
	static final int ENCLOSED = 0, ARCADE = 1, MARKET = 2;
	/** Chance in 100 of an arcade and of a market hall (the rest are enclosed); a form that does not fit the lot falls back to enclosed. */
	static final int ARCADE_PCT = 30, MARKET_PCT = 20;
	/** Kind of the single raised mass of a market hall. */
	public static final String MARKET_KIND = "market";

	static int groundMode(BuildContext c) {
		int v = c.draws("shop/ground").below(100);
		return v < ARCADE_PCT ? ARCADE : v < ARCADE_PCT + MARKET_PCT ? MARKET : ENCLOSED;
	}

	/** Height of the open market floor under a market hall, or 0 if the envelope is too narrow for the flight of steps up (as {@code TowerType.legHeight}). */
	static int legHeight(BuildContext c) {
		BuildingSpec sp = c.spec();
		int cap = Math.min(5, Math.min((sp.width() + 1) / 2, sp.maxHeight() - 2 - GROUND));
		return cap < Stilts.MIN_LIFT ? 0 : c.draws("shop/legs").range(Stilts.MIN_LIFT, cap);
	}

	// lots 7..12 wide (no side margin: shops stand shoulder to shoulder), 12..24 deep
	// shops want a level frontage: a lot whose relief exceeds 1 in SHOP_GRADE is rejected by the spec policy (as the city block's), so the display floor is at most about 3 above the street
	private static final int SHOP_GRADE = 12;
	private static final TypeLimits LIMITS = new TypeLimits(7, 11, 2, 4, 1, 1, 1, false,
			List.of(new int[] {9, 14}, new int[] {11, 17}, new int[] {14, 22}, new int[] {12, 25}), 400, SHOP_GRADE);

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }

	/** A market hall stands on a row of posts (an open floor for stalls, 2-3 blocks apart); the steps up to the shop floor are the site pass's front steps. */
	@Override
	public Stilts stilts(BuildContext c) {
		return c.massing().main().kind().equals(MARKET_KIND) ? new Stilts(legHeight(c), false, 3) : null;
	}

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
