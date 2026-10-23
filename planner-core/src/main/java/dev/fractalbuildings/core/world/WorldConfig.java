package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.gen.v1.GeneratorV1;
import java.util.Set;
import java.util.TreeSet;

/**
 * The pinned, per-world settings of a {@link WorldPlanner}. They are part of the output: the same seed with another config is another
 * world, so a game saves them with the world (plan B6).
 *
 * @param cellSize edge of the region field's hashed cells, blocks (ADR 0003: 1024)
 * @param frequency multiplies the region planner's base chance of a settlement per cell; 0 gives none
 * @param maxRadius largest settlement radius a metropolis may draw, blocks
 * @param styles the enabled style ids; empty means all of them. A lot whose style is disabled is built as {@code plain}
 * @param genVersion the generator version the world was created with (carried in plans; the planner builds with the current one)
 * @param intercityRoads whether settlements are linked by roads (ADR 0004). Off: no road graph, no approaches, no road feature
 */
public record WorldConfig(int cellSize, double frequency, int maxRadius, Set<String> styles, int genVersion, boolean intercityRoads) {
	public static final int DEFAULT_CELL_SIZE = 1024;

	public WorldConfig(int cellSize, double frequency, int maxRadius, Set<String> styles, int genVersion) {
		this(cellSize, frequency, maxRadius, styles, genVersion, true);
	}

	public WorldConfig {
		if (cellSize < 256 || cellSize > 65536) throw new IllegalArgumentException("cellSize " + cellSize);
		if (frequency < 0 || Double.isNaN(frequency)) throw new IllegalArgumentException("frequency " + frequency);
		if (maxRadius <= 0) throw new IllegalArgumentException("maxRadius " + maxRadius);
		styles = java.util.Collections.unmodifiableSet(new TreeSet<>(styles)); // sorted: iteration order never matters
	}

	/** The defaults: ADR 0003's cell size, the region planner's own base chance and radius cap, every style, the current generator. */
	public static WorldConfig defaults() {
		return new WorldConfig(DEFAULT_CELL_SIZE, 1.0, dev.fractalbuildings.core.region.Tiers.DEFAULT_MAX_RADIUS, Set.of(), GeneratorV1.GEN_VERSION);
	}

	/** The same config with the road switch set (test fixtures that pin a settlement's layout switch the roads off). */
	public WorldConfig withIntercityRoads(boolean on) { return new WorldConfig(cellSize, frequency, maxRadius, styles, genVersion, on); }

	public boolean styleEnabled(String style) { return styles.isEmpty() || styles.contains(style); }
}
