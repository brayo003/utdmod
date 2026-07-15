package com.utdmod.network;

import com.utdmod.UTDMod;
import com.utdmod.research.agent.ResearchClientAgentManager;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Client → server: execution status and completion reports. */
public final class ResearchAgentStatusPacket {
  public static final Identifier ID = new Identifier(UTDMod.MOD_ID, "research_agent_status");

  private ResearchAgentStatusPacket() {}

  public static void registerServer() {
    ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
      String agentId = buf.readString();
      long objectiveId = buf.readVarLong();
      BlockPos pos = buf.readBlockPos();
      boolean atTarget = buf.readBoolean();
      boolean idle = buf.readBoolean();
      boolean mining = buf.readBoolean();
      boolean exploring = buf.readBoolean();
      boolean building = buf.readBoolean();
      boolean buildComplete = buf.readBoolean();
      String currentTask = buf.readString();
      server.execute(() -> ResearchClientAgentManager.onClientStatus(
          player, agentId, objectiveId, pos, atTarget, idle, mining, exploring, building, buildComplete, currentTask
      ));
    });
  }

  public static void send(
      String agentId,
      long objectiveId,
      BlockPos pos,
      boolean atTarget,
      boolean idle,
      boolean mining,
      boolean exploring,
      boolean building,
      boolean buildComplete,
      String currentTask
  ) {
    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
    buf.writeString(agentId == null ? "agent0" : agentId);
    buf.writeVarLong(objectiveId);
    buf.writeBlockPos(pos == null ? BlockPos.ORIGIN : pos);
    buf.writeBoolean(atTarget);
    buf.writeBoolean(idle);
    buf.writeBoolean(mining);
    buf.writeBoolean(exploring);
    buf.writeBoolean(building);
    buf.writeBoolean(buildComplete);
    buf.writeString(currentTask == null ? "idle" : currentTask);
    ClientPlayNetworking.send(ID, buf);
  }
}
