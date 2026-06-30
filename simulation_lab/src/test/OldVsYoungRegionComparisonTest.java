import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

public class OldVsYoungRegionComparisonTest {
    @Test
    void compareOldAndYoungRegions() {
        // Grid and parameters
        int W = 41, H = 41; // larger area to measure propagation
        int centerX = W/2, centerY = H/2;

        ParameterSet p = new ParameterSet();
        // keep default dynamics; enable modest fieldDepth growth used earlier
        p.v3FieldDepthGain = 0.01;
        p.v3FieldDepthDecay = 0.0001;
        p.v3FieldDepthRadius = 3.0;
        p.v3FieldDepthEffectK = 0.1;
        p.v3DistanceDecay = 1.0;

        // Build durations
        int buildShort = 500;
        int buildLong = 5000;

        // Disturbance injection location (nearby)
        int disturbX = centerX + 2;
        int disturbY = centerY;

        // Probe duration after disturbance
        int probeTicks = 400;

        System.out.println("=== Old vs Young Region Comparison ===");

        RegionSummary young = runScenario(W, H, p, buildShort, centerX, centerY, disturbX, disturbY, probeTicks);
        RegionSummary oldRegion = runScenario(W, H, p, buildLong, centerX, centerY, disturbX, disturbY, probeTicks);

        // Print side-by-side
        System.out.println("Metric,Young(\"" + buildShort + " ticks\"),Old(\"" + buildLong + " ticks\")");
        System.out.println(String.format("activationPeak,%.6f,%.6f", young.activationPeak, oldRegion.activationPeak));
        System.out.println(String.format("propagationRadiusPeak,%.6f,%.6f", young.propagationRadiusPeak, oldRegion.propagationRadiusPeak));
        System.out.println(String.format("decayHalfLife(ticks),%.2f,%.2f", young.halfLife, oldRegion.halfLife));
        System.out.println(String.format("reactivationTimeToPeak(ticks),%d,%d", young.timeToPeak, oldRegion.timeToPeak));
        System.out.println(String.format("affectedCellsAtPeak,%d,%d", young.affectedCellsAtPeak, oldRegion.affectedCellsAtPeak));

        // Interpret
        System.out.println("--- Interpretation ---");
        double relAct = Math.abs(oldRegion.activationPeak - young.activationPeak);
        if (relAct < 1e-3) {
            System.out.println("Activation peak differences negligible; likely limited by transport gating or saturation.");
        } else {
            System.out.println(String.format("Activation peak differs by %.3g (absolute)", relAct));
        }
    }

    private RegionSummary runScenario(int W, int H, ParameterSet p, int buildTicks, int seedX, int seedY, int disturbX, int disturbY, int probeTicks) {
        double[][] A = new double[H][W];
        double[][] M = new double[H][W];
        double[][] C = new double[H][W];

        // Build phase: repeated mining at seed each tick
        for (int t = 0; t < buildTicks; t++) {
            A[seedY][seedX] += 1.0; // mining activity
            stepOnce(A, M, C, p, t, seedX, seedY, false);
        }

        // Stop mining; inject single disturbance near center
        A[disturbY][disturbX] += 1.0;

        List<Double> maxAHistory = new ArrayList<>();
        double propagationRadiusPeak = 0.0;
        int timeToPeak = -1;
        double activationPeak = 0.0;
        int affectedCellsAtPeak = 0;

        for (int t = 0; t < probeTicks; t++) {
            stepOnce(A, M, C, p, t, seedX, seedY, false);
            double maxA = 0.0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) maxA = Math.max(maxA, A[y][x]);
            maxAHistory.add(maxA);
            double radius = MetricEvaluator.diffusionRadius(A, p.regionActiveThreshold, maxA, seedX, seedY);
            propagationRadiusPeak = Math.max(propagationRadiusPeak, radius);
            if (maxA > activationPeak) {
                activationPeak = maxA;
                timeToPeak = t;
                affectedCellsAtPeak = MetricEvaluator.countActiveCells(A, p.regionActiveThreshold, maxA);
            }
        }

