import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * ADAPTIVE BOUNDARY SWEEP
 *
 * Purpose: refine the transition region inside the frozen transport architecture
 * and identify whether a robust playable window exists.
 *
 * Frozen architecture remains fixed. Only exposed parameters vary:
 *   - transport gain
 *   - distance attenuation length
 *   - memory decay and field-depth decay are held at their current defaults.
 */
public class TransportAdaptiveBoundarySweepTest {

    private final String outDir = "run/phase_map_dense";

    private final double[] transportGains = {
        0.02, 0.03, 0.04, 0.05, 0.06,
        0.07, 0.08, 0.09, 0.10, 0.11, 0.12
    };
    private final double[] distanceDecays = {
        1.00, 1.10, 1.20, 1.30, 1.40, 1.50,
        1.60, 1.70, 1.80, 1.90, 2.00
    };

    private final double READINESS_SCALE = 2.0;
    private final double V2_ACTIVATION_DECAY = 0.45;
    private final double V2_MEMORY_GAIN = 0.02;
    private final double V2_MEMORY_DECAY = 0.001;

    @Test
    void sweepBoundaryRegion() throws Exception {
        File d = new File(outDir);
        if (!d.exists()) d.mkdirs();

        System.out.println("=== ADAPTIVE BOUNDARY SWEEP ===");
        System.out.println("Dense grid around dead/runaway transition");
        System.out.println("Outputs: " + outDir);

        List<OperatingPoint> results = new ArrayList<>();
        for (double gain : transportGains) {
            for (double distDecay : distanceDecays) {
                System.out.printf("Testing gain=%.2f distDecay=%.2f... ", gain, distDecay);
                System.out.flush();
                OperatingPoint op = evaluateOperatingPoint(gain, distDecay);
                results.add(op);
                System.out.printf("regime=%s persist=%.0f growth=%.1f corridor=%.4f lcc=%.1f\n",
                        op.regime, op.settlementPersistence, op.autonomousGrowthRadius,
                        op.tradeRouteStrength, op.largestActiveComponent);
            }
        }

        writePhaseDiagramCSV(results, new File(outDir, "phase_diagram_dense.csv"));
        writePhaseSummary(results, new File(outDir, "phase_diagram.txt"));
        writeRegimeStatistics(results, new File(outDir, "regime_statistics.csv"));
        writeBoundaryLocations(results, new File(outDir, "boundary_locations.csv"));
        writeGameplayWindowReport(results, new File(outDir, "gameplay_window_report.txt"));
        writeWorldEvolutionExamples(results, new File(outDir, "world_evolution_examples.txt"));

        System.out.println("Sweep complete. Outputs written to " + outDir);
    }

    private OperatingPoint evaluateOperatingPoint(double gain, double distDecay) {
        ParameterSet params = new ParameterSet();
        params.v2TransportGain = gain;
        params.v3DistanceDecay = distDecay;
        params.v2ActivationDecay = V2_ACTIVATION_DECAY;
        params.v2MemoryGain = V2_MEMORY_GAIN;
        params.v2MemoryDecay = V2_MEMORY_DECAY;
        params.readinessMemoryScale = READINESS_SCALE;
        params.v3FieldDepthGain = 0.01;
        params.v3FieldDepthDecay = 0.0001;

        OperatingPoint op = new OperatingPoint(gain, distDecay);
        op.settlementPersistence = measureSettlementPersistence(params);
        op.autonomousGrowthRadius = measureAutonomousGrowthRadius(params);
        op.popupRadius = measureHotspotExpansion(params);
        op.tradeRouteStrength = measureTradeRouteStrength(params);
        op.recoveryEase = measureRecoveryEase(params);
        op.historicalAdvantage = measureHistoricalAdvantage(params);
        op.territorialStability = measureTerritorialStability(params);
        op.activeRegionCount = measureActiveRegions(params);
        op.largestActiveComponent = measureLargestConnectedComponent(params);
        op.averageTransport = measureAverageTransport(params);
        op.maximumTransport = measureMaximumTransport(params);
        op.activityDiversity = measureActivityDiversity(params);
        op.localityScore = measureLocalityScore(params);

        op.classify();
        return op;
    }

