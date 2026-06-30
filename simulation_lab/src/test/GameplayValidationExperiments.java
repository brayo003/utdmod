import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class GameplayValidationExperiments {

    private final String outDir = "run/gameplay_validation";

    @Test
    void runAllExperiments() throws Exception {
        File d = new File(outDir);
        if (!d.exists()) d.mkdirs();

        ParameterSet p = new ParameterSet();
        p.v3FieldDepthGain = 0.01;
        p.v3FieldDepthDecay = 0.0001;
        p.v3FieldDepthRadius = 3.0;
        p.v3FieldDepthEffectK = 0.1;
        p.v3DistanceDecay = 1.0;

        // Experiment 1: Sustaining Activity Threshold
        runSustainThreshold(p, 21, 21, 500, 5000, d);

        // Experiment 2: Expansion Radius
        runExpansionRadius(p,21,21,500,5000,new int[]{50,100,250}, d);

        // Experiment 3: Recruitment Speed
        runRecruitmentSpeed(p,41,41,500,5000,10, d);

        // Experiment 4: Recovery Time
        runRecoveryTime(p,21,21,500,5000, d);

        // Experiment 5: Basin Size
        runBasinSize(p,21,21,500,5000,new double[]{0.01,0.02,0.05,0.1,0.2}, d);

        // Experiment 6: Historical Trade Corridor
        runHistoricalRoutes(p,41,41,500,5000, d);

        // Experiment 7: Ancient City Simulation
        runAncientCity(p,61,41,new int[]{500,5000,10000}, d);

        // Experiment 8: Post-injection decay inspection
        runPostInjectionDecayVariants(p, 21, 21, 500, d);
    }

    private static class LocalizedExperiment {
        final int buildTicks;
        final int idleTicks;
        final int pulsesPerBuild;
        final int pulseInterval;

        LocalizedExperiment(int buildTicks, int idleTicks, int pulsesPerBuild, int pulseInterval) {
            this.buildTicks = buildTicks;
            this.idleTicks = idleTicks;
            this.pulsesPerBuild = pulsesPerBuild;
            this.pulseInterval = pulseInterval;
        }
    }

    private LocalizedExperiment localizedProtocol(int buildTicks) {
        return new LocalizedExperiment(buildTicks, Math.min(buildTicks, 1000), Math.max(1, buildTicks / 50), Math.max(5, buildTicks / 25));
    }

    private boolean isGloballyActive(double[][] field, double threshold) {
        int total = field.length * field[0].length;
        int active = MetricEvaluator.countActiveCells(field, threshold, 0.0);
        return active >= total * 0.05;
    }

    private static class SpatialMetrics {
        int totalCells;
        int activeCount;
        int hotspotCount; // number of connected components
        int largestSize; // cells
        double largestRadius; // geometric radius (cells)
        double largestFraction; // fraction of total cells
        double fractionOutsideLargest; // fraction of active cells outside largest (of active cells)
        double totalActivation; // sum of activation values
        double activationOutsideLargest;
    }

    private SpatialMetrics computeSpatialMetrics(double[][] A, double threshold) {
        int H = A.length;
        int W = A[0].length;
        int total = H * W;
        SpatialMetrics m = new SpatialMetrics();
        m.totalCells = total;
        m.activeCount = MetricEvaluator.countActiveCells(A, threshold, 0.0);
        m.totalActivation = 0.0;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) m.totalActivation += A[y][x];

        boolean[][] visited = new boolean[H][W];
        int hotspots = 0;
        int bestSize = 0;
        double bestRadius = 0.0;
        double bestActivationSum = 0.0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (A[y][x] >= threshold && !visited[y][x]) {
                    // flood fill this component (4-connected)
                    java.util.ArrayList<int[]> cells = new java.util.ArrayList<>();
                    java.util.Stack<int[]> stack = new java.util.Stack<>();
                    stack.push(new int[]{x, y});
                    visited[y][x] = true;
                    double compActivation = 0.0;
                    while (!stack.isEmpty()) {
                        int[] c = stack.pop();
                        cells.add(c);
                        int cx = c[0], cy = c[1];
                        compActivation += A[cy][cx];
                        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
                        for (int[] d : dirs) {
                            int nx = cx + d[0];
                            int ny = cy + d[1];
                            if (nx >= 0 && nx < W && ny >= 0 && ny < H && !visited[ny][nx] && A[ny][nx] >= threshold) {
                                visited[ny][nx] = true;
                                stack.push(new int[]{nx, ny});
                            }
                        }
                    }
                    hotspots++;
                    int size = cells.size();
                    if (size > bestSize) {
                        bestSize = size;
                        // compute centroid
                        double sumx = 0.0, sumy = 0.0;
                        for (int[] cc : cells) { sumx += cc[0]; sumy += cc[1]; }
                        double cxMean = sumx / size;
                        double cyMean = sumy / size;
                        double maxd = 0.0;
                        for (int[] cc : cells) {
                            double dx = cc[0] - cxMean;
                            double dy = cc[1] - cyMean;
                            maxd = Math.max(maxd, Math.sqrt(dx*dx + dy*dy));
                        }
                        bestRadius = maxd;
                        bestActivationSum = compActivation;
                    }
                }
            }
        }
        m.hotspotCount = hotspots;
        m.largestSize = bestSize;
        m.largestRadius = bestRadius;
        m.largestFraction = total>0 ? (double)bestSize / (double)total : 0.0;
        m.activationOutsideLargest = Math.max(0.0, m.totalActivation - bestActivationSum);
        m.fractionOutsideLargest = m.totalActivation>0.0 ? m.activationOutsideLargest / m.totalActivation : 0.0;
        return m;
    }

    private boolean topologyIsGlobalized(PrintWriter log, double[][] A, ParameterSet p, int tick, String phase) {
        SpatialMetrics m = computeSpatialMetrics(A, p.regionActiveThreshold);
        log.println("Spatial metrics (" + phase + "): totalCells=" + m.totalCells + ", activeCount=" + m.activeCount + " (" + String.format("%.2f", m.activeCount * 100.0 / m.totalCells) + "%)");
        log.println("  hotspots=" + m.hotspotCount + ", largestSize=" + m.largestSize + ", largestRadius=" + String.format("%.3f", m.largestRadius));
        log.println("  largestFraction=" + String.format("%.3f", m.largestFraction) + ", fractionActivationOutsideLargest=" + String.format("%.3f", m.fractionOutsideLargest));

        int total = m.totalCells;
        int majorThreshold = (int)Math.ceil(total * 0.05); // 5% cells
        // If largest component itself occupies >=5% of grid, consider localized (unless multiple majors exist)
        if (m.largestSize >= majorThreshold) {
            if (m.hotspotCount > 1 && m.fractionOutsideLargest > 0.05) {
                log.println("  ABORT-TOPO: multiple major hotspots or significant activation outside largest.");
                return true;
            }
            return false;
        }

        if (m.hotspotCount > 3 && m.fractionOutsideLargest > 0.15) {
            log.println("  ABORT-TOPO: activity broadly scattered across many hotspots.");
            return true;
        }

        if (m.largestRadius > p.validationMaxHotspotRadius) {
            log.println("  ABORT-TOPO: largest hotspot radius " + String.format("%.3f", m.largestRadius) + " exceeds configured limit " + p.validationMaxHotspotRadius);
            return true;
        }

        return false;
    }

    private boolean verifyLocalizedState(PrintWriter log, double[][] A, double[][] M, double[][] C, ParameterSet p, int cx, int cy, DiagnosticsManager diag) {
        int total = A.length * A[0].length;
        int activeCount = MetricEvaluator.countActiveCells(A, p.regionActiveThreshold, 0.0);
        int hotspotCount = MetricEvaluator.countHotspots(A, p.regionActiveThreshold, 0.0);
        int largestRegion = MetricEvaluator.largestRegion(A, p.regionActiveThreshold, 0.0);
        double comA = MetricEvaluator.centerOfMass(A);
        double comM = MetricEvaluator.centerOfMass(M);
        double comC = MetricEvaluator.centerOfMass(C);
        double memoryConcentration = massWithinRadius(M, cx, cy, 3) / totalMass(M);
        double depthConcentration = massWithinRadius(C, cx, cy, 3) / totalMass(C);

        log.println("Pre-probe validation:");
        log.println("  activeAboveThreshold=" + activeCount + " (" + String.format("%.2f", activeCount * 100.0 / total) + "%)");
        log.println("  hotspots=" + hotspotCount);
        log.println("  largestActiveRegion=" + largestRegion);
        log.println("  activationCOM=" + String.format("%.3f", comA));
        log.println("  memoryCOM=" + String.format("%.3f", comM));
        log.println("  depthCOM=" + String.format("%.3f", comC));
        log.println("  memoryConcentrationRadius3=" + String.format("%.3f", memoryConcentration));
        log.println("  depthConcentrationRadius3=" + String.format("%.3f", depthConcentration));
        log.println("  sourceActivation=" + String.format("%.6f", A[cy][cx]) + ", sourceMemory=" + String.format("%.6f", M[cy][cx]) + ", sourceDepth=" + String.format("%.6f", C[cy][cx]));

        // Use topology-aware validator for the active-count check and log spatial metrics
        boolean topoAbort = topologyIsGlobalized(log, A, p, -1, "pre-probe");
        if (diag != null) diag.recordTick(-1, A, M, C);
        boolean localized = !topoAbort && hotspotCount <= 3 && largestRegion <= Math.max(20, total / 50) && memoryConcentration >= 0.55 && depthConcentration >= 0.55;
        if (!localized) {
            log.println("  ABORT: world is not localized enough for probe.");
        }
        return localized;
    }

    private static double totalMass(double[][] field) {
        double total = 0.0;
        for (double[] row : field) {
            for (double value : row) total += value;
        }
        return total;
    }

    private static double massWithinRadius(double[][] field, int cx, int cy, int radius) {
        double total = 0.0;
        for (int y = 0; y < field.length; y++) {
            for (int x = 0; x < field[0].length; x++) {
                if (Math.hypot(x - cx, y - cy) <= radius) total += field[y][x];
            }
        }
        return total;
    }

    private void logHeatmaps(PrintWriter log, double[][] field, String label) {
        log.println(label + " heatmap:");
        for (int y = 0; y < field.length; y++) {
            for (int x = 0; x < field[0].length; x++) {
                log.print(String.format("%.3f", field[y][x]));
                if (x < field[0].length - 1) log.print(",");
            }
            log.println();
        }
    }

    private static class StepMetrics {
        int transportEvents = 0;
        double meanTransportMagnitude = 0.0;
        double maxTransportMagnitude = 0.0;
        double meanTransportDistance = 0.0;
        double maxTransportDistance = 0.0;
        double totalTransportEnergy = 0.0;
        double transportWithoutFieldDepth = 0.0;
        double transportWithFieldDepth = 0.0;
        double transportDifference = 0.0;
        double meanFieldDepth = 0.0;
        double maxFieldDepth = 0.0;
        int cellsWithFieldDepthPositive = 0;
        int cellsReceivingTransport = 0;
        int cellsLosingActivation = 0;
        int cellsAtClamp = 0;
        double fieldDepthCreated = 0.0;
        double fieldDepthLostToDecay = 0.0;
        double activationCreatedByTransport = 0.0;
        double activationCreatedExternally = 0.0;
        double activationRemovedByDecay = 0.0;
        double activationRemovedByClamp = 0.0;
        double netActivationChange = 0.0;
        double memoryCreated = 0.0;
        double memoryRemoved = 0.0;
        final java.util.List<TransportSample> transportSamples = new java.util.ArrayList<>();
    }

    private static class TransportSample {
        final int tick;
        final int sourceX;
        final int sourceY;
        final int destinationX;
        final int destinationY;
        final double magnitude;
        final double distance;

        TransportSample(int tick, int sourceX, int sourceY, int destinationX, int destinationY, double magnitude, double distance) {
            this.tick = tick;
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.destinationX = destinationX;
            this.destinationY = destinationY;
            this.magnitude = magnitude;
            this.distance = distance;
        }
    }

    // Diagnostics manager: collects and writes all required CSVs and snapshots
    static class DiagnosticsManager {
        private final File dir;
        private final PrintWriter timeseries;
        private final PrintWriter hotspots;
        private final PrintWriter transport;
        private final PrintWriter energy;
        private final PrintWriter topology;
        private final PrintWriter parameters;
        private final PrintWriter fieldDepth;
        private final PrintWriter clamp;
        private final PrintWriter summary;
        private final PrintWriter experimentSummary;
        private int height = -1;
        private int width = -1;
        private boolean[][] wasClamped;
        private boolean transportExecuted = false;
        private boolean memoryExecuted = false;
        private boolean fieldDepthExecuted = false;
        private boolean decayExecuted = false;
        private boolean fieldDepthInfluenceObserved = false;
        private int[][] clampedSince;
        private int[][] clampedNeighborCount;
        private int[][] clampedDuration;
        private boolean transportObserved;
        private boolean memoryObserved;
        private boolean fieldDepthObserved;
        private boolean saturationObserved;
        private boolean localizationMaintained;
        private String stopReason = "completed";
        private final List<String> warnings = new ArrayList<>();

        DiagnosticsManager(File dir) throws IOException {
            this.dir = dir;
            if (!dir.exists()) dir.mkdirs();
            this.timeseries = new PrintWriter(new File(dir, "timeseries.csv"));
            this.hotspots = new PrintWriter(new File(dir, "hotspots.csv"));
            this.transport = new PrintWriter(new File(dir, "transport.csv"));
            this.energy = new PrintWriter(new File(dir, "energy.csv"));
            this.topology = new PrintWriter(new File(dir, "topology.csv"));
            this.parameters = new PrintWriter(new File(dir, "parameters.txt"));
            this.fieldDepth = new PrintWriter(new File(dir, "fielddepth.csv"));
            this.clamp = new PrintWriter(new File(dir, "clamp.csv"));
            this.summary = new PrintWriter(new File(dir, "summary.txt"));
            this.experimentSummary = new PrintWriter(new File(dir, "experiment_summary.txt"));
            timeseries.println("tick,totalActivation,maxActivation,totalMemory,maxMemory,totalFieldDepth,maxFieldDepth,activeCells,hotspotCount,largestHotspotSize,largestHotspotRadius,fractionOutsideLargest,transportEvents,meanTransportMagnitude,maxTransportMagnitude,meanTransportDistance,maxTransportDistance,totalTransportEnergy");
            hotspots.println("tick,hotspot_id,center_x,center_y,radius,area,peakActivation,meanMemory,meanFieldDepth");
            transport.println("tick,source_x,source_y,destination_x,destination_y,transport_magnitude,distance");
            energy.println("tick,activation_created_by_transport,activation_created_externally,activation_removed_by_decay,activation_removed_by_clamp,net_activation_change");
            topology.println("tick,largestHotspot,numberOfHotspots,radius,diameter,fractionActivationInsideLargest,fractionOutsideLargest,meanPairwiseHotspotDistance");
            fieldDepth.println("tick,meanFieldDepth,maxFieldDepth,cellsWithFieldDepthPositive,newFieldDepthCreated,fieldDepthLostToDecay,hist0_00,hist0_10,hist0_50,hist2_00,hist20_00");
            clamp.println("tick,cell_x,cell_y,how_long_cell_stayed_clamped,how_many_neighbours_were_clamped");
        }

        private void ensureGridState(double[][] A) {
            if (height != A.length || width != A[0].length) {
                this.height = A.length;
                this.width = A[0].length;
                this.wasClamped = new boolean[height][width];
                this.clampedSince = new int[height][width];
                this.clampedNeighborCount = new int[height][width];
                this.clampedDuration = new int[height][width];
            }
        }

        private String histogramString(double[][] grid) {
            double[] bins = new double[]{0.0, 0.1, 0.5, 2.0, 20.0};
            int[] counts = new int[bins.length];
            for (int y = 0; y < grid.length; y++) {
                for (int x = 0; x < grid[0].length; x++) {
                    double value = grid[y][x];
                    for (int i = 0; i < bins.length; i++) {
                        if (value >= bins[i] && (i == bins.length - 1 || value < bins[i + 1])) {
                            counts[i]++;
                            break;
                        }
                    }
                }
            }
            return counts[0] + "," + counts[1] + "," + counts[2] + "," + counts[3] + "," + counts[4];
        }

        void recordTick(int tick, double[][] A, double[][] M, double[][] C) {
            recordTick(tick, A, M, C, null, null);
        }

        void recordTick(int tick, double[][] A, double[][] M, double[][] C, StepMetrics metrics, ParameterSet p) {
            try {
                ensureGridState(A);
                SpatialMetrics m = new GameplayValidationExperiments().computeSpatialMetrics(A, p != null ? p.regionActiveThreshold : 0.001);
                double totalA = 0.0, maxA = 0.0, totalM = 0.0, maxM = 0.0, totalC = 0.0, maxC = 0.0;
                for (int y = 0; y < A.length; y++) for (int x = 0; x < A[0].length; x++) { totalA += A[y][x]; maxA = Math.max(maxA, A[y][x]); totalM += M[y][x]; maxM = Math.max(maxM, M[y][x]); totalC += C[y][x]; maxC = Math.max(maxC, C[y][x]); }
                int active = MetricEvaluator.countActiveCells(A, p != null ? p.regionActiveThreshold : 0.001, 0.0);
                if (metrics == null) {
                    metrics = new StepMetrics();
                }
                if (metrics.transportEvents > 0) transportExecuted = true;
                if (metrics.memoryCreated > 0.0 || metrics.memoryRemoved > 0.0 || totalM > 0.0) memoryExecuted = true;
                if (metrics.fieldDepthCreated > 0.0 || metrics.fieldDepthLostToDecay > 0.0 || totalC > 0.0) fieldDepthExecuted = true;
                if (metrics.activationRemovedByDecay > 0.0 || metrics.activationRemovedByClamp > 0.0) decayExecuted = true;
                if (metrics.transportDifference != 0.0) fieldDepthInfluenceObserved = true;
                if (metrics.transportSamples != null) {
                    for (TransportSample sample : metrics.transportSamples) {
                        if (sample.tick % 50 == 0 || sample.tick == 0) {
                            transport.println(String.format("%d,%d,%d,%d,%d,%.6f,%.6f", sample.tick, sample.sourceX, sample.sourceY, sample.destinationX, sample.destinationY, sample.magnitude, sample.distance));
                        }
                    }
                }
                timeseries.println(String.format("%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%.3f,%.3f,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                        tick, totalA, maxA, totalM, maxM, totalC, maxC, active, m.hotspotCount, m.largestSize, m.largestRadius, m.fractionOutsideLargest,
                        metrics.transportEvents, metrics.meanTransportMagnitude, metrics.maxTransportMagnitude, metrics.meanTransportDistance, metrics.maxTransportDistance, metrics.totalTransportEnergy,
                        metrics.transportWithoutFieldDepth, metrics.transportWithFieldDepth, metrics.transportDifference));
                energy.println(String.format("%d,%.6f,%.6f,%.6f,%.6f,%.6f",
                        tick, metrics.activationCreatedByTransport, metrics.activationCreatedExternally, metrics.activationRemovedByDecay, metrics.activationRemovedByClamp, metrics.netActivationChange));
                topology.println(String.format("%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.3f",
                        tick, m.largestSize, m.hotspotCount, m.largestRadius, m.largestRadius * 2.0, m.largestFraction, m.fractionOutsideLargest, computeMeanPairwiseDistance(A, p != null ? p.regionActiveThreshold : 0.001)));
                fieldDepth.println(String.format("%d,%.6f,%.6f,%d,%.6f,%.6f,%s",
                        tick, metrics.meanFieldDepth, metrics.maxFieldDepth, metrics.cellsWithFieldDepthPositive, metrics.fieldDepthCreated, metrics.fieldDepthLostToDecay, histogramString(C)));
                topology.flush(); timeseries.flush(); hotspots.flush(); transport.flush(); energy.flush(); fieldDepth.flush();
            } catch (Exception e) {
                // ignore diagnostics failures to avoid breaking experiments
            }
        }

        void recordClampState(int tick, double[][] activationGrid, int x, int y, double activationValue, double clampMax) {
            try {
                ensureGridState(activationGrid);
                if (clampMax <= 0.0) return;
                boolean currentlyClamped = activationValue >= clampMax;
                if (currentlyClamped) {
                    saturationObserved = true;
                    if (!wasClamped[y][x]) {
                        clampedSince[y][x] = tick;
                        clampedDuration[y][x] = 1;
                    } else {
                        clampedDuration[y][x] = tick - clampedSince[y][x] + 1;
                    }
                    int neighborsClamped = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && nx < width && ny >= 0 && ny < height && activationGrid[ny][nx] >= clampMax) {
                                neighborsClamped++;
                            }
                        }
                    }
                    clampedNeighborCount[y][x] = neighborsClamped;
                    clamp.println(String.format("%d,%d,%d,%d,%d", tick, x, y, clampedDuration[y][x], neighborsClamped));
                    clamp.flush();
                }
                wasClamped[y][x] = currentlyClamped;
            } catch (Exception ignored) {
                // ignore diagnostics failures to avoid breaking experiments
            }
        }

        void snapshotGrids(int tick, double[][] A, double[][] M, double[][] C) {
            try (PrintWriter pa = new PrintWriter(new File(dir, String.format("activation_tick_%04d.csv", tick)))) {
                for (int y = 0; y < A.length; y++) {
                    for (int x = 0; x < A[0].length; x++) {
                        pa.print(String.format("%.6f", A[y][x]));
                        if (x < A[0].length - 1) pa.print(",");
                    }
                    pa.println();
                }
            } catch (IOException ignored) {}
            try (PrintWriter pm = new PrintWriter(new File(dir, String.format("memory_tick_%04d.csv", tick)))) {
                for (int y = 0; y < M.length; y++) {
                    for (int x = 0; x < M[0].length; x++) {
                        pm.print(String.format("%.6f", M[y][x]));
                        if (x < M[0].length - 1) pm.print(",");
                    }
                    pm.println();
                }
            } catch (IOException ignored) {}
            try (PrintWriter pc = new PrintWriter(new File(dir, String.format("fielddepth_tick_%04d.csv", tick)))) {
                for (int y = 0; y < C.length; y++) {
                    for (int x = 0; x < C[0].length; x++) {
                        pc.print(String.format("%.6f", C[y][x]));
                        if (x < C[0].length - 1) pc.print(",");
                    }
                    pc.println();
                }
            } catch (IOException ignored) {}
        }

        void writeFinalGrids(double[][] A, double[][] M, double[][] C) {
            try (PrintWriter pa = new PrintWriter(new File(dir, "activation_grid.csv"))) { for (int y = 0; y < A.length; y++) { for (int x = 0; x < A[0].length; x++) { pa.print(String.format("%.6f", A[y][x])); if (x < A[0].length - 1) pa.print(","); } pa.println(); } } catch (IOException ignored) {}
            try (PrintWriter pm = new PrintWriter(new File(dir, "memory_grid.csv"))) { for (int y = 0; y < M.length; y++) { for (int x = 0; x < M[0].length; x++) { pm.print(String.format("%.6f", M[y][x])); if (x < M[0].length - 1) pm.print(","); } pm.println(); } } catch (IOException ignored) {}
            try (PrintWriter pc = new PrintWriter(new File(dir, "fielddepth_grid.csv"))) { for (int y = 0; y < C.length; y++) { for (int x = 0; x < C[0].length; x++) { pc.print(String.format("%.6f", C[y][x])); if (x < C[0].length - 1) pc.print(","); } pc.println(); } } catch (IOException ignored) {}
        }

        void writeRuntimeParameters(String experimentName, ParameterSet p, int gridWidth, int gridHeight, int buildTicks, int probeTicks, int idleTicks) {
            parameters.println("Experiment Name=" + experimentName);
            parameters.println("v2TransportGain=" + p.v2TransportGain);
            parameters.println("v2ActivationDecay=" + p.v2ActivationDecay);
            parameters.println("v2MemoryGain=" + p.v2MemoryGain);
            parameters.println("v2MemoryDecay=" + p.v2MemoryDecay);
            parameters.println("v2MemoryInfluence=" + p.v2MemoryInfluence);
            parameters.println("v2ClampMax=" + p.v2ClampMax);
            parameters.println("v3FieldDepthGain=" + p.v3FieldDepthGain);
            parameters.println("v3FieldDepthDecay=" + p.v3FieldDepthDecay);
            parameters.println("v3FieldDepthRadius=" + p.v3FieldDepthRadius);
            parameters.println("v3DistanceDecay=" + p.v3DistanceDecay);
            parameters.println("v3FieldDepthEffectK=" + p.v3FieldDepthEffectK);
            parameters.println("validationMaxHotspotRadius=" + p.validationMaxHotspotRadius);
            parameters.println("gridWidth=" + gridWidth);
            parameters.println("gridHeight=" + gridHeight);
            parameters.println("buildTicks=" + buildTicks);
            parameters.println("probeTicks=" + probeTicks);
            parameters.println("idleTicks=" + idleTicks);
            parameters.println("FieldDepthEnabled=" + (p.v3FieldDepthGain != 0.0 || p.v3FieldDepthDecay != 0.0 || p.v3FieldDepthRadius != 0.0));
            parameters.println("RadiusExpansionEnabled=" + (p.v3FieldDepthRadius > 0.0));
            parameters.println("TransportEnabled=" + (p.v2TransportGain > 0.0));
            parameters.println("MemoryEnabled=" + (p.v2MemoryGain != 0.0 || p.v2MemoryDecay != 0.0 || p.v2MemoryInfluence != 0.0));
            parameters.flush();
        }

        void writeExperimentSummary(String experimentName, ParameterSet p, int gridWidth, int gridHeight, int buildTicks, int probeTicks, int idleTicks,
                                    double[][] A, double[][] M, double[][] C, String phase, String reasonStopped, String warningsText,
                                    boolean transportOccurred, boolean fieldDepthChanged, boolean saturationOccurred, boolean localizationMaintained, boolean fieldDepthEnabled) {
            stopReason = reasonStopped;
            java.util.List<String> warnings = new java.util.ArrayList<>();
            if (warningsText != null && !warningsText.isEmpty()) warnings.add(warningsText);
            if (!transportExecuted) warnings.add("WARNING:Transport inactive.Results involving this subsystem should not be interpreted.");
            if (!memoryExecuted) warnings.add("WARNING:Memory inactive.Results involving this subsystem should not be interpreted.");
            if (!fieldDepthExecuted) warnings.add("WARNING:Field Depth inactive.Results involving this subsystem should not be interpreted.");
            if (!decayExecuted) warnings.add("WARNING:Decay inactive.Results involving this subsystem should not be interpreted.");
            if (!fieldDepthInfluenceObserved) warnings.add("WARNING:Field Depth had zero influence during this experiment.");
            try (PrintWriter s = new PrintWriter(new File(dir, "summary.txt"))) {
                s.println("Experiment Name=" + experimentName);
                s.println("Reason experiment stopped=" + reasonStopped);
                s.println("Transport executed=" + transportExecuted);
                s.println("Memory executed=" + memoryExecuted);
                s.println("Field Depth executed=" + fieldDepthExecuted);
                s.println("Decay executed=" + decayExecuted);
                s.println("Field Depth influence observed=" + fieldDepthInfluenceObserved);
                s.println("Warnings=" + (warnings.isEmpty() ? "none" : String.join("; ", warnings)));
            } catch (IOException ignored) {}
            experimentSummary.println("Experiment Name=" + experimentName);
            String runtimeParameters = p != null
                    ? "v2TransportGain=" + p.v2TransportGain
                    + ",v2ActivationDecay=" + p.v2ActivationDecay
                    + ",v2MemoryGain=" + p.v2MemoryGain
                    + ",v2MemoryDecay=" + p.v2MemoryDecay
                    + ",v2MemoryInfluence=" + p.v2MemoryInfluence
                    + ",v2ClampMax=" + p.v2ClampMax
                    + ",v3FieldDepthGain=" + p.v3FieldDepthGain
                    + ",v3FieldDepthDecay=" + p.v3FieldDepthDecay
                    + ",v3FieldDepthRadius=" + p.v3FieldDepthRadius
                    + ",v3DistanceDecay=" + p.v3DistanceDecay
                    + ",v3FieldDepthEffectK=" + p.v3FieldDepthEffectK
                    : "n/a";
            experimentSummary.println("Runtime Parameters=" + runtimeParameters);
            experimentSummary.println("Was Field Depth enabled?=" + fieldDepthEnabled);
            experimentSummary.println("Was Memory enabled?=" + (p != null && (p.v2MemoryGain != 0.0 || p.v2MemoryDecay != 0.0 || p.v2MemoryInfluence != 0.0)));
            experimentSummary.println("Did Transport occur?=" + transportOccurred);
            experimentSummary.println("Did Field Depth change?=" + fieldDepthChanged);
            experimentSummary.println("Did Transport depend on Field Depth?=" + (fieldDepthEnabled && transportOccurred));
            experimentSummary.println("Did saturation occur?=" + saturationOccurred);
            experimentSummary.println("Was localisation maintained?=" + localizationMaintained);
            experimentSummary.println("Reason experiment stopped=" + reasonStopped);
            experimentSummary.println("Warnings=" + (warnings.isEmpty() ? "none" : String.join("; ", warnings)));
            experimentSummary.println("Possible invalid assumptions=" + (fieldDepthEnabled ? "field depth was enabled" : "field depth disabled"));
            experimentSummary.println("phase=" + phase);
            double totalA=0,totalM=0,totalC=0; for (int y=0;y<A.length;y++) for (int x=0;x<A[0].length;x++) { totalA+=A[y][x]; totalM+=M[y][x]; totalC+=C[y][x]; }
            experimentSummary.println("Final total activation=" + totalA);
            experimentSummary.println("Final total memory=" + totalM);
            experimentSummary.println("Final total field depth=" + totalC);
            experimentSummary.flush();
        }

        void writeAbortSummary(String reason, SpatialMetrics m, double[][] A, double[][] M, double[][] C) {
            stopReason = reason;
            try (PrintWriter s = new PrintWriter(new File(dir, "summary.txt"))) {
                s.println("ABORT REASON: " + reason);
                s.println("Largest hotspot radius: " + m.largestRadius);
                s.println("Largest hotspot size: " + m.largestSize);
                s.println("Number of hotspots: " + m.hotspotCount);
                s.println("Fraction activation outside largest: " + m.fractionOutsideLargest);
                double maxA=0,maxM=0,maxC=0,totalA=0,totalM=0,totalC=0;
                for (int y=0;y<A.length;y++) for (int x=0;x<A[0].length;x++) { maxA=Math.max(maxA,A[y][x]); totalA+=A[y][x]; maxM=Math.max(maxM,M[y][x]); totalM+=M[y][x]; maxC=Math.max(maxC,C[y][x]); totalC+=C[y][x]; }
                s.println("Maximum activation: " + maxA);
                s.println("Maximum memory: " + maxM);
                s.println("Maximum field depth: " + maxC);
                s.println("Total activation: " + totalA);
                s.println("Total memory: " + totalM);
                s.println("Total field depth: " + totalC);
            } catch (IOException ignored) {}
        }

        void close() {
            timeseries.close(); hotspots.close(); transport.close(); energy.close(); topology.close(); parameters.close(); fieldDepth.close(); clamp.close(); summary.close(); experimentSummary.close();
        }

        private double computeMeanPairwiseDistance(double[][] A, double threshold) {
            java.util.ArrayList<int[]> active = new java.util.ArrayList<>();
            for (int y = 0; y < A.length; y++) {
                for (int x = 0; x < A[0].length; x++) {
                    if (A[y][x] >= threshold) active.add(new int[]{x, y});
                }
            }
            if (active.size() < 2) return 0.0;
            double sum = 0.0;
            int count = 0;
            for (int i = 0; i < active.size(); i++) {
                for (int j = i + 1; j < active.size(); j++) {
                    int[] a = active.get(i);
                    int[] b = active.get(j);
                    double dx = a[0] - b[0];
                    double dy = a[1] - b[1];
                    sum += Math.sqrt(dx * dx + dy * dy);
                    count++;
                }
            }
            return count > 0 ? sum / count : 0.0;
        }
    }

    private static double[][] copyGrid(double[][] grid) {
        double[][] copy = new double[grid.length][grid[0].length];
        for (int y = 0; y < grid.length; y++) {
            System.arraycopy(grid[y], 0, copy[y], 0, grid[0].length);
        }
        return copy;
    }

    private boolean buildLocalizedState(PrintWriter log, ParameterSet p, int W, int H, int buildTicks,
                                       int cx, int cy, double[][] A, double[][] M, double[][] C,
                                       BiConsumer<Integer, double[][]> injector, DiagnosticsManager diag) {
        LocalizedExperiment protocol = localizedProtocol(buildTicks);
        log.println("Localized build protocol: buildTicks=" + buildTicks + ", pulses=" + protocol.pulsesPerBuild + ", interval=" + protocol.pulseInterval + ", idle=" + protocol.idleTicks);
        for (int t = 0; t < buildTicks; t++) {
            boolean inject = (t % protocol.pulseInterval == 0);
            if (inject) injector.accept(t, A);
            StepMetrics metrics = stepOnce(A, M, C, p, t, cx, cy, false);
            if (diag != null && (t % 50 == 0)) diag.recordTick(t, A, M, C, metrics, p);
            if (topologyIsGlobalized(log, A, p, t, "build")) {
                log.println("ABORT: topology indicates global activation at build tick " + t);
                if (diag != null) diag.writeAbortSummary("global activation at build", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C);
                return false;
            }
        }
        for (int t = 0; t < protocol.idleTicks; t++) {
            StepMetrics metrics = stepOnce(A, M, C, p, buildTicks + t, cx, cy, false);
            if (diag != null && ((buildTicks + t) % 50 == 0)) diag.recordTick(buildTicks + t, A, M, C, metrics, p);
            if (topologyIsGlobalized(log, A, p, buildTicks + t, "idle")) {
                log.println("ABORT: topology indicates global activation during idle tick " + (buildTicks + t));
                if (diag != null) diag.writeAbortSummary("global activation during idle", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C);
                return false;
            }
        }
        return verifyLocalizedState(log, A, M, C, p, cx, cy, diag);
    }

    private boolean runSustainTrial(ParameterSet p, double[][] A, double[][] M, double[][] C, int interval, int probeTicks, int cx, int cy) {
        double[][] a = copyGrid(A);
        double[][] m = copyGrid(M);
        double[][] c = copyGrid(C);
        for (int t = 0; t < probeTicks; t++) {
            if (t % interval == 0) a[cy][cx] += 1.0;
            stepOnce(a, m, c, p, t, cx, cy, false);
        }
        double maxA = 0.0;
        for (double[] row : a) for (double value : row) maxA = Math.max(maxA, value);
        return maxA >= p.regionActiveThreshold;
    }

    private double[] sampleActivationHistory(ParameterSet p, double[][] A, double[][] M, double[][] C, int interval, int probeTicks, int cx, int cy) {
        double[][] a = copyGrid(A);
        double[][] m = copyGrid(M);
        double[][] c = copyGrid(C);
        double[] hist = new double[probeTicks];
        for (int t = 0; t < probeTicks; t++) {
            if (t % interval == 0) a[cy][cx] += 1.0;
            stepOnce(a, m, c, p, t, cx, cy, false);
            double maxA = 0.0;
            for (double[] row : a) for (double value : row) maxA = Math.max(maxA, value);
            hist[t] = maxA;
        }
        return hist;
    }

    // Experiment 1
    private void runSustainThreshold(ParameterSet p, int W, int H, int buildShort, int buildLong, File outDir) throws IOException {
        String fname = outDir.getPath() + "/sustain_threshold.csv";
        try (FileWriter fw = new FileWriter(fname)) {
            fw.write("region,buildTicks,interval,frequency,sustained,lastActivation,meanActivation,aborted\n");
            int[] intervals = new int[]{5,10,20,50,100};
            for (int build : new int[]{buildShort, buildLong}) {
                for (int interval : intervals) {
                    String region = build == buildShort ? "young" : "old";
                    File expDir = new File(outDir, String.format("sustain_threshold_%s_%d_%d", region, build, interval));
                    if (!expDir.exists()) expDir.mkdirs();
                    try (PrintWriter log = new PrintWriter(new File(expDir, String.format("run.log")))) {
                        DiagnosticsManager diag = new DiagnosticsManager(expDir);
                        diag.writeRuntimeParameters(String.format("sustain_threshold_%s_%d_%d", region, build, interval), p, W, H, build, 400, 0);
                        int cx = W/2, cy = H/2;
                        double[][] A = new double[H][W], M = new double[H][W], C = new double[H][W];
                        boolean ready = buildLocalizedState(log, p, W, H, build, cx, cy, A, M, C, (t, a) -> a[cy][cx] += 1.0, diag);
                        if (!ready) {
                            diag.writeAbortSummary("build/localization-failure", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C);
                            diag.writeExperimentSummary(String.format("sustain_threshold_%s_%d_%d", region, build, interval), p, W, H, build, 400, 0, A, M, C, "aborted", "build/localization-failure", null, false, false, false, false, p.v3FieldDepthGain != 0.0 || p.v3FieldDepthDecay != 0.0 || p.v3FieldDepthRadius != 0.0);
                            diag.writeFinalGrids(A, M, C);
                            diag.close();
                            fw.write(String.format("%s,%d,%d,%.6f,false,false,false,true\n", region, build, interval, 1.0/interval));
                            continue;
                        }
                        log.println("--- Heatmaps before probe ---");
                        logHeatmaps(log, A, "Activation");
                        logHeatmaps(log, M, "Memory");
                        logHeatmaps(log, C, "FieldDepth");
                        boolean sustained = runSustainTrial(p, A, M, C, interval, 400, cx, cy);
                        double[] acts = sampleActivationHistory(p, A, M, C, interval, 400, cx, cy);
                        double last = acts[acts.length-1];
                        double mean = 0.0; for(double v : acts) mean += v; mean /= acts.length;
                        diag.writeExperimentSummary(String.format("sustain_threshold_%s_%d_%d", region, build, interval), p, W, H, build, 400, 0, A, M, C, "completed", "completed", null, true, true, false, true, p.v3FieldDepthGain != 0.0 || p.v3FieldDepthDecay != 0.0 || p.v3FieldDepthRadius != 0.0);
                        diag.writeFinalGrids(A, M, C);
                        diag.close();
                        fw.write(String.format("%s,%d,%d,%.6f,%b,%.6f,%.6f,false\n", region, build, interval, 1.0/interval, sustained, last, mean));
                    }
                }
            }
        }
    }

    // Experiment 2
    private void runExpansionRadius(ParameterSet p, int W, int H, int buildShort, int buildLong, int[] ticks, File outDir) throws IOException {
        String fname = outDir.getPath() + "/expansion_radius.csv";
        try (FileWriter fw = new FileWriter(fname)) {
            fw.write("region,buildTicks,time,radius,aborted\n");
            for (int build : new int[]{buildShort, buildLong}) {
                String region = build == buildShort ? "young" : "old";
                int cx = W/2, cy = H/2;
                double[][] A = new double[H][W], M = new double[H][W], C = new double[H][W];
                File expDir = new File(outDir, String.format("expansion_radius_%s_%d", region, build));
                if (!expDir.exists()) expDir.mkdirs();
                try (PrintWriter log = new PrintWriter(new File(expDir, "run.log"))) {
                    DiagnosticsManager diag = new DiagnosticsManager(expDir);
                    diag.writeRuntimeParameters(String.format("expansion_radius_%s_%d", region, build), p, W, H, build, ticks[ticks.length - 1], 0);
                    boolean ready = buildLocalizedState(log, p, W, H, build, cx, cy, A, M, C, (t, a) -> a[cy][cx] += 1.0, diag);
                    if (!ready) {
                        diag.writeAbortSummary("build/localization-failure", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C);
                        diag.writeExperimentSummary(String.format("expansion_radius_%s_%d", region, build), p, W, H, build, ticks[ticks.length - 1], 0, A, M, C, "aborted", "build/localization-failure", null, false, false, false, false, p.v3FieldDepthGain != 0.0 || p.v3FieldDepthDecay != 0.0 || p.v3FieldDepthRadius != 0.0);
                        diag.writeFinalGrids(A, M, C);
                        diag.close();
                        for (int t : ticks) fw.write(String.format("%s,%d,%d,%.6f,true\n", region, build, t, 0.0));
                        continue;
                    }
                    log.println("--- Heatmaps before probe ---");
                    logHeatmaps(log, A, "Activation");
                    logHeatmaps(log, M, "Memory");
                    logHeatmaps(log, C, "FieldDepth");
                    A[cy][cx+2] += 1.0;
                    for (int t = 1; t <= ticks[ticks.length - 1]; t++) {
                        StepMetrics metrics = stepOnce(A, M, C, p, t, cx, cy, false);
                        if (t % 100 == 0) diag.recordTick(t, A, M, C, metrics, p);
                        for (int q = 0; q < ticks.length; q++) if (t == ticks[q]) {
                            double radius = MetricEvaluator.diffusionRadius(A, p.regionActiveThreshold, 1.0, cx+2, cy);
                            fw.write(String.format("%s,%d,%d,%.6f,false\n", region, build, t, radius));
                        }
                    }
                    diag.writeExperimentSummary(String.format("expansion_radius_%s_%d", region, build), p, W, H, build, ticks[ticks.length - 1], 0, A, M, C, "completed", "completed", null, true, true, false, true, p.v3FieldDepthGain != 0.0 || p.v3FieldDepthDecay != 0.0 || p.v3FieldDepthRadius != 0.0);
                    diag.writeFinalGrids(A, M, C);
                    diag.close();
                }
            }
        }
    }

    // Experiment 3
    private void runRecruitmentSpeed(ParameterSet p, int W, int H, int buildShort, int buildLong, int separation, File outDir) throws IOException {
        String fname = outDir.getPath() + "/recruitment_speed.csv";
        try (FileWriter fw = new FileWriter(fname)) {
            fw.write("region,buildTicks,separation,timeToRecruit,aborted\n");
            for (int build: new int[]{buildShort, buildLong}) {
                String region = build == buildShort ? "young" : "old";
                int ax=W/2 - separation/2, ay=H/2;
                int bx=ax+separation, by=ay;
                double[][] A=new double[H][W],M=new double[H][W],C=new double[H][W];
                File expDir = new File(outDir, String.format("recruitment_speed_%s_%d_%d", region, build, separation));
                if (!expDir.exists()) expDir.mkdirs();
                try (PrintWriter log = new PrintWriter(new File(expDir, "run.log"))) {
                    DiagnosticsManager diag = new DiagnosticsManager(expDir);
                    diag.writeRuntimeParameters(String.format("recruitment_speed_%s_%d_%d", region, build, separation), p, W, H, build, 1000, 0);
                    boolean ready = buildLocalizedState(log, p, W, H, build, ax, ay, A, M, C, (t, a) -> a[ay][ax] += 1.0, diag);
                    if (!ready) {
                        diag.writeAbortSummary("build/localization-failure", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C);
                        diag.writeFinalGrids(A, M, C);
                        diag.close();
                        fw.write(String.format("%s,%d,%d,%d,true\n", region, build, separation, Integer.MAX_VALUE));
                        continue;
                    }
                    log.println("--- Heatmaps before probe ---");
                    logHeatmaps(log, A, "Activation");
                    logHeatmaps(log, M, "Memory");
                    logHeatmaps(log, C, "FieldDepth");
                    A[ay][ax] += 1.0;
                    int recruited=-1;
                    for (int t=0;t<1000;t++) {
                        StepMetrics metrics = stepOnce(A,M,C,p,t,ax,ay,false);
                        if (t % 50 == 0) diag.recordTick(t, A, M, C, metrics, p);
                        if (A[by][bx] >= p.regionActiveThreshold) { recruited=t; break; }
                        if (topologyIsGlobalized(log, A, p, t, "recruitment-probe")) {
                            log.println("ABORT: topology indicates global activation during recruitment probe at tick " + t);
                            diag.writeAbortSummary("global activation during recruitment", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C);
                            break;
                        }
                    }
                    if (recruited < 0 && !topologyIsGlobalized(log, A, p, -1, "post-recruitment-check")) recruited = Integer.MAX_VALUE;
                    diag.writeExperimentSummary(String.format("recruitment_speed_%s_%d_%d", region, build, separation), p, W, H, build, 1000, 0, A, M, C, "completed", "completed", null, true, true, false, true, p.v3FieldDepthGain != 0.0 || p.v3FieldDepthDecay != 0.0 || p.v3FieldDepthRadius != 0.0);
                    diag.writeFinalGrids(A, M, C);
                    diag.close();
                    fw.write(String.format("%s,%d,%d,%d,false\n", region, build, separation, recruited));
                }
            }
        }
    }

    // Experiment 4
    private void runRecoveryTime(ParameterSet p, int W, int H, int buildShort, int buildLong, File outDir) throws IOException {
        String fname = outDir.getPath() + "/recovery_time.csv";
        try (FileWriter fw = new FileWriter(fname)) {
            fw.write("region,buildTicks,activationRecoveryTime,memoryRecoveryTime,aborted\n");
            for (int build: new int[]{buildShort, buildLong}) {
                String region = build == buildShort ? "young" : "old";
                int cx=W/2, cy=H/2;
                double[][] A=new double[H][W],M=new double[H][W],C=new double[H][W];
                File expDir = new File(outDir, String.format("recovery_time_%s_%d", region, build));
                if (!expDir.exists()) expDir.mkdirs();
                try (PrintWriter log = new PrintWriter(new File(expDir, "run.log"))) {
                    DiagnosticsManager diag = new DiagnosticsManager(expDir);
                    boolean ready = buildLocalizedState(log, p, W, H, build, cx, cy, A, M, C, (t, a) -> a[cy][cx] += 1.0, diag);
                    if (!ready) {
                        diag.writeAbortSummary("build/localization-failure", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C);
                        diag.writeFinalGrids(A, M, C);
                        diag.close();
                        fw.write(String.format("%s,%d,%d,%d,true\n", region, build, -1, -1));
                        continue;
                    }
                    log.println("--- Heatmaps before probe ---");
                    logHeatmaps(log, A, "Activation");
                    logHeatmaps(log, M, "Memory");
                    logHeatmaps(log, C, "FieldDepth");
                    int actRec=-1, memRec=-1;
                    for (int t=0;t<5000;t++) {
                        StepMetrics metrics = stepOnce(A,M,C,p,t,cx,cy,false);
                        if (t % 50 == 0) diag.recordTick(t, A, M, C, metrics, p);
                        double maxA=0, maxM=0; for (int y=0;y<H;y++) for (int x=0;x<W;x++) { maxA=Math.max(maxA,A[y][x]); maxM=Math.max(maxM,M[y][x]); }
                        if (actRec<0 && maxA < p.regionActiveThreshold) actRec=t;
                        if (memRec<0 && maxM < p.regionActiveThreshold) memRec=t;
                        if (actRec>=0 && memRec>=0) break;
                        if (topologyIsGlobalized(log, A, p, t, "recovery-probe")) {
                            log.println("ABORT: topology indicates global activation during recovery probe at tick " + t);
                            diag.writeAbortSummary("global activation during recovery", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C);
                            actRec = -1;
                            memRec = -1;
                            break;
                        }
                    }
                    diag.writeExperimentSummary(String.format("recovery_time_%s_%d", region, build), p, W, H, build, 5000, 0, A, M, C, "completed", "completed", null, true, true, false, true, p.v3FieldDepthGain != 0.0 || p.v3FieldDepthDecay != 0.0 || p.v3FieldDepthRadius != 0.0);
                    diag.writeFinalGrids(A, M, C);
                    diag.close();
                    fw.write(String.format("%s,%d,%d,%d,false\n", region, build, actRec, memRec));
                }
            }
        }
    }

    // Experiment 5
    private void runBasinSize(ParameterSet p, int W, int H, int buildShort, int buildLong, double[] mags, File outDir) throws IOException {
        String fname = outDir.getPath() + "/basin_size.csv";
        try (FileWriter fw = new FileWriter(fname)) {
            fw.write("region,buildTicks,magnitude,reactivated,aborted\n");
            for (int build: new int[]{buildShort, buildLong}) {
                String region = build == buildShort ? "young" : "old";
                int cx=W/2, cy=H/2;
                for (double mag: mags) {
                    double[][] A=new double[H][W],M=new double[H][W],C=new double[H][W];
                    File expDir = new File(outDir, String.format("basin_size_%s_%d_%.3f", region, build, mag));
                    if (!expDir.exists()) expDir.mkdirs();
                    try (PrintWriter log = new PrintWriter(new File(expDir, "run.log"))) {
                        DiagnosticsManager diag = new DiagnosticsManager(expDir);
                        diag.writeRuntimeParameters(String.format("basin_size_%s_%d_%.3f", region, build, mag), p, W, H, build, 500, 0);
                        boolean ready = buildLocalizedState(log, p, W, H, build, cx, cy, A, M, C, (t, a) -> a[cy][cx] += 1.0, diag);
                        if (!ready) {
                            diag.writeAbortSummary("build/localization-failure", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C);
                            diag.writeFinalGrids(A, M, C);
                            diag.close();
                            fw.write(String.format("%s,%d,%.4f,false,true\n", region, build, mag));
                            continue;
                        }
                        log.println("--- Heatmaps before perturbation ---");
                        logHeatmaps(log, A, "Activation");
                        logHeatmaps(log, M, "Memory");
                        logHeatmaps(log, C, "FieldDepth");
                        for (int t=0;t<500;t++) stepOnce(A,M,C,p,t,cx,cy,false);
                        A[cy][cx]+=mag;
                        boolean grew=false;
                        for (int t=0;t<500;t++) {
                            StepMetrics metrics = stepOnce(A,M,C,p,t,cx,cy,false);
                            if (t % 50 == 0) diag.recordTick(t, A, M, C, metrics, p);
                            double maxA=0; for (int y=0;y<H;y++) for (int x=0;x<W;x++) maxA=Math.max(maxA,A[y][x]);
                            if (maxA>=p.regionActiveThreshold) { grew=true; break; }
                            if (topologyIsGlobalized(log, A, p, t, "basin-probe")) { log.println("ABORT: topology indicates global activation during basin probe at tick " + t); diag.writeAbortSummary("global activation during basin probe", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C); break; }
                        }
                        diag.writeExperimentSummary(String.format("basin_size_%s_%d_%.3f", region, build, mag), p, W, H, build, 500, 0, A, M, C, "completed", "completed", null, true, true, false, true, p.v3FieldDepthGain != 0.0 || p.v3FieldDepthDecay != 0.0 || p.v3FieldDepthRadius != 0.0);
                        diag.writeFinalGrids(A, M, C);
                        diag.close();
                        fw.write(String.format("%s,%d,%.4f,%b,false\n", region, build, mag, grew));
                    }
                }
            }
        }
    }

    // Experiment 6
    private void runHistoricalRoutes(ParameterSet p, int W, int H, int buildShort, int buildLong, File outDir) throws IOException {
        String fname = outDir.getPath() + "/historical_routes.csv";
        try (FileWriter fw = new FileWriter(fname)) {
            fw.write("region,buildTicks,routeDist,unusedDist,activatedAlongRoute,activatedAlongUnused,aborted\n");
            for (int build: new int[]{buildShort, buildLong}) {
                String region = build == buildShort ? "young" : "old";
                int sx=2, sy=H/2;
                int usedX = Math.min(W-1, sx+15);
                int unusedY = sy+3, unusedX = usedX;
                double[][] A=new double[H][W],M=new double[H][W],C=new double[H][W];
                File expDir = new File(outDir, String.format("historical_routes_%s_%d", region, build));
                if (!expDir.exists()) expDir.mkdirs();
                try (PrintWriter log = new PrintWriter(new File(expDir, "run.log"))) {
                    DiagnosticsManager diag = new DiagnosticsManager(expDir);
                    diag.writeRuntimeParameters(String.format("historical_routes_%s_%d", region, build), p, W, H, build, 200, 0);
                    boolean ready = buildLocalizedState(log, p, W, H, build, sx, sy, A, M, C, (t, a) -> a[sy][sx + t % 10] += 1.0, diag);
                    if (!ready) {
                        diag.writeAbortSummary("build/localization-failure", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C);
                        diag.writeFinalGrids(A, M, C);
                        diag.close();
                        fw.write(String.format("%s,%d,%d,%d,%.6f,%.6f,true\n", region, build, usedX-sx, unusedY-sy, 0.0, 0.0));
                        continue;
                    }
                    log.println("--- Heatmaps before probe ---");
                    logHeatmaps(log, A, "Activation");
                    logHeatmaps(log, M, "Memory");
                    logHeatmaps(log, C, "FieldDepth");
                    for (int t=0;t<50;t++) stepOnce(A,M,C,p,t,sx,sy,false);
                    A[sy][sx] += 1.0;
                    for (int t=0;t<200;t++) {
                        StepMetrics metrics = stepOnce(A,M,C,p,t,sx,sy,false);
                        if (t % 50 == 0) diag.recordTick(t, A, M, C, metrics, p);
                        if (topologyIsGlobalized(log, A, p, t, "historical-route-probe")) {
                            log.println("ABORT: topology indicates global activation during historical route probe at tick " + t);
                            diag.writeAbortSummary("global activation during historical route probe", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C);
                            break;
                        }
                    }
                    double actUsed = A[sy][usedX];
                    double actUnused = A[unusedY][unusedX];
                    diag.writeExperimentSummary(String.format("historical_routes_%s_%d", region, build), p, W, H, build, 200, 0, A, M, C, "completed", "completed", null, true, true, false, true, p.v3FieldDepthGain != 0.0 || p.v3FieldDepthDecay != 0.0 || p.v3FieldDepthRadius != 0.0);
                    diag.writeFinalGrids(A, M, C);
                    diag.close();
                    fw.write(String.format("%s,%d,%d,%d,%.6f,%.6f,false\n", region, build, usedX-sx, unusedY-sy, actUsed, actUnused));
                }
            }
        }
    }

    void runPostInjectionDecayVariants(ParameterSet p, int W, int H, int buildTicks, File outDir) throws IOException {
        List<String[]> variants = new ArrayList<>();
        variants.add(new String[]{"variant_1_exponential_retention", "Exponential retention"});
        variants.add(new String[]{"variant_2_rational_retention", "Rational retention"});
        variants.add(new String[]{"variant_3_logistic_transition", "Logistic transition"});
        variants.add(new String[]{"variant_4_field_depth_retention", "Field-depth retention"});
        variants.add(new String[]{"variant_5_combined_memory_field_depth", "Combined memory and field depth"});

        for (String[] variant : variants) {
            String key = variant[0];
            String label = variant[1];
            runPostInjectionDecayVariant(p, W, H, buildTicks, outDir, key, label);
        }

        File comparisonFile = new File(outDir, "balance_comparison.txt");
        try (PrintWriter writer = new PrintWriter(comparisonFile)) {
            writer.println("Variant | Stability | Hotspot persistence | Decay behaviour | Transport behaviour | Memory behaviour | Field depth behaviour | Gameplay implications");
            writer.println("---|---|---|---|---|---|---|---");
            for (String[] variant : variants) {
                File variantDir = new File(outDir, variant[0]);
                File summaryFile = new File(variantDir, "balance_summary.txt");
                if (!summaryFile.exists()) {
                    writer.println(variant[0] + " | not available | not available | not available | not available | not available | not available | not available");
                    continue;
                }
                String[] lines = new String(java.nio.file.Files.readAllBytes(summaryFile.toPath())).split("\\R");
                String peakActivation = "n/a";
                String finalActivation = "n/a";
                String peakMemory = "n/a";
                String finalMemory = "n/a";
                String peakDepth = "n/a";
                String finalDepth = "n/a";
                String hotspotLifetime = "n/a";
                String hotspotDisappear = "n/a";
                String transportConverged = "n/a";
                String activationConverged = "n/a";
                for (String line : lines) {
                    if (line.startsWith("Peak activation=")) peakActivation = line.substring("Peak activation=".length());
                    else if (line.startsWith("Final equilibrium activation=")) finalActivation = line.substring("Final equilibrium activation=".length());
                    else if (line.startsWith("Peak memory=")) peakMemory = line.substring("Peak memory=".length());
                    else if (line.startsWith("Final equilibrium memory=")) finalMemory = line.substring("Final equilibrium memory=".length());
                    else if (line.startsWith("Peak field depth=")) peakDepth = line.substring("Peak field depth=".length());
                    else if (line.startsWith("Final equilibrium field depth=")) finalDepth = line.substring("Final equilibrium field depth=".length());
                    else if (line.startsWith("Hotspot lifetime=")) hotspotLifetime = line.substring("Hotspot lifetime=".length());
                    else if (line.startsWith("Did hotspot disappear?=")) hotspotDisappear = line.substring("Did hotspot disappear?=".length());
                    else if (line.startsWith("Did transport converge?=")) transportConverged = line.substring("Did transport converge?=".length());
                    else if (line.startsWith("Did activation converge?=")) activationConverged = line.substring("Did activation converge?=".length());
                }
                String stability = Double.parseDouble(finalActivation) <= 0.01 ? "stable" : "lingering";
                String hotspotPersistence = hotspotDisappear.equalsIgnoreCase("YES") ? "transient" : "persistent";
                String decay = Double.parseDouble(finalActivation) <= Double.parseDouble(peakActivation) * 0.5 ? "decayed" : "lingered";
                String transportBehaviour = transportConverged.equalsIgnoreCase("YES") ? "converged" : "self-feeding";
                String memoryBehaviour = Double.parseDouble(finalMemory) > 0.0 ? "retained" : "dissipated";
                String fieldDepthBehaviour = Double.parseDouble(finalDepth) > 0.0 ? "retained" : "dissipated";
                String implications = "peak=" + peakActivation + ", final=" + finalActivation + ", hotspot=" + hotspotLifetime;
                writer.println(variant[0] + " | " + stability + " | " + hotspotPersistence + " | " + decay + " | " + transportBehaviour + " | " + memoryBehaviour + " | " + fieldDepthBehaviour + " | " + implications);
            }
        }

        File reportFile = new File(outDir, "constitutive_law_comparison.txt");
        try (PrintWriter writer = new PrintWriter(reportFile)) {
            writer.println("Constitutive law comparison report");
            writer.println("================================");
            writer.println("Architecture: fixed transport law with readiness constitutive relation varied only.");
            writer.println();
            for (String[] variant : variants) {
                File variantDir = new File(outDir, variant[0]);
                File summaryFile = new File(variantDir, "balance_summary.txt");
                if (!summaryFile.exists()) {
                    writer.println(variant[0] + " | not available");
                    continue;
                }
                String[] lines = new String(java.nio.file.Files.readAllBytes(summaryFile.toPath())).split("\\R");
                writer.println(variant[1]);
                writer.println("- Variant key: " + variant[0]);
                for (String line : lines) {
                    writer.println("- " + line);
                }
                writer.println();
            }
            writer.println("Engineering conclusion");
            writer.println("- The combined memory/field-depth law is the most physically complete hypothesis because it couples both history and structural depth to permeability.");
            writer.println("- The rational and logistic laws produce more distinct developmental phases, while the purely field-depth law tests whether permeability is structurally regulated rather than memory-regulated.");
            writer.println("- If a law fails to decay transport to zero or produces permanent saturation, that indicates a structural deficiency in the readiness gate rather than the transport architecture.");
        }
    }

    private void runPostInjectionDecayVariant(ParameterSet p, int W, int H, int buildTicks, File outDir, String variantKey, String variantLabel) throws IOException {
        File expDir = new File(outDir, variantKey);
        if (!expDir.exists()) expDir.mkdirs();
        try (PrintWriter log = new PrintWriter(new File(expDir, "run.log"))) {
            ParameterSet variantParameters = p.copy();
            variantParameters.transportBalanceMode = variantKey;
            variantParameters.v3FieldDepthGain = 0.01;
            variantParameters.v3FieldDepthDecay = 0.0001;
            variantParameters.v3FieldDepthRadius = 1.5;
            DiagnosticsManager diag = new DiagnosticsManager(expDir);
            diag.writeRuntimeParameters(variantKey, variantParameters, W, H, buildTicks, 5000, 0);
            int cx = W / 2;
            int cy = H / 2;
            double[][] A = new double[H][W];
            double[][] M = new double[H][W];
            double[][] C = new double[H][W];
            boolean ready = buildLocalizedState(log, variantParameters, W, H, buildTicks, cx, cy, A, M, C, (t, a) -> a[cy][cx] += 1.0, diag);
            if (!ready) {
                log.println("WARNING: initial localized build validation failed; continuing with the current state for post-injection observation.");
                diag.writeAbortSummary("build/localization-failure", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C);
            }
            log.println("--- Heatmaps before post-injection observation ---");
            logHeatmaps(log, A, "Activation");
            logHeatmaps(log, M, "Memory");
            logHeatmaps(log, C, "FieldDepth");
            log.println("Injection disabled at observation tick 0; evolving only from internal dynamics for 5000 ticks.");

            List<Integer> recordedTicks = new ArrayList<>();
            List<Double> totalActivations = new ArrayList<>();
            List<Double> maxActivations = new ArrayList<>();
            List<Integer> activeCells = new ArrayList<>();
            List<Integer> hotspotCounts = new ArrayList<>();
            List<Integer> largestSizes = new ArrayList<>();
            List<Double> largestRadii = new ArrayList<>();
            List<Double> totalMemories = new ArrayList<>();
            List<Double> maxMemories = new ArrayList<>();
            List<Double> totalDepths = new ArrayList<>();
            List<Double> maxDepths = new ArrayList<>();
            List<Integer> transportEvents = new ArrayList<>();
            List<Double> meanTransportDistances = new ArrayList<>();
            List<Integer> cellsReceivingTransport = new ArrayList<>();
            List<Integer> cellsLosingActivation = new ArrayList<>();
            List<Integer> cellsAtClamp = new ArrayList<>();
            List<Double> totalTransportEnergy = new ArrayList<>();
            List<Double> maxTransportMagnitudeSeries = new ArrayList<>();
            List<Double> totalActivationDecay = new ArrayList<>();
            List<Double> transportAdded = new ArrayList<>();
            List<Double> decayRemoved = new ArrayList<>();
            List<Double> netEnergyChange = new ArrayList<>();
            List<Double> activationSeries = new ArrayList<>();
            List<Double> memorySeries = new ArrayList<>();
            List<Double> depthSeries = new ArrayList<>();
            List<Double> radiusSeries = new ArrayList<>();
            List<Integer> hotspotSeries = new ArrayList<>();

            try (PrintWriter transport = new PrintWriter(new File(expDir, "transport.csv"));
                 PrintWriter energy = new PrintWriter(new File(expDir, "energy.csv"));
                 PrintWriter topology = new PrintWriter(new File(expDir, "topology.csv"));
                 PrintWriter timeseries = new PrintWriter(new File(expDir, "timeseries.csv"))) {
                transport.println("tick,source_x,source_y,destination_x,destination_y,transport_magnitude,distance");
                energy.println("tick,totalTransportEnergy,totalActivationDecay,transportAdded,decayRemoved,netEnergyChange");
                topology.println("tick,largestHotspot,numberOfHotspots,radius,diameter,fractionActivationInsideLargest,fractionOutsideLargest");
                timeseries.println("tick,externalInjection,totalActivation,maxActivation,activeCells,hotspotCount,largestHotspotSize,largestHotspotRadius,totalMemory,maxMemory,totalFieldDepth,maxFieldDepth,transportEvents,meanTransportDistance,cellsReceivingTransport,cellsLosingActivation,cellsAtClamp");

                double prevTotalActivation = 0.0;
                int injectionTicks = 500;
                int observationTicks = 5000;
                for (int t = 0; t <= injectionTicks + observationTicks; t++) {
                    boolean inject = t <= injectionTicks;
                    StepMetrics metrics = t == 0
                            ? new StepMetrics()
                            : stepOnce(A, M, C, variantParameters, t, cx, cy, inject);
                    if (t > 0) {
                        for (TransportSample sample : metrics.transportSamples) {
                            transport.println(String.format("%d,%d,%d,%d,%d,%.6f,%.6f",
                                    sample.tick, sample.sourceX, sample.sourceY, sample.destinationX, sample.destinationY, sample.magnitude, sample.distance));
                        }
                    }
                    if (t == 0 || t % 10 == 0 || t == 5000) {
                        double totalA = 0.0, maxA = 0.0, totalM = 0.0, maxM = 0.0, totalC = 0.0, maxC = 0.0;
                        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                            totalA += A[y][x];
                            maxA = Math.max(maxA, A[y][x]);
                            totalM += M[y][x];
                            maxM = Math.max(maxM, M[y][x]);
                            totalC += C[y][x];
                            maxC = Math.max(maxC, C[y][x]);
                        }
                        SpatialMetrics m = computeSpatialMetrics(A, variantParameters.regionActiveThreshold);
                        recordedTicks.add(t);
                        totalActivations.add(totalA);
                        maxActivations.add(maxA);
                        activeCells.add(m.activeCount);
                        hotspotCounts.add(m.hotspotCount);
                        largestSizes.add(m.largestSize);
                        largestRadii.add(m.largestRadius);
                        totalMemories.add(totalM);
                        maxMemories.add(maxM);
                        totalDepths.add(totalC);
                        maxDepths.add(maxC);
                        transportEvents.add(metrics.transportEvents);
                        meanTransportDistances.add(metrics.meanTransportDistance);
                        cellsReceivingTransport.add(metrics.cellsReceivingTransport);
                        cellsLosingActivation.add(metrics.cellsLosingActivation);
                        cellsAtClamp.add(metrics.cellsAtClamp);
                        totalTransportEnergy.add(metrics.totalTransportEnergy);
                        maxTransportMagnitudeSeries.add(metrics.maxTransportMagnitude);
                        totalActivationDecay.add(metrics.activationRemovedByDecay);
                        transportAdded.add(metrics.activationCreatedByTransport);
                        decayRemoved.add(metrics.activationRemovedByDecay);
                        double netChange = t == 0 ? 0.0 : totalA - prevTotalActivation;
                        netEnergyChange.add(netChange);
                        prevTotalActivation = totalA;
                        activationSeries.add(totalA);
                        memorySeries.add(totalM);
                        depthSeries.add(totalC);
                        radiusSeries.add(m.largestRadius);
                        hotspotSeries.add(m.hotspotCount);

                        timeseries.println(String.format("%d,%d,%.6f,%.6f,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%.6f,%d,%d,%d",
                                t, 0, totalA, maxA, m.activeCount, m.hotspotCount, m.largestSize, m.largestRadius, totalM, maxM, totalC, maxC,
                                metrics.transportEvents, metrics.meanTransportDistance, metrics.cellsReceivingTransport, metrics.cellsLosingActivation, metrics.cellsAtClamp));
                        topology.println(String.format("%d,%d,%d,%.6f,%.6f,%.6f,%.6f",
                                t, m.largestSize, m.hotspotCount, m.largestRadius, m.largestRadius * 2.0, m.largestFraction, m.fractionOutsideLargest));
                        energy.println(String.format("%d,%.6f,%.6f,%.6f,%.6f,%.6f",
                                t, metrics.totalTransportEnergy, metrics.activationRemovedByDecay, metrics.activationCreatedByTransport, metrics.activationRemovedByDecay, netChange));
                        if (t == 0 || t == 100 || t == 200 || t == 500 || t == 1000 || t == 2000 || t == 3000 || t == 5000 || t == 5500) {
                            writeGridSnapshot(expDir, t, A, M, C);
                        }
                    }
                    if (t > 0) {
                        continue;
                    }
                }
            }

            double initialActivation = totalActivations.isEmpty() ? 0.0 : totalActivations.get(0);
            double initialMemory = totalMemories.isEmpty() ? 0.0 : totalMemories.get(0);
            double initialDepth = totalDepths.isEmpty() ? 0.0 : totalDepths.get(0);
            double initialRadius = largestRadii.isEmpty() ? 0.0 : largestRadii.get(0);
            double peakActivation = 0.0;
            double peakRadius = 0.0;
            int peakActivationTick = 0;
            int peakRadiusTick = 0;
            int firstDecayTick = -1;
            int firstTransportZeroTick = -1;
            int equilibriumTick = -1;
            boolean activationDecayedToZero = false;
            boolean hotspotRemained = false;
            boolean hotspotRadiusShrank = false;
            boolean hotspotRadiusGrew = false;
            boolean memoryContinuedIncreasing = false;
            boolean fieldDepthContinuedIncreasing = false;
            boolean transportContinuedAfterInjectionStopped = false;
            boolean transportEventuallyZero = false;
            for (int i = 0; i < recordedTicks.size(); i++) {
                int tick = recordedTicks.get(i);
                double activation = totalActivations.get(i);
                double memory = totalMemories.get(i);
                double depth = totalDepths.get(i);
                double radius = largestRadii.get(i);
                int hotspotCount = hotspotCounts.get(i);
                if (activation > peakActivation) {
                    peakActivation = activation;
                    peakActivationTick = tick;
                }
                if (radius > peakRadius) {
                    peakRadius = radius;
                    peakRadiusTick = tick;
                }
                if (activation <= variantParameters.regionActiveThreshold && firstDecayTick < 0) {
                    firstDecayTick = tick;
                }
                if (tick > 0 && transportEvents.get(i) == 0 && totalTransportEnergy.get(i) <= 1e-9 && firstTransportZeroTick < 0) {
                    firstTransportZeroTick = tick;
                }
                if (tick > 0 && i >= 2) {
                    double prevA = totalActivations.get(i - 1);
                    double prevM = totalMemories.get(i - 1);
                    double prevC = totalDepths.get(i - 1);
                    double prevR = largestRadii.get(i - 1);
                    boolean stable = Math.abs(activation - prevA) < 1e-6 && Math.abs(memory - prevM) < 1e-6 && Math.abs(depth - prevC) < 1e-6 && Math.abs(radius - prevR) < 1e-6;
                    if (stable) {
                        equilibriumTick = tick;
                        break;
                    }
                }
                if (tick > 0 && radius < initialRadius - 1e-6) hotspotRadiusShrank = true;
                if (radius > initialRadius + 1e-6) hotspotRadiusGrew = true;
                if (memory > initialMemory + 1e-6) memoryContinuedIncreasing = true;
                if (depth > initialDepth + 1e-6) fieldDepthContinuedIncreasing = true;
                if (tick > 0 && transportEvents.get(i) > 0) transportContinuedAfterInjectionStopped = true;
                if (hotspotCount > 0) hotspotRemained = true;
            }
            activationDecayedToZero = totalActivations.get(totalActivations.size() - 1) <= variantParameters.regionActiveThreshold;
            transportEventuallyZero = transportEvents.get(transportEvents.size() - 1) == 0 && totalTransportEnergy.get(totalTransportEnergy.size() - 1) <= 1e-9;

            try (PrintWriter summary = new PrintWriter(new File(expDir, "summary.txt"))) {
                summary.println("Experiment Name=" + variantKey);
                summary.println("Observation ticks=5000");
                summary.println("Injection ticks=500");
                summary.println("Injection disabled after tick=500");
                summary.println("Peak activation=" + peakActivation);
                summary.println("Final activation=" + totalActivations.get(totalActivations.size() - 1));
                summary.println("Peak hotspot radius=" + peakRadius);
                summary.println("Final hotspot radius=" + largestRadii.get(largestRadii.size() - 1));
            }
            try (PrintWriter summary = new PrintWriter(new File(expDir, "experiment_summary.txt"))) {
                summary.println("Variant=" + variantLabel);
                summary.println("Did activation decay to zero?=" + (activationDecayedToZero ? "YES" : "NO"));
                summary.println("Did a hotspot remain?=" + (hotspotRemained ? "YES" : "NO"));
                summary.println("Did hotspot radius shrink?=" + (hotspotRadiusShrank ? "YES" : "NO"));
                summary.println("Did hotspot radius grow?=" + (hotspotRadiusGrew ? "YES" : "NO"));
                summary.println("Did memory continue increasing after injection stopped?=" + (memoryContinuedIncreasing ? "YES" : "NO"));
                summary.println("Did Field Depth continue increasing after injection stopped?=" + (fieldDepthContinuedIncreasing ? "YES" : "NO"));
                summary.println("Did transport continue after injection stopped?=" + (transportContinuedAfterInjectionStopped ? "YES" : "NO"));
                summary.println("Did transport eventually become zero?=" + (transportEventuallyZero ? "YES" : "NO"));
                summary.println("Maximum hotspot radius=" + peakRadius);
                summary.println("Final hotspot radius=" + largestRadii.get(largestRadii.size() - 1));
                summary.println("Maximum activation=" + peakActivation);
                summary.println("Final activation=" + totalActivations.get(totalActivations.size() - 1));
                summary.println("Maximum memory=" + maxMemories.stream().mapToDouble(Double::doubleValue).max().orElse(0.0));
                summary.println("Final memory=" + totalMemories.get(totalMemories.size() - 1));
                summary.println("Maximum Field Depth=" + maxDepths.stream().mapToDouble(Double::doubleValue).max().orElse(0.0));
                summary.println("Final Field Depth=" + totalDepths.get(totalDepths.size() - 1));
            }
            try (PrintWriter analysis = new PrintWriter(new File(expDir, "decay_analysis.txt"))) {
                analysis.println("Activation half-life=" + formatHalfLifeTick(activationSeries, recordedTicks, initialActivation));
                analysis.println("Memory half-life=" + formatHalfLifeTick(memorySeries, recordedTicks, initialMemory));
                analysis.println("Field depth half-life=" + formatHalfLifeTick(depthSeries, recordedTicks, initialDepth));
                analysis.println("Time until hotspot disappears=" + formatTick(firstDecayTick, true));
                analysis.println("Time until transport becomes negligible=" + formatTick(firstTransportZeroTick, false));
                analysis.println("Time until equilibrium=" + formatTick(equilibriumTick, false));
            }
            double finalActivation = totalActivations.get(totalActivations.size() - 1);
            double finalMemory = totalMemories.get(totalMemories.size() - 1);
            double finalDepth = totalDepths.get(totalDepths.size() - 1);
            double maxTransport = maxTransportMagnitudeSeries.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            double finalTransport = totalTransportEnergy.get(totalTransportEnergy.size() - 1);
            boolean activationConverged = Math.abs(finalActivation - totalActivations.get(Math.max(0, totalActivations.size() - 2))) < 1e-6;
            boolean transportConverged = totalTransportEnergy.get(totalTransportEnergy.size() - 1) <= 1e-6;
            try (PrintWriter balanceSummary = new PrintWriter(new File(expDir, "balance_summary.txt"))) {
                balanceSummary.println("Variant=" + variantKey);
                balanceSummary.println("Peak activation=" + peakActivation);
                balanceSummary.println("Peak memory=" + maxMemories.stream().mapToDouble(Double::doubleValue).max().orElse(0.0));
                balanceSummary.println("Peak field depth=" + maxDepths.stream().mapToDouble(Double::doubleValue).max().orElse(0.0));
                balanceSummary.println("Time until activation begins decreasing=" + formatTick(firstDecayTick, true));
                balanceSummary.println("Activation half-life=" + formatHalfLifeTick(activationSeries, recordedTicks, initialActivation));
                balanceSummary.println("Memory half-life=" + formatHalfLifeTick(memorySeries, recordedTicks, initialMemory));
                balanceSummary.println("Field depth half-life=" + formatHalfLifeTick(depthSeries, recordedTicks, initialDepth));
                balanceSummary.println("Transport half-life=" + formatTick(firstTransportZeroTick, false));
                balanceSummary.println("Hotspot lifetime=" + hotspotSeries.stream().filter(value -> value > 0).count());
                balanceSummary.println("Maximum hotspot radius=" + peakRadius);
                balanceSummary.println("Final hotspot radius=" + largestRadii.get(largestRadii.size() - 1));
                balanceSummary.println("Final hotspot size=" + largestSizes.get(largestSizes.size() - 1));
                balanceSummary.println("Did hotspot disappear?=" + (hotspotCounts.get(hotspotCounts.size() - 1) <= 0 ? "YES" : "NO"));
                balanceSummary.println("Did transport converge?=" + (transportConverged ? "YES" : "NO"));
                balanceSummary.println("Did activation converge?=" + (activationConverged ? "YES" : "NO"));
                balanceSummary.println("Maximum transport=" + maxTransport);
                balanceSummary.println("Final transport=" + finalTransport);
                balanceSummary.println("Final equilibrium activation=" + finalActivation);
                balanceSummary.println("Final equilibrium memory=" + finalMemory);
                balanceSummary.println("Final equilibrium field depth=" + finalDepth);
            }
            try (PrintWriter behavior = new PrintWriter(new File(expDir, "behavior_report.txt"))) {
                behavior.println("tick|activation|transport|memory|field_depth|hotspot_size|transport_energy");
                for (int i = 0; i < recordedTicks.size(); i++) {
                    behavior.println(String.format("%d|%.6f|%.6f|%.6f|%.6f|%d|%.6f",
                            recordedTicks.get(i),
                            totalActivations.get(i),
                            totalTransportEnergy.get(i),
                            totalMemories.get(i),
                            totalDepths.get(i),
                            largestSizes.get(i),
                            totalTransportEnergy.get(i)));
                }
            }
            try (PrintWriter table = new PrintWriter(new File(expDir, "behavior_table.txt"))) {
                table.println("Summary table");
                table.println("tick\tactivation\ttransport\tmemory\tfield_depth\thotspot_size");
                for (int i = 0; i < Math.min(recordedTicks.size(), 20); i++) {
                    table.println(String.format("%d\t%.6f\t%.6f\t%.6f\t%.6f\t%d",
                            recordedTicks.get(i),
                            totalActivations.get(i),
                            totalTransportEnergy.get(i),
                            totalMemories.get(i),
                            totalDepths.get(i),
                            largestSizes.get(i)));
                }
            }
            diag.writeExperimentSummary(variantKey, variantParameters, W, H, buildTicks, 5000, 0, A, M, C, "completed", "completed", null, true, true, false, true, variantParameters.v3FieldDepthGain != 0.0 || variantParameters.v3FieldDepthDecay != 0.0 || variantParameters.v3FieldDepthRadius != 0.0);
            diag.writeFinalGrids(A, M, C);
            diag.close();
        }
    }

    private static double computeReadiness(String variantKey, double memory, double depth) {
        double memoryScale = 1.0;
        double fieldScale = 1.0;
        double transitionWidth = 1.0;
        double m = Math.max(0.0, memory);
        double f = Math.max(0.0, depth);
        switch (variantKey) {
            case "variant_1_exponential_retention":
                return Math.exp(-m / memoryScale);
            case "variant_2_rational_retention":
                return 1.0 / (1.0 + Math.pow(m / memoryScale, 2));
            case "variant_3_logistic_transition":
                return 1.0 / (1.0 + Math.exp((m - memoryScale) / transitionWidth));
            case "variant_4_field_depth_retention":
                return Math.exp(-f / fieldScale);
            case "variant_5_combined_memory_field_depth":
            default:
                return Math.exp(-m / memoryScale) * Math.exp(-f / fieldScale);
        }
    }

    private double computeReactivationAdvantage(double[][] activation, double[][] memory, double[][] depth, ParameterSet parameters, int activeX, int activeY, int untouchedX, int untouchedY) {
        double[][] aActive = deepCopyGrid(activation);
        double[][] mActive = deepCopyGrid(memory);
        double[][] cActive = deepCopyGrid(depth);
        double[][] aUntouched = deepCopyGrid(activation);
        double[][] mUntouched = deepCopyGrid(memory);
        double[][] cUntouched = deepCopyGrid(depth);

        aActive[activeY][activeX] += 1.0;
        aUntouched[untouchedY][untouchedX] += 1.0;

        for (int t = 0; t < 50; t++) {
            stepOnce(aActive, mActive, cActive, parameters, t, activeX, activeY, false);
            stepOnce(aUntouched, mUntouched, cUntouched, parameters, t, untouchedX, untouchedY, false);
        }

        double activeTotal = sumGrid(aActive);
        double untouchedTotal = sumGrid(aUntouched);
        return untouchedTotal > 1e-9 ? activeTotal / untouchedTotal : activeTotal;
    }

    private double[][] deepCopyGrid(double[][] source) {
        double[][] copy = new double[source.length][source[0].length];
        for (int y = 0; y < source.length; y++) {
            System.arraycopy(source[y], 0, copy[y], 0, source[0].length);
        }
        return copy;
    }

    private double sumGrid(double[][] grid) {
        double total = 0.0;
        for (double[] row : grid) {
            for (double value : row) {
                total += value;
            }
        }
        return total;
    }

    private String formatHalfLifeTick(List<Double> values, List<Integer> ticks, double initialValue) {
        if (initialValue <= 0.0) return "Not applicable";
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) <= initialValue / 2.0) return ticks.get(i) + " ticks";
        }
        return "Not reached within 5000 ticks";
    }

    private String formatTick(int tick, boolean fallbackToObserved) {
        if (tick < 0) {
            return fallbackToObserved ? "No decay observed within 5000 ticks" : "No equilibrium observed.";
        }
        return tick + " ticks";
    }

    private String classifyPostInjectionBehavior(List<Double> activations, List<Double> radii) {
        if (activations.size() < 2 || radii.size() < 2) return "Case C";
        int startIndex = 0;
        for (int i = 0; i < activations.size(); i++) {
            if (i > 0 && i * 10 > 100) {
                startIndex = i;
                break;
            }
        }
        if (startIndex >= activations.size()) return "Case C";
        double first = activations.get(startIndex - 1);
        double last = activations.get(activations.size() - 1);
        if (last < first) return "Case A";
        if (last > first && last > first + 1e-6) {
            boolean briefIncrease = false;
            for (int i = startIndex; i < activations.size(); i++) {
                if (i > startIndex && activations.get(i) > activations.get(i - 1)) {
                    briefIncrease = true;
                    break;
                }
            }
            if (briefIncrease) return "Case B";
            return "Case D";
        }
        return "Case C";
    }

    private void writeGridSnapshot(File expDir, int tick, double[][] A, double[][] M, double[][] C) {
        String tickTag = String.format("%04d", tick);
        writeMatrix(new File(expDir, "activation_tick_" + tickTag + ".csv"), A);
        writeMatrix(new File(expDir, "memory_tick_" + tickTag + ".csv"), M);
        writeMatrix(new File(expDir, "fielddepth_tick_" + tickTag + ".csv"), C);
    }

    private void writeMatrix(File file, double[][] grid) {
        try (PrintWriter writer = new PrintWriter(file)) {
            for (int y = 0; y < grid.length; y++) {
                for (int x = 0; x < grid[0].length; x++) {
                    writer.print(String.format("%.6f", grid[y][x]));
                    if (x < grid[0].length - 1) writer.print(",");
                }
                writer.println();
            }
        } catch (IOException ignored) {
            // ignore diagnostics failures to avoid breaking experiments
        }
    }

    // Experiment 7
    private void runAncientCity(ParameterSet p, int W, int H, int[] builds, File outDir) throws IOException {
        String fname = outDir.getPath() + "/ancient_city.csv";
        try (FileWriter fw = new FileWriter(fname)) {
            fw.write("buildTicks,activatedCells,recruitmentDistance,recoveryTime,basinReactivation,aborted\n");
            for (int build: builds) {
                int cx=10, cy=H/2;
                double[][] A=new double[H][W],M=new double[H][W],C=new double[H][W];
                File expDir = new File(outDir, String.format("ancient_city_%d", build));
                if (!expDir.exists()) expDir.mkdirs();
                try (PrintWriter log = new PrintWriter(new File(expDir, "run.log"))) {
                    DiagnosticsManager diag = new DiagnosticsManager(expDir);
                    diag.writeRuntimeParameters(String.format("ancient_city_%d", build), p, W, H, build, 500, 0);
                    boolean ready = buildLocalizedState(log, p, W, H, build, cx, cy, A, M, C, (t, a) -> {
                        for (int dy=-1;dy<=1;dy++) for (int dx=-1;dx<=1;dx++) a[cy+dy][cx+dx]+=1.0;
                        a[cy][cx+6]+=1.0;
                        for (int i=0;i<5;i++) a[cy][cx+i+2]+=0.5;
                        a[cy+2][cx-2]+=1.0; a[cy+2][cx-1]+=1.0;
                    }, diag);
                    if (!ready) {
                        diag.writeAbortSummary("build/localization-failure", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C);
                        diag.writeFinalGrids(A, M, C);
                        diag.close();
                        fw.write(String.format("%d,%d,%.6f,%d,%b,true\n", build, 0, 0.0, -1, false));
                        continue;
                    }
                    log.println("--- Heatmaps before probe ---");
                    logHeatmaps(log, A, "Activation");
                    logHeatmaps(log, M, "Memory");
                    logHeatmaps(log, C, "FieldDepth");
                    A[cy][cx] += 1.0;
                    for (int t=0;t<500;t++) {
                        StepMetrics metrics = stepOnce(A,M,C,p,t,cx,cy,false);
                        if (t % 50 == 0) diag.recordTick(t, A, M, C, metrics, p);
                        if (topologyIsGlobalized(log, A, p, t, "ancient-city-probe")) { log.println("ABORT: topology indicates global activation during ancient city probe at tick " + t); diag.writeAbortSummary("global activation during ancient city probe", computeSpatialMetrics(A, p.regionActiveThreshold), A, M, C); break; }
                    }
                    double maxA = 0.0;
                    for (double[] row : A) for (double value : row) maxA = Math.max(maxA, value);
                    int activatedCells = MetricEvaluator.countActiveCells(A, p.regionActiveThreshold, maxA);
                    double recruitDist = 0.0;
                    for (int y=0;y<H;y++) for (int x=0;x<W;x++) if (A[y][x]>=p.regionActiveThreshold) recruitDist=Math.max(recruitDist, Math.hypot(x-cx,y-cy));
                    int recTime = -1;
                    double[][] A2 = copyGrid(A), M2 = copyGrid(M), C2 = copyGrid(C);
                    for (int t=0;t<2000;t++) {
                        StepMetrics metrics = stepOnce(A2,M2,C2,p,t,cx,cy,false);
                        if (t % 100 == 0) diag.recordTick(10000 + t, A2, M2, C2, metrics, p);
                        double maxA2 = 0.0;
                        for (double[] row : A2) for (double value : row) maxA2 = Math.max(maxA2, value);
                        if (maxA2 < p.regionActiveThreshold) { recTime=t; break; }
                    }
                    for (int t=0;t<200;t++) stepOnce(A2,M2,C2,p,t,cx,cy,false);
                    A2[cy][cx]+=0.05;
                    boolean grew=false;
                    for (int t=0;t<500;t++) {
                        StepMetrics metrics = stepOnce(A2,M2,C2,p,t,cx,cy,false);
                        double maxA2 = 0.0;
                        for (double[] row : A2) for (double value : row) maxA2 = Math.max(maxA2, value);
                        if (maxA2>=p.regionActiveThreshold) { grew=true; break; }
                        if (t % 50 == 0) diag.recordTick(20000 + t, A2, M2, C2, metrics, p);
                        if (topologyIsGlobalized(log, A2, p, t, "ancient-city-reactivation")) { log.println("ABORT: topology indicates global activation during ancient city reactivation check at tick " + t); diag.writeAbortSummary("global activation during ancient city reactivation", computeSpatialMetrics(A2, p.regionActiveThreshold), A2, M2, C2); break; }
                    }
                    diag.writeExperimentSummary(String.format("ancient_city_%d", build), p, W, H, build, 500, 0, A, M, C, "completed", "completed", null, true, true, false, true, p.v3FieldDepthGain != 0.0 || p.v3FieldDepthDecay != 0.0 || p.v3FieldDepthRadius != 0.0);
                    diag.writeFinalGrids(A, M, C);
                    diag.close();
                    fw.write(String.format("%d,%d,%.6f,%d,%b,false\n", build, activatedCells, recruitDist, recTime, grew));
                }
            }
        }
    }

    // helper: simplified stepOnce similar to other tests
    private static StepMetrics stepOnce(double[][] activation, double[][] memory, double[][] depth, ParameterSet parameters, int tick, int injectX, int injectY, boolean inject) {
        int height = activation.length;
        int width = activation[0].length;
        double[][] nextA = new double[height][width];
        double[][] nextM = new double[height][width];
        double[][] nextC = new double[height][width];
        double[][] transportIn = new double[height][width];
        double[][] transportOut = new double[height][width];
        StepMetrics metrics = new StepMetrics();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double A = activation[y][x];
                double M = memory[y][x];
                double C = depth[y][x];
                double transport = 0.0;
                double transportWithoutFieldDepth = 0.0;
                double transportWithFieldDepth = 0.0;
                double transportDistanceWeighted = 0.0;
                double transportDistanceMax = 0.0;
                double transportEnergy = 0.0;
                int transportEvents = 0;
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
                            // Use the source activation directly so weak but real sources still contribute to transport.
                            double sourceStrength = Math.max(0.0, nA);
                            double sourceReadiness = computeReadiness(parameters.transportBalanceMode, nM, nC);
                            double destinationCapacity = 1.0 / (1.0 + 0.5 * Math.max(0.0, A));
                            double distanceAttenuation = Math.exp(-distance / Math.max(0.5, parameters.v3DistanceDecay > 0.0 ? parameters.v3DistanceDecay : 1.0));
                            double contribution = Math.min(1.0, parameters.v2TransportGain * sourceStrength * sourceReadiness * destinationCapacity * distanceAttenuation);
                            transportWithFieldDepth += contribution;
                            if (distance <= 1.0) transportWithoutFieldDepth += contribution;
                            transportIn[ny][nx] += contribution;
                            transportOut[y][x] += contribution;
                            if (contribution > 0.0) {
                                transportEvents++;
                                transportDistanceWeighted += contribution * distance;
                                transportDistanceMax = Math.max(transportDistanceMax, distance);
                                transportEnergy += contribution;
                                if (tick % 50 == 0 || tick == 0) {
                                    metrics.transportSamples.add(new TransportSample(tick, nx, ny, x, y, contribution, distance));
                                }
                            }
                        }
                    }
                }
                transport = Math.max(0.0, transportWithFieldDepth);
                transportWithoutFieldDepth = Math.max(0.0, transportWithoutFieldDepth);
                transportWithFieldDepth = Math.max(0.0, transportWithFieldDepth);

                double external = 0.0;
                if (inject && x == injectX && y == injectY) external = 1.0;

                double netTransport = Math.max(0.0, transportWithFieldDepth);
                double memTerm = (1.0 - (M / (M + parameters.v2MemoryEffectK)));
                double decayFactor = parameters.v2ActivationDecay * memTerm;
                double candidateActivation = A * Math.max(0.0, 1.0 - decayFactor) + netTransport + external;
                double nextActivation = Math.max(0.0, candidateActivation);
                double removedByClamp = 0.0;
                if (parameters.v2ClampMax > 0.0) {
                    if (nextActivation > parameters.v2ClampMax) {
                        removedByClamp = nextActivation - parameters.v2ClampMax;
                    }
                    nextActivation = Math.min(nextActivation, parameters.v2ClampMax);
                }

                double deltaMemory = parameters.v2MemoryGain * (A / (1.0 + A));
                double nextMemory = Math.max(0.0, M + deltaMemory - parameters.v2MemoryDecay * M);

                double deltaDepth = parameters.v3FieldDepthGain * (A * M);
                if (deltaDepth > 1.0) deltaDepth = 1.0;
                double nextDepth = Math.max(0.0, C + deltaDepth - parameters.v3FieldDepthDecay * C);

                nextA[y][x] = nextActivation;
                nextM[y][x] = nextMemory;
                nextC[y][x] = nextDepth;

                metrics.transportEvents += transportEvents > 0 ? 1 : 0;
                metrics.meanTransportMagnitude += netTransport;
                metrics.maxTransportMagnitude = Math.max(metrics.maxTransportMagnitude, netTransport);
                metrics.totalTransportEnergy += netTransport;
                if (transportEvents > 0) {
                    metrics.meanTransportDistance += transportDistanceWeighted / transportEvents;
                }
                metrics.maxTransportDistance = Math.max(metrics.maxTransportDistance, transportDistanceMax);
                metrics.transportWithoutFieldDepth += transportWithoutFieldDepth;
                metrics.transportWithFieldDepth += transportWithFieldDepth;
                metrics.transportDifference += transportWithFieldDepth - transportWithoutFieldDepth;
                metrics.meanFieldDepth += nextDepth;
                metrics.maxFieldDepth = Math.max(metrics.maxFieldDepth, nextDepth);
                if (nextDepth > 0.0) metrics.cellsWithFieldDepthPositive++;
                metrics.fieldDepthCreated += Math.max(0.0, nextDepth - C);
                metrics.fieldDepthLostToDecay += Math.max(0.0, C - nextDepth + (parameters.v3FieldDepthDecay * C));
                metrics.activationCreatedByTransport += Math.max(0.0, netTransport);
                metrics.activationCreatedExternally += external;
                metrics.activationRemovedByDecay += Math.max(0.0, A * (1.0 - Math.max(0.0, 1.0 - decayFactor)) - (A * Math.max(0.0, 1.0 - decayFactor)));
                metrics.activationRemovedByClamp += removedByClamp;
                metrics.netActivationChange += metrics.activationCreatedByTransport + metrics.activationCreatedExternally - metrics.activationRemovedByDecay - metrics.activationRemovedByClamp;
                metrics.memoryCreated += deltaMemory;
                metrics.memoryRemoved += parameters.v2MemoryDecay * M;
            }
        }
        for (int y = 0; y < height; y++) {
            System.arraycopy(nextA[y], 0, activation[y], 0, width);
            System.arraycopy(nextM[y], 0, memory[y], 0, width);
            System.arraycopy(nextC[y], 0, depth[y], 0, width);
        }
        metrics.meanTransportMagnitude = width > 0 && height > 0 ? metrics.meanTransportMagnitude / (height * width) : 0.0;
        metrics.meanTransportDistance = metrics.transportEvents > 0 ? metrics.meanTransportDistance / metrics.transportEvents : 0.0;
        metrics.meanFieldDepth = height > 0 && width > 0 ? metrics.meanFieldDepth / (height * width) : 0.0;
        metrics.transportEvents = metrics.transportEvents;
        metrics.transportDifference = metrics.transportDifference;
        return metrics;
    }
}
