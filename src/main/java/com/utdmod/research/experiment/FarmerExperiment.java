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
    ctx.navigation.stop();
    ctx.currentTarget = ctx.snapshot.homePos;
  }

  @Override
  public void tick(ResearchPlayerContext ctx, long tick) {
    AnimalEntity animal = ctx.vision.findNearestPassiveFood();
    if (animal != null) {
      ctx.currentTarget = animal.getBlockPos();
      ctx.navigation.setTarget(ctx.currentTarget);
      ctx.navigation.face(ctx.currentTarget);
      if (ctx.player.squaredDistanceTo(animal) <= 2.5) {
        ctx.player.attack(animal);
      } else {
        ctx.navigation.tick();
      }
      ctx.snapshot.transitionReason = "farmer target";
    } else {
      // Default to exploration waypoints so the agent perturbs substrate.
      if ((ctx.snapshot.stateTicks % 180) == 0) {
        var wp = ctx.exploration.nextWaypoint();
        ctx.currentTarget = wp;
        ctx.navigation.setTarget(wp);
      }
      ctx.navigation.tick();
      ctx.snapshot.transitionReason = "farmer wander";
    }
  }

  @Override
  public void shutdown(ResearchPlayerContext ctx, long tick) {
    ctx.navigation.stop();
    ctx.snapshot.transitionReason = "farmer shutdown";
  }
}