    private double measureSettlementPersistence(ParameterSet params) {
        int size = 21;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        int cx = size / 2, cy = size / 2;
        for (int t = 0; t < 20; t++) {
            activation[cx][cy] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        int collapse = 300;
        for (int t = 0; t < 300; t++) {
            if (getMaxActivation(activation, size) < 0.001) {
                collapse = t;
                break;
            }
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        return collapse;
    }

    private double measureAutonomousGrowthRadius(ParameterSet params) {
        int size = 29;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        int cx = size / 2, cy = size / 2;
        for (int t = 0; t < 30; t++) {
            activation[cx][cy] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        return measureActiveRadius(activation, size, 0.002);
    }

    private double measureHotspotExpansion(ParameterSet params) {
        int size = 29;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        int cx = size / 2, cy = size / 2;
        double maxRadius = 0;
        for (int t = 0; t < 30; t++) {
            activation[cx][cy] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
            maxRadius = Math.max(maxRadius, measureActiveRadius(activation, size, 0.002));
        }
        return maxRadius;
    }

    private double measureTradeRouteStrength(ParameterSet params) {
        int size = 55;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        int x1 = 12, y1 = 27;
        int x2 = 42, y2 = 27;
        for (int t = 0; t < 25; t++) {
            activation[x1][y1] += 0.5;
            activation[x2][y2] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        int midX = (x1 + x2) / 2;
        return activation[midX][y1];
    }

    private double measureRecoveryEase(ParameterSet params) {
        int size = 21;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        int cx = size / 2, cy = size / 2;
        for (int t = 0; t < 30; t++) {
            activation[cx][cy] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        for (int t = 0; t < 30; t++) {
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        return exponentialReadiness(memory[cx][cy]);
    }

    private double measureHistoricalAdvantage(ParameterSet params) {
        int size = 21;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        int cx = size / 2, cy = size / 2;
        for (int t = 0; t < 30; t++) {
            activation[cx][cy] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        for (int t = 0; t < 20; t++) {
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        double readyOld = exponentialReadiness(memory[cx][cy]);
        double readyNew = exponentialReadiness(0.0);
        return readyOld - readyNew;
    }

    private double measureTerritorialStability(ParameterSet params) {
        int size = 29;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        int[] sx = {7, 22, 14};
        int[] sy = {7, 7, 22};
        for (int t = 0; t < 25; t++) {
            for (int i = 0; i < 3; i++) activation[sx[i]][sy[i]] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        double[] dom1 = computeDominance(computeInfluence(activation, sx, sy, size));
        for (int t = 0; t < 35; t++) {
            for (int i = 0; i < 3; i++) activation[sx[i]][sy[i]] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        double[] dom2 = computeDominance(computeInfluence(activation, sx, sy, size));
        double change = Math.abs(dom1[0] - dom2[0]) + Math.abs(dom1[1] - dom2[1]) + Math.abs(dom1[2] - dom2[2]);
        return 1.0 - Math.min(change, 1.0);
    }

    private int measureActiveRegions(ParameterSet params) {
        int size = 29;
        double threshold = 0.01;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        int cx = size / 2, cy = size / 2;
        for (int t = 0; t < 30; t++) {
            activation[cx][cy] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        return countConnectedComponents(activation, size, threshold);
    }

    private double measureLargestConnectedComponent(ParameterSet params) {
        int size = 29;
        double threshold = 0.01;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        int cx = size / 2, cy = size / 2;
        for (int t = 0; t < 30; t++) {
            activation[cx][cy] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        return largestConnectedComponentSize(activation, size, threshold);
    }

    private double measureAverageTransport(ParameterSet params) {
        int size = 29;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        int cx = size / 2, cy = size / 2;
        double totalTransport = 0.0;
        int ticks = 0;
        for (int t = 0; t < 30; t++) {
            activation[cx][cy] += 0.5;
            totalTransport += stepSimulation(activation, memory, fieldDepth, params, size);
            ticks++;
        }
        return (ticks == 0) ? 0.0 : totalTransport / ticks;
    }

    private double measureMaximumTransport(ParameterSet params) {
        int size = 29;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        int cx = size / 2, cy = size / 2;
        double maxTransport = 0.0;
        for (int t = 0; t < 30; t++) {
            activation[cx][cy] += 0.5;
            maxTransport = Math.max(maxTransport, stepSimulation(activation, memory, fieldDepth, params, size));
        }
        return maxTransport;
    }

    private double measureActivityDiversity(ParameterSet params) {
        int size = 29;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        int cx = size / 2, cy = size / 2;
        for (int t = 0; t < 30; t++) {
            activation[cx][cy] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        return computeActivationDiversity(activation, size);
    }

    private double measureLocalityScore(ParameterSet params) {
        int size = 55;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        int x1 = 12, y1 = 27;
        int x2 = 42, y2 = 27;
        for (int t = 0; t < 25; t++) {
            activation[x1][y1] += 0.5;
            activation[x2][y2] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        double source1 = activation[x1][y1];
        double source2 = activation[x2][y2];
        double midpoint = activation[27][27];
        double sourceAvg = (source1 + source2) / 2.0;
        return sourceAvg > 0 ? 1.0 - (midpoint / sourceAvg) : 0.0;
    }

    private double stepSimulation(double[][] activation, double[][] memory, double[][] fieldDepth, ParameterSet params, int size) {
        double[][] newActivation = new double[size][size];
        double totalTransport = 0.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double incomingTransport = 0.0;
                for (int dy = -15; dy <= 15; dy++) {
                    for (int dx = -15; dx <= 15; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || nx >= size || ny < 0 || ny >= size) continue;
                        double sourceAct = activation[nx][ny];
                        double readiness = exponentialReadiness(memory[nx][ny]);
                        double destCap = 1.0 / (1.0 + 0.5 * activation[x][y]);
                        double distance = Math.exp(-Math.hypot(dx, dy) / params.v3DistanceDecay);
                        double transport = sourceAct * readiness * destCap * distance * params.v2TransportGain;
                        incomingTransport += transport;
                    }
                }
                newActivation[x][y] = activation[x][y] * (1.0 - params.v2ActivationDecay) + incomingTransport;
                if (activation[x][y] > 0) {
                    memory[x][y] = Math.min(10.0, memory[x][y] + params.v2MemoryGain * activation[x][y]);
                }
                memory[x][y] *= (1.0 - params.v2MemoryDecay);
                totalTransport += incomingTransport;
            }
        }
        for (int y = 0; y < size; y++) {
            System.arraycopy(newActivation[y], 0, activation[y], 0, size);
        }
        return totalTransport;
    }

    private double exponentialReadiness(double memory) {
        return Math.exp(-memory / READINESS_SCALE);
    }

    private double getMaxActivation(double[][] activation, int size) {
        double max = 0;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) max = Math.max(max, activation[x][y]);
        return max;
    }

    private double measureActiveRadius(double[][] activation, int size, double threshold) {
        int cx = size / 2, cy = size / 2;
        double maxDist = 0;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            if (activation[x][y] > threshold) {
                maxDist = Math.max(maxDist, Math.hypot(x - cx, y - cy));
            }
        }
        return maxDist;
    }

    private int countConnectedComponents(double[][] activation, int size, double threshold) {
        boolean[][] seen = new boolean[size][size];
        int count = 0;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            if (!seen[x][y] && activation[x][y] > threshold) {
                count++;
                floodFill(activation, seen, x, y, size, threshold);
            }
        }
        return count;
    }

    private double largestConnectedComponentSize(double[][] activation, int size, double threshold) {
        boolean[][] seen = new boolean[size][size];
        int largest = 0;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            if (!seen[x][y] && activation[x][y] > threshold) {
                int component = floodFill(activation, seen, x, y, size, threshold);
                largest = Math.max(largest, component);
            }
        }
        return largest;
    }

    private int floodFill(double[][] activation, boolean[][] seen, int sx, int sy, int size, double threshold) {
        int count = 0;
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{sx, sy});
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int x = p[0], y = p[1];
            if (x < 0 || x >= size || y < 0 || y >= size || seen[x][y] || activation[x][y] <= threshold) continue;
            seen[x][y] = true;
            count++;
            stack.push(new int[]{x + 1, y});
            stack.push(new int[]{x - 1, y});
            stack.push(new int[]{x, y + 1});
            stack.push(new int[]{x, y - 1});
        }
        return count;
    }

    private double computeActivationDiversity(double[][] activation, int size) {
        double sum = 0.0;
        double sumSq = 0.0;
        int n = size * size;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            double v = activation[x][y];
            sum += v;
            sumSq += v * v;
        }
        double mean = sum / n;
        double variance = sumSq / n - mean * mean;
        return mean > 0 ? Math.sqrt(Math.max(0.0, variance)) / mean : 0.0;
    }

    private double[][] computeInfluence(double[][] activation, int[] sx, int[] sy, int size) {
        double[][] influence = new double[3][size * size];
        for (int i = 0; i < 3; i++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    influence[i][y * size + x] = activation[sx[i]][sy[i]] * Math.exp(-Math.hypot(x - sx[i], y - sy[i]) / 5.0);
                }
            }
        }
        return influence;
    }

    private double[] computeDominance(double[][] influence) {
        double[] dom = new double[3];
        double total = 0.0;
        for (int i = 0; i < 3; i++) {
            for (double v : influence[i]) dom[i] += v;
            total += dom[i];
        }
        if (total > 0) for (int i = 0; i < 3; i++) dom[i] /= total;
        return dom;
    }

    private void writePhaseDiagramCSV(List<OperatingPoint> points, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("gain,distDecay,persistence,growth,hotspot,corridor,recovery,history,stability,regions,lcc,avgTransport,maxTransport,diversity,locality,regime");
            for (OperatingPoint op : points) {
                pw.printf(Locale.US, "%.2f,%.2f,%.0f,%.1f,%.1f,%.4f,%.3f,%.4f,%.3f,%d,%.1f,%.5f,%.5f,%.5f,%.3f,%s%n",
                        op.transportGain, op.distanceDecay, op.settlementPersistence, op.autonomousGrowthRadius,
                        op.popupRadius, op.tradeRouteStrength, op.recoveryEase, op.historicalAdvantage,
                        op.territorialStability, op.activeRegionCount, op.largestActiveComponent,
                        op.averageTransport, op.maximumTransport, op.activityDiversity, op.localityScore,
                        op.regime);
            }
        }
    }

    private void writePhaseSummary(List<OperatingPoint> points, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("PHASE DIAGRAM SUMMARY");
            pw.println("======================\n");
            Map<String, List<OperatingPoint>> groups = new TreeMap<>();
            for (OperatingPoint op : points) groups.computeIfAbsent(op.regime, k -> new ArrayList<>()).add(op);
            for (String regime : groups.keySet()) {
                List<OperatingPoint> list = groups.get(regime);
                pw.printf("%s: %d points\n", regime, list.size());
                if (!list.isEmpty()) {
                    for (int i = 0; i < Math.min(3, list.size()); i++) {
                        OperatingPoint op = list.get(i);
                        pw.printf("  %.2f,%.2f persist=%.0f growth=%.1f corridor=%.4f origin=%s\n",
                                op.transportGain, op.distanceDecay, op.settlementPersistence,
                                op.autonomousGrowthRadius, op.tradeRouteStrength, op.regime);
                    }
                }
                pw.println();
            }
        }
    }

