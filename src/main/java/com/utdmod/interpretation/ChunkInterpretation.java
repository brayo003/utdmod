package com.utdmod.interpretation;

import com.utdmod.tension.SubstrateState;
import com.utdmod.tension.SpatialStencil;
import net.minecraft.util.math.ChunkPos;

/**
 * Complete read-only interpretation bundle for one chunk tick.
 */
public final class ChunkInterpretation {
  public final ChunkPos chunkPos;
  public final SubstrateState substrate;
  public final SpatialStencil stencil;
  public final SubstrateSemantics semantics;
  public final GameplayIntent primaryIntent;

  public ChunkInterpretation(ChunkPos chunkPos, SubstrateState substrate, SpatialStencil stencil,
                             SubstrateSemantics semantics, GameplayIntent primaryIntent) {
    this.chunkPos = chunkPos;
    this.substrate = substrate;
    this.stencil = stencil;
    this.semantics = semantics;
    this.primaryIntent = primaryIntent;
  }
}
