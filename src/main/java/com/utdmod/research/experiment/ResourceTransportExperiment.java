package com.utdmod.research.experiment;

import com.utdmod.interpretation.ChunkInterpretation;
import com.utdmod.interpretation.InterpretationService;
import com.utdmod.research.player.ResearchPlayerContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

/**
 * Deterministic resource transport treatment (minimal implementation).
 *
 * It biases navigation targets by the local interpretation transport vector.
 */
public final class ResourceTransportExperiment extends TimedExperiment {

  @Override
  public String protocolId() {
    return "RESOURCE_TRANSPORT";
  }

  @Override
  public void initialize(ResearchPlayerContext ctx, long tick) {
    ctx.snapshot.stateTicks = 0;
    ctx.snapshot.transitionReason = "transport init";
    ctx.navigation.stop();
    ctx.currentTarget = ctx.snapshot.homePos;
  }

  @Override
  public void tick(ResearchPlayerContext ctx, long tick) {
    if (ctx.snapshot.stateTicks == 1 || (ctx.snapshot.stateTicks % 120) == 0) {
      BlockPos origin = ctx.player.getBlockPos();
      ChunkPos cp = new ChunkPos(origin);
      ChunkInterpretation interp = InterpretationService.get(ctx.world, cp);
      double biasX = interp != null && interp.semantics != null ? interp.semantics.transportBiasX : 0.0;
      double biasZ = interp != null && interp.semantics != null ? interp.semantics.transportBiasZ : 0.0;

      int step = 16; // one chunk-size stride
      int dx = biasX == 0.0 ? 0 : (biasX > 0 ? step : -step);
      int dz = biasZ == 0.0 ? 0 : (biasZ > 0 ? step : -step);

      int x = origin.getX() + dx;
      int z = origin.getZ() + dz;
      int y = ctx.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
      BlockPos candidate = new BlockPos(x, Math.max(y, 64), z);

      int radiusChunks = Math.max(1, ctx.snapshot.allowedRadiusChunks);
      if (!ResearchExperimentLibrary.withinRadiusChunks(candidate, ctx.snapshot.homePos, radiusChunks)) {
        candidate = ctx.exploration.nextWaypoint();
      }

      ctx.currentTarget = candidate;
      ctx.navigation.setTarget(candidate);
      ctx.snapshot.transitionReason = "transport biased target";
    }

    ctx.navigation.tick();
  }

  @Override
  public void shutdown(ResearchPlayerContext ctx, long tick) {
    ctx.navigation.stop();
    ctx.snapshot.transitionReason = "transport shutdown";
  }
}

