import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SpatialAudit {
    private static final int WIDTH = 40;
    private static final int HEIGHT = 40;
    private static final int TICKS = 20000;
    private static final int SNAP_TICKS[] = {0, 1000, 10000, TICKS - 1};
    private static final Path OUT = Path.of("output/region_phase/spatial_audit");

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        ParameterSet params = new ParameterSet();
        params.v2AdaptiveRelaxationEnabled = true;
        GridSimulationV2 simulation = new GridSimulationV2(WIDTH, HEIGHT, TICKS, params);
        initState(simulation);

        double totalTransportMagnitude = 0.0;
        double totalAbsTransport = 0.0;
        double maxTransport = 0.0;
        int transportSamples = 0;
        double sumSignedTransport = 0.0;
        double sumSquaredTransport = 0.0;

        List<StateSnapshot> snapshots = new ArrayList<>();
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
                    if (tick >= TICKS - 5000) {
                        totalTransportMagnitude += Math.abs(transport);
                        totalAbsTransport += Math.abs(transport);
                        maxTransport = Math.max(maxTransport, Math.abs(transport));
                        sumSignedTransport += transport;
                        sumSquaredTransport += transport * transport;
                        transportSamples++;
                    }
                    GridSimulationV2.CellUpdateResult update = simulation.computeCellUpdate(
                            simulation.activation[y][x],
                            simulation.memory[y][x],
                            simulation.baseline[y][x],
                            simulation.fieldDepth[y][x],
                            0.0,
                            transport,
                            true);
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
            for (int snap : SNAP_TICKS) {
                if (tick == snap) {
                    snapshots.add(new StateSnapshot(tick, copyGrid(simulation.activation), copyGrid(simulation.baseline), copyGrid(simulation.memory), copyGrid(simulation.fieldDepth)));
                }
            }
        }

        writeSnapshots(snapshots);
        writeSummary(totalTransportMagnitude, totalAbsTransport, maxTransport, transportSamples, sumSignedTransport, sumSquaredTransport);
        writeSpatialStats(simulation, OUT.resolve("final_spatial_stats.csv"));
    }

    private static void initState(GridSimulationV2 simulation) {
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
        simulation.activation = activation;
        simulation.memory = memory;
        simulation.baseline = baseline;
        simulation.fieldDepth = fieldDepth;
    }

    private static void writeSnapshots(List<StateSnapshot> snapshots) throws IOException {
        for (StateSnapshot snapshot : snapshots) {
            writeGrid(snapshot.tick, "activation", snapshot.activation);
            writeGrid(snapshot.tick, "baseline", snapshot.baseline);
            writeGrid(snapshot.tick, "memory", snapshot.memory);
            writeGrid(snapshot.tick, "field_depth", snapshot.fieldDepth);
        }
    }

    private static void writeGrid(int tick, String name, double[][] grid) throws IOException {
        Path path = OUT.resolve(name + "_tick_" + tick + ".csv");
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (x > 0) sb.append(',');
                sb.append(String.format(java.util.Locale.US, "%.15f", grid[y][x]));
            }
            sb.append(System.lineSeparator());
        }
        Files.writeString(path, sb.toString());
    }

    private static void writeSummary(double totalTransportMagnitude, double totalAbsTransport, double maxTransport, int transportSamples, double sumSignedTransport, double sumSquaredTransport) throws IOException {
        Path path = OUT.resolve("transport_metrics.csv");
        StringBuilder sb = new StringBuilder();
        sb.append("measure,value").append(System.lineSeparator());
        sb.append("transportSamples,").append(transportSamples).append(System.lineSeparator());
        sb.append("totalTransportMagnitude,").append(String.format(java.util.Locale.US, "%.15f", totalTransportMagnitude)).append(System.lineSeparator());
        sb.append("meanAbsTransport,").append(String.format(java.util.Locale.US, "%.15f", transportSamples == 0 ? 0.0 : totalAbsTransport / transportSamples)).append(System.lineSeparator());
        sb.append("maxTransport,").append(String.format(java.util.Locale.US, "%.15f", maxTransport)).append(System.lineSeparator());
        sb.append("sumSignedTransport,").append(String.format(java.util.Locale.US, "%.15f", sumSignedTransport)).append(System.lineSeparator());
        sb.append("meanSignedTransport,").append(String.format(java.util.Locale.US, "%.15f", transportSamples == 0 ? 0.0 : sumSignedTransport / transportSamples)).append(System.lineSeparator());
        sb.append("rmsTransport,").append(String.format(java.util.Locale.US, "%.15f", transportSamples == 0 ? 0.0 : Math.sqrt(sumSquaredTransport / transportSamples))).append(System.lineSeparator());
        Files.writeString(path, sb.toString());
    }

    private static void writeSpatialStats(GridSimulationV2 simulation, Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("variable,min,max,stddev,variance,max_neighbor_diff").append(System.lineSeparator());
        writeStats(sb, "activation", simulation.activation);
        writeStats(sb, "baseline", simulation.baseline);
        writeStats(sb, "memory", simulation.memory);
        writeStats(sb, "field_depth", simulation.fieldDepth);
        Files.writeString(path, sb.toString());
    }

    private static void writeStats(StringBuilder sb, String name, double[][] grid) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        double sumSq = 0.0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                double v = grid[y][x];
                min = Math.min(min, v);
                max = Math.max(max, v);
                sum += v;
                sumSq += v * v;
            }
        }
        double mean = sum / (WIDTH * HEIGHT);
        double variance = (sumSq / (WIDTH * HEIGHT)) - mean * mean;
        double std = Math.sqrt(Math.max(0.0, variance));
        double maxNeighborDiff = 0.0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                for (int[] delta : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + delta[0];
                    int ny = y + delta[1];
                    if (nx >= 0 && nx < WIDTH && ny >= 0 && ny < HEIGHT) {
                        double diff = Math.abs(grid[y][x] - grid[ny][nx]);
                        maxNeighborDiff = Math.max(maxNeighborDiff, diff);
                    }
                }
            }
        }
        sb.append(name).append(',')
          .append(String.format(java.util.Locale.US, "%.15f", min)).append(',')
          .append(String.format(java.util.Locale.US, "%.15f", max)).append(',')
          .append(String.format(java.util.Locale.US, "%.15f", std)).append(',')
          .append(String.format(java.util.Locale.US, "%.15f", variance)).append(',')
          .append(String.format(java.util.Locale.US, "%.15f", maxNeighborDiff)).append(System.lineSeparator());
    }

    private static double[][] copyGrid(double[][] src) {
        double[][] out = new double[HEIGHT][WIDTH];
        for (int y = 0; y < HEIGHT; y++) {
            System.arraycopy(src[y], 0, out[y], 0, WIDTH);
        }
        return out;
    }

    private record Seed(int x, int y, double activation, double memory, double baseline, double fieldDepth) {}
    private record StateSnapshot(int tick, double[][] activation, double[][] baseline, double[][] memory, double[][] fieldDepth) {}
}
