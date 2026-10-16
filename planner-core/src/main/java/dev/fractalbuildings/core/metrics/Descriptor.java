package dev.fractalbuildings.core.metrics;

import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.voxel.VoxelGrid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Per-building summary used by {@link Diversity}. Everything is in the shell's local frame, so
 * placement (position, rotation, foundations) never counts as variety. {@code voxels} is the sorted
 * packed positions of the shell's non-air cells (site boxes excluded).
 */
public record Descriptor(
		String specHash,
		int width,
		int depth,
		int storeys,
		String ridgeAxis,
		String roomsPerStorey,
		int doorEighths,
		int windows,
		long[] voxels,
		long voxelHash) {

	public static Descriptor of(PlacedBuilding b) {
		var shell = b.shell();
		var spec = shell.spec();
		// Shell only: foundations are site-dependent, not design.
		VoxelGrid g = VoxelGrid.of(new PlacedBuilding(b.id(), b.lots(), b.ox(), b.oy(), b.oz(), b.rotation(), shell, List.of()));
		long[] tmp = new long[g.count()];
		int[] n = {0};
		g.forEach((x, y, z, role) -> {
			if (role != Role.AIR) tmp[n[0]++] = pack(x, y, z);
		});
		Arrays.sort(tmp);
		long h = Hash64.of("voxels");
		for (long k : tmp) h = Hash64.mix(h ^ k);

		int[] perStorey = new int[shell.storeys().size()];
		for (var r : shell.rooms()) if (r.storey() >= 0 && r.storey() < perStorey.length) perStorey[r.storey()]++;
		StringBuilder rooms = new StringBuilder();
		for (int i = 0; i < perStorey.length; i++) rooms.append(i == 0 ? "" : ",").append(perStorey[i]);

		int windows = 0;
		int door = -1;
		for (Opening o : shell.openings()) {
			if (o.kind() == OpeningKind.WINDOW) windows++;
			else if (o.kind() == OpeningKind.DOOR && door < 0 && (o.from().equals(Opening.OUTSIDE) || o.to().equals(Opening.OUTSIDE))) {
				int w = Math.max(1, shell.bounds().sx());
				door = Math.min(7, (o.box().x() - shell.bounds().x()) * 8 / w);
			}
		}
		String ridge = "none";
		if (!shell.roofs().isEmpty() && shell.roofs().get(0).ridgeAxis() != null) ridge = shell.roofs().get(0).ridgeAxis().name();
		return new Descriptor(dev.fractalbuildings.core.ir.SpecHash.of(spec), spec.width(), spec.depth(), spec.storeys(),
				ridge, rooms.toString(), door, windows, tmp, h);
	}

	static long pack(int x, int y, int z) {
		return ((long) (y + 4096) << 40) | ((long) (z + 1_000_000) << 20) | (long) (x + 1_000_000 & 0xFFFFF);
	}

	/** Width, depth, storeys and rooms: the "signature" counted as distinct. */
	public String signature() { return width + "x" + depth + "x" + storeys + "/" + roomsPerStorey; }

	/** Named categorical values for entropy, in fixed order. */
	List<String[]> categories() {
		List<String[]> l = new ArrayList<>();
		l.add(new String[] {"width", Integer.toString(width)});
		l.add(new String[] {"depth", Integer.toString(depth)});
		l.add(new String[] {"storeys", Integer.toString(storeys)});
		l.add(new String[] {"ridgeAxis", ridgeAxis});
		l.add(new String[] {"roomsPerStorey", roomsPerStorey});
		l.add(new String[] {"doorEighths", Integer.toString(doorEighths)});
		l.add(new String[] {"windows", Integer.toString(windows)});
		return l;
	}
}
