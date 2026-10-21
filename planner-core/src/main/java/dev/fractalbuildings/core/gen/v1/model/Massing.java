package dev.fractalbuildings.core.gen.v1.model;

import java.util.List;
import java.util.Objects;

/**
 * The frozen output of the massing phase: the storey grid, the masses (the first is the main mass:
 * ground floor, holds the main entrance on its front), the circulation cores, the masses that get an
 * exterior entrance of their own ({@code entrances}, the main mass first: row houses and city-block
 * units have one each), and the {@code partyWalls}: shared wall strips that stay closed (no door
 * between two units). Rules every massing must keep are in {@link MassingCheck}; the plan explains
 * them (docs/plans/phase4.md "Massing contract").
 */
public record Massing(StoreyGrid grid, List<Mass> masses, List<Core> cores, List<String> entrances, List<Party> partyWalls) {
	/** Two masses whose shared wall strip gets no door. */
	public record Party(String a, String b) {
		public Party {
			Objects.requireNonNull(a);
			Objects.requireNonNull(b);
		}

		public boolean is(Mass x, Mass y) {
			return a.equals(x.id()) && b.equals(y.id()) || a.equals(y.id()) && b.equals(x.id());
		}
	}

	public Massing {
		Objects.requireNonNull(grid);
		masses = List.copyOf(masses);
		cores = List.copyOf(cores);
		entrances = List.copyOf(entrances);
		partyWalls = List.copyOf(partyWalls);
		if (masses.isEmpty()) throw new IllegalArgumentException("no masses");
	}

	/** One entrance (the main mass's), no party walls. */
	public Massing(StoreyGrid grid, List<Mass> masses, List<Core> cores) {
		this(grid, masses, cores, List.of(masses.get(0).id()), List.of());
	}

	/** The same massing {@code dz} blocks further back (all masses and cores), for shells whose envelope grew at the front (air rights). */
	public Massing shiftedZ(int dz) {
		if (dz == 0) return this;
		List<Mass> ms = new java.util.ArrayList<>();
		for (Mass m : masses) ms.add(new Mass(m.id(), m.kind(), m.x(), m.z() + dz, m.sx(), m.sz(), m.from(), m.to(), m.parent(), m.open()));
		List<Core> cs = new java.util.ArrayList<>();
		for (Core k : cores) cs.add(new Core(k.x(), k.z() + dz, k.sx(), k.sz(), k.from(), k.to()));
		return new Massing(grid, ms, cs, entrances, partyWalls);
	}

	public Mass main() { return masses.get(0); }

	public Mass mass(String id) {
		for (Mass m : masses) if (m.id().equals(id)) return m;
		throw new IllegalArgumentException("no mass " + id);
	}

	/** Number of grid storeys used (the highest {@code to}). */
	public int storeys() {
		int n = 0;
		for (Mass m : masses) n = Math.max(n, m.to());
		return n;
	}

	/** True if the shared strip of a and b is a party wall. */
	public boolean party(Mass a, Mass b) {
		for (Party p : partyWalls) if (p.is(a, b)) return true;
		return false;
	}

	/** True if a mass other than {@code self} spans storey s and contains column (cx, cz). */
	public boolean covered(Mass self, int s, int cx, int cz) {
		for (Mass m : masses) if (m != self && m.spans(s) && m.contains(cx, cz)) return true;
		return false;
	}

	/** The mass spanning storey s whose interior contains column (cx, cz), or null. */
	public Mass interiorAt(int s, int cx, int cz) {
		for (Mass m : masses) if (m.spans(s) && m.interior(cx, cz)) return m;
		return null;
	}

	/** The mass spanning storey s that strictly contains the whole rectangle, or null. */
	public Mass interiorContaining(int s, int x, int z, int sx, int sz) {
		for (Mass m : masses) {
			if (m.spans(s) && m.interior(x, z) && m.interior(x + sx - 1, z + sz - 1)) return m;
		}
		return null;
	}

	/** True if column (cx, cz) lies in a core serving storey s. */
	public boolean inCore(int s, int cx, int cz) {
		for (Core c : cores) if (s >= c.from() && s < c.to() && c.contains(cx, cz)) return true;
		return false;
	}

	/**
	 * The shared wall strip of two masses (their footprint intersection when it is exactly one block
	 * thick), as {x, z, sx, sz}, or null if they don't intersect or overlap more than a strip.
	 */
	public static int[] strip(Mass a, Mass b) {
		int x0 = Math.max(a.x(), b.x()), x1 = Math.min(a.maxX(), b.maxX());
		int z0 = Math.max(a.z(), b.z()), z1 = Math.min(a.maxZ(), b.maxZ());
		if (x1 <= x0 || z1 <= z0) return null;
		if (x1 - x0 != 1 && z1 - z0 != 1) return null;
		return new int[] {x0, z0, x1 - x0, z1 - z0};
	}
}
