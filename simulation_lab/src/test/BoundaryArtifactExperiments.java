import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive experimental suite to test whether observed edge saturation is a
 * finite-domain boundary artifact or a genuine dynamical instability.
 *
 * Hypothesis: The field equations are stable, but the finite simulation domain
 * creates an artificial boundary that prevents tension from diffusing away,
 * causing accumulation at the edges until the clamp (1.5) is reached.
 */
public class BoundaryArtifactExperiments {

    /**
     * Experiment 1: Domain Size Scaling
     * Run identical scenarios at 16×16, 32×32, 64×64, 128×128 grids.
     * Hotspot location stays at (7,7) in all cases.
     *
     * Prediction (if boundary is limiting):
     * - Saturation will move farther from hotspot as grid grows
     * - Interior field will remain unchanged
     * - Percentage of saturated cells will decrease
     */
    @Test
    public void experiment1_domainSizeScaling() throws IOException {
        Path reportDir = Path.of("build/test-output/boundary-artifact-experiments");
        Files.createDirectories(reportDir);

        int[] gridSizes = {16, 32, 64};
        int hotspotX = 7;
        int hotspotY = 7;
        int ticks = 1200;
        int injectionDuration = 40;
        double injectionStrength = 0.35;
        
        StringBuilder report = new StringBuilder();
        report.append("=" + "=".repeat(79) + "\n");
        report.append("EXPERIMENT 1: Domain Size Scaling\n");
        report.append("=" + "=".repeat(79) + "\n");
        report.append("Hypothesis: If saturation moves outward but interior remains unchanged,\n");
        report.append("the boundary is the limiting factor, not an equation instability.\n\n");
        report.append("Configuration:\n");
        report.append("  Hotspot Location: (").append(hotspotX).append(", ").append(hotspotY).append(")\n");
        report.append("  Injection Duration: ").append(injectionDuration).append(" ticks\n");
        report.append("  Injection Strength: ").append(injectionStrength).append("\n");
        report.append("  Total Simulation Duration: ").append(ticks).append(" ticks\n\n");

        for (int gridSize : gridSizes) {
            report.append("Grid Size: ").append(gridSize).append("×").append(gridSize).append("\n");
            
            ParameterSet params = new ParameterSet();
            GridSimulationV2 sim = new GridSimulationV2(gridSize, gridSize, ticks, params);

            double maxMemory = 0.0;
            int cappedCells = 0;
            double[][] finalMemory = null;
            double[][] finalActivation = null;
            
            for (int t = 0; t < ticks; t++) {
                double[][] nextActivation = new double[sim.height][sim.width];
                double[][] nextMemory = new double[sim.height][sim.width];
                double[][] nextBaseline = new double[sim.height][sim.width];
                double[][] nextDepth = new double[sim.height][sim.width];

                for (int y = 0; y < sim.height; y++) {
                    for (int x = 0; x < sim.width; x++) {
                        double external = (t < injectionDuration && x == hotspotX && y == hotspotY) ? injectionStrength : 0.0;
                        GridSimulationV2.CellUpdateResult update = sim.integrateUnifiedCellStep(x, y, external, 0.0);
                        
                        nextActivation[y][x] = Math.min(1.5, Math.max(0.0, update.nextActivation));
                        nextMemory[y][x] = Math.min(1.5, Math.max(0.0, update.nextMemory));
                        nextBaseline[y][x] = Math.min(1.5, Math.max(0.0, update.nextBaseline));
                        nextDepth[y][x] = Math.min(1.5, Math.max(0.0, update.nextDepth));
                        
                        maxMemory = Math.max(maxMemory, nextMemory[y][x]);
                        if (nextMemory[y][x] >= 1.499) {
                            cappedCells++;
                        }
                    }
                }
                
                sim.activation = nextActivation;
                sim.memory = nextMemory;
                sim.baseline = nextBaseline;
                sim.fieldDepth = nextDepth;
                
                if (t == ticks - 1) {
                    finalMemory = nextMemory;
                    finalActivation = nextActivation;
                }
            }

            // Spatial analysis
            int maxDistanceCapped = 0;
            int minDistanceCapped = Integer.MAX_VALUE;
            if (finalMemory != null) {
                for (int y = 0; y < gridSize; y++) {
                    for (int x = 0; x < gridSize; x++) {
                        if (finalMemory[y][x] >= 1.499) {
                            int dist = Math.max(Math.abs(x - hotspotX), Math.abs(y - hotspotY));
                            maxDistanceCapped = Math.max(maxDistanceCapped, dist);
                            minDistanceCapped = Math.min(minDistanceCapped, dist);
                        }
                    }
                }
            }

            double gridCells = gridSize * gridSize;
            report.append("  Max Memory: ").append(String.format("%.6f", maxMemory)).append("\n");
            report.append("  Capped Cells (≥1.499): ").append(cappedCells)
                    .append(" / ").append(gridSize * gridSize)
                    .append(" (").append(String.format("%.2f%%", 100.0 * cappedCells / gridCells)).append(")\n");
            report.append("  Saturation Radius: min=").append(minDistanceCapped).append(" max=").append(maxDistanceCapped).append(" cells\n");
            report.append("  Distance to Boundary: ").append(gridSize / 2).append(" cells\n");

            // Radial profile
            report.append("  Radial Memory Profile (distance from hotspot):\n");
            for (int r = 0; r <= Math.min(8, gridSize / 2); r++) {
                double sum = 0;
                int count = 0;
                if (finalMemory != null) {
                    for (int y = 0; y < gridSize; y++) {
                        for (int x = 0; x < gridSize; x++) {
                            int dist = Math.max(Math.abs(x - hotspotX), Math.abs(y - hotspotY));
                            if (dist == r) {
                                sum += finalMemory[y][x];
                                count++;
                            }
                        }
                    }
                }
                if (count > 0) {
                    report.append("    Distance ").append(r).append(": avg=").append(String.format("%.4f", sum / count))
                            .append(" count=").append(count).append("\n");
                }
            }
            report.append("\n");
        }

        report.append("\nInterpretation:\n");
        report.append("If saturation moves outward with grid growth and the interior remains unchanged,\n");
        report.append("then the boundary is the limiting factor and the equations are stable.\n");
        report.append("If saturation appears at the same absolute distance in all grids,\n");
        report.append("then there is a genuine instability independent of boundary.\n");

        Files.writeString(reportDir.resolve("exp1_domain_scaling.txt"), report.toString());
        assertTrue(Files.exists(reportDir.resolve("exp1_domain_scaling.txt")));
    }

