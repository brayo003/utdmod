package com.utdmod.research.experiment;

import com.utdmod.research.player.ResearchPlayerContext;
import net.minecraft.entity.passive.AnimalEntity;

/**
 * Deterministic "farming" treatment (minimal implementation).
 *
 * For now it opportunistically engages deterministic-nearest passive animals
 * (vanilla combat) and otherwise continues deterministic exploration.
 */
public final class FarmerExperiment extends TimedExperiment {

  @Override
  public String protocolId() {
    return "FARMER";
  }

  @Override
  public void initialize(ResearchPlayerContext ctx, long tick) {
    ctx.snapshot.stateTicks = 0;
    ctx.snapshot.transitionReason = "farmer init";
    ctx.controller.stop();
    ctx.currentTarget = ctx.snapshot.homePos;
  }

  @Override
  public void tick(ResearchPlayerContext ctx, long tick) {
    AnimalEntity animal = ctx.vision.findNearestPassiveFood();
    if (animal != null) {
      ctx.currentTarget = animal.getBlockPos();
      ctx.controller.setTarget(ctx.currentTarget);
      ctx.controller.face(ctx.currentTarget);
      if (ctx.player.squaredDistanceTo(animal) <= 2.5) {
        ctx.player.attack(animal);
      } else {
        ctx.controller.tick();
      }
      ctx.snapshot.transitionReason = "farmer target";
    } else {
      if ((ctx.snapshot.stateTicks % 180) == 0) {
        var wp = ctx.controller.nextExplorationWaypoint();
        ctx.currentTarget = wp;
        ctx.controller.setTarget(wp);
      }
      ctx.controller.tick();
      ctx.snapshot.transitionReason = "farmer wander";
    }
  }

  @Override
  public void shutdown(ResearchPlayerContext ctx, long tick) {
    ctx.controller.stop();
    ctx.snapshot.transitionReason = "farmer shutdown";
  }
}
