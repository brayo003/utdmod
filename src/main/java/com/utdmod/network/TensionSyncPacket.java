package com.utdmod.network;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.utdmod.signals.TensionManager;

public class TensionSyncPacket {
    
    public static final Identifier TENSION_SYNC_ID = 
        new Identifier("utdmod", "tension_sync");
    
    public static void sendToPlayer(ServerPlayerEntity player, double tension, boolean stormActive) {
        if (player == null) return;
        
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeDouble(tension);
        buf.writeBoolean(stormActive);
        
        ServerPlayNetworking.send(player, TENSION_SYNC_ID, buf);
    }
    
    public static void sendToAllPlayers(net.minecraft.server.MinecraftServer server, double tension, boolean stormActive) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendToPlayer(player, tension, stormActive);
        }
    }
    
    public static void registerReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(TENSION_SYNC_ID,
            (client, handler, buf, responseSender) -> {
                double tension = buf.readDouble();
                boolean stormActive = buf.readBoolean();
                
                client.execute(() -> {
                    // Update client-side tension state
                    TensionManager.setTension(tension);
                    System.out.println("[TENSION_SYNC] Received: " + tension + ", Storm: " + stormActive);
                });
            }
        );
    }
    
    public static PacketByteBuf write(double tension, boolean stormActive) {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeDouble(tension);
        buf.writeBoolean(stormActive);
        return buf;
    }
    
    public static TensionData read(PacketByteBuf buf) {
        double tension = buf.readDouble();
        boolean stormActive = buf.readBoolean();
        return new TensionData(tension, stormActive);
    }
    
    public static class TensionData {
        public final double tension;
        public final boolean stormActive;
        
        public TensionData(double tension, boolean stormActive) {
            this.tension = tension;
            this.stormActive = stormActive;
        }
    }
}
