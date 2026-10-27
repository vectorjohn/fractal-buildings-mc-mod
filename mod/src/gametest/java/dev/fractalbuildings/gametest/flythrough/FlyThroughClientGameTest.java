package dev.fractalbuildings.gametest.flythrough;

import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.GroundPiece;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import dev.fractalbuildings.mod.probe.LiveProbes;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/**
 * B9 (6.8): a fly-through of fixed seeds. For each seed one world; for each tier (hamlet, village, town, city, metropolis) the nearest
 * settlement of exactly that tier within {@code FB_FLY_CELLS} region cells of the origin (missing tiers are logged, not an error); six
 * frames per settlement: 1 approach (150 blocks out, from the origin's side), 2 overview from above, 3 street at eye height along the
 * longest road piece of the centre tile, 4 interior (just inside a door whose far side has a ceiling), 5 slope (steepest planned tile
 * near the centre, seen from its low side), 6 edge (outside the settlement, looking in). Camera heights come from the exact terrain
 * probe (or the loaded heightmap for street and interior) plus a margin, so no frame starts under the ground; the HUD and chat are hidden.
 *
 * <p>Opt-in: runs only with {@code FB_FLY} set ({@code FB_FLY=only} also skips the other client tests). Settings by environment:
 * {@code FB_FLY_SEEDS} (default 987654321,12345,1), {@code FB_FLY_OUT} (JPEG folder, default {@code mod/build/flythrough} under the run dir's
 * project), {@code FB_FLY_RD} (render distance, default 10), {@code FB_FLY_CELLS} (search radius in cells, default 6). Use
 * {@code scripts/flythrough.sh}. Per-frame checks: sky fraction below 0.9 and no single colour over 80% of the frame (a camera inside
 * the ground fills the frame); frames are kept either way and failures reported at the end.
 */
// gametest-order: 8
public class FlyThroughClientGameTest implements FabricClientGameTest {
	static final Tier[] TIERS = {Tier.HAMLET, Tier.VILLAGE, Tier.TOWN, Tier.CITY, Tier.METROPOLIS};
	/** Camera ceiling: the cloud layer is at about 192 and turns a frame white. */
	static final double CLOUDS = 175;

	public static boolean enabled() { return System.getenv("FB_FLY") != null; }

	/** The other client tests return at once in a fly-only run. */
	public static boolean skipOthers() { return "only".equals(System.getenv("FB_FLY")); }

	/** F1: no hotbar, no chat, no crosshair in the frames. */
	static void hideHud(net.minecraft.client.Minecraft client) { if (!client.gui.hud.isHidden()) client.gui.hud.toggle(); }

	private static String env(String k, String d) { String v = System.getenv(k); return v == null || v.isBlank() ? d : v; }

	record Frame(String label, double x, double y, double z, double tx, double ty, double tz, int settle) {}

	/** @param tag set for a synthetic set of frames that belongs to no settlement (the asphalt comparison): the name used for its tier and id */
	record Prepared(String seed, SettlementSite site, List<Frame> frames, List<String> notes, String tag) {
		Prepared(String seed, SettlementSite site, List<Frame> frames, List<String> notes) { this(seed, site, frames, notes, null); }
		String tier() { return tag != null ? tag : site.tier().json; }
		String id() { return tag != null ? tag : site.id(); }
		String centre() { return tag != null ? "0,0" : site.centre().x() + "," + site.centre().z(); }
	}

	/**
	 * Waits {@code ticks}, then polls (10-tick steps, up to {@code 8 * ticks} more) until the client holds the chunks under the camera, a point
	 * 60% of the way to the target and the target, and the renderer has every section (the renderer alone says "done" before the chunks of a far
	 * teleport have arrived); a cap only logs.
	 */
	static void settle(ClientGameTestContext context, Frame f) {
		context.waitTicks(f.settle());
		int[][] pts = {{(int) f.x(), (int) f.z()}, {(int) (f.x() + 0.6 * (f.tx() - f.x())), (int) (f.z() + 0.6 * (f.tz() - f.z()))}, {(int) f.tx(), (int) f.tz()}};
		int cap = 8 * f.settle();
		for (int waited = 0; ; waited += 10) {
			boolean[] done = {false};
			context.runOnClient(c -> {
				boolean all = c.levelRenderer.hasRenderedAllSections();
				for (int[] p : pts) all &= c.level.getChunkSource().hasChunk(p[0] >> 4, p[1] >> 4);
				done[0] = all;
			});
			if (done[0]) break;
			if (waited >= cap) { FractalBuildingsMod.LOGGER.warn("[B9] chunks or sections still missing after {} ticks; taking the screenshot anyway", f.settle() + cap); break; }
			context.waitTicks(10);
		}
		context.waitTicks(20);
	}