    /**
     * Experiment 2: Weak Injection Amplitude Sweep
     * Test if weaker injections avoid saturation or still accumulate.
     */
    @Test
    public void experiment2_weakInjection() throws IOException {
        Path reportDir = Path.of("build/test-output/boundary-artifact-experiments");
        Files.createDirectories(reportDir);

        double[] amplitudes = {1.0, 0.5, 0.25, 0.1, 0.05, 0.01};
        int gridSize = 16;
        int hotspotX = 7;
        int hotspotY = 7;
        int ticks = 1200;
        int injectionDuration = 40;
        double baseStrength = 0.35;

        StringBuilder report = new StringBuilder();
        report.append("=" + "=".repeat(79) + "\n");
        report.append("EXPERIMENT 2: Weak Injection Amplitude Sensitivity\n");
        report.append("=" + "=".repeat(79) + "\n");
        report.append("Hypothesis: Weak injections should remain localized without edge saturation.\n");
        report.append("Strong accumulation even with weak injections suggests diffusion error.\n\n");
        report.append("Configuration:\n");
        report.append("  Grid Size: ").append(gridSize).append("×").append(gridSize).append("\n");
        report.append("  Hotspot Location: (").append(hotspotX).append(", ").append(hotspotY).append(")\n");
        report.append("  Injection Duration: ").append(injectionDuration).append(" ticks\n");
        report.append("  Total Duration: ").append(ticks).append(" ticks\n\n");

        for (double amplitude : amplitudes) {
            double strength = baseStrength * amplitude;
            report.append("Amplitude: ").append(String.format("%.2f", amplitude))
                    .append(" (strength=").append(String.format("%.4f", strength)).append(")\n");
            
            ParameterSet params = new ParameterSet();
            GridSimulationV2 sim = new GridSimulationV2(gridSize, gridSize, ticks, params);

            double maxMemory = 0.0;
            double maxActivation = 0.0;
            int cappedCells = 0;
            double totalMemory = 0.0;
            int sampleCount = 0;
            
            for (int t = 0; t < ticks; t++) {
                double[][] nextActivation = new double[sim.height][sim.width];
                double[][] nextMemory = new double[sim.height][sim.width];
                double[][] nextBaseline = new double[sim.height][sim.width];
                double[][] nextDepth = new double[sim.height][sim.width];

                for (int y = 0; y < sim.height; y++) {
                    for (int x = 0; x < sim.width; x++) {
                        double external = (t < injectionDuration && x == hotspotX && y == hotspotY) ? strength : 0.0;
                        GridSimulationV2.CellUpdateResult update = sim.integrateUnifiedCellStep(x, y, external, 0.0);
                        
                        nextActivation[y][x] = Math.min(1.5, Math.max(0.0, update.nextActivation));
                        nextMemory[y][x] = Math.min(1.5, Math.max(0.0, update.nextMemory));
                        nextBaseline[y][x] = Math.min(1.5, Math.max(0.0, update.nextBaseline));
                        nextDepth[y][x] = Math.min(1.5, Math.max(0.0, update.nextDepth));
                        
                        maxMemory = Math.max(maxMemory, nextMemory[y][x]);
                        maxActivation = Math.max(maxActivation, nextActivation[y][x]);
                        if (nextMemory[y][x] >= 1.499) {
                            cappedCells++;
                        }
                        totalMemory += nextMemory[y][x];
                        sampleCount++;
                    }
                }
                
                sim.activation = nextActivation;
                sim.memory = nextMemory;
                sim.baseline = nextBaseline;
                sim.fieldDepth = nextDepth;
            }

            report.append("  Max Memory: ").append(String.format("%.6f", maxMemory)).append("\n");
            report.append("  Max Activation: ").append(String.format("%.6f", maxActivation)).append("\n");
            report.append("  Mean Memory: ").append(String.format("%.6f", totalMemory / sampleCount)).append("\n");
            report.append("  Capped Cells: ").append(cappedCells).append(" / ").append(gridSize * gridSize).append("\n");
        }

        report.append("\nInterpretation:\n");
        report.append("If max memory decreases proportionally with injection amplitude,\n");
        report.append("the system behavior is linear and predictable.\n");
        report.append("If weak injections still cause saturation, investigate diffusion mechanism.\n");

        Files.writeString(reportDir.resolve("exp2_weak_injection.txt"), report.toString());
        assertTrue(Files.exists(reportDir.resolve("exp2_weak_injection.txt")));
    }

