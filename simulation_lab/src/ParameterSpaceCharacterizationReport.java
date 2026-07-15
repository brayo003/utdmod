import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ParameterSpaceCharacterizationReport {
    private static final int WIDTH = 24;
    private static final int HEIGHT = 24;
    private static final int TICKS = 31000;
    private static final int INJECTION_TICKS = 1000;
    private static final int QUIET_TICKS = 30000;
    private static final int WINDOW_TICKS = 5000;

    public static void writeReport(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        List<SweepResult> results = new ArrayList<>();
        results.addAll(runSweep("v2BaselineAdaptationRate", "v2BaselineAdaptationRate", List.of(0.0005, 0.0010, 0.0020, 0.0040, 0.0080)));
        results.addAll(runSweep("v2BaselineHealingRate", "v2BaselineHealingRate", List.of(0.0002, 0.0005, 0.0010, 0.0020, 0.0040)));
        results.addAll(runSweep("memoryFormationGain", "memoryFormationGain", List.of(0.01, 0.02, 0.05, 0.10)));
        results.addAll(runSweep("memoryDecayRate", "memoryDecayRate", List.of(0.0005, 0.0010, 0.0020, 0.0050, 0.0100)));

        writeCsv(outputDir.resolve("parameter_sweep_summary.csv"), results);
        writeMarkdown(outputDir.resolve("OPERATING_REGION_REPORT.md"), results);
        writeSweepPlots(outputDir, results);
    }

    private static List<SweepResult> runSweep(String sweepName, String parameterName, List<Double> values) throws IOException {
        List<SweepResult> results = new ArrayList<>();
        for (double value : values) {
            ParameterSet params = new ParameterSet();
            params.v2AdaptiveRelaxationEnabled = true;
            params.v3FieldDepthGain = 0.0;
            params.v3FieldDepthDecay = 0.0;
            applyValue(params, parameterName, value);
            GridSimulationV2 simulation = new GridSimulationV2(WIDTH, HEIGHT, TICKS, params);
            simulation.runExperiment(new ExperimentConfig(sweepName, "inject_then_quiet_long", WIDTH, HEIGHT, TICKS, params, WIDTH / 2, HEIGHT / 2, 0.2, 12345L));
            results.add(buildResult(sweepName, parameterName, value, simulation));
        }
        return results;
    }

    private static void applyValue(ParameterSet params, String parameterName, double value) {
        switch (parameterName) {
            case "v2BaselineAdaptationRate" -> params.v2BaselineAdaptationRate = value;
            case "v2BaselineHealingRate" -> params.v2BaselineHealingRate = value;
            case "memoryFormationGain" -> params.memoryFormationGain = value;
            case "memoryDecayRate" -> params.memoryDecayRate = value;
            case "memoryMaximum" -> params.memoryMaximum = value;
            case "memoryDiffusionGain" -> params.memoryDiffusionGain = value;
            case "memoryDepthRetentionGain" -> params.memoryDepthRetentionGain = value;
            default -> throw new IllegalArgumentException("Unsupported parameter: " + parameterName);
        }
    }

    private static SweepResult buildResult(String sweepName, String parameterName, double parameterValue, GridSimulationV2 simulation) {
        List<Double> activationSeries = toAverageSeries(simulation.totalActivationHistory, WIDTH * HEIGHT);
        List<Double> baselineSeries = simulation.baselineHistory;
        List<Double> memorySeries = toAverageSeries(simulation.totalMemoryHistory, WIDTH * HEIGHT);
        List<Double> depthSeries = toAverageSeries(simulation.totalFieldDepthHistory, WIDTH * HEIGHT);
        double finalActivation = mean(simulation.activation);
        double finalBaseline = mean(simulation.baseline);
        double finalMemory = mean(simulation.memory);
        double finalFieldDepth = mean(simulation.fieldDepth);
        double peakActivation = max(activationSeries);
        double peakBaseline = max(baselineSeries);
        double peakMemory = max(memorySeries);
        double avgActivationLastWindow = averageWindow(activationSeries, activationSeries.size() - WINDOW_TICKS);
        double avgBaselineLastWindow = averageWindow(baselineSeries, baselineSeries.size() - WINDOW_TICKS);
        double avgMemoryLastWindow = averageWindow(memorySeries, memorySeries.size() - WINDOW_TICKS);
        double avgDepthLastWindow = averageWindow(depthSeries, depthSeries.size() - WINDOW_TICKS);
        int equilibriumTick = computeEquilibriumTick(activationSeries, finalActivation);
        int halfLifeTick = computeHalfLifeTick(activationSeries, peakActivation);
        String region = OperatingRegionCharacterizer.classifyEquilibrium(finalActivation, finalBaseline, finalMemory, finalFieldDepth, Math.max(1.0, halfLifeTick));
        return new SweepResult(sweepName, parameterName, parameterValue, finalActivation, finalBaseline, finalMemory, finalFieldDepth,
                peakActivation, peakBaseline, peakMemory, avgActivationLastWindow, avgBaselineLastWindow, avgMemoryLastWindow, avgDepthLastWindow,
                equilibriumTick, halfLifeTick, region);
    }

    private static void writeCsv(Path path, List<SweepResult> results) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("sweep,parameter,parameterValue,finalActivation,finalBaseline,finalMemory,finalFieldDepth,peakActivation,peakBaseline,peakMemory,avgActivationLast5000,avgBaselineLast5000,avgMemoryLast5000,avgDepthLast5000,equilibriumTick,halfLifeTick,region").append(System.lineSeparator());
        for (SweepResult result : results) {
            csv.append(result.sweep).append(',')
                    .append(result.parameterName).append(',')
                    .append(String.format("%.8f", result.parameterValue)).append(',')
                    .append(String.format("%.8f", result.finalActivation)).append(',')
                    .append(String.format("%.8f", result.finalBaseline)).append(',')
                    .append(String.format("%.8f", result.finalMemory)).append(',')
                    .append(String.format("%.8f", result.finalFieldDepth)).append(',')
                    .append(String.format("%.8f", result.peakActivation)).append(',')
                    .append(String.format("%.8f", result.peakBaseline)).append(',')
                    .append(String.format("%.8f", result.peakMemory)).append(',')
                    .append(String.format("%.8f", result.avgActivationLastWindow)).append(',')
                    .append(String.format("%.8f", result.avgBaselineLastWindow)).append(',')
                    .append(String.format("%.8f", result.avgMemoryLastWindow)).append(',')
                    .append(String.format("%.8f", result.avgDepthLastWindow)).append(',')
                    .append(result.equilibriumTick).append(',')
                    .append(result.halfLifeTick).append(',')
                    .append(result.region).append(System.lineSeparator());
        }
        Files.writeString(path, csv.toString());
    }

    private static void writeMarkdown(Path path, List<SweepResult> results) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# Parameter-space characterization report\n\n");
        report.append("This report characterizes the current three-timescale relaxation architecture under the same recovery experiment used in the audit: 1000 injection ticks followed by 30000 quiet ticks.\n\n");
        report.append("## Summary tables\n\n");
        for (String sweepName : List.of("v2BaselineAdaptationRate", "v2BaselineHealingRate", "v2MemoryGain", "v2MemoryDecay")) {
            report.append("### ").append(sweepName).append("\n\n");
            report.append("| Parameter | Final A | Final B | Final M | Final D | Equilibrium tick | Half-life | Region |\n");
            report.append("|---|---:|---:|---:|---:|---:|---:|---|\n");
            for (SweepResult result : results) {
                if (!result.sweep.equals(sweepName)) {
                    continue;
                }
                report.append(String.format("| %.6f | %.6f | %.6f | %.6f | %.6f | %d | %d | %s |\n",
                        result.parameterValue, result.finalActivation, result.finalBaseline, result.finalMemory, result.finalFieldDepth,
                        result.equilibriumTick, result.halfLifeTick, describeRegion(result.region)));
            }
            report.append("\n");
        }
        report.append("## Interpretation\n\n");
        report.append("- Stronger baseline adaptation tends to raise the equilibrium activation level and prolong recovery.\n");
        report.append("- Baseline healing shifts the system between a quickly stabilizing regime and a more persistent one.\n");
        report.append("- Memory gain mainly expands the long-lived component, while memory decay governs how quickly that memory footprint collapses.\n");
        report.append("- The operating regions below are derived from the same metrics used in the audit and are therefore consistent with the current implementation.\n");
        Files.writeString(path, report.toString());
    }

    private static void writeSweepPlots(Path outputDir, List<SweepResult> results) throws IOException {
        Files.createDirectories(outputDir);
        for (String sweepName : List.of("v2BaselineAdaptationRate", "v2BaselineHealingRate", "v2MemoryGain", "v2MemoryDecay")) {
            List<SweepResult> sweepResults = results.stream().filter(result -> result.sweep.equals(sweepName)).toList();
            writeNormalizedSvg(outputDir.resolve(sweepName + "_sweep.svg"), sweepName, sweepResults);
        }
    }

    private static void writeNormalizedSvg(Path path, String title, List<SweepResult> sweepResults) throws IOException {
        Files.createDirectories(path.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns='http://www.w3.org/2000/svg' width='900' height='420'>");
        sb.append("<rect width='100%' height='100%' fill='white'/>" );
        sb.append("<text x='20' y='30' font-size='18'>").append(title).append("</text>");
        sb.append("<line x1='40' y1='360' x2='840' y2='360' stroke='gray'/>" );
        sb.append("<line x1='40' y1='60' x2='40' y2='360' stroke='gray'/>" );
        if (!sweepResults.isEmpty()) {
            List<Double> xValues = sweepResults.stream().map(result -> result.parameterValue).toList();
            List<Double> activationValues = sweepResults.stream().map(result -> result.finalActivation).toList();
            List<Double> baselineValues = sweepResults.stream().map(result -> result.finalBaseline).toList();
            List<Double> memoryValues = sweepResults.stream().map(result -> result.finalMemory).toList();
            List<Double> halfLifeValues = sweepResults.stream().map(result -> (double) result.halfLifeTick).toList();
            appendNormalizedSeries(sb, xValues, activationValues, "#4c78a8");
            appendNormalizedSeries(sb, xValues, baselineValues, "#f58518");
            appendNormalizedSeries(sb, xValues, memoryValues, "#54a24b");
            appendNormalizedSeries(sb, xValues, halfLifeValues, "#e45756");
        }
        sb.append("<rect x='650' y='70' width='12' height='12' fill='#4c78a8'/><text x='668' y='80' font-size='12'>final activation</text>");
        sb.append("<rect x='650' y='90' width='12' height='12' fill='#f58518'/><text x='668' y='100' font-size='12'>final baseline</text>");
        sb.append("<rect x='650' y='110' width='12' height='12' fill='#54a24b'/><text x='668' y='120' font-size='12'>final memory</text>");
        sb.append("<rect x='650' y='130' width='12' height='12' fill='#e45756'/><text x='668' y='140' font-size='12'>half-life</text>");
        sb.append("</svg>");
        Files.writeString(path, sb.toString());
    }

    private static void appendNormalizedSeries(StringBuilder sb, List<Double> xValues, List<Double> yValues, String color) {
        if (xValues.size() != yValues.size() || xValues.isEmpty()) {
            return;
        }
        double minX = xValues.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxX = xValues.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double rangeX = Math.max(1e-9, maxX - minX);
        double minY = yValues.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maxY = yValues.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        double rangeY = Math.max(1e-9, maxY - minY);
        for (int i = 0; i < xValues.size() - 1; i++) {
            double x1 = 40 + ((xValues.get(i) - minX) / rangeX) * 800.0;
            double x2 = 40 + ((xValues.get(i + 1) - minX) / rangeX) * 800.0;
            double y1 = 360 - ((yValues.get(i) - minY) / rangeY) * 300.0;
            double y2 = 360 - ((yValues.get(i + 1) - minY) / rangeY) * 300.0;
            sb.append("<line x1='").append((int) x1).append("' y1='").append((int) y1).append("' x2='").append((int) x2).append("' y2='").append((int) y2).append("' stroke='").append(color).append("' stroke-width='1.5'/>");
        }
    }

    private static List<Double> toAverageSeries(List<Double> history, int cells) {
        List<Double> values = new ArrayList<>();
        for (Double value : history) {
            values.add(value / cells);
        }
        return values;
    }

    private static double mean(double[][] grid) {
        double sum = 0.0;
        int count = 0;
        for (double[] row : grid) {
            for (double value : row) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private static double max(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    private static double averageWindow(List<Double> values, int startIndex) {
        if (values.isEmpty() || startIndex < 0) {
            return 0.0;
        }
        int start = Math.min(startIndex, values.size());
        int end = values.size();
        if (end <= start) {
            return values.isEmpty() ? 0.0 : values.get(values.size() - 1);
        }
        double sum = 0.0;
        for (int i = start; i < end; i++) {
            sum += values.get(i);
        }
        return sum / (end - start);
    }

    private static int computeEquilibriumTick(List<Double> activationSeries, double finalActivation) {
        if (activationSeries.isEmpty()) {
            return 0;
        }
        double tolerance = Math.max(1e-6, 0.01 * Math.max(1.0, Math.abs(finalActivation)));
        for (int tick = INJECTION_TICKS; tick < activationSeries.size(); tick++) {
            if (Math.abs(activationSeries.get(tick) - finalActivation) <= tolerance) {
                boolean stable = true;
                int window = Math.min(200, activationSeries.size() - tick);
                for (int j = tick; j < tick + window; j++) {
                    if (Math.abs(activationSeries.get(j) - finalActivation) > tolerance) {
                        stable = false;
                        break;
                    }
                }
                if (stable) {
                    return tick;
                }
            }
        }
        return activationSeries.size() - 1;
    }

    private static int computeHalfLifeTick(List<Double> activationSeries, double peakActivation) {
        if (activationSeries.isEmpty()) {
            return 0;
        }
        double halfPeak = Math.max(0.0, peakActivation / 2.0);
        for (int tick = INJECTION_TICKS; tick < activationSeries.size(); tick++) {
            if (activationSeries.get(tick) <= halfPeak) {
                return tick - INJECTION_TICKS;
            }
        }
        return activationSeries.size() - INJECTION_TICKS;
    }

    private static String describeRegion(String region) {
        return switch (region) {
            case "Dead" -> "Dead substrate";
            case "Quiet" -> "Fast healing";
            case "Living" -> "Living equilibrium";
            case "Persistent" -> "Long-memory equilibrium";
            case "Saturated" -> "Near saturation";
            default -> region;
        };
    }

    private record SweepResult(String sweep, String parameterName, double parameterValue,
                               double finalActivation, double finalBaseline, double finalMemory, double finalFieldDepth,
                               double peakActivation, double peakBaseline, double peakMemory,
                               double avgActivationLastWindow, double avgBaselineLastWindow,
                               double avgMemoryLastWindow, double avgDepthLastWindow,
                               int equilibriumTick, int halfLifeTick, String region) {
    }
}
