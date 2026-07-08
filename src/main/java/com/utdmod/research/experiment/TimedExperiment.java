package com.utdmod.research.experiment;

import com.utdmod.research.player.ResearchPlayerContext;

/**
 * Shared termination logic: durationTicks in the injected snapshot.
 */
abstract class TimedExperiment implements ResearchExperiment {
  @Override
  public boolean isFinished(ResearchPlayerContext ctx, long tick) {
    int duration = ctx.snapshot.durationTicks;
    if (duration <= 0) {
      return false;
    }
    return ctx.snapshot.stateTicks >= duration;
  }
}

