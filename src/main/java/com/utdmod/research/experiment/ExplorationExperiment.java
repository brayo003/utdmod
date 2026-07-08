package com.utdmod.research.experiment;

import com.utdmod.research.player.ResearchPlayerContext;
import net.minecraft.util.math.BlockPos;

/** Deterministic spiral exploration rooted at snapshot.homePos. */
public final class ExplorationExperiment extends TimedExperiment {

  @Override
  public String protocolId() {
    return "EXPLORE";
  }

  @Override
  public void initialize(ResearchPlayerContext ctx, long tick) {
    ctx.snapshot.stateTicks = 0;
    ctx.snapshot.transitionReason = "explore init";
    ctx.navigation.stop();
    ctx.exploration.reset();
    ctx.snapshot.explorationIndex = 0;
    ctx.currentTarget = ctx.snapshot.homePos;
  }

  @Override
  public void tick(ResearchPlayerContext ctx, long tick) {
    int radiusChunks = Math.max(1, ctx.snapshot.allowedRadiusChunks);
    if (ctx.snapshot.stateTicks == 0 || (ctx.snapshot.stateTicks % 200) == 0) {
      BlockPos candidate = ctx.exploration.nextWaypoint();
      int attempts = 0;
      while (!ResearchExperimentLibrary.withinRadiusChunks(candidate, ctx.snapshot.homePos, radiusChunks) && attempts < 64) {
        candidate = ctx.exploration.nextWaypoint();
        attempts++;
      }
      ctx.currentTarget = candidate;
      ctx.navigation.setTarget(candidate);
      ctx.snapshot.transitionReason = "explore waypoint";
    }
    ctx.navigation.tick();
  }

  @Override
  public void shutdown(ResearchPlayerContext ctx, long tick) {
    ctx.navigation.stop();
    ctx.snapshot.transitionReason = "explore done";
  }
}