	/** Per-cell mean colours of a frame (48 x 27 grid), then its sky fraction, for comparing two captures. */
	private static double[] signature(Path png) {
		try {
			BufferedImage img = ImageIO.read(png.toFile());
			int w = img.getWidth(), h = img.getHeight(), gx = 48, gy = 27;
			double[] sig = new double[gx * gy * 3 + 1];
			long sky = 0, tot = 0;
			for (int cy = 0; cy < gy; cy++) for (int cx = 0; cx < gx; cx++) {
				int x0 = cx * w / gx, x1 = (cx + 1) * w / gx, y0 = cy * h / gy, y1 = (cy + 1) * h / gy;
				double r = 0, g = 0, b = 0; int k = 0;
				for (int y = y0; y < y1; y += 2) for (int x = x0; x < x1; x += 2) {
					int c = img.getRGB(x, y), cr = c >> 16 & 255, cg = c >> 8 & 255, cb = c & 255;
					r += cr; g += cg; b += cb; k++;
					if (cb > 150 && cb > cr + 30 && cb >= cg) sky++;
					tot++;
				}
				int i = (cy * gx + cx) * 3;
				if (k > 0) { sig[i] = r / k; sig[i + 1] = g / k; sig[i + 2] = b / k; }
			}
			sig[sig.length - 1] = sky / (double) Math.max(1, tot);
			return sig;
		} catch (IOException e) { throw new IllegalStateException(e); }
	}

	/** Two captures agree: mean absolute colour difference per channel under 4 of 255, and sky fractions within 0.02. */
	static boolean agree(double[] a, double[] b) {
		double d = 0;
		for (int i = 0; i < a.length - 1; i++) d += Math.abs(a[i] - b[i]);
		d /= a.length - 1;
		return d < 4 && Math.abs(a[a.length - 1] - b[b.length - 1]) < 0.02;
	}

	/**
	 * The renderer can say "all sections rendered" while sections are still being compiled (frames with a patch of world floating in sky).
	 * Takes a screenshot every 20 ticks until two in a row agree (at most 8 tries; a cap only logs and keeps the last), pinning the time of
	 * day each round. The last capture is the frame; earlier probe captures are deleted.
	 */
	static Path stableShot(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		Path prev = null; double[] prevSig = null;
		for (int i = 0; i < 8; i++) {
			Path png = context.takeScreenshot("fly-" + name + (i == 0 ? "" : "-r" + i));
			double[] sig = signature(png);
			boolean ok = prevSig != null && agree(prevSig, sig);
			if (prev != null) try { Files.deleteIfExists(prev); } catch (IOException e) { /* a stray probe file */ }
			if (ok) {
				if (i > 1) FractalBuildingsMod.LOGGER.info("[B9] {}: stable after {} captures", name, i + 1);
				return png;
			}
			prev = png; prevSig = sig;
			world.getServer().runCommand("time set noon");
			context.waitTicks(20);
		}
		FractalBuildingsMod.LOGGER.warn("[B9] {}: two captures never agreed after 8 tries; keeping the last", name);
		return prev;
	}

