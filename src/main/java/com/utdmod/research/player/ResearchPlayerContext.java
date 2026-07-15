package com.utdmod.research.player;

import com.utdmod.research.agent.RemoteResearchController;
import com.utdmod.research.agent.ResearchAgentController;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Shared mutable context passed between FSM states each tick. */
public final class ResearchPlayerContext {
  public final ServerWorld world;
  public final ServerPlayerEntity player;
  public final DeterministicRandom random;
  public final ResearchPlayerSnapshot snapshot;

  public final ResearchAgentController controller;
  public ResearchPlayerVision vision;
  public ResearchPlayerInventoryManager inventory;
  public ResearchPlayerCrafting crafting;
  public ResearchPlayerCombat combat;

  public BlockPos currentTarget = null;
  public String lastFailure = "";

  public ResearchPlayerContext(ServerWorld world, ServerPlayerEntity player, ResearchPlayerSnapshot snapshot) {
    this.world = world;
    this.player = player;
    this.snapshot = snapshot;
    this.random = new DeterministicRandom(snapshot.rngState == 0
        ? DeterministicRandom.fromSeeds(world.getSeed(), snapshot.experimentSeed != 0
                ? snapshot.experimentSeed
                : snapshot.botSeed)
            .nextLong()
        : snapshot.rngState);
    this.controller = new RemoteResearchController(world, player, snapshot);
    this.vision = new ResearchPlayerVision(world, player);
    this.inventory = new ResearchPlayerInventoryManager(player);
    this.crafting = new ResearchPlayerCrafting(player);
    this.combat = new ResearchPlayerCombat(world, player);
  }

  public void syncRng() {
    snapshot.rngState = random.getState();
  }
}
