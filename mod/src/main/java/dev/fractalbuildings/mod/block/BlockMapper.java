package dev.fractalbuildings.mod.block;

import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.raster.Shape;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps a palette block id plus A3's {@link Shape} to a Minecraft block state. A pure function of its inputs
 * (cached). Properties are applied by name and value string, so the mapping covers doors, stairs, slabs, logs,
 * panes, fences and walls without per-class code, and ignores properties a block does not have.
 * Unknown ids become stone with one warning per id.
 */
public final class BlockMapper {
	private static final Logger LOG = LoggerFactory.getLogger("fractalbuildings/blocks");
	private static final Map<String, BlockState> CACHE = new ConcurrentHashMap<>();
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	private BlockMapper() {
	}

	/** Role-aware mapping: a {@link Role#STAIR} cell with a facing becomes the material's stair block. */
	public static BlockState state(Role role, String blockId, Shape shape) {
		if (role.isRoofStair() && shape.facing() != null) {
			Optional<String> stair = ShapeTable.stair(blockId);
			if (stair.isPresent()) return state(stair.get(), shape);
			if (WARNED.add("nostair:" + blockId)) LOG.warn("no stair variant for {}, keeping the full block", blockId);
			return state(blockId, Shape.NONE);
		}
		if (role == Role.ROOF_SLAB || role == Role.ROOF_SLAB_TOP) {
			Optional<String> slab = ShapeTable.slab(blockId);
			if (slab.isPresent()) return state(slab.get(), shape);
			if (WARNED.add("noslab:" + blockId)) LOG.warn("no slab variant for {}, keeping the full block", blockId);
			return state(blockId, Shape.NONE);
		}
		if (role == Role.STAIR && shape.facing() != null) {
			Optional<String> stair = ShapeTable.stair(blockId);
			if (stair.isPresent()) return state(stair.get(), shape.half() == null ? Shape.stair(shape.facing()) : shape);
			if (WARNED.add("nostair:" + blockId)) LOG.warn("no stair variant for {}, keeping the full block", blockId);
		}
		return state(blockId, role == Role.STAIR ? Shape.NONE : shape);
	}

	public static BlockState state(String blockId, Shape shape) {
		return CACHE.computeIfAbsent(blockId + "|" + shape, k -> build(blockId, shape));
	}

	private static BlockState build(String blockId, Shape shape) {
		Optional<Block> b = ShapeTable.block(blockId);
		if (b.isEmpty()) {
			if (WARNED.add(blockId)) LOG.warn("unknown block id '{}' in a palette, using stone", blockId);
			return Blocks.STONE.defaultBlockState();
		}
		BlockState s = b.get().defaultBlockState();
		for (Property<?> p : s.getBlock().getStateDefinition().getProperties()) {
			String v = valueFor(p.getName(), shape);
			if (v != null) s = with(s, p, v);
		}
		return s;
	}

	private static String lower(Enum<?> e) { return e == null ? null : e.name().toLowerCase(java.util.Locale.ROOT); }

	private static String valueFor(String prop, Shape sh) {
		switch (prop) {
			case "facing": return sh.facing() == null ? null : sh.facing().json();
			case "half": case "type": return lower(sh.half());
			case "hinge": return lower(sh.hinge());
			case "shape": return lower(sh.corner());
			case "axis": return lower(sh.axis());
			case "north": return side(sh, Shape.N);
			case "east": return side(sh, Shape.E);
			case "south": return side(sh, Shape.S);
			case "west": return side(sh, Shape.W);
			case "up": { // walls: a post unless the wall runs straight through
				if (sh.connect() == 0) return null;
				int c = sh.connect();
				boolean straight = c == (Shape.N | Shape.S) || c == (Shape.E | Shape.W);
				return String.valueOf(!straight);
			}
			default: return null;
		}
	}

	/** Boolean sides (panes, fences) take true/false; wall sides take low/none. */
	private static String side(Shape sh, int bit) { return (sh.connect() & bit) != 0 ? "true" : "false"; }

	private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, String v) {
		Optional<T> val = p.getValue(v);
		if (val.isEmpty() && (v.equals("true") || v.equals("false"))) val = p.getValue(v.equals("true") ? "low" : "none");
		if (val.isEmpty()) val = p.getValue(switch (v) { case "lower" -> "bottom"; case "upper" -> "top"; case "bottom" -> "lower"; case "top" -> "upper"; default -> v; });
		return val.isPresent() ? s.setValue(p, val.get()) : s;
	}
}
