package dev.fractalbuildings.gametest.fbnote;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.settlement.DistrictGrid;
import dev.fractalbuildings.core.world.SettlementInfo;
import dev.fractalbuildings.core.settlement.Tier;
import dev.fractalbuildings.core.world.BuildingPiece;
import dev.fractalbuildings.core.world.WorldPlanner;
import dev.fractalbuildings.mod.worldgen.WorldPlanners;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * FBNOTE: {@code /fbnote test} typed in a singleplayer world (seed 987654321) standing in a room of a settlement building writes the
 * PNG and the sidecar JSON, and the planner fields (settlement, tier, lot, building, room) are filled in. The command is registered
 * here because the switch is on by default (no flag is set; FBNOTEON); "absent when switched off" is {@code FbNoteCommandTest}.
 */
public class FbNoteClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (dev.fractalbuildings.gametest.compat.CompatOnly.skip("FbNoteClientGameTest")) return;
		if (dev.fractalbuildings.gametest.perf.PerfGate.skipOthers()) return;
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).adjustSettings(c -> c.setSeed("987654321")).create()) {
			context.runOnClient(client -> client.options.renderDistance().set(6));
			AtomicReference<int[]> spot = new AtomicReference<>();
			AtomicReference<String> expect = new AtomicReference<>();
			world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				WorldPlanner planner = WorldPlanners.of(level, level.getChunkSource().getGenerator()).orElseThrow();
				SettlementInfo info = null;
				for (int r = 1; r <= 3 && info == null; r++) info = planner.nearest(0, 0, Tier.VILLAGE, r).orElse(null);
				if (info == null) throw new IllegalStateException("no village within 3 cells of the origin");
				DistrictGrid.Tile t0 = planner.grid().tileAt(info.site().centre().x(), info.site().centre().z());
				for (int d = 0; d <= 3 && spot.get() == null; d++) for (int dz = -d; dz <= d && spot.get() == null; dz++) for (int dx = -d; dx <= d && spot.get() == null; dx++) {
					int ix = t0.ix() + dx, iz = t0.iz() + dz;
					if (planner.tile(ix, iz).isEmpty()) continue;
					for (BuildingPiece b : planner.buildings(ix, iz)) {
						if (b.building().shell().rooms().isEmpty()) continue;
						Room r = b.building().shell().rooms().get(0);
						Box3 p = b.building().placement().toWorld(r.parts().get(0));
						spot.set(new int[] {p.x() + p.sx() / 2, p.y(), p.z() + p.sz() / 2, ix, iz});
						expect.set(b.building().id() + "|" + b.brief().id() + "|" + r.id());
						DistrictGrid.Tile pick = planner.grid().tile(ix, iz);
						for (int cz = pick.minZ() >> 4; cz < pick.maxZ() >> 4; cz++) for (int cx = pick.minX() >> 4; cx < pick.maxX() >> 4; cx++) level.getChunk(cx, cz, ChunkStatus.FULL, true);
						break;
					}
				}
				if (spot.get() == null) throw new IllegalStateException("no building with a room near the village");
			});
			int[] s = spot.get();
			world.getServer().runCommand("gamemode creative @p");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("tp @p " + (s[0] + 0.5) + " " + s[1] + " " + (s[2] + 0.5) + " 0 20");
			context.waitTicks(60);
			try {
				world.getConnection().waitForChunksRender();
			} catch (AssertionError e) {
				System.out.println("FBNOTE chunks did not finish rendering in time; going on");
			}
			context.waitTicks(20);

			context.runOnClient(client -> {
				if (ClientCommands.getActiveDispatcher() == null || ClientCommands.getActiveDispatcher().getRoot().getChild("fbnote") == null) throw new AssertionError("/fbnote is not registered by default");
			});
			context.runOnClient(client -> client.player.connection.sendCommand("fbnote test, with \"quotes\" and 123"));
			File[] found = new File[2];
			for (int i = 0; i < 400 && (found[0] == null || found[1] == null); i++) {
				context.waitTicks(5);
				context.runOnClient(client -> {
					File dir = new File(client.gameDirectory, "screenshots");
					File[] fs = dir.listFiles((d, n) -> n.matches("\\d{4}-\\d\\d-\\d\\d_\\d\\d\\.\\d\\d\\.\\d\\d(_\\d+)?\\.(png|json)"));
					if (fs == null) return;
					for (File f : fs) {
						if (!f.getName().endsWith(".json")) continue;
						File png = new File(dir, f.getName().replace(".json", ".png"));
						if (png.exists() && png.length() > 0 && f.length() > 0) { found[0] = png; found[1] = f; }
					}
				});
			}
			if (found[0] == null) throw new AssertionError("no screenshot + sidecar pair after the command (looked in the game directory's screenshots/)");
			try {
				byte[] head = Files.readAllBytes(found[0].toPath());
				if (head.length < 5000 || (head[0] & 0xff) != 0x89 || head[1] != 'P' || head[2] != 'N' || head[3] != 'G') throw new AssertionError("not a PNG of a real frame: " + found[0] + " " + head.length + " bytes");
				JsonObject j = JsonParser.parseString(Files.readString(found[1].toPath())).getAsJsonObject();
				System.out.println("FBNOTE sidecar " + found[1] + ":\n" + Files.readString(found[1].toPath()));
				check("fb-playtest-note/1".equals(j.get("format").getAsString()), "format");
				check("test, with \"quotes\" and 123".equals(j.get("comment").getAsString()), "comment " + j.get("comment"));
				check(found[0].getName().equals(j.get("screenshot").getAsString()), "screenshot name");
				check("987654321".equals(j.get("seed").getAsString()), "seed " + j.get("seed"));
				check(j.get("dimension").getAsString().equals("minecraft:overworld"), "dimension");
				JsonObject pos = j.getAsJsonObject("position");
				check(Math.abs(pos.get("x").getAsDouble() - (s[0] + 0.5)) < 2 && Math.abs(pos.get("z").getAsDouble() - (s[2] + 0.5)) < 2, "position " + pos);
				check(j.has("biome") && !j.get("biome").isJsonNull(), "biome");
				check(j.has("time") && j.has("gameTime") && j.has("mod") && j.has("target"), "time, gameTime, mod, target keys");
				JsonObject pl = j.getAsJsonObject("planner");
				check(pl != null && pl.get("available").getAsBoolean(), "planner available: " + pl);
				check(!pl.get("settlement").isJsonNull() && pl.getAsJsonObject("settlement").has("id") && pl.getAsJsonObject("settlement").has("tier"), "settlement");
				check(pl.has("district") && pl.has("tile"), "district and tile");
				String[] e = expect.get().split("\\|");
				check(e[0].equals(pl.getAsJsonObject("building").get("id").getAsString()), "building id " + pl.get("building") + " expected " + e[0]);
				check(e[1].equals(pl.getAsJsonObject("lot").get("id").getAsString()), "lot id " + pl.get("lot") + " expected " + e[1]);
				check(pl.getAsJsonObject("building").has("type") && pl.getAsJsonObject("building").has("style") && pl.getAsJsonObject("building").has("seed"), "building type, style, seed");
				check(pl.has("room"), "room: " + pl);
			} catch (java.io.IOException ex) {
				throw new RuntimeException(ex);
			}
			context.takeScreenshot("fbnote-room");
			// the remote-server path as an operator: forced remote, the note must get the seed from vanilla /seed (the player of a test world may run it)
			context.runOnClient(client -> {
				if (client.getConnection().getCommands().getRoot().getChild("seed") == null) throw new AssertionError("test player cannot see /seed");
				dev.fractalbuildings.mod.client.FbNoteClient.testForceRemote = true;
			});
			try {
				File first = found[1];
				context.runOnClient(client -> client.player.connection.sendCommand("fbnote remote op"));
				File[] second = new File[1];
				for (int i = 0; i < 200 && second[0] == null; i++) {
					context.waitTicks(5);
					context.runOnClient(client -> {
						File[] fs = new File(client.gameDirectory, "screenshots").listFiles((d, n) -> n.endsWith(".json") && !n.equals(first.getName()));
						if (fs != null) for (File f : fs) if (f.length() > 0) second[0] = f;
					});
				}
				if (second[0] == null) throw new AssertionError("FBNOTE: no second note");
				JsonObject j2 = JsonParser.parseString(Files.readString(second[0].toPath())).getAsJsonObject();
				System.out.println("FBNOTE remote sidecar:\n" + Files.readString(second[0].toPath()));
				check("remote op".equals(j2.get("comment").getAsString()), "remote comment");
				check(!j2.getAsJsonObject("planner").get("available").getAsBoolean(), "remote note has no planner data");
				check(j2.get("seed") != null && "987654321".equals(j2.get("seed").getAsString()), "op seed from /seed: " + j2.get("seed"));
			} catch (java.io.IOException ex) {
				throw new RuntimeException(ex);
			} finally {
				dev.fractalbuildings.mod.client.FbNoteClient.testForceRemote = false;
			}
		}
	}

	static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FBNOTE: " + what);
	}
}
