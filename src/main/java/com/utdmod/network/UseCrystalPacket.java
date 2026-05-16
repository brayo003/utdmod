package com.utdmod.network;

import com.utdmod.core.TensionManager;
import com.utdmod.core.TensionTraceClock;
import com.utdmod.ritual.RitualHandler;
import com.utdmod.tick.TensionLogger;
import com.utdmod.tension.ChunkTensionManager;
import com.utdmod.UTDMod;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;

public class UseCrystalPacket {

    public static final Identifier USE_CRYSTAL_ID = new Identifier(UTDMod.MOD_ID, "use_crystal");

    public static void sendToServer() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        ClientPlayNetworking.send(USE_CRYSTAL_ID, buf);
    }

    public static void registerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(USE_CRYSTAL_ID,
            (server, player, handler, buf, responseSender) ->
                server.execute(() -> handleCrystalUse(server, player)));
    }

    private static void handleCrystalUse(MinecraftServer server, ServerPlayerEntity player) {
        if (player == null) return;

        ServerWorld world = player.getServerWorld();

        if (RitualHandler.canPerformRitual(world, player)) {
            ChunkPos cp = new ChunkPos(player.getBlockPos());
            double g0 = TensionManager.getTension();
            double l0 = ChunkTensionManager.getLocalTension(world, cp);
            RitualHandler.performWardingRitual(world, player);
            double g1 = TensionManager.getTension();
            double l1 = ChunkTensionManager.getLocalTension(world, cp);

            TensionLogger.traceWithGlobals(
                TensionTraceClock.getServerTick(),
                "RITUAL",
                "crystal_use",
                "warding_ritual",
                cp,
                player,
                world,
                l1 - l0,
                l1,
                g0,
                g1
            );

            double currentTension = TensionManager.getTension();
            boolean stormActive = TensionManager.isStormActive();

            TensionSyncPacket.sendToAllPlayers(server, currentTension, stormActive);
        } else {
            player.sendMessage(Text.literal("Cannot use Warding Crystal now!"));
        }
    }
}
