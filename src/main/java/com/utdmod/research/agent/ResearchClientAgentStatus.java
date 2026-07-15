package com.utdmod.research.agent;

import net.minecraft.util.math.BlockPos;

/** Last reported execution state from a research client. */
public final class ResearchClientAgentStatus {
  public long lastObjectiveId;
  public BlockPos position = BlockPos.ORIGIN;
  public boolean atTarget;
  public boolean idle = true;
  public boolean mining;
  public boolean exploring;
  public boolean building;
  public boolean buildComplete;
  public String currentTask = "idle";
  public long lastReportTick;
}
