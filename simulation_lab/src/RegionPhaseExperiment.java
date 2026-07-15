import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public class RegionPhaseExperiment {
    private static final int WIDTH = 40;
    private static final int HEIGHT = 40;
    private static final int TICKS = 20000;
    private static final int SAMPLE_INTERVAL = 100;
    private static final int EQUILIBRIUM_WINDOW = 2000;
    private static final double ACTIVE_THRESHOLD = 0.05;

    public static void run(Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        List<RunConfig> configs = buildConfigs();
        List<RunResult> results = new ArrayList<>();
        for (RunConfig config : configs) {
            RunResult result = runSingle(config, outputDir);
            results.add(result);
        }
        writeCsv(outputDir.resolve("REGION_PHASE_RESULTS.csv"), results);
        writeReport(outputDir.resolve("REGION_PHASE_DIAGRAM.md"), results);
    }

    private static List<RunConfig> buildConfigs() {
        List<RunConfig> configs = new ArrayList<>();
        configs.add(new RunConfig("baseline", null, 0.0, null));
        configs.add(new RunConfig("v2ActivationRelaxationGain", "v2ActivationRelaxationGain", 0.09, "v2ActivationRelaxationGain"));
        configs.add(new RunConfig("v2ActivationRelaxationGain", "v2ActivationRelaxationGain", 0.18, "v2ActivationRelaxationGain"));
        configs.add(new RunConfig("v2ActivationRelaxationGain", "v2ActivationRelaxationGain", 0.36, "v2ActivationRelaxationGain"));
        configs.add(new RunConfig("v2BaselineAdaptationRate", "v2BaselineAdaptationRate", 0.001, "v2BaselineAdaptationRate"));
        configs.add(new RunConfig("v2BaselineAdaptationRate", "v2BaselineAdaptationRate", 0.002, "v2BaselineAdaptationRate"));
        configs.add(new RunConfig("v2BaselineAdaptationRate", "v2BaselineAdaptationRate", 0.004, "v2BaselineAdaptationRate"));
        configs.add(new RunConfig("v2BaselineHealingRate", "v2BaselineHealingRate", 0.00025, "v2BaselineHealingRate"));
        configs.add(new RunConfig("v2BaselineHealingRate", "v2BaselineHealingRate", 0.0005, "v2BaselineHealingRate"));
        configs.add(new RunConfig("v2BaselineHealingRate", "v2BaselineHealingRate", 0.001, "v2BaselineHealingRate"));
        configs.add(new RunConfig("v2MemoryGain", "v2MemoryGain", 0.01, "v2MemoryGain"));
        configs.add(new RunConfig("v2MemoryGain", "v2MemoryGain", 0.02, "v2MemoryGain"));
        configs.add(new RunConfig("v2MemoryGain", "v2MemoryGain", 0.05, "v2MemoryGain"));
        configs.add(new RunConfig("v2MemoryDecay", "v2MemoryDecay", 0.0005, "v2MemoryDecay"));
        configs.add(new RunConfig("v2MemoryDecay", "v2MemoryDecay", 0.001, "v2MemoryDecay"));
        configs.add(new RunConfig("v2MemoryDecay", "v2MemoryDecay", 0.002, "v2MemoryDecay"));
        configs.add(new RunConfig("v2TransportGain", "v2TransportGain", 0.04, "v2TransportGain"));
        configs.add(new RunConfig("v2TransportGain", "v2TransportGain", 0.08, "v2TransportGain"));
        configs.add(new RunConfig("v2TransportGain", "v2TransportGain", 0.16, "v2TransportGain"));
        configs.add(new RunConfig("v3FieldDepthGain", "v3FieldDepthGain", 0.0, "v3FieldDepthGain"));
        configs.add(new RunConfig("v3FieldDepthGain", "v3FieldDepthGain", 0.00001, "v3FieldDepthGain"));
        configs.add(new RunConfig("v3FieldDepthGain", "v3FieldDepthGain", 0.00002, "v3FieldDepthGain"));
        configs.add(new RunConfig("v3FieldDepthDecay", "v3FieldDepthDecay", 0.0, "v3FieldDepthDecay"));
        configs.add(new RunConfig("v3FieldDepthDecay", "v3FieldDepthDecay", 0.000001, "v3FieldDepthDecay"));
        configs.add(new RunConfig("v3FieldDepthDecay", "v3FieldDepthDecay", 0.00001, "v3FieldDepthDecay"));
        return configs;
    }

    private static RunResult runSingle(RunConfig config, Path outputDir) throws IOException {
        ParameterSet params = new ParameterSet();
        params.v2AdaptiveRelaxationEnabled = true;
        if (config.parameterName != null) {
            applyParameter(params, config.parameterName, config.value);
        }
        GridSimulationV2 simulation = new GridSimulationV2(WIDTH, HEIGHT, TICKS, params);
        InitialState initialState = buildInitialState();
        simulation.activation = initialState.activation;
        simulation.memory = initialState.memory;
        simulation.baseline = initialState.baseline;
        simulation.fieldDepth = initialState.fieldDepth;

        List<Snapshot> snapshots = new ArrayList<>();
        List<Double> activationTrace = new ArrayList<>();
        List<Double> baselineTrace = new ArrayList<>();
        List<Double> memoryTrace = new ArrayList<>();
        List<Double> depthTrace = new ArrayList<>();
        List<Integer> regionCountTrace = new ArrayList<>();
        List<Double> maxActivationTrace = new ArrayList<>();
        List<Double> varianceActivationTrace = new ArrayList<>();
        List<Double> varianceBaselineTrace = new ArrayList<>();
        List<Double> varianceMemoryTrace = new ArrayList<>();
        List<Double> varianceDepthTrace = new ArrayList<>();

        for (int tick = 0; tick < TICKS; tick++) {
            double[][] nextA = new double[HEIGHT][WIDTH];
            double[][] nextM = new double[HEIGHT][WIDTH];
            double[][] nextB = new double[HEIGHT][WIDTH];
            double[][] nextD = new double[HEIGHT][WIDTH];
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    double transport = 0.0;
                    int maxSearchRadius = 1 + (int) Math.floor(params.v3FieldDepthRadius);
                    for (int dy = -maxSearchRadius; dy <= maxSearchRadius; dy++) {
                        for (int dx = -maxSearchRadius; dx <= maxSearchRadius; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && nx < WIDTH && ny >= 0 && ny < HEIGHT) {
                                double distance = Math.sqrt(dx * dx + dy * dy);
                                if (distance > maxSearchRadius) continue;
                                double nA = simulation.activation[ny][nx];
                                double nM = simulation.memory[ny][nx];
                                double nC = simulation.fieldDepth[ny][nx];
                                int sourceRadius = 1 + (int) Math.floor(params.v3FieldDepthRadius * nC / (nC + params.v3FieldDepthEffectK));
                                if (distance > sourceRadius) continue;
                                double neighborStim = nA / (1.0 + nA);
                                neighborStim *= (1.0 + params.v2MemoryInfluence * (nM / (nM + params.v2MemoryEffectK)));
                                double weight = params.v3DistanceDecay > 0.0 ? Math.exp(-distance / params.v3DistanceDecay) : 1.0 / (1.0 + distance);
                                transport += neighborStim * weight;
                            }
                        }
                    }
                    transport *= params.v2TransportGain;
                    transport *= (1.0 - simulation.activation[y][x] / (1.0 + simulation.activation[y][x]));
                    GridSimulationV2.CellUpdateResult update = simulation.integrateUnifiedCellStep(
                            x,
                            y,
                            0.0,
                            transport);
                    nextA[y][x] = update.nextActivation;
                    nextM[y][x] = update.nextMemory;
                    nextB[y][x] = update.nextBaseline;
                    nextD[y][x] = update.nextDepth;
                }
            }
            simulation.activation = nextA;
            simulation.memory = nextM;
            simulation.baseline = nextB;
            simulation.fieldDepth = nextD;
            if (tick % SAMPLE_INTERVAL == 0) {
                List<ComponentMetrics> components = computeComponents(simulation.activation, simulation.baseline, simulation.memory, simulation.fieldDepth, ACTIVE_THRESHOLD);
                int regionCount = components.size();
                regionCountTrace.add(regionCount);
                activationTrace.add(sum(simulation.activation));
                baselineTrace.add(sum(simulation.baseline));
                memoryTrace.add(sum(simulation.memory));
                depthTrace.add(sum(simulation.fieldDepth));
                maxActivationTrace.add(max(simulation.activation));
                varianceActivationTrace.add(variance(components, c -> c.meanActivation));
                varianceBaselineTrace.add(variance(components, c -> c.meanBaseline));
                varianceMemoryTrace.add(variance(components, c -> c.meanMemory));
                varianceDepthTrace.add(variance(components, c -> c.meanFieldDepth));
                snapshots.add(new Snapshot(tick, regionCount, components));
            }
        }

        List<ComponentMetrics> finalComponents = computeComponents(simulation.activation, simulation.baseline, simulation.memory, simulation.fieldDepth, ACTIVE_THRESHOLD);
        double interRegionTransport = computeInterRegionTransport(simulation, finalComponents);
        int equilibriumTick = findEquilibriumTick(activationTrace, maxActivationTrace, regionCountTrace);
        String classification = classifyRun(initialState, snapshots, finalComponents, activationTrace, regionCountTrace, maxActivationTrace);
        Path runDir = outputDir.resolve(sanitize(config.name));
        Files.createDirectories(runDir.resolve("heatmaps"));
        Files.createDirectories(runDir.resolve("plots"));
        writeHeatmapSvg(runDir.resolve("heatmaps").resolve("activation.svg"), simulation.activation, "Activation");
        writeHeatmapSvg(runDir.resolve("heatmaps").resolve("baseline.svg"), simulation.baseline, "Baseline");
        writeHeatmapSvg(runDir.resolve("heatmaps").resolve("memory.svg"), simulation.memory, "Memory");
        writeHeatmapSvg(runDir.resolve("heatmaps").resolve("field_depth.svg"), simulation.fieldDepth, "Field depth");
        writeRegionalTimeSeries(runDir.resolve("plots").resolve("regional_activation.svg"), snapshots, finalComponents.size());
        return new RunResult(config.name, config.parameterName, config.value, finalComponents.size(), meanComponentValue(finalComponents, c -> c.meanActivation), meanComponentValue(finalComponents, c -> c.meanBaseline), meanComponentValue(finalComponents, c -> c.meanMemory), meanComponentValue(finalComponents, c -> c.meanFieldDepth), interRegionTransport, largestComponentSize(finalComponents), varianceAcrossComponents(finalComponents, c -> c.meanActivation), varianceAcrossComponents(finalComponents, c -> c.meanBaseline), varianceAcrossComponents(finalComponents, c -> c.meanMemory), varianceAcrossComponents(finalComponents, c -> c.meanFieldDepth), equilibriumTick, classification);
    }

    private static InitialState buildInitialState() {
        double[][] activation = new double[HEIGHT][WIDTH];
        double[][] memory = new double[HEIGHT][WIDTH];
        double[][] baseline = new double[HEIGHT][WIDTH];
        double[][] fieldDepth = new double[HEIGHT][WIDTH];
        List<Seed> seeds = List.of(
                new Seed(8, 8, 4.0, 1.5, 2.4, 0.4),
                new Seed(8, 32, 3.0, 2.5, 1.6, 0.6),
                new Seed(32, 8, 2.5, 3.5, 1.2, 0.8),
                new Seed(32, 32, 2.0, 4.5, 0.8, 1.0));
        for (Seed seed : seeds) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    double distance = Math.hypot(x - seed.x, y - seed.y);
                    if (distance <= 6.0) {
                        double decay = Math.exp(-distance / 2.5);
                        activation[y][x] = Math.max(activation[y][x], seed.activation * decay);
                        memory[y][x] = Math.max(memory[y][x], seed.memory * decay);
                        baseline[y][x] = Math.max(baseline[y][x], seed.baseline * decay);
                        fieldDepth[y][x] = Math.max(fieldDepth[y][x], seed.fieldDepth * decay);
                    }
                }
            }
        }
        return new InitialState(activation, memory, baseline, fieldDepth);
    }

    private static void applyParameter(ParameterSet params, String parameterName, double value) {
        switch (parameterName) {
            case "v2ActivationRelaxationGain" -> params.v2ActivationRelaxationGain = value;
            case "v2BaselineAdaptationRate" -> params.v2BaselineAdaptationRate = value;
            case "v2BaselineHealingRate" -> params.v2BaselineHealingRate = value;
            case "memoryFormationGain" -> params.memoryFormationGain = value;
            case "memoryMaximum" -> params.memoryMaximum = value;
            case "memoryDiffusionGain" -> params.memoryDiffusionGain = value;
            case "memoryDepthRetentionGain" -> params.memoryDepthRetentionGain = value;
            case "memoryDecayRate" -> params.memoryDecayRate = value;
            case "v2TransportGain" -> params.v2TransportGain = value;
            case "v3FieldDepthGain" -> params.v3FieldDepthGain = value;
            case "v3FieldDepthDecay" -> params.v3FieldDepthDecay = value;
            default -> throw new IllegalArgumentException("Unsupported parameter: " + parameterName);
        }
    }

    private static List<ComponentMetrics> computeComponents(double[][] activationGrid, double[][] baselineGrid, double[][] memoryGrid, double[][] depthGrid, double threshold) {
        boolean[][] visited = new boolean[HEIGHT][WIDTH];
        List<ComponentMetrics> components = new ArrayList<>();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (activationGrid[y][x] > threshold && !visited[y][x]) {
                    List<int[]> cells = new ArrayList<>();
                    ArrayDeque<int[]> stack = new ArrayDeque<>();
                    stack.push(new int[]{x, y});
                    visited[y][x] = true;
                    while (!stack.isEmpty()) {
                        int[] cell = stack.pop();
                        int cx = cell[0];
                        int cy = cell[1];
                        cells.add(new int[]{cx, cy});
                        for (int[] delta : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            int nx = cx + delta[0];
                            int ny = cy + delta[1];
                            if (nx >= 0 && nx < WIDTH && ny >= 0 && ny < HEIGHT && !visited[ny][nx] && activationGrid[ny][nx] > threshold) {
                                visited[ny][nx] = true;
                                stack.push(new int[]{nx, ny});
                            }
                        }
                    }
                    components.add(ComponentMetrics.fromCells(cells, activationGrid, baselineGrid, memoryGrid, depthGrid));
                }
            }
        }
        components.sort(Comparator.comparingInt(c -> c.size));
        return components;
    }

    private static double computeInterRegionTransport(GridSimulationV2 simulation, List<ComponentMetrics> components) {
        if (components.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (ComponentMetrics component : components) {
            for (int[] cell : component.cells) {
                int x = cell[0];
                int y = cell[1];
                for (int[] delta : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + delta[0];
                    int ny = y + delta[1];
                    if (nx < 0 || nx >= WIDTH || ny < 0 || ny >= HEIGHT) continue;
                    int componentIndex = componentIndexAt(components, nx, ny);
                    if (componentIndex != component.id) {
                        double neighborActivation = simulation.activation[ny][nx];
                        double neighborMemory = simulation.memory[ny][nx];
                        double neighborDepth = simulation.fieldDepth[ny][nx];
                        double weight = 1.0 / (1.0 + Math.hypot(delta[0], delta[1]));
                        total += neighborActivation * weight * (1.0 + neighborMemory / (1.0 + neighborDepth));
                    }
                }
            }
        }
        return total;
    }

    private static int componentIndexAt(List<ComponentMetrics> components, int x, int y) {
        for (ComponentMetrics component : components) {
            if (component.contains(x, y)) {
                return component.id;
            }
        }
        return -1;
    }

    private static int findEquilibriumTick(List<Double> activationTrace, List<Double> maxActivationTrace, List<Integer> regionCountTrace) {
        if (activationTrace.size() < 3) return 0;
        int start = Math.max(0, activationTrace.size() - EQUILIBRIUM_WINDOW / SAMPLE_INTERVAL);
        double tolerance = 1e-4;
        for (int index = start; index < activationTrace.size(); index++) {
            boolean stable = true;
            for (int j = index; j < activationTrace.size(); j++) {
                double delta = Math.abs(activationTrace.get(j) - activationTrace.get(index));
                if (delta > tolerance) {
                    stable = false;
                    break;
                }
            }
            if (stable) {
                return index * SAMPLE_INTERVAL;
            }
        }
        return (activationTrace.size() - 1) * SAMPLE_INTERVAL;
    }

    private static String classifyRun(InitialState initialState, List<Snapshot> snapshots, List<ComponentMetrics> finalComponents, List<Double> activationTrace, List<Integer> regionCountTrace, List<Double> maxActivationTrace) {
        if (activationTrace.size() < 3) return "Single global attractor";
        double maxAbsValue = maxActivationTrace.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        if (maxAbsValue > 1e6) return "Divergent/unstable";
        int initialCount = countInitialComponents(initialState.activation);
        int finalCount = finalComponents.size();
        boolean merged = initialCount > 1 && finalCount <= 1;
        boolean oscillatory = hasOscillation(activationTrace);
        if (oscillatory) return "Oscillatory dynamics";
        if (merged) return "Metastable regions that eventually merge";
        if (finalCount > 1) return "Multiple stable regional equilibria";
        return "Single global attractor";
    }

    private static int countInitialComponents(double[][] grid) {
        return computeComponents(grid, new double[HEIGHT][WIDTH], new double[HEIGHT][WIDTH], new double[HEIGHT][WIDTH], ACTIVE_THRESHOLD).size();
    }

    private static boolean hasOscillation(List<Double> activationTrace) {
        if (activationTrace.size() < 8) return false;
        int signChanges = 0;
        for (int i = 1; i < activationTrace.size(); i++) {
            double prev = activationTrace.get(i - 1);
            double cur = activationTrace.get(i);
            if ((prev < 0.0 && cur >= 0.0) || (prev >= 0.0 && cur < 0.0)) {
                signChanges++;
            }
        }
        return signChanges > 4;
    }

    private static void writeCsv(Path path, List<RunResult> results) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("run,parameter,parameterValue,connectedEquilibriumRegions,meanActivation,meanBaseline,meanMemory,meanFieldDepth,interRegionTransport,largestConnectedComponent,activationVariance,baselineVariance,memoryVariance,depthVariance,equilibriumTick,classification").append(System.lineSeparator());
        for (RunResult result : results) {
            csv.append(result.runName).append(',')
                    .append(result.parameterName).append(',')
                    .append(String.format(Locale.US, "%.8f", result.parameterValue)).append(',')
                    .append(result.connectedEquilibriumRegions).append(',')
                    .append(String.format(Locale.US, "%.8f", result.meanActivation)).append(',')
                    .append(String.format(Locale.US, "%.8f", result.meanBaseline)).append(',')
                    .append(String.format(Locale.US, "%.8f", result.meanMemory)).append(',')
                    .append(String.format(Locale.US, "%.8f", result.meanFieldDepth)).append(',')
                    .append(String.format(Locale.US, "%.8f", result.interRegionTransport)).append(',')
                    .append(result.largestConnectedComponent).append(',')
                    .append(String.format(Locale.US, "%.8f", result.activationVariance)).append(',')
                    .append(String.format(Locale.US, "%.8f", result.baselineVariance)).append(',')
                    .append(String.format(Locale.US, "%.8f", result.memoryVariance)).append(',')
                    .append(String.format(Locale.US, "%.8f", result.depthVariance)).append(',')
                    .append(result.equilibriumTick).append(',')
                    .append(result.classification).append(System.lineSeparator());
        }
        Files.writeString(path, csv.toString());
    }

    private static void writeReport(Path path, List<RunResult> results) throws IOException {
        StringBuilder report = new StringBuilder();
        report.append("# Region Phase Diagram\n\n");
        report.append("This report characterizes the current equations over a parameter sweep while keeping the implementation fixed.\n\n");
        report.append("## Summary table\n\n");
        report.append("| Run | Parameter | Value | Classification | Connected regions | Mean activation | Mean baseline | Mean memory | Mean depth | Equilibrium tick |\n");
        report.append("|---|---|---:|---|---:|---:|---:|---:|---:|---:|\n");
        for (RunResult result : results) {
            report.append(String.format(Locale.US, "| %s | %s | %.6f | %s | %d | %.6f | %.6f | %.6f | %.6f | %d |\n", result.runName, result.parameterName, result.parameterValue, result.classification, result.connectedEquilibriumRegions, result.meanActivation, result.meanBaseline, result.meanMemory, result.meanFieldDepth, result.equilibriumTick));
        }
        report.append("\n## Interpretation\n\n");
        boolean discoveredMultipleEquilibria = results.stream().anyMatch(result -> result.classification.equals("Multiple stable regional equilibria"));
        boolean allSingle = results.stream().allMatch(result -> result.classification.equals("Single global attractor") || result.connectedEquilibriumRegions <= 1);
        if (allSingle && !discoveredMultipleEquilibria) {
            report.append("- The current mathematical architecture appears to possess only a single attractor over the explored parameter space.\n");
            report.append("- No parameter regime in this sweep produced multiple stable regional equilibria; the initially separated regions always collapsed toward one connected equilibrium.\n");
        } else {
            report.append("- Multiple equilibrium classes were observed across the explored parameter space.\n");
            for (RunResult result : results) {
                if (!result.classification.equals("Single global attractor")) {
                    report.append("- ").append(result.runName).append(" classified as ").append(result.classification).append(" with ").append(result.connectedEquilibriumRegions).append(" connected equilibrium regions.\n");
                }
            }
        }
        report.append("\n## Output files\n\n");
        report.append("- Heatmaps and regional plots were written under the per-run directories in the output folder.\n");
        Files.writeString(path, report.toString());
    }

    private static void writeHeatmapSvg(Path path, double[][] values, String title) throws IOException {
        Files.createDirectories(path.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns='http://www.w3.org/2000/svg' width='320' height='320'>");
        sb.append("<rect width='100%' height='100%' fill='white'/>");
        sb.append("<text x='10' y='20' font-size='16'>").append(title).append("</text>");
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                double value = values[y][x];
                double scaled = Math.max(0.0, Math.min(1.0, value / 8.0));
                int color = (int) (255.0 * scaled);
                sb.append("<rect x='").append(12 + x * 7).append("' y='").append(28 + y * 7).append("' width='6' height='6' fill='rgb(").append(color).append(",").append(color).append(",").append(color).append(")'/>");
            }
        }
        sb.append("</svg>");
        Files.writeString(path, sb.toString());
    }

    private static void writeRegionalTimeSeries(Path path, List<Snapshot> snapshots, int regionCount) throws IOException {
        Files.createDirectories(path.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns='http://www.w3.org/2000/svg' width='900' height='420'>");
        sb.append("<rect width='100%' height='100%' fill='white'/>");
        sb.append("<text x='20' y='30' font-size='18'>Regional activation trajectories</text>");
        sb.append("<line x1='40' y1='360' x2='840' y2='360' stroke='gray'/>");
        sb.append("<line x1='40' y1='60' x2='40' y2='360' stroke='gray'/>");
        if (!snapshots.isEmpty()) {
            double minX = snapshots.get(0).tick;
            double maxX = snapshots.get(snapshots.size() - 1).tick;
            double rangeX = Math.max(1.0, maxX - minX);
            for (int regionIndex = 0; regionIndex < Math.min(regionCount, 4); regionIndex++) {
                String color = switch (regionIndex) {
                    case 0 -> "#4c78a8";
                    case 1 -> "#f58518";
                    case 2 -> "#54a24b";
                    default -> "#e45756";
                };
                for (int i = 0; i < snapshots.size() - 1; i++) {
                    Snapshot a = snapshots.get(i);
                    Snapshot b = snapshots.get(i + 1);
                    double valueA = a.components.isEmpty() || regionIndex >= a.components.size() ? 0.0 : a.components.get(regionIndex).meanActivation;
                    double valueB = b.components.isEmpty() || regionIndex >= b.components.size() ? 0.0 : b.components.get(regionIndex).meanActivation;
                    double minY = 0.0;
                    double maxY = 8.0;
                    double x1 = 40 + ((a.tick - minX) / rangeX) * 800.0;
                    double x2 = 40 + ((b.tick - minX) / rangeX) * 800.0;
                    double y1 = 360 - ((valueA - minY) / Math.max(1e-9, maxY - minY)) * 300.0;
                    double y2 = 360 - ((valueB - minY) / Math.max(1e-9, maxY - minY)) * 300.0;
                    sb.append("<line x1='").append((int) x1).append("' y1='").append((int) y1).append("' x2='").append((int) x2).append("' y2='").append((int) y2).append("' stroke='").append(color).append("' stroke-width='1.5'/>");
                }
            }
        }
        sb.append("</svg>");
        Files.writeString(path, sb.toString());
    }

    private static double sum(double[][] grid) {
        double total = 0.0;
        for (double[] row : grid) {
            for (double value : row) {
                total += value;
            }
        }
        return total;
    }

    private static double max(double[][] grid) {
        double maxValue = 0.0;
        for (double[] row : grid) {
            for (double value : row) {
                maxValue = Math.max(maxValue, value);
            }
        }
        return maxValue;
    }

    private static double variance(List<ComponentMetrics> components, Function<ComponentMetrics, Double> accessor) {
        if (components.isEmpty()) return 0.0;
        double mean = components.stream().mapToDouble(c -> accessor.apply(c)).average().orElse(0.0);
        double sum = 0.0;
        for (ComponentMetrics component : components) {
            double diff = accessor.apply(component) - mean;
            sum += diff * diff;
        }
        return sum / Math.max(1, components.size());
    }

    private static double meanComponentValue(List<ComponentMetrics> components, Function<ComponentMetrics, Double> accessor) {
        return components.isEmpty() ? 0.0 : components.stream().mapToDouble(c -> accessor.apply(c)).average().orElse(0.0);
    }

    private static double varianceAcrossComponents(List<ComponentMetrics> components, Function<ComponentMetrics, Double> accessor) {
        return variance(components, accessor);
    }

    private static int largestComponentSize(List<ComponentMetrics> components) {
        return components.stream().mapToInt(c -> c.size).max().orElse(0);
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private record RunConfig(String name, String parameterName, double value, String parameter) {
    }

    private record InitialState(double[][] activation, double[][] memory, double[][] baseline, double[][] fieldDepth) {
    }

    private record Seed(int x, int y, double activation, double memory, double baseline, double fieldDepth) {
    }

    private record Snapshot(int tick, int regionCount, List<ComponentMetrics> components) {
    }

    private record RunResult(String runName, String parameterName, double parameterValue, int connectedEquilibriumRegions, double meanActivation, double meanBaseline, double meanMemory, double meanFieldDepth, double interRegionTransport, int largestConnectedComponent, double activationVariance, double baselineVariance, double memoryVariance, double depthVariance, int equilibriumTick, String classification) {
    }

    private static class ComponentMetrics {
        private final int id;
        private final int size;
        private final double meanActivation;
        private final double meanBaseline;
        private final double meanMemory;
        private final double meanFieldDepth;
        private final List<int[]> cells;

        private ComponentMetrics(int id, int size, double meanActivation, double meanBaseline, double meanMemory, double meanFieldDepth, List<int[]> cells) {
            this.id = id;
            this.size = size;
            this.meanActivation = meanActivation;
            this.meanBaseline = meanBaseline;
            this.meanMemory = meanMemory;
            this.meanFieldDepth = meanFieldDepth;
            this.cells = cells;
        }

        private static ComponentMetrics fromCells(List<int[]> cells, double[][] activationGrid, double[][] baselineGrid, double[][] memoryGrid, double[][] depthGrid) {
            double sumActivation = 0.0;
            double sumBaseline = 0.0;
            double sumMemory = 0.0;
            double sumFieldDepth = 0.0;
            for (int[] cell : cells) {
                int x = cell[0];
                int y = cell[1];
                sumActivation += activationGrid[y][x];
                sumBaseline += baselineGrid[y][x];
                sumMemory += memoryGrid[y][x];
                sumFieldDepth += depthGrid[y][x];
            }
            return new ComponentMetrics(cells.hashCode(), cells.size(), cells.isEmpty() ? 0.0 : sumActivation / cells.size(), cells.isEmpty() ? 0.0 : sumBaseline / cells.size(), cells.isEmpty() ? 0.0 : sumMemory / cells.size(), cells.isEmpty() ? 0.0 : sumFieldDepth / cells.size(), cells);
        }

        private boolean contains(int x, int y) {
            for (int[] cell : cells) {
                if (cell[0] == x && cell[1] == y) {
                    return true;
                }
            }
            return false;
        }
    }
}
