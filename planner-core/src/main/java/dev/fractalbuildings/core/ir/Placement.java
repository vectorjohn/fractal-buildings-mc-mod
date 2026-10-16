package dev.fractalbuildings.core.ir;

/**
 * Local-to-world transform of a shell: a quarter-turn rotation about +y, then a translation. The
 * rotated footprint stays in the positive quadrant of {@code origin}, so a local footprint of
 * {@code sx} by {@code sz} columns covers world x in [ox, ox + sx') and z in [oz, oz + sz'),
 * where {@code sx' = sz} and {@code sz' = sx} for odd rotations.
 *
 * <p>Rotation r turns clockwise seen from above (north to east): the local front (z = 0, facing
 * north) faces {@code Facing.NORTH.rotate(r)}. With local column (lx, lz):
 * <ul>
 *   <li>r = 0: (ox + lx, oz + lz)
 *   <li>r = 1: (ox + sz - 1 - lz, oz + lx)
 *   <li>r = 2: (ox + sx - 1 - lx, oz + sz - 1 - lz)
 *   <li>r = 3: (ox + lz, oz + sx - 1 - lx)
 * </ul>
 * {@code sx} and {@code sz} are the shell's bounds extent ({@code bounds.maxX()}, {@code bounds.maxZ()}),
 * so the transform maps the whole [0, maxX) x [0, maxZ) rectangle; y is just {@code oy + ly}.
 */
public record Placement(int ox, int oy, int oz, int rotation, int sx, int sz) {
	public Placement {
		if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("rotation " + rotation);
		if (sx <= 0 || sz <= 0) throw new IllegalArgumentException("extent " + sx + "x" + sz);
	}

	public int worldX(int lx, int lz) {
		return switch (rotation) {
			case 0 -> ox + lx;
			case 1 -> ox + sz - 1 - lz;
			case 2 -> ox + sx - 1 - lx;
			default -> ox + lz;
		};
	}

	public int worldZ(int lx, int lz) {
		return switch (rotation) {
			case 0 -> oz + lz;
			case 1 -> oz + lx;
			case 2 -> oz + sz - 1 - lz;
			default -> oz + sx - 1 - lx;
		};
	}

	public int worldY(int ly) { return oy + ly; }

	/** Inverse of {@link #worldX}/{@link #worldZ}: local x of a world column. */
	public int localX(int wx, int wz) {
		return switch (rotation) {
			case 0 -> wx - ox;
			case 1 -> wz - oz;
			case 2 -> ox + sx - 1 - wx;
			default -> oz + sx - 1 - wz;
		};
	}

	public int localZ(int wx, int wz) {
		return switch (rotation) {
			case 0 -> wz - oz;
			case 1 -> ox + sz - 1 - wx;
			case 2 -> oz + sz - 1 - wz;
			default -> wx - ox;
		};
	}

	/** The world box covered by a local box (rotation applied to its footprint). */
	public Box3 toWorld(Box3 b) {
		int ax = worldX(b.x(), b.z()), az = worldZ(b.x(), b.z());
		int bx = worldX(b.maxX() - 1, b.maxZ() - 1), bz = worldZ(b.maxX() - 1, b.maxZ() - 1);
		int x0 = Math.min(ax, bx), z0 = Math.min(az, bz);
		return new Box3(x0, oy + b.y(), z0, Math.max(ax, bx) - x0 + 1, b.sy(), Math.max(az, bz) - z0 + 1);
	}

	/** The world direction a local facing points to. */
	public Facing toWorld(Facing f) { return f.rotate(rotation); }
}
