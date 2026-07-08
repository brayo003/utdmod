package com.utdmod.research.experiment;

import net.minecraft.util.math.BlockPos;

/** Config parameters injected per agent experiment assignment. */
public final class ResearchExperimentConfig {
  public String experimentId = "exp0";
  public String protocolId = "PASSIVE_WANDER";
  public String loggingLabel = "";

  public BlockPos startingPos = null;
  public int durationTicks = 2000;
  public int allowedRadiusChunks = 8;

  public int botSeed = 0;
  public int experimentSeed = 0; // protocol-owned deterministic RNG seed

  // Minimal mining parameters for initial protocols.
  public String miningOreId = "COAL"; // COAL or IRON
  public int miningGoalCount = 16;

  // Field coupling/enable knobs (global).
  public boolean fieldEnabled = true;
  public String couplingPresetId = "LAB";

  public ResearchExperimentConfig() {}
}

