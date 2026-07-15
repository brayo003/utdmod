package com.utdmod.client.research;

import com.utdmod.network.ResearchAgentObjectivePacket;
import com.utdmod.network.ResearchAgentRegisterPacket;
import com.utdmod.network.ResearchAgentStatusPacket;
import com.utdmod.research.agent.ResearchObjectiveType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

/**
 * Client-side research agent controller.
 * Receives high-level objectives from the server and delegates execution to Baritone.
 */
public final class ResearchClientController {
  private static final ResearchClientController INSTANCE = new ResearchClientController();

  private String agentId = "agent0";
  private String experimentId = "default_experiment";
  private int botSeed = 0;
  private boolean registered;
  private boolean handlersInstalled;
  private long lastObjectiveId = -1;
  private ClientBaritoneExecutor executor;

  private ResearchClientController() {}

  public static ResearchClientController get() {
    return INSTANCE;
  }

  public void installHandlers() {
    if (handlersInstalled) return;
    handlersInstalled = true;
    ResearchAgentObjectivePacket.registerClient(this::onObjective);
  }

  public void configure(String agentId, String experimentId, int botSeed) {
    if (agentId != null && !agentId.isBlank()) {
      this.agentId = agentId;
    }
    if (experimentId != null && !experimentId.isBlank()) {
      this.experimentId = experimentId;
    }
    this.botSeed = botSeed;
  }

  public void onWorldJoin(MinecraftClient client) {
    registered = false;
    executor = null;
    lastObjectiveId = -1;
    String propAgent = System.getProperty("utdmod.agentId");
    if (propAgent != null && !propAgent.isBlank()) {
      agentId = propAgent.trim();
    }
    String propExperiment = System.getProperty("utdmod.experimentId");
    if (propExperiment != null && !propExperiment.isBlank()) {
      experimentId = propExperiment.trim();
    }
    String propSeed = System.getProperty("utdmod.botSeed");
    if (propSeed != null && !propSeed.isBlank()) {
      try {
        botSeed = Integer.parseInt(propSeed.trim());
      } catch (NumberFormatException ignored) {
      }
    }
  }

  public void tick(MinecraftClient client) {
    if (client.player == null || client.world == null || client.getNetworkHandler() == null) {
      return;
    }
    if (!registered) {
      ResearchAgentRegisterPacket.send(agentId, experimentId, botSeed);
      registered = true;
      executor = new ClientBaritoneExecutor(client);
    }
    if (executor == null) {
      executor = new ClientBaritoneExecutor(client);
    }
    executor.tick();

    if (client.world.getTime() % 5 == 0) {
      BlockPos pos = client.player.getBlockPos();
      ResearchAgentStatusPacket.send(
          agentId,
          lastObjectiveId,
          pos,
          executor.isAtTarget(2.5),
          executor.isIdle(),
          executor.isMining(),
          executor.isExploring(),
          executor.isBuilding(),
          executor.isBuildComplete(),
          executor.currentTask()
      );
    }
  }

  private void onObjective(
      MinecraftClient client,
      long objectiveId,
      String agentId,
      ResearchObjectiveType type,
      BlockPos target,
      int centerX,
      int centerZ,
      String[] blockNames
  ) {
    if (agentId != null && !agentId.isBlank()) {
      this.agentId = agentId;
    }
    lastObjectiveId = objectiveId;
    if (executor == null && client != null) {
      executor = new ClientBaritoneExecutor(client);
    }
    if (executor != null) {
      executor.applyObjective(type, target, centerX, centerZ, blockNames);
    }
  }
}
