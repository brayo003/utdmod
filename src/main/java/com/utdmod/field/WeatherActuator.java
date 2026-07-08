package com.utdmod.field;

import com.utdmod.interpretation.ChunkInterpretation;
import net.minecraft.server.world.ServerWorld;

/**
 * Per-chunk weather actuation is handled by {@link com.utdmod.interpretation.RegionalWeatherCoordinator}.
 * This actuator remains registered for telemetry compatibility.
 */
public final class WeatherActuator implements FieldActuator {

  @Override
  public void actuate(ServerWorld world, ChunkInterpretation interpretation) {
    // Regional weather applied once per world tick in TensionServerTick.
  }

  @Override
  public String getId() {
    return "weather";
  }
}