        double halfLife = MetricEvaluator.halfLife(maxAHistory);
        RegionSummary s = new RegionSummary();
        s.activationPeak = activationPeak;
        s.propagationRadiusPeak = propagationRadiusPeak;
        s.halfLife = halfLife;
        s.timeToPeak = timeToPeak;
        s.affectedCellsAtPeak = affectedCellsAtPeak;
        return s;
    }

    private static class RegionSummary {
        double activationPeak;
        double propagationRadiusPeak;
        double halfLife;
        int timeToPeak;
        int affectedCellsAtPeak;
    }

    // copy of stepOnce from FieldDepthSweepTest to keep identical dynamics
    private static void stepOnce(double[][] activation, double[][] memory, double[][] depth, ParameterSet parameters, int tick, int injectX, int injectY, boolean inject) {
        int height = activation.length;
        int width = activation[0].length;
        double[][] nextA = new double[height][width];
        double[][] nextM = new double[height][width];
        double[][] nextC = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double A = activation[y][x];
                double M = memory[y][x];
                double C = depth[y][x];
                double transport = 0.0;
                int maxSearchRadius = 1 + (int) Math.floor(parameters.v3FieldDepthRadius);
                for (int dy = -maxSearchRadius; dy <= maxSearchRadius; dy++) {
                    for (int dx = -maxSearchRadius; dx <= maxSearchRadius; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            double distance = Math.sqrt(dx * dx + dy * dy);
                            if (distance > maxSearchRadius) continue;
                            double nA = activation[ny][nx];
                            double nM = memory[ny][nx];
                            double nC = depth[ny][nx];
                            int sourceRadius = 1 + (int) Math.floor(parameters.v3FieldDepthRadius * nC / (nC + parameters.v3FieldDepthEffectK));
                            if (distance > sourceRadius) continue;
                            double neighborStim = nA / (1.0 + nA);
                            neighborStim *= (1.0 + parameters.v2MemoryInfluence * (nM / (nM + parameters.v2MemoryEffectK)));
                            double weight = 1.0;
                            if (parameters.v3DistanceDecay > 0.0) {
                                weight = Math.exp(-distance / parameters.v3DistanceDecay);
                            } else {
                                weight = 1.0 / (1.0 + distance);
                            }
                            transport += neighborStim * weight;
                        }
                    }
                }
                transport *= parameters.v2TransportGain;
                transport *= (1.0 - A / (1.0 + A));

                double external = 0.0;
                if (inject && x == injectX && y == injectY) external = 1.0;

                double memTerm = (1.0 - (M / (M + parameters.v2MemoryEffectK)));
                double decayFactor = parameters.v2ActivationDecay * memTerm;
                double nextActivation = Math.max(0.0, A * Math.max(0.0, 1.0 - decayFactor) + transport + external);
                if (parameters.v2ClampMax > 0.0) nextActivation = Math.min(nextActivation, parameters.v2ClampMax);

                double deltaMemory = parameters.v2MemoryGain * (A / (1.0 + A));
                double nextMemory = Math.max(0.0, M + deltaMemory - parameters.v2MemoryDecay * M);

                double deltaDepth = parameters.v3FieldDepthGain * (A * M);
                if (deltaDepth > 1.0) deltaDepth = 1.0;
                double nextDepth = Math.max(0.0, C + deltaDepth - parameters.v3FieldDepthDecay * C);

                nextA[y][x] = nextActivation;
                nextM[y][x] = nextMemory;
                nextC[y][x] = nextDepth;
            }
        }
        for (int y = 0; y < height; y++) {
            System.arraycopy(nextA[y], 0, activation[y], 0, width);
            System.arraycopy(nextM[y], 0, memory[y], 0, width);
            System.arraycopy(nextC[y], 0, depth[y], 0, width);
        }
    }
}