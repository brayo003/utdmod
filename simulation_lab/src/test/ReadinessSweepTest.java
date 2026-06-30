import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ReadinessSweepTest {

    private static class ReadinessConfig {
        String variantKey;
        String variantLabel;
        double memoryScale;
        double fieldScale;
        double transitionWidth;

        ReadinessConfig(String variantKey, String variantLabel, double memoryScale, double fieldScale, double transitionWidth) {
            this.variantKey = variantKey;
            this.variantLabel = variantLabel;
            this.memoryScale = memoryScale;
            this.fieldScale = fieldScale;
            this.transitionWidth = transitionWidth;
        }
    }

    private static class SweepResult {
        ReadinessConfig config;
        double readinessAtLowMemory;
        double readinessAtMediumMemory;
        double readinessAtHighMemory;
        double readinessAtLowDepth;
        double readinessAtMediumDepth;
        double readinessAtHighDepth;
        double peakReadiness;
        double minReadiness;
    }

    @Test
    void sweepReadinessLaws() throws Exception {
        Path outDir = Paths.get("run/readiness_sweep");
        Files.createDirectories(outDir);

        List<ReadinessConfig> configs = generateReadinessConfigs();
        List<SweepResult> results = new ArrayList<>();

        for (ReadinessConfig config : configs) {
            SweepResult result = evaluateReadinessConfig(config);
            if (result != null) {
                results.add(result);
            }
        }

        writeSweepSummaryTable(outDir, results);
        writeVariantComparison(outDir, results);
        writeCharacterizationReport(outDir, results);
    }

    private List<ReadinessConfig> generateReadinessConfigs() {
        List<ReadinessConfig> configs = new ArrayList<>();

        // Variant 1: Exponential retention - sweep memoryScale
        double[] expMemScales = {0.2, 0.5, 1.0, 2.0, 5.0, 10.0};
        for (double scale : expMemScales) {
            configs.add(new ReadinessConfig("variant_1_exponential_retention",
                    "Exponential(m=" + scale + ")", scale, 1.0, 1.0));
        }

        // Variant 2: Rational retention - sweep memoryScale
        double[] ratMemScales = {0.2, 0.5, 1.0, 2.0, 5.0, 10.0};
        for (double scale : ratMemScales) {
            configs.add(new ReadinessConfig("variant_2_rational_retention",
                    "Rational(m=" + scale + ")", scale, 1.0, 1.0));
        }

        // Variant 3: Logistic transition - sweep memoryScale and transitionWidth
        double[] logMemScales = {0.5, 1.0, 2.0, 5.0};
        double[] logTransWidths = {0.2, 0.5, 1.0, 2.0};
        for (double mscale : logMemScales) {
            for (double twidth : logTransWidths) {
                configs.add(new ReadinessConfig("variant_3_logistic_transition",
                        "Logistic(m=" + mscale + ",w=" + twidth + ")", mscale, 1.0, twidth));
            }
        }

        // Variant 4: Field-depth retention - sweep fieldScale
        double[] fieldScales = {0.5, 1.0, 2.0, 5.0, 10.0, 20.0};
        for (double scale : fieldScales) {
            configs.add(new ReadinessConfig("variant_4_field_depth_retention",
                    "FieldDepth(f=" + scale + ")", 1.0, scale, 1.0));
        }

        // Variant 5: Combined memory + field-depth - sweep both scales
        double[] combMemScales = {0.5, 1.0, 2.0, 5.0};
        double[] combFieldScales = {0.5, 1.0, 2.0, 5.0};
        for (double mscale : combMemScales) {
            for (double fscale : combFieldScales) {
                configs.add(new ReadinessConfig("variant_5_combined_memory_field_depth",
                        "Combined(m=" + mscale + ",f=" + fscale + ")", mscale, fscale, 1.0));
            }
        }

        return configs;
    }

    private SweepResult evaluateReadinessConfig(ReadinessConfig config) {
        SweepResult result = new SweepResult();
        result.config = config;

        // Evaluate readiness at different memory levels (variant 1,2,3,5)
        result.readinessAtLowMemory = computeReadinessWithConfig(config, 0.1, 1.0);
        result.readinessAtMediumMemory = computeReadinessWithConfig(config, 1.0, 1.0);
        result.readinessAtHighMemory = computeReadinessWithConfig(config, 5.0, 1.0);

        // Evaluate readiness at different depth levels (variant 4,5)
        result.readinessAtLowDepth = computeReadinessWithConfig(config, 1.0, 0.1);
        result.readinessAtMediumDepth = computeReadinessWithConfig(config, 1.0, 1.0);
        result.readinessAtHighDepth = computeReadinessWithConfig(config, 1.0, 5.0);

        // Find peak and minimum across a range
        result.peakReadiness = 1.0; // max possible
        result.minReadiness = Double.POSITIVE_INFINITY;
        for (double m = 0.0; m <= 10.0; m += 0.5) {
            for (double d = 0.0; d <= 10.0; d += 0.5) {
                double r = computeReadinessWithConfig(config, m, d);
                result.peakReadiness = Math.max(result.peakReadiness, r);
                result.minReadiness = Math.min(result.minReadiness, r);
            }
        }
        if (result.minReadiness == Double.POSITIVE_INFINITY) {
            result.minReadiness = 0.0;
        }

        return result;
    }

    private double computeReadinessWithConfig(ReadinessConfig config, double memory, double depth) {
        double m = Math.max(0.0, memory);
        double f = Math.max(0.0, depth);
        switch (config.variantKey) {
            case "variant_1_exponential_retention":
                return Math.exp(-m / config.memoryScale);
            case "variant_2_rational_retention":
                return 1.0 / (1.0 + Math.pow(m / config.memoryScale, 2));
            case "variant_3_logistic_transition":
                return 1.0 / (1.0 + Math.exp((m - config.memoryScale) / config.transitionWidth));
            case "variant_4_field_depth_retention":
                return Math.exp(-f / config.fieldScale);
            case "variant_5_combined_memory_field_depth":
            default:
                return Math.exp(-m / config.memoryScale) * Math.exp(-f / config.fieldScale);
        }
    }

    private void writeSweepSummaryTable(Path outDir, List<SweepResult> results) throws IOException {
        Path tableFile = outDir.resolve("readiness_parameter_sweep.txt");
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(tableFile))) {
            writer.println("Readiness Parameter Sweep - Characterization");
            writer.println("==========================================");
            writer.println();
            writer.println("Variant | Label | MemScale | FieldScale | TransWidth | R@m=0.1 | R@m=1.0 | R@m=5.0 | R@d=0.1 | R@d=1.0 | R@d=5.0 | Peak | Min");
            for (SweepResult result : results) {
                writer.println(String.format("%s | %s | %.2f | %.2f | %.2f | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f | %.6f",
                        result.config.variantKey.substring(0, Math.min(10, result.config.variantKey.length())),
                        result.config.variantLabel,
                        result.config.memoryScale,
                        result.config.fieldScale,
                        result.config.transitionWidth,
                        result.readinessAtLowMemory,
                        result.readinessAtMediumMemory,
                        result.readinessAtHighMemory,
                        result.readinessAtLowDepth,
                        result.readinessAtMediumDepth,
                        result.readinessAtHighDepth,
                        result.peakReadiness,
                        result.minReadiness));
            }
        }
    }

    private void writeVariantComparison(Path outDir, List<SweepResult> results) throws IOException {
        Path compFile = outDir.resolve("variant_readiness_comparison.txt");
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(compFile))) {
            writer.println("Readiness Variant Comparison");
            writer.println("=============================");
            writer.println();
            writer.println("Summary of readiness behavior for each variant:");
            writer.println();

            Map<String, List<SweepResult>> byVariant = new TreeMap<>();
            for (SweepResult result : results) {
                String key = result.config.variantKey;
                byVariant.computeIfAbsent(key, k -> new ArrayList<>()).add(result);
            }

            for (Map.Entry<String, List<SweepResult>> entry : byVariant.entrySet()) {
                List<SweepResult> variantResults = entry.getValue();
                if (variantResults.isEmpty()) continue;

                writer.println("Variant: " + entry.getKey());
                writer.println("  Behavior: ");
                
                if (entry.getKey().contains("exponential")) {
                    writer.println("    - Exponential decay in readiness with increasing memory");
                    writer.println("    - R(m) = exp(-m/scale)");
                } else if (entry.getKey().contains("rational")) {
                    writer.println("    - Rational decay in readiness with increasing memory");
                    writer.println("    - R(m) = 1/(1 + (m/scale)^2)");
                } else if (entry.getKey().contains("logistic")) {
                    writer.println("    - Logistic transition in readiness at memory threshold");
                    writer.println("    - R(m) = 1/(1 + exp((m - scale)/width))");
                } else if (entry.getKey().contains("field_depth")) {
                    writer.println("    - Exponential decay in readiness with increasing field depth");
                    writer.println("    - R(f) = exp(-f/scale)");
                } else if (entry.getKey().contains("combined")) {
                    writer.println("    - Product of exponential decays in memory and field depth");
                    writer.println("    - R(m,f) = exp(-m/mscale) * exp(-f/fscale)");
                }

                for (SweepResult result : variantResults) {
                    writer.println("  Config: " + result.config.variantLabel);
                    writer.println("    R at memory=0.1: " + String.format("%.6f", result.readinessAtLowMemory));
                    writer.println("    R at memory=1.0: " + String.format("%.6f", result.readinessAtMediumMemory));
                    writer.println("    R at memory=5.0: " + String.format("%.6f", result.readinessAtHighMemory));
                    writer.println("    Range: " + String.format("[%.6f, %.6f]", result.minReadiness, result.peakReadiness));
                }
                writer.println();
            }
        }
    }

    private void writeCharacterizationReport(Path outDir, List<SweepResult> results) throws IOException {
        Path reportFile = outDir.resolve("readiness_characterization.txt");
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(reportFile))) {
            writer.println("Readiness Gate Characterization Report");
            writer.println("=====================================");
            writer.println();
            writer.println("Transport Law: T = SourceStrength × Readiness × DestinationCapacity × Distance × Gain");
            writer.println("Architecture: FIXED - no gates added, removed, or redesigned");
            writer.println();
            writer.println("Readiness Implementations Tested:");
            writer.println("1. Exponential retention: R(m) = exp(-m/scale)");
            writer.println("2. Rational retention: R(m) = 1/(1 + (m/scale)^2)");
            writer.println("3. Logistic transition: R(m) = 1/(1 + exp((m-scale)/width))");
            writer.println("4. Field-depth retention: R(f) = exp(-f/scale)");
            writer.println("5. Combined memory+field-depth: R(m,f) = exp(-m/scale) * exp(-f/scale)");
            writer.println();
            writer.println("Readiness Function Role:");
            writer.println("  Readiness gates the transport contribution from source to destination.");
            writer.println("  Higher readiness = higher transport throughput.");
            writer.println("  Lower readiness = suppressed transport (inhibitory effect).");
            writer.println();
            writer.println("Parameter Ranges Tested:");
            writer.println("  Memory scale: 0.2 to 10.0");
            writer.println("  Field-depth scale: 0.5 to 20.0");
            writer.println("  Logistic transition width: 0.2 to 2.0");
            writer.println();
            writer.println("Summary of Results:");
            writer.println();

            // Group by variant and describe aggregate behavior
            Map<String, List<SweepResult>> byVariant = new TreeMap<>();
            for (SweepResult result : results) {
                String key = result.config.variantKey;
                byVariant.computeIfAbsent(key, k -> new ArrayList<>()).add(result);
            }

            for (Map.Entry<String, List<SweepResult>> entry : byVariant.entrySet()) {
                List<SweepResult> variantResults = entry.getValue();
                if (variantResults.isEmpty()) continue;

                double avgReadinessLow = variantResults.stream().mapToDouble(r -> r.readinessAtLowMemory).average().orElse(0.0);
                double avgReadinessMed = variantResults.stream().mapToDouble(r -> r.readinessAtMediumMemory).average().orElse(0.0);
                double avgReadinessHigh = variantResults.stream().mapToDouble(r -> r.readinessAtHighMemory).average().orElse(0.0);

                writer.println(entry.getKey() + ":");
                writer.println("  Number of parameter sets tested: " + variantResults.size());
                writer.println("  Average readiness at m=0.1: " + String.format("%.6f", avgReadinessLow));
                writer.println("  Average readiness at m=1.0: " + String.format("%.6f", avgReadinessMed));
                writer.println("  Average readiness at m=5.0: " + String.format("%.6f", avgReadinessHigh));
                writer.println("  Characterization:");
                
                if (avgReadinessLow > 0.8 && avgReadinessMed > 0.5 && avgReadinessHigh < 0.3) {
                    writer.println("    - Moderate gating effect: retains 80%+ readiness at low memory");
                } else if (avgReadinessLow > 0.9 && avgReadinessMed > 0.8) {
                    writer.println("    - Weak gating effect: readiness stays high across memory range");
                } else if (avgReadinessLow < 0.3 && avgReadinessMed < 0.1) {
                    writer.println("    - Strong gating effect: readiness rapidly suppressed by memory");
                } else {
                    writer.println("    - Mixed gating effect across parameter space");
                }
                writer.println();
            }

            writer.println("Recommendations:");
            writer.println("  The transport architecture is frozen with the readiness gate as the primary");
            writer.println("  behavioral modulator. Parameter ranges above show how readiness scales:");
            writer.println("  - Exponential/Rational: suitable for memory-dependent suppression");
            writer.println("  - Logistic: suitable for threshold-based switching behavior");
            writer.println("  - Field-depth: suitable for structural memory effects");
            writer.println("  - Combined: suitable for multi-factor behavioral gating");
        }
    }
}
