package com.utdmod.interpretation;

import com.utdmod.experiment.ActuatorOutcome;
import com.utdmod.experiment.ExperimentLogger;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.level.ServerWorldProperties;

import java.util.Map;

/**
 * Aggregates chunk semantics into world-level weather without per-chunk fighting.
 */
public final class RegionalWeatherCoordinator {
  private RegionalWeatherCoordinator() {}

  public static void applyWorldWeather(ServerWorld world, Map<ChunkPos, ChunkInterpretation> interpretations) {
    if (interpretations.isEmpty()) {
      return;
    }
    double stressSum = 0.0;
    double transportSum = 0.0;
    double activationSum = 0.0;
    double stabilitySum = 0.0;
    int count = 0;
    for (ChunkInterpretation interpretation : interpretations.values()) {
      SubstrateSemantics semantics = interpretation.semantics;
      stressSum += semantics.ecologicalStress;
      transportSum += interpretation.substrate.getTransport();
      activationSum += interpretation.substrate.getActivation();
      stabilitySum += semantics.environmentalStability;
      count++;
    }
    if (count == 0) {
      return;
    }
    double meanStress = stressSum / count;
    double meanTransport = transportSum / count;
    double meanActivation = activationSum / count;
    double meanStability = stabilitySum / count;

    boolean shouldThunder = meanStress > 0.62 && meanActivation > 0.35 && meanTransport > 0.12;
    boolean shouldRain = meanStress > 0.38 && !shouldThunder && meanStability < 0.72;

    WorldProperties properties = world.getLevelProperties();
    if (!(properties instanceof ServerWorldProperties serverProperties)) {
      return;
    }

    if (shouldThunder) {
      if (!world.isThundering()) {
        serverProperties.setThundering(true);
      }
      if (!world.isRaining()) {
        serverProperties.setRaining(true);
      }
      ExperimentLogger.logGlobalDecision(world.getTime(), world, "weather_regional",
          "START_THUNDER", "aggregated ecological stress + transport",
          "setThundering(true);setRaining(true)", ActuatorOutcome.SUCCESS);
    } else if (shouldRain) {
      if (world.isThundering()) {
        serverProperties.setThundering(false);
      }
      if (!world.isRaining()) {
        serverProperties.setRaining(true);
      }
      ExperimentLogger.logGlobalDecision(world.getTime(), world, "weather_regional",
          "START_RAIN", "aggregated stress with moderate stability",
          "setRaining(true)", ActuatorOutcome.SUCCESS);
    } else if (meanStability > 0.55) {
      if (world.isThundering()) {
        serverProperties.setThundering(false);
      }
      if (world.isRaining()) {
        serverProperties.setRaining(false);
        ExperimentLogger.logGlobalDecision(world.getTime(), world, "weather_regional",
            "STOP_RAIN", "aggregated stability above threshold",
            "setRaining(false);setThundering(false)", ActuatorOutcome.SUCCESS);
      }
    }
  }
}
