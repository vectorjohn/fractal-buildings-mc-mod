package dev.fractalbuildings.mod.teleport;

import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Holds a nether-portal trip until the exit search will not block the server thread (6.30 PORTALCOLD, option c). Vanilla's
 * {@code NetherPortalBlock.getPortalDestination} runs on the server thread: with no portal in range, {@code PortalForcer.createPortal} reads
 * heights and blocks over a 33 x 33 block spiral round the scaled exit, and every chunk it touches is generated to FULL synchronously (with
 * its 11-chunk dependency ring: 625 chunks for a 3 x 3 search), which froze the whole server for 37 s (town) to 65 s (metropolis) in the
 * PORTALCOLD measurement.
 *
 * <p>{@code EntityPortalHoldMixin} asks {@link #gate} each tick an entity is inside a nether portal. The first time, the hold works out what
 * vanilla's call will touch and requests exactly that asynchronously (chunk tickets plus generation tasks), and the entity stays in the
 * portal (no portal cooldown is set, so vanilla retries every tick) until it is ready; then vanilla's own call runs unchanged:
 * <ul>
 *   <li>First the chunks of the POI search (radius 128 blocks in the overworld: 17 x 17 chunks; 16 in the nether: 3 x 3) are loaded to
 *       status EMPTY (read from disk or created empty, nothing generated); then vanilla's own {@code PoiManager.ensureLoadedAndValid}
 *       runs (no longer waiting on anything) and the POI query decides which of the two cases below vanilla will take.</li>
 *   <li>An existing portal in range: only its chunk and the chunks round it that {@code getLargestRectangleAround} reads are needed, at
 *       FULL (normally a disk load).</li>
 *   <li>No portal: the chunks the spiral and the frame checks read ({@link #searchChunks}) are generated to {@code INITIALIZE_LIGHT}, with
 *       one more ring at the same status (what their LIGHT step needs). That is everything a FULL chunk depends on except its last steps
 *       (LIGHT, SPAWN, FULL), which vanilla's call then runs on the server thread (a fraction of a second: lighting 9 to 16 chunks). Holding them one status short of FULL
 *       keeps their ticket level at 34, so {@code Level.hasChunk} still says false for them, as in a cold world: {@code Level.getHeight}
 *       returns the minimum height for a chunk without a FULL-level ticket, and vanilla's search depends on that (the first column it
 *       reads in each cold chunk). With the hold the exit portal therefore comes out where the unheld call would have put it.</li>
 * </ul>
 * Nothing else is waited for: not the player's view distance, not the arrival chunks, no planner warming (John 2026-10-31: "I want to wait
 * the minimum, even if that means the player sees loading chunks. I just want to avoid the possible server crash.").
 *
 * <p>Every entity is held, not only players (a mob or item would freeze the server the same way); players get the action-bar message.
 * Spectators are not held (vanilla never creates a portal for them). An entity that leaves the portal ends its wait; the request is dropped
 * after {@link #IDLE_TICKS} without anyone inside. After {@link #maxHoldMillis} the trip proceeds anyway (the rest then generates
 * synchronously, as without the hold). The end portal is not held (its exits are fixed: the End platform or the spawn point).
 *
 * <p>Timing only: the destination is vanilla's, computed by vanilla from the entity's position when it finally goes. Server-only. Off with
 * {@code -Dfractalbuildings.portalHold=false}.
 */
public final class PortalHold {
	/** Keeps the requested chunks loaded while the hold waits; removed by hand, expires as a safety net after 150 s. */
	public static final TicketType TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE,
		Identifier.fromNamespaceAndPath(FractalBuildingsMod.MOD_ID, "portal_hold"), new TicketType(20L * 150, TicketType.FLAG_LOADING));

	/**
	 * The default of {@link #maxHoldMillis}: 120 s. Proceeding earlier does not save the wait, it moves the rest of it onto the server thread
	 * (the freeze the hold exists to avoid; TPHOLD15 saw the same after its 15 s limit), and the player can walk out of the portal at any time.
	 * So the cap is only a safety net against a request that never completes, above the 65 s worst case measured.
	 */
	public static final long DEFAULT_MAX_HOLD_MILLIS = 120_000;
	/** Longest wait, milliseconds of wall clock from the request, before the trip proceeds anyway. */
	public static volatile long maxHoldMillis = Long.getLong("fractalbuildings.portalHoldMaxMillis", DEFAULT_MAX_HOLD_MILLIS);
	public static volatile boolean enabled = !"false".equals(System.getProperty("fractalbuildings.portalHold"));
	/** A request nobody has touched for this many ticks (everyone walked out) is dropped with its tickets. */
	static final int IDLE_TICKS = 100;
	/** Test hook: the idle limit of requests opened from now on (null: {@link #IDLE_TICKS}); each request keeps the value it opened with. */
	public static volatile Integer testIdleTicks = null;
	/** {@code BlockUtil.getLargestRectangleAround} reaches 21 blocks from an existing portal block. */
	static final int RECTANGLE = 21;
	/** Ticket level of a chunk held one status short of FULL: INITIALIZE_LIGHT, 34 (no FULL-level ticket, so {@code hasChunk} stays false). */
	static final ChunkStatus PREPARED = ChunkStatus.INITIALIZE_LIGHT;

	/** What the last hold did (the harness and tests read it): poi ms, chunks, status, wait ms, ticks. */
	public static volatile String lastStats = "";

	private record Key(ResourceKey<Level> dim, int x, int z) {}

	/** One batch of chunks requested at one status, with one ticket each (ticket level = the status's level). */
	private static final class Request {
		final ChunkStatus status;
		final int ticketLevel;
		final long[] chunks;
		final CompletableFuture<?>[] futures;

		Request(ChunkStatus status, long[] chunks) {
			this.status = status;
			this.ticketLevel = ChunkLevel.byStatus(status);
			this.chunks = chunks;
			this.futures = new CompletableFuture<?>[chunks.length];
		}

		/** Radius argument of the ServerChunkCache ticket calls for our level (level = 33 - radius). */
		int radiusArg() { return ChunkLevel.byStatus(FullChunkStatus.FULL) - ticketLevel; }
	}

	private static final class Area {
		final ServerLevel level;
		final BlockPos exit;
		final boolean axisX;
		final long started = System.nanoTime();
		final long deadline = started + maxHoldMillis * 1_000_000L;
		final int startTick;
		final int idleTicks = testIdleTicks != null ? testIdleTicks : IDLE_TICKS;
		/** First the POI chunks at EMPTY, then (once searched) what the rest of vanilla's call reads. */
		final List<Request> requests = new ArrayList<>();
		boolean searched;
		double poiMillis;
		int portals = -1;
		int lastTouch;
		boolean released;

		Area(ServerLevel level, BlockPos exit, boolean axisX, int tick) {
			this.level = level;
			this.exit = exit;
			this.axisX = axisX;
			this.startTick = tick;
			this.lastTouch = tick;
		}

		Request current() { return requests.get(requests.size() - 1); }
	}

	private static final class Waiter {
		final ServerPlayer player;
		int lastTouch, lastMessage = Integer.MIN_VALUE / 2;
		Waiter(ServerPlayer player) { this.player = player; }
	}

	private static final Map<Key, Area> AREAS = new LinkedHashMap<>();
	private static final Map<UUID, Waiter> WAITERS = new LinkedHashMap<>();
	/** Our tickets per (dimension, ticket level, chunk), counted: overlapping requests share one vanilla ticket (same type and level). */
	private static final Map<ResourceKey<Level>, Map<Ref, int[]>> REFS = new HashMap<>();

	private PortalHold() {}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PortalHold::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> { AREAS.clear(); WAITERS.clear(); REFS.clear(); });
	}

	/**
	 * Called from {@code Entity.handlePortal} with the result of {@code PortalProcessor.processPortalTeleportation} ({@code go}); returns
	 * what vanilla should do with it. {@code inside}: the entity was in the portal this tick; {@code allowed}: {@code canUsePortal(false)}.
	 * Starts the request as soon as a player stands in the portal (during vanilla's own 4 s survival wait), defers the trip while it is not
	 * ready. Server thread only.
	 */
	public static boolean gate(ServerLevel from, Entity e, boolean netherPortal, BlockPos entry, boolean inside, boolean allowed, boolean go) {
		if (!enabled || !inside || !allowed || !netherPortal || e.isSpectator()) return go;
		if (!go && !(e instanceof ServerPlayer)) return false;
		MinecraftServer server = from.getServer();
		ServerLevel to = server.getLevel(from.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER);
		if (to == null || !from.isAllowedToEnterPortal(to)) return go;
		BlockPos exit = exitPos(from, to, e);
		Key key = new Key(to.dimension(), exit.getX(), exit.getZ());
		int now = server.getTickCount();
		Area a = AREAS.get(key);
		if (a == null || a.released) {
			if (a != null) { // released earlier this tick for another entity: its tickets go now, before the map forgets it
				AREAS.remove(key);
				drop(a);
			}
			boolean axisX = from.getBlockState(entry).getOptionalValue(NetherPortalBlock.AXIS).orElse(Direction.Axis.X) == Direction.Axis.X;
			if (allFull(to, searchChunks(exit.getX(), exit.getZ(), axisX))) return go; // a warm area: vanilla's call is quick already
			a = open(to, exit, axisX, now);
			AREAS.put(key, a);
		}
		a.lastTouch = now;
		if (!go) return false;
		progress(a);
		boolean ready = ready(a);
		boolean timedOut = !ready && System.nanoTime() - a.deadline >= 0;
		if (!ready && !timedOut) {
			if (e instanceof ServerPlayer p) {
				Waiter w = WAITERS.computeIfAbsent(p.getUUID(), id -> new Waiter(p));
				w.lastTouch = now;
				if (now - w.lastMessage >= 20) {
					w.lastMessage = now;
					p.sendOverlayMessage(TeleportHold.message());
				}
			}
			return false;
		}
		long ms = (System.nanoTime() - a.started) / 1_000_000L;
		Request q = a.current();
		lastStats = String.format(java.util.Locale.ROOT, "poiChunks=%d poiMs=%.1f portals=%d chunks=%d status=%s waitMs=%d waitTicks=%d timedOut=%s",
			a.requests.get(0).chunks.length, a.poiMillis, a.portals, a.searched ? q.chunks.length : 0, a.searched ? q.status.getName() : "-", ms, now - a.startTick, timedOut);
		if (timedOut) FractalBuildingsMod.LOGGER.warn("Portal hold at {} timed out after {} ms; going anyway ({})", a.exit.toShortString(), ms, lastStats);
		else FractalBuildingsMod.LOGGER.info("Portal hold for {} at {} ready ({})", e.getName().getString(), a.exit.toShortString(), lastStats);
		a.released = true; // tickets go at the end of this tick; vanilla's call below makes the chunks it needs FULL with its own tickets
		if (e instanceof ServerPlayer p && WAITERS.remove(p.getUUID()) != null) p.sendOverlayMessage(Component.empty());
		return true;
	}

	/**
	 * After a portal trip that changed the dimension, drops the entity's {@code mainSupportingBlockPos}. A player keeps its {@code Entity}
	 * object across dimensions, and vanilla (26.3) leaves that field at the block it stood on in the old dimension; in the same tick
	 * {@code LivingEntity.travel} reads it through {@code getOnPos} (Entity.java:1110-1117) in the new level, a block at the old
	 * coordinates (the exit / 8 or x 8): usually a cold chunk, generated synchronously. Seen in {@code PortalHoldClientGameTest}: an 11 to 15 s
	 * tick right after the held trip, stack in {@code Player.travel}. The field is a cache the next grounded move refills. Other entities are
	 * recreated in the new level and start without it.
	 */
	public static void afterTrip(Entity e, ServerLevel from) {
		if (enabled && e.level() != from && !e.isRemoved()) e.mainSupportingBlockPos = java.util.Optional.empty();
	}

	/** Vanilla's scaled exit point ({@code NetherPortalBlock.getPortalDestination}, lines 132-135 in 26.3). */
	static BlockPos exitPos(ServerLevel from, ServerLevel to, Entity e) {
		WorldBorder border = to.getWorldBorder();
		double scale = DimensionType.getTeleportationScale(from.dimensionType(), to.dimensionType());
		return border.clampToBounds(e.getX() * scale, e.getY(), e.getZ() * scale);
	}

	/** The chunks vanilla's createPortal reads for an exit at block (x, z): {@link PortalSearch#chunks}. */
	public static int[] searchChunks(int x, int z, boolean axisX) { return PortalSearch.chunks(x, z, axisX); }

	private static boolean allFull(ServerLevel level, int[] r) {
		for (int cz = r[2]; cz <= r[3]; cz++) for (int cx = r[0]; cx <= r[1]; cx++) if (level.getChunkSource().getChunkNow(cx, cz) == null) return false;
		return true;
	}

	/** Opens a request: first the chunks of vanilla's POI search at status EMPTY (loaded from disk or created empty, nothing generated). */
	private static Area open(ServerLevel to, BlockPos exit, boolean axisX, int now) {
		Area a = new Area(to, exit, axisX, now);
		int r = Math.floorDiv(poiRadius(to), 16); // PoiManager.ensureLoadedAndValid: SectionPos.aroundChunk(exit chunk, radius / 16)
		int ecx = exit.getX() >> 4, ecz = exit.getZ() >> 4;
		List<Long> chunks = new ArrayList<>();
		for (int cz = ecz - r; cz <= ecz + r; cz++) for (int cx = ecx - r; cx <= ecx + r; cx++) addChunk(chunks, cx, cz);
		request(a, ChunkStatus.EMPTY, chunks);
		return a;
	}

	/** PortalForcer.NETHER_PORTAL_RADIUS / OVERWORLD_PORTAL_RADIUS: the POI search radius in the target level. */
	private static int poiRadius(ServerLevel to) { return to.dimension() == Level.NETHER ? 16 : 128; }

	/** Advances a request: starts generation tasks, and once the POI chunks are in, runs the search and requests the rest. */
	private static void progress(Area a) {
		poll(a.level, a.current());
		if (a.searched || !done(a.current())) return;
		a.searched = true;
		ServerLevel to = a.level;
		int radius = poiRadius(to);
		long t0 = System.nanoTime();
		PoiManager poi = to.getPoiManager();
		poi.ensureLoadedAndValid(to, a.exit, radius); // vanilla's own first step; its chunks are loaded now, so it does not wait
		WorldBorder border = to.getWorldBorder();
		List<BlockPos> portals = poi.getInSquare(t -> t.is(PoiTypes.NETHER_PORTAL), a.exit, radius, PoiManager.Occupancy.ANY)
			.map(PoiRecord::getPos).filter(border::isWithinBounds).toList();
		a.poiMillis = (System.nanoTime() - t0) / 1e6;
		a.portals = portals.size();
		List<Long> chunks = new ArrayList<>();
		if (!portals.isEmpty()) { // the found portal's blocks are read (getBlockState): its chunks at FULL, normally a disk load
			BlockPos best = null;
			for (BlockPos p : portals) {
				addChunk(chunks, p.getX() >> 4, p.getZ() >> 4);
				if (best == null || p.distSqr(a.exit) < best.distSqr(a.exit) || p.distSqr(a.exit) == best.distSqr(a.exit) && p.getY() < best.getY()) best = p;
			}
			for (int cz = (best.getZ() - RECTANGLE) >> 4; cz <= (best.getZ() + RECTANGLE) >> 4; cz++)
				for (int cx = (best.getX() - RECTANGLE) >> 4; cx <= (best.getX() + RECTANGLE) >> 4; cx++) addChunk(chunks, cx, cz);
			request(a, ChunkStatus.FULL, chunks);
		} else { // createPortal: the search chunks and one ring, one status short of FULL
			int[] r = searchChunks(a.exit.getX(), a.exit.getZ(), a.axisX);
			for (int cz = r[2] - 1; cz <= r[3] + 1; cz++) for (int cx = r[0] - 1; cx <= r[1] + 1; cx++) addChunk(chunks, cx, cz);
			request(a, PREPARED, chunks);
		}
		poll(a.level, a.current());
	}

	private static void request(Area a, ChunkStatus status, List<Long> chunks) {
		Request q = new Request(status, chunks.stream().mapToLong(Long::longValue).toArray());
		a.requests.add(q);
		Map<Ref, int[]> refs = REFS.computeIfAbsent(a.level.dimension(), k -> new HashMap<>());
		for (long c : q.chunks) {
			int[] n = refs.computeIfAbsent(new Ref(c, q.ticketLevel), k -> new int[1]);
			if (n[0]++ == 0) a.level.getChunkSource().addTicketWithRadius(TICKET, ChunkPos.unpack(c), q.radiusArg());
		}
		poll(a.level, q);
	}

	private static void addChunk(List<Long> out, int cx, int cz) {
		long k = ChunkPos.pack(cx, cz);
		if (!out.contains(k)) out.add(k);
	}

	private record Ref(long chunk, int level) {}

	/** Starts the generation task of every requested chunk whose holder has our ticket level by now (a ticket is processed in the next chunk tick). */
	private static void poll(ServerLevel level, Request q) {
		var chunkMap = level.getChunkSource().chunkMap;
		for (int i = 0; i < q.chunks.length; i++) {
			if (q.futures[i] != null) continue;
			ChunkHolder h = chunkMap.getUpdatingChunkIfPresent(q.chunks[i]);
			if (h != null && h.getTicketLevel() <= q.ticketLevel) q.futures[i] = h.scheduleChunkGenerationTask(q.status, chunkMap);
		}
	}

	private static boolean done(Request q) {
		for (CompletableFuture<?> f : q.futures) if (f == null || !f.isDone()) return false;
		return true;
	}

	private static boolean ready(Area a) { return a.searched && done(a.current()); }

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (!WAITERS.isEmpty()) {
			for (var it = WAITERS.values().iterator(); it.hasNext();) {
				Waiter w = it.next();
				if (now - w.lastTouch > 2) { // walked out (or went): clear the message
					it.remove();
					if (!w.player.hasDisconnected()) w.player.sendOverlayMessage(Component.empty());
				}
			}
		}
		if (AREAS.isEmpty()) return;
		for (var it = AREAS.values().iterator(); it.hasNext();) {
			Area a = it.next();
			if (a.released || now - a.lastTouch > a.idleTicks) {
				it.remove();
				drop(a);
			} else {
				progress(a);
			}
		}
	}

	private static void drop(Area a) {
		Map<Ref, int[]> refs = REFS.get(a.level.dimension());
		if (refs == null) return;
		for (Request q : a.requests) {
			for (long c : q.chunks) {
				Ref k = new Ref(c, q.ticketLevel);
				int[] n = refs.get(k);
				if (n == null) continue;
				if (--n[0] <= 0) {
					refs.remove(k);
					a.level.getChunkSource().removeTicketWithRadius(TICKET, ChunkPos.unpack(c), q.radiusArg());
				}
			}
		}
	}

	/** True while a request for some exit is open (tests). */
	public static boolean waiting() { return !AREAS.isEmpty(); }

	/** True while a request for the exit at block column (x, z) of that level is open (tests). */
	public static boolean waitingFor(ResourceKey<Level> dim, int x, int z) {
		Area a = AREAS.get(new Key(dim, x, z));
		return a != null && !a.released;
	}

	/** True while this player is being told to wait (tests). */
	public static boolean holding(ServerPlayer p) { return WAITERS.containsKey(p.getUUID()); }
}
