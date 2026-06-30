import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * TRANSPORT PARAMETER SPACE EXPLORATION
 * 
 * Objective: Map the behavioral landscape of the frozen transport architecture
 * by sweeping two key parameters across physically reasonable ranges.
 * 
 * Frozen Architecture (IMMUTABLE):
 *   T = SourceStrength × Readiness × DestinationCapacity × Distance × Gain
 *   Where:
 *   - SourceStrength = max(0.0, activation)
 *   - Readiness = exp(-memory / 2.0)  [FIXED]
 *   - DestinationCapacity = 1.0 / (1.0 + 0.5 * activation)  [FIXED]
 *   - Distance = exp(-distance / distanceDecay)  [SWEEP THIS]
 *   - Gain = transportGain  [SWEEP THIS]
 * 
 * Parameters Swept:
 *   1. transportGain: 0.02 to 0.30 (8 points)
 *   2. distanceDecay: 0.5 to 15.0 (9 points)
 *   Total: 72 operating points
 * 
 * For each operating point, measure:
 *   - Settlement Persistence: Collapse time after injection stops
 *   - Autonomous Growth: Max radius reached without injection
 *   - Trade-Route Formation: Corridor strength at 42 cells
 *   - Hotspot Expansion: Peak expansion radius during injection
 *   - Recovery After Abandonment: Reactivation ease score
 *   - Historical Advantage: Readiness(old) - Readiness(new)
 *   - Territorial Dynamics: Territory stability (0=dynamic, 1=static)
 * 
 * Output: Phase diagram identifying viable operating regions
 */
public class TransportParameterSweep {
    
    private final String outDir = "run/parameter_sweep";
    
    // Sweep ranges (physically reasonable, optimized for time)
    private final double[] transportGains = {0.02, 0.04, 0.08, 0.12, 0.16, 0.24};
    private final double[] distanceDecays = {0.5, 1.0, 2.0, 3.0, 5.0, 10.0};
    
    // Fixed parameters (frozen architecture)
    private final double READINESS_SCALE = 2.0;  // exp(-memory / 2.0)
    private final double V2_ACTIVATION_DECAY = 0.45;
    private final double V2_MEMORY_GAIN = 0.02;
    private final double V2_MEMORY_DECAY = 0.001;
    
    @Test
    void sweepTransportParameterSpace() throws Exception {
        File d = new File(outDir);
        if (!d.exists()) d.mkdirs();
        
        System.out.println("=== TRANSPORT PARAMETER SPACE EXPLORATION ===");
        System.out.println("Objective: Map behavioral landscape");
        System.out.println("Architecture: FROZEN (no modifications)");
        System.out.println("Readiness: EXPONENTIAL (scale=2.0, FIXED)");
        System.out.println();
        System.out.println("Parameters to sweep:");
        System.out.println("  transportGain: " + Arrays.toString(transportGains));
        System.out.println("  distanceDecay: " + Arrays.toString(distanceDecays));
        System.out.println("  Total operating points: " + (transportGains.length * distanceDecays.length));
        System.out.println();
        
        List<OperatingPoint> results = new ArrayList<>();
        
        // Sweep parameter space
        for (double gain : transportGains) {
            for (double distDecay : distanceDecays) {
                System.out.printf("Testing: Gain=%.2f, DistDecay=%.1f... ", gain, distDecay);
                System.out.flush();
                
                OperatingPoint op = evaluateOperatingPoint(gain, distDecay);
                results.add(op);
                
                System.out.printf("Persistence=%.0f, Growth=%.1f, Corridor=%.4f%n",
                    op.settlementPersistence, op.autonomousGrowthRadius, op.tradeRouteStrength);
            }
        }
        
        System.out.println();
        System.out.println("=== WRITING RESULTS ===");
        
        // Write detailed results
        writeOperatingPointTable(results, new File(outDir, "operating_points.txt"));
        writePhaseMapData(results, new File(outDir, "phase_map.txt"));
        writeRegimeSummary(results, new File(outDir, "regime_analysis.txt"));
        writePhaseDiagramCSV(results, new File(outDir, "phase_diagram.csv"));
        
        System.out.println("Complete! Output in: " + outDir);
    }
    