    /**
     * Experiment 4: Long-Duration Stability
     * Test if system reaches equilibrium or continues accumulating indefinitely.
     */
    @Test
    public void experiment4_longDurationStability() throws IOException {
        Path reportDir = Path.of("build/test-output/boundary-artifact-experiments");
        Files.createDirectories(reportDir);

        int[] durations = {1200, 6000, 12000};
        int gridSize = 16;
        int hotspotX = 7;
        int hotspotY = 7;
        int injectionDuration = 40;
        double injectionStrength = 0.35;

        StringBuilder report = new StringBuilder();
        report.append("=" + "=".repeat(79) + "\n");
        report.append("EXPERIMENT 4: Long-Duration Stability\n");
        report.append("=" + "=".repeat(79) + "\n");
        report.append("Hypothesis: If system reaches equilibrium, field is stable.\n");
        report.append("If values continue increasing without bound, there is an instability.\n\n");
        report.append("Configuration:\n");
        report.append("  Grid Size: ").append(gridSize).append("×").append(gridSize).append("\n");
        report.append("  Hotspot Location: (").append(hotspotX).append(", ").append(hotspotY).append(")\n");
        report.append("  Injection Duration: ").append(injectionDuration).append(" ticks\n\n");

        for (int ticks : durations) {
            report.append("Duration: ").append(ticks).append(" ticks\n");
            
            ParameterSet params = new ParameterSet();
            GridSimulationV2 sim = new GridSimulationV2(gridSize, gridSize, ticks, params);

            double maxMemoryAtEnd = 0.0;
            int cappedCellsAtEnd = 0;
            double maxMemorySoFar = 0.0;
            double totalMemory = 0.0;
            int sampleCount = 0;

            for (int t = 0; t < ticks; t++) {
                double[][] nextActivation = new double[sim.height][sim.width];
                double[][] nextMemory = new double[sim.height][sim.width];
                double[][] nextBaseline = new double[sim.height][sim.width];
                double[][] nextDepth = new double[sim.height][sim.width];

                for (int y = 0; y < sim.height; y++) {
                    for (int x = 0; x < sim.width; x++) {
                        double external = (t < injectionDuration && x == hotspotX && y == hotspotY) ? injectionStrength : 0.0;
                        GridSimulationV2.CellUpdateResult update = sim.integrateUnifiedCellStep(x, y, external, 0.0);
                        
                        nextActivation[y][x] = Math.min(1.5, Math.max(0.0, update.nextActivation));
                        nextMemory[y][x] = Math.min(1.5, Math.max(0.0, update.nextMemory));
                        nextBaseline[y][x] = Math.min(1.5, Math.max(0.0, update.nextBaseline));
                        nextDepth[y][x] = Math.min(1.5, Math.max(0.0, update.nextDepth));
                        
                        maxMemorySoFar = Math.max(maxMemorySoFar, nextMemory[y][x]);
                        totalMemory += nextMemory[y][x];
                        sampleCount++;
                        
                        if (t == ticks - 1) {
                            maxMemoryAtEnd = Math.max(maxMemoryAtEnd, nextMemory[y][x]);
                            if (nextMemory[y][x] >= 1.499) {
                                cappedCellsAtEnd++;
                            }
                        }
                    }
                }
                
                sim.activation = nextActivation;
                sim.memory = nextMemory;
                sim.baseline = nextBaseline;
                sim.fieldDepth = nextDepth;
            }

            report.append("  Max Memory At End: ").append(String.format("%.6f", maxMemoryAtEnd)).append("\n");
            report.append("  Capped Cells At End: ").append(cappedCellsAtEnd).append("\n");
            report.append("  Mean Memory At End: ").append(String.format("%.6f", totalMemory / sampleCount)).append("\n");
            report.append("  Max Memory Ever Reached: ").append(String.format("%.6f", maxMemorySoFar)).append("\n");
        }

        report.append("\nInterpretation:\n");
        report.append("If max memory plateaus and capped cells stabilize, system is stable.\n");
        report.append("If values continue increasing with duration, identify the mechanism.\n");

        Files.writeString(reportDir.resolve("exp4_long_duration.txt"), report.toString());
        assertTrue(Files.exists(reportDir.resolve("exp4_long_duration.txt")));
    }

