package com.utdmod.field;

import com.utdmod.interpretation.ChunkInterpretation;
import net.minecraft.server.world.ServerWorld;

/**
 * Actuators read interpreted substrate state and drive Minecraft systems.
 * Never duplicate substrate mathematics. Always read-only from interpretation layer.
 */
public interface FieldActuator {
  void actuate(ServerWorld world, ChunkInterpretation interpretation);

  String getId();
}