	static String tpCommand(Frame f) {
		double dx = f.tx() - f.x(), dy = f.ty() - f.y(), dz = f.tz() - f.z();
		double yaw = Math.toDegrees(Math.atan2(-dx, dz)), pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		return String.format(java.util.Locale.ROOT, "tp @p %.1f %.1f %.1f %.1f %.1f", f.x(), f.y(), f.z(), yaw, pitch);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (dev.fractalbuildings.gametest.compat.CompatOnly.skip("FlyThroughClientGameTest")) return;
		if (!enabled()) { FractalBuildingsMod.LOGGER.info("[B9] FlyThroughClientGameTest skipped (FB_FLY not set)"); return; }
		String[] seeds = env("FB_FLY_SEEDS", "987654321,12345,1").split(",");
		Path out = Path.of(env("FB_FLY_OUT", "build/flythrough")).toAbsolutePath();
		int rd = Integer.parseInt(env("FB_FLY_RD", "10"));
		int cells = Integer.parseInt(env("FB_FLY_CELLS", "6"));
		try { Files.createDirectories(out); } catch (IOException e) { throw new IllegalStateException(e); }
		Path manifest = out.resolve("manifest.tsv");
		try {
			Files.writeString(manifest, "seed\ttier\tsite\tcentre\tframe\tlabel\tsky\tdominant\tfile\n");
		} catch (IOException e) { throw new IllegalStateException(e); }
		List<String> failures = new ArrayList<>();
		int interiors = 0;
		context.runOnClient(client -> {
			client.options.renderDistance().set(rd);
			hideHud(client);
		});
		for (String seed : seeds) {
			try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed(seed)).create()) {
				context.runOnClient(FlyThroughClientGameTest::hideHud);
				AtomicReference<List<Prepared>> prep = new AtomicReference<>(List.of());
				world.getServer().runOnServer(server -> prep.set(RoadLook.enabled() ? RoadLook.prepare(server.overworld(), seed, cells, rd, RoadLook.FOUND) : prepare(server.overworld(), seed, cells, rd)));
				world.getServer().runCommand("gamemode spectator @p");
				world.getServer().runCommand("time set noon");
				world.getServer().runCommand("weather clear");
				for (Prepared p : prep.get()) {
					int n = 0;
					for (Frame f : p.frames()) {
						n++;
						world.getServer().runCommand("time set noon"); // the clock runs on while we wait: pin it per frame
						world.getServer().runCommand(tpCommand(f));
						settle(context, f);
						String name = seed + "-" + p.tier() + "-" + n + "-" + f.label();
						// a camera inside a block draws mostly void: say so next to the frame's numbers
						String[] at = {""};
						context.runOnClient(c -> {
							BlockState st = c.level.getBlockState(BlockPos.containing(c.player.getEyePosition()));
							if (!st.isAir()) at[0] = st.getBlock().getDescriptionId();
						});
						if (!at[0].isEmpty()) FractalBuildingsMod.LOGGER.warn("[B9] {}: the camera is inside {}", name, at[0]);
						Path png = stableShot(context, world, name);
						if (f.label().equals("interior")) interiors++;
						String res = analyse(png, out.resolve(name + ".jpg"));
						String[] r = res.split("\t");
						double sky = Double.parseDouble(r[0]), dom = Double.parseDouble(r[1]);
						String line = String.join("\t", seed, p.tier(), p.id(), p.centre(), "" + n, f.label(), r[0], r[1], name + ".jpg");
						try { Files.writeString(manifest, line + "\n", StandardOpenOption.APPEND); } catch (IOException e) { throw new IllegalStateException(e); }
						FractalBuildingsMod.LOGGER.info("[B9] frame {}", line);
						if (p.tag() == null && sky > 0.9) failures.add(name + ": sky fraction " + sky);
						if (p.tag() == null && dom > 0.8) failures.add(name + ": one colour fills " + dom + " of the frame (camera in the ground?)");
					}
					for (String note : p.notes()) FractalBuildingsMod.LOGGER.info("[B9] {} {}: {}", seed, p.tier(), note);
				}
			}
		}
		if (interiors == 0 && !RoadLook.enabled()) failures.add("no interior frame on any seed");
		if (!failures.isEmpty()) throw new IllegalStateException("fly-through checks failed:\n" + String.join("\n", failures));
		FractalBuildingsMod.LOGGER.info("[B9] fly-through done, frames in {}", out);
	}

	/** "sky\tdominant": fraction of sky-blue pixels and of the most common 4-bit colour; also writes a 960-wide JPEG. */
	private static String analyse(Path png, Path jpg) {
		try {
			BufferedImage img = ImageIO.read(png.toFile());
			int w = img.getWidth(), h = img.getHeight();
			Map<Integer, Integer> bins = new HashMap<>();
			long sky = 0; int top = 0;
			for (int y = 0; y < h; y += 2) for (int x = 0; x < w; x += 2) {
				int c = img.getRGB(x, y), r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
				if (b > 150 && b > r + 30 && b >= g) sky++;
				top = bins.merge((r >> 4) << 8 | (g >> 4) << 4 | (b >> 4), 1, Integer::sum) > top ? bins.get((r >> 4) << 8 | (g >> 4) << 4 | (b >> 4)) : top;
			}
			double total = (double) ((w + 1) / 2) * ((h + 1) / 2);
			int ow = Math.min(960, w), oh = Math.round(h * (ow / (float) w));
			BufferedImage small = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2 = small.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2.drawImage(img, 0, 0, ow, oh, null);
			g2.dispose();
			ImageWriter wr = ImageIO.getImageWritersByFormatName("jpeg").next();
			ImageWriteParam prm = wr.getDefaultWriteParam();
			prm.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			prm.setCompressionQuality(0.8f);
			try (ImageOutputStream os = ImageIO.createImageOutputStream(jpg.toFile())) {
				wr.setOutput(os);
				wr.write(null, new IIOImage(small, null, null), prm);
			}
			wr.dispose();
			return String.format(java.util.Locale.ROOT, "%.3f\t%.3f", sky / total, top / total);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int surface(ServerLevel level, int x, int z) { return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z); }

	private static List<Prepared> prepare(ServerLevel level, String seed, int cells, int rd) {
		NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
		WorldPlanner planner = WorldPlanners.of(level, gen).orElseThrow(() -> new IllegalStateException("no planner in a normal world"));
		TerrainProbe exact = new LiveProbes(gen, level.getChunkSource().randomState()).exact();
		int cs = planner.config().cellSize();
		TreeMap<String, SettlementSite> all = new TreeMap<>();
		for (int j = -cells; j <= cells; j++) for (int i = -cells; i <= cells; i++) {
			planner.nearest(i * cs + cs / 2, j * cs + cs / 2, Tier.HAMLET, 0).ifPresent(s -> all.put(s.site().id(), s.site()));
		}
		List<Prepared> out = new ArrayList<>();
		for (Tier t : TIERS) {
			SettlementSite best = null; double bd = Double.MAX_VALUE;
			for (SettlementSite s : all.values()) {
				if (s.tier() != t) continue;
				double d = Math.hypot(s.centre().x(), s.centre().z());
				if (d < bd) { bd = d; best = s; }
			}
			if (best == null) { FractalBuildingsMod.LOGGER.info("[B9] seed {}: no {} within {} cells of the origin", seed, t.json, cells); continue; }
			try {
				out.add(frames(level, planner, exact, seed, best, rd));
			} catch (RuntimeException e) {
				FractalBuildingsMod.LOGGER.warn("[B9] seed {} {} {}: frames not prepared: {}", seed, t.json, best.id(), e.toString());
			}
		}
		return out;
	}

	/** Terrain height for a camera: the highest (min sea level) exact floor along the segment, sampled every 12 blocks. */
	private static double ridge(TerrainProbe exact, double x0, double z0, double x1, double z1) {
		double h = 63;
		int n = (int) Math.max(2, Math.hypot(x1 - x0, z1 - z0) / 12);
		for (int i = 0; i <= n; i++) h = Math.max(h, exact.floorY((int) (x0 + (x1 - x0) * i / n), (int) (z0 + (z1 - z0) * i / n)));
		return h;
	}

	private static Prepared frames(ServerLevel level, WorldPlanner planner, TerrainProbe exact, String seed, SettlementSite site, int rd) {
		int cx = site.centre().x(), cz = site.centre().z(), r = Math.max(site.radius(), 40);
		List<String> notes = new ArrayList<>();
		List<Frame> fr = new ArrayList<>();
		DistrictGrid.Tile ct = planner.grid().tileAt(cx, cz);
		// the planned tile nearest the centre with buildings
		DistrictGrid.Tile pick = null;
		for (int d = 0; d <= 4 && pick == null; d++) for (int dz = -d; dz <= d && pick == null; dz++) for (int dx = -d; dx <= d && pick == null; dx++) {
			if (planner.tile(ct.ix() + dx, ct.iz() + dz).isPresent() && !planner.buildings(ct.ix() + dx, ct.iz() + dz).isEmpty()) pick = planner.grid().tile(ct.ix() + dx, ct.iz() + dz);
		}
		if (pick == null) throw new IllegalStateException("no planned tile with buildings near the centre");
		for (int z = pick.minZ() >> 4; z < pick.maxZ() >> 4; z++) for (int x = pick.minX() >> 4; x < pick.maxX() >> 4; x++) level.getChunk(x, z, ChunkStatus.FULL, true);
		int mx = (pick.minX() + pick.maxX()) / 2, mz = (pick.minZ() + pick.maxZ()) / 2;
		double cy = exact.floorY(cx, cz);

		// outward direction: away from the world origin (south when the centre is the origin)
		double ox = cx, oz = cz, ol = Math.hypot(ox, oz);
		if (ol < 1) { ox = 0; oz = 1; ol = 1; }
		ox /= ol; oz /= ol;
		// 1 approach: 150 blocks out on the origin's side, high enough to clear the ridge between, looking at the centre
		int view = rd * 16; // nothing beyond the render distance is drawn: every target stays inside it
		double back = Math.min(150, view - 16);
		double ax = cx - ox * back, az = cz - oz * back;
		fr.add(new Frame("approach", ax, Math.min(CLOUDS, ridge(exact, ax, az, cx, cz) + 35), az, cx, cy + 12, cz, 80));
		// 2 overview from above, from the south
		int ro = Math.min(r, 260);
		double off = Math.min(ro * 0.7, view * 0.5); // a big settlement shows its middle: the rest is out of range
		double vx = cx, vz = cz + off;
		fr.add(new Frame("overview", vx, Math.min(CLOUDS, ridge(exact, cx - off, cz - off, cx + off, cz + off) + 15 + off * 1.1), vz, cx, cy, cz, 60));
		// 3 street: along the longest road/paving box of the picked tile
		Box3 road = null; int len = 0;
		for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
			if (planner.tile(pick.ix() + dx, pick.iz() + dz).isEmpty()) continue;
			for (GroundPiece g : planner.ground(pick.ix() + dx, pick.iz() + dz)) for (Box b : g.boxes()) {
				if (!b.role().isRoad() && b.role() != Role.PAVING) continue;
				int l = Math.max(b.box().sx(), b.box().sz());
				if (l > len && Math.min(b.box().sx(), b.box().sz()) >= 2) { len = l; road = b.box(); }
			}
		}
		if (road != null && len >= 8) {
			boolean alongX = road.sx() >= road.sz();
			double sx = alongX ? road.x() + 0.5 : road.x() + road.sx() / 2.0, sz = alongX ? road.z() + road.sz() / 2.0 : road.z() + 0.5;
			double ex = alongX ? road.x() + road.sx() - 0.5 : sx, ez = alongX ? sz : road.z() + road.sz() - 0.5;
			level.getChunk((int) sx >> 4, (int) sz >> 4, ChunkStatus.FULL, true);
			level.getChunk((int) ex >> 4, (int) ez >> 4, ChunkStatus.FULL, true);
			double top = road.y() + road.sy(); // the road's own surface: the heightmap would give the canopy of a tree over it
			fr.add(new Frame("street", sx, top, sz, ex, top + 1.6, ez, 40));
			notes.add("street along " + road + " (" + len + " long)");
		} else notes.add("no road piece of 8+ blocks near the centre tile; no street frame");
		// 4 interior: the door nearest the tile centre with a ceiling on one side and air on both
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		long best = Long.MAX_VALUE; double[] in = null;
		for (int z = pick.minZ(); z < pick.maxZ(); z++) for (int x = pick.minX(); x < pick.maxX(); x++) {
			int top = surface(level, x, z);
			for (int y = top - 1; y >= top - 40; y--) {
				BlockState st = level.getBlockState(p.set(x, y, z));
				if (!(st.getBlock() instanceof DoorBlock) || st.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) continue;
				Direction f = st.getValue(DoorBlock.FACING);
				for (int sgn = -1; sgn <= 1; sgn += 2) {
					int ix = x + sgn * f.getStepX() * 2, iz = z + sgn * f.getStepZ() * 2;
					boolean free = level.getBlockState(p.set(ix, y, iz)).isAir() && level.getBlockState(p.set(ix, y + 1, iz)).isAir()
							&& level.getBlockState(p.set(x + sgn * f.getStepX(), y, z + sgn * f.getStepZ())).isAir();
					boolean roof = false;
					for (int k = 2; k <= 7 && !roof; k++) roof = !level.getBlockState(p.set(ix, y + k, iz)).isAir();
					long d = (long) (x - mx) * (x - mx) + (long) (z - mz) * (z - mz);
					if (free && roof && d < best) { best = d; in = new double[]{x + 0.5 + sgn * f.getStepX() * 1.5, y, z + 0.5 + sgn * f.getStepZ() * 1.5, sgn * f.getStepX(), sgn * f.getStepZ()}; }
				}
			}
		}
		if (in != null) fr.add(new Frame("interior", in[0], in[1], in[2], in[0] + in[3] * 6, in[1] + 1.2, in[2] + in[4] * 6, 30));
		else notes.add("no door with a roofed free side in the picked tile; no interior frame");
		// 5 slope: the most uneven planned tile near the centre, seen from its lowest sampled corner side, 60 blocks out. Only a tile whose
		// camera clears the ridge on the way (by 14) below the cloud cap counts: on the 12345 metropolis (tile 49,-13, relief 135) the cap
		// held the camera under the ridge and the frame was 85 % one colour (CITYQ r3).
		int bestRelief = -1; DistrictGrid.Tile slope = null; double[] cam = null;
		for (int dz = -4; dz <= 4; dz++) for (int dx = -4; dx <= 4; dx++) {
			int ix = ct.ix() + dx, iz = ct.iz() + dz;
			if (planner.tile(ix, iz).isEmpty()) continue;
			DistrictGrid.Tile t = planner.grid().tile(ix, iz);
			int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE; double[] lp = null;
			for (int z = t.minZ(); z <= t.maxZ(); z += 8) for (int x = t.minX(); x <= t.maxX(); x += 8) {
				int fy = exact.floorY(x, z);
				if (fy < lo) { lo = fy; lp = new double[]{x, z}; }
				hi = Math.max(hi, fy);
			}
			if (hi - lo <= bestRelief) continue;
			double tx = (t.minX() + t.maxX()) / 2.0, tz = (t.minZ() + t.maxZ()) / 2.0;
			double ddx = lp[0] - tx, ddz = lp[1] - tz, dl = Math.max(1, Math.hypot(ddx, ddz));
			double kx = tx + ddx / dl * 60, kz = tz + ddz / dl * 60;
			double ty = exact.floorY((int) tx, (int) tz), rg = ridge(exact, kx, kz, tx, tz);
			if (rg + 14 > CLOUDS) continue;
			// at most 70 above the target (but always 14 over the ridge): a camera much higher sees mostly past the render distance
			double sy = Math.max(rg + 14, Math.min(rg + 14 + 18 * site.tier().ordinal(), ty + 70));
			bestRelief = hi - lo; slope = t; cam = new double[]{kx, sy, kz, tx, ty, tz, rg, exact.floorY((int) kx, (int) kz)};
		}
		if (slope != null) {
			fr.add(new Frame("slope", cam[0], cam[1], cam[2], cam[3], cam[4] + 4, cam[5], 50));
			notes.add("slope tile " + slope.ix() + "," + slope.iz() + " relief " + bestRelief + String.format(java.util.Locale.ROOT,
					": camera %.0f,%.0f,%.0f (ridge %.0f, ground %.0f) target y %.0f", cam[0], cam[1], cam[2], cam[6], cam[7], cam[4]));
		}
		// 6 edge: outside the built footprint on the origin's far side, looking in. The built edge is the farthest tile with buildings
		// along the outward ray, not the drawn radius: a coastal city's disk can run hundreds of blocks out over open sea (12345 city,
		// r 807, east rim frozen ocean: the frame showed only ice, which the sky test also reads as sky).
		// A lot to build within 16 blocks of the ray counts (a tile is too coarse: its lots can be far off the ray).
		int rb = 0;
		for (int d = 0; d <= r; d += 8) {
			int px = (int) Math.floor(cx + ox * d), pz = (int) Math.floor(cz + oz * d);
			DistrictGrid.TileId t = planner.grid().tileOf(px, pz);
			if (planner.tile(t.ix(), t.iz()).isEmpty()) continue;
			for (dev.fractalbuildings.core.settlement.LotBrief l : planner.plan(t.ix(), t.iz()).plan().lots()) {
				if (!dev.fractalbuildings.core.settlement.LotBrief.BUILD.equals(l.status())) continue;
				if (px >= l.x() - 16 && px < l.x() + l.w() + 16 && pz >= l.z() - 16 && pz < l.z() + l.d() + 16) { rb = d; break; }
			}
		}
		rb = Math.max(rb, 40);
		notes.add("edge: built to " + rb + " of radius " + r + " along the outward ray");
		double ex = cx + ox * (rb + 40), ez = cz + oz * (rb + 40);
		double ix = cx + ox * (rb - 40), iz = cz + oz * (rb - 40);
		fr.add(new Frame("edge", ex, Math.min(CLOUDS, ridge(exact, ex, ez, ix, iz) + 14 + 8 * site.tier().ordinal()), ez, ix, exact.floorY((int) ix, (int) iz) + 6, iz, 60));
		notes.add("centre " + cx + "," + cz + " radius " + site.radius() + ", picked tile " + pick.ix() + "," + pick.iz());
		return new Prepared(seed, site, fr, notes);
	}
}
