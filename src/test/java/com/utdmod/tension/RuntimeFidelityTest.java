package com.utdmod.tension;

import com.utdmod.config.UTDConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RuntimeFidelityTest {

    @AfterEach
    void resetRuntimeCache() {
        ParameterSet.resetRuntimeCache();
        UTDConfig.substrateGameplayTuningEnabled = false;
    }

    @Test
    void runtimeUsesLaboratoryDefaultsWhenGameplayTuningDisabled() {
        UTDConfig.substrateGameplayTuningEnabled = false;
        UTDConfig.substrateClampMax = 5.0;
        UTDConfig.substrateFieldDepthRadius = 1.0;
        UTDConfig.substrateReadinessScale = 2.0;
        ParameterSet.resetRuntimeCache();

        ParameterSet runtime = ParameterSet.runtime();
        assertEquals(1.5, runtime.v2ClampMax, 1e-9);
        assertEquals(0.0, runtime.v3FieldDepthRadius, 1e-9);
        assertEquals(1.0, runtime.readinessMemoryScale, 1e-9);
    }

    @Test
    void deprecatedIntegrationPathsAreBlocked() {
        SubstrateState state = new SubstrateState();
        assertThrows(UnsupportedOperationException.class,
            () -> state.advance(0.0, 0.0));
        assertThrows(UnsupportedOperationException.class,
            () -> state.updateWithTransport(0.0, 0.0));
        assertThrows(UnsupportedOperationException.class,
            () -> state.updateFromLocalTension(0.0, 0.0, 0.0, false, 0.0));
    }
}
