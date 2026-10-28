package dev.fractalbuildings.gametest.flythrough;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.probe.TerrainProbe;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.settlement.SettlementSite;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.BuildingPiece;
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
 * B9 (6.8), redesigned by FLY2: a few high-quality frames per settlement instead of six. For each seed one world; for each tier (hamlet,
 * village, town, city, metropolis) the nearest settlement of exactly that tier within {@code FB_FLY_CELLS} region cells of the origin (a missing
 * tier is logged, not an error). The camera targets come from the plan: every planned building of the settlement is a weight on the built
 * footprint, and the densest window (side up to (view distance - 16) / 1.3, so it stays in range) is framed from a shallow high angle in the
 * best of eight directions (lowest terrain on the sight line): hamlet, village, town one "view" frame; city and metropolis two sweeps
 * (a: the densest window, b: another part of the city when the footprint is wider than the window, else the same from the other side); a town
 * also gets one interior frame. Every tier also gets street-level frames (r4): the main street at eye 3 over the surface, the hub square toward its hall or temple, a side-on bridge (city, metropolis) and, in a town, the back corner of the largest ground-floor room. City sweep-a is the window of most built volume, sweep-b the rim looking in. {@code FB_FLY_POSES} replays the poses of an earlier manifest. Parks and empty lots are not targets. Clouds are off, the HUD hidden.
 *
 * <p>Opt-in: runs only with {@code FB_FLY} set ({@code FB_FLY=only} also skips the other client tests). Settings by environment:
 * {@code FB_FLY_SEEDS} (default 987654321,12345,1), {@code FB_FLY_OUT} (JPEG folder), {@code FB_FLY_RD} (render distance for hamlet, village, town, default 10), {@code FB_FLY_RD_BIG} (for city and metropolis sweeps, default = FB_FLY_RD; 32 gives the wide shots; 24-32
 * for the wide sweeps, GPU only), {@code FB_FLY_SIZE} (window and capture size, default 1920x1080), {@code FB_FLY_FILL_S} (cap of the per-frame chunk-fill wait in seconds; default 60 / 90 / 180 for rd up to 12 / 24 / more), {@code FB_FLY_CELLS} (search radius in
 * cells, default 6). Use {@code scripts/flythrough.sh}. Per-frame checks: sky fraction below 0.9, no single colour over 80% of the frame,
 * and a sweep window with at least one planned building; frames are kept either way and failures reported at the end.
 */
// gametest-order: 8
public class FlyThroughClientGameTest implements FabricClientGameTest {
	static final Tier[] TIERS = {Tier.HAMLET, Tier.VILLAGE, Tier.TOWN, Tier.CITY, Tier.METROPOLIS};
	/** Camera ceiling: clouds are switched off, so only the build limit (320) bounds the camera. */
	static final double CEILING = 300;

	public static boolean enabled() { return System.getenv("FB_FLY") != null; }

	/** The other client tests return at once in a fly-only run. */
	public static boolean skipOthers() { return "only".equals(System.getenv("FB_FLY")); }

	/** F1: no hotbar, no chat, no crosshair in the frames. */
	static void hideHud(net.minecraft.client.Minecraft client) { if (!client.gui.hud.isHidden()) client.gui.hud.toggle(); }

	private static String env(String k, String d) { String v = System.getenv(k); return v == null || v.isBlank() ? d : v; }

	/** @param rd the render distance for this frame; 0 = the tier's default (street-level frames use 12-16: nothing far is in them) */
	record Frame(String label, double x, double y, double z, double tx, double ty, double tz, int settle, int bldgs, int side, int rd) {
		Frame(String label, double x, double y, double z, double tx, double ty, double tz, int settle, int bldgs, int side) { this(label, x, y, z, tx, ty, tz, settle, bldgs, side, 0); }
		Frame(String label, double x, double y, double z, double tx, double ty, double tz, int settle) { this(label, x, y, z, tx, ty, tz, settle, 0, 0, 0); }
	}

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
	private static double[] settle(ClientGameTestContext context, Frame f, int rd) {
		context.waitTicks(f.settle());
		int[][] pts = {{(int) f.x(), (int) f.z()}, {(int) (f.x() + 0.6 * (f.tx() - f.x())), (int) (f.z() + 0.6 * (f.tz() - f.z()))}, {(int) f.tx(), (int) f.tz()}};
		// the fill scales with the render distance: the server generates about 44 chunks/s whatever the renderer (vanilla lets a player
		// have 4 in flight), so a fresh view takes ~45 s at rd 24 and ~90-120 s at rd 32. Wait for 98 % of the chunks of the circle of
		// radius rd - 1 around the camera, then for the renderer; the cap is wall-clock seconds (a loaded machine runs ticks slowly):  60 s (rd <= 12), 90 s (rd <= 24), 180 s (more), FB_FLY_FILL_S sets it.
		int capS = Integer.parseInt(env("FB_FLY_FILL_S", rd <= 12 ? "60" : rd <= 24 ? "90" : "180"));
		int cx = (int) Math.floor(f.x()) >> 4, cz = (int) Math.floor(f.z()) >> 4;
		long t0 = System.nanoTime();
		double frac = 0;
		for (int waited = 0; ; waited += 10) {
			double[] fr = {0}; boolean[] done = {false};
			context.runOnClient(c -> {
				int have = 0, want = 0, r = Math.max(1, rd - 1);
				for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
					if (dx * dx + dz * dz > r * r) continue;
					want++;
					if (c.level.getChunkSource().hasChunk(cx + dx, cz + dz)) have++;
				}
				fr[0] = have / (double) want;
				boolean all = c.levelRenderer.hasRenderedAllSections() && fr[0] >= 0.98;
				for (int[] p : pts) all &= c.level.getChunkSource().hasChunk(p[0] >> 4, p[1] >> 4);
				done[0] = all;
			});
			frac = fr[0];
			if (done[0]) break;
			if ((System.nanoTime() - t0) / 1e9 >= capS) { FractalBuildingsMod.LOGGER.warn("[B9] fill: only {} of the view loaded and drawn after {} s; taking the screenshot anyway", String.format(java.util.Locale.ROOT, "%.3f", frac), capS); break; }
			context.waitTicks(10);
		}
		context.waitTicks(20);
		FractalBuildingsMod.LOGGER.info("[B9] fill: {} s for {} of the view (cap {} s)", String.format(java.util.Locale.ROOT, "%.1f", (System.nanoTime() - t0) / 1e9), String.format(java.util.Locale.ROOT, "%.3f", frac), capS);
		return new double[]{frac, (System.nanoTime() - t0) / 1e9};
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