    private void writeRegimeStatistics(List<OperatingPoint> points, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("regime,count,meanPersistence,meanGrowth,meanCorridor,meanStability,meanRegions,meanLocality");
            Map<String, List<OperatingPoint>> groups = new TreeMap<>();
            for (OperatingPoint op : points) groups.computeIfAbsent(op.regime, k -> new ArrayList<>()).add(op);
            for (String regime : groups.keySet()) {
                List<OperatingPoint> list = groups.get(regime);
                double sumPersist = 0, sumGrow = 0, sumCorr = 0, sumStab = 0, sumRegions = 0, sumLocal = 0;
                for (OperatingPoint op : list) {
                    sumPersist += op.settlementPersistence;
                    sumGrow += op.autonomousGrowthRadius;
                    sumCorr += op.tradeRouteStrength;
                    sumStab += op.territorialStability;
                    sumRegions += op.activeRegionCount;
                    sumLocal += op.localityScore;
                }
                int n = list.size();
                pw.printf(Locale.US, "%s,%d,%.1f,%.1f,%.4f,%.4f,%.2f,%.4f%n",
                        regime, n, sumPersist / n, sumGrow / n, sumCorr / n, sumStab / n, sumRegions / n, sumLocal / n);
            }
        }
    }

    private void writeBoundaryLocations(List<OperatingPoint> points, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("gain,distDecay,regime,persistence,growth,corridor,regions,lcc,locality");
            for (OperatingPoint op : points) {
                if (op.regime.equals("DEAD") || op.regime.equals("RUNAWAY") || op.regime.equals("STABLE_SETTLEMENTS") || op.regime.equals("TRADE_CORRIDORS")) {
                    pw.printf(Locale.US, "%.2f,%.2f,%s,%.0f,%.1f,%.4f,%d,%.1f,%.4f%n",
                            op.transportGain, op.distanceDecay, op.regime,
                            op.settlementPersistence, op.autonomousGrowthRadius,
                            op.tradeRouteStrength, op.activeRegionCount, op.largestActiveComponent, op.localityScore);
                }
            }
        }
    }

    private void writeGameplayWindowReport(List<OperatingPoint> points, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("GAMEPLAY WINDOW REPORT");
            pw.println("======================\n");
            boolean found = false;
            for (OperatingPoint op : points) {
                if (op.regime.equals("STABLE_SETTLEMENTS") || op.regime.equals("TRADE_CORRIDORS") || op.regime.equals("REGIONAL_NETWORKS")) {
                    if (!found) {
                        pw.println("Candidate playable operating points:");
                        found = true;
                    }
                    pw.printf("- Gain=%.2f DistDecay=%.2f regime=%s persist=%.0f growth=%.1f corridor=%.4f locality=%.3f\n",
                            op.transportGain, op.distanceDecay, op.regime,
                            op.settlementPersistence, op.autonomousGrowthRadius,
                            op.tradeRouteStrength, op.localityScore);
                }
            }
            if (!found) {
                pw.println("No candidate playable window detected in the sampled boundary region.");
            }
            pw.println();
            pw.println("Summary criteria used:");
            pw.println("  - STABLE_SETTLEMENTS: persistence>180, growth 5-14, corridor <0.01, locality>0.8");
            pw.println("  - TRADE_CORRIDORS: persistence>120, corridor>0.01, growth>8, locality>0.5");
            pw.println("  - REGIONAL_NETWORKS: active regions>1, stability<0.95, locality>0.6");
        }
    }

    private void writeWorldEvolutionExamples(List<OperatingPoint> points, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("WORLD EVOLUTION EXAMPLES");
            pw.println("========================\n");
            for (OperatingPoint op : points) {
                if (op.regime.equals("DEAD") || op.regime.equals("RUNAWAY") || op.regime.equals("STABLE_SETTLEMENTS") || op.regime.equals("TRADE_CORRIDORS")) {
                    pw.printf("Point: gain=%.2f distDecay=%.2f\n", op.transportGain, op.distanceDecay);
                    pw.printf("  Regime: %s\n", op.regime);
                    pw.printf("  Persistence: %.0f ticks\n", op.settlementPersistence);
                    pw.printf("  Growth radius: %.1f\n", op.autonomousGrowthRadius);
                    pw.printf("  Trade corridor: %.4f\n", op.tradeRouteStrength);
                    pw.printf("  Largest component: %.1f cells\n", op.largestActiveComponent);
                    pw.printf("  Region count: %d\n", op.activeRegionCount);
                    pw.printf("  Locality score: %.3f\n", op.localityScore);
                    pw.println();
                }
            }
        }
    }

    static class OperatingPoint {
        double transportGain;
        double distanceDecay;
        double settlementPersistence;
        double autonomousGrowthRadius;
        double popupRadius;
        double tradeRouteStrength;
        double recoveryEase;
        double historicalAdvantage;
        double territorialStability;
        int activeRegionCount;
        double largestActiveComponent;
        double averageTransport;
        double maximumTransport;
        double activityDiversity;
        double localityScore;
        String regime;

        OperatingPoint(double gain, double distDecay) {
            this.transportGain = gain;
            this.distanceDecay = distDecay;
        }

        void classify() {
            if (settlementPersistence < 80 && autonomousGrowthRadius < 5) {
                regime = "DEAD";
                return;
            }
            if (autonomousGrowthRadius > 15 || largestActiveComponent > 150) {
                regime = "RUNAWAY";
                return;
            }
            if (tradeRouteStrength > 0.01 && settlementPersistence > 120 && localityScore > 0.5) {
                regime = "TRADE_CORRIDORS";
                return;
            }
            if (settlementPersistence > 180 && autonomousGrowthRadius >= 5 && autonomousGrowthRadius <= 14 && tradeRouteStrength < 0.01 && localityScore > 0.75) {
                regime = "STABLE_SETTLEMENTS";
                return;
            }
            if (activeRegionCount > 1 && territorialStability < 0.95 && localityScore > 0.6) {
                regime = "REGIONAL_NETWORKS";
                return;
            }
            if (settlementPersistence < 100 && historicalAdvantage > 0 && tradeRouteStrength < 0.01) {
                regime = "SCAR_ONLY";
                return;
            }
            if (settlementPersistence > 100 && historicalAdvantage > 0 && tradeRouteStrength < 0.01) {
                regime = "LOCALIZED_HISTORY";
                return;
            }
            if (tradeRouteStrength > 0.01 && localityScore < 0.5) {
                regime = "WORLD_SCALE_SYNCHRONIZATION";
                return;
            }
            regime = "MARGINAL";
        }
    }
}

