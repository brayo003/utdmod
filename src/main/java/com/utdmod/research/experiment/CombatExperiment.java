package com.utdmod.research.experiment;

import com.utdmod.research.player.ResearchPlayerContext;

/** Deterministic combat engagement against nearest hostile. */
public final class CombatExperiment extends TimedExperiment {

  @Override
  public String protocolId() {
    return "COMBAT";
  }

  @Override
  public void initialize(ResearchPlayerContext ctx, long tick) {
    ctx.snapshot.stateTicks = 0;
    ctx.snapshot.transitionReason = "combat init";
    ctx.controller.stop();
  }

  @Override
  public void tick(ResearchPlayerContext ctx, long tick) {
    var hostile = ctx.vision.findNearestHostile();
    if (hostile != null) {
      ctx.currentTarget = hostile.getBlockPos();
    }
    boolean resolved = ctx.combat.tickCombat(ctx.inventory, ctx.controller);
    if (resolved) {
      if ((ctx.snapshot.stateTicks % 160) == 0) {
        var wp = ctx.controller.nextExplorationWaypoint();
        ctx.currentTarget = wp;
        ctx.controller.setTarget(wp);
      }
    }
    ctx.controller.tick();
    ctx.snapshot.transitionReason = resolved ? "combat no hostile" : "combat tick";
  }

  @Override
  public void shutdown(ResearchPlayerContext ctx, long tick) {
    ctx.controller.stop();
    ctx.snapshot.transitionReason = "combat shutdown";
  }
}