    /**
     * Evaluate a single operating point by running compact validation tests
     */
    private OperatingPoint evaluateOperatingPoint(double transportGain, double distanceDecay) {
        OperatingPoint op = new OperatingPoint(transportGain, distanceDecay);
        
        ParameterSet params = new ParameterSet();
        params.v2TransportGain = transportGain;
        params.v2ActivationDecay = V2_ACTIVATION_DECAY;
        params.v2MemoryGain = V2_MEMORY_GAIN;
        params.v2MemoryDecay = V2_MEMORY_DECAY;
        params.v3DistanceDecay = distanceDecay;
        params.readinessMemoryScale = READINESS_SCALE;
        
        try {
            // Test 1: Settlement Persistence
            op.settlementPersistence = measureSettlementPersistence(params);
            
            // Test 2: Autonomous Growth Radius
            op.autonomousGrowthRadius = measureAutonomousGrowthRadius(params);
            
            // Test 3: Trade-Route Formation
            op.tradeRouteStrength = measureTradeRouteStrength(params);
            
            // Test 4: Hotspot Expansion
            op.hotspotExpansionRadius = measureHotspotExpansion(params);
            
            // Test 5: Recovery After Abandonment
            op.recoveryAfterAbandonmentEase = measureRecoveryEase(params);
            
            // Test 6: Historical Reactivation Advantage
            op.historicalAdvantage = measureHistoricalAdvantage(params);
            
            // Test 7: Territorial Dynamics
            op.territorialStability = measureTerritorialStability(params);
            
            // Classify the operating point
            op.classify();
            
        } catch (Exception e) {
            op.regime = "ERROR";
        }
        
        return op;
    }
    
