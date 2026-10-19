package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.gen.v1.facade.FacadePlan;
import dev.fractalbuildings.core.gen.v1.model.Face;
import dev.fractalbuildings.core.gen.v1.model.Faces;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Hook;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Roof;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.rand.Draws;
import dev.fractalbuildings.core.rand.Hash64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;

/**
 * The building in progress, one per shell build, never shared between threads. Phases run in the
 * fixed order of {@link Phase}; each reads the frozen results of the earlier ones (massing, faces,
 * facade plan, rooms) and writes its own boxes, which are applied in phase order (later phases win
 * where boxes overlap). See docs/plans/phase4.md "Pipeline".
 *
 * <p>Randomness: {@link #draws} / {@link #hash} give sub-seeds of the spec seed by purpose, so a
 * pass that adds or removes a draw never shifts another pass's choices. Use a purpose prefixed with
 * the pass name ("roof/form", "facade/entrance").
 */
public final class BuildContext {
	/** Pass order; boxes are emitted in this order. */
	public enum Phase { MASSING, STRUCTURE, ROOF, FACADE, INTERIOR, OPENINGS, DONE }

	private final BuildingSpec spec;
	private final BuildingType type;
	private final StyleRules style;
	private Phase phase = Phase.MASSING;
	private Massing massing;
	private List<Face> faces;
	private FacadePlan facadePlan;
	private final EnumMap<Phase, List<Box>> boxes = new EnumMap<>(Phase.class);
	private final List<Roof> roofs = new ArrayList<>();
	private final List<Room> rooms = new ArrayList<>();
	private final List<Opening> openings = new ArrayList<>();
	private final List<Hook> hooks = new ArrayList<>();
	private Canvas canvas;

	public BuildContext(BuildingSpec spec, BuildingType type, StyleRules style) {
		this.spec = Objects.requireNonNull(spec);
		this.type = Objects.requireNonNull(type);
		this.style = Objects.requireNonNull(style);
		for (Phase p : Phase.values()) boxes.put(p, new ArrayList<>());
	}

	public BuildingSpec spec() { return spec; }

	public BuildingType type() { return type; }

	public StyleRules style() { return style; }

	public Phase phase() { return phase; }

	/** Envelope width (local x) = bounds x extent. */
	public int width() { return spec.width(); }

	/** Envelope depth (local z) = bounds z extent. */
	public int depth() { return spec.depth(); }

	// ---- randomness ----

	public long hash(String purpose, long... keys) { return Hash64.hash(spec.specSeed(), purpose, keys); }

	public Draws draws(String purpose, long... keys) { return new Draws(hash(purpose, keys)); }

	// ---- frozen phase results ----

	public Massing massing() { return need(massing, "massing"); }

	/** Exterior wall runs of the massing ({@link Faces#of}), computed once. */
	public List<Face> faces() {
		if (faces == null) faces = List.copyOf(Faces.of(massing()));
		return faces;
	}

	public FacadePlan facadePlan() { return need(facadePlan, "facade plan"); }

	public List<Room> rooms() { return Collections.unmodifiableList(rooms); }

	public List<Opening> openings() { return Collections.unmodifiableList(openings); }

	public List<Roof> roofs() { return Collections.unmodifiableList(roofs); }

	public List<Hook> hooks() { return Collections.unmodifiableList(hooks); }

	/** Boxes emitted so far by one phase. */
	public List<Box> boxes(Phase p) { return Collections.unmodifiableList(boxes.get(p)); }

	// ---- writes (by the current phase) ----

	void advance(Phase p) {
		if (p.ordinal() != phase.ordinal() + 1) throw new IllegalStateException("phase " + p + " after " + phase);
		phase = p;
	}

	void setMassing(Massing m) {
		if (phase != Phase.MASSING) throw new IllegalStateException("massing set in phase " + phase);
		massing = Objects.requireNonNull(m);
	}

	public void setFacadePlan(FacadePlan plan) {
		if (phase != Phase.FACADE) throw new IllegalStateException("facade plan set in phase " + phase);
		facadePlan = Objects.requireNonNull(plan);
	}

	public void box(Box b) {
		if (phase == Phase.MASSING || phase == Phase.DONE) throw new IllegalStateException("box added in phase " + phase);
		boxes.get(phase).add(b);
		if (canvas != null && !canvas.paint(b)) canvas = null;
	}

	public void box(Role role, Layer layer, int storey, int x, int y, int z, int sx, int sy, int sz) {
		box(new Box(Box3.of(x, y, z, sx, sy, sz), role, layer, storey));
	}

	public void roof(Roof r) { roofs.add(Objects.requireNonNull(r)); }

	/** Adds a room with the next id ("r0", "r1"...) and returns its id. */
	public String room(int storey, String volume, List<Box3> parts, String use) {
		String id = "r" + rooms.size();
		rooms.add(new Room(id, storey, volume, parts, use));
		return id;
	}

	/** Adds an opening with the next id ("o0"...) and returns its id. */
	public String opening(OpeningKind kind, Box3 box, Facing facing, String from, String to) {
		String id = "o" + openings.size();
		openings.add(new Opening(id, kind, box, facing, from, to));
		return id;
	}

	/** Adds a furnishing hook with the next id ("h0"...) and returns its id. */
	public String hook(String kind, String room, int x, int y, int z, Facing facing) {
		String id = "h" + hooks.size();
		hooks.add(new Hook(id, kind, room, x, y, z, facing));
		return id;
	}

	// ---- reading the voxels so far ----

	/** Role at a local cell after all boxes emitted so far ({@link Role#AIR} outside the envelope). */
	public Role role(int x, int y, int z) {
		if (canvas == null) canvas = new Canvas(spec.width(), spec.depth(), boxes);
		return canvas.get(x, y, z);
	}

	/** Id of the room whose parts contain the cell, or null. */
	public String roomAt(int x, int y, int z) {
		for (Room r : rooms) for (Box3 p : r.parts()) if (p.contains(x, y, z)) return r.id();
		return null;
	}

	private static <T> T need(T v, String what) {
		if (v == null) throw new IllegalStateException(what + " is not available yet in this phase");
		return v;
	}

	/** Dense role grid over the envelope, rebuilt after boxes change. */
	private static final class Canvas {
		private static final Role[] ROLES = Role.values();
		final int sx, sy, sz;
		final byte[] data;

		Canvas(int sx, int sz, EnumMap<Phase, List<Box>> boxes) {
			int top = 1;
			for (List<Box> l : boxes.values()) for (Box b : l) top = Math.max(top, b.box().maxY());
			this.sx = sx;
			this.sy = top;
			this.sz = sz;
			this.data = new byte[sx * sy * sz];
			for (List<Box> l : boxes.values()) for (Box b : l) paint(b);
		}

		/** Applies a box on top; false if it reaches above the grid (then the grid must be rebuilt). */
		boolean paint(Box b) {
			Box3 q = b.box();
			if (q.maxY() > sy) return false;
			byte v = (byte) b.role().ordinal();
			for (int y = Math.max(0, q.y()); y < q.maxY(); y++)
				for (int z = Math.max(0, q.z()); z < Math.min(sz, q.maxZ()); z++)
					for (int x = Math.max(0, q.x()); x < Math.min(sx, q.maxX()); x++) data[(y * sz + z) * sx + x] = v;
			return true;
		}

		Role get(int x, int y, int z) {
			if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return Role.AIR;
			return ROLES[data[(y * sz + z) * sx + x]];
		}
	}
}
