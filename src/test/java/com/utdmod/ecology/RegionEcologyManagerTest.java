package com.utdmod.ecology;

import com.utdmod.core.RegionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionEcologyManagerTest {
    @Test
    void smallTreeCutsStayGrowingUnlessLocalStressBuilds() {
        RegionHistory history = new RegionHistory();
        for (int i = 0; i < 6; i++) {
            history.recordTreeCut();
        }

        EcologyPolicy policy = RegionEcologyManager.selectPolicy(RegionState.GROWING, history, 0.15, 0);

        assertEquals(EcologyPolicy.GROWING, policy);
    }

    @Test
    void sustainedLoggingPushesGrowingRegionsTowardStrained() {
        RegionHistory history = new RegionHistory();
        for (int i = 0; i < 35; i++) {
            history.recordTreeCut();
        }

        EcologyPolicy policy = RegionEcologyManager.selectPolicy(RegionState.GROWING, history, 1.25, 0);

        assertEquals(EcologyPolicy.STRAINED, policy);
    }
}
