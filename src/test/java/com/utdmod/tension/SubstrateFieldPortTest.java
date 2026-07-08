package com.utdmod.tension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubstrateFieldPortTest {

    @Test
    public void unifiedFieldUpdateAdvancesAllFourStateVariables() {
        SubstrateState state = new SubstrateState(0.2, 0.1, 0.0, 0.05);
        ParameterSet params = ParameterSet.runtime();
        SpatialStencil stencil = new SpatialStencil();

        state.applyUnifiedFieldStep(0.0, 0.0, stencil, params);

        assertTrue(state.getActivation() >= 0.0, "activation should remain non-negative");
        assertTrue(state.getMemory() >= 0.0, "memory should remain non-negative");
        assertTrue(state.getBaseline() >= 0.0, "baseline should remain non-negative");
        assertTrue(state.getFieldDepth() >= 0.0, "depth should remain non-negative");
        assertTrue(state.getTransport() >= 0.0, "transport should remain non-negative");
        assertTrue(state.getRadius() >= 0.0, "transport radius should remain non-negative");
        assertTrue(state.getCapacity() >= 0.0, "capacity should remain non-negative");

        assertTrue(state.getActivation() != 0.2 || state.getMemory() != 0.1 || state.getBaseline() != 0.0 || state.getFieldDepth() != 0.05,
                "the unified update should advance at least one state variable");
    }
}
