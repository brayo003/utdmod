package com.utdmod.field;

import com.utdmod.experiment.ActuatorOutcome;
import com.utdmod.experiment.ExperimentLogger;
import com.utdmod.interpretation.ChunkInterpretation;
import com.utdmod.core.TensionTraceClock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for all field actuators. Actuators consume interpreted substrate state only.
 */
public final class FieldActuatorRegistry {
  private static final Map<String, FieldActuator> ACTUATORS = new HashMap<>();

  private FieldActuatorRegistry() {}

  public static void register(FieldActuator actuator) {
    ACTUATORS.put(actuator.getId(), actuator);
  }

  public static void actuateAll(ServerWorld world, ChunkInterpretation interpretation) {
    if (world == null || interpretation == null || interpretation.chunkPos == null
        || interpretation.semantics == null || interpretation.substrate == null) {
      return;
    }
    if (!com.utdmod.interpretation.SpatialBehaviorHelper.isChunkLoaded(world, interpretation.chunkPos)) {
      return;
    }
    for (FieldActuator actuator : ACTUATORS.values()) {
      if ("weather".equals(actuator.getId())) {
        continue;
      }
      long startNs = System.nanoTime();
      ActuatorOutcome outcome = ActuatorOutcome.UNKNOWN;
      try {
        actuator.actuate(world, interpretation);
        outcome = ActuatorOutcome.SUCCESS;
      } catch (Throwable t) {
        outcome = ActuatorOutcome.FAILED;
        com.utdmod.UTDMod.LOGGER.error("[UTD] Actuator {} failed at chunk {},{}: {}",
            actuator.getId(), interpretation.chunkPos.x, interpretation.chunkPos.z, t.toString());
      } finally {
        ExperimentLogger.logActuatorInvocation(
            TensionTraceClock.getServerTick(),
            world,
            interpretation.chunkPos,
            actuator.getId(),
            interpretation.substrate,
            "EXECUTED",
            interpretation.primaryIntent != null ? interpretation.primaryIntent.type.name() : "ambient",
            System.nanoTime() - startNs,
            outcome);
      }
    }
  }

  public static void initialize() {
    ACTUATORS.clear();
    register(new MobBehaviorActuator());
    register(new VillageActuator());
    register(new WeatherActuator());
    register(new ResourceRegenerationActuator());
    register(new ParticleVisualActuator());
    register(new GrassWearActuator());
    register(new SettlementStructureActuator());
  }
}
