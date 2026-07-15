package com.utdmod.research.experiment;

import com.utdmod.research.player.ResearchPlayerContext;
import com.utdmod.research.player.ResearchPlayerInventoryManager;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/** Deterministic ore mining until inventory goal is reached. */
public final class MiningExperiment extends TimedExperiment {
  private final Item targetDropItem;
  private boolean miningStarted;

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
    ctx.controller.stop();
    miningStarted = false;
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

    if (!miningStarted) {
      if (targetDropItem == Items.COAL) {
        ctx.controller.mineByName("coal_ore", "deepslate_coal_ore");
      } else {
        ctx.controller.mineByName("iron_ore", "deepslate_iron_ore");
      }
      miningStarted = true;
      ctx.snapshot.transitionReason = "mining start";
    }

    int radiusChunks = Math.max(1, ctx.snapshot.allowedRadiusChunks);
    if (!ResearchExperimentLibrary.withinRadiusChunks(
        ctx.player.getBlockPos(), ctx.snapshot.homePos, radiusChunks)) {
      ctx.controller.setTarget(ctx.snapshot.homePos);
      ctx.snapshot.transitionReason = "mining radius recall";
    } else if (targetDropItem == Items.COAL ? ctx.vision.findNearestCoal() == null : ctx.vision.findNearestIron() == null) {
      BlockPos wp = ctx.controller.nextExplorationWaypoint();
      if (ResearchExperimentLibrary.withinRadiusChunks(wp, ctx.snapshot.homePos, radiusChunks)) {
        ctx.currentTarget = wp;
        ctx.controller.setTarget(wp);
      } else {
        ctx.controller.setTarget(ctx.snapshot.homePos);
      }
      ctx.snapshot.transitionReason = "mining exploring fallback";
    }

    ctx.controller.tick();
    if (!ctx.snapshot.transitionReason.equals("mining start")
        && !ctx.snapshot.transitionReason.equals("mining exploring fallback")
        && !ctx.snapshot.transitionReason.equals("mining radius recall")) {
      ctx.snapshot.transitionReason = "mining tick";
    }
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
    ctx.controller.stop();
    ctx.controller.stopMining();
    miningStarted = false;
    ctx.snapshot.transitionReason = "mining shutdown";
  }
}
