package dev.fractalbuildings.core.settlement;

import dev.fractalbuildings.core.gen.Brief;
import dev.fractalbuildings.core.ir.Facing;
import java.util.Objects;

/**
 * A lot with its brief. {@code streets} is the mask of sides facing a street in the lot's local frame (as
 * {@link Brief}); {@code frontY} is {@link Brief#NO_FRONT_Y} if unknown; {@code status} is {@code "build"} or
 * {@code "empty:<reason>"}.
 */
public record LotBrief(String id, int x, int z, int w, int d, Facing front, String type, String style, int minStoreys, int maxStoreys,
		int streets, int airRights, int frontY, int density, String status, Brief.Walk walk, boolean square) {
	public static final String BUILD = "build";

	/** A lot that is not a market-hall lot (7.17 S4: {@code square} marks the one lot per square that may carry the market hall). */
	public LotBrief(String id, int x, int z, int w, int d, Facing front, String type, String style, int minStoreys, int maxStoreys,
			int streets, int airRights, int frontY, int density, String status, Brief.Walk walk) {
		this(id, x, z, w, d, front, type, style, minStoreys, maxStoreys, streets, airRights, frontY, density, status, walk, false);
	}

	/** A lot without a wall-walk level. */
	public LotBrief(String id, int x, int z, int w, int d, Facing front, String type, String style, int minStoreys, int maxStoreys,
			int streets, int airRights, int frontY, int density, String status) {
		this(id, x, z, w, d, front, type, style, minStoreys, maxStoreys, streets, airRights, frontY, density, status, null);
	}

	public LotBrief {
		Objects.requireNonNull(id);
		Objects.requireNonNull(front);
		Objects.requireNonNull(type);
		Objects.requireNonNull(style);
		Objects.requireNonNull(status);
		if (w <= 0 || d <= 0) throw new IllegalArgumentException("lot " + id + " " + w + "x" + d);
		if (minStoreys < 1 || maxStoreys < minStoreys) throw new IllegalArgumentException("lot " + id + " storeys " + minStoreys + ".." + maxStoreys);
		if (streets < 0 || streets > 15) throw new IllegalArgumentException("lot " + id + " streets " + streets);
		if (airRights < 0) throw new IllegalArgumentException("lot " + id + " airRights " + airRights);
		if (density < 0 || density > 100) throw new IllegalArgumentException("lot " + id + " density " + density);
		if (!status.equals(BUILD) && !(status.startsWith("empty:") && status.length() > 6)) throw new IllegalArgumentException("lot " + id + " status '" + status + "'");
	}

	public boolean builds() { return status.equals(BUILD); }

	public Rect rect() { return new Rect(x, z, w, d); }

	/** The brief to hand to the generator (the front bit is forced by the spec policy). */
	public Brief brief() { return new Brief(minStoreys, maxStoreys, streets, airRights, frontY, walk, square); }
}
