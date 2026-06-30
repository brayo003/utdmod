import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TransportPipelineAuditTest {

    @Test
    void usesDirectSourceActivationForTransportStrength() {
        ParameterSet p = new ParameterSet();
        p.v2TransportGain = 1.0;
        p.v3DistanceDecay = 1.0;
        p.v3FieldDepthRadius = 1.0;

        double[][] activation = new double[3][3];
        activation[0][1] = 1.0;
        double[][] memory = new double[3][3];
        double[][] depth = new double[3][3];

        SimulationStepResult step = stepOnce(activation, memory, depth, p, 0, 0, 0, false,
                false, false, false, false, false, true);

        if (step.traceEvents.isEmpty()) {
            throw new IllegalStateException("Expected a transport trace event");
        }

        TransportTraceEvent event = step.traceEvents.get(0);
        if (Math.abs(event.sourceActivation - 1.0) > 1e-9) {
            throw new IllegalStateException("Expected source activation to be 1.0");
        }
        if (Math.abs(event.sourceStrength - event.sourceActivation) > 1e-9) {
            throw new IllegalStateException("Expected source strength to use the source activation directly");
        }
    }

    @Test
    void runTransportPipelineAudit() throws Exception {
        Path outDir = Paths.get("run/transport_pipeline_audit");
        Files.createDirectories(outDir);

        ParameterSet p = new ParameterSet();
        p.v2TransportGain = 0.08;
        p.v2ActivationDecay = 0.45;
        p.v2MemoryGain = 0.02;
        p.v2MemoryDecay = 0.001;
        p.v2MemoryInfluence = 2.0;
        p.v2MemoryEffectK = 0.1;
        p.v2ClampMax = 5.0;
        p.v3FieldDepthGain = 0.01;
        p.v3FieldDepthDecay = 0.0001;
        p.v3FieldDepthRadius = 1.5;
        p.v3DistanceDecay = 1.0;
        p.v3FieldDepthEffectK = 0.1;
        p.regionActiveThreshold = 0.001;
        p.validationMaxHotspotRadius = 10.0;

        SimulationResult baseline = runSimulation("baseline", p, false, false, false, false, false, 200, 500);
        SimulationResult readiness = runSimulation("readiness_disabled", p, true, false, false, false, false, 200, 500);
        SimulationResult capacity = runSimulation("capacity_disabled", p, false, true, false, false, false, 200, 500);
        SimulationResult distance = runSimulation("distance_disabled", p, false, false, true, false, false, 200, 500);
        SimulationResult fieldDepth = runSimulation("field_depth_gating_disabled", p, false, false, false, true, false, 200, 500);
        SimulationResult sourceStrength = runSimulation("source_strength_saturation_disabled", p, false, false, false, false, true, 200, 500);

        writeAblationReport(outDir, baseline, readiness, capacity, distance, fieldDepth, sourceStrength);
        writeStageStatistics(outDir, baseline.traceEvents, baseline.pipelineStats);
        writeTraceCsv(outDir, baseline.traceEvents);

        // Spot-check that the audit produced a real transport trace and a non-trivial ablation summary.
        if (baseline.traceEvents.isEmpty()) {
            throw new IllegalStateException("No transport trace events were recorded");
        }
        if (baseline.maxTransport <= 0.0) {
            throw new IllegalStateException("No transport was observed in the baseline run");
        }
    }

    private SimulationResult runSimulation(String label, ParameterSet baseParameters, boolean disableReadiness,
                                           boolean disableCapacity, boolean disableDistance,
                                           boolean disableFieldDepthGating, boolean disableSourceStrengthSaturation,
                                           int buildTicks, int observeTicks) throws IOException {
        ParameterSet p = baseParameters.copy();
        int width = 21;
        int height = 21;
        double[][] activation = new double[height][width];
        double[][] memory = new double[height][width];
        double[][] depth = new double[height][width];

        int cx = width / 2;
        int cy = height / 2;
        List<Double> transportMassSeries = new ArrayList<>();
        List<Double> hotspotRadiusSeries = new ArrayList<>();
        List<Integer> hotspotLifetimeSeries = new ArrayList<>();
        List<Double> expansionRadiusSeries = new ArrayList<>();
        List<TransportTraceEvent> traceEvents = new ArrayList<>();
        PipelineStageStats pipelineStats = new PipelineStageStats();

        for (int tick = 0; tick < buildTicks; tick++) {
            boolean inject = (tick % 10) == 0;
            SimulationStepResult step = stepOnce(activation, memory, depth, p, tick, cx, cy, inject,
                    disableReadiness, disableCapacity, disableDistance, disableFieldDepthGating,
                    disableSourceStrengthSaturation, false);
            pipelineStats.accumulate(step.pipelineStats);
            transportMassSeries.add(step.transportMass);
            hotspotRadiusSeries.add(computeHotspotRadius(activation, p.regionActiveThreshold));
            expansionRadiusSeries.add(computeHotspotRadius(activation, p.regionActiveThreshold));
            hotspotLifetimeSeries.add(activeCellCount(activation, p.regionActiveThreshold));
        }

        // Use the first observation tick after an external injection to produce a complete trace.
        for (int tick = 0; tick < observeTicks; tick++) {
            boolean inject = tick == 0;
            SimulationStepResult step = stepOnce(activation, memory, depth, p, buildTicks + tick, cx, cy, inject,
                    disableReadiness, disableCapacity, disableDistance, disableFieldDepthGating,
                    disableSourceStrengthSaturation, tick == 0);
            pipelineStats.accumulate(step.pipelineStats);
            transportMassSeries.add(step.transportMass);
            hotspotRadiusSeries.add(computeHotspotRadius(activation, p.regionActiveThreshold));
            expansionRadiusSeries.add(computeHotspotRadius(activation, p.regionActiveThreshold));
            hotspotLifetimeSeries.add(activeCellCount(activation, p.regionActiveThreshold));
            if (tick == 0) {
                traceEvents.addAll(step.traceEvents);
            }
        }

        double maxTransport = transportMassSeries.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double averageTransport = transportMassSeries.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double maxRadius = hotspotRadiusSeries.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        int hotspotLifetime = (int) hotspotLifetimeSeries.stream().filter(v -> v > 0).count();
        double expansionRadius = expansionRadiusSeries.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double transportedMassPerTick = transportMassSeries.stream().mapToDouble(Double::doubleValue).sum() / Math.max(1, transportMassSeries.size());

        return new SimulationResult(label, maxTransport, averageTransport, maxRadius, hotspotLifetime,
                expansionRadius, transportedMassPerTick, traceEvents, pipelineStats);
    }

    private static double computeHotspotRadius(double[][] grid, double threshold) {
        int height = grid.length;
        int width = grid[0].length;
        double radius = 0.0;
        int centerX = width / 2;
        int centerY = height / 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x] >= threshold) {
                    double dx = x - centerX;
                    double dy = y - centerY;
                    radius = Math.max(radius, Math.sqrt(dx * dx + dy * dy));
                }
            }
        }
        return radius;
    }

    private static int activeCellCount(double[][] grid, double threshold) {
        int count = 0;
        for (double[] row : grid) {
            for (double value : row) {
                if (value >= threshold) count++;
            }
        }
        return count;
    }

    private static SimulationStepResult stepOnce(double[][] activation, double[][] memory, double[][] depth, ParameterSet parameters,
                                                int tick, int injectX, int injectY, boolean inject,
                                                boolean disableReadiness, boolean disableCapacity,
                                                boolean disableDistance, boolean disableFieldDepthGating,
                                                boolean disableSourceStrengthSaturation, boolean traceThisTick) {
        int height = activation.length;
        int width = activation[0].length;
        double[][] nextA = new double[height][width];
        double[][] nextM = new double[height][width];
        double[][] nextC = new double[height][width];
        List<TransportTraceEvent> traceEvents = new ArrayList<>();
        double transportMass = 0.0;
        PipelineStageStats pipelineStats = new PipelineStageStats();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double A = activation[y][x];
                double M = memory[y][x];
                double C = depth[y][x];
                double incomingTransport = 0.0;

                int maxSearchRadius = 1 + (int) Math.floor(parameters.v3FieldDepthRadius);
                for (int dy = -maxSearchRadius; dy <= maxSearchRadius; dy++) {
                    for (int dx = -maxSearchRadius; dx <= maxSearchRadius; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        int ny = y + dy;
                        pipelineStats.candidateNeighbors++;
                        if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                            pipelineStats.outOfBoundsNeighbors++;
                            continue;
                        }
                        double distance = Math.sqrt(dx * dx + dy * dy);
                        if (distance > maxSearchRadius) continue;

                        double nA = activation[ny][nx];
                        double nM = memory[ny][nx];
                        double nC = depth[ny][nx];
                        int sourceRadius = 1 + (int) Math.floor(parameters.v3FieldDepthRadius * nC / (nC + parameters.v3FieldDepthEffectK));
                        if (!disableFieldDepthGating && distance > sourceRadius) continue;

                        double sourceActivation = nA;
                        // Use the source activation directly so weak but real sources still contribute to transport.
                        double sourceStrength = Math.max(0.0, sourceActivation);
                        double readiness = disableReadiness ? 1.0 : computeReadiness(parameters.transportBalanceMode, nM, nC);
                        double destinationCapacity = disableCapacity ? 1.0 : (1.0 / (1.0 + 0.5 * Math.max(0.0, A)));
                        double distanceAttenuation = disableDistance ? 1.0 : Math.exp(-distance / Math.max(0.5, parameters.v3DistanceDecay > 0.0 ? parameters.v3DistanceDecay : 1.0));
                        double transportGain = parameters.v2TransportGain;
                        double rawTransport = Math.min(1.0, transportGain * sourceStrength * readiness * destinationCapacity * distanceAttenuation);
                        pipelineStats.observeContribution(sourceActivation, sourceStrength, readiness, destinationCapacity, distanceAttenuation, transportGain, rawTransport, sourceStrength < 1e-6, readiness < 1e-6, destinationCapacity < 1e-6, distanceAttenuation < 1e-6, transportGain < 1e-6);
                        incomingTransport += rawTransport;
                        transportMass += rawTransport;

                        if (traceThisTick && rawTransport > 0.0) {
                            traceEvents.add(new TransportTraceEvent(
                                    tick,
                                    x,
                                    y,
                                    nx,
                                    ny,
                                    inject ? 1.0 : 0.0,
                                    sourceActivation,
                                    sourceStrength,
                                    readiness,
                                    destinationCapacity,
                                    distanceAttenuation,
                                    transportGain,
                                    rawTransport,
                                    incomingTransport,
                                    0.0
                            ));
                        }
                    }
                }

                double external = 0.0;
                if (inject && x == injectX && y == injectY) external = 1.0;

                double memTerm = (1.0 - (M / (M + parameters.v2MemoryEffectK)));
                double decayFactor = parameters.v2ActivationDecay * memTerm;
                double nextActivation = A * Math.max(0.0, 1.0 - decayFactor) + incomingTransport + external;
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
        return new SimulationStepResult(traceEvents, transportMass, pipelineStats);
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

    private void writeAblationReport(Path outDir, SimulationResult baseline, SimulationResult readiness,
                                     SimulationResult capacity, SimulationResult distance,
                                     SimulationResult fieldDepth, SimulationResult sourceStrength) throws IOException {
        Path report = outDir.resolve("ablation_report.txt");
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(report))) {
            writer.println("Transport pipeline ablation study");
            writer.println("================================");
            writer.println("Run | maxTransport | avgTransport | hotspotRadius | hotspotLifetime | expansionRadius | transportedMassPerTick");
            writer.println(String.format("%s | %.9f | %.9f | %.6f | %d | %.6f | %.9f",
                    baseline.label, baseline.maxTransport, baseline.averageTransport, baseline.hotspotRadius,
                    baseline.hotspotLifetime, baseline.expansionRadius, baseline.transportedMassPerTick));
            writer.println(String.format("%s | %.9f | %.9f | %.6f | %d | %.6f | %.9f",
                    readiness.label, readiness.maxTransport, readiness.averageTransport, readiness.hotspotRadius,
                    readiness.hotspotLifetime, readiness.expansionRadius, readiness.transportedMassPerTick));
            writer.println(String.format("%s | %.9f | %.9f | %.6f | %d | %.6f | %.9f",
                    capacity.label, capacity.maxTransport, capacity.averageTransport, capacity.hotspotRadius,
                    capacity.hotspotLifetime, capacity.expansionRadius, capacity.transportedMassPerTick));
            writer.println(String.format("%s | %.9f | %.9f | %.6f | %d | %.6f | %.9f",
                    distance.label, distance.maxTransport, distance.averageTransport, distance.hotspotRadius,
                    distance.hotspotLifetime, distance.expansionRadius, distance.transportedMassPerTick));
            writer.println(String.format("%s | %.9f | %.9f | %.6f | %d | %.6f | %.9f",
                    fieldDepth.label, fieldDepth.maxTransport, fieldDepth.averageTransport, fieldDepth.hotspotRadius,
                    fieldDepth.hotspotLifetime, fieldDepth.expansionRadius, fieldDepth.transportedMassPerTick));
            writer.println(String.format("%s | %.9f | %.9f | %.6f | %d | %.6f | %.9f",
                    sourceStrength.label, sourceStrength.maxTransport, sourceStrength.averageTransport, sourceStrength.hotspotRadius,
                    sourceStrength.hotspotLifetime, sourceStrength.expansionRadius, sourceStrength.transportedMassPerTick));
            writer.println();
            writer.println("Interpretation");
            SimulationResult worst = findMostSuppressive(readiness, capacity, distance, fieldDepth, sourceStrength, baseline);
            writer.println("Most suppressive ablation: " + worst.label + " (transport retained=" + worst.maxTransport + ")");
            writer.println("The gate that most clearly recovers transport is the one whose ablation yields the largest increase in max transport relative to baseline.");
        }
    }

    private SimulationResult findMostSuppressive(SimulationResult readiness, SimulationResult capacity,
                                                 SimulationResult distance, SimulationResult fieldDepth,
                                                 SimulationResult sourceStrength, SimulationResult baseline) {
        List<SimulationResult> runs = new ArrayList<>();
        runs.add(readiness);
        runs.add(capacity);
        runs.add(distance);
        runs.add(fieldDepth);
        runs.add(sourceStrength);
        SimulationResult best = null;
        double bestGain = -1.0;
        for (SimulationResult candidate : runs) {
            double gain = candidate.maxTransport / Math.max(1e-12, baseline.maxTransport);
            if (gain > bestGain) {
                bestGain = gain;
                best = candidate;
            }
        }
        return best == null ? baseline : best;
    }

    private void writeStageStatistics(Path outDir, List<TransportTraceEvent> events, PipelineStageStats stats) throws IOException {
        Path report = outDir.resolve("stage_statistics.txt");
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(report))) {
            writer.println("Transport stage statistics");
            writer.println("==========================");
            writer.println("candidate_neighbors=" + stats.candidateNeighbors + " out_of_bounds_neighbors=" + stats.outOfBoundsNeighbors);
            writer.println("source_strength_rejected=" + stats.sourceStrengthRejected + " readiness_rejected=" + stats.readinessRejected + " capacity_rejected=" + stats.capacityRejected + " distance_rejected=" + stats.distanceRejected + " gain_rejected=" + stats.gainRejected);
            writeStageSection(writer, "source_strength", events, event -> event.sourceStrength);
            writeStageSection(writer, "readiness", events, event -> event.readiness);
            writeStageSection(writer, "destination_capacity", events, event -> event.destinationCapacity);
            writeStageSection(writer, "distance_attenuation", events, event -> event.distanceAttenuation);
            writeStageSection(writer, "transport_gain", events, event -> event.transportGain);
            writeStageSection(writer, "raw_transport", events, event -> event.rawTransport);
            writer.println();
            writer.println("Multiplicative-gate suppression summary");
            writer.println("source_strength_suppression_pct=" + String.format("%.3f", stats.sourceStrengthSuppressionPct()));
            writer.println("readiness_suppression_pct=" + String.format("%.3f", stats.readinessSuppressionPct()));
            writer.println("destination_capacity_suppression_pct=" + String.format("%.3f", stats.capacitySuppressionPct()));
            writer.println("distance_attenuation_suppression_pct=" + String.format("%.3f", stats.distanceSuppressionPct()));
            writer.println("transport_gain_suppression_pct=" + String.format("%.3f", stats.gainSuppressionPct()));
            writer.println("before_after_multiplication_ranges");
            writer.println(String.format("source_strength: before=%.9f..%.9f after=%.9f..%.9f", stats.sourceBeforeMin, stats.sourceBeforeMax, stats.sourceAfterMin, stats.sourceAfterMax));
            writer.println(String.format("readiness: before=%.9f..%.9f after=%.9f..%.9f", stats.readinessBeforeMin, stats.readinessBeforeMax, stats.readinessAfterMin, stats.readinessAfterMax));
            writer.println(String.format("destination_capacity: before=%.9f..%.9f after=%.9f..%.9f", stats.capacityBeforeMin, stats.capacityBeforeMax, stats.capacityAfterMin, stats.capacityAfterMax));
            writer.println(String.format("distance_attenuation: before=%.9f..%.9f after=%.9f..%.9f", stats.distanceBeforeMin, stats.distanceBeforeMax, stats.distanceAfterMin, stats.distanceAfterMax));
            writer.println(String.format("transport_gain: before=%.9f..%.9f after=%.9f..%.9f", stats.gainBeforeMin, stats.gainBeforeMax, stats.gainAfterMin, stats.gainAfterMax));
        }
    }

    private void writeStageSection(PrintWriter writer, String label, List<TransportTraceEvent> events, java.util.function.Function<TransportTraceEvent, Double> extractor) {
        if (events.isEmpty()) {
            writer.println(label + " | no events");
            return;
        }
        List<Double> values = new ArrayList<>();
        for (TransportTraceEvent event : events) {
            values.add(extractor.apply(event));
        }
        Collections.sort(values);
        double min = values.get(0);
        double max = values.get(values.size() - 1);
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double median = values.size() % 2 == 0
                ? 0.5 * (values.get(values.size() / 2 - 1) + values.get(values.size() / 2))
                : values.get(values.size() / 2);
        writer.println(String.format("%s | min=%.9f max=%.9f mean=%.9f median=%.9f", label, min, max, mean, median));
    }

    private void writeTraceCsv(Path outDir, List<TransportTraceEvent> events) throws IOException {
        Path trace = outDir.resolve("transport_trace.csv");
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(trace))) {
            writer.println("tick,source_x,source_y,destination_x,destination_y,external_input,source_activation,source_strength,readiness,destination_capacity,distance_attenuation,transport_gain,raw_transport,accumulated_incoming_transport,final_activation_update");
            for (TransportTraceEvent event : events) {
                writer.println(String.format("%d,%d,%d,%d,%d,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f",
                        event.tick, event.sourceX, event.sourceY, event.destinationX, event.destinationY,
                        event.externalInput, event.sourceActivation, event.sourceStrength, event.readiness,
                        event.destinationCapacity, event.distanceAttenuation, event.transportGain,
                        event.rawTransport, event.accumulatedIncomingTransport, event.finalActivationUpdate));
            }
        }
    }

    private static class SimulationResult {
        String label;
        double maxTransport;
        double averageTransport;
        double hotspotRadius;
        int hotspotLifetime;
        double expansionRadius;
        double transportedMassPerTick;
        List<TransportTraceEvent> traceEvents;
        PipelineStageStats pipelineStats;

        SimulationResult(String label, double maxTransport, double averageTransport, double hotspotRadius,
                         int hotspotLifetime, double expansionRadius, double transportedMassPerTick,
                         List<TransportTraceEvent> traceEvents, PipelineStageStats pipelineStats) {
            this.label = label;
            this.maxTransport = maxTransport;
            this.averageTransport = averageTransport;
            this.hotspotRadius = hotspotRadius;
            this.hotspotLifetime = hotspotLifetime;
            this.expansionRadius = expansionRadius;
            this.transportedMassPerTick = transportedMassPerTick;
            this.traceEvents = traceEvents;
            this.pipelineStats = pipelineStats;
        }
    }

    private static class SimulationStepResult {
        List<TransportTraceEvent> traceEvents;
        double transportMass;
        PipelineStageStats pipelineStats;

        SimulationStepResult(List<TransportTraceEvent> traceEvents, double transportMass, PipelineStageStats pipelineStats) {
            this.traceEvents = traceEvents;
            this.transportMass = transportMass;
            this.pipelineStats = pipelineStats;
        }
    }

    private static class PipelineStageStats {
        int candidateNeighbors = 0;
        int outOfBoundsNeighbors = 0;
        int sourceStrengthRejected = 0;
        int readinessRejected = 0;
        int capacityRejected = 0;
        int distanceRejected = 0;
        int gainRejected = 0;

        double sourceBeforeMin = Double.POSITIVE_INFINITY;
        double sourceBeforeMax = Double.NEGATIVE_INFINITY;
        double sourceAfterMin = Double.POSITIVE_INFINITY;
        double sourceAfterMax = Double.NEGATIVE_INFINITY;
        double readinessBeforeMin = Double.POSITIVE_INFINITY;
        double readinessBeforeMax = Double.NEGATIVE_INFINITY;
        double readinessAfterMin = Double.POSITIVE_INFINITY;
        double readinessAfterMax = Double.NEGATIVE_INFINITY;
        double capacityBeforeMin = Double.POSITIVE_INFINITY;
        double capacityBeforeMax = Double.NEGATIVE_INFINITY;
        double capacityAfterMin = Double.POSITIVE_INFINITY;
        double capacityAfterMax = Double.NEGATIVE_INFINITY;
        double distanceBeforeMin = Double.POSITIVE_INFINITY;
        double distanceBeforeMax = Double.NEGATIVE_INFINITY;
        double distanceAfterMin = Double.POSITIVE_INFINITY;
        double distanceAfterMax = Double.NEGATIVE_INFINITY;
        double gainBeforeMin = Double.POSITIVE_INFINITY;
        double gainBeforeMax = Double.NEGATIVE_INFINITY;
        double gainAfterMin = Double.POSITIVE_INFINITY;
        double gainAfterMax = Double.NEGATIVE_INFINITY;

        void accumulate(PipelineStageStats other) {
            if (other == null) return;
            this.candidateNeighbors += other.candidateNeighbors;
            this.outOfBoundsNeighbors += other.outOfBoundsNeighbors;
            this.sourceStrengthRejected += other.sourceStrengthRejected;
            this.readinessRejected += other.readinessRejected;
            this.capacityRejected += other.capacityRejected;
            this.distanceRejected += other.distanceRejected;
            this.gainRejected += other.gainRejected;
            this.sourceBeforeMin = Math.min(this.sourceBeforeMin, other.sourceBeforeMin);
            this.sourceBeforeMax = Math.max(this.sourceBeforeMax, other.sourceBeforeMax);
            this.sourceAfterMin = Math.min(this.sourceAfterMin, other.sourceAfterMin);
            this.sourceAfterMax = Math.max(this.sourceAfterMax, other.sourceAfterMax);
            this.readinessBeforeMin = Math.min(this.readinessBeforeMin, other.readinessBeforeMin);
            this.readinessBeforeMax = Math.max(this.readinessBeforeMax, other.readinessBeforeMax);
            this.readinessAfterMin = Math.min(this.readinessAfterMin, other.readinessAfterMin);
            this.readinessAfterMax = Math.max(this.readinessAfterMax, other.readinessAfterMax);
            this.capacityBeforeMin = Math.min(this.capacityBeforeMin, other.capacityBeforeMin);
            this.capacityBeforeMax = Math.max(this.capacityBeforeMax, other.capacityBeforeMax);
            this.capacityAfterMin = Math.min(this.capacityAfterMin, other.capacityAfterMin);
            this.capacityAfterMax = Math.max(this.capacityAfterMax, other.capacityAfterMax);
            this.distanceBeforeMin = Math.min(this.distanceBeforeMin, other.distanceBeforeMin);
            this.distanceBeforeMax = Math.max(this.distanceBeforeMax, other.distanceBeforeMax);
            this.distanceAfterMin = Math.min(this.distanceAfterMin, other.distanceAfterMin);
            this.distanceAfterMax = Math.max(this.distanceAfterMax, other.distanceAfterMax);
            this.gainBeforeMin = Math.min(this.gainBeforeMin, other.gainBeforeMin);
            this.gainBeforeMax = Math.max(this.gainBeforeMax, other.gainBeforeMax);
            this.gainAfterMin = Math.min(this.gainAfterMin, other.gainAfterMin);
            this.gainAfterMax = Math.max(this.gainAfterMax, other.gainAfterMax);
        }

        void observeContribution(double sourceActivation, double sourceStrength, double readiness, double destinationCapacity,
                                 double distanceAttenuation, double transportGain, double rawTransport,
                                 boolean sourceRejected, boolean readinessRejected, boolean capacityRejected,
                                 boolean distanceRejected, boolean gainRejected) {
            if (sourceRejected) this.sourceStrengthRejected++;
            if (readinessRejected) this.readinessRejected++;
            if (capacityRejected) this.capacityRejected++;
            if (distanceRejected) this.distanceRejected++;
            if (gainRejected) this.gainRejected++;

            this.sourceBeforeMin = Math.min(this.sourceBeforeMin, sourceActivation);
            this.sourceBeforeMax = Math.max(this.sourceBeforeMax, sourceActivation);
            this.sourceAfterMin = Math.min(this.sourceAfterMin, sourceStrength);
            this.sourceAfterMax = Math.max(this.sourceAfterMax, sourceStrength);

            this.readinessBeforeMin = Math.min(this.readinessBeforeMin, sourceStrength);
            this.readinessBeforeMax = Math.max(this.readinessBeforeMax, sourceStrength);
            this.readinessAfterMin = Math.min(this.readinessAfterMin, sourceStrength * readiness);
            this.readinessAfterMax = Math.max(this.readinessAfterMax, sourceStrength * readiness);

            this.capacityBeforeMin = Math.min(this.capacityBeforeMin, sourceStrength * readiness);
            this.capacityBeforeMax = Math.max(this.capacityBeforeMax, sourceStrength * readiness);
            this.capacityAfterMin = Math.min(this.capacityAfterMin, sourceStrength * readiness * destinationCapacity);
            this.capacityAfterMax = Math.max(this.capacityAfterMax, sourceStrength * readiness * destinationCapacity);

            this.distanceBeforeMin = Math.min(this.distanceBeforeMin, sourceStrength * readiness * destinationCapacity);
            this.distanceBeforeMax = Math.max(this.distanceBeforeMax, sourceStrength * readiness * destinationCapacity);
            this.distanceAfterMin = Math.min(this.distanceAfterMin, sourceStrength * readiness * destinationCapacity * distanceAttenuation);
            this.distanceAfterMax = Math.max(this.distanceAfterMax, sourceStrength * readiness * destinationCapacity * distanceAttenuation);

            this.gainBeforeMin = Math.min(this.gainBeforeMin, sourceStrength * readiness * destinationCapacity * distanceAttenuation);
            this.gainBeforeMax = Math.max(this.gainBeforeMax, sourceStrength * readiness * destinationCapacity * distanceAttenuation);
            this.gainAfterMin = Math.min(this.gainAfterMin, rawTransport);
            this.gainAfterMax = Math.max(this.gainAfterMax, rawTransport);
        }

        double sourceStrengthSuppressionPct() {
            return candidateNeighbors > 0 ? 100.0 * sourceStrengthRejected / candidateNeighbors : 0.0;
        }
        double readinessSuppressionPct() {
            return candidateNeighbors > 0 ? 100.0 * readinessRejected / candidateNeighbors : 0.0;
        }
        double capacitySuppressionPct() {
            return candidateNeighbors > 0 ? 100.0 * capacityRejected / candidateNeighbors : 0.0;
        }
        double distanceSuppressionPct() {
            return candidateNeighbors > 0 ? 100.0 * distanceRejected / candidateNeighbors : 0.0;
        }
        double gainSuppressionPct() {
            return candidateNeighbors > 0 ? 100.0 * gainRejected / candidateNeighbors : 0.0;
        }
    }

    private static class TransportTraceEvent {
        int tick;
        int sourceX;
        int sourceY;
        int destinationX;
        int destinationY;
        double externalInput;
        double sourceActivation;
        double sourceStrength;
        double readiness;
        double destinationCapacity;
        double distanceAttenuation;
        double transportGain;
        double rawTransport;
        double accumulatedIncomingTransport;
        double finalActivationUpdate;

        TransportTraceEvent(int tick, int sourceX, int sourceY, int destinationX, int destinationY,
                            double externalInput, double sourceActivation, double sourceStrength,
                            double readiness, double destinationCapacity, double distanceAttenuation,
                            double transportGain, double rawTransport, double accumulatedIncomingTransport,
                            double finalActivationUpdate) {
            this.tick = tick;
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.destinationX = destinationX;
            this.destinationY = destinationY;
            this.externalInput = externalInput;
            this.sourceActivation = sourceActivation;
            this.sourceStrength = sourceStrength;
            this.readiness = readiness;
            this.destinationCapacity = destinationCapacity;
            this.distanceAttenuation = distanceAttenuation;
            this.transportGain = transportGain;
            this.rawTransport = rawTransport;
            this.accumulatedIncomingTransport = accumulatedIncomingTransport;
            this.finalActivationUpdate = finalActivationUpdate;
        }
    }
}
