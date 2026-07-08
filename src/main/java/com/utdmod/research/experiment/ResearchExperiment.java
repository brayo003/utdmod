package com.utdmod.research.experiment;

import com.utdmod.research.player.ResearchPlayerContext;

/**
 * Deterministic experimental protocol. Implementations should not directly
 * call the substrate/interpretation pipeline; they must act through the
 * player's normal vanilla mechanics (movement, mining, interacting, etc.).
 */
public interface ResearchExperiment {

  /** Protocol id, e.g. "MINE_COAL" or "EXPLORE". */
  String protocolId();

  /** Called when the protocol is injected or changed. */
  void initialize(ResearchPlayerContext ctx, long tick);

  /** Called every bot tick while the agent is running. */
  void tick(ResearchPlayerContext ctx, long tick);

  /** Whether this agent's experiment instance has completed. */
  boolean isFinished(ResearchPlayerContext ctx, long tick);

  /** Called exactly once when the protocol completes or is replaced. */
  void shutdown(ResearchPlayerContext ctx, long tick);
}

