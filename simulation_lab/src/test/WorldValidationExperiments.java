import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * GAMEPLAY VALIDATION EXPERIMENTS
 * 
 * Validates whether the frozen transport architecture produces believable world
 * evolution from a player's perspective. Uses 7 gameplay-scale scenarios.
 * 
 * Frozen Transport Architecture:
 *   T = SourceStrength × Readiness × DestinationCapacity × Distance × Gain
 *   Where:
 *   - SourceStrength = max(0.0, activation)
 *   - Readiness = exp(-memory / 2.0)  [exponential with scale = 2.0]
 *   - DestinationCapacity = 1.0 / (1.0 + 0.5 * activation)
 *   - Distance = exp(-distance / 1.0)
 *   - Gain = 0.08
 */
public class WorldValidationExperiments {

    private final String outDir = "run/world_validation";
    private final double V2_TRANSPORT_GAIN = 0.08;
    private final double V2_ACTIVATION_DECAY = 0.45;
    private final double V2_MEMORY_GAIN = 0.02;
    private final double V2_MEMORY_DECAY = 0.001;
    private final double READINESS_SCALE = 2.0;  // Recommended: exponential
    private final double REGION_ACTIVE_THRESHOLD = 0.001;

    @Test
    void validateWorldEvolution() throws Exception {
        File d = new File(outDir);
        if (!d.exists()) d.mkdirs();

        ParameterSet p = createRecommendedParameters();

        System.out.println("=== WORLD VALIDATION EXPERIMENTS ===");
        System.out.println("Transport Architecture: FROZEN");
        System.out.println("Readiness Law: EXPONENTIAL (scale = " + READINESS_SCALE + ")");
        System.out.println();

        // Scenario 1: Settlement Growth
        System.out.println("[1/7] Settlement Growth...");
        validationSettlementGrowth(p, d);

        // Scenario 2: Historical Persistence
        System.out.println("[2/7] Historical Persistence...");
        validationHistoricalPersistence(p, d);

        // Scenario 3: Mining Scar Experiment
        System.out.println("[3/7] Mining Scar Experiment...");
        validationMiningScar(p, d);

        // Scenario 4: Road Formation
        System.out.println("[4/7] Road Formation...");
        validationRoadFormation(p, d);

        // Scenario 5: Conflict Recovery
        System.out.println("[5/7] Conflict Recovery...");
        validationConflictRecovery(p, d);

        // Scenario 6: Competing Settlements
        System.out.println("[6/7] Competing Settlements...");
        validationCompetingSettlements(p, d);

        // Scenario 7: Frontier Expansion
        System.out.println("[7/7] Frontier Expansion...");
        validationFrontierExpansion(p, d);

        System.out.println();
        System.out.println("=== ALL VALIDATION SCENARIOS COMPLETE ===");
        System.out.println("Output location: " + outDir);
    }

    /**
     * SCENARIO 1: SETTLEMENT GROWTH
     * 
     * Question: Does a small village naturally expand into its surroundings?
     * 
     * Setup: Inject continuous activity into a single cell for 100 ticks.
     * Measure: Growth speed, equilibrium size, expansion stability.
     */
    private void validationSettlementGrowth(ParameterSet p, File outDir) throws IOException {
        int size = 41;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        double[][] transport = new double[size][size];

        int centerX = size / 2, centerY = size / 2;
        double injectionStrength = 0.5;
        int injectionDuration = 100;
        int stabilizationDuration = 300;
        int totalDuration = injectionDuration + stabilizationDuration;

        List<TickSnapshot> snapshots = new ArrayList<>();

        for (int tick = 0; tick < totalDuration; tick++) {
            // Inject activity at center
            if (tick < injectionDuration) {
                activation[centerY][centerX] = injectionStrength;
            }

            // Compute transport
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double incomingTransport = 0.0;
                    for (int dy = -10; dy <= 10; dy++) {
                        for (int dx = -10; dx <= 10; dx++) {
                            int ny = y + dy, nx = x + dx;
                            if (ny >= 0 && ny < size && nx >= 0 && nx < size && (dy != 0 || dx != 0)) {
                                double sourceActivation = activation[ny][nx];
                                double readiness = exponentialReadiness(memory[ny][nx]);
                                double destCapacity = 1.0 / (1.0 + 0.5 * activation[y][x]);
                                double distance = Math.exp(-Math.sqrt(dy*dy + dx*dx) / 1.0);
                                double sourceStrength = Math.max(0.0, sourceActivation);
                                incomingTransport += sourceStrength * readiness * destCapacity * distance * V2_TRANSPORT_GAIN;
                            }
                        }
                    }
                    transport[y][x] = incomingTransport;
                }
            }

