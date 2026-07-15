import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class EcologicalValidationSuite {
    public static List<EcologicalScenarioResult> runAllScenarios(String outputDir, int scenarioCount, int width, int height, int ticks) throws IOException {
        Path outDir = Path.of(outputDir);
        Files.createDirectories(outDir);

        List<EcologicalScenarioResult> results = new ArrayList<>();
        String[] scenarioNames = {
                "persistent_travel_corridor",
                "local_resource_extraction",
                "settlement_growth",
                "abandonment",
                "repeated_attack",
                "competing_centers",
                "wilderness",
                "recovery"
        };

        for (int i = 0; i < scenarioCount; i++) {
            EcologicalScenarioResult result = runScenario(i + 1, scenarioNames[i], width, height, ticks, outDir);
            results.add(result);
        }

        writeScenarioSummary(outDir, results);
        writeScenarioReports(outDir, results);
        return results;
    }

    private static EcologicalScenarioResult runScenario(int scenarioIndex, String scenarioName, int width, int height, int ticks, Path outputDir) throws IOException {
        ParameterSet params = new ParameterSet();
        GridSimulationV2 sim = new GridSimulationV2(width, height, ticks, params);
        List<Double> meanActivation = new ArrayList<>();
        List<Double> meanMemory = new ArrayList<>();
        List<Double> meanBaseline = new ArrayList<>();
        List<Double> meanFieldDepth = new ArrayList<>();
        List<Double> maxActivation = new ArrayList<>();
        List<Double> maxMemory = new ArrayList<>();
        List<Double> maxBaseline = new ArrayList<>();
        List<Double> maxFieldDepth = new ArrayList<>();
        List<Double> organizationProduction = new ArrayList<>();
        List<Double> erosion = new ArrayList<>();
        List<Double> netDepthProduction = new ArrayList<>();
        List<Double> spatialVariance = new ArrayList<>();
        List<SpatialMetricRow> spatialMetrics = new ArrayList<>();
        Path scenarioDir = outputDir.resolve("scenario_" + scenarioIndex + "_" + scenarioName);
        Files.createDirectories(scenarioDir);

        applyScenarioInitialConditions(sim, scenarioName);
        exportSpatialState(scenarioDir, scenarioName, 0, sim, spatialMetrics);

        int exportEvery = Math.max(1, ticks / 8);
        for (int t = 0; t < ticks; t++) {
            double[][] nextActivation = new double[sim.height][sim.width];
            double[][] nextMemory = new double[sim.height][sim.width];
            double[][] nextBaseline = new double[sim.height][sim.width];
            double[][] nextDepth = new double[sim.height][sim.width];
            double totalActivation = 0.0;
            double totalMemory = 0.0;
            double totalBaseline = 0.0;
            double totalDepth = 0.0;
            double maxA = 0.0;
            double maxM = 0.0;
            double maxB = 0.0;
            double maxD = 0.0;
            double totalOrganization = 0.0;
            double totalErosion = 0.0;
            double totalNetDepth = 0.0;
            double variance = 0.0;

            for (int y = 0; y < sim.height; y++) {
                for (int x = 0; x < sim.width; x++) {
                    double A = sim.activation[y][x];
                    double M = sim.memory[y][x];
                    double B = sim.baseline[y][x];
                    double D = sim.fieldDepth[y][x];
                    SpatialStencil stencil = new SpatialStencil(x, y, sim.activation, sim.memory, sim.fieldDepth, sim.baseline);
                    double transport = computeTransport(sim, x, y);
                    double external = scenarioExternalInput(sim, scenarioName, t, x, y);
                    GridSimulationV2.CellUpdateResult update = sim.integrateUnifiedCellStep(x, y, external, transport);

                    nextActivation[y][x] = update.nextActivation;
                    nextMemory[y][x] = update.nextMemory;
                    nextBaseline[y][x] = update.nextBaseline;
                    nextDepth[y][x] = update.nextDepth;

                    totalActivation += update.nextActivation;
                    totalMemory += update.nextMemory;
                    totalBaseline += update.nextBaseline;
                    totalDepth += update.nextDepth;
                    maxA = Math.max(maxA, update.nextActivation);
                    maxM = Math.max(maxM, update.nextMemory);
                    maxB = Math.max(maxB, update.nextBaseline);
                    maxD = Math.max(maxD, update.nextDepth);

                    double organization = sim.computeFieldOrganization(A, B, D, stencil, external, transport);
                    double erosionRate = sim.computeFieldErosion(A, B, D, stencil, external, transport);
                    totalOrganization += organization;
                    totalErosion += erosionRate;
                    totalNetDepth += params.depthGrowthGain * organization - params.depthErosionGain * erosionRate;
                    variance += (update.nextDepth - (totalDepth / (x + 1 + y * sim.width))) * (update.nextDepth - (totalDepth / (x + 1 + y * sim.width)));
                }
            }

            sim.activation = nextActivation;
            sim.memory = nextMemory;
            sim.baseline = nextBaseline;
            sim.fieldDepth = nextDepth;

            meanActivation.add(totalActivation / (sim.width * sim.height));
            meanMemory.add(totalMemory / (sim.width * sim.height));
            meanBaseline.add(totalBaseline / (sim.width * sim.height));
            meanFieldDepth.add(totalDepth / (sim.width * sim.height));
            maxActivation.add(maxA);
            maxMemory.add(maxM);
            maxBaseline.add(maxB);
            maxFieldDepth.add(maxD);
            organizationProduction.add(totalOrganization / (sim.width * sim.height));
            erosion.add(totalErosion / (sim.width * sim.height));
            netDepthProduction.add(totalNetDepth / (sim.width * sim.height));
            spatialVariance.add(variance / (sim.width * sim.height));

            if (t == 0 || (t + 1) % exportEvery == 0 || t == ticks - 1) {
                exportSpatialState(scenarioDir, scenarioName, t + 1, sim, spatialMetrics);
            }
        }

        List<Double> transportSeries = new ArrayList<>();
        for (int t = 0; t < ticks; t++) {
            double totalTransport = 0.0;
            for (int y = 0; y < sim.height; y++) {
                for (int x = 0; x < sim.width; x++) {
                    totalTransport += computeTransport(sim, x, y);
                }
            }
            transportSeries.add(totalTransport / (sim.width * sim.height));
        }

        double corrAM = correlation(meanActivation, meanMemory);
        double corrMD = correlation(meanMemory, meanFieldDepth);
        double corrTD = correlation(transportSeries, meanFieldDepth);
        double totalOrganizationValue = sum(organizationProduction);
        double totalErosionValue = sum(erosion);
        double totalNetDepth = sum(netDepthProduction);
        double finalMeanDepth = meanFieldDepth.get(meanFieldDepth.size() - 1);
        double finalMaxDepth = maxFieldDepth.get(maxFieldDepth.size() - 1);
        String interpretation = interpretScenario(scenarioName, corrAM, corrMD, corrTD, finalMeanDepth, finalMaxDepth);

        writeScenarioCsv(outputDir, scenarioIndex, scenarioName, meanActivation, meanMemory, meanBaseline, meanFieldDepth, maxActivation, maxMemory, maxBaseline, maxFieldDepth, organizationProduction, erosion, netDepthProduction, spatialVariance);
        writeSpatialMetricsCsv(scenarioDir, spatialMetrics);
        writeSpatialValidationReport(outputDir, scenarioIndex, scenarioName, spatialMetrics, descriptionForScenario(scenarioName));

        return new EcologicalScenarioResult(
                scenarioName,
                descriptionForScenario(scenarioName),
                meanActivation,
                meanMemory,
                meanBaseline,
                meanFieldDepth,
                maxActivation,
                maxMemory,
                maxBaseline,
                maxFieldDepth,
                organizationProduction,
                erosion,
                netDepthProduction,
                spatialVariance,
                corrAM,
                corrMD,
                corrTD,
                totalOrganizationValue,
                totalErosionValue,
                totalNetDepth,
                finalMeanDepth,
                finalMaxDepth,
                interpretation);
    }

    private static void exportSpatialState(Path scenarioDir, String scenarioName, int tick, GridSimulationV2 sim, List<SpatialMetricRow> spatialMetrics) throws IOException {
        Files.createDirectories(scenarioDir);
        StringBuilder snapshot = new StringBuilder();
        snapshot.append("x,y,activation,memory,baseline,depth\n");
        for (int y = 0; y < sim.height; y++) {
            for (int x = 0; x < sim.width; x++) {
                snapshot.append(x).append(',').append(y).append(',')
                        .append(sim.activation[y][x]).append(',')
                        .append(sim.memory[y][x]).append(',')
                        .append(sim.baseline[y][x]).append(',')
                        .append(sim.fieldDepth[y][x]).append('\n');
            }
        }
        Files.writeString(scenarioDir.resolve(String.format("spatial_snapshot_%04d.csv", tick)), snapshot.toString());
        spatialMetrics.add(computeSpatialMetrics(tick, sim.activation, sim.memory, sim.baseline, sim.fieldDepth));
    }

    private static void writeSpatialMetricsCsv(Path scenarioDir, List<SpatialMetricRow> spatialMetrics) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("tick,activationFractionAboveThreshold,activationOccupiedArea,activationConcentrationIndex,activationComponentCount,activationLargestComponent,activationAverageComponentSize,activationEntropy,activationMoranI,depthFractionAboveThreshold,depthOccupiedArea,depthConcentrationIndex,depthComponentCount,depthLargestComponent,depthAverageComponentSize,depthEntropy,depthMoranI,depthMeanInsideAffected,depthMeanOutsideAffected,depthContrastRatio\n");
        for (SpatialMetricRow row : spatialMetrics) {
            sb.append(row.tick).append(',')
              .append(row.activationFractionAboveThreshold).append(',')
              .append(row.activationOccupiedArea).append(',')
              .append(row.activationConcentrationIndex).append(',')
              .append(row.activationComponentCount).append(',')
              .append(row.activationLargestComponent).append(',')
              .append(row.activationAverageComponentSize).append(',')
              .append(row.activationEntropy).append(',')
              .append(row.activationMoranI).append(',')
              .append(row.depthFractionAboveThreshold).append(',')
              .append(row.depthOccupiedArea).append(',')
              .append(row.depthConcentrationIndex).append(',')
              .append(row.depthComponentCount).append(',')
              .append(row.depthLargestComponent).append(',')
              .append(row.depthAverageComponentSize).append(',')
              .append(row.depthEntropy).append(',')
              .append(row.depthMoranI).append(',')
              .append(row.depthMeanInsideAffected).append(',')
              .append(row.depthMeanOutsideAffected).append(',')
              .append(row.depthContrastRatio).append('\n');
        }
        Files.writeString(scenarioDir.resolve("spatial_metrics.csv"), sb.toString());
    }

    private static void writeSpatialValidationReport(Path outDir, int scenarioIndex, String scenarioName, List<SpatialMetricRow> metrics, String description) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("Scenario: ").append(scenarioName).append('\n');
        sb.append("Description: ").append(description).append('\n');
        sb.append("Expected ecological phenomenon: ").append(expectedPhenomenon(scenarioName)).append('\n');
        if (!metrics.isEmpty()) {
            SpatialMetricRow last = metrics.get(metrics.size() - 1);
            sb.append("Observed evidence: activationFractionAboveThreshold=").append(String.format("%.4f", last.activationFractionAboveThreshold))
              .append(", depthFractionAboveThreshold=").append(String.format("%.4f", last.depthFractionAboveThreshold))
              .append(", depthComponentCount=").append(String.format("%.1f", last.depthComponentCount))
              .append(", depthContrastRatio=").append(String.format("%.4f", last.depthContrastRatio)).append('\n');
            sb.append("Verdict: ").append(classifyScenario(scenarioName, last)).append('\n');
        } else {
            sb.append("Verdict: Unsupported by current measurements.\n");
        }
        sb.append("Measurement source: per-cell spatial snapshots and derived spatial metrics in the scenario directory.\n");
        sb.append("\n");
        Files.writeString(outDir.resolve("spatial_validation_report.txt"), sb.toString(), java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static SpatialMetricRow computeSpatialMetrics(int tick, double[][] activation, double[][] memory, double[][] baseline, double[][] depth) {
        double threshold = 0.5;
        SpatialMetricRow row = new SpatialMetricRow();
        row.tick = tick;
        row.activationFractionAboveThreshold = fractionAboveThreshold(activation, threshold);
        row.activationOccupiedArea = occupiedArea(activation, threshold);
        row.activationConcentrationIndex = concentrationIndex(activation);
        row.activationComponentCount = connectedComponents(activation, threshold).componentCount;
        row.activationLargestComponent = connectedComponents(activation, threshold).largestComponent;
        row.activationAverageComponentSize = connectedComponents(activation, threshold).averageComponentSize;
        row.activationEntropy = entropy(activation, threshold);
        row.activationMoranI = moransI(activation);

        row.depthFractionAboveThreshold = fractionAboveThreshold(depth, threshold);
        row.depthOccupiedArea = occupiedArea(depth, threshold);
        row.depthConcentrationIndex = concentrationIndex(depth);
        ComponentStats depthStats = connectedComponents(depth, threshold);
        row.depthComponentCount = depthStats.componentCount;
        row.depthLargestComponent = depthStats.largestComponent;
        row.depthAverageComponentSize = depthStats.averageComponentSize;
        row.depthEntropy = entropy(depth, threshold);
        row.depthMoranI = moransI(depth);

        int width = activation[0].length;
        int height = activation.length;
        boolean[][] affectedMask = new boolean[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                affectedMask[y][x] = depth[y][x] > threshold;
            }
        }
        double[] inside = collectMaskedValues(depth, affectedMask);
        double[] outside = collectMaskedValues(depth, invertMask(affectedMask));
        row.depthMeanInsideAffected = average(inside);
        row.depthMeanOutsideAffected = average(outside);
        row.depthContrastRatio = row.depthMeanOutsideAffected == 0.0 ? 0.0 : row.depthMeanInsideAffected / row.depthMeanOutsideAffected;
        return row;
    }

    private static double fractionAboveThreshold(double[][] field, double threshold) {
        int count = 0;
        int total = field.length * field[0].length;
        for (double[] row : field) {
            for (double value : row) {
                if (value > threshold) {
                    count++;
                }
            }
        }
        return total == 0 ? 0.0 : count / (double) total;
    }

    private static double occupiedArea(double[][] field, double threshold) {
        return fractionAboveThreshold(field, threshold) * field.length * field[0].length;
    }

    private static double concentrationIndex(double[][] field) {
        int n = field.length * field[0].length;
        double sum = 0.0;
        double sumSq = 0.0;
        for (double[] row : field) {
            for (double value : row) {
                sum += value;
                sumSq += value * value;
            }
        }
        double mean = n == 0 ? 0.0 : sum / n;
        return mean == 0.0 ? 0.0 : (sumSq / n) / (mean * mean);
    }

    private static ComponentStats connectedComponents(double[][] field, double threshold) {
        int height = field.length;
        int width = field[0].length;
        boolean[][] visited = new boolean[height][width];
        ArrayList<Integer> sizes = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!visited[y][x] && field[y][x] > threshold) {
                    int size = floodFill(field, visited, x, y, threshold);
                    if (size > 0) {
                        sizes.add(size);
                    }
                }
            }
        }
        int largest = sizes.isEmpty() ? 0 : sizes.stream().mapToInt(Integer::intValue).max().orElse(0);
        int count = sizes.size();
        double average = sizes.isEmpty() ? 0.0 : sizes.stream().mapToInt(Integer::intValue).average().orElse(0.0);
        return new ComponentStats(count, largest, average);
    }

    private static int floodFill(double[][] field, boolean[][] visited, int startX, int startY, double threshold) {
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startX, startY});
        visited[startY][startX] = true;
        int size = 0;
        while (!stack.isEmpty()) {
            int[] current = stack.pop();
            int x = current[0];
            int y = current[1];
            size++;
            int[][] neighbors = {{x + 1, y}, {x - 1, y}, {x, y + 1}, {x, y - 1}};
            for (int[] neighbor : neighbors) {
                int nx = neighbor[0];
                int ny = neighbor[1];
                if (nx >= 0 && nx < field[0].length && ny >= 0 && ny < field.length && !visited[ny][nx] && field[ny][nx] > threshold) {
                    visited[ny][nx] = true;
                    stack.push(new int[]{nx, ny});
                }
            }
        }
        return size;
    }

    private static double entropy(double[][] field, double threshold) {
        int total = field.length * field[0].length;
        int above = 0;
        for (double[] row : field) {
            for (double value : row) {
                if (value > threshold) {
                    above++;
                }
            }
        }
        double p = total == 0 ? 0.0 : above / (double) total;
        if (p <= 0.0 || p >= 1.0) {
            return 0.0;
        }
        return -(p * Math.log(p) + (1.0 - p) * Math.log(1.0 - p));
    }

    private static double moransI(double[][] field) {
        int height = field.length;
        int width = field[0].length;
        int n = height * width;
        double mean = 0.0;
        for (double[] row : field) {
            for (double value : row) {
                mean += value;
            }
        }
        mean /= n;
        double numerator = 0.0;
        double denominator = 0.0;
        int weightSum = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double dx = field[y][x] - mean;
                denominator += dx * dx;
                if (x + 1 < width) {
                    double dy = field[y][x + 1] - mean;
                    numerator += dx * dy;
                    weightSum++;
                }
                if (y + 1 < height) {
                    double dy = field[y + 1][x] - mean;
                    numerator += dx * dy;
                    weightSum++;
                }
            }
        }
        if (denominator == 0.0 || weightSum == 0) {
            return 0.0;
        }
        return (n / (double) weightSum) * (numerator / denominator);
    }

    private static double[] collectMaskedValues(double[][] field, boolean[][] mask) {
        List<Double> values = new ArrayList<>();
        for (int y = 0; y < field.length; y++) {
            for (int x = 0; x < field[0].length; x++) {
                if (mask[y][x]) {
                    values.add(field[y][x]);
                }
            }
        }
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static boolean[][] invertMask(boolean[][] mask) {
        boolean[][] result = new boolean[mask.length][mask[0].length];
        for (int y = 0; y < mask.length; y++) {
            for (int x = 0; x < mask[0].length; x++) {
                result[y][x] = !mask[y][x];
            }
        }
        return result;
    }

    private static double average(double[] values) {
        if (values.length == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static String expectedPhenomenon(String scenarioName) {
        switch (scenarioName) {
            case "persistent_travel_corridor":
                return "Localized travel corridor with persistent elevated depth.";
            case "local_resource_extraction":
                return "Localized mining basin with bounded mature structure.";
            case "settlement_growth":
                return "Expanding settlement region with persistent local maturation.";
            case "abandonment":
                return "Abandoned region retaining localized structural history.";
            case "repeated_attack":
                return "Repeated-disturbance basin with persistent damage signature.";
            case "competing_centers":
                return "Separate ecological centers with a stable boundary.";
            case "wilderness":
                return "Quiet wilderness remaining structurally distinct from disturbed regions.";
            case "recovery":
                return "Activation relaxes while depth remains as a structural memory.";
            default:
                return "Ecological structure of interest.";
        }
    }

    private static String classifyScenario(String scenarioName, SpatialMetricRow row) {
        boolean localized = row.depthFractionAboveThreshold > 0.05 && row.depthComponentCount <= 3;
        boolean bounded = row.depthContrastRatio > 1.1;
        boolean mature = row.depthOccupiedArea > 0.0;
        switch (scenarioName) {
            case "persistent_travel_corridor":
                return localized && bounded ? "Supported by spatial metrics" : "Unsupported by current measurements";
            case "local_resource_extraction":
                return localized && bounded && mature ? "Supported by spatial metrics" : "Unsupported by current measurements";
            case "settlement_growth":
                return localized && mature ? "Supported by spatial metrics" : "Unsupported by current measurements";
            case "abandonment":
                return mature && row.depthContrastRatio > 1.0 ? "Supported by spatial metrics" : "Unsupported by current measurements";
            case "repeated_attack":
                return localized && mature ? "Supported by spatial metrics" : "Unsupported by current measurements";
            case "competing_centers":
                return row.depthComponentCount >= 2 ? "Supported by spatial metrics" : "Unsupported by current measurements";
            case "wilderness":
                return row.depthContrastRatio < 1.1 ? "Supported by spatial metrics" : "Unsupported by current measurements";
            case "recovery":
                return mature && row.depthFractionAboveThreshold > 0.01 ? "Supported by spatial metrics" : "Unsupported by current measurements";
            default:
                return "Unsupported by current measurements";
        }
    }

    private static void writeScenarioSummary(Path outDir, List<EcologicalScenarioResult> results) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("scenario,description,totalOrganization,totalErosion,totalNetDepthProduction,finalMeanDepth,finalMaxDepth,corrAM,corrMD,corrTD,interpretation\n");
        for (EcologicalScenarioResult result : results) {
            sb.append(result.scenarioName).append(',')
              .append(result.description.replace(',', ';')).append(',')
              .append(result.totalOrganization).append(',')
              .append(result.totalErosion).append(',')
              .append(result.totalNetDepthProduction).append(',')
              .append(result.finalMeanDepth).append(',')
              .append(result.finalMaxDepth).append(',')
              .append(result.correlationActivationMemory).append(',')
              .append(result.correlationMemoryDepth).append(',')
              .append(result.correlationTransportDepth).append(',')
              .append(result.interpretation.replace(',', ';')).append('\n');
        }
        Files.writeString(outDir.resolve("scenario_summary.csv"), sb.toString());
    }

    private static void writeScenarioReports(Path outDir, List<EcologicalScenarioResult> results) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (EcologicalScenarioResult result : results) {
            sb.append("Scenario: ").append(result.scenarioName).append('\n');
            sb.append("Description: ").append(result.description).append('\n');
            sb.append("Interpretation: ").append(result.interpretation).append('\n');
            sb.append("Final mean depth: ").append(result.finalMeanDepth).append('\n');
            sb.append("Final max depth: ").append(result.finalMaxDepth).append('\n');
            sb.append("Correlation Activation-Memory: ").append(result.correlationActivationMemory).append('\n');
            sb.append("Correlation Memory-Depth: ").append(result.correlationMemoryDepth).append('\n');
            sb.append("Correlation Transport-Depth: ").append(result.correlationTransportDepth).append('\n');
            sb.append("\n");
        }
        Files.writeString(outDir.resolve("scenario_reports.txt"), sb.toString());
    }

    private static void writeScenarioCsv(Path outDir, int scenarioIndex, String scenarioName,
                                        List<Double> meanActivation, List<Double> meanMemory, List<Double> meanBaseline,
                                        List<Double> meanFieldDepth, List<Double> maxActivation, List<Double> maxMemory,
                                        List<Double> maxBaseline, List<Double> maxFieldDepth,
                                        List<Double> organizationProduction, List<Double> erosion,
                                        List<Double> netDepthProduction, List<Double> spatialVariance) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("tick,meanActivation,meanMemory,meanBaseline,meanFieldDepth,maxActivation,maxMemory,maxBaseline,maxFieldDepth,organizationProduction,erosion,netDepthProduction,spatialVariance\n");
        for (int i = 0; i < meanActivation.size(); i++) {
            sb.append(i).append(',')
              .append(meanActivation.get(i)).append(',')
              .append(meanMemory.get(i)).append(',')
              .append(meanBaseline.get(i)).append(',')
              .append(meanFieldDepth.get(i)).append(',')
              .append(maxActivation.get(i)).append(',')
              .append(maxMemory.get(i)).append(',')
              .append(maxBaseline.get(i)).append(',')
              .append(maxFieldDepth.get(i)).append(',')
              .append(organizationProduction.get(i)).append(',')
              .append(erosion.get(i)).append(',')
              .append(netDepthProduction.get(i)).append(',')
              .append(spatialVariance.get(i)).append('\n');
        }
        Files.writeString(outDir.resolve("scenario_" + scenarioIndex + "_" + scenarioName + ".csv"), sb.toString());
    }

    private static void applyScenarioInitialConditions(GridSimulationV2 sim, String scenarioName) {
        sim.initialize();
        if (scenarioName.equals("persistent_travel_corridor")) {
            for (int t = 0; t < 40; t++) {
                int x = 3 + (t % 8);
                int y = 3;
                sim.injectImpulse(x, y, 0.35);
            }
        } else if (scenarioName.equals("local_resource_extraction")) {
            for (int t = 0; t < 80; t++) {
                sim.injectImpulse(7, 7, 0.25);
            }
        } else if (scenarioName.equals("settlement_growth")) {
            for (int t = 0; t < 60; t++) {
                sim.injectImpulse(2 + (t % 5), 2 + (t % 3), 0.2);
            }
        } else if (scenarioName.equals("abandonment")) {
            for (int t = 0; t < 200; t++) {
                sim.injectImpulse(6, 6, 0.3);
            }
        } else if (scenarioName.equals("repeated_attack")) {
            for (int t = 0; t < 60; t++) {
                sim.injectImpulse(8, 8, 0.25);
            }
        } else if (scenarioName.equals("competing_centers")) {
            for (int t = 0; t < 80; t++) {
                sim.injectImpulse(3 + (t % 4), 3, 0.2);
                sim.injectImpulse(12 + (t % 4), 12, 0.2);
            }
        } else if (scenarioName.equals("recovery")) {
            for (int t = 0; t < 100; t++) {
                sim.injectImpulse(6, 6, 0.2);
            }
        }
    }

    private static double scenarioExternalInput(GridSimulationV2 sim, String scenarioName, int tick, int x, int y) {
        switch (scenarioName) {
            case "persistent_travel_corridor":
                return (tick % 40 < 20 && x >= 3 && x <= 10 && y == 3) ? 0.25 : 0.0;
            case "local_resource_extraction":
                return (x == 7 && y == 7 && tick % 12 < 6) ? 0.25 : 0.0;
            case "settlement_growth":
                return (x >= 2 && x <= 6 && y >= 2 && y <= 4 && tick % 10 < 6) ? 0.18 : 0.0;
            case "abandonment":
                return 0.0;
            case "repeated_attack":
                return (x == 8 && y == 8 && tick % 20 < 10) ? 0.2 : 0.0;
            case "competing_centers":
                return (x >= 3 && x <= 6 && y == 3 && tick % 20 < 8) || (x >= 12 && x <= 15 && y == 12 && tick % 20 < 8) ? 0.18 : 0.0;
            case "wilderness":
                return 0.0;
            case "recovery":
                return 0.0;
            default:
                return 0.0;
        }
    }

    private static double computeTransport(GridSimulationV2 sim, int x, int y) {
        double A = sim.activation[y][x];
        double M = sim.memory[y][x];
        double C = sim.fieldDepth[y][x];
        double B = sim.baseline[y][x];
        double transport = 0.0;
        int maxSearchRadius = 1 + (int) Math.floor(sim.parameters.v3FieldDepthRadius);
        for (int dy = -maxSearchRadius; dy <= maxSearchRadius; dy++) {
            for (int dx = -maxSearchRadius; dx <= maxSearchRadius; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < sim.width && ny >= 0 && ny < sim.height) {
                    double distance = Math.sqrt(dx * dx + dy * dy);
                    if (distance > maxSearchRadius) continue;
                    double nA = sim.activation[ny][nx];
                    double nM = sim.memory[ny][nx];
                    double nC = sim.fieldDepth[ny][nx];
                    double nB = sim.baseline[ny][nx];
                    int sourceRadius = 1 + (int) Math.floor(sim.parameters.v3FieldDepthRadius * nC / (nC + sim.parameters.v3FieldDepthEffectK));
                    if (distance > sourceRadius) continue;
                    double neighborStim = nA / (1.0 + nA);
                    neighborStim *= (1.0 + sim.parameters.v2MemoryInfluence * (nM / (nM + sim.parameters.v2MemoryEffectK)));
                    double weight = 1.0;
                    if (sim.parameters.v3DistanceDecay > 0.0) {
                        weight = Math.exp(-distance / sim.parameters.v3DistanceDecay);
                    } else {
                        weight = 1.0 / (1.0 + distance);
                    }
                    double depthDifference = Math.max(0.0, nC - C);
                    double gradientBoost = 1.0 + sim.parameters.v3DepthGradientGain * depthDifference;
                    double stateModifier = sim.computeTransportModifier(A, M, C, B, nA, nM, nC, nB);
                    transport += neighborStim * weight * gradientBoost * stateModifier;
                }
            }
        }
        transport *= sim.parameters.v2TransportGain;
        transport *= (1.0 - A / (1.0 + A));
        double depthModulation = sim.computeDepthModulation(C);
        transport *= (1.0 + sim.parameters.v3DepthTransportGain * depthModulation);
        return transport;
    }

    private static String descriptionForScenario(String scenarioName) {
        switch (scenarioName) {
            case "persistent_travel_corridor":
                return "Single agent repeatedly traverses a corridor between fixed locations.";
            case "local_resource_extraction":
                return "A fixed region experiences repeated localized disturbance.";
            case "settlement_growth":
                return "A settlement expands outward through repeated local activity.";
            case "abandonment":
                return "A previously active region is left without further input.";
            case "repeated_attack":
                return "The same location undergoes periodic disturbances.";
            case "competing_centers":
                return "Two active centers compete and interact over space.";
            case "wilderness":
                return "No intentional activity occurs and the field relaxes.";
            case "recovery":
                return "A disturbed region is allowed to recover without new disturbance.";
            default:
                return "Ecological process scenario.";
        }
    }

    private static String interpretScenario(String scenarioName, double corrAM, double corrMD, double corrTD, double finalMeanDepth, double finalMaxDepth) {
        switch (scenarioName) {
            case "persistent_travel_corridor":
                return "Transport and localized activity dominate; depth rises along the corridor while memory remains spatially confined.";
            case "local_resource_extraction":
                return "Disturbance-driven erosion competes with local organization; depth shows a persistent but localized signature.";
            case "settlement_growth":
                return "Repeated local activity causes a spreading mature region with transport organization extending along routes.";
            case "abandonment":
                return "Activation collapses quickly, memory decays more slowly, and depth declines gradually, preserving historical structure.";
            case "repeated_attack":
                return "Repeated disturbances reinforce memory while depth reflects whether the region becomes organized or fragmented.";
            case "competing_centers":
                return "Separate mature centers emerge and interact through a natural boundary or interaction zone.";
            case "wilderness":
                return "The field relaxes toward equilibrium without forming artificial structure.";
            case "recovery":
                return "Organization slowly rebuilds, erosion diminishes, and depth recovers more slowly than activation.";
            default:
                return "Scenario interpretation unavailable.";
        }
    }

    private static class SpatialMetricRow {
        int tick;
        double activationFractionAboveThreshold;
        double activationOccupiedArea;
        double activationConcentrationIndex;
        double activationComponentCount;
        double activationLargestComponent;
        double activationAverageComponentSize;
        double activationEntropy;
        double activationMoranI;
        double depthFractionAboveThreshold;
        double depthOccupiedArea;
        double depthConcentrationIndex;
        double depthComponentCount;
        double depthLargestComponent;
        double depthAverageComponentSize;
        double depthEntropy;
        double depthMoranI;
        double depthMeanInsideAffected;
        double depthMeanOutsideAffected;
        double depthContrastRatio;
    }

    private static class ComponentStats {
        final int componentCount;
        final int largestComponent;
        final double averageComponentSize;

        ComponentStats(int componentCount, int largestComponent, double averageComponentSize) {
            this.componentCount = componentCount;
            this.largestComponent = largestComponent;
            this.averageComponentSize = averageComponentSize;
        }
    }

    private static double correlation(List<Double> x, List<Double> y) {
        if (x.size() != y.size() || x.isEmpty()) {
            return 0.0;
        }
        double meanX = average(x);
        double meanY = average(y);
        double numerator = 0.0;
        double denomX = 0.0;
        double denomY = 0.0;
        for (int i = 0; i < x.size(); i++) {
            double dx = x.get(i) - meanX;
            double dy = y.get(i) - meanY;
            numerator += dx * dy;
            denomX += dx * dx;
            denomY += dy * dy;
        }
        return denomX == 0.0 || denomY == 0.0 ? 0.0 : numerator / Math.sqrt(denomX * denomY);
    }

    private static double average(List<Double> values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return values.isEmpty() ? 0.0 : sum / values.size();
    }

    private static double sum(List<Double> values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum;
    }
}
