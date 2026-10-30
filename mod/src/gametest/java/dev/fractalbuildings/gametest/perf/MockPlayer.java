package dev.fractalbuildings.gametest.perf;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;

/**
 * A real {@link ServerPlayer} in the player list with a dead-end (embedded) connection, the way vanilla's {@code GameTestHelper} makes one:
 * chunk loading, tracking and teleport go through the same code as a client's player, with no client and no renderer.
 */
final class MockPlayer {
	private MockPlayer() {}

	/** Adds a spectator to the player list; {@code viewDistance} is what its "client" requests. */
	static ServerPlayer join(MinecraftServer server, ServerLevel level, String name, int viewDistance) {
		return join(server, level, name, viewDistance, GameType.SPECTATOR);
	}

	/** As above with a game mode (PORTALCOLD uses creative: a portal takes a creative player at once). */
	static ServerPlayer join(MinecraftServer server, ServerLevel level, String name, int viewDistance, GameType mode) {
		GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name);
		ClientInformation d = ClientInformation.createDefault();
		ClientInformation info = new ClientInformation(d.language(), viewDistance, d.chatVisibility(), d.chatColors(), d.modelCustomisation(), d.mainHand(), d.textFilteringEnabled(), d.allowsListing(), d.particleStatus());
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
		ServerPlayer p = new ServerPlayer(server, level, profile, info) {
			@Override public GameType gameMode() { return mode; }
		};
		Connection c = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(c);
		server.getPlayerList().placeNewPlayer(c, p, cookie);
		p.setGameMode(mode);
		return p;
	}

	/**
	 * Moves the player and its chunk view, as a client's move packet does. {@code teleportTo} alone only moves the entity: vanilla moves the
	 * player's chunk tickets in {@code ServerGamePacketListenerImpl.handlePlayerPositionChange} (and {@code ServerPlayer.tick} only while
	 * spectating another entity), which a dead connection never reaches, so before PERFSRV r5 the flight loaded no chunk along its path.
	 */
	static void flyTo(ServerPlayer p, double x, double y, double z) {
		p.teleportTo(x, y, z);
		p.level().getChunkSource().move(p);
	}
}