	/** The arguments of the teleport that puts the player (feet) at the frame's camera: "x y z yaw pitch". */
	static String tpArgs(Frame f) {
		double dx = f.tx() - f.x(), dy = f.ty() - f.y(), dz = f.tz() - f.z();
		double yaw = Math.toDegrees(Math.atan2(-dx, dz)), pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		return String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f %.1f %.1f", f.x(), f.y(), f.z(), yaw, pitch);
	}

	static String tpCommand(Frame f) { return "tp @p " + tpArgs(f); }

	/**
	 * Teleports to the frame, waits for the view, takes the stable screenshot, writes the JPEG and the manifest line; returns "sky\tdominant".
	 * {@code rdNow} is the render distance of this frame (the world was created at the largest).
	 */
	private static String[] shoot(ClientGameTestContext context, TestSingleplayerContext world, Frame f, String name, int rdNow, Path out, Path manifest,
			String seed, String tier, String site, String centre, int n) {
		context.runOnClient(c -> c.options.renderDistance().set(rdNow));
		world.getServer().runCommand("time set noon"); // the clock runs on while we wait: pin it per frame
		world.getServer().runCommand(tpCommand(f));
		double[] fill = settle(context, f, rdNow);
		// a camera inside a block draws mostly void: say so next to the frame's numbers
		String[] at = {""};
		context.runOnClient(c -> {
			BlockState st = c.level.getBlockState(BlockPos.containing(c.player.getEyePosition()));
			if (!st.isAir()) at[0] = st.getBlock().getDescriptionId();
		});
		if (!at[0].isEmpty()) FractalBuildingsMod.LOGGER.warn("[B9] {}: the camera is inside {}", name, at[0]);
		Path png = stableShot(context, world, name);
		String[] r = analyse(png, out.resolve(name + ".jpg")).split("\t");
		String line = String.join("\t", seed, tier, site, centre, "" + n, f.label(), "" + f.bldgs(), "" + f.side(), "" + rdNow, String.format(java.util.Locale.ROOT, "%.3f", fill[0]), String.format(java.util.Locale.ROOT, "%.0f", fill[1]), tpArgs(f), env("FB_FLY_COMMIT", "unknown"), r[0], r[1], name + ".jpg");
		try { Files.writeString(manifest, line + "\n", StandardOpenOption.APPEND); } catch (IOException e) { throw new IllegalStateException(e); }
		FractalBuildingsMod.LOGGER.info("[B9] frame {}", line);
		return r;
	}

