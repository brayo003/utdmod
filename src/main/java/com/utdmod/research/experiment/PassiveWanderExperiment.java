package com.utdmod.research.experiment;

import com.utdmod.research.player.ResearchPlayerContext;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** Deterministic walk to pseudo-random targets within allowed radius. */
public final class PassiveWanderExperiment extends TimedExperiment {

  @Override
  public String protocolId() {
    return "PASSIVE_WANDER";
  }

  @Override
  public void initialize(ResearchPlayerContext ctx, long tick) {
    ctx.snapshot.stateTicks = 0;
    ctx.snapshot.transitionReason = "experiment init";
    ctx.controller.stop();
    ctx.currentTarget = null;
    ctx.snapshot.targetPos = ctx.snapshot.homePos;
  }

  @Override
  public void tick(ResearchPlayerContext ctx, long tick) {
    int radiusChunks = Math.max(1, ctx.snapshot.allowedRadiusChunks);
    if (ctx.currentTarget == null || ctx.controller.isAtTarget(2.5) || (ctx.snapshot.stateTicks % 60) == 0) {
      int maxBlocks = Math.max(16, radiusChunks * 8);
      int dx = ctx.random.nextInt(maxBlocks * 2 + 1) - maxBlocks;
      int dz = ctx.random.nextInt(maxBlocks * 2 + 1) - maxBlocks;
      BlockPos base = ctx.snapshot.homePos;
      ServerWorld world = ctx.world;
      int x = base.getX() + dx;
      int z = base.getZ() + dz;
      int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
      BlockPos target = new BlockPos(x, Math.max(y, 64), z);
      if (!ResearchExperimentLibrary.withinRadiusChunks(target, base, radiusChunks)) {
        target = base;
      }
      ctx.currentTarget = target;
      ctx.snapshot.targetPos = target.toImmutable();
      ctx.controller.goTo(target);
      ctx.snapshot.transitionReason = "wander target";
    }

    ctx.controller.tick();
    ctx.snapshot.transitionReason = "wandering";
  }

  @Override
  public void shutdown(ResearchPlayerContext ctx, long tick) {
    ctx.controller.stop();
    ctx.snapshot.transitionReason = "experiment done";
  }
}
