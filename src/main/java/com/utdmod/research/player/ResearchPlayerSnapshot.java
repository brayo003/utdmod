package com.utdmod.research.player;

import net.minecraft.util.math.BlockPos;

/** Serializable controller state for persistence. */
public final class ResearchPlayerSnapshot {
  public ResearchPlayerState state = ResearchPlayerState.IDLE;
  public ResearchPlayerState previousState = ResearchPlayerState.IDLE;
  public ResearchPlayerState resumeState = ResearchPlayerState.SURVEY;
  public String transitionReason = "init";

  // Experiment protocol orchestration (injected by ExperimentManager).
  public String agentId = "agent0";
  public String experimentId = "default_experiment";
  public String protocolId = "PASSIVE_WANDER";
  public String previousProtocolId = "PASSIVE_WANDER";
  public int durationTicks = 2000;
  public int allowedRadiusChunks = 8;
  public String loggingLabel = "";
  // Seed for protocol-owned deterministic decisions.
  public int experimentSeed = 0;

  // Protocol-specific goals (kept minimal; protocols interpret what they need).
  public String miningOreId = "COAL"; // COAL or IRON for now
  public int miningGoalCount = 16;

  public int botSeed;
  public long rngState;
  public int stateTicks;
  public int taskProgress;
  public int explorationIndex;
  public BlockPos homePos = BlockPos.ORIGIN;
  public BlockPos targetPos = BlockPos.ORIGIN;
  public BlockPos shelterOrigin = BlockPos.ORIGIN;
  public int shelterStep;
  public boolean running;
  public boolean paused;
  public boolean loggingEnabled = true;
  public boolean spawned;
}
