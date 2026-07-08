package com.utdmod.research.player;

import com.utdmod.research.experiment.ResearchExperiment;
import com.utdmod.research.experiment.ResearchExperimentLibrary;

/**
 * Experiment-driven bot executor.
 *
 * This class no longer contains hardcoded mine/build/farm sequencing.
 * It only dispatches execution to the injected {@code ResearchExperiment}
 * protocol and maintains timing + logging.
 */
public final class ResearchPlayerFsm {

  private ResearchExperiment current(ResearchPlayerContext ctx) {
    String pid = ctx.snapshot.protocolId;
    return ResearchExperimentLibrary.create(pid);
  }

  private static ResearchPlayerState mapProtocolToState(String protocolId) {
    if (protocolId == null) return ResearchPlayerState.IDLE;
    return switch (protocolId) {
      case "EXPLORE" -> ResearchPlayerState.EXPLORE;
      case "BUILD_SHELTER" -> ResearchPlayerState.BUILD_SHELTER;
      case "MINE_COAL" -> ResearchPlayerState.MINE_COAL;
      case "MINE_IRON" -> ResearchPlayerState.MINE_IRON;
      case "COMBAT" -> ResearchPlayerState.COMBAT;
      default -> ResearchPlayerState.EXPLORE;
    };
  }

  public void tick(ResearchPlayerContext ctx, long worldTick) {
    if (ctx == null || ctx.snapshot == null) return;

    // If protocol was swapped, run the protocol's initialize/shutdown hooks.
    String protocolId = ctx.snapshot.protocolId;
    String prevProtocolId = ctx.snapshot.previousProtocolId;
    boolean protocolChanged = (prevProtocolId == null ? true : !prevProtocolId.equals(protocolId))
        || ctx.snapshot.stateTicks == 0;

    if (protocolChanged) {
      ResearchExperiment prev = ResearchExperimentLibrary.create(prevProtocolId);
      prev.shutdown(ctx, worldTick);

      ctx.snapshot.previousProtocolId = protocolId;
      ResearchExperiment next = ResearchExperimentLibrary.create(protocolId);
      ctx.snapshot.previousState = ctx.snapshot.state;
      ctx.snapshot.state = mapProtocolToState(protocolId);
      next.initialize(ctx, worldTick);
      ctx.snapshot.transitionReason = "protocol init";
    }

    // Protocol timing is shared: stateTicks is elapsed protocol ticks.
    ctx.snapshot.stateTicks++;

    ResearchExperiment exp = current(ctx);
    exp.tick(ctx, worldTick);

    if (exp.isFinished(ctx, worldTick)) {
      exp.shutdown(ctx, worldTick);
      ctx.snapshot.running = false;
      ctx.snapshot.transitionReason = "protocol finished";
    }

    ctx.syncRng();
    if (ctx.snapshot.loggingEnabled) {
      ResearchPlayerLogger.log(ctx, worldTick, ctx.snapshot.transitionReason);
    }
  }
}
