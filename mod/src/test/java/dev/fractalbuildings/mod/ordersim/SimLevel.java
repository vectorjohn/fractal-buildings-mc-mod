package dev.fractalbuildings.mod.ordersim;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.util.RandomSource;

/**
 * ORDERSIM S1: a {@link WorldGenLevel} bound to one decorating chunk, as a {@link Proxy} over a {@link SimWorld}. It mimics
 * {@code WorldGenRegion}: reads and writes only within the zone (the 3 x 3 chunks around the centre by default); a write
 * outside the write zone is dropped and recorded as a {@link SimWorld.ZoneViolation} (an "ours" violation when the writer label
 * starts with {@code ours:}); {@code getChunk} outside the read zone throws. Anything the method table does not model and
 * that is not an interface default throws {@link UnsupportedOperationException} naming the method.
 */
public final class SimLevel {
	private final SimWorld world;
	private final int ccx, ccz, readR, writeR;
	private final long seed;
	private final RandomSource random;
	private String writer;
	private final WorldGenLevel proxy;

	public SimLevel(SimWorld world, int cx, int cz, String writer, long seed) { this(world, cx, cz, 1, 1, writer, seed); }

	public SimLevel(SimWorld world, int cx, int cz, int readRadius, int writeRadius, String writer, long seed) {
		this.world = world; this.ccx = cx; this.ccz = cz; this.readR = readRadius; this.writeR = writeRadius;
		this.writer = writer; this.seed = seed;
		this.random = RandomSource.create(seed ^ (cx * 341873128712L) ^ (cz * 132897987541L));
		this.proxy = (WorldGenLevel) Proxy.newProxyInstance(SimLevel.class.getClassLoader(), new Class<?>[] {WorldGenLevel.class}, new Handler());
	}

	public WorldGenLevel proxy() { return proxy; }

	/** Change the label later writes are attributed to (e.g. {@code feature:oak@1,70,2}, {@code ours:3,4}). */
	public SimLevel writer(String label) { this.writer = label; return this; }

	public int centreX() { return ccx; }

	public int centreZ() { return ccz; }

	boolean inRead(int cx, int cz) { return Math.abs(cx - ccx) <= readR && Math.abs(cz - ccz) <= readR; }

	boolean inWrite(int cx, int cz) { return Math.abs(cx - ccx) <= writeR && Math.abs(cz - ccz) <= writeR; }

	private boolean ours() { return writer.startsWith("ours:"); }

	private ProtoChunk readChunk(int cx, int cz) {
		if (!inRead(cx, cz)) {
			world.violations.add(new SimWorld.ZoneViolation(cx << 4, 0, cz << 4, writer, false, ours()));
			throw new IllegalStateException("read of chunk " + cx + "," + cz + " outside the zone of " + ccx + "," + ccz + " (" + writer + ")");
		}
		return world.chunk(cx, cz);
	}

	private BlockState stateAt(BlockPos pos) {
		if (pos.getY() < SimWorld.MIN_Y || pos.getY() >= SimWorld.MIN_Y + SimWorld.HEIGHT) return net.minecraft.world.level.block.Blocks.VOID_AIR.defaultBlockState();
		return readChunk(pos.getX() >> 4, pos.getZ() >> 4).getBlockState(pos);
	}

	private boolean setBlock(BlockPos pos, BlockState state, int flags) {
		int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
		if (!inWrite(cx, cz)) {
			world.violations.add(new SimWorld.ZoneViolation(pos.getX(), pos.getY(), pos.getZ(), writer, true, ours()));
			return false;
		}
		if (pos.getY() < SimWorld.MIN_Y || pos.getY() >= SimWorld.MIN_Y + SimWorld.HEIGHT) return false;
		ProtoChunk chunk = world.chunk(cx, cz);
		BlockState old = chunk.setBlockState(pos, state, flags);
		if (old == null) return false;
		if (state.hasBlockEntity() && state.getBlock() instanceof EntityBlock eb) {
			BlockEntity be = eb.newBlockEntity(pos.immutable(), state);
			if (be != null) chunk.setBlockEntity(be);
		}
		if ((flags & 16) == 0) {
			BlockPos pp = state.getPostProcessPos(proxy, pos);
			if (pp != null) chunk.markPosForPostProcessing(pp);
		}
		if (world.logWrites) {
			String frame = null;
			if (world.audit) {
				frame = StackWalker.getInstance().walk(s -> s.map(f -> f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber())
					.filter(n -> !n.startsWith("dev.fractalbuildings.mod.ordersim.") && !n.startsWith("jdk.proxy") && !n.startsWith("net.minecraft.") && !n.startsWith("java."))
					.findFirst().orElse("?"));
			}
			world.writes.add(new SimWorld.Write(pos.getX(), pos.getY(), pos.getZ(), old, state, flags, writer, frame));
			world.lastWriter.put(SimWorld.key(pos.getX(), pos.getY(), pos.getZ()), writer);
		}
		return true;
	}

