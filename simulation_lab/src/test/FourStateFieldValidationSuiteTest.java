import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FourStateFieldValidationSuiteTest {
    private ParameterSet createParams() {
        ParameterSet params = new ParameterSet();
        params.v2AdaptiveRelaxationEnabled = true;
        params.v2TransportGain = 0.0;
        params.v3FieldDepthGain = 0.03;
        params.v3FieldDepthDecay = 0.0001;
        params.v3FieldDepthDiffusionGain = 0.0;
        params.memoryFormationGain = 0.05;
        params.memoryDecayRate = 0.001;
        params.memoryDepthRetentionGain = 0.8;
        params.v2ActivationDecay = 0.35;
        params.v2ClampMax = 2.0;
        return params;
    }

    @Test
    public void activationShouldDecayTowardZeroWithoutSustainedInput() {
        ParameterSet params = createParams();
        GridSimulationV2 sim = new GridSimulationV2(1, 1, 1, params);
        sim.activation[0][0] = 0.0;
        sim.memory[0][0] = 0.0;
        sim.baseline[0][0] = params.v2BaselineResting;
        sim.fieldDepth[0][0] = 0.0;
        sim.injectImpulse(0, 0, 1.0);

        List<Double> activationSeries = new ArrayList<>();
        activationSeries.add(sim.activation[0][0]);
        for (int tick = 0; tick < 20; tick++) {
            stepGrid(sim, 0.0);
            activationSeries.add(sim.activation[0][0]);
        }

        assertTrue(activationSeries.get(0) > 0.5, "initial activation should be injected");
        for (int i = 1; i < activationSeries.size(); i++) {
            assertTrue(activationSeries.get(i) <= activationSeries.get(i - 1) + 1e-9,
                    "activation should not grow once input stops");
        }
        assertTrue(activationSeries.get(activationSeries.size() - 1) < 1e-3,
                "activation should decay toward zero without sustained input");
    }

    @Test
    public void memoryShouldPersistLongerThanActivationAfterStimulationStops() {
        ParameterSet params = createParams();
        GridSimulationV2 sim = new GridSimulationV2(1, 1, 1, params);
        sim.activation[0][0] = 0.0;
        sim.memory[0][0] = 0.0;
        sim.baseline[0][0] = params.v2BaselineResting;
        sim.fieldDepth[0][0] = 0.0;

        for (int tick = 0; tick < 10; tick++) {
            stepGrid(sim, 0.6);
        }

        double peakActivation = sim.activation[0][0];
        double peakMemory = sim.memory[0][0];
        for (int tick = 0; tick < 20; tick++) {
            stepGrid(sim, 0.0);
        }

        assertTrue(peakActivation > 0.1, "stimulation should create visible activation");
        assertTrue(peakMemory > 0.02, "repeated stimulation should build memory");
        assertTrue(sim.activation[0][0] < 0.01, "activation should have mostly disappeared after the input stops");
        assertTrue(sim.memory[0][0] > 0.005, "memory should remain after activation has decayed");
        assertTrue(sim.memory[0][0] > peakActivation * 10.0,
                "memory should persist longer than activation");
    }

    @Test
    public void memoryShouldChangeSusceptibilityToTheSameFutureImpulse() {
        ParameterSet params = createParams();
        GridSimulationV2 history = new GridSimulationV2(1, 1, 1, params);
        GridSimulationV2 virgin = new GridSimulationV2(1, 1, 1, params);

        for (int tick = 0; tick < 100; tick++) {
            stepGrid(history, 0.6);
        }
        for (int tick = 0; tick < 30; tick++) {
            stepGrid(history, 0.0);
        }

        assertTrue(history.memory[0][0] > 0.02, "history should build memory");
        assertTrue(history.activation[0][0] < 0.02, "activation should be near resting after quiet period");

        stepGrid(history, 1.0);
        stepGrid(virgin, 1.0);

        assertTrue(history.activation[0][0] > virgin.activation[0][0] * 1.5,
                "memory should change the response to the same future input");
    }

    @Test
    public void fieldDepthShouldActAsAResidualStructuralModifierAfterMemoryFades() {
        ParameterSet params = createParams();
        params.v3FieldDepthGain = 0.05;
        GridSimulationV2 history = new GridSimulationV2(1, 1, 1, params);
        GridSimulationV2 virgin = new GridSimulationV2(1, 1, 1, params);

        for (int tick = 0; tick < 1500; tick++) {
            stepGrid(history, 0.8);
        }
        for (int tick = 0; tick < 300; tick++) {
            stepGrid(history, 0.0);
        }

        assertTrue(history.fieldDepth[0][0] > 0.05, "repeated history should build field depth");
        assertTrue(history.memory[0][0] < 0.01, "memory should mostly fade while depth remains");

        stepGrid(history, 1.0);
        stepGrid(virgin, 1.0);

        assertTrue(history.activation[0][0] > virgin.activation[0][0] * 1.2,
                "field depth should still alter future activation after memory has decayed");
    }

    @Test
    public void spatialMemoryShouldFormDistinctIslands() {
        ParameterSet params = createParams();
        GridSimulationV2 sim = new GridSimulationV2(32, 32, 1, params);

        sim.injectImpulse(2, 2, 1.0);
        for (int tick = 0; tick < 25; tick++) {
            stepGrid(sim, 0.0);
        }

        sim.injectImpulse(29, 29, 1.0);
        for (int tick = 0; tick < 25; tick++) {
            stepGrid(sim, 0.0);
        }

        double topLeftMean = localMean(sim.memory, 0, 0, 6, 6);
        double bottomRightMean = localMean(sim.memory, 24, 24, 6, 6);
        double centerMean = localMean(sim.memory, 12, 12, 6, 6);

        assertTrue(topLeftMean > 0.01, "the upper-left region should retain memory");
        assertTrue(bottomRightMean > 0.01, "the lower-right region should retain memory");
        assertTrue(centerMean < Math.min(topLeftMean, bottomRightMean),
                "memory should remain spatially localized rather than uniform");
    }

    @Test
    public void spatialIdentityShouldProduceDifferentResponsesToTheSameUniformPerturbation() {
        ParameterSet params = createParams();
        GridSimulationV2 history = new GridSimulationV2(16, 16, 1, params);
        GridSimulationV2 virgin = new GridSimulationV2(16, 16, 1, params);

        for (int tick = 0; tick < 40; tick++) {
            history.injectImpulse(2, 2, 0.6);
            history.injectImpulse(13, 13, 0.6);
            stepGrid(history, 0.0);
        }
        for (int tick = 0; tick < 20; tick++) {
            stepGrid(history, 0.0);
        }

        assertTrue(history.memory[2][2] > 0.01, "a primed region should retain memory");
        assertTrue(history.memory[13][13] > 0.01, "a second primed region should retain memory");

        stepGrid(history, 1.0);
        stepGrid(virgin, 1.0);

        assertTrue(history.activation[2][2] > virgin.activation[2][2] * 1.2,
                "history should change the response of a region to the same uniform perturbation");
        assertTrue(history.activation[13][13] > virgin.activation[13][13] * 1.2,
                "different historical regions should respond differently to the same input");
    }

    private void stepGrid(GridSimulationV2 sim, double external) {
        double[][] nextActivation = new double[sim.height][sim.width];
        double[][] nextMemory = new double[sim.height][sim.width];
        double[][] nextBaseline = new double[sim.height][sim.width];
        double[][] nextDepth = new double[sim.height][sim.width];

        for (int y = 0; y < sim.height; y++) {
            for (int x = 0; x < sim.width; x++) {
                GridSimulationV2.CellUpdateResult update = sim.integrateUnifiedCellStep(x, y, external, 0.0);
                nextActivation[y][x] = update.nextActivation;
                nextMemory[y][x] = update.nextMemory;
                nextBaseline[y][x] = update.nextBaseline;
                nextDepth[y][x] = update.nextDepth;
            }
        }

        sim.activation = nextActivation;
        sim.memory = nextMemory;
        sim.baseline = nextBaseline;
        sim.fieldDepth = nextDepth;
    }

    private double localMean(double[][] values, int centerX, int centerY, int radiusX, int radiusY) {
        double sum = 0.0;
        int count = 0;
        for (int y = Math.max(0, centerY - radiusY); y <= Math.min(values.length - 1, centerY + radiusY); y++) {
            for (int x = Math.max(0, centerX - radiusX); x <= Math.min(values[0].length - 1, centerX + radiusX); x++) {
                sum += values[y][x];
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }
}
