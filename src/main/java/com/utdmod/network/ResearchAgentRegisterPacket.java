package com.utdmod.network;

import com.utdmod.UTDMod;
import com.utdmod.research.agent.ResearchClientAgentManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** Client → server: register this connection as a research agent. */
public final class ResearchAgentRegisterPacket {
  public static final Identifier ID = new Identifier(UTDMod.MOD_ID, "research_agent_register");

  private ResearchAgentRegisterPacket() {}

  public static void registerServer() {
    ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
      String agentId = buf.readString();
      String experimentId = buf.readString();
      int botSeed = buf.readVarInt();
      server.execute(() -> ResearchClientAgentManager.onClientRegister(server, player, agentId, experimentId, botSeed));
    });
  }

  public static void send(String agentId, String experimentId, int botSeed) {
    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
    buf.writeString(agentId == null ? "agent0" : agentId);
    buf.writeString(experimentId == null ? "default_experiment" : experimentId);
    buf.writeVarInt(botSeed);
    ClientPlayNetworking.send(ID, buf);
  }
}
