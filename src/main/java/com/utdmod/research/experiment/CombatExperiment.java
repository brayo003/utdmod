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
    ctx.navigation.stop();
  }

  @Override
  public void tick(ResearchPlayerContext ctx, long tick) {
    var hostile = ctx.vision.findNearestHostile();
    if (hostile != null) {
      ctx.currentTarget = hostile.getBlockPos();
    }
    boolean resolved = ctx.combat.tickCombat(ctx.inventory, ctx.navigation);
    if (resolved) {
      // No hostile: advance exploration deterministically so the bot still perturbs substrate.
      if ((ctx.snapshot.stateTicks % 160) == 0) {
        var wp = ctx.exploration.nextWaypoint();
        ctx.currentTarget = wp;
        ctx.navigation.setTarget(wp);
      }
    }
    ctx.navigation.tick();
    ctx.snapshot.transitionReason = resolved ? "combat no hostile" : "combat tick";
  }

  @Override
  public void shutdown(ResearchPlayerContext ctx, long tick) {
    ctx.navigation.stop();
    ctx.snapshot.transitionReason = "combat shutdown";
  }
}