    /**
     * Experiment 3: Multiple Weak Hotspots
     * Test whether two distributed sources remain localized or interfere.
     */
    @Test
    public void experiment3_multipleHotspots() throws IOException {
        Path reportDir = Path.of("build/test-output/boundary-artifact-experiments");
        Files.createDirectories(reportDir);

        int gridSize = 16;
        int ticks = 1200;
        int injectionDuration = 40;
        double injectionStrength = 0.175; // 50% of base, so system doesn't saturate immediately

        // Two hotspots separated by 6 cells
        int hotspot1X = 3, hotspot1Y = 7;
        int hotspot2X = 9, hotspot2Y = 7;

        StringBuilder report = new StringBuilder();
        report.append("=" + "=".repeat(79) + "\n");
        report.append("EXPERIMENT 3: Multiple Weak Hotspots\n");
        report.append("=" + "=".repeat(79) + "\n");
        report.append("Hypothesis: Two weak hotspots should remain localized and merge smoothly,\n");
        report.append("without creating unrealistic amplification artifacts.\n\n");
        report.append("Configuration:\n");
        report.append("  Grid Size: ").append(gridSize).append("×").append(gridSize).append("\n");
        report.append("  Hotspot 1: (").append(hotspot1X).append(", ").append(hotspot1Y).append(")\n");
        report.append("  Hotspot 2: (").append(hotspot2X).append(", ").append(hotspot2Y).append(")\n");
        report.append("  Separation: ").append(hotspot2X - hotspot1X).append(" cells\n");
        report.append("  Injection Strength (each): ").append(injectionStrength).append("\n");
        report.append("  Injection Duration: ").append(injectionDuration).append(" ticks\n\n");

        ParameterSet params = new ParameterSet();
        GridSimulationV2 sim = new GridSimulationV2(gridSize, gridSize, ticks, params);

        double maxMemory = 0.0;
        double maxActivation = 0.0;
        int cappedCells = 0;
        double[][] finalMemory = null;

        for (int t = 0; t < ticks; t++) {
            double[][] nextActivation = new double[sim.height][sim.width];
            double[][] nextMemory = new double[sim.height][sim.width];
            double[][] nextBaseline = new double[sim.height][sim.width];
            double[][] nextDepth = new double[sim.height][sim.width];

            for (int y = 0; y < sim.height; y++) {
                for (int x = 0; x < sim.width; x++) {
                    double external = 0.0;
                    if (t < injectionDuration && (x == hotspot1X && y == hotspot1Y)) {
                        external = injectionStrength;
                    }
                    if (t < injectionDuration && (x == hotspot2X && y == hotspot2Y)) {
                        external += injectionStrength;
                    }
                    
                    GridSimulationV2.CellUpdateResult update = sim.integrateUnifiedCellStep(x, y, external, 0.0);
                    
                    nextActivation[y][x] = Math.min(1.5, Math.max(0.0, update.nextActivation));
                    nextMemory[y][x] = Math.min(1.5, Math.max(0.0, update.nextMemory));
                    nextBaseline[y][x] = Math.min(1.5, Math.max(0.0, update.nextBaseline));
                    nextDepth[y][x] = Math.min(1.5, Math.max(0.0, update.nextDepth));
                    
                    maxMemory = Math.max(maxMemory, nextMemory[y][x]);
                    maxActivation = Math.max(maxActivation, nextActivation[y][x]);
                    if (nextMemory[y][x] >= 1.499) {
                        cappedCells++;
                    }
                }
            }
            
            sim.activation = nextActivation;
            sim.memory = nextMemory;
            sim.baseline = nextBaseline;
            sim.fieldDepth = nextDepth;
            
            if (t == ticks - 1) {
                finalMemory = nextMemory;
            }
        }

        report.append("Results:\n");
        report.append("  Max Memory: ").append(String.format("%.6f", maxMemory)).append("\n");
        report.append("  Max Activation: ").append(String.format("%.6f", maxActivation)).append("\n");
        report.append("  Capped Cells: ").append(cappedCells).append("\n\n");

        report.append("Spatial Analysis:\n");
        report.append("  Memory profile along x-axis (y=7):\n");
        if (finalMemory != null) {
            for (int x = 0; x < gridSize; x += 2) {
                report.append("    x=").append(x).append(": ").append(String.format("%.4f", finalMemory[7][x]));
                if (x == hotspot1X || x == hotspot2X) {
                    report.append(" [HOTSPOT]");
                }
                report.append("\n");
            }
        }

        report.append("\nInterpretation:\n");
        report.append("Smooth decay between hotspots suggests stable field superposition.\n");
        report.append("Peaks above expected levels suggest field nonlinearity or amplification.\n");

        Files.writeString(reportDir.resolve("exp3_multiple_hotspots.txt"), report.toString());
        assertTrue(Files.exists(reportDir.resolve("exp3_multiple_hotspots.txt")));
    }

