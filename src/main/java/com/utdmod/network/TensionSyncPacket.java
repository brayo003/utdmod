package com.utdmod.network;

import com.utdmod.client.TensionSyncState;
import com.utdmod.core.RegionDiagnosticsManager;
import com.utdmod.UTDMod;
import com.utdmod.tension.ChunkTensionManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

public class TensionSyncPacket {

    public static final Identifier TENSION_SYNC_ID = new Identifier(UTDMod.MOD_ID, "tension_sync");

    public static void sendToPlayer(
        ServerPlayerEntity player,
        double tension,
        boolean stormActive,
        double localTension,
        double regionAvg,
        double regionMax,
        int regionId,
        int regionAge,
        double regionMaturity,
        int regionChunkCount,
        int regionStateOrdinal
    ) {
        if (player == null) return;

        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeDouble(tension);
        buf.writeBoolean(stormActive);
        buf.writeDouble(localTension);
        buf.writeDouble(regionAvg);
        buf.writeDouble(regionMax);
        buf.writeInt(regionId);
        buf.writeInt(regionAge);
        buf.writeDouble(regionMaturity);
        buf.writeInt(regionChunkCount);
        buf.writeInt(regionStateOrdinal);

        ServerPlayNetworking.send(player, TENSION_SYNC_ID, buf);
    }

    public static void sendToAllPlayers(MinecraftServer server, double tension, boolean stormActive) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ChunkPos cp = new ChunkPos(player.getBlockPos());
            double local = ChunkTensionManager.getLocalTension(player.getServerWorld(), cp);
            RegionDiagnosticsManager.RegionSnapshot snap = RegionDiagnosticsManager.get(cp.x, cp.z);
            double rAvg = snap != null ? snap.avg : local;
            double rMax = snap != null ? snap.max : local;
            int regionId = 0;
            int regionAge = 0;
            double regionMaturity = 0.0;
            int regionChunkCount = 0;
            int regionState = 0;
            com.utdmod.core.Region r = com.utdmod.core.RegionManager.getRegionForChunk(player.getServerWorld(), cp);
            if (r != null) {
                regionId = r.getId();
                regionAge = r.getAge();
                regionMaturity = r.getMaturity();
                regionChunkCount = r.getChunkCount();
                regionState = r.getState().ordinal();
            }
            sendToPlayer(player, tension, stormActive, local, rAvg, rMax, regionId, regionAge, regionMaturity, regionChunkCount, regionState);
        }
    }

    @Environment(EnvType.CLIENT)
    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(TENSION_SYNC_ID, TensionSyncPacket::onClientPacket);
    }

    @Environment(EnvType.CLIENT)
    private static void onClientPacket(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender responseSender) {
        double tension = buf.readDouble();
        boolean stormActive = buf.readBoolean();
        double localTension = buf.readDouble();
        double regionAvg = buf.readableBytes() >= 8 ? buf.readDouble() : localTension;
        double regionMax = buf.readableBytes() >= 8 ? buf.readDouble() : localTension;
        int regionId = buf.readableBytes() >= 4 ? buf.readInt() : 0;
        int regionAge = buf.readableBytes() >= 4 ? buf.readInt() : 0;
        double regionMaturity = buf.readableBytes() >= 8 ? buf.readDouble() : 0.0;
        int regionChunkCount = buf.readableBytes() >= 4 ? buf.readInt() : 0;
        int regionStateOrdinal = buf.readableBytes() >= 4 ? buf.readInt() : 0;
        client.execute(() -> TensionSyncState.applySnapshot(tension, stormActive, localTension, regionAvg, regionMax, regionId, regionAge, regionMaturity, regionChunkCount, regionStateOrdinal));
    }

    public static PacketByteBuf write(double tension, boolean stormActive, double localTension, double regionAvg, double regionMax) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeDouble(tension);
        buf.writeBoolean(stormActive);
        buf.writeDouble(localTension);
        buf.writeDouble(regionAvg);
        buf.writeDouble(regionMax);
        return buf;
    }

    public static TensionData read(PacketByteBuf buf) {
        double tension = buf.readDouble();
        boolean stormActive = buf.readBoolean();
        double local = buf.readDouble();
        double rAvg = buf.readableBytes() >= 8 ? buf.readDouble() : local;
        double rMax = buf.readableBytes() >= 8 ? buf.readDouble() : local;
        return new TensionData(tension, stormActive, local, rAvg, rMax);
    }

    public static class TensionData {
        public final double tension;
        public final boolean stormActive;
        public final double localTension;
        public final double regionAvg;
        public final double regionMax;

        public TensionData(double tension, boolean stormActive, double localTension, double regionAvg, double regionMax) {
            this.tension = tension;
            this.stormActive = stormActive;
            this.localTension = localTension;
            this.regionAvg = regionAvg;
            this.regionMax = regionMax;
        }
    }
}
