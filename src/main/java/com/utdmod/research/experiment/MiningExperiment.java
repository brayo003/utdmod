package com.utdmod.research.experiment;

import com.utdmod.research.player.ResearchPlayerContext;
import com.utdmod.research.player.ResearchPlayerInventoryManager;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/** Deterministic ore mining until inventory goal is reached. */
public final class MiningExperiment extends TimedExperiment {
  private final Item targetDropItem;

  public MiningExperiment(Item targetDropItem) {
    this.targetDropItem = targetDropItem;
  }

  @Override
  public String protocolId() {
    if (targetDropItem == Items.COAL) return "MINE_COAL";
    if (targetDropItem == Items.IRON_INGOT) return "MINE_IRON";
    return "MINING";
  }

  @Override
  public void initialize(ResearchPlayerContext ctx, long tick) {
    ctx.snapshot.stateTicks = 0;
    ctx.snapshot.transitionReason = "mining init";
    ctx.navigation.stop();
    ctx.currentTarget = null;
  }

  @Override
  public void tick(ResearchPlayerContext ctx, long tick) {
    int goal = Math.max(1, ctx.snapshot.miningGoalCount);
    ResearchPlayerInventoryManager inv = ctx.inventory;

    if (inv.count(targetDropItem) >= goal) {
      ctx.snapshot.transitionReason = "mining goal reached";
      return;
    }

    int radiusChunks = Math.max(1, ctx.snapshot.allowedRadiusChunks);
    BlockPos target = targetDropItem == Items.COAL
        ? ctx.vision.findNearestCoal()
        : ctx.vision.findNearestIron();

    if (target == null || !ResearchExperimentLibrary.withinRadiusChunks(
        target, ctx.snapshot.homePos, radiusChunks)) {
      // Fallback: keep exploring deterministically until ore is seen.
      BlockPos wp = ctx.exploration.nextWaypoint();
      if (ResearchExperimentLibrary.withinRadiusChunks(wp, ctx.snapshot.homePos, radiusChunks)) {
        ctx.currentTarget = wp;
        ctx.navigation.setTarget(wp);
      } else {
        ctx.navigation.setTarget(ctx.snapshot.homePos);
      }
      ctx.navigation.tick();
      ctx.snapshot.transitionReason = "mining exploring fallback";
      return;
    }

    ctx.currentTarget = target;
    ctx.navigation.setTarget(target);

    if (!ctx.mining.isBreaking() && ctx.navigation.isAtTarget(3.5)) {
      ctx.mining.start(target, ctx.navigation, ctx.inventory);
      ctx.snapshot.transitionReason = "mining start";
    }

    // Run both movement and mining; mining will stop itself when block breaks.
    ctx.navigation.tick();
    ctx.mining.tick(ctx.navigation);
    ctx.snapshot.transitionReason = "mining tick";
  }

  @Override
  public boolean isFinished(ResearchPlayerContext ctx, long tick) {
    int goal = Math.max(1, ctx.snapshot.miningGoalCount);
    int held = ctx.inventory.count(targetDropItem);
    if (held >= goal) return true;
    return super.isFinished(ctx, tick);
  }

  @Override
  public void shutdown(ResearchPlayerContext ctx, long tick) {
    ctx.navigation.stop();
    ctx.mining.stop();
    ctx.snapshot.transitionReason = "mining shutdown";
  }
}