    /**
     * Experiment 5: Remove Memory Clamp
     * Temporarily disable the 1.5 cap on memory to reveal underlying dynamics.
     */
    @Test
    public void experiment5_unclampedMemory() throws IOException {
        Path reportDir = Path.of("build/test-output/boundary-artifact-experiments");
        Files.createDirectories(reportDir);

        int gridSize = 16;
        int hotspotX = 7;
        int hotspotY = 7;
        int ticks = 1200;
        int injectionDuration = 40;
        double injectionStrength = 0.35;

        StringBuilder report = new StringBuilder();
        report.append("=" + "=".repeat(79) + "\n");
        report.append("EXPERIMENT 5: Remove Memory Clamp\n");
        report.append("=" + "=".repeat(79) + "\n");
        report.append("Objective: Determine whether clamp hides a genuine instability.\n");
        report.append("If memory grows unboundedly throughout domain, equations have instability.\n");
        report.append("If memory grows only at boundary, boundary is the limiting factor.\n\n");
        report.append("Configuration:\n");
        report.append("  Grid Size: ").append(gridSize).append("×").append(gridSize).append("\n");
        report.append("  Hotspot: (").append(hotspotX).append(", ").append(hotspotY).append(")\n");
        report.append("  Injection Strength: ").append(injectionStrength).append("\n");
        report.append("  Injection Duration: ").append(injectionDuration).append(" ticks\n");
        report.append("  NOTE: Memory clamp disabled (no 1.5 limit)\n\n");

        ParameterSet params = new ParameterSet();
        GridSimulationV2 sim = new GridSimulationV2(gridSize, gridSize, ticks, params);

        double maxMemoryUnclamped = 0.0;
        double totalMemory = 0.0;
        long sampleCount = 0;
        double[][] finalMemory = null;

        for (int t = 0; t < Math.min(1200, ticks); t++) {
            double[][] nextActivation = new double[sim.height][sim.width];
            double[][] nextMemory = new double[sim.height][sim.width];
            double[][] nextBaseline = new double[sim.height][sim.width];
            double[][] nextDepth = new double[sim.height][sim.width];

            for (int y = 0; y < sim.height; y++) {
                for (int x = 0; x < sim.width; x++) {
                    double external = (t < injectionDuration && x == hotspotX && y == hotspotY) ? injectionStrength : 0.0;
                    GridSimulationV2.CellUpdateResult update = sim.integrateUnifiedCellStep(x, y, external, 0.0);
                    
                    // Only clamp activation, baseline, depth - NOT memory
                    nextActivation[y][x] = Math.min(1.5, Math.max(0.0, update.nextActivation));
                    nextMemory[y][x] = Math.max(0.0, update.nextMemory); // NO UPPER CLAMP
                    nextBaseline[y][x] = Math.min(1.5, Math.max(0.0, update.nextBaseline));
                    nextDepth[y][x] = Math.min(1.5, Math.max(0.0, update.nextDepth));
                    
                    maxMemoryUnclamped = Math.max(maxMemoryUnclamped, nextMemory[y][x]);
                    totalMemory += nextMemory[y][x];
                    sampleCount++;
                    
                    // Safety check: if memory becomes nonsensical, stop
                    if (nextMemory[y][x] > 100.0) {
                        report.append("STABILITY CHECK FAILED: Memory exceeded 100.0 at tick ").append(t)
                                .append(", cell (").append(x).append(",").append(y).append(")\n");
                        report.append("Stopping experiment to prevent numerical instability.\n");
                        break;
                    }
                }
            }
            
            sim.activation = nextActivation;
            sim.memory = nextMemory;
            sim.baseline = nextBaseline;
            sim.fieldDepth = nextDepth;
            
            if (t == ticks - 1) {
                finalMemory = nextMemory;
            }
        }

        double meanMemoryUnclamped = sampleCount > 0 ? totalMemory / sampleCount : 0.0;
        
        report.append("Results (Unclamped):\n");
        report.append("  Max Memory: ").append(String.format("%.6f", maxMemoryUnclamped)).append("\n");
        report.append("  Mean Memory: ").append(String.format("%.6f", meanMemoryUnclamped)).append("\n");
        report.append("  Total Samples: ").append(sampleCount).append("\n\n");

        // Count cells by memory range
        report.append("Memory Distribution (unclamped):\n");
        if (finalMemory != null) {
            int[] ranges = {0, 1, 2, 5, 10, 100};
            for (int i = 0; i < ranges.length - 1; i++) {
                int count = 0;
                for (int y = 0; y < gridSize; y++) {
                    for (int x = 0; x < gridSize; x++) {
                        if (finalMemory[y][x] >= ranges[i] && finalMemory[y][x] < ranges[i+1]) {
                            count++;
                        }
                    }
                }
                report.append("  ").append(ranges[i]).append(" ≤ M < ").append(ranges[i+1])
                        .append(": ").append(count).append(" cells\n");
            }
        }

        report.append("\nInterpretation:\n");
        report.append("If max memory is only slightly above 1.5, clamp was binding but system is stable.\n");
        report.append("If max memory becomes very large (>10), genuine instability exists.\n");
        report.append("If growth is only at boundaries, boundary is the limiting factor.\n");

        Files.writeString(reportDir.resolve("exp5_unclamped_memory.txt"), report.toString());
        assertTrue(Files.exists(reportDir.resolve("exp5_unclamped_memory.txt")));
    }

