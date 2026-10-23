package dev.fractalbuildings.gametest.order;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import dev.fractalbuildings.mod.worldgen.PieceLedger;

/** Reads the hook blocks (B5: beds, chests, workstations, lights) back from the world: each is what the ledger says, every bed has both halves, every chest a chest block entity. */
public final class HookReadback {
	private HookReadback() {}

	public record Result(int hooks, int hooksBad, int beds, int bedsBad, int chests, int chestsBad, int otherBad, String first) {
		public static final Result NONE = new Result(0, 0, 0, 0, 0, 0, 0, "");

		public Result plus(Result o) { return new Result(hooks + o.hooks, hooksBad + o.hooksBad, beds + o.beds, bedsBad + o.bedsBad, chests + o.chests, chestsBad + o.chestsBad, otherBad + o.otherBad, first.isEmpty() ? o.first : first); }

		/** Chests and every other hook exactly right; beds with at most {@code bedLossLimit} of their halves missing (a known B5 defect, see the B2 r2 log). */
		public boolean ok(double bedLossLimit) { return otherBad == 0 && chestsBad == 0 && bedsBad <= beds * bedLossLimit; }

		public boolean ok() { return ok(0); }

		@Override public String toString() { return "hooks " + hooks + " bad " + hooksBad + ", beds " + beds + " bad " + bedsBad + ", chests " + chests + " bad " + chestsBad + (first.isEmpty() ? "" : ", first " + first); }
	}

	public static Result check(ServerLevel level, Map<Long, Integer> hooks) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(), q = new BlockPos.MutableBlockPos();
		int n = 0, bad = 0, beds = 0, bedsBad = 0, chests = 0, chestsBad = 0, otherBad = 0;
		String first = "";
		for (var e : hooks.entrySet()) {
			long k = e.getKey();
			BlockState want = Block.BLOCK_STATE_REGISTRY.byId(e.getValue());
			int x = PieceLedger.x(k), y = PieceLedger.y(k), z = PieceLedger.z(k);
			BlockState have = level.getBlockState(p.set(x, y, z));
			n++;
			boolean ok = have == want;
			if (want.getBlock() instanceof BedBlock) {
				beds++;
				Direction f = want.getValue(BedBlock.FACING);
				boolean foot = want.getValue(BedBlock.PART) == BedPart.FOOT;
				int ox = x + (foot ? f.getStepX() : -f.getStepX()), oz = z + (foot ? f.getStepZ() : -f.getStepZ());
				BlockState other = level.getBlockState(q.set(ox, y, oz));
				boolean both = other.is(want.getBlock()) && other.getValue(BedBlock.FACING) == f && other.getValue(BedBlock.PART) == (foot ? BedPart.HEAD : BedPart.FOOT);
				if (!ok || !both) { bedsBad++; if (first.isEmpty()) first = "bed " + x + "," + y + "," + z + " want " + want + " have " + have + " other half " + other + " (other half in the ledger: " + hooks.containsKey(PieceLedger.pack(ox, y, oz)) + ", chunks " + (x >> 4) + "," + (z >> 4) + " and " + (ox >> 4) + "," + (oz >> 4) + ")"; }
			} else if (want.getBlock() instanceof ChestBlock) {
				chests++;
				if (!ok || !(level.getBlockEntity(p) instanceof ChestBlockEntity)) chestsBad++;
			}
			if (!ok) {
				bad++;
				if (!(want.getBlock() instanceof BedBlock)) otherBad++;
				if (first.isEmpty()) first = x + "," + y + "," + z + " want " + want + " have " + have;
			}
		}
		return new Result(n, bad, beds, bedsBad, chests, chestsBad, otherBad, first);
	}
}
