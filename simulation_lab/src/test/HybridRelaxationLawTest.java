import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class HybridRelaxationLawTest {
    @Test
    void adaptiveRelaxationGrowsWithTension() {
        ParameterSet params = new ParameterSet();
        params.v2AdaptiveRelaxationEnabled = true;
        params.v2RelaxationLambdaMin = 0.0006;
        params.v2RelaxationLambdaGain = 0.01;
        params.v2RelaxationK = 20.0;
        params.v2RelaxationTc = 1.0;
        params.v2RelaxationMemoryBeta = 0.0;
        params.v2RelaxationDepthGamma = 0.0;

        GridSimulationV2 sim = new GridSimulationV2(1, 1, 1, params);
        double low = sim.computeEffectiveRelaxationCoefficient(0.1, 0.0, 0.0);
        double high = sim.computeEffectiveRelaxationCoefficient(2.0, 0.0, 0.0);

        assertTrue(high > low, "Adaptive relaxation should strengthen as tension rises");
    }

    @Test
    void adaptiveRelaxationWeakensWithMemoryAndDepth() {
        ParameterSet params = new ParameterSet();
        params.v2AdaptiveRelaxationEnabled = true;
        params.v2RelaxationLambdaMin = 0.0006;
        params.v2RelaxationLambdaGain = 0.01;
        params.v2RelaxationK = 20.0;
        params.v2RelaxationTc = 1.0;
        params.v2RelaxationMemoryBeta = 0.2;
        params.v2RelaxationDepthGamma = 0.5;

        GridSimulationV2 sim = new GridSimulationV2(1, 1, 1, params);
        double fresh = sim.computeEffectiveRelaxationCoefficient(1.0, 0.0, 0.0);
        double scarred = sim.computeEffectiveRelaxationCoefficient(1.0, 2.0, 1.5);

        assertTrue(scarred < fresh, "Memory and field depth should slow recovery");
    }
}
