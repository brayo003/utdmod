import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThreeTimescaleModelTest {
    @Test
    void hybridModelProducesDifferentDynamicsThanBaseline() {
        ParameterSet baselineParams = new ParameterSet();
        baselineParams.v2AdaptiveRelaxationEnabled = false;
        GridSimulationV2 baseline = new GridSimulationV2(5, 5, 20, baselineParams);
        baseline.runExperiment(new ExperimentConfig("baseline", "single_impulse", 5, 5, 20, baselineParams, 2, 2, 0.0, 123L));

        ParameterSet hybridParams = new ParameterSet();
        hybridParams.v2AdaptiveRelaxationEnabled = true;
        GridSimulationV2 hybrid = new GridSimulationV2(5, 5, 20, hybridParams);
        hybrid.runExperiment(new ExperimentConfig("hybrid", "single_impulse", 5, 5, 20, hybridParams, 2, 2, 0.0, 123L));

        double baselineEnd = baseline.totalActivationHistory.get(baseline.totalActivationHistory.size() - 1);
        double hybridEnd = hybrid.totalActivationHistory.get(hybrid.totalActivationHistory.size() - 1);
        double baselineDepth = baseline.totalFieldDepthHistory.get(baseline.totalFieldDepthHistory.size() - 1);
        double hybridDepth = hybrid.totalFieldDepthHistory.get(hybrid.totalFieldDepthHistory.size() - 1);

        assertTrue(Math.abs(hybridEnd - baselineEnd) > 1e-6, "Hybrid model should diverge from baseline activation dynamics");
        assertTrue(hybridDepth > baselineDepth, "Hybrid model should accumulate field depth differently from baseline");
    }
}
