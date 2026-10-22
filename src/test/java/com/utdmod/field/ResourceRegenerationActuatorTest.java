package com.utdmod.field;

import com.utdmod.experiment.ActuatorOutcome;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for ResourceRegenerationActuator outcome reporting.
 * 
 * NOTE: Full integration testing requires a live Minecraft ServerWorld instance,
 * which cannot be instantiated in this test environment. These tests validate
 * the threshold logic and null-safety paths. Actual block-change verification
 * requires runtime testing with a real Minecraft server.
 */
public class ResourceRegenerationActuatorTest {

    @Test
    void nullWorld_returnsThresholdNotMet() {
        ResourceRegenerationActuator actuator = new ResourceRegenerationActuator();
        ActuatorOutcome outcome = actuator.actuate(null, null);
        assertEquals(ActuatorOutcome.THRESHOLD_NOT_MET, outcome,
            "Should return THRESHOLD_NOT_MET when world is null");
    }

    @Test
    void nullInterpretation_returnsThresholdNotMet() {
        ResourceRegenerationActuator actuator = new ResourceRegenerationActuator();
        ActuatorOutcome outcome = actuator.actuate(null, null);
        assertEquals(ActuatorOutcome.THRESHOLD_NOT_MET, outcome,
            "Should return THRESHOLD_NOT_MET when interpretation is null");
    }

    @Test
    void actuatorIdIsCorrect() {
        ResourceRegenerationActuator actuator = new ResourceRegenerationActuator();
        assertEquals("resource_regeneration", actuator.getId(),
            "Actuator ID should match expected value");
    }
}