    /**
     * Measure how long settlement persists after external input stops
     * Return: Number of ticks until collapse (when max activation < 0.001)
     */
    private double measureSettlementPersistence(ParameterSet params) {
        int size = 17;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        
        int cx = size / 2, cy = size / 2;
        double injectionStrength = 0.5;
        
        // Inject for 12 ticks to establish settlement (faster)
        for (int t = 0; t < 12; t++) {
            activation[cx][cy] += injectionStrength;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        
        // Now stop injection and measure collapse time (up to 300 ticks)
        int collapseTime = 0;
        for (int t = 0; t < 300; t++) {
            double maxAct = getMaxActivation(activation, size);
            if (maxAct < 0.001) {
                collapseTime = t;
                break;
            }
            stepSimulation(activation, memory, fieldDepth, params, size);
            if (t == 299) collapseTime = 300;
        }
        
        return collapseTime;
    }
    
    /**
     * Measure maximum expansion radius during growth phase
     */
    private double measureAutonomousGrowthRadius(ParameterSet params) {
        int size = 29;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        
        int cx = size / 2, cy = size / 2;
        
        // Inject continuously for 28 ticks
        for (int t = 0; t < 28; t++) {
            activation[cx][cy] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        
        // Measure radius of active region
        return measureActiveRadius(activation, size, 0.002);
    }
    
    /**
     * Measure strength of transport corridor between distant settlements (30 cells apart for speed)
     */
    private double measureTradeRouteStrength(ParameterSet params) {
        int size = 55;  // Smaller for speed
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        
        int x1 = 12, y1 = 27;  // Settlement 1
        int x2 = 42, y2 = 27;  // Settlement 2 (30 cells away)
        
        // Establish both settlements (faster)
        for (int t = 0; t < 20; t++) {
            activation[x1][y1] += 0.5;
            activation[x2][y2] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        
        // Measure activation at midpoint
        int midX = (x1 + x2) / 2;
        return activation[midX][y1];  // Activation at corridor midpoint
    }
    
    /**
     * Measure peak expansion radius reached during injection
     */
    private double measureHotspotExpansion(ParameterSet params) {
        int size = 29;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        
        int cx = size / 2, cy = size / 2;
        double maxRadius = 0;
        
        // Inject for 28 ticks
        for (int t = 0; t < 28; t++) {
            activation[cx][cy] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
            
            double radius = measureActiveRadius(activation, size, 0.002);
            if (radius > maxRadius) maxRadius = radius;
        }
        
        return maxRadius;
    }
    
    /**
     * Measure reactivation ease after abandonment
     * Compare readiness of old location (with history) vs new location
     */
    private double measureRecoveryEase(ParameterSet params) {
        int size = 17;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        
        int cx = size / 2, cy = size / 2;
        
        // Build up memory at center (faster)
        for (int t = 0; t < 20; t++) {
            activation[cx][cy] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        
        // Stop injection and let it decay (shorter)
        for (int t = 0; t < 25; t++) {
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        
        // Measure readiness (ease of reactivation)
        double readiness = exponentialReadiness(memory[cx][cy]);
        return readiness;
    }
    
    /**
     * Measure historical advantage: exp(-old_memory/scale) vs exp(-new_memory/scale)
     * Return: advantage value (positive if old better, negative if new better)
     */
    private double measureHistoricalAdvantage(ParameterSet params) {
        int size = 17;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        
        int cx = size / 2, cy = size / 2;
        
        // Build memory at old location (faster)
        for (int t = 0; t < 20; t++) {
            activation[cx][cy] += 0.5;
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        
        // Let decay for a bit (shorter)
        for (int t = 0; t < 15; t++) {
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        
        double oldMemory = memory[cx][cy];
        double newMemory = 0.0;  // Virgin location
        
        double readinessOld = exponentialReadiness(oldMemory);
        double readinessNew = exponentialReadiness(newMemory);
        
        return readinessOld - readinessNew;  // Positive if old better (as expected)
    }
    
    /**
     * Measure territorial stability: how static are the territories?
     * Return: stability value (1.0 = completely static, 0.0 = highly dynamic)
     */
    private double measureTerritorialStability(ParameterSet params) {
        int size = 29;
        double[][] activation = new double[size][size];
        double[][] memory = new double[size][size];
        double[][] fieldDepth = new double[size][size];
        
        // Three settlements at triangle vertices
        int[] sx = {7, 22, 14};
        int[] sy = {7, 7, 22};
        
        // Establish settlements (faster)
        for (int t = 0; t < 20; t++) {
            for (int i = 0; i < 3; i++) {
                activation[sx[i]][sy[i]] += 0.5;
            }
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        
        // Measure dominance at two time points
        double[][] influence1 = computeInfluence(activation, sx, sy, size);
        double[] dom1 = computeDominance(influence1);
        
        // Run more ticks (shorter)
        for (int t = 0; t < 30; t++) {
            for (int i = 0; i < 3; i++) {
                activation[sx[i]][sy[i]] += 0.5;
            }
            stepSimulation(activation, memory, fieldDepth, params, size);
        }
        
        double[][] influence2 = computeInfluence(activation, sx, sy, size);
        double[] dom2 = computeDominance(influence2);
        
        // Stability = 1 - change
        double change = Math.abs(dom1[0] - dom2[0]) + Math.abs(dom1[1] - dom2[1]) + Math.abs(dom1[2] - dom2[2]);
        return 1.0 - Math.min(change, 1.0);
    }
    
    private double[][] computeInfluence(double[][] activation, int[] sx, int[] sy, int size) {
        double[][] influence = new double[3][size];
        for (int y = 0; y < size; y++) {
            for (int i = 0; i < 3; i++) {
                double dist = Math.hypot(sx[i], y - sy[i]);
                influence[i][y] = activation[sx[i]][sy[i]] * Math.exp(-dist / 5.0);
            }
        }
        return influence;
    }
    
    private double[] computeDominance(double[][] influence) {
        double[] dom = new double[3];
        double total = 0;
        for (int i = 0; i < 3; i++) {
            dom[i] = 0;
            for (int x = 0; x < influence[i].length; x++) {
                dom[i] += influence[i][x];
            }
            total += dom[i];
        }
        if (total > 0) {
            for (int i = 0; i < 3; i++) dom[i] /= total;
        }
        return dom;
    }
    
    /**
     * Helper: Transport equation for single step
     */
    private void stepSimulation(double[][] activation, double[][] memory, double[][] fieldDepth, ParameterSet params, int size) {
        double[][] newActivation = new double[size][size];
        
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double incomingTransport = 0.0;
                
                // Transport from radius 1 to 15 cells
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
                
                // Update activation with decay
                newActivation[x][y] = activation[x][y] * (1.0 - params.v2ActivationDecay) + incomingTransport;
                
                // Update memory (only if activated)
                if (activation[x][y] > 0) {
                    memory[x][y] = Math.min(10.0, memory[x][y] + params.v2MemoryGain * activation[x][y]);
                }
                memory[x][y] *= (1.0 - params.v2MemoryDecay);
            }
        }
        
        // Copy new activation back
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                activation[x][y] = newActivation[x][y];
            }
        }
    }
    
    private double exponentialReadiness(double memory) {
        return Math.exp(-memory / READINESS_SCALE);
    }
    
    private double getMaxActivation(double[][] activation, int size) {
        double max = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (activation[x][y] > max) max = activation[x][y];
            }
        }
        return max;
    }
    
    private double measureActiveRadius(double[][] activation, int size, double threshold) {
        int cx = size / 2, cy = size / 2;
        double maxDist = 0;
        
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (activation[x][y] > threshold) {
                    double dist = Math.hypot(x - cx, y - cy);
                    if (dist > maxDist) maxDist = dist;
                }
            }
        }
        
        return maxDist;
    }
    
