package com.utdmod.network;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import com.utdmod.signals.TensionManager;
import com.utdmod.ritual.RitualHandler;
import com.utdmod.items.WardingCrystalItem;

public class UseCrystalPacket {
    
    public static final Identifier USE_CRYSTAL_ID = 
        new Identifier("utdmod", "use_crystal");
    
    public static void sendToServer() {
        PacketByteBuf buf = new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
        ClientPlayNetworking.send(USE_CRYSTAL_ID, buf);
    }
    
    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(USE_CRYSTAL_ID, 
            (server, player, handler, buf, responseSender) -> {
                server.execute(() -> {
                    handleCrystalUse(server, player);
                });
            });
    }
    
    private static void handleCrystalUse(net.minecraft.server.MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) return;
        
        ServerWorld world = player.getServerWorld();
        
        // Check if ritual can be performed
        if (RitualHandler.canPerformRitual(world, player)) {
            // Apply warding effect
            WardingCrystalItem.performWardingEffect(player);
            
            // Perform ritual
            RitualHandler.performWardingRitual(world, player);
            
            // Sync tension to all clients
            double currentTension = TensionManager.getTension();
            boolean stormActive = TensionManager.getTension() > 1.0;
            
            TensionSyncPacket.sendToAllPlayers(server, currentTension, stormActive);
            
            System.out.println("[CRYSTAL_USE] " + player.getName().getString() + " used Warding Crystal");
            
        } else {
            player.sendMessage(Text.literal("§cCannot use Warding Crystal now!"));
        }
    }
    
    public static PacketByteBuf write() {
        return new PacketByteBuf(io.netty.buffer.Unpooled.buffer());
    }
    
    public static void read(PacketByteBuf buf) {
        // No data to read - simple trigger packet
    }
}
