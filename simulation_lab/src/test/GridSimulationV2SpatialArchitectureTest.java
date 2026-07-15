import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class GridSimulationV2SpatialArchitectureTest {
    private ParameterSet createParams() {
        ParameterSet params = new ParameterSet();
        params.v2AdaptiveRelaxationEnabled = true;
        params.v3FieldDepthGain = 0.01;
        params.v3FieldDepthDecay = 0.001;
        params.v3FieldDepthRadius = 2.0;
        params.memoryFormationGain = 0.03;
        params.memoryMaximum = 2.0;
        params.memoryFormationThreshold = 0.05;
        params.memoryDiffusionGain = 0.02;
        params.memoryDepthRetentionGain = 0.8;
        params.memoryDecayRate = 0.003;
        params.v2TransportGain = 0.08;
        params.v3FieldDepthDiffusionGain = 0.002;
        return params;
    }

    @Test
    public void fieldDepthShouldInfluenceFutureActivationAndMemory() {
        ParameterSet params = createParams();
        GridSimulationV2 shallow = new GridSimulationV2(1, 1, 1, params);
        GridSimulationV2 deep = new GridSimulationV2(1, 1, 1, params);

        GridSimulationV2.CellUpdateResult shallowResult = shallow.integrateUnifiedStepForTest(
                0.5, 0.4, 0.3, 0.0, 0.0, 0.0, 0.0, 0.0);
        GridSimulationV2.CellUpdateResult deepResult = deep.integrateUnifiedStepForTest(
                0.5, 0.4, 0.3, 100.0, 0.0, 0.0, 0.0, 0.0);

        assertTrue(Math.abs(shallowResult.nextActivation - deepResult.nextActivation) > 1e-9,
                "activation should differ for shallow versus deep field-depth states");
        assertTrue(Math.abs(shallowResult.nextDepth - deepResult.nextDepth) > 1e-9,
                "field-depth should differ for shallow versus deep field-depth states");
    }

    @Test
    public void memoryDiffusionShouldChangeNeighboringCellsWithoutActivationTransport() {
        ParameterSet params = createParams();
        GridSimulationV2 sim = new GridSimulationV2(2, 1, 2, params);
        sim.memory[0][0] = 0.2;
        sim.memory[0][1] = 0.0;
        sim.fieldDepth[0][0] = 0.0;
        sim.fieldDepth[0][1] = 0.0;

        sim.integrateUnifiedStepForTest(0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        sim.memory[0][0] = 0.2;
        sim.memory[0][1] = 0.0;

        GridSimulationV2.CellUpdateResult result = sim.integrateUnifiedStepForTest(0.0, 0.2, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        assertTrue(result.nextMemory > 0.0);
    }

    @Test
    public void depthDiffusionShouldSpreadHistoricalScarsSlowly() {
        ParameterSet params = createParams();
        GridSimulationV2 sim = new GridSimulationV2(3, 1, 5, params);
        sim.fieldDepth[0][0] = 0.6;
        sim.fieldDepth[0][1] = 0.0;
        sim.fieldDepth[0][2] = 0.0;

        for (int i = 0; i < 5; i++) {
            double[][] nextDepth = new double[1][3];
            for (int x = 0; x < 3; x++) {
                double neighborDepthAverage = 0.0;
                int count = 0;
                if (x > 0) {
                    neighborDepthAverage += sim.fieldDepth[0][x - 1];
                    count++;
                }
                if (x < 2) {
                    neighborDepthAverage += sim.fieldDepth[0][x + 1];
                    count++;
                }
                if (count > 0) {
                    neighborDepthAverage /= count;
                }
                GridSimulationV2.CellUpdateResult result = sim.integrateUnifiedStepForTest(
                        0.0, 0.0, 0.0, sim.fieldDepth[0][x], 0.0, 0.0, 0.0, neighborDepthAverage);
                nextDepth[0][x] = result.nextDepth;
            }
            for (int x = 0; x < 3; x++) {
                sim.fieldDepth[0][x] = nextDepth[0][x];
            }
        }

        assertTrue(sim.fieldDepth[0][1] > 0.0 || sim.fieldDepth[0][2] > 0.0);
    }

    @Test
    public void ancientAndRecentScarsShouldRespondDifferentlyToTheSameInjection() {
        ParameterSet params = createParams();
        GridSimulationV2 recent = new GridSimulationV2(1, 1, 1, params);
        GridSimulationV2 ancient = new GridSimulationV2(1, 1, 1, params);
        GridSimulationV2.CellUpdateResult recentResult = recent.integrateUnifiedStepForTest(0.8, 0.8, 0.2, 0.1, 0.3, 0.0, 0.0, 0.0);
        GridSimulationV2.CellUpdateResult ancientResult = ancient.integrateUnifiedStepForTest(0.1, 0.1, 0.2, 0.8, 0.3, 0.0, 0.0, 0.0);
        assertTrue(Math.abs(recentResult.nextActivation - ancientResult.nextActivation) > 1e-9);
    }

    @Test
    public void steepSpatialGradientShouldChangeTheLocalUpdateEvenAtSameNeighborAverage() {
        ParameterSet params = createParams();
        GridSimulationV2 steep = new GridSimulationV2(3, 1, 1, params);
        GridSimulationV2 smooth = new GridSimulationV2(3, 1, 1, params);

        steep.memory[0][0] = 0.2;
        steep.memory[0][1] = 0.8;
        steep.memory[0][2] = 0.2;

        smooth.memory[0][0] = 0.5;
        smooth.memory[0][1] = 0.5;
        smooth.memory[0][2] = 0.5;

        GridSimulationV2.CellUpdateResult steepResult = steep.integrateUnifiedStepForTest(
                0.3, 0.5, 0.1, 0.0, 0.0, 0.0, 0.5, 0.0);
        GridSimulationV2.CellUpdateResult smoothResult = smooth.integrateUnifiedStepForTest(
                0.3, 0.5, 0.1, 0.0, 0.0, 0.0, 0.5, 0.0);

        assertTrue(Math.abs(steepResult.nextActivation - smoothResult.nextActivation) > 1e-9,
                "a steep gradient should change the local update even when the simple neighbor average is the same");
    }

    @Test
    public void longSimulationsShouldRetainSpatialVariance() {
        ParameterSet params = createParams();
        params.v3FieldDepthGain = 0.015;
        params.memoryDiffusionGain = 0.03;
        params.v3FieldDepthDiffusionGain = 0.003;
        params.v3ActivationBistabilityGain = 0.8;
        params.v3MemorySynchronizationGain = 0.03;
        GridSimulationV2 sim = new GridSimulationV2(8, 8, 100000, params);
        sim.injectImpulse(1, 1, 1.0);
        sim.injectImpulse(6, 6, 0.7);
        sim.runExperiment(new ExperimentConfig("two_impulses", 0, 0));

        double activationVariance = variance(sim.copyActivationGrid());
        double memoryVariance = variance(sim.copyMemoryGrid());
        double depthVariance = variance(sim.copyFieldDepthGrid());
        int regionCount = countRegions(sim.copyActivationGrid(), 0.2);
        assertTrue(activationVariance > 1e-4, "activationVariance=" + activationVariance);
        assertTrue(memoryVariance > 1e-4, "memoryVariance=" + memoryVariance);
        assertTrue(depthVariance > 1e-4, "depthVariance=" + depthVariance);
        assertTrue(regionCount > 1, "regionCount=" + regionCount);
    }

    private double variance(double[][] values) {
        double mean = 0.0;
        int count = 0;
        for (double[] row : values) {
            for (double value : row) {
                mean += value;
                count++;
            }
        }
        mean /= Math.max(1, count);
        double sum = 0.0;
        for (double[] row : values) {
            for (double value : row) {
                double delta = value - mean;
                sum += delta * delta;
            }
        }
        return sum / Math.max(1, count);
    }

    private int countRegions(double[][] values, double threshold) {
        int height = values.length;
        int width = values[0].length;
        boolean[][] visited = new boolean[height][width];
        int regions = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!visited[y][x] && values[y][x] > threshold) {
                    regions++;
                    Deque<int[]> stack = new ArrayDeque<>();
                    stack.push(new int[]{x, y});
                    visited[y][x] = true;
                    while (!stack.isEmpty()) {
                        int[] cell = stack.pop();
                        int cx = cell[0];
                        int cy = cell[1];
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                if (dx == 0 && dy == 0) continue;
                                int nx = cx + dx;
                                int ny = cy + dy;
                                if (nx >= 0 && nx < width && ny >= 0 && ny < height && !visited[ny][nx] && values[ny][nx] > threshold) {
                                    visited[ny][nx] = true;
                                    stack.push(new int[]{nx, ny});
                                }
                            }
                        }
                    }
                }
            }
        }
        return regions;
    }
}
