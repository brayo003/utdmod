package com.utdmod.network;

import com.utdmod.UTDMod;
import com.utdmod.research.agent.ResearchObjectiveType;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Server → client: high-level protocol objective for Baritone execution. */
public final class ResearchAgentObjectivePacket {
  public static final Identifier ID = new Identifier(UTDMod.MOD_ID, "research_agent_objective");

  private ResearchAgentObjectivePacket() {}

  public static void send(
      ServerPlayerEntity player,
      long objectiveId,
      String agentId,
      ResearchObjectiveType type,
      BlockPos target,
      int centerX,
      int centerZ,
      String[] blockNames
  ) {
    if (player == null) return;
    PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
    buf.writeVarLong(objectiveId);
    buf.writeString(agentId == null ? "agent0" : agentId);
    buf.writeVarInt(type.id());
    buf.writeBlockPos(target == null ? BlockPos.ORIGIN : target);
    buf.writeVarInt(centerX);
    buf.writeVarInt(centerZ);
    buf.writeVarInt(blockNames == null ? 0 : blockNames.length);
    if (blockNames != null) {
      for (String name : blockNames) {
        buf.writeString(name);
      }
    }
    ServerPlayNetworking.send(player, ID, buf);
  }

  public static void registerClient(ResearchAgentObjectiveHandler handler) {
    ClientPlayNetworking.registerGlobalReceiver(ID, (client, networkHandler, buf, responseSender) -> {
      long objectiveId = buf.readVarLong();
      String agentId = buf.readString();
      ResearchObjectiveType type = ResearchObjectiveType.fromId(buf.readVarInt());
      BlockPos target = buf.readBlockPos();
      int centerX = buf.readVarInt();
      int centerZ = buf.readVarInt();
      int nameCount = buf.readVarInt();
      String[] blockNames = new String[nameCount];
      for (int i = 0; i < nameCount; i++) {
        blockNames[i] = buf.readString();
      }
      client.execute(() -> handler.onObjective(client, objectiveId, agentId, type, target, centerX, centerZ, blockNames));
    });
  }

  @FunctionalInterface
  public interface ResearchAgentObjectiveHandler {
    void onObjective(
        MinecraftClient client,
        long objectiveId,
        String agentId,
        ResearchObjectiveType type,
        BlockPos target,
        int centerX,
        int centerZ,
        String[] blockNames
    );
  }
}
