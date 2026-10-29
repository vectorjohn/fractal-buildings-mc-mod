package dev.fractalbuildings.mod.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Debug hook (ROADLEAFORDER): {@code FB_FOLIAGE_WATCH="x,y,z,r;..."} logs every block write that reaches a watched box (cube of radius r),
 * with the chunk being generated, the feature or our pass that wrote it, and the decoration start of every chunk within 2 chunks of a
 * box. Off (no box) by default; one volatile read per write then.
 */
public final class FoliageWatch {
	private FoliageWatch() {}

	private static final int[][] BOXES = parse(System.getenv("FB_FOLIAGE_WATCH"));
	public static final boolean ACTIVE = BOXES.length > 0;
	/** Debug control: {@code FB_VANILLA_ONLY=1} turns our pieces, the road feature and TreeClip off (vanilla worldgen with the planner only). */
	public static final boolean VANILLA_ONLY = "1".equals(System.getenv("FB_VANILLA_ONLY"));

	private static int[][] parse(String s) {
		if (s == null || s.isBlank()) return new int[0][];
		List<int[]> out = new ArrayList<>();
		for (String p : s.split(";")) {
			String[] v = p.trim().split(",");
			out.add(new int[] {Integer.parseInt(v[0].trim()), Integer.parseInt(v[1].trim()), Integer.parseInt(v[2].trim()), Integer.parseInt(v[3].trim())});
		}
		return out.toArray(new int[0][]);
	}

	public static boolean watched(int x, int y, int z) {
		for (int[] b : BOXES) if (Math.abs(x - b[0]) <= b[3] && Math.abs(y - b[1]) <= b[3] && Math.abs(z - b[2]) <= b[3]) return true;
		return false;
	}

	public static boolean near(ChunkPos c) {
		for (int[] b : BOXES) if (Math.abs(c.x() - (b[0] >> 4)) <= 2 && Math.abs(c.z() - (b[2] >> 4)) <= 2) return true;
		return false;
	}

	private static String ours() {
		for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
			String c = e.getClassName();
			if (c.startsWith("dev.fractalbuildings.") && !c.endsWith("FoliageWatch") && !c.contains("mixin")) return c.substring(c.lastIndexOf('.') + 1) + "." + e.getMethodName() + ":" + e.getLineNumber();
		}
		return "-";
	}

	public static void write(ChunkPos center, String feature, BlockPos pos, BlockState old, BlockState now) {
		System.out.println("FBWATCH write " + pos.getX() + "," + pos.getY() + "," + pos.getZ() + " " + name(old) + "->" + name(now) + " gen " + center + " by " + feature + " ours " + ours());
	}

	public static boolean nearTree(BlockPos p) {
		for (int[] b : BOXES) if (Math.abs(p.getX() - b[0]) <= b[3] + 8 && Math.abs(p.getZ() - b[2]) <= b[3] + 8) return true;
		return false;
	}

	public static void decorate(ChunkPos c) { System.out.println("FBWATCH decorate " + c); }

	public static void tree(BlockPos origin, String what) { System.out.println("FBWATCH tree " + origin.getX() + "," + origin.getY() + "," + origin.getZ() + " " + what); }

	static String name(BlockState s) { return s.toString().replace("Block{minecraft:", "").replace("}", ""); }
}
