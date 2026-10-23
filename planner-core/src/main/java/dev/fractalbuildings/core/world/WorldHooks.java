package dev.fractalbuildings.core.world;

import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Hook;
import dev.fractalbuildings.core.ir.PlacedBuilding;
import dev.fractalbuildings.core.ir.Placement;
import dev.fractalbuildings.core.rand.Hash64;
import dev.fractalbuildings.core.settlement.Tier;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * The furnishing hooks of placed buildings in world coordinates, and the rules the game applies to them (plan B5): which beds get
 * a villager. Pure: a function of the pieces, so the mod and the tests agree, and nothing depends on the order chunks generate in.
 */
public final class WorldHooks {
	/** Most villagers one district tile gets (plan decision 2). */
	public static final int MAX_VILLAGERS_PER_DISTRICT = 10;

	private WorldHooks() {}

	/**
	 * One hook in the world. {@code facing} is the world direction the furniture faces; for a bed it is the direction from the foot
	 * (the hook cell) to the head.
	 */
	public record WorldHook(String id, String kind, String room, int x, int y, int z, Facing facing, String buildingType, String style, String lotId) {
		/** The cell of a bed's head. */
		public int headX() { return x + facing.dx; }

		public int headZ() { return z + facing.dz; }

		/** Both cells of a bed lie in one chunk (a bed across a chunk edge is never placed, so no piece writes into another chunk). */
		public boolean bedInOneChunk() { return (x >> 4) == (headX() >> 4) && (z >> 4) == (headZ() >> 4); }

		/** The seed of anything random about this hook (loot, colours, which workstation): the world seed and the position, never a shared RNG. */
		public long seed(long worldSeed, String purpose) { return Hash64.hash(worldSeed, purpose, x, y, z); }
	}

	/** The hooks of one building piece in world coordinates, in the shell's hook order. */
	public static List<WorldHook> of(BuildingPiece piece) {
		PlacedBuilding b = piece.building();
		Placement pl = b.placement();
		List<WorldHook> out = new ArrayList<>();
		for (Hook h : b.shell().hooks()) {
			out.add(new WorldHook(h.id(), h.kind(), h.room(), pl.worldX(h.x(), h.z()), pl.worldY(h.y()), pl.worldZ(h.x(), h.z()), pl.toWorld(h.facing()),
					piece.brief().type(), piece.brief().style(), piece.brief().id()));
		}
		return out;
	}

	/** Tiers whose settlements get villagers (plan decision 2: beds and job sites everywhere, people only in the small ones). */
	public static boolean hasVillagers(Tier t) { return t == Tier.HAMLET || t == Tier.VILLAGE; }

	/**
	 * The beds of a tile that get a villager: at most {@code max}, the {@code max} placeable beds with the smallest hash of their
	 * position, so the pick does not depend on lot order or on which chunk generates first. Keys are {@code x, y, z} packed by {@link #key}.
	 */
	public static java.util.Set<Long> villagerBeds(List<BuildingPiece> tile, long worldSeed, int max) {
		TreeMap<Long, Long> byHash = new TreeMap<>(); // hash -> key; a hash collision keeps the lower key, which is still deterministic
		for (BuildingPiece p : tile) {
			for (WorldHook h : of(p)) {
				if (!h.kind().equals("bed")) continue;
				long k = key(h.x(), h.y(), h.z());
				byHash.merge(h.seed(worldSeed, "villager"), k, Math::min);
			}
		}
		java.util.TreeSet<Long> out = new java.util.TreeSet<>();
		for (long k : byHash.values()) {
			if (out.size() >= max) break;
			out.add(k);
		}
		return out;
	}

	public static long key(int x, int y, int z) { return ((long) (x + (1 << 25)) << 38) | ((long) (z + (1 << 25)) << 12) | (y + 2048); }
}
