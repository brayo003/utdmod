import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameplayArchetypeValidationTest {

    private final String outDir = "run/archetype_validation";
    private final double ACTIVE_THRESHOLD = 0.001;
    private final int TRANSPORT_RADIUS = 15;

    @Test
    void runGameplayArchetypeValidation() throws Exception {
        File d = new File(outDir);
        if (!d.exists()) d.mkdirs();

        ParameterSet p = candidateParameters();
        writeHarnessHeader(new File(d, "summary.txt"), p);

        runMinerArchetype(p, new File(d, "miner"));
        runBuilderArchetype(p, new File(d, "builder"));
        runTraderArchetype(p, new File(d, "trader"));
        runExplorerArchetype(p, new File(d, "explorer"));
    }

    private ParameterSet candidateParameters() {
        ParameterSet p = new ParameterSet();
        p.v2TransportGain = 0.03;
        p.v3DistanceDecay = 1.20;
        p.v2ActivationDecay = 0.45;
        p.v2MemoryGain = 0.02;
        p.v2MemoryDecay = 0.001;
        p.readinessMemoryScale = 2.0;
        p.v3FieldDepthGain = 0.01;
        p.v3FieldDepthDecay = 0.0001;
        p.transportBalanceMode = "frozen";
        p.validationMaxHotspotRadius = 12.0;
        return p;
    }

    private void writeHarnessHeader(File summaryFile, ParameterSet p) throws IOException {
        try (PrintWriter log = new PrintWriter(new FileWriter(summaryFile))) {
            log.println("GAMEPLAY ARCHETYPE VALIDATION HARNESS");
            log.println("====================================");
            log.println("Operating point selected from dense adaptive boundary sweep:");
            log.println(String.format("  transportGain=%.2f, distanceDecay=%.2f", p.v2TransportGain, p.v3DistanceDecay));
            log.println("Frozen transport architecture preserved. No parameter tuning beyond window selection.");
            log.println();
            log.println("Readiness law: exp(-memory / " + p.readinessMemoryScale + ")");
            log.println("Activation decay: " + p.v2ActivationDecay);
            log.println("Memory gain/decay: " + p.v2MemoryGain + " / " + p.v2MemoryDecay);
            log.println("Field depth gain/decay: " + p.v3FieldDepthGain + " / " + p.v3FieldDepthDecay);
            log.println();
        }
    }

    private void runMinerArchetype(ParameterSet p, File outDir) throws IOException {
        if (!outDir.exists()) outDir.mkdirs();
        PrintWriter log = new PrintWriter(new FileWriter(new File(outDir, "summary.txt")));
        log.println("SCENARIO: MINER ARCHETYPE");
        log.println("Question: Does past extraction remain easier to reopen than new sites?");
        log.println();

        int size = 51;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];

        int oldX = 15, oldY = 25;
        int newX = 35, newY = 25;
        double extractionStrength = 0.6;

        for (int t = 0; t < 120; t++) {
            activation[oldY][oldX] = extractionStrength;
            stepSimulation(activation, memory, fieldDepth, p, size);
            if (t % 30 == 0) {
                log.printf("build tick=%d oldMemory=%.4f oldActivation=%.4f\n", t, meanNearby(memory, oldX, oldY, 3), activation[oldY][oldX]);
            }
        }

        for (int t = 0; t < 80; t++) {
            activation[oldY][oldX] = 0.0;
            stepSimulation(activation, memory, fieldDepth, p, size);
        }

        double beforeOld = meanNearby(memory, oldX, oldY, 3);
        double beforeNew = meanNearby(memory, newX, newY, 3);

        List<Double> oldActivationTrace = new ArrayList<>();
        List<Double> newActivationTrace = new ArrayList<>();

        for (int t = 0; t < 100; t++) {
            activation[oldY][oldX] = extractionStrength;
            activation[newY][newX] = extractionStrength;
            stepSimulation(activation, memory, fieldDepth, p, size);
            oldActivationTrace.add(meanNearby(activation, oldX, oldY, 3));
            newActivationTrace.add(meanNearby(activation, newX, newY, 3));
        }

        double oldFinal = average(oldActivationTrace);
        double newFinal = average(newActivationTrace);
        double advantageRatio = newFinal > 0.0 ? oldFinal / newFinal : Double.POSITIVE_INFINITY;
        double oldMemory = meanNearby(memory, oldX, oldY, 3);
        double newMemory = meanNearby(memory, newX, newY, 3);

        log.println();
        log.println("Result summary:");
        log.println(String.format("  old site final activation=%.4f", oldFinal));
        log.println(String.format("  new site final activation=%.4f", newFinal));
        log.println(String.format("  old/new activation ratio=%.4f", advantageRatio));
        log.println(String.format("  old site memory=%.4f", oldMemory));
        log.println(String.format("  new site memory=%.4f", newMemory));
        log.println(String.format("  sustained old region size=%d", countActiveRegionSize(activation, oldX, oldY, 3)));
        log.println(String.format("  sustained new region size=%d", countActiveRegionSize(activation, newX, newY, 3)));
        log.println();
        log.println("Gameplay verdict:");
        if (advantageRatio > 1.05 && oldMemory > newMemory) {
            log.println("  PASS: previously mined location remains easier to reactivate than a new one.");
        } else {
            log.println("  WARNING: old mine does not show a clear reopening advantage.");
        }

        log.close();
    }

    private void runBuilderArchetype(ParameterSet p, File outDir) throws IOException {
        if (!outDir.exists()) outDir.mkdirs();
        PrintWriter log = new PrintWriter(new FileWriter(new File(outDir, "summary.txt")));
        log.println("SCENARIO: BUILDER ARCHETYPE");
        log.println("Question: Does a modest settlement grow steadily without runaway or collapse?");
        log.println();

        int size = 61;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];

        int centerX = size / 2;
        int centerY = size / 2;
        double buildStrength = 0.4;

        List<Integer> activeAreas = new ArrayList<>();
        List<Double> maxActivations = new ArrayList<>();

        for (int t = 0; t < 100; t++) {
            activation[centerY][centerX] = buildStrength;
            stepSimulation(activation, memory, fieldDepth, p, size);
            if (t % 20 == 0) {
                activeAreas.add(countActiveCells(activation, ACTIVE_THRESHOLD));
                maxActivations.add(maxValue(activation));
                log.printf("build tick=%d activeArea=%d maxAct=%.4f\n", t, activeAreas.get(activeAreas.size()-1), maxActivations.get(maxActivations.size()-1));
            }
        }

        for (int t = 0; t < 200; t++) {
            activation[centerY][centerX] = 0.0;
            stepSimulation(activation, memory, fieldDepth, p, size);
            if (t % 50 == 0) {
                activeAreas.add(countActiveCells(activation, ACTIVE_THRESHOLD));
                maxActivations.add(maxValue(activation));
            }
        }

        int finalArea = countActiveCells(activation, ACTIVE_THRESHOLD);
        double finalRadius = maxDistanceFromCenter(activation, centerX, centerY, ACTIVE_THRESHOLD);
        log.println();
        log.println("Result summary:");
        log.println("  final active cells=" + finalArea);
        log.println(String.format("  final active radius=%.2f", finalRadius));
        log.println(String.format("  final max activation=%.4f", maxValue(activation)));
        log.println(String.format("  memory at center=%.4f", memory[centerY][centerX]));
        log.println();
        if (finalArea > 150 && finalRadius < 18.0 && finalRadius > 6.0) {
            log.println("  PASS: settlement grows to a stable region without runaway.");
        } else if (finalArea <= 80) {
            log.println("  WARNING: settlement failed to sustain a stable footprint.");
        } else {
            log.println("  NOTE: settlement grows but may be marginal or too diffuse.");
        }

        log.close();
    }

    private void runTraderArchetype(ParameterSet p, File outDir) throws IOException {
        if (!outDir.exists()) outDir.mkdirs();
        PrintWriter log = new PrintWriter(new FileWriter(new File(outDir, "summary.txt")));
        log.println("SCENARIO: TRADER ARCHETYPE");
        log.println("Question: Do transport corridors emerge between two sustained hubs?");
        log.println();

        int size = 61;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];

        int aX = 12, aY = 15;
        int bX = 48, bY = 45;
        double marketStrength = 0.4;

        List<Double> corridorStrength = new ArrayList<>();

        for (int t = 0; t < 250; t++) {
            activation[aY][aX] = marketStrength;
            activation[bY][bX] = marketStrength;
            stepSimulation(activation, memory, fieldDepth, p, size);
            if (t >= 100 && t % 25 == 0) {
                double meanCorridor = computeCorridorMean(activation, aX, aY, bX, bY);
                corridorStrength.add(meanCorridor);
                log.printf("tick=%d corridorMean=%.4f activeA=%.4f activeB=%.4f\n", t, meanCorridor, activation[aY][aX], activation[bY][bX]);
            }
        }

        double averageCorridor = average(corridorStrength);
        double finalTradeFlow = computeCorridorMean(activation, aX, aY, bX, bY);
        log.println();
        log.println("Result summary:");
        log.println(String.format("  corridor mean activation=%.4f", averageCorridor));
        log.println(String.format("  final corridor activation=%.4f", finalTradeFlow));
        log.println(String.format("  hub A activation=%.4f", activation[aY][aX]));
        log.println(String.format("  hub B activation=%.4f", activation[bY][bX]));
        log.println(String.format("  corridor length=%.2f", Math.hypot(aX - bX, aY - bY)));
        log.println();
        if (averageCorridor > 0.015 && finalTradeFlow > 0.02) {
            log.println("  PASS: trade-like corridor emerges between hubs.");
        } else {
            log.println("  WARNING: corridor activity remains weak or highly localized.");
        }

        log.close();
    }

    private void runExplorerArchetype(ParameterSet p, File outDir) throws IOException {
        if (!outDir.exists()) outDir.mkdirs();
        PrintWriter log = new PrintWriter(new FileWriter(new File(outDir, "summary.txt")));
        log.println("SCENARIO: EXPLORER ARCHETYPE");
        log.println("Question: Can sparse exploration generate sustained discovery without runaway?");
        log.println();

        int size = 61;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        Random rng = new Random(12345);

        int centerX = size / 2;
        int centerY = size / 2;
        List<Integer> sustainedClusters = new ArrayList<>();
        List<Double> frontierRadii = new ArrayList<>();

        for (int t = 0; t < 300; t++) {
            if (t % 40 == 0) {
                int angle = t * 30;
                int radius = 18 + (t / 80) * 4;
                int x = centerX + (int) Math.round(radius * Math.cos(Math.toRadians(angle)));
                int y = centerY + (int) Math.round(radius * Math.sin(Math.toRadians(angle)));
                if (x >= 0 && x < size && y >= 0 && y < size) {
                    activation[y][x] += 0.5;
                }
            }
            stepSimulation(activation, memory, fieldDepth, p, size);
            if (t % 30 == 0) {
                frontierRadii.add(maxDistanceFromCenter(activation, centerX, centerY, ACTIVE_THRESHOLD));
                sustainedClusters.add(countConnectedComponents(activation, size, ACTIVE_THRESHOLD));
                log.printf("tick=%d frontierRadius=%.2f clusters=%d totalActive=%d\n", t, frontierRadii.get(frontierRadii.size()-1), sustainedClusters.get(sustainedClusters.size()-1), countActiveCells(activation, ACTIVE_THRESHOLD));
            }
        }

        double finalRadius = frontierRadii.isEmpty() ? 0.0 : frontierRadii.get(frontierRadii.size() - 1);
        int finalClusters = sustainedClusters.isEmpty() ? 0 : sustainedClusters.get(sustainedClusters.size() - 1);
        log.println();
        log.println("Result summary:");
        log.println(String.format("  final frontier radius=%.2f", finalRadius));
        log.println("  sustained active clusters=" + finalClusters);
        log.println(String.format("  total active cells=%d", countActiveCells(activation, ACTIVE_THRESHOLD)));
        log.println();
        if (finalRadius > 18.0 && finalClusters >= 3) {
            log.println("  PASS: explorer archetype can discover new regions and maintain them.");
        } else {
            log.println("  WARNING: exploration remains too localized or too transient.");
        }

        log.close();
    }

    private void stepSimulation(double[][] activation, double[][] memory, double[][] fieldDepth, ParameterSet p, int size) {
        double[][] nextActivation = new double[size][size];

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double incoming = 0.0;
                for (int dy = -TRANSPORT_RADIUS; dy <= TRANSPORT_RADIUS; dy++) {
                    for (int dx = -TRANSPORT_RADIUS; dx <= TRANSPORT_RADIUS; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || nx >= size || ny < 0 || ny >= size) continue;
                        double source = activation[ny][nx];
                        if (source <= 0.0) continue;
                        double readiness = Math.exp(-memory[ny][nx] / p.readinessMemoryScale);
                        double destCap = 1.0 / (1.0 + 0.5 * activation[y][x]);
                        double distance = Math.exp(-Math.hypot(dx, dy) / p.v3DistanceDecay);
                        incoming += source * readiness * destCap * distance * p.v2TransportGain;
                    }
                }
                nextActivation[y][x] = activation[y][x] * (1.0 - p.v2ActivationDecay) + incoming;
            }
        }

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                activation[y][x] = nextActivation[y][x];
                memory[y][x] = memory[y][x] * (1.0 - p.v2MemoryDecay) + Math.abs(activation[y][x]) * p.v2MemoryGain;
                fieldDepth[y][x] = fieldDepth[y][x] * (1.0 - p.v3FieldDepthDecay) + p.v3FieldDepthGain * Math.max(0.0, activation[y][x]);
            }
        }
    }

    private double meanNearby(double[][] grid, int cx, int cy, int radius) {
        double sum = 0.0;
        int count = 0;
        for (int y = Math.max(0, cy - radius); y <= Math.min(grid.length - 1, cy + radius); y++) {
            for (int x = Math.max(0, cx - radius); x <= Math.min(grid[0].length - 1, cx + radius); x++) {
                sum += grid[y][x];
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private int countActiveCells(double[][] grid, double threshold) {
        int count = 0;
        for (int y = 0; y < grid.length; y++) {
            for (int x = 0; x < grid[0].length; x++) {
                if (grid[y][x] >= threshold) count++;
            }
        }
        return count;
    }

    private double maxValue(double[][] grid) {
        double max = 0.0;
        for (double[] row : grid) {
            for (double v : row) {
                max = Math.max(max, v);
            }
        }
        return max;
    }

    private double maxDistanceFromCenter(double[][] grid, int cx, int cy, double threshold) {
        double max = 0.0;
        for (int y = 0; y < grid.length; y++) {
            for (int x = 0; x < grid[0].length; x++) {
                if (grid[y][x] >= threshold) {
                    double dist = Math.hypot(x - cx, y - cy);
                    max = Math.max(max, dist);
                }
            }
        }
        return max;
    }

    private int countConnectedComponents(double[][] grid, int size, double threshold) {
        boolean[][] seen = new boolean[size][size];
        int count = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!seen[y][x] && grid[y][x] >= threshold) {
                    floodFill(grid, seen, x, y, size, threshold);
                    count++;
                }
            }
        }
        return count;
    }

    private void floodFill(double[][] grid, boolean[][] seen, int x, int y, int size, double threshold) {
        List<int[]> stack = new ArrayList<>();
        stack.add(new int[]{x, y});
        while (!stack.isEmpty()) {
            int[] coord = stack.remove(stack.size() - 1);
            int cx = coord[0];
            int cy = coord[1];
            if (cx < 0 || cx >= size || cy < 0 || cy >= size) continue;
            if (seen[cy][cx] || grid[cy][cx] < threshold) continue;
            seen[cy][cx] = true;
            stack.add(new int[]{cx + 1, cy});
            stack.add(new int[]{cx - 1, cy});
            stack.add(new int[]{cx, cy + 1});
            stack.add(new int[]{cx, cy - 1});
        }
    }

    private int countActiveRegionSize(double[][] grid, int cx, int cy, int radius) {
        int size = grid.length;
        int count = 0;
        for (int y = Math.max(0, cy - radius); y <= Math.min(size - 1, cy + radius); y++) {
            for (int x = Math.max(0, cx - radius); x <= Math.min(size - 1, cx + radius); x++) {
                if (grid[y][x] >= ACTIVE_THRESHOLD) count++;
            }
        }
        return count;
    }

    private double computeCorridorMean(double[][] grid, int ax, int ay, int bx, int by) {
        int steps = 50;
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.round(ax + t * (bx - ax));
            int y = (int) Math.round(ay + t * (by - ay));
            if (x >= 0 && x < grid[0].length && y >= 0 && y < grid.length) {
                sum += grid[y][x];
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private double average(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.size();
    }
}
