package com.utdmod.network;

import com.utdmod.client.TensionSyncState;
import com.utdmod.UTDMod;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Syncs aggregated substrate semantics for natural player presentation.
 */
public final class PresentationSyncPacket {
  public static final Identifier PRESENTATION_SYNC_ID = new Identifier(UTDMod.MOD_ID, "presentation_sync");

  private PresentationSyncPacket() {}

  public static void broadcast(MinecraftServer server, double stress, double stability, double prosperity,
                               double recovery, int corridorChunks, int settlementChunks,
                               double transportBiasX, double transportBiasZ) {
    for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
      PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
      buf.writeDouble(stress);
      buf.writeDouble(stability);
      buf.writeDouble(prosperity);
      buf.writeDouble(recovery);
      buf.writeInt(corridorChunks);
      buf.writeInt(settlementChunks);
      buf.writeDouble(transportBiasX);
      buf.writeDouble(transportBiasZ);
      ServerPlayNetworking.send(player, PRESENTATION_SYNC_ID, buf);
    }
  }

  @Environment(EnvType.CLIENT)
  public static void registerReceiver() {
    ClientPlayNetworking.registerGlobalReceiver(PRESENTATION_SYNC_ID, PresentationSyncPacket::onClientPacket);
  }

  @Environment(EnvType.CLIENT)
  private static void onClientPacket(MinecraftClient client, ClientPlayNetworkHandler handler,
                                     PacketByteBuf buf, PacketSender responseSender) {
    double stress = buf.readDouble();
    double stability = buf.readDouble();
    double prosperity = buf.readDouble();
    double recovery = buf.readDouble();
    int corridorChunks = buf.readInt();
    int settlementChunks = buf.readInt();
    double transportBiasX = buf.readDouble();
    double transportBiasZ = buf.readDouble();
    client.execute(() -> TensionSyncState.applyPresentationSnapshot(
        stress, stability, prosperity, recovery, corridorChunks, settlementChunks, transportBiasX, transportBiasZ));
  }
}
