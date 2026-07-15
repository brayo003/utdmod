package com.utdmod.research.experiment;

import com.utdmod.research.player.ResearchPlayerContext;
import net.minecraft.entity.passive.VillagerEntity;

import java.util.Comparator;

/** Deterministic trade-oriented treatment (minimal: approach villagers). */
public final class TradeExperiment extends TimedExperiment {

  @Override
  public String protocolId() {
    return "TRADE";
  }

  @Override
  public void initialize(ResearchPlayerContext ctx, long tick) {
    ctx.snapshot.stateTicks = 0;
    ctx.snapshot.transitionReason = "trade init";
    ctx.controller.stop();
    ctx.currentTarget = ctx.snapshot.homePos;
  }

  @Override
  public void tick(ResearchPlayerContext ctx, long tick) {
    var player = ctx.player;
    var box = player.getBoundingBox().expand(8);
    var villagers = ctx.world.getEntitiesByClass(VillagerEntity.class, box, e -> e.isAlive());
    villagers.sort(Comparator
        .comparingDouble((VillagerEntity e) -> e.squaredDistanceTo(player))
        .thenComparing(e -> e.getUuid().toString()));

    if (!villagers.isEmpty()) {
      var v = villagers.get(0);
      ctx.currentTarget = v.getBlockPos();
      ctx.controller.setTarget(ctx.currentTarget);
      ctx.controller.face(ctx.currentTarget);
      ctx.controller.tick();
      ctx.snapshot.transitionReason = "trade approach villager";
      return;
    }

    if ((ctx.snapshot.stateTicks % 180) == 0) {
      var wp = ctx.controller.nextExplorationWaypoint();
      ctx.currentTarget = wp;
      ctx.controller.setTarget(wp);
      ctx.snapshot.transitionReason = "trade wander waypoint";
    }
    ctx.controller.tick();
    ctx.snapshot.transitionReason = ctx.snapshot.transitionReason.isEmpty() ? "trade tick" : ctx.snapshot.transitionReason;
  }

  @Override
  public void shutdown(ResearchPlayerContext ctx, long tick) {
    ctx.controller.stop();
    ctx.snapshot.transitionReason = "trade shutdown";
  }
}
