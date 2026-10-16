package dev.fractalbuildings.core.voxel;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.ir.Role;

/**
 * Dense voxel grid of one placed building in its local frame: the union of the shell bounds and
 * every shell and site box. Cells hold the role ordinal (0 = air). Shell boxes are applied first,
 * then site boxes, each in list order; an {@code air} box clears. Iteration is (y, z, x) order.
 */
public final class VoxelGrid {
	/** Visitor for {@link #forEach}; only non-air cells are visited. */
	@FunctionalInterface
	public interface Visitor {
		void visit(int x, int y, int z, Role role);
	}

	private static final Role[] ROLES = Role.values();

	public final int minX, minY, minZ, sx, sy, sz;
	private final byte[] data;
	private final Placement placement;

	private VoxelGrid(Box3 extent, Placement placement) {
		this.minX = extent.x();
		this.minY = extent.y();
		this.minZ = extent.z();
		this.sx = extent.sx();
		this.sy = extent.sy();
		this.sz = extent.sz();
		this.data = new byte[sx * sy * sz];
		this.placement = placement;
	}

	public static VoxelGrid of(PlacedBuilding b) {
		int x0 = b.shell().bounds().x(), y0 = b.shell().bounds().y(), z0 = b.shell().bounds().z();
		int x1 = b.shell().bounds().maxX(), y1 = b.shell().bounds().maxY(), z1 = b.shell().bounds().maxZ();
		for (var list : java.util.List.of(b.shell().boxes(), b.siteBoxes())) {
			for (Box bx : list) {
				Box3 q = bx.box();
				x0 = Math.min(x0, q.x());
				y0 = Math.min(y0, q.y());
				z0 = Math.min(z0, q.z());
				x1 = Math.max(x1, q.maxX());
				y1 = Math.max(y1, q.maxY());
				z1 = Math.max(z1, q.maxZ());
			}
		}
		VoxelGrid g = new VoxelGrid(Box3.of(x0, y0, z0, x1 - x0, y1 - y0, z1 - z0), b.placement());
		for (var list : java.util.List.of(b.shell().boxes(), b.siteBoxes())) {
			for (Box bx : list) g.fill(bx.box(), bx.role());
		}
		return g;
	}

	private void fill(Box3 q, Role role) {
		byte v = (byte) role.ordinal();
		for (int y = q.y(); y < q.maxY(); y++)
			for (int z = q.z(); z < q.maxZ(); z++)
				for (int x = q.x(); x < q.maxX(); x++) data[index(x, y, z)] = v;
	}

	private int index(int x, int y, int z) {
		return ((y - minY) * sz + (z - minZ)) * sx + (x - minX);
	}

	public boolean inGrid(int x, int y, int z) {
		return x >= minX && x < minX + sx && y >= minY && y < minY + sy && z >= minZ && z < minZ + sz;
	}

	/** Role at a local cell; {@link Role#AIR} outside the grid. */
	public Role get(int x, int y, int z) {
		return inGrid(x, y, z) ? ROLES[data[index(x, y, z)]] : Role.AIR;
	}

	public int count(Role role) {
		int n = 0;
		byte v = (byte) role.ordinal();
		for (byte d : data) if (d == v) n++;
		return n;
	}

	/** Number of non-air cells. */
	public int count() {
		int n = 0;
		for (byte d : data) if (d != 0) n++;
		return n;
	}

	public void forEach(Visitor v) {
		for (int y = minY; y < minY + sy; y++)
			for (int z = minZ; z < minZ + sz; z++)
				for (int x = minX; x < minX + sx; x++) {
					byte d = data[index(x, y, z)];
					if (d != 0) v.visit(x, y, z, ROLES[d]);
				}
	}

	public Placement placement() { return placement; }

	public int worldX(int lx, int lz) { return placement.worldX(lx, lz); }
	public int worldY(int ly) { return placement.worldY(ly); }
	public int worldZ(int lx, int lz) { return placement.worldZ(lx, lz); }

	/** A 1x1x1 world-space box for a local cell. */
	public Box3 worldCell(int lx, int ly, int lz) {
		return Box3.of(worldX(lx, lz), worldY(ly), worldZ(lx, lz), 1, 1, 1);
	}
}
