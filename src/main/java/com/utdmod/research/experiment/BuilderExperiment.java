package com.utdmod.research.experiment;

import com.utdmod.research.player.ResearchPlayerContext;
import net.minecraft.util.math.BlockPos;

/** Build a deterministic minimal shelter near snapshot.homePos. */
public final class BuilderExperiment extends TimedExperiment {

  @Override
  public String protocolId() {
    return "BUILD_SHELTER";
  }

  @Override
  public void initialize(ResearchPlayerContext ctx, long tick) {
    ctx.snapshot.stateTicks = 0;
    ctx.snapshot.transitionReason = "builder init";
    ctx.snapshot.shelterOrigin = ctx.snapshot.homePos.toImmutable();
    ctx.snapshot.shelterStep = 0;
    ctx.building.setShelterOrigin(ctx.snapshot.homePos);
    ctx.navigation.stop();
    ctx.currentTarget = ctx.snapshot.homePos;
  }

  @Override
  public void tick(ResearchPlayerContext ctx, long tick) {
    boolean done = ctx.building.tickBuildStep(ctx.navigation);
    ctx.snapshot.transitionReason = done ? "builder complete" : "placing shelter";
    // Best-effort: expose a stable navigation target for logging.
    if (ctx.currentTarget == null) {
      ctx.currentTarget = ctx.snapshot.homePos;
    }
  }

  @Override
  public boolean isFinished(ResearchPlayerContext ctx, long tick) {
    // End early if the shelter completes.
    return super.isFinished(ctx, tick) || ctx.snapshot.shelterStep >= 999999;
  }

  @Override
  public void shutdown(ResearchPlayerContext ctx, long tick) {
    ctx.navigation.stop();
    ctx.snapshot.transitionReason = "builder shutdown";
  }
}

