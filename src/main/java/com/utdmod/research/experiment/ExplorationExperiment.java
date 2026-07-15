package com.utdmod.research.experiment;

import com.utdmod.research.player.ResearchPlayerContext;
import net.minecraft.util.math.BlockPos;

/** Deterministic spiral exploration rooted at snapshot.homePos. */
public final class ExplorationExperiment extends TimedExperiment {
  private boolean exploreStarted;

  @Override
  public String protocolId() {
    return "EXPLORE";
  }

  @Override
  public void initialize(ResearchPlayerContext ctx, long tick) {
    ctx.snapshot.stateTicks = 0;
    ctx.snapshot.transitionReason = "explore init";
    ctx.controller.stop();
    ctx.controller.resetExploration();
    exploreStarted = false;
    ctx.snapshot.explorationIndex = 0;
    ctx.currentTarget = ctx.snapshot.homePos;
  }

  @Override
  public void tick(ResearchPlayerContext ctx, long tick) {
    int radiusChunks = Math.max(1, ctx.snapshot.allowedRadiusChunks);

    if (!exploreStarted) {
      BlockPos home = ctx.snapshot.homePos;
      ctx.controller.explore(home.getX(), home.getZ());
      exploreStarted = true;
      ctx.snapshot.transitionReason = "explore baritone start";
    }

    if (!ResearchExperimentLibrary.withinRadiusChunks(
        ctx.player.getBlockPos(), ctx.snapshot.homePos, radiusChunks)) {
      ctx.controller.setTarget(ctx.snapshot.homePos);
      ctx.snapshot.transitionReason = "explore radius recall";
    } else if (!ctx.controller.isExploring()) {
      if (ctx.snapshot.stateTicks == 0 || (ctx.snapshot.stateTicks % 200) == 0) {
        BlockPos candidate = ctx.controller.nextExplorationWaypoint();
        int attempts = 0;
        while (!ResearchExperimentLibrary.withinRadiusChunks(candidate, ctx.snapshot.homePos, radiusChunks)
            && attempts < 64) {
          candidate = ctx.controller.nextExplorationWaypoint();
          attempts++;
        }
        ctx.currentTarget = candidate;
        ctx.controller.setTarget(candidate);
        ctx.snapshot.transitionReason = "explore waypoint";
      }
    }

    ctx.controller.tick();
  }

  @Override
  public void shutdown(ResearchPlayerContext ctx, long tick) {
    ctx.controller.stop();
    exploreStarted = false;
    ctx.snapshot.transitionReason = "explore done";
  }
}
