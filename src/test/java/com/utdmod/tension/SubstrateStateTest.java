package com.utdmod.tension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SubstrateStateTest {

    @Test
    public void unifiedFieldStepAdvancesAllFourVariables() {
        ParameterSet params = new ParameterSet();
        SubstrateState state = new SubstrateState(0.25, 0.4, 0.0, 0.0, 0.02);
        SpatialStencil stencil = new SpatialStencil();
        state.applyUnifiedFieldStep(0.5, 0.3, stencil, params);
        assertTrue(state.getActivation() >= 0.0);
        assertTrue(state.getMemory() >= 0.0);
        assertTrue(state.getFieldDepth() >= 0.0);
        assertTrue(state.getBaseline() >= 0.0);
    }

    @Test
    public void transportContributionUsesParameterSet() {
        ParameterSet params = new ParameterSet();
        double contribution = SubstrateState.computeTransportContribution(0.8, 0.2, 0.25, 1.0, params);
        double neighborStim = (0.8 / (1.0 + 0.8)) * (1.0 + params.v2MemoryInfluence * (0.2 / (0.2 + params.v2MemoryEffectK)));
        double weight = Math.exp(-1.0 / params.v3DistanceDecay);
        double expected = neighborStim * weight * params.v2TransportGain * (1.0 - 0.25 / (1.0 + 0.25));
        assertEquals(expected, contribution, 1e-9);
    }

    @Test
    public void readinessUsesMemoryScaleFromParameters() {
        ParameterSet params = new ParameterSet();
        params.readinessMemoryScale = 2.0;
        double readiness = SubstrateState.computeReadiness(1.0, 0.5, params);
        assertEquals(Math.exp(-0.5), readiness, 1e-9);
    }
}
