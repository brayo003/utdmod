import java.util.ArrayList;
import java.util.List;

public class GridSimulationV2 {
    public final int width;
    public final int height;
    public final int ticks;
    public final ParameterSet parameters;
    public double[][] activation;
    public double[][] memory;
    public double[][] fieldDepth;

    public final List<Double> totalActivationHistory;
    public final List<Double> maxActivationHistory;
    public final List<Double> totalMemoryHistory;
    public final List<Double> maxMemoryHistory;
    public final List<Double> totalFieldDepthHistory;
    public final List<Double> maxFieldDepthHistory;

    public GridSimulationV2(int width, int height, int ticks, ParameterSet parameters) {
        this.width = width;
        this.height = height;
        this.ticks = ticks;
        this.parameters = parameters.copy();
        this.activation = new double[height][width];
        this.memory = new double[height][width];
        this.fieldDepth = new double[height][width];
        this.totalActivationHistory = new ArrayList<>();
        this.maxActivationHistory = new ArrayList<>();
        this.totalMemoryHistory = new ArrayList<>();
        this.maxMemoryHistory = new ArrayList<>();
        this.totalFieldDepthHistory = new ArrayList<>();
        this.maxFieldDepthHistory = new ArrayList<>();
        initialize();
    }

    public void initialize() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                activation[y][x] = 0.0;
                memory[y][x] = 0.0;
                fieldDepth[y][x] = 0.0;
            }
        }
    }

    public void injectImpulse(int x, int y, double amount) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            activation[y][x] += Math.max(0.0, amount);
        }
    }

    public void runExperiment(ExperimentConfig config) {
        initialize();
        injectImpulse(config.impulseX, config.impulseY, 1.0);
        for (int t = 0; t < ticks; t++) {
            double[][] nextA = new double[height][width];
            double[][] nextM = new double[height][width];
            double[][] nextC = new double[height][width];
            double totalA = 0.0;
            double maxA = 0.0;
            double totalM = 0.0;
            double maxM = 0.0;
            double totalC = 0.0;
            double maxC = 0.0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double A = activation[y][x];
                    double M = memory[y][x];
                    double C = fieldDepth[y][x];
                    // transport: neighbors stimulate this cell (source does not lose)
                    double transport = 0.0;
                    int maxSearchRadius = 1 + (int) Math.floor(parameters.v3FieldDepthRadius);
                    for (int dy = -maxSearchRadius; dy <= maxSearchRadius; dy++) {
                        for (int dx = -maxSearchRadius; dx <= maxSearchRadius; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                                double distance = Math.sqrt(dx * dx + dy * dy);
                                if (distance > maxSearchRadius) continue;
                                double nA = activation[ny][nx];
                                double nM = memory[ny][nx];
                                double nC = fieldDepth[ny][nx];
                                int sourceRadius = 1 + (int) Math.floor(parameters.v3FieldDepthRadius * nC / (nC + parameters.v3FieldDepthEffectK));
                                if (distance > sourceRadius) continue;
                                // neighbor contribution increases with neighbor activation
                                // saturating neighbor contribution: prevents runaway from large nA
                                double neighborStim = nA / (1.0 + nA);
                                // amplify if neighbor has memory (asymmetric reinforcement)
                                neighborStim *= (1.0 + parameters.v2MemoryInfluence * (nM / (nM + parameters.v2MemoryEffectK)));
                                double weight = 1.0;
                                if (parameters.v3DistanceDecay > 0.0) {
                                    weight = Math.exp(-distance / parameters.v3DistanceDecay);
                                } else {
                                    weight = 1.0 / (1.0 + distance);
                                }
                                transport += neighborStim * weight;
                            }
                        }
                    }
                    transport *= parameters.v2TransportGain;
                    // reduce incoming transport when this cell already has high activation
                    transport *= (1.0 - A / (1.0 + A));

                    // external input only on initial tick for single impulse scenario
                    double external = 0.0;
                    if (t == 0 && config != null && "single_impulse".equals(config.scenario)) {
                        if (x == config.impulseX && y == config.impulseY) external = 1.0;
                    }

                    // activation update: current retained minus decay plus transport and external
                    // memory reduces decay; field depth no longer affects decay
                    double memTerm = (1.0 - (M / (M + parameters.v2MemoryEffectK)));
                    double decayFactor = parameters.v2ActivationDecay * memTerm;
                    double nextActivation = Math.max(0.0, A * Math.max(0.0, 1.0 - decayFactor) + transport + external);
                    if (parameters.v2ClampMax > 0.0) nextActivation = Math.min(nextActivation, parameters.v2ClampMax);

                    // memory update: slowly accumulates from activation, decays slowly
                    // memory growth saturates with activation magnitude
                    double deltaMemory = parameters.v2MemoryGain * (A / (1.0 + A));
                    double nextMemory = Math.max(0.0, M + deltaMemory - parameters.v2MemoryDecay * M);

                    // field depth update: very slow structural growth from persistent activity & memory
                    double deltaDepth = parameters.v3FieldDepthGain * (A * M);
                    // saturate tiny per-tick growth to avoid large jumps
                    if (deltaDepth > 1.0) deltaDepth = 1.0;
                    double nextDepth = Math.max(0.0, C + deltaDepth - parameters.v3FieldDepthDecay * C);

                    nextA[y][x] = nextActivation;
                    nextM[y][x] = nextMemory;
                    nextC[y][x] = nextDepth;

                    totalA += nextActivation;
                    maxA = Math.max(maxA, nextActivation);
                    totalM += nextMemory;
                    maxM = Math.max(maxM, nextMemory);
                    totalC += nextDepth;
                    maxC = Math.max(maxC, nextDepth);
                }
            }
            activation = nextA;
            memory = nextM;
            fieldDepth = nextC;
            totalActivationHistory.add(totalA);
            maxActivationHistory.add(maxA);
            totalMemoryHistory.add(totalM);
            maxMemoryHistory.add(maxM);
            totalFieldDepthHistory.add(totalC);
            maxFieldDepthHistory.add(maxC);
        }
    }

    public double[][] copyActivationGrid() {
        double[][] snapshot = new double[height][width];
        for (int y = 0; y < height; y++) System.arraycopy(activation[y], 0, snapshot[y], 0, width);
        return snapshot;
    }

    public double[][] copyMemoryGrid() {
        double[][] snapshot = new double[height][width];
        for (int y = 0; y < height; y++) System.arraycopy(memory[y], 0, snapshot[y], 0, width);
        return snapshot;
    }
}