	private final class Handler implements InvocationHandler {
		@Override
		public Object invoke(Object p, Method m, Object[] a) throws Throwable {
			String name = m.getName();
			int n = a == null ? 0 : a.length;
			switch (name + "/" + n) {
				case "hashCode/0": return System.identityHashCode(p);
				case "equals/1": return p == a[0];
				case "toString/0": return "SimLevel[" + ccx + "," + ccz + " " + writer + "]";
				case "getBlockState/1": return stateAt((BlockPos) a[0]);
				case "getFluidState/1": return stateAt((BlockPos) a[0]).getFluidState();
				case "setBlock/3": return setBlock((BlockPos) a[0], (BlockState) a[1], (Integer) a[2]);
				case "setBlock/4": return setBlock((BlockPos) a[0], (BlockState) a[1], (Integer) a[2]);
				case "isStateAtPosition/2": return ((java.util.function.Predicate<BlockState>) a[1]).test(stateAt((BlockPos) a[0]));
				case "isFluidAtPosition/2": return ((java.util.function.Predicate<FluidState>) a[1]).test(stateAt((BlockPos) a[0]).getFluidState());
				case "getChunk/1": { BlockPos bp = (BlockPos) a[0]; return readChunk(bp.getX() >> 4, bp.getZ() >> 4); }
				case "getChunk/2": case "getChunk/3": case "getChunk/4": return readChunk((Integer) a[0], (Integer) a[1]);
				case "hasChunk/2": case "hasChunkAt/2": return inRead((Integer) a[0], (Integer) a[1]) && world.hasChunk((Integer) a[0], (Integer) a[1]);
				case "getHeight/3": { // top block + 1, like Level.getHeight
					Heightmap.Types t = (Heightmap.Types) a[0];
					int x = (Integer) a[1], z = (Integer) a[2];
					return readChunk(x >> 4, z >> 4).getHeight(t, x & 15, z & 15) + 1;
				}
				case "getHeightmapPos/2": {
					BlockPos bp = (BlockPos) a[1];
					return new BlockPos(bp.getX(), readChunk(bp.getX() >> 4, bp.getZ() >> 4).getHeight((Heightmap.Types) a[0], bp.getX() & 15, bp.getZ() & 15) + 1, bp.getZ());
				}
				case "getSeaLevel/0": return 63;
				case "getMinY/0": return SimWorld.MIN_Y;
				case "getMaxY/0": return SimWorld.MIN_Y + SimWorld.HEIGHT - 1;
				case "getHeight/0": return SimWorld.HEIGHT;
				case "isClientSide/0": return false;
				case "getSeed/0": return seed;
				case "getRandom/0": return random;
				case "getBiome/1": { BlockPos bp = (BlockPos) a[0]; return world.biomeAt(bp.getX(), bp.getZ()); }
				case "getBiome/3": return world.biomeAt((Integer) a[0], (Integer) a[2]);
				case "scheduleTick/3": case "scheduleTick/4": case "scheduleTick/5": return null;
				case "getBlockTicks/0": case "getFluidTicks/0": return BlackholeTickAccess.emptyLevelList();
				case "getBrightness/2": case "getRawBrightness/2": case "getRawBrightness/3": return 0;
				case "getBlockEntity/1": { BlockPos bp = (BlockPos) a[0]; return readChunk(bp.getX() >> 4, bp.getZ() >> 4).getBlockEntity(bp); }
				case "ensureCanWrite/1": { BlockPos bp = (BlockPos) a[0]; return inWrite(bp.getX() >> 4, bp.getZ() >> 4); }
				case "setCurrentlyGenerating/1": return null;
				default:
					if (m.isDefault()) return InvocationHandler.invokeDefault(p, m, a);
					throw new UnsupportedOperationException("SimLevel does not model " + m.getDeclaringClass().getSimpleName() + "." + name + "/" + n);
			}
		}
	}
}
