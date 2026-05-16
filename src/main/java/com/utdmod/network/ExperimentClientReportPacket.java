package com.utdmod.network;

import com.utdmod.diag.DiagnosticLogs;
import com.utdmod.UTDMod;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Client → integrated server: sparse TEST4 line for terminal observability.
 */
public final class ExperimentClientReportPacket {

    public static final Identifier ID = new Identifier(UTDMod.MOD_ID, "exp_client_feel");

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            double perceived = buf.readDouble();
            int audio = buf.readVarInt();
            double overlay = buf.readDouble();
            int hostiles = buf.readVarInt();
            server.execute(() -> DiagnosticLogs.test4(perceived, audio, overlay, hostiles));
        });
    }

    public static void send(MinecraftClient client, double perceived, int audio, double overlay, int hostiles) {
        if (client.getServer() == null) return;
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeDouble(perceived);
        buf.writeVarInt(audio);
        buf.writeDouble(overlay);
        buf.writeVarInt(hostiles);
        ClientPlayNetworking.send(ID, buf);
    }
}
