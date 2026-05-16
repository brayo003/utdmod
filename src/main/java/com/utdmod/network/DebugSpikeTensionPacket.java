package com.utdmod.network;

import com.utdmod.core.TensionManager;
import com.utdmod.core.TensionTraceClock;
import com.utdmod.core.TensionTraceLogger;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * Debug-only: client requests a small tension impulse on the server (authoritative).
 */
public final class DebugSpikeTensionPacket {

    public static final Identifier ID = new Identifier(com.utdmod.UTDMod.MOD_ID, "debug_tension_spike");

    private DebugSpikeTensionPacket() {}

    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) ->
            server.execute(() -> {
                double g0 = TensionManager.getTension();
                TensionManager.addEvent(0.5);
                double g1 = TensionManager.getTension();
                TensionTraceLogger.traceSystem(
                    TensionTraceClock.getServerTick(),
                    "DEBUG_SPIKE",
                    "impulse_buffer+0.5",
                    g1,
                    "global_before=" + String.format("%.4f", g0) + " global_after=" + String.format("%.4f", g1)
                );
            }));
    }

    public static void sendToServer() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        ClientPlayNetworking.send(ID, buf);
    }
}