	/** A frame from a recorded "x y z yaw pitch": the target 64 blocks along the view (the settle waits for the chunks there too). */
	static Frame fromTp(String label, String tp, int bldgs, int side) {
		String[] v = tp.trim().split(" ");
		double x = Double.parseDouble(v[0]), y = Double.parseDouble(v[1]), z = Double.parseDouble(v[2]), yaw = Math.toRadians(Double.parseDouble(v[3])), pitch = Math.toRadians(Double.parseDouble(v[4]));
		double h = Math.cos(pitch) * 64;
		return new Frame(label, x, y, z, x - Math.sin(yaw) * h, y - Math.sin(pitch) * 64, z + Math.cos(yaw) * h, 60, bldgs, side);
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (dev.fractalbuildings.gametest.compat.CompatOnly.skip("FlyThroughClientGameTest")) return;
		if (dev.fractalbuildings.gametest.perf.PerfGate.skipOthers()) return; // B8: a perf-only run
		if (!enabled()) { FractalBuildingsMod.LOGGER.info("[B9] FlyThroughClientGameTest skipped (FB_FLY not set)"); return; }
		String poses = System.getenv("FB_FLY_POSES");
		List<String[]> rows = new ArrayList<>();
		String[] head = null;
		if (poses != null && !poses.isBlank()) {
			// replay: every row of an earlier manifest.tsv is shot again from its recorded pose, in its own render distance
			try {
				List<String> lines = Files.readAllLines(Path.of(poses));
				head = lines.get(0).split("\t", -1);
				for (String l : lines.subList(1, lines.size())) if (!l.isBlank()) rows.add(l.split("\t", -1));
			} catch (IOException e) { throw new IllegalStateException("cannot read the poses file " + poses, e); }
		}
		Map<String, Integer> col = new HashMap<>();
		if (head != null) for (int i = 0; i < head.length; i++) col.put(head[i], i);
		String[] seeds = head != null ? rows.stream().map(r -> r[col.get("seed")]).distinct().toArray(String[]::new) : env("FB_FLY_SEEDS", "987654321,12345,1").split(",");
		Path out = Path.of(env("FB_FLY_OUT", "build/flythrough")).toAbsolutePath();
		int rd = Integer.parseInt(env("FB_FLY_RD", "10"));
		int rdBig0 = Integer.parseInt(env("FB_FLY_RD_BIG", "" + rd));
		int rdMax = rdBig0;
		if (head != null) for (String[] r : rows) rdMax = Math.max(rdMax, Integer.parseInt(r[col.get("rd")]));
		int rdBig = rdBig0, worldRd = Math.max(rd, rdMax);
		int cells = Integer.parseInt(env("FB_FLY_CELLS", "6"));
		String[] wh = env("FB_FLY_SIZE", "1920x1080").split("x");
		int width = Integer.parseInt(wh[0]), height = Integer.parseInt(wh[1]);
		try { Files.createDirectories(out); } catch (IOException e) { throw new IllegalStateException(e); }
		Path manifest = out.resolve("manifest.tsv");
		try {
			Files.writeString(manifest, "seed\ttier\tsite\tcentre\tframe\tlabel\tbuildings\twindow\trd\tfill\tfill_s\ttp\tcommit\tsky\tdominant\tfile\n");
		} catch (IOException e) { throw new IllegalStateException(e); }
		List<String> failures = new ArrayList<>();
		int interiors = 0;
		context.runOnClient(client -> {
			// the world is created at the larger distance: the integrated server caps every player at the distance it started with, so a distance raised later never filled (987654321: four sweeps at 0.727)
			client.options.renderDistance().set(worldRd);
			client.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
			hideHud(client);
		});
		context.getInput().resizeWindow(width, height);
		for (String seed : seeds) {
			// every world starts at the larger distance: the options still hold the last frame's (street frames use 12-16), and the integrated server caps the view at its start
			context.runOnClient(c -> c.options.renderDistance().set(worldRd));
			try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed(seed)).create()) {
				context.runOnClient(FlyThroughClientGameTest::hideHud);
				world.getServer().runCommand("gamemode spectator @p");
				world.getServer().runCommand("time set noon");
				world.getServer().runCommand("weather clear");
				if (head != null) {
					for (String[] r : rows) {
						if (!r[col.get("seed")].equals(seed)) continue;
						String file = r[col.get("file")];
						Frame f = fromTp(r[col.get("label")], r[col.get("tp")], Integer.parseInt(r[col.get("buildings")]), Integer.parseInt(r[col.get("window")]));
						shoot(context, world, f, file.endsWith(".jpg") ? file.substring(0, file.length() - 4) : file, Integer.parseInt(r[col.get("rd")]), out, manifest,
								seed, r[col.get("tier")], r[col.get("site")], r[col.get("centre")], Integer.parseInt(r[col.get("frame")]));
					}
					continue;
				}
				AtomicReference<List<Prepared>> prep = new AtomicReference<>(List.of());
				world.getServer().runOnServer(server -> prep.set(RoadLook.enabled() ? RoadLook.prepare(server.overworld(), seed, cells, rd, RoadLook.FOUND) : prepare(server.overworld(), seed, cells, rd, rdBig)));
				for (Prepared p : prep.get()) {
					int tierRd = p.site() != null && p.site().tier().ordinal() >= Tier.CITY.ordinal() ? rdBig : rd; // the wide sweeps get the larger render distance
					int n = 0;
					for (Frame f : p.frames()) {
						n++;
						int rdNow = f.rd() > 0 ? Math.min(f.rd(), tierRd) : tierRd;
						String name = seed + "-" + p.tier() + "-" + n + "-" + f.label();
						String[] r = shoot(context, world, f, name, rdNow, out, manifest, seed, p.tier(), p.id(), p.centre(), n);
						if (f.label().equals("interior")) interiors++;
						double sky = Double.parseDouble(r[0]), dom = Double.parseDouble(r[1]);
						if (p.tag() == null && sky > 0.9) failures.add(name + ": sky fraction " + sky);
						if (p.tag() == null && dom > 0.8) failures.add(name + ": one colour fills " + dom + " of the frame (camera in the ground?)");
						if (p.tag() == null && f.label().startsWith("sweep") && f.bldgs() == 0) failures.add(name + ": the window holds no planned building");
					}
					for (String note : p.notes()) FractalBuildingsMod.LOGGER.info("[B9] {} {}: {}", seed, p.tier(), note);
				}
			}
		}
		if (head == null && interiors == 0 && !RoadLook.enabled()) failures.add("no interior frame on any seed");
		if (!failures.isEmpty()) throw new IllegalStateException("fly-through checks failed:\n" + String.join("\n", failures));
		FractalBuildingsMod.LOGGER.info("[B9] fly-through done, frames in {}", out);
	}


	/** "sky\tdominant": fraction of sky-blue pixels and of the most common 4-bit colour; also writes a JPEG up to 1920 wide. */
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
			int ow = Math.min(1920, w), oh = Math.round(h * (ow / (float) w));
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

	private static List<Prepared> prepare(ServerLevel level, String seed, int cells, int rd, int rdBig) {
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
				out.add(frames(level, planner, exact, seed, best, t.ordinal() >= Tier.CITY.ordinal() ? rdBig : rd));
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

	/** One planned building as a weight on the footprint: its box centre, its footprint-times-height weight, its vertical extent and its volume (footprint times height). */
	record Mass(double x, double z, double w, int minY, int maxY, double vol) {}

	/** A square window of the built footprint: its centre, side, the weight of the buildings whose centre lies in it, their count, tallest top and built volume. */
	record Window(double x, double z, double side, double weight, int count, int top, double vol) {}

	/** The weight of a building: footprint area, more for a tall one (a tower weighs like a few blocks of houses). */
	static double weight(int sx, int sz, int sy) { return (double) sx * sz * (1 + Math.min(sy, 120) / 30.0); }

	static Window window(List<Mass> masses, double cx, double cz, double side) {
		double h = side / 2, w = 0, v = 0; int n = 0, top = Integer.MIN_VALUE;
		for (Mass m : masses) if (Math.abs(m.x() - cx) <= h && Math.abs(m.z() - cz) <= h) { w += m.w(); v += m.vol(); n++; top = Math.max(top, m.maxY()); }
		return new Window(cx, cz, side, w, n, top, v);
	}

	/**
	 * The best window of the given side over the footprint (candidate centres every 16 blocks), skipping candidates within
	 * {@code avoid.side() * 0.6} of {@code avoid}; ties go to the one nearer the footprint's middle. By weight (footprint, a little more
	 * for height) when {@code byVolume} is false, else by built volume (so a downtown of towers beats a wider suburb). Null when no candidate has a building.
	 */
	static Window densest(List<Mass> masses, double side, Window avoid, boolean byVolume) {
		double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
		for (Mass m : masses) { x0 = Math.min(x0, m.x()); x1 = Math.max(x1, m.x()); z0 = Math.min(z0, m.z()); z1 = Math.max(z1, m.z()); }
		double mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
		Window best = null; double bestKey = -1;
		for (double z = z0; z <= z1 + 8; z += 16) for (double x = x0; x <= x1 + 8; x += 16) {
			if (avoid != null && Math.hypot(x - avoid.x(), z - avoid.z()) < avoid.side() * 0.6) continue;
			Window w = window(masses, x, z, side);
			double key = (byVolume ? w.vol() : w.weight()) - 1e-4 * Math.hypot(x - mx, z - mz);
			if (w.count() > 0 && key > bestKey) { best = w; bestKey = key; }
		}
		return best;
	}

	/**
	 * The window nearest the footprint's rim that still holds a real part of the city (at least 30 % of the busiest window's buildings, 6 at
	 * least): the edge, where DS-18's taper from the core to the suburb shows. Null when no candidate qualifies.
	 */
	static Window rim(List<Mass> masses, double side, double cx, double cz, Window busiest) {
		double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
		for (Mass m : masses) { x0 = Math.min(x0, m.x()); x1 = Math.max(x1, m.x()); z0 = Math.min(z0, m.z()); z1 = Math.max(z1, m.z()); }
		int need = Math.max(6, busiest.count() * 3 / 10);
		Window best = null; double bestD = -1;
		for (double z = z0; z <= z1 + 8; z += 16) for (double x = x0; x <= x1 + 8; x += 16) {
			Window w = window(masses, x, z, side);
			double d = Math.hypot(x - cx, z - cz);
			if (w.count() >= need && d > bestD) { best = w; bestD = d; }
		}
		return best;
	}

	/** The tiles (ix, iz) the settlement owns, in order. */
	private static List<int[]> siteTiles(WorldPlanner planner, SettlementSite site) {
		int reach = site.radius() + 160;
		DistrictGrid.Tile a = planner.grid().tileAt(site.centre().x() - reach, site.centre().z() - reach);
		DistrictGrid.Tile b = planner.grid().tileAt(site.centre().x() + reach, site.centre().z() + reach);
		List<int[]> out = new ArrayList<>();
		for (int iz = a.iz(); iz <= b.iz(); iz++) for (int ix = a.ix(); ix <= b.ix(); ix++) {
			var info = planner.tile(ix, iz);
			if (info.isEmpty() || !info.get().site().id().equals(site.id())) continue;
			out.add(new int[]{ix, iz});
		}
		return out;
	}

	/** Every planned building of the settlement as masses (box centre in the footprint), over the tiles the settlement owns. */
	private static List<Mass> masses(WorldPlanner planner, SettlementSite site) {
		List<Mass> out = new ArrayList<>();
		for (int[] t : siteTiles(planner, site)) {
			for (BuildingPiece bp : planner.buildings(t[0], t[1])) {
				Box3 bx = bp.box();
				out.add(new Mass(bx.x() + bx.sx() / 2.0, bx.z() + bx.sz() / 2.0, weight(bx.sx(), bx.sz(), bx.sy()), bx.y(), bx.y() + bx.sy(), (double) bx.sx() * bx.sz() * bx.sy()));
			}
		}
		return out;
	}

	/**
	 * A shallow, high view of a window: the camera stands {@code 0.7 * side} back from the window centre in the best of eight directions
	 * (the one whose sight line crosses the lowest terrain), raised to an elevation of {@code elevDeg} above the centre's ground (and toward the tallest building, but at most 30 degrees down, and above
	 * the ridge on the way), looking at a point a quarter of the way up the window's tallest building. The whole window stays inside the
	 * render distance: far edge at 1.3 * side, so side is at most (view - 16) / 1.3. With {@code out} (a unit vector from the window toward the settlement's centre)
	 * the camera stands on the core side and looks out over the taper to the rim window, the target moved 0.2 sides outward: the edge taper.
	 */
	private static Frame sweep(TerrainProbe exact, String label, Window w, double elevDeg, double avoidAngle, int settle, double[] chosenAngle, double[] out) {
		double d = w.side() * 0.7;
		double ground = exact.floorY((int) w.x(), (int) w.z());
		double tall = Math.max(0, w.top() - ground);
		double ty = ground + Math.max(4, Math.min(40, tall * 0.3));
		double bestScore = Double.MAX_VALUE, bx = 0, bz = 0, bridge = 0, ba = 0;
		for (int k = 0; k < 8; k++) {
			double ang = Math.toRadians(k * 45 + 22.5);
			if (out != null) ang = Math.atan2(-out[0], out[1]); // camera at w + out * d: -sin(ang) = out.x, cos(ang) = out.z
			else if (!Double.isNaN(avoidAngle) && Math.abs(Math.IEEEremainder(ang - avoidAngle, 2 * Math.PI)) < Math.toRadians(80)) continue;
			double cx = w.x() - Math.sin(ang) * d, cz = w.z() + Math.cos(ang) * d;
			double ridge = ridge(exact, cx, cz, w.x(), w.z());
			double score = ridge + 0.3 * exact.floorY((int) cx, (int) cz);
			if (score < bestScore) { bestScore = score; bx = cx; bz = cz; bridge = ridge; ba = ang; }
			if (out != null) break;
		}
		chosenAngle[0] = ba;
		double dn = Math.max(bridge + 18, ground + d * Math.tan(Math.toRadians(elevDeg)));
		// tall windows: raise the camera toward the towers' height, but never past 30 degrees down (it was near-vertical over 150 m towers)
		double cy = Math.min(CEILING, Math.max(dn, ground + Math.min(0.55 * tall, d * Math.tan(Math.toRadians(30)))));
		// look down at least 24 degrees: aimed at a point up a tower the frame was 40-50 % sky and haze, the settlement a strip at the horizon
		ty = Math.min(ty, cy - d * Math.tan(Math.toRadians(24)));
		double tx = w.x(), tz = w.z();
		if (out != null) { tx -= out[0] * w.side() * 0.2; tz -= out[1] * w.side() * 0.2; }
		return new Frame(label, bx, cy, bz, tx, ty, tz, settle, w.count(), (int) w.side());
	}

	/** The ground height under a street point, from the heightmap's neighbours: the planned {@code y} is the standing level of the street. */
	private static void load(ServerLevel level, double x, double z) { level.getChunk((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4, ChunkStatus.FULL, true); }

	/**
	 * The street frame: the longest main street (else arterial, else street) of the tiles around {@code near}, from its start, eye 3 blocks over
	 * the surface, looking along it up to 90 blocks. Null when no street of 24+ blocks is there.
	 */
	private static Frame street(ServerLevel level, WorldPlanner planner, SettlementSite site, double nx, double nz, List<String> notes) {
		dev.fractalbuildings.core.settlement.Street best = null; int bestRank = 9; double bestLen = 0;
		for (int[] t : siteTiles(planner, site)) {
			DistrictGrid.Tile tile = planner.grid().tile(t[0], t[1]);
			if (Math.hypot((tile.minX() + tile.maxX()) / 2.0 - nx, (tile.minZ() + tile.maxZ()) / 2.0 - nz) > 400) continue;
			for (var s : planner.plan(t[0], t[1]).plan().streets()) {
				int rank = switch (s.kind()) { case MAIN -> 0; case ARTERIAL -> 1; case STREET -> 2; default -> 9; };
				if (rank > 2 || s.points().size() < 2) continue;
				double len = 0;
				for (int i = 1; i < s.points().size(); i++) len += Math.hypot(s.points().get(i).x() - s.points().get(i - 1).x(), s.points().get(i).z() - s.points().get(i - 1).z());
				if (len < 24) continue;
				if (rank < bestRank || rank == bestRank && len > bestLen) { best = s; bestRank = rank; bestLen = len; }
			}
		}
		if (best == null) { notes.add("no street of 24+ blocks near the densest window; no street frame"); return null; }
		var pts = best.points(); int[] y = best.y();
		// start where the street is flat for the longest stretch (a street's end is often a retaining wall on a slope): the run ends when the surface leaves 4 of the start's level or after 90 blocks
		int first = 0, last = 1; double bestRun = -1;
		for (int i = 0; i < pts.size() - 1; i++) {
			double run = 0; int j = i;
			while (j + 1 < pts.size() && Math.abs(y[j + 1] - y[i]) <= 4 && run < 90) { run += Math.hypot(pts.get(j + 1).x() - pts.get(j).x(), pts.get(j + 1).z() - pts.get(j).z()); j++; }
			if (j > i && run > bestRun + 1e-9) { bestRun = run; first = i; last = j; }
		}
		double sx = pts.get(first).x() + 0.5, sz = pts.get(first).z() + 0.5, ex = pts.get(last).x() + 0.5, ez = pts.get(last).z() + 0.5;
		load(level, sx, sz); load(level, ex, ez);
		notes.add("street " + best.id() + " (" + best.kind().json + ", width " + best.width() + ", " + (int) bestLen + " long)");
		return new Frame("street", sx, y[first] + 1.4, sz, ex, y[last] + 1.6, ez, 40, 0, 0, 16);
	}

	/**
	 * The square frame: the plaza nearest the settlement's centre seen from its far side toward the hall, temple or market landmark nearest it
	 * (within 80 blocks of the plaza's middle), eye 3 over the paving. Null when the settlement has no plaza or no such landmark.
	 */
	private static Frame square(ServerLevel level, WorldPlanner planner, SettlementSite site, List<String> notes) {
		dev.fractalbuildings.core.settlement.Plaza pl = null; double bd = Double.MAX_VALUE;
		List<double[]> marks = new ArrayList<>(); // x, z, kind (0 hall, 1 temple) of the hall and temple lots: they are lots of those types, not landmarks
		for (int[] t : siteTiles(planner, site)) {
			var tp = planner.plan(t[0], t[1]);
			for (var p : tp.plan().plazas()) {
				double d = Math.hypot(p.x() + p.w() / 2.0 - site.centre().x(), p.z() + p.d() / 2.0 - site.centre().z());
				if (d < bd) { bd = d; pl = p; }
			}
			for (var l : tp.plan().lots()) if (l.builds() && (l.type().equals("hall") || l.type().equals("temple"))) marks.add(new double[]{l.x() + l.w() / 2.0, l.z() + l.d() / 2.0, l.type().equals("hall") ? 0 : 1});
		}
		if (pl == null) { notes.add("no plaza; no square frame"); return null; }
		double px = pl.x() + pl.w() / 2.0, pz = pl.z() + pl.d() / 2.0;
		double[] lm = null; double ld = 80;
		for (var m : marks) {
			double d = Math.hypot(m[0] - px, m[1] - pz);
			if (d < ld && d >= 6) { ld = d; lm = m; }
		}
		double tx, tz, dx, dz;
		if (lm != null) { tx = lm[0]; tz = lm[1]; dx = px - tx; dz = pz - tz; }
		else { tx = px; tz = pz; dx = px - site.centre().x(); dz = pz - site.centre().z(); } // no hall or temple: across the square itself, from the side away from the centre
		double dl = Math.hypot(dx, dz);
		if (dl < 1) { dx = 0; dz = 1; dl = 1; }
		// the camera stands on the plaza's paving at the edge farthest along the line from the target through the plaza's middle (trees and the neighbours' walls stand outside it)
		double ux = dx / dl, uz = dz / dl;
		double sx = Math.abs(ux) < 1e-6 ? Double.MAX_VALUE : pl.w() / 2.0 / Math.abs(ux), sz = Math.abs(uz) < 1e-6 ? Double.MAX_VALUE : pl.d() / 2.0 / Math.abs(uz);
		double reach = Math.max(2, Math.min(sx, sz) - 1.5);
		double cx = px + ux * reach, cz = pz + uz * reach;
		load(level, cx, cz); load(level, tx, tz);
		notes.add("square " + pl.id() + " (" + pl.kind() + " " + pl.w() + "x" + pl.d() + ") toward " + (lm != null ? (lm[2] == 0 ? "hall" : "temple") + " lot at " + (int) lm[0] + "," + (int) lm[1] : "its own middle (no hall or temple lot within 80)"));
		return new Frame("square", cx, pl.y() + 1.4, cz, tx, pl.y() + (lm != null ? 4 : 2), tz, 40, 0, 0, 16);
	}

	/**
	 * The side-on bridge frame: the longest run of a street over water (10+ blocks; bridge decks of a city take the road surface, so the
	 * deck role is not there to find), seen from 30+ blocks to one side (the side with lower ground), eye just over the deck. Null when no street crosses water.
	 */
	private static Frame bridge(ServerLevel level, TerrainProbe exact, WorldPlanner planner, SettlementSite site, List<String> notes) {
		double bestLen = 0, bx0 = 0, bz0 = 0, bx1 = 0, bz1 = 0, by = 0; String bid = "";
		for (int[] t : siteTiles(planner, site)) for (var st : planner.plan(t[0], t[1]).plan().streets()) {
			var pts = st.points(); int[] y = st.y();
			double runLen = 0, sx = 0, sz = 0, sy = 0; boolean in = false; double lx = 0, lz = 0, ly = 0;
			for (int i = 0; i + 1 < pts.size(); i++) {
				double dx = pts.get(i + 1).x() - pts.get(i).x(), dz = pts.get(i + 1).z() - pts.get(i).z(), len = Math.hypot(dx, dz);
				int steps = (int) Math.max(1, len / 2);
				for (int k = 0; k <= steps; k++) {
					double f = k / (double) steps, x = pts.get(i).x() + dx * f, z = pts.get(i).z() + dz * f, yy = y[i] + (y[i + 1] - y[i]) * f;
					boolean wet = exact.waterDepth((int) Math.floor(x), (int) Math.floor(z)) > 0;
					if (wet && !in) { in = true; sx = x; sz = z; sy = yy; runLen = 0; }
					if (wet) { runLen = Math.hypot(x - sx, z - sz); lx = x; lz = z; ly = yy; }
					if ((!wet || i + 2 == pts.size() && k == steps) && in) {
						in = false;
						if (runLen > bestLen) { bestLen = runLen; bx0 = sx; bz0 = sz; bx1 = lx; bz1 = lz; by = (sy + ly) / 2; bid = st.id(); }
					}
				}
			}
		}
		if (bestLen < 10) { notes.add("no street runs 10+ blocks over water; no bridge frame"); return null; }
		double mx = (bx0 + bx1) / 2, mz = (bz0 + bz1) / 2, dx = bx1 - bx0, dz = bz1 - bz0, dl = Math.max(1, Math.hypot(dx, dz));
		double nxA = -dz / dl, nzA = dx / dl; // perpendicular to the span
		double d = Math.max(30, bestLen * 0.8);
		double hiA = exact.floorY((int) (mx + nxA * d), (int) (mz + nzA * d)), hiB = exact.floorY((int) (mx - nxA * d), (int) (mz - nzA * d));
		double sg = hiA <= hiB ? 1 : -1;
		double cx = mx + sg * nxA * d, cz = mz + sg * nzA * d;
		load(level, cx, cz); load(level, mx, mz);
		notes.add(String.format(java.util.Locale.ROOT, "bridge on street %s: %.0f blocks over water, deck y %.0f", bid, bestLen, by));
		// 14 over the deck: clear of the canopy beside the water (the heightmap at the camera was read before its neighbours' trees grew: a camera in a tree is a green wall)
		return new Frame("bridge", cx, by + 14, cz, mx, by - 1, mz, 40, 0, 0, 16);
	}

	private static Prepared frames(ServerLevel level, WorldPlanner planner, TerrainProbe exact, String seed, SettlementSite site, int rd) {
		List<String> notes = new ArrayList<>();
		List<Frame> fr = new ArrayList<>();
		List<Mass> masses = masses(planner, site);
		if (masses.isEmpty()) throw new IllegalStateException("no planned building in the settlement");
		double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
		for (Mass m : masses) { x0 = Math.min(x0, m.x()); x1 = Math.max(x1, m.x()); z0 = Math.min(z0, m.z()); z1 = Math.max(z1, m.z()); }
		double span = Math.max(x1 - x0, z1 - z0) + 24;
		int view = rd * 16; // nothing beyond the render distance is drawn: the window's far edge stays inside it
		double side = Math.max(48, Math.min(span, (view - 16) / 1.3));
		boolean big = site.tier().ordinal() >= Tier.CITY.ordinal();
		notes.add(String.format(java.util.Locale.ROOT, "%d buildings, footprint x %.0f..%.0f z %.0f..%.0f (span %.0f), window side %.0f at rd %d", masses.size(), x0, x1, z0, z1, span, side, rd));
		Window a = densest(masses, side, null, big); // big: ranked by built volume, so the downtown of towers beats a wider suburb
		double[] ang = {Double.NaN};
		fr.add(sweep(exact, big ? "sweep-a" : "view", a, big ? 24 : 28, Double.NaN, 90, ang, null));
		if (big) {
			// the second sweep: the built rim looking in (the taper toward the edge, DS-18) when the footprint is wider than the window, else the same part from the other side
			Window b = span > side * 1.3 ? rim(masses, side, site.centre().x(), site.centre().z(), a) : null;
			double ox = 0, oz = 0;
			if (b != null) { ox = b.x() - site.centre().x(); oz = b.z() - site.centre().z(); }
			double ol = Math.hypot(ox, oz);
			if (b != null && ol > side * 0.5 && Math.hypot(b.x() - a.x(), b.z() - a.z()) > a.side() * 0.6) {
				fr.add(sweep(exact, "sweep-b", b, 26, Double.NaN, 90, new double[1], new double[]{-ox / ol, -oz / ol})); // camera on the core side, looking out over the taper to the rim
			} else {
				fr.add(sweep(exact, "sweep-b", a, 26, ang[0], 90, new double[1], null));
			}
		}
		// the settlement's own tiles around the densest window must be generated before their blocks are read
		DistrictGrid.Tile pick = planner.grid().tileAt((int) a.x(), (int) a.z());
		for (int z = pick.minZ() >> 4; z < pick.maxZ() >> 4; z++) for (int x = pick.minX() >> 4; x < pick.maxX() >> 4; x++) level.getChunk(x, z, ChunkStatus.FULL, true);
		// street level, every tier
		Frame st = street(level, planner, site, a.x(), a.z(), notes);
		if (st != null) fr.add(st);
		Frame sq = square(level, planner, site, notes);
		if (sq != null) fr.add(sq);
		if (big) {
			Frame br = bridge(level, exact, planner, site, notes);
			if (br != null) fr.add(br);
		}
		if (site.tier() == Tier.TOWN) {
			Frame in = interior(level, planner, pick, a, notes);
			if (in != null) fr.add(in); else notes.add("no ground-floor room of 4x4 or more in the densest tile; no interior frame");
		}
		for (Frame f : fr) notes.add(String.format(java.util.Locale.ROOT, "%s: camera %.0f %.0f %.0f -> target %.0f %.0f %.0f, window side %d with %d buildings", f.label(), f.x(), f.y(), f.z(), f.tx(), f.ty(), f.tz(), f.side(), f.bldgs()));
		return new Prepared(seed, site, fr, notes);
	}

	/**
	 * The interior frame: the largest ground-floor room (not a stair) of 4x4 or more among the buildings of the tile nearest the window centre,
	 * seen from the corner farthest from the nearest street, at eye height 1.62, looking at the opposite corner a little down: floor, ceiling and two walls.
	 */
	private static Frame interior(ServerLevel level, WorldPlanner planner, DistrictGrid.Tile pick, Window w, List<String> notes) {
		var plan = planner.plan(pick.ix(), pick.iz()).plan();
		List<int[]> street = new ArrayList<>();
		for (var s : plan.streets()) for (var p : s.points()) street.add(new int[]{p.x(), p.z()});
		Box3 bestRoom = null; long bestArea = 0; double bestD = Double.MAX_VALUE;
		for (BuildingPiece bp : planner.buildings(pick.ix(), pick.iz())) {
			var pl = bp.building().placement();
			for (var r : bp.building().shell().rooms()) {
				if (r.storey() != 0 || r.use().equals("stair")) continue;
				for (Box3 part : r.parts()) {
					Box3 wb = pl.toWorld(part);
					long area = (long) wb.sx() * wb.sz();
					if (wb.sx() < 4 || wb.sz() < 4 || wb.sy() < 2) continue;
					double d = Math.hypot(wb.x() + wb.sx() / 2.0 - w.x(), wb.z() + wb.sz() / 2.0 - w.z());
					if (area > bestArea || area == bestArea && d < bestD) { bestArea = area; bestRoom = wb; bestD = d; }
				}
			}
		}
		if (bestRoom == null) return null;
		Box3 rm = bestRoom;
		double cxm = rm.x() + rm.sx() / 2.0, czm = rm.z() + rm.sz() / 2.0;
		double sdx = 0, sdz = 0, sd = Double.MAX_VALUE;
		for (int[] s : street) { double d = Math.hypot(s[0] - cxm, s[1] - czm); if (d < sd) { sd = d; sdx = s[0]; sdz = s[1]; } }
		double fx = 0, fz = 0, far = -1; double ox = 0, oz = 0;
		for (int i = 0; i < 4; i++) {
			double px = i % 2 == 0 ? rm.x() + 0.7 : rm.maxX() - 0.7, pz = i / 2 == 0 ? rm.z() + 0.7 : rm.maxZ() - 0.7;
			double d = street.isEmpty() ? i : Math.hypot(px - sdx, pz - sdz);
			if (d > far) { far = d; fx = px; fz = pz; ox = i % 2 == 0 ? rm.maxX() - 0.7 : rm.x() + 0.7; oz = i / 2 == 0 ? rm.maxZ() - 0.7 : rm.z() + 0.7; }
		}
		load(level, fx, fz);
		// the floor: the first level at or above the room's base with air at the feet and the head and a solid block under (the box's y is the planned slab level)
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		Integer feet = null;
		for (int y = rm.y() - 1; y <= rm.y() + 3 && feet == null; y++) {
			if (level.getBlockState(p.set((int) fx, y, (int) fz)).isAir() || level.getBlockState(p.set((int) fx, y - 1, (int) fz)).isAir()) continue;
			if (level.getBlockState(p.set((int) fx, y + 1, (int) fz)).isAir() && level.getBlockState(p.set((int) fx, y + 2, (int) fz)).isAir()) feet = y + 1;
		}
		if (feet == null) {
			// first y over the base with two air blocks above a solid one
			for (int y = rm.y(); y <= rm.y() + 4 && feet == null; y++) {
				if (!level.getBlockState(p.set((int) fx, y - 1, (int) fz)).isAir() && level.getBlockState(p.set((int) fx, y, (int) fz)).isAir() && level.getBlockState(p.set((int) fx, y + 1, (int) fz)).isAir()) feet = y;
			}
		}
		if (feet == null) { notes.add("room " + rm + ": no standing level at its corner"); return null; }
		notes.add("interior: room " + rm + " from the corner " + (int) fx + "," + (int) fz);
		return new Frame("interior", fx, feet, fz, ox, feet + 1.62 - 0.4, oz, 30, 0, 0, 12);
	}
}
