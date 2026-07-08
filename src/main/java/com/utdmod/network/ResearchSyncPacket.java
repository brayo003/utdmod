package com.utdmod.network;

import com.utdmod.client.TensionSyncState;
import com.utdmod.research.ResearchSnapshot;
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

public final class ResearchSyncPacket {
    public static final Identifier RESEARCH_SYNC_ID = new Identifier(UTDMod.MOD_ID, "research_sync");

    private ResearchSyncPacket() {}

    public static void sendToPlayer(ServerPlayerEntity player, ResearchSnapshot snapshot) {
        if (player == null || snapshot == null) return;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(snapshot.mode != null ? snapshot.mode : "default");
        buf.writeString(snapshot.narrative != null ? snapshot.narrative : "");
        buf.writeInt(snapshot.activeRegions);
        buf.writeInt(snapshot.largestRegionChunks);
        buf.writeInt(snapshot.scarCount);
        buf.writeInt(snapshot.waveCount);
        buf.writeInt(snapshot.oscillationCount);
        buf.writeDouble(snapshot.fieldDepthMean);
        buf.writeDouble(snapshot.transportX);
        buf.writeDouble(snapshot.transportZ);
        buf.writeDouble(snapshot.regionCenterX);
        buf.writeDouble(snapshot.regionCenterZ);
        buf.writeInt(snapshot.regionChunkCount);
        ServerPlayNetworking.send(player, RESEARCH_SYNC_ID, buf);
    }

    public static void sendToAllPlayers(MinecraftServer server, ResearchSnapshot snapshot) {
        if (server == null) return;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendToPlayer(player, snapshot);
        }
    }

    @Environment(EnvType.CLIENT)
    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(RESEARCH_SYNC_ID, ResearchSyncPacket::onClientPacket);
    }

    @Environment(EnvType.CLIENT)
    private static void onClientPacket(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        String mode = buf.readString();
        String narrative = buf.readString();
        int activeRegions = buf.readInt();
        int largestRegionChunks = buf.readInt();
        int scarCount = buf.readInt();
        int waveCount = buf.readInt();
        int oscillationCount = buf.readInt();
        double fieldDepthMean = buf.readDouble();
        double transportX = buf.readDouble();
        double transportZ = buf.readDouble();
        double regionCenterX = buf.readDouble();
        double regionCenterZ = buf.readDouble();
        int regionChunkCount = buf.readInt();
        client.execute(() -> TensionSyncState.applyResearchSnapshot(mode, narrative, activeRegions, largestRegionChunks, scarCount, waveCount, oscillationCount, fieldDepthMean, transportX, transportZ, regionCenterX, regionCenterZ, regionChunkCount));
    }
}
