package dev.fractalbuildings.core.gen.v1.types.shop;

import dev.fractalbuildings.core.gen.v1.BuildContext;
import dev.fractalbuildings.core.gen.v1.BuildingType;
import dev.fractalbuildings.core.gen.v1.Program;
import dev.fractalbuildings.core.gen.v1.TypeLimits;
import dev.fractalbuildings.core.gen.v1.interior.RoomProgram;
import dev.fractalbuildings.core.gen.v1.model.Massing;
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

	// lots 7..12 wide (no side margin: shops stand shoulder to shoulder), 12..24 deep
	private static final TypeLimits LIMITS = new TypeLimits(7, 11, 2, 4, 1, 1, 1, false,
			List.of(new int[] {9, 14}, new int[] {11, 17}, new int[] {14, 22}, new int[] {12, 25}), 400, 1);

	@Override
	public String id() { return ID; }

	@Override
	public TypeLimits limits() { return LIMITS; }

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
