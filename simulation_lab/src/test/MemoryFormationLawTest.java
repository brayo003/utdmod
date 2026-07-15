import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MemoryFormationLawTest {
    @Test
    public void memoryFormationShouldRequireActivationAndIgnoreBaselineAndDepth() throws Exception {
        ParameterSet params = new ParameterSet();
        params.memoryFormationGain = 0.05;
        params.memoryFormationThreshold = 0.05;
        params.memoryDepthRetentionGain = 0.8;
        GridSimulationV2 sim = new GridSimulationV2(1, 1, 1, params);

        Method method = GridSimulationV2.class.getDeclaredMethod("computeMemoryFormation", double.class, double.class, double.class);
        method.setAccessible(true);

        double formationWithoutActivation = (double) method.invoke(sim, 0.0, 1.0, 10.0);
        assertEquals(0.0, formationWithoutActivation, 1e-12,
                "formation should be exactly zero when activation is absent");

        double formationWithActivation = (double) method.invoke(sim, 0.2, 1.0, 10.0);
        assertTrue(formationWithActivation > 0.0,
                "formation should be positive when activation is above threshold");

        double formationWhenActivationIsBelowThreshold = (double) method.invoke(sim, 0.04, 1.0, 10.0);
        assertEquals(0.0, formationWhenActivationIsBelowThreshold, 1e-12,
                "formation should be zero when activation is at or below the threshold");
    }
}
