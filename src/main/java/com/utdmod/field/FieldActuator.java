package com.utdmod.field;

import com.utdmod.experiment.ActuatorOutcome;
import com.utdmod.interpretation.ChunkInterpretation;
import net.minecraft.server.world.ServerWorld;

/**
 * Actuators read interpreted substrate state and drive Minecraft systems.
 * Never duplicate substrate mathematics. Always read-only from interpretation layer.
 *
 * Return values:
 *   SUCCESS          — actuator executed and changed world state
 *   NO_CHANGE        — actuator ran but world was already in desired state
 *   NO_TARGET        — no valid target found (e.g. no crops to grow)
 *   THRESHOLD_NOT_MET — preconditions not satisfied; actuator did not execute
 *   FAILED           — execution error
 */
public interface FieldActuator {
  ActuatorOutcome actuate(ServerWorld world, ChunkInterpretation interpretation);

  String getId();
}
