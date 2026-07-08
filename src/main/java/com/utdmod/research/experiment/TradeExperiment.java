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
    ctx.navigation.stop();
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
      ctx.navigation.setTarget(ctx.currentTarget);
      ctx.navigation.face(ctx.currentTarget);
      ctx.navigation.tick();
      ctx.snapshot.transitionReason = "trade approach villager";
      return;
    }

    if ((ctx.snapshot.stateTicks % 180) == 0) {
      var wp = ctx.exploration.nextWaypoint();
      ctx.currentTarget = wp;
      ctx.navigation.setTarget(wp);
      ctx.snapshot.transitionReason = "trade wander waypoint";
    }
    ctx.navigation.tick();
    ctx.snapshot.transitionReason = ctx.snapshot.transitionReason.isEmpty() ? "trade tick" : ctx.snapshot.transitionReason;
  }

  @Override
  public void shutdown(ResearchPlayerContext ctx, long tick) {
    ctx.navigation.stop();
    ctx.snapshot.transitionReason = "trade shutdown";
  }
}