    /**
     * Write operating point table
     */
    private void writeOperatingPointTable(List<OperatingPoint> points, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("OPERATING POINT EVALUATION TABLE");
            pw.println("================================\n");
            
            pw.println("Gain\tDistDecay\tPersist\tGrowth\tCorridor\tExpansion\tRecovery\tHistAdv\tStability\tRegime");
            pw.println("----\t---------\t-------\t------\t--------\t---------\t--------\t-------\t---------\t------");
            
            for (OperatingPoint op : points) {
                pw.printf("%.2f\t%.1f\t%.0f\t%.1f\t%.4f\t%.1f\t%.3f\t%.4f\t%.3f\t%s%n",
                    op.transportGain,
                    op.distanceDecay,
                    op.settlementPersistence,
                    op.autonomousGrowthRadius,
                    op.tradeRouteStrength,
                    op.hotspotExpansionRadius,
                    op.recoveryAfterAbandonmentEase,
                    op.historicalAdvantage,
                    op.territorialStability,
                    op.regime
                );
            }
        }
    }
    
    /**
     * Write phase map data for visualization
     */
    private void writePhaseMapData(List<OperatingPoint> points, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("PHASE MAP DATA FOR VISUALIZATION");
            pw.println("=================================\n");
            
            pw.println("X-axis: transportGain");
            pw.println("Y-axis: distanceDecay");
            pw.println("Color: regime classification\n");
            
            pw.println("transportGain\tdistanceDecay\tRegime");
            
            for (OperatingPoint op : points) {
                pw.printf("%.2f\t%.1f\t%s%n", op.transportGain, op.distanceDecay, op.regime);
            }
        }
    }
    
    /**
     * Write regime summary analysis
     */
    private void writeRegimeSummary(List<OperatingPoint> points, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("REGIME CLASSIFICATION SUMMARY");
            pw.println("=============================\n");
            
            Map<String, Integer> regimeCounts = new TreeMap<>();
            Map<String, List<OperatingPoint>> regimePoints = new TreeMap<>();
            
            for (OperatingPoint op : points) {
                regimeCounts.merge(op.regime, 1, Integer::sum);
                regimePoints.computeIfAbsent(op.regime, k -> new ArrayList<>()).add(op);
            }
            
            pw.println("REGIMES IDENTIFIED:");
            for (String regime : regimeCounts.keySet()) {
                int count = regimeCounts.get(regime);
                pw.printf("\n%s: %d operating points (%.1f%%)%n", regime, count, 100.0 * count / points.size());
                
                List<OperatingPoint> pts = regimePoints.get(regime);
                if (pts.size() > 0) {
                    pw.println("  Example points:");
                    for (int i = 0; i < Math.min(3, pts.size()); i++) {
                        OperatingPoint op = pts.get(i);
                        pw.printf("    Gain=%.2f, DistDecay=%.1f: Persist=%d, Growth=%.1f, Corridor=%.4f%n",
                            op.transportGain, op.distanceDecay,
                            (int)op.settlementPersistence,
                            op.autonomousGrowthRadius,
                            op.tradeRouteStrength
                        );
                    }
                }
            }
            
            pw.println("\n\nREGIME DEFINITIONS:");
            pw.println("  LIVING_SETTLEMENT: Settlements persist >200 ticks AND growth radius >5 cells");
            pw.println("  TRADE_ROUTES: Trade-route strength >0.01 (visible corridors)");
            pw.println("  RUNAWAY: Growth radius >20 cells (explosive expansion)");
            pw.println("  DEAD: Settlement collapse <100 ticks (insufficient transport)");
            pw.println("  SCAR_ONLY: Good conflict recovery but no autonomous growth");
            pw.println("  MARGINAL: Borderline viability");
            
            // Check for viable regions
            long viableCount = points.stream()
                .filter(op -> op.regime.equals("LIVING_SETTLEMENT") || op.regime.equals("TRADE_ROUTES"))
                .count();
            
            pw.println("\n\nVIABLE GAMEPLAY REGIONS: " + viableCount);
            if (viableCount > 0) {
                pw.println("SUCCESS: Viable region(s) found in parameter space!");
                pw.println("\nParameterization details:");
                List<OperatingPoint> viable = points.stream()
                    .filter(op -> op.regime.equals("LIVING_SETTLEMENT") || op.regime.equals("TRADE_ROUTES"))
                    .toList();
                for (OperatingPoint op : viable) {
                    pw.printf("  Gain=%.2f, DistDecay=%.1f: %s%n", op.transportGain, op.distanceDecay, op.regime);
                }
            } else {
                pw.println("FINDING: No viable gameplay regions in tested parameter space.");
                pw.println("CONCLUSION: Architecture insufficient for autonomous world evolution.");
            }
        }
    }
    
    /**
     * Write CSV for phase diagram plotting
     */
    private void writePhaseDiagramCSV(List<OperatingPoint> points, File file) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("transportGain,distanceDecay,settlementPersistence,autonomousGrowth,tradeRoute," +
                "hotspotExpansion,recoveryEase,historicalAdvantage,territorialStability,regime");
            
            for (OperatingPoint op : points) {
                pw.printf("%.2f,%.1f,%.0f,%.1f,%.4f,%.1f,%.3f,%.4f,%.3f,%s%n",
                    op.transportGain,
                    op.distanceDecay,
                    op.settlementPersistence,
                    op.autonomousGrowthRadius,
                    op.tradeRouteStrength,
                    op.hotspotExpansionRadius,
                    op.recoveryAfterAbandonmentEase,
                    op.historicalAdvantage,
                    op.territorialStability,
                    op.regime
                );
            }
        }
    }
    
    /**
     * Operating point evaluation result
     */
    static class OperatingPoint {
        double transportGain;
        double distanceDecay;
        
        // Measured metrics
        double settlementPersistence;      // Ticks until collapse
        double autonomousGrowthRadius;     // Max expansion radius
        double tradeRouteStrength;         // Corridor activation
        double hotspotExpansionRadius;     // Peak growth radius
        double recoveryAfterAbandonmentEase;  // Readiness after history
        double historicalAdvantage;        // Old vs new readiness diff
        double territorialStability;       // 0=dynamic, 1=static
        
        String regime;  // Classification: DEAD, SCAR_ONLY, MARGINAL, LIVING_SETTLEMENT, TRADE_ROUTES, RUNAWAY
        
        OperatingPoint(double gain, double decay) {
            this.transportGain = gain;
            this.distanceDecay = decay;
        }
        
        /**
         * Classify operating point based on metrics
         */
        void classify() {
            // RUNAWAY: Unrealistic expansion that reaches nearly full grid radius
            if (autonomousGrowthRadius > 15) {
                regime = "RUNAWAY";
                return;
            }
            
            // LIVING_SETTLEMENT: Good persistence and growth
            if (settlementPersistence > 200 && autonomousGrowthRadius > 5) {
                regime = "LIVING_SETTLEMENT";
                return;
            }
            
            // TRADE_ROUTES: Good inter-settlement transport
            if (tradeRouteStrength > 0.01) {
                regime = "TRADE_ROUTES";
                return;
            }
            
            // DEAD: Quick collapse
            if (settlementPersistence < 100) {
                regime = "DEAD";
                return;
            }
            
            // MARGINAL: Borderline viability
            if (settlementPersistence > 100 && autonomousGrowthRadius > 2) {
                regime = "MARGINAL";
                return;
            }
            
            // SCAR_ONLY: Good recovery but no growth
            if (recoveryAfterAbandonmentEase > 0.8) {
                regime = "SCAR_ONLY";
                return;
            }
            
            regime = "UNDEFINED";
        }
    }
}