            // Update activation and memory
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double newActivation = activation[y][x] * (1.0 - V2_ACTIVATION_DECAY) + transport[y][x];
                    memory[y][x] = memory[y][x] * (1.0 - V2_MEMORY_DECAY) + Math.abs(newActivation) * V2_MEMORY_GAIN;
                    fieldDepth[y][x] = fieldDepth[y][x] * 0.9999 + 0.01 * Math.max(0.0, activation[y][x]);
                    activation[y][x] = newActivation;
                }
            }

            // Record snapshots
            if (tick % 25 == 0 || tick == injectionDuration - 1) {
                snapshots.add(new TickSnapshot(tick, copyArray(activation), copyArray(memory), copyArray(fieldDepth), copyArray(transport)));
            }
        }

        writeSettlementGrowthReport(snapshots, size, centerX, centerY, outDir);
    }

    /**
     * SCENARIO 2: HISTORICAL PERSISTENCE
     * 
     * Question: How long does a city remain "remembered" after abandonment?
     * 
     * Setup: Simulate a city for 200 ticks, then remove all activity and observe.
     * Measure: Memory persistence, reactivation ease vs untouched terrain.
     */
    private void validationHistoricalPersistence(ParameterSet p, File outDir) throws IOException {
        int size = 41;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];

        // Phase 1: Build a city
        int cityRadius = 8;
        int centerX = size / 2, centerY = size / 2;
        for (int tick = 0; tick < 200; tick++) {
            // Pulse activity in city area
            if (tick % 10 < 5) {
                for (int y = centerY - cityRadius; y <= centerY + cityRadius; y++) {
                    for (int x = centerX - cityRadius; x <= centerX + cityRadius; x++) {
                        if (y >= 0 && y < size && x >= 0 && x < size) {
                            activation[y][x] = 0.3;
                        }
                    }
                }
            }
            stepSimulation(activation, memory, fieldDepth, size);
        }

        // Phase 2: Remove activity and observe memory decay
        double[] memoryTrace = new double[400];
        double[] reactivationStrength = new double[400];

        for (int tick = 0; tick < 400; tick++) {
            double avgMemory = 0.0;
            int countOld = 0;
            int countNew = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    avgMemory += memory[y][x];
                    if (Math.sqrt((y-centerY)*(y-centerY) + (x-centerX)*(x-centerX)) <= cityRadius) {
                        countOld++;
                    } else {
                        countNew++;
                    }
                }
            }
            memoryTrace[tick] = avgMemory / (size * size);

            // Test reactivation in old city
            double reactivationOld = 0.0;
            for (int y = centerY - cityRadius; y <= centerY + cityRadius; y++) {
                for (int x = centerX - cityRadius; x <= centerX + cityRadius; x++) {
                    if (y >= 0 && y < size && x >= 0 && x < size) {
                        reactivationOld += exponentialReadiness(memory[y][x]);
                    }
                }
            }
            reactivationStrength[tick] = reactivationOld / (4 * cityRadius * cityRadius);
            
            stepSimulation(activation, memory, fieldDepth, size);
        }

        writeHistoricalPersistenceReport(memoryTrace, reactivationStrength, outDir);
    }

    /**
     * SCENARIO 3: MINING SCAR EXPERIMENT
     * 
     * Question: Is reopening an old mine easier than opening a new one?
     * 
     * Setup: Mine location 1 for 100 ticks, stop for 200 ticks, restart for 100 ticks.
     * Then mine completely new location 2 for 100 ticks.
     * Measure: Comparison of regrowth speed and readiness in both locations.
     */
    private void validationMiningScar(ParameterSet p, File outDir) throws IOException {
        int size = 51;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];

        int oldMineX = 15, oldMineY = 15;
        int newMineX = 35, newMineY = 15;
        double miningStrength = 0.6;

        List<MiningPhaseMetrics> phases = new ArrayList<>();

        // Phase 1: Mine old location (100 ticks)
        for (int tick = 0; tick < 100; tick++) {
            activation[oldMineY][oldMineX] = miningStrength;
            stepSimulation(activation, memory, fieldDepth, size);
        }
        phases.add(new MiningPhaseMetrics("OldMine_Active", recordMetricsNearPoint(activation, memory, oldMineX, oldMineY, size)));

        // Phase 2: Abandon old mine, wait (200 ticks)
        for (int tick = 0; tick < 200; tick++) {
            activation[oldMineY][oldMineX] = 0.0;
            stepSimulation(activation, memory, fieldDepth, size);
        }
        phases.add(new MiningPhaseMetrics("OldMine_Abandoned", recordMetricsNearPoint(activation, memory, oldMineX, oldMineY, size)));

        // Phase 3: Reopen old mine (100 ticks)
        for (int tick = 0; tick < 100; tick++) {
            activation[oldMineY][oldMineX] = miningStrength;
            stepSimulation(activation, memory, fieldDepth, size);
        }
        phases.add(new MiningPhaseMetrics("OldMine_Reopened", recordMetricsNearPoint(activation, memory, oldMineX, oldMineY, size)));

        // Phase 4: New mine opened (100 ticks)
        for (int tick = 0; tick < 100; tick++) {
            activation[newMineY][newMineX] = miningStrength;
            stepSimulation(activation, memory, fieldDepth, size);
        }
        phases.add(new MiningPhaseMetrics("NewMine_Initial", recordMetricsNearPoint(activation, memory, newMineX, newMineY, size)));

        writeMiningScarReport(phases, outDir);
    }

    /**
     * SCENARIO 4: ROAD FORMATION
     * 
     * Question: Do natural transport corridors form between two distant settlements?
     * 
     * Setup: Place settlements at (10, 10) and (30, 30). Observe transport flow.
     * Measure: Corridor emergence, persistence, width, transport volume.
     */
    private void validationRoadFormation(ParameterSet p, File outDir) throws IOException {
        int size = 51;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        double[][] transport = new double[size][size];

        int settleA_X = 10, settleA_Y = 10;
        int settleB_X = 40, settleB_Y = 40;
        double settlementStrength = 0.4;

        List<RoadFormationSnapshot> snapshots = new ArrayList<>();

        for (int tick = 0; tick < 500; tick++) {
            // Maintain settlements
            activation[settleA_Y][settleA_X] = settlementStrength;
            activation[settleB_Y][settleB_X] = settlementStrength;

            // Compute transport
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double incomingTransport = 0.0;
                    for (int dy = -15; dy <= 15; dy++) {
                        for (int dx = -15; dx <= 15; dx++) {
                            int ny = y + dy, nx = x + dx;
                            if (ny >= 0 && ny < size && nx >= 0 && nx < size && (dy != 0 || dx != 0)) {
                                double sourceActivation = activation[ny][nx];
                                double readiness = exponentialReadiness(memory[ny][nx]);
                                double destCapacity = 1.0 / (1.0 + 0.5 * activation[y][x]);
                                double distance = Math.exp(-Math.sqrt(dy*dy + dx*dx) / 1.0);
                                double sourceStrength = Math.max(0.0, sourceActivation);
                                incomingTransport += sourceStrength * readiness * destCapacity * distance * V2_TRANSPORT_GAIN;
                            }
                        }
                    }
                    transport[y][x] = incomingTransport;
                }
            }

            // Update
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double newActivation = activation[y][x] * (1.0 - V2_ACTIVATION_DECAY) + transport[y][x];
                    memory[y][x] = memory[y][x] * (1.0 - V2_MEMORY_DECAY) + Math.abs(newActivation) * V2_MEMORY_GAIN;
                    fieldDepth[y][x] = fieldDepth[y][x] * 0.9999 + 0.01 * Math.max(0.0, activation[y][x]);
                    activation[y][x] = newActivation;
                }
            }

            if (tick % 50 == 0) {
                snapshots.add(new RoadFormationSnapshot(tick, copyArray(activation), copyArray(transport)));
            }
        }

        writeRoadFormationReport(snapshots, size, settleA_X, settleA_Y, settleB_X, settleB_Y, outDir);
    }

    /**
     * SCENARIO 5: CONFLICT RECOVERY
     * 
     * Question: How do scars persist after a catastrophic event?
     * 
     * Setup: Create a localized city, then inject massive temporary energy burst.
     * Measure: Activation spike, recovery time, memory scarring.
     */
    private void validationConflictRecovery(ParameterSet p, File outDir) throws IOException {
        int size = 41;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];

        int centerX = size / 2, centerY = size / 2;

        // Phase 1: Build settlement (100 ticks)
        for (int tick = 0; tick < 100; tick++) {
            int r = 5;
            for (int y = centerY - r; y <= centerY + r; y++) {
                for (int x = centerX - r; x <= centerX + r; x++) {
                    if (y >= 0 && y < size && x >= 0 && x < size) {
                        activation[y][x] = 0.2;
                    }
                }
            }
            stepSimulation(activation, memory, fieldDepth, size);
        }

        // Phase 2: Catastrophic event (inject massive energy spike)
        int r = 7;
        for (int y = centerY - r; y <= centerY + r; y++) {
            for (int x = centerX - r; x <= centerX + r; x++) {
                if (y >= 0 && y < size && x >= 0 && x < size) {
                    activation[y][x] = 1.5;  // Massive spike
                    memory[y][x] = 2.0;      // Heavy scarring
                }
            }
        }

        // Phase 3: Observe recovery (300 ticks)
        double[] activationPeak = new double[300];
        double[] memoryPeak = new double[300];
        double[] recoveryTime = new double[300];

        for (int tick = 0; tick < 300; tick++) {
            // No new activity injected; just observe
            stepSimulation(activation, memory, fieldDepth, size);

            double maxAct = 0.0, maxMem = 0.0;
            for (int y = centerY - 10; y <= centerY + 10; y++) {
                for (int x = centerX - 10; x <= centerX + 10; x++) {
                    if (y >= 0 && y < size && x >= 0 && x < size) {
                        maxAct = Math.max(maxAct, activation[y][x]);
                        maxMem = Math.max(maxMem, memory[y][x]);
                    }
                }
            }
            activationPeak[tick] = maxAct;
            memoryPeak[tick] = maxMem;
            recoveryTime[tick] = tick;
        }

        writeConflictRecoveryReport(activationPeak, memoryPeak, recoveryTime, outDir);
    }

    /**
     * SCENARIO 6: COMPETING SETTLEMENTS
     * 
     * Question: Do natural trade regions emerge when multiple settlements compete?
     * 
     * Setup: 3 settlements arranged in triangle. Observe transport/competition.
     * Measure: Emergence of territories, transport flow patterns, stability.
     */
    private void validationCompetingSettlements(ParameterSet p, File outDir) throws IOException {
        int size = 61;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];

        int settle1_X = 15, settle1_Y = 15;
        int settle2_X = 45, settle2_Y = 15;
        int settle3_X = 30, settle3_Y = 45;
        double settlementStrength = 0.4;

        double[] dominanceA = new double[400];
        double[] dominanceB = new double[400];
        double[] dominanceC = new double[400];

        for (int tick = 0; tick < 400; tick++) {
            activation[settle1_Y][settle1_X] = settlementStrength;
            activation[settle2_Y][settle2_X] = settlementStrength;
            activation[settle3_Y][settle3_X] = settlementStrength;

            stepSimulation(activation, memory, fieldDepth, size);

            // Measure dominance: which settlement's activity reaches each region most?
            double countA = 0, countB = 0, countC = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double distA = Math.sqrt((y-settle1_Y)*(y-settle1_Y) + (x-settle1_X)*(x-settle1_X));
                    double distB = Math.sqrt((y-settle2_Y)*(y-settle2_Y) + (x-settle2_X)*(x-settle2_X));
                    double distC = Math.sqrt((y-settle3_Y)*(y-settle3_Y) + (x-settle3_X)*(x-settle3_X));
                    double inflA = activation[settle1_Y][settle1_X] * exponentialReadiness(memory[settle1_Y][settle1_X]) * Math.exp(-distA/5.0);
                    double inflB = activation[settle2_Y][settle2_X] * exponentialReadiness(memory[settle2_Y][settle2_X]) * Math.exp(-distB/5.0);
                    double inflC = activation[settle3_Y][settle3_X] * exponentialReadiness(memory[settle3_Y][settle3_X]) * Math.exp(-distC/5.0);
                    double total = inflA + inflB + inflC;
                    if (total > 0.001) {
                        if (inflA > inflB && inflA > inflC) countA += 1.0;
                        else if (inflB > inflA && inflB > inflC) countB += 1.0;
                        else if (inflC > inflA && inflC > inflB) countC += 1.0;
                    }
                }
            }
            dominanceA[tick] = countA / (size * size);
            dominanceB[tick] = countB / (size * size);
            dominanceC[tick] = countC / (size * size);
        }

        writeCompetingSettlementsReport(dominanceA, dominanceB, dominanceC, outDir);
    }

    /**
     * SCENARIO 7: FRONTIER EXPANSION
     * 
     * Question: How fast does a civilization expand into empty territory?
     * 
     * Setup: Single seed settlement in empty world. Measure expansion over time.
     * Measure: Expansion radius vs time, saturation point.
     */
    private void validationFrontierExpansion(ParameterSet p, File outDir) throws IOException {
        int size = 81;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];

        int seedX = size / 2, seedY = size / 2;
        double seedStrength = 0.5;

        double[] frontierRadius = new double[600];
        double[] frontierActivation = new double[600];
        double[] totalActivation = new double[600];

        for (int tick = 0; tick < 600; tick++) {
            activation[seedY][seedX] = seedStrength;
            stepSimulation(activation, memory, fieldDepth, size);

            // Measure frontier: find radius where activation just exceeds threshold
            double maxRadius = 0.0;
            double frontierAct = 0.0;
            double totalAct = 0.0;

            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    totalAct += activation[y][x];
                    double dist = Math.sqrt((y-seedY)*(y-seedY) + (x-seedX)*(x-seedX));
                    if (activation[y][x] > REGION_ACTIVE_THRESHOLD) {
                        maxRadius = Math.max(maxRadius, dist);
                        if (dist > maxRadius - 3.0) {  // Near frontier
                            frontierAct += activation[y][x];
                        }
                    }
                }
            }

            frontierRadius[tick] = maxRadius;
            frontierActivation[tick] = frontierAct;
            totalActivation[tick] = totalAct;
        }

        writeFrontierExpansionReport(frontierRadius, frontierActivation, totalActivation, outDir);
    }

    // ========== HELPER METHODS ==========

    private ParameterSet createRecommendedParameters() {
        ParameterSet p = new ParameterSet();
        p.readinessMemoryScale = READINESS_SCALE;
        p.transportBalanceMode = "exponential";
        p.v2TransportGain = V2_TRANSPORT_GAIN;
        p.v2ActivationDecay = V2_ACTIVATION_DECAY;
        p.v2MemoryGain = V2_MEMORY_GAIN;
        p.v2MemoryDecay = V2_MEMORY_DECAY;
        return p;
    }

    private double exponentialReadiness(double memory) {
        return Math.exp(-memory / READINESS_SCALE);
    }

    private void stepSimulation(double[][] activation, double[][] memory, double[][] fieldDepth, int size) {
        double[][] newActivation = new double[size][size];
        double[][] transport = new double[size][size];

        // Compute transport everywhere
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double incomingTransport = 0.0;
                int radius = 15;
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        int ny = y + dy, nx = x + dx;
                        if (ny >= 0 && ny < size && nx >= 0 && nx < size && (dy != 0 || dx != 0)) {
                            double sourceActivation = activation[ny][nx];
                            double readiness = exponentialReadiness(memory[ny][nx]);
                            double destCapacity = 1.0 / (1.0 + 0.5 * activation[y][x]);
                            double dist = Math.sqrt(dy*dy + dx*dx);
                            double distance = Math.exp(-dist / 1.0);
                            double sourceStrength = Math.max(0.0, sourceActivation);
                            incomingTransport += sourceStrength * readiness * destCapacity * distance * V2_TRANSPORT_GAIN;
                        }
                    }
                }
                transport[y][x] = incomingTransport;
            }
        }

        // Update state
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double newAct = activation[y][x] * (1.0 - V2_ACTIVATION_DECAY) + transport[y][x];
                newActivation[y][x] = newAct;
                memory[y][x] = memory[y][x] * (1.0 - V2_MEMORY_DECAY) + Math.abs(newAct) * V2_MEMORY_GAIN;
                fieldDepth[y][x] = fieldDepth[y][x] * 0.9999 + 0.01 * Math.max(0.0, newAct);
            }
        }

        // Copy back
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                activation[y][x] = newActivation[y][x];
            }
        }
    }

    private double[][] copyArray(double[][] arr) {
        int h = arr.length, w = arr[0].length;
        double[][] copy = new double[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                copy[y][x] = arr[y][x];
            }
        }
        return copy;
    }

    private double[] recordMetricsNearPoint(double[][] activation, double[][] memory, int cx, int cy, int size) {
        double[] metrics = new double[5];
        int r = 5;
        for (int y = Math.max(0, cy-r); y < Math.min(size, cy+r+1); y++) {
            for (int x = Math.max(0, cx-r); x < Math.min(size, cx+r+1); x++) {
                metrics[0] += activation[y][x];
                metrics[1] += memory[y][x];
                metrics[2] += 1.0;
            }
        }
        metrics[0] /= metrics[2];
        metrics[1] /= metrics[2];
        return metrics;
    }

    // ========== REPORT WRITERS ==========

    private void writeSettlementGrowthReport(List<TickSnapshot> snapshots, int size, int cx, int cy, File outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(new File(outDir, "01_settlement_growth.txt")))) {
            w.println("SCENARIO 1: SETTLEMENT GROWTH");
            w.println("=============================");
            w.println("Question: Does a small village naturally expand into its surroundings?");
            w.println();
            w.println("Setup: Continuous activity injection (0.5) at center for 100 ticks");
            w.println("       Then observe stabilization for 300 more ticks");
            w.println();
            w.println("Tick | Center Act | Mean Act | Max Act | Hotspot Radius | Notes");
            w.println("-----|------------|----------|---------|----------------|------");

            for (TickSnapshot snap : snapshots) {
                double centerAct = snap.activation[cy][cx];
                double meanAct = 0.0, maxAct = 0.0;
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        meanAct += snap.activation[y][x];
                        maxAct = Math.max(maxAct, snap.activation[y][x]);
                    }
                }
                meanAct /= (size * size);

                double radius = 0.0;
                for (int y = 0; y < size; y++) {
                    for (int x = 0; x < size; x++) {
                        if (snap.activation[y][x] > REGION_ACTIVE_THRESHOLD) {
                            double dist = Math.sqrt((y-cy)*(y-cy) + (x-cx)*(x-cx));
                            radius = Math.max(radius, dist);
                        }
                    }
                }

                String note = snap.tick < 100 ? "GROWTH" : "STABILIZATION";
                w.printf("%4d | %10.4f | %8.4f | %7.4f | %14.2f | %s%n", snap.tick, centerAct, meanAct, maxAct, radius, note);
            }
            w.println();
            w.println("GAMEPLAY OBSERVATION:");
            w.println("Does a player see steady outward expansion followed by stable settlement?");
        }
    }

    private void writeHistoricalPersistenceReport(double[] memoryTrace, double[] reactivationStrength, File outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(new File(outDir, "02_historical_persistence.txt")))) {
            w.println("SCENARIO 2: HISTORICAL PERSISTENCE");
            w.println("===================================");
            w.println("Question: How long does a city remain 'remembered' after abandonment?");
            w.println();
            w.println("Setup: Build city for 200 ticks, then observe for 400 ticks post-abandonment");
            w.println();
            w.println("Tick | Avg Memory | Reactivation Ease | Notes");
            w.println("-----|------------|-------------------|--------------");

            for (int tick = 0; tick < Math.min(400, memoryTrace.length); tick++) {
                String note = (tick < 50) ? "EARLY_RECOVERY" : (tick < 200) ? "MID_DECAY" : "LATE_DECAY";
                w.printf("%4d | %10.4f | %17.4f | %s%n", tick, memoryTrace[tick], reactivationStrength[tick], note);
            }
            w.println();
            w.println("GAMEPLAY OBSERVATION:");
            w.println("Can the player see history encoded in the landscape?");
            w.println("How much faster do old cities reactivate vs virgin land?");
        }
    }

    private void writeMiningScarReport(List<MiningPhaseMetrics> phases, File outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(new File(outDir, "03_mining_scar.txt")))) {
            w.println("SCENARIO 3: MINING SCAR EXPERIMENT");
            w.println("==================================");
            w.println("Question: Is reopening an old mine easier than opening a new one?");
            w.println();
            w.println("Phase | Avg Activation | Avg Memory | Analysis");
            w.println("------|----------------|------------|------------------------------------------");

            for (int i = 0; i < phases.size(); i++) {
                MiningPhaseMetrics m = phases.get(i);
                w.printf("%-30s | %14.4f | %10.4f | %s%n", m.name, m.metrics[0], m.metrics[1], 
                    (i == 2 ? "REOPENED_FASTER?" : i == 3 ? "NEW_MINE_BASELINE" : ""));
            }
            w.println();
            w.println("GAMEPLAY OBSERVATION:");
            w.println("Can the player see that returning to old mines is faster/easier?");
        }
    }

    private void writeRoadFormationReport(List<RoadFormationSnapshot> snapshots, int size, int ax, int ay, int bx, int by, File outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(new File(outDir, "04_road_formation.txt")))) {
            w.println("SCENARIO 4: ROAD FORMATION");
            w.println("==========================");
            w.println("Question: Do natural transport corridors form between settlements?");
            w.println();
            w.println("Setup: Two settlements at (" + ax + "," + ay + ") and (" + bx + "," + by + ")");
            w.println();
            w.println("Tick | Corridor Activation | Road Visibility | Notes");
            w.println("-----|---------------------|-----------------|-------");

            for (RoadFormationSnapshot snap : snapshots) {
                double corridorTransport = 0.0;
                int corridorCells = 0;
                // Measure transport along diagonal line between settlements
                for (double t = 0.0; t <= 1.0; t += 0.05) {
                    int py = (int)(ay + t * (by - ay));
                    int px = (int)(ax + t * (bx - ax));
                    if (py >= 0 && py < size && px >= 0 && px < size) {
                        corridorTransport += snap.transport[py][px];
                        corridorCells++;
                    }
                }
                double avgCorridorTransport = corridorCells > 0 ? corridorTransport / corridorCells : 0.0;
                w.printf("%4d | %19.4f | %15s | %s%n", snap.tick, avgCorridorTransport, 
                    avgCorridorTransport > 0.01 ? "VISIBLE_ROAD" : "NO_ROAD_YET", "");
            }
            w.println();
            w.println("GAMEPLAY OBSERVATION:");
            w.println("Can the player see trade routes forming as worn paths in the landscape?");
        }
    }

    private void writeConflictRecoveryReport(double[] activationPeak, double[] memoryPeak, double[] time, File outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(new File(outDir, "05_conflict_recovery.txt")))) {
            w.println("SCENARIO 5: CONFLICT RECOVERY");
            w.println("=============================");
            w.println("Question: How do scars persist after a catastrophic event?");
            w.println();
            w.println("Setup: Build settlement, inject 1.5x energy spike (conflict), observe recovery");
            w.println();
            w.println("Tick | Activation Peak | Memory Peak | Recovery Stage");
            w.println("-----|-----------------|-------------|----------------------------");

            for (int tick = 0; tick < Math.min(300, activationPeak.length); tick++) {
                String stage = (tick < 50) ? "ACTIVE_RECOVERY" : (tick < 150) ? "MID_DECAY" : "LATE_MEMORY";
                w.printf("%4d | %15.4f | %11.4f | %s%n", tick, activationPeak[tick], memoryPeak[tick], stage);
            }
            w.println();
            w.println("GAMEPLAY OBSERVATION:");
            w.println("Do players see visible scars from conflict?");
            w.println("Can they tell old battlefields by heightened memory?");
        }
    }

    private void writeCompetingSettlementsReport(double[] domA, double[] domB, double[] domC, File outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(new File(outDir, "06_competing_settlements.txt")))) {
            w.println("SCENARIO 6: COMPETING SETTLEMENTS");
            w.println("=================================");
            w.println("Question: Do natural trade regions emerge when settlements compete?");
            w.println();
            w.println("Tick | Settlement_A Dominance | Settlement_B Dominance | Settlement_C Dominance | Territory Stability");
            w.println("-----|------------------------|------------------------|------------------------|-------------------");

            for (int tick = 0; tick < Math.min(400, domA.length); tick++) {
                double stability = 1.0 - (Math.abs(domA[tick] - (tick > 0 ? domA[tick-1] : domA[tick])) + 
                                         Math.abs(domB[tick] - (tick > 0 ? domB[tick-1] : domB[tick])) + 
                                         Math.abs(domC[tick] - (tick > 0 ? domC[tick-1] : domC[tick]))) / 3.0;
                w.printf("%4d | %22.4f | %22.4f | %22.4f | %17.4f%n", tick, domA[tick], domB[tick], domC[tick], Math.max(0, stability));
            }
            w.println();
            w.println("GAMEPLAY OBSERVATION:");
            w.println("Do players see natural territorial boundaries emerging?");
            w.println("Do settlements maintain stable territories?");
        }
    }

    private void writeFrontierExpansionReport(double[] radius, double[] frontierAct, double[] totalAct, File outDir) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(new File(outDir, "07_frontier_expansion.txt")))) {
            w.println("SCENARIO 7: FRONTIER EXPANSION");
            w.println("===============================");
            w.println("Question: How fast does a civilization expand into empty territory?");
            w.println();
            w.println("Tick | Frontier Radius | Expansion Rate | Frontier Activity | Total Activity | Growth Stage");
            w.println("-----|-----------------|-----------------|-------------------|-----------------|---------------------------");

            double prevRadius = 0.0;
            for (int tick = 0; tick < Math.min(600, radius.length); tick++) {
                double expansionRate = (tick > 0) ? (radius[tick] - prevRadius) : 0.0;
                String stage = (tick < 150) ? "RAPID_EXPANSION" : (tick < 300) ? "MODERATE_GROWTH" : (tick < 450) ? "SLOWING" : "SATURATION";
                w.printf("%4d | %15.2f | %15.4f | %17.4f | %15.4f | %s%n", 
                    tick, radius[tick], expansionRate, frontierAct[tick], totalAct[tick], stage);
                prevRadius = radius[tick];
            }
            w.println();
            w.println("GAMEPLAY OBSERVATION:");
            w.println("Does the player see consistent expansion followed by saturation?");
            w.println("Does growth curve match player intuition about territory expansion?");
        }
    }

    // Helper classes
    private static class TickSnapshot {
        int tick;
        double[][] activation, memory, fieldDepth, transport;
        TickSnapshot(int tick, double[][] a, double[][] m, double[][] f, double[][] t) {
            this.tick = tick;
            this.activation = a;
            this.memory = m;
            this.fieldDepth = f;
            this.transport = t;
        }
    }

    private static class MiningPhaseMetrics {
        String name;
        double[] metrics;
        MiningPhaseMetrics(String n, double[] m) { this.name = n; this.metrics = m; }
    }

    private static class RoadFormationSnapshot {
        int tick;
        double[][] activation, transport;
        RoadFormationSnapshot(int tick, double[][] a, double[][] t) {
            this.tick = tick;
            this.activation = a;
            this.transport = t;
        }
    }
}
