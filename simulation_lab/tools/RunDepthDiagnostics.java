import java.util.ArrayList;
import java.util.List;

public class RunDepthDiagnostics {
    private static class RunSummary {
        final int tick;
        final double meanActivation;
        final double meanMemory;
        final double meanFieldDepth;
        final double maxFieldDepth;
        final double totalOrganization;
        final double totalErosion;
        final double netDepthProduction;

        RunSummary(int tick, double meanActivation, double meanMemory, double meanFieldDepth,
                   double maxFieldDepth, double totalOrganization, double totalErosion,
                   double netDepthProduction) {
            this.tick = tick;
            this.meanActivation = meanActivation;
            this.meanMemory = meanMemory;
            this.meanFieldDepth = meanFieldDepth;
            this.maxFieldDepth = maxFieldDepth;
            this.totalOrganization = totalOrganization;
            this.totalErosion = totalErosion;
            this.netDepthProduction = netDepthProduction;
        }
    }

    public static void main(String[] args) throws Exception {
        ParameterSet params = new ParameterSet();
        params.v2TransportGain = 0.08;
        params.v2ActivationDecay = 0.35;
        params.v3FieldDepthDiffusionGain = 0.0002;
        params.depthGrowthGain = 0.04;
        params.depthErosionGain = 0.03;

        ExperimentConfig config = new ExperimentConfig(
                "depth_diagnostics",
                "inject_then_quiet_long",
                16,
                16,
                300,
                params,
                7,
                7,
                1.0,
                12345L
        );

        GridSimulationV2 sim = new GridSimulationV2(config.width, config.height, config.ticks, config.parameters);
        List<RunSummary> summaries = new ArrayList<>();

        for (int t = 0; t < config.ticks; t++) {
            double[][] nextActivation = new double[sim.height][sim.width];
            double[][] nextMemory = new double[sim.height][sim.width];
            double[][] nextBaseline = new double[sim.height][sim.width];
            double[][] nextDepth = new double[sim.height][sim.width];

            double totalActivation = 0.0;
            double totalMemory = 0.0;
            double totalDepth = 0.0;
            double totalOrganization = 0.0;
            double totalErosion = 0.0;
            double netDepthProduction = 0.0;
            double maxDepth = 0.0;

            for (int y = 0; y < sim.height; y++) {
                for (int x = 0; x < sim.width; x++) {
                    double A = sim.activation[y][x];
                    double M = sim.memory[y][x];
                    double B = sim.baseline[y][x];
                    double C = sim.fieldDepth[y][x];

                    SpatialStencil stencil = new SpatialStencil(x, y, sim.activation, sim.memory, sim.fieldDepth, sim.baseline);
                    double transport = computeTransport(sim, x, y);
                    double external = sim.scenarioInputForTick(config, t, x, y);
                    double organization = sim.computeFieldOrganization(A, B, C, stencil, external, transport);
                    double erosion = sim.computeFieldErosion(A, B, C, stencil, external, transport);
                    GridSimulationV2.CellUpdateResult update = sim.integrateUnifiedCellStep(x, y, external, transport);

                    nextActivation[y][x] = update.nextActivation;
                    nextMemory[y][x] = update.nextMemory;
                    nextBaseline[y][x] = update.nextBaseline;
                    nextDepth[y][x] = update.nextDepth;

                    totalActivation += update.nextActivation;
                    totalMemory += update.nextMemory;
                    totalDepth += update.nextDepth;
                    totalOrganization += organization;
                    totalErosion += erosion;
                    netDepthProduction += (params.depthGrowthGain * organization - params.depthErosionGain * erosion);
                    maxDepth = Math.max(maxDepth, update.nextDepth);
                }
            }

            sim.activation = nextActivation;
            sim.memory = nextMemory;
            sim.baseline = nextBaseline;
            sim.fieldDepth = nextDepth;

            summaries.add(new RunSummary(
                    t,
                    totalActivation / (sim.width * sim.height),
                    totalMemory / (sim.width * sim.height),
                    totalDepth / (sim.width * sim.height),
                    maxDepth,
                    totalOrganization,
                    totalErosion,
                    netDepthProduction
            ));
        }

        printSummary(summaries);
        printDepthHeatmap(sim, 0);
        printDepthHeatmap(sim, config.ticks - 1);
    }

    private static void printSummary(List<RunSummary> summaries) {
        System.out.println("tick,meanActivation,meanMemory,meanFieldDepth,maxFieldDepth,totalOrganization,totalErosion,netDepthProduction");
        for (RunSummary row : summaries) {
            System.out.printf("%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                    row.tick,
                    row.meanActivation,
                    row.meanMemory,
                    row.meanFieldDepth,
                    row.maxFieldDepth,
                    row.totalOrganization,
                    row.totalErosion,
                    row.netDepthProduction);
        }
    }

    private static void printDepthHeatmap(GridSimulationV2 sim, int tick) {
        System.out.println("\nDepth heatmap at tick " + tick + ":");
        for (int y = 0; y < sim.height; y++) {
            StringBuilder line = new StringBuilder();
            for (int x = 0; x < sim.width; x++) {
                double value = sim.fieldDepth[y][x];
                if (value > 0.05) {
                    line.append('#');
                } else if (value > 0.01) {
                    line.append('~');
                } else {
                    line.append('.');
                }
            }
            System.out.println(line);
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
}