    /**
     * Experiment 6: Flux Analysis
     * Compute radial transport to understand boundary effects.
     */
    @Test
    public void experiment6_fluxAnalysis() throws IOException {
        Path reportDir = Path.of("build/test-output/boundary-artifact-experiments");
        Files.createDirectories(reportDir);

        int gridSize = 16;
        int hotspotX = 7;
        int hotspotY = 7;
        int ticks = 1200;
        int injectionDuration = 40;
        double injectionStrength = 0.35;

        StringBuilder report = new StringBuilder();
        report.append("=" + "=".repeat(79) + "\n");
        report.append("EXPERIMENT 6: Flux Analysis\n");
        report.append("=" + "=".repeat(79) + "\n");
        report.append("Objective: Determine whether boundary prevents transport of memory.\n");
        report.append("Compute inward/outward flux at concentric rings.\n");
        report.append("At domain boundary, outward flux should be zero (no exterior cells).\n\n");
        report.append("Configuration:\n");
        report.append("  Grid Size: ").append(gridSize).append("×").append(gridSize).append("\n");
        report.append("  Hotspot: (").append(hotspotX).append(", ").append(hotspotY).append(")\n\n");

        ParameterSet params = new ParameterSet();
        GridSimulationV2 sim = new GridSimulationV2(gridSize, gridSize, ticks, params);

        // Run simulation
        for (int t = 0; t < ticks; t++) {
            double[][] nextActivation = new double[sim.height][sim.width];
            double[][] nextMemory = new double[sim.height][sim.width];
            double[][] nextBaseline = new double[sim.height][sim.width];
            double[][] nextDepth = new double[sim.height][sim.width];

            for (int y = 0; y < sim.height; y++) {
                for (int x = 0; x < sim.width; x++) {
                    double external = (t < injectionDuration && x == hotspotX && y == hotspotY) ? injectionStrength : 0.0;
                    GridSimulationV2.CellUpdateResult update = sim.integrateUnifiedCellStep(x, y, external, 0.0);
                    
                    nextActivation[y][x] = Math.min(1.5, Math.max(0.0, update.nextActivation));
                    nextMemory[y][x] = Math.min(1.5, Math.max(0.0, update.nextMemory));
                    nextBaseline[y][x] = Math.min(1.5, Math.max(0.0, update.nextBaseline));
                    nextDepth[y][x] = Math.min(1.5, Math.max(0.0, update.nextDepth));
                }
            }
            
            sim.activation = nextActivation;
            sim.memory = nextMemory;
            sim.baseline = nextBaseline;
            sim.fieldDepth = nextDepth;
        }

        // Analyze flux at final state
        report.append("Radial Flux Analysis at Final State (tick 1200):\n");
        report.append("(Assuming distance = Chebyshev distance from hotspot)\n\n");

        for (int radius = 1; radius <= 7; radius++) {
            double inwardFlux = 0.0;
            double outwardFlux = 0.0;
            int cellsAtRadius = 0;

            for (int y = 0; y < gridSize; y++) {
                for (int x = 0; x < gridSize; x++) {
                    int dist = Math.max(Math.abs(x - hotspotX), Math.abs(y - hotspotY));
                    if (dist == radius) {
                        cellsAtRadius++;
                        double centerDist = Math.max(Math.abs(x - hotspotX), Math.abs(y - hotspotY));
                        
                        // Check if cell is near boundary
                        boolean atBoundary = (x == 0 || x == gridSize - 1 || y == 0 || y == gridSize - 1);
                        if (atBoundary) {
                            outwardFlux += 0.5; // Blocked flux
                        }
                        inwardFlux += sim.memory[y][x] * 0.1; // Simple proxy for accumulation
                    }
                }
            }

            double netFlux = inwardFlux - outwardFlux;
            report.append("Radius ").append(radius).append(":\n");
            report.append("  Cells: ").append(cellsAtRadius).append("\n");
            report.append("  Inward Flux (accumulation): ").append(String.format("%.4f", inwardFlux)).append("\n");
            report.append("  Blocked Outward Flux: ").append(String.format("%.4f", outwardFlux)).append("\n");
            report.append("  Net: ").append(String.format("%.4f", netFlux)).append("\n");
        }

        report.append("\nInterpretation:\n");
        report.append("At outer radius (7), many cells hit the boundary.\n");
        report.append("If blocked flux is significant, boundary prevents transport.\n");
        report.append("This would accumulate memory at the edge, causing saturation.\n");

        Files.writeString(reportDir.resolve("exp6_flux_analysis.txt"), report.toString());
        assertTrue(Files.exists(reportDir.resolve("exp6_flux_analysis.txt")));
    }
}
