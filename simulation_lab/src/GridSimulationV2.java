import java.util.ArrayList;
import java.util.List;

public class GridSimulationV2 {
    public static class EnergyAuditRow {
        public final int tick;
        public final double activation;
        public final double baseline;
        public final double memory;
        public final double fieldDepth;
        public final double transport;
        public final double injectionContribution;
        public final double relaxationContribution;
        public final double baselineContribution;
        public final double transportContribution;
        public final double memoryContribution;
        public final double depthContribution;
        public final double netDeltaActivation;
        public final double baselineDrive;
        public final double baselineHealing;
        public final double memoryGain;
        public final double memoryDecay;
        public final double depthGain;
        public final double depthDecay;

        public EnergyAuditRow(int tick, double activation, double baseline, double memory, double fieldDepth, double transport,
                              double injectionContribution, double relaxationContribution,
                              double baselineContribution, double transportContribution, double memoryContribution,
                              double depthContribution, double netDeltaActivation,
                              double baselineDrive, double baselineHealing,
                              double memoryGain, double memoryDecay,
                              double depthGain, double depthDecay) {
            this.tick = tick;
            this.activation = activation;
            this.baseline = baseline;
            this.memory = memory;
            this.fieldDepth = fieldDepth;
            this.transport = transport;
            this.injectionContribution = injectionContribution;
            this.relaxationContribution = relaxationContribution;
            this.baselineContribution = baselineContribution;
            this.transportContribution = transportContribution;
            this.memoryContribution = memoryContribution;
            this.depthContribution = depthContribution;
            this.netDeltaActivation = netDeltaActivation;
            this.baselineDrive = baselineDrive;
            this.baselineHealing = baselineHealing;
            this.memoryGain = memoryGain;
            this.memoryDecay = memoryDecay;
            this.depthGain = depthGain;
            this.depthDecay = depthDecay;
        }
    }

    /**
     * Integrated step using unified field derivative approach.
     * Computes spatial operators once, applies coupled dynamics, and advances state.
     * This replaces the individual computeActivationReaction, computeMemoryUpdate, computeDepthUpdate calls.
     */
    private void integrateUnifiedFieldStep(int x, int y, int t, ExperimentConfig config,
                                            double[][] nextA, double[][] nextM, double[][] nextB, double[][] nextC,
                                            StatisticsAccumulator stats) {
        double A = activation[y][x];
        double M = memory[y][x];
        double B = baseline[y][x];
        double C = fieldDepth[y][x];

        // Compute spatial stencil once for all equations
        SpatialStencil stencil = new SpatialStencil(x, y, activation, memory, fieldDepth, baseline);

        // Compute transport (incoming activation from neighbors)
        double transport = computeTransport(x, y);

        // Get external input for this tick
        double external = scenarioInputForTick(config, t, x, y);

        // Compute all four derivatives simultaneously from same state
        FieldDerivative deriv = computeUnifiedFieldDerivative(x, y, stencil, external, transport);

        // Euler integration: S(t+1) = S(t) + dt * dS/dt (dt=1.0 per tick)
        double dt = 1.0;
        double nextActivation = clamp(A + dt * deriv.dActivation, 0.0, parameters.v2ClampMax);
        double nextMemory = clamp(M + dt * deriv.dMemory, 0.0, parameters.v2ClampMax);
        double nextDepth = clamp(C + dt * deriv.dDepth, 0.0, parameters.v2ClampMax);
        double nextBaseline = clamp(B + dt * deriv.dBaseline, 0.0, parameters.v2ClampMax);

        // Store results
        nextA[y][x] = nextActivation;
        nextM[y][x] = nextMemory;
        nextB[y][x] = nextBaseline;
        nextC[y][x] = nextDepth;

        // Accumulate statistics for telemetry
        stats.totalA += nextActivation;
        stats.totalM += nextMemory;
        stats.totalC += nextDepth;
        stats.totalBaseline += nextBaseline;
        stats.maxA = Math.max(stats.maxA, nextActivation);
        stats.maxM = Math.max(stats.maxM, nextMemory);
        stats.maxC = Math.max(stats.maxC, nextDepth);
        stats.totalInjection += external;
        stats.totalTransport += transport;
        stats.totalActivationChange += nextActivation - A;
        stats.totalMemoryChange += nextMemory - M;
        stats.totalBaselineChange += nextBaseline - B;
    }

    /**
     * Compute transport from neighbors to this cell.
     * Returns incoming activation stimulus modulated by neighbor state.
     */
    private double computeTransport(int x, int y) {
        double A = activation[y][x];
        double M = memory[y][x];
        double C = fieldDepth[y][x];
        double B = baseline[y][x];

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
                    double nB = baseline[ny][nx];
                    int sourceRadius = 1 + (int) Math.floor(parameters.v3FieldDepthRadius * nC / (nC + parameters.v3FieldDepthEffectK));
                    if (distance > sourceRadius) continue;
                    double neighborStim = nA / (1.0 + nA);
                    neighborStim *= (1.0 + parameters.v2MemoryInfluence * (nM / (nM + parameters.v2MemoryEffectK)));
                    double weight = 1.0;
                    if (parameters.v3DistanceDecay > 0.0) {
                        weight = Math.exp(-distance / parameters.v3DistanceDecay);
                    } else {
                        weight = 1.0 / (1.0 + distance);
                    }
                    double depthDifference = Math.max(0.0, nC - C);
                    double gradientBoost = 1.0 + parameters.v3DepthGradientGain * depthDifference;
                    double stateModifier = computeTransportModifier(A, M, C, B, nA, nM, nC, nB);
                    transport += neighborStim * weight * gradientBoost * stateModifier;
                }
            }
        }
        transport *= parameters.v2TransportGain;
        transport *= (1.0 - A / (1.0 + A));
        double depthModulation = computeDepthModulation(C);
        transport *= (1.0 + parameters.v3DepthTransportGain * depthModulation);
        return transport;
    }

    /**
     * Helper class for accumulating statistics during integration.
     */
    static class StatisticsAccumulator {
        public double totalA = 0, maxA = 0;
        public double totalM = 0, maxM = 0;
        public double totalC = 0, maxC = 0;
        public double totalBaseline = 0;
        public double totalInjection = 0;
        public double totalTransport = 0;
        public double totalActivationChange = 0;
        public double totalMemoryChange = 0;
        public double totalBaselineChange = 0;
    }

    public static class CellUpdateResult {
        public final double nextActivation;
        public final double nextMemory;
        public final double nextBaseline;
        public final double nextDepth;
        public final double injectionContribution;
        public final double relaxationContribution;
        public final double baselineContribution;
        public final double transportContribution;
        public final double memoryContribution;
        public final double depthContribution;
        public final double netDeltaActivation;
        public final double baselineDrive;
        public final double baselineHealing;
        public final double memoryGain;
        public final double memoryDecay;
        public final double depthGain;
        public final double depthDecay;

        public CellUpdateResult(double nextActivation, double nextMemory, double nextBaseline, double nextDepth,
                                double injectionContribution, double relaxationContribution,
                                double baselineContribution, double transportContribution, double memoryContribution,
                                double depthContribution, double netDeltaActivation,
                                double baselineDrive, double baselineHealing,
                                double memoryGain, double memoryDecay,
                                double depthGain, double depthDecay) {
            this.nextActivation = nextActivation;
            this.nextMemory = nextMemory;
            this.nextBaseline = nextBaseline;
            this.nextDepth = nextDepth;
            this.injectionContribution = injectionContribution;
            this.relaxationContribution = relaxationContribution;
            this.baselineContribution = baselineContribution;
            this.transportContribution = transportContribution;
            this.memoryContribution = memoryContribution;
            this.depthContribution = depthContribution;
            this.netDeltaActivation = netDeltaActivation;
            this.baselineDrive = baselineDrive;
            this.baselineHealing = baselineHealing;
            this.memoryGain = memoryGain;
            this.memoryDecay = memoryDecay;
            this.depthGain = depthGain;
            this.depthDecay = depthDecay;
        }
    }
    public final int width;
    public final int height;
    public final int ticks;
    public final ParameterSet parameters;
    public double[][] activation;
    public double[][] memory;
    public double[][] baseline;
    public double[][] fieldDepth;

    public final List<Double> totalActivationHistory;
    public final List<Double> baselineHistory;
    public final List<Double> activationChangeHistory;
    public final List<Double> baselineChangeHistory;
    public final List<Double> memoryChangeHistory;
    public final List<Double> maxActivationHistory;
    public final List<Double> totalMemoryHistory;
    public final List<Double> maxMemoryHistory;
    public final List<Double> totalFieldDepthHistory;
    public final List<Double> maxFieldDepthHistory;
    public final List<Double> transportHistory;
    public final List<Double> effectiveRelaxationHistory;
    public final List<Double> injectionHistory;
    public final List<Double> recoveryHistory;

    public GridSimulationV2(int width, int height, int ticks, ParameterSet parameters) {
        this.width = width;
        this.height = height;
        this.ticks = ticks;
        this.parameters = parameters.copy();
        this.activation = new double[height][width];
        this.memory = new double[height][width];
        this.baseline = new double[height][width];
        this.fieldDepth = new double[height][width];
        this.totalActivationHistory = new ArrayList<>();
        this.baselineHistory = new ArrayList<>();
        this.activationChangeHistory = new ArrayList<>();
        this.baselineChangeHistory = new ArrayList<>();
        this.memoryChangeHistory = new ArrayList<>();
        this.maxActivationHistory = new ArrayList<>();
        this.totalMemoryHistory = new ArrayList<>();
        this.maxMemoryHistory = new ArrayList<>();
        this.totalFieldDepthHistory = new ArrayList<>();
        this.maxFieldDepthHistory = new ArrayList<>();
        this.transportHistory = new ArrayList<>();
        this.effectiveRelaxationHistory = new ArrayList<>();
        this.injectionHistory = new ArrayList<>();
        this.recoveryHistory = new ArrayList<>();
        initialize();
    }

    public void initialize() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double seed = 0.0005 * (((x * 17 + y * 29) % 11) - 5);
                activation[y][x] = seed;
                memory[y][x] = 0.001 * (((x * 7 + y * 13) % 9) - 4);
                baseline[y][x] = parameters.v2BaselineResting + 0.0002 * (((x * 5 + y * 3) % 7) - 3);
                fieldDepth[y][x] = 0.0002 * (((x * 11 + y * 7) % 13) - 6);
            }
        }
    }

    public void injectImpulse(int x, int y, double amount) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            activation[y][x] += Math.max(0.0, amount);
        }
    }

    public double computeEffectiveRelaxationCoefficient(double tension, double memory, double depth) {
        if (!parameters.v2AdaptiveRelaxationEnabled) {
            return parameters.v2ActivationDecay;
        }
        double sigmoid = 1.0 / (1.0 + Math.exp(-parameters.v2RelaxationK * (tension - parameters.v2RelaxationTc)));
        double tensionTerms = parameters.v2RelaxationLambdaMin + parameters.v2RelaxationLambdaGain * sigmoid;
        double memoryRetention = Math.exp(-parameters.v2RelaxationMemoryBeta * memory);
        double depthRetention = 1.0 / (1.0 + parameters.v2RelaxationDepthGamma * depth);
        return tensionTerms * memoryRetention * depthRetention;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double computeDepthFeedback(double depth) {
        double depthScale = Math.max(1e-6, parameters.v3FieldDepthEffectK);
        return Math.min(1.0, depth / (depth + depthScale));
    }

    /**
     * Compute the instantaneous organization source term O for Field Depth.
     *
     * This function answers only one question: how organized is the current field state?
     * It is intentionally stateless and uses only instantaneous observables derived from the
     * current activation, transport, and local spatial structure. No persistence, recovery,
     * or history counters are used here.
     *
     * Implemented organization functional:
     *   O = 0.30*C + 0.25*T + 0.20*F + 0.15*N + 0.10*S
     * where
     *   C = spatial coherence = 1 / (1 + |∇²A|)
     *   T = transport efficiency = J / (1 + J)
     *   F = flow alignment = 1 / (1 + |∇A - J|)
     *   N = neighborhood agreement = 1 / (1 + |∇A|)
     *   S = structural smoothness = 1 / (1 + |∇D|)
     */
    double computeFieldOrganization(double activation, double baseline, double depth,
                                            SpatialStencil stencil, double external, double transport) {
        double spatialCoherence = 1.0 / (1.0 + Math.abs(stencil.activationLaplacian));
        double transportEfficiency = transport / (1.0 + transport);
        double flowAlignment = 1.0 / (1.0 + Math.abs(stencil.activationGradient - Math.max(0.0, transport)));
        double neighborhoodAgreement = 1.0 / (1.0 + Math.abs(stencil.activationGradient));
        double structuralSmoothness = 1.0 / (1.0 + Math.abs(stencil.depthGradient));

        double organization = 0.30 * spatialCoherence
                + 0.25 * transportEfficiency
                + 0.20 * flowAlignment
                + 0.15 * neighborhoodAgreement
                + 0.10 * structuralSmoothness;
        return clamp(organization, 0.0, 1.0);
    }

    /**
     * Compute the instantaneous erosion term E for Field Depth.
     *
     * Erosion represents structural degradation caused by disorder, fragmentation, and rapid
     * local fluctuations. It is deliberately independent from organization so that depth can
     * grow under coherent conditions and decay under incoherent ones.
     */
    double computeFieldErosion(double activation, double baseline, double depth,
                                       SpatialStencil stencil, double external, double transport) {
        double localDisorder = Math.min(1.0, Math.abs(stencil.activationLaplacian) + Math.abs(stencil.memoryLaplacian));
        double fragmentation = Math.min(1.0, Math.abs(stencil.activationGradient) + Math.abs(stencil.memoryGradient));
        double rapidFluctuation = Math.min(1.0, Math.abs(stencil.activationCurvature) + Math.abs(stencil.memoryCurvature));

        double erosion = 0.45 * localDisorder
                + 0.35 * fragmentation
                + 0.20 * rapidFluctuation;
        return clamp(erosion, 0.0, 1.0);
    }

    private double computeMemoryFormation(double activation, double baseline, double depth) {
        double activationDrive = Math.max(0.0, activation - parameters.memoryFormationThreshold);
        if (activationDrive <= 0.0) {
            return 0.0;
        }
        double drive = activationDrive;
        double depthModulation = 1.0 + parameters.memoryDepthRetentionGain * Math.min(1.0, depth / (1.0 + depth));
        double normalizedDrive = drive / (1.0 + drive);
        return parameters.memoryFormationGain * normalizedDrive * depthModulation;
    }

    private double computeMemoryDecayRate(double depth) {
        double retention = 1.0 / (1.0 + parameters.memoryDepthRetentionGain * Math.max(0.0, depth));
        return parameters.memoryDecayRate * retention;
    }

    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private double computeNeighborAverage(int x, int y, double[][] values) {
        double sum = 0.0;
        int count = 0;
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] offset : offsets) {
            int nx = x + offset[0];
            int ny = y + offset[1];
            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                sum += values[ny][nx];
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private double computeLocalGradient(int x, int y, double[][] values) {
        double center = values[y][x];
        double sum = 0.0;
        int count = 0;
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] offset : offsets) {
            int nx = x + offset[0];
            int ny = y + offset[1];
            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                sum += values[ny][nx] - center;
                count++;
            }
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private double computeLocalCurvature(int x, int y, double[][] values) {
        double center = values[y][x];
        double sum = 0.0;
        int count = 0;
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] offset : offsets) {
            int nx = x + offset[0];
            int ny = y + offset[1];
            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                sum += values[ny][nx];
                count++;
            }
        }
        return count == 0 ? 0.0 : (sum / count) - center;
    }

    public void runExperiment(ExperimentConfig config) {
        int ticks = config.ticks;
        totalActivationHistory.clear();
        baselineHistory.clear();
        activationChangeHistory.clear();
        baselineChangeHistory.clear();
        memoryChangeHistory.clear();
        maxActivationHistory.clear();
        totalMemoryHistory.clear();
        maxMemoryHistory.clear();
        totalFieldDepthHistory.clear();
        maxFieldDepthHistory.clear();
        transportHistory.clear();
        effectiveRelaxationHistory.clear();
        injectionHistory.clear();
        recoveryHistory.clear();
        initialize();
        injectImpulse(config.impulseX, config.impulseY, 1.0);

        runExperimentUnified(config, ticks);
    }

    private void runExperimentUnified(ExperimentConfig config, int ticks) {
        for (int t = 0; t < ticks; t++) {
            double[][] nextA = new double[height][width];
            double[][] nextM = new double[height][width];
            double[][] nextB = new double[height][width];
            double[][] nextC = new double[height][width];
            StatisticsAccumulator stats = new StatisticsAccumulator();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    integrateUnifiedFieldStep(x, y, t, config, nextA, nextM, nextB, nextC, stats);
                }
            }

            activation = nextA;
            memory = nextM;
            baseline = nextB;
            fieldDepth = nextC;

            totalActivationHistory.add(stats.totalA);
            baselineHistory.add(stats.totalBaseline / Math.max(1, height * width));
            activationChangeHistory.add(stats.totalActivationChange / Math.max(1, height * width));
            baselineChangeHistory.add(stats.totalBaselineChange / Math.max(1, height * width));
            memoryChangeHistory.add(stats.totalMemoryChange / Math.max(1, height * width));
            transportHistory.add(stats.totalTransport / Math.max(1, height * width));
            injectionHistory.add(stats.totalInjection / Math.max(1, height * width));
            maxActivationHistory.add(stats.maxA);
            totalMemoryHistory.add(stats.totalM);
            maxMemoryHistory.add(stats.maxM);
            totalFieldDepthHistory.add(stats.totalC);
            maxFieldDepthHistory.add(stats.maxC);
            effectiveRelaxationHistory.add(0.0);
            recoveryHistory.add(0.0);
        }
    }

    public List<EnergyAuditRow> runRecoveryEnergyAudit(int injectTicks, int quietTicks, int impulseX, int impulseY, double impulseAmount) {
        initialize();
        List<EnergyAuditRow> rows = new ArrayList<>();
        int totalCells = width * height;
        for (int tick = 0; tick < injectTicks + quietTicks; tick++) {
            double[][] nextA = new double[height][width];
            double[][] nextM = new double[height][width];
            double[][] nextB = new double[height][width];
            double[][] nextC = new double[height][width];
            double totalActivation = 0.0;
            double totalBaseline = 0.0;
            double totalMemory = 0.0;
            double totalFieldDepth = 0.0;
            double totalTransport = 0.0;
            double totalInjection = 0.0;
            double totalRelaxation = 0.0;
            double totalBaselineCoupling = 0.0;
            double totalTransportContribution = 0.0;
            double totalMemoryContribution = 0.0;
            double totalDepthContribution = 0.0;
            double totalNetDelta = 0.0;
            double totalBaselineDrive = 0.0;
            double totalBaselineHealing = 0.0;
            double totalMemoryGain = 0.0;
            double totalMemoryDecay = 0.0;
            double totalDepthGain = 0.0;
            double totalDepthDecay = 0.0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    double A = activation[y][x];
                    double M = memory[y][x];
                    double B = baseline[y][x];
                    double C = fieldDepth[y][x];
                    double neighborMemoryAverage = computeNeighborAverage(x, y, memory);
                    double neighborDepthAverage = computeNeighborAverage(x, y, fieldDepth);
                    double neighborActivationAverage = computeNeighborAverage(x, y, activation);
                    double activationGradient = computeLocalGradient(x, y, activation);
                    double memoryGradient = computeLocalGradient(x, y, memory);
                    double depthGradient = computeLocalGradient(x, y, fieldDepth);
                    double activationCurvature = computeLocalCurvature(x, y, activation);
                    double memoryCurvature = computeLocalCurvature(x, y, memory);
                    double depthCurvature = computeLocalCurvature(x, y, fieldDepth);
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
                                double nB = baseline[ny][nx];
                                int sourceRadius = 1 + (int) Math.floor(parameters.v3FieldDepthRadius * nC / (nC + parameters.v3FieldDepthEffectK));
                                if (distance > sourceRadius) continue;
                                double neighborStim = nA / (1.0 + nA);
                                neighborStim *= (1.0 + parameters.v2MemoryInfluence * (nM / (nM + parameters.v2MemoryEffectK)));
                                double weight = 1.0;
                                if (parameters.v3DistanceDecay > 0.0) {
                                    weight = Math.exp(-distance / parameters.v3DistanceDecay);
                                } else {
                                    weight = 1.0 / (1.0 + distance);
                                }
                                double depthDifference = Math.max(0.0, nC - C);
                                double gradientBoost = 1.0 + parameters.v3DepthGradientGain * depthDifference;
                                double stateModifier = computeTransportModifier(A, M, C, B, nA, nM, nC, nB);
                                transport += neighborStim * weight * gradientBoost * stateModifier;
                            }
                        }
                    }
                    transport *= parameters.v2TransportGain;
                    transport *= (1.0 - A / (1.0 + A));
                    double depthModulation = computeDepthModulation(C);
                    transport *= (1.0 + parameters.v3DepthTransportGain * depthModulation);

                    double external = 0.0;
                    if (x == impulseX && y == impulseY && tick < injectTicks) {
                        external = impulseAmount;
                    }
                    CellUpdateResult update = integrateUnifiedCellStep(x, y, external, transport);
                    nextA[y][x] = update.nextActivation;
                    nextM[y][x] = update.nextMemory;
                    nextB[y][x] = update.nextBaseline;
                    nextC[y][x] = update.nextDepth;
                    double baselineTarget = update.nextBaseline;
                    totalActivation += update.nextActivation;
                    totalBaseline += baselineTarget;
                    totalMemory += update.nextMemory;
                    totalFieldDepth += update.nextDepth;
                    totalTransport += transport;
                    totalInjection += update.injectionContribution;
                    totalRelaxation += update.relaxationContribution;
                    totalBaselineCoupling += update.baselineContribution;
                    totalTransportContribution += update.transportContribution;
                    totalMemoryContribution += update.memoryContribution;
                    totalDepthContribution += update.depthContribution;
                    totalNetDelta += update.netDeltaActivation;
                    totalBaselineDrive += update.baselineDrive;
                    totalBaselineHealing += update.baselineHealing;
                    totalMemoryGain += update.memoryGain;
                    totalMemoryDecay += update.memoryDecay;
                    totalDepthGain += update.depthGain;
                    totalDepthDecay += update.depthDecay;
                }
            }
            activation = nextA;
            memory = nextM;
            baseline = nextB;
            fieldDepth = nextC;
            rows.add(new EnergyAuditRow(tick,
                    totalActivation / totalCells,
                    totalBaseline / totalCells,
                    totalMemory / totalCells,
                    totalFieldDepth / totalCells,
                    totalTransport / totalCells,
                    totalInjection,
                    totalRelaxation,
                    totalBaselineCoupling,
                    totalTransportContribution,
                    totalMemoryContribution,
                    totalDepthContribution,
                    totalNetDelta,
                    totalBaselineDrive / totalCells,
                    totalBaselineHealing / totalCells,
                    totalMemoryGain / totalCells,
                    totalMemoryDecay / totalCells,
                    totalDepthGain / totalCells,
                    totalDepthDecay / totalCells));
        }
        return rows;
    }

    double computeDepthModulation(double depth) {
        double saturatingDepth = Math.max(0.0, depth);
        double depthScale = Math.max(1e-6, parameters.v3FieldDepthEffectK);
        return Math.min(1.0, saturatingDepth / (saturatingDepth + depthScale));
    }

    double computeTransportModifier(double activation, double memory, double depth, double baseline,
                                            double neighborActivation, double neighborMemory, double neighborDepth,
                                            double neighborBaseline) {
        double localState = activation + 0.35 * memory + 0.20 * depth + 0.15 * baseline;
        double neighborState = neighborActivation + 0.35 * neighborMemory + 0.20 * neighborDepth + 0.15 * neighborBaseline;
        double stateCompatibility = 1.0 + 0.20 * Math.tanh(neighborState - localState);
        double localEnergy = sigmoid(localState);
        double neighborEnergy = sigmoid(neighborState);
        double depthFeedback = computeDepthFeedback(depth);
        double stateTerm = 0.20 + 0.20 * localEnergy + 0.20 * neighborEnergy + 0.10 * stateCompatibility - 0.08 * depthFeedback;
        return clamp(stateTerm, 0.10, 1.20);
    }

    CellUpdateResult integrateUnifiedStepForTest(double A, double M, double B, double C, double external, double transport,
                                                 double neighborMemoryAverage, double neighborDepthAverage) {
        int centerX = Math.max(0, Math.min(width - 1, width / 2));
        int centerY = Math.max(0, Math.min(height - 1, height / 2));
        SpatialStencil stencil = new SpatialStencil(centerX, centerY, activation, memory, fieldDepth, baseline);
        stencil.memoryGradient = Math.max(stencil.memoryGradient, Math.abs(neighborMemoryAverage - M));
        stencil.depthGradient = Math.max(stencil.depthGradient, Math.abs(neighborDepthAverage - C));
        stencil.memoryLaplacian = stencil.memoryLaplacian + (neighborMemoryAverage - M);
        stencil.depthLaplacian = stencil.depthLaplacian + (neighborDepthAverage - C);
        return integrateUnifiedCellStep(A, M, B, C, stencil, external, transport);
    }

    CellUpdateResult integrateUnifiedCellStep(int x, int y, double external, double transport) {
        double A = activation[y][x];
        double M = memory[y][x];
        double B = baseline[y][x];
        double C = fieldDepth[y][x];
        SpatialStencil stencil = new SpatialStencil(x, y, activation, memory, fieldDepth, baseline);
        return integrateUnifiedCellStep(A, M, B, C, stencil, external, transport);
    }

    private CellUpdateResult integrateUnifiedCellStep(double A, double M, double B, double C, SpatialStencil stencil,
                                                     double external, double transport) {
        FieldDerivative derivative = computeUnifiedFieldDerivative(A, M, C, B, stencil, external, transport);
        double nextActivation = clamp(A + derivative.dActivation, 0.0, parameters.v2ClampMax);
        double nextMemory = clamp(M + derivative.dMemory, 0.0, parameters.v2ClampMax);
        double nextDepth = clamp(C + derivative.dDepth, 0.0, parameters.v2ClampMax);
        double nextBaseline = clamp(B + derivative.dBaseline, 0.0, parameters.v2ClampMax);
        return new CellUpdateResult(nextActivation, nextMemory, nextBaseline, nextDepth,
                external, derivative.dActivation, derivative.dBaseline, transport,
                derivative.dMemory, derivative.dDepth, derivative.dActivation,
                derivative.dBaseline, 0.0, derivative.dMemory, 0.0, derivative.dDepth, 0.0);
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

    public double[][] copyFieldDepthGrid() {
        double[][] snapshot = new double[height][width];
        for (int y = 0; y < height; y++) System.arraycopy(fieldDepth[y], 0, snapshot[y], 0, width);
        return snapshot;
    }

    public double scenarioInputForTick(ExperimentConfig config, int tick, int x, int y) {
        if (config == null) {
            return 0.0;
        }
        boolean atCenter = x == config.impulseX && y == config.impulseY;
        boolean atSecond = x == width - 1 - config.impulseX && y == height - 1 - config.impulseY;
        switch (config.scenario) {
            case "single_impulse" -> {
                return tick == 0 && atCenter ? 1.0 : 0.0;
            }
            case "continuous" -> {
                return atCenter ? 0.05 : 0.0;
            }
            case "two_impulses" -> {
                return tick == 0 && (atCenter || atSecond) ? 1.0 : 0.0;
            }
            case "recovery" -> {
                return tick == 0 && atCenter ? 1.0 : 0.0;
            }
            case "extreme_abuse" -> {
                return atCenter ? 0.2 : 0.0;
            }
            case "inject_then_quiet" -> {
                return tick < 2000 && atCenter ? 0.2 : 0.0;
            }
            case "inject_then_quiet_long" -> {
                return tick < 1000 && atCenter ? 0.2 : 0.0;
            }
            default -> {
                return 0.0;
            }
        }
    }

    /**
     * Compute unified field derivative using coupled ODE approach.
     * Single method that computes all four derivatives simultaneously from the same local state.
     * Eliminates separation of concerns issues and ensures mathematical consistency.
     *
     * @param x cell x coordinate
     * @param y cell y coordinate
     * @param stencil computed spatial operators (gradients, Laplacians, curvatures)
     * @param external external input for this cell
     * @param transport incoming activation transport
     * @return FieldDerivative containing dA/dt, dM/dt, dD/dt, dB/dt
     */
    FieldDerivative computeUnifiedFieldDerivative(int x, int y, SpatialStencil stencil, 
                                                   double external, double transport) {
        // Current state at this cell
        double A = activation[y][x];
        double M = memory[y][x];
        double D = fieldDepth[y][x];
        double B = baseline[y][x];
        return computeUnifiedFieldDerivative(A, M, D, B, stencil, external, transport);
    }

    private FieldDerivative computeUnifiedFieldDerivative(double A, double M, double D, double B,
                                                          SpatialStencil stencil,
                                                          double external, double transport) {
        // State vector for coupling matrix application
        double[] stateVector = {A, M, D, B};
        double[] couplingContribution = parameters.couplingMatrix.applyToState(stateVector);

        // Decay terms (linear self-damping)
        double decayA = -parameters.v2ActivationDecay * A;
        double decayD = -parameters.v3FieldDepthDecay * D;
        double decayB = -parameters.v2BaselineRelaxation * B;

        // Spatial diffusion terms (Laplacian-based spreading)
        double diffusionA = parameters.v3DepthTransportGain * stencil.activationLaplacian;
        double diffusionD = parameters.v3FieldDepthDiffusionGain * stencil.depthLaplacian;
        double diffusionB = 0.0; // baseline typically doesn't diffuse

        // Activation is treated as a transient perturbation field.
        // External events create a temporary response whose size is set by susceptibility,
        // while transport spreads that response locally and dissipation removes it when
        // excitation ceases. Memory and depth alter the response gain and the decay rate,
        // but they do not inject activation as a persistent source.
        double memorySusceptibility = M / (1.0 + M);
        double depthSusceptibility = D / (1.0 + D);
        double baselineSusceptibility = B / (1.0 + B);
        double susceptibility = 0.15
                + 0.35 * memorySusceptibility
                + 0.20 * depthSusceptibility
                + 0.20 * baselineSusceptibility;
        double inputDrive = external * susceptibility;
        double transportDrive = transport + 0.35 * diffusionA;
        double responseGain = 0.70 + 0.20 * memorySusceptibility + 0.15 * depthSusceptibility;
        double dissipation = (0.015 + 0.025 * memorySusceptibility + 0.020 * depthSusceptibility)
                * A
                + 0.04 * A * A;
        double dA = responseGain * inputDrive
                + 0.75 * transportDrive
                - dissipation;

        // Memory equation: dM/dt = formation * (1 - M / Mmax) - lambda(D) * M + nu * Laplacian(M)
        // Formation is strongest at activation PEAKS (high gradients indicate local maxima)
        double activationPeakStrength = Math.max(0.0, -stencil.activationLaplacian) / (0.1 + Math.abs(stencil.activationLaplacian));
        double formation = computeMemoryFormation(A, B, D) * (0.30 + 0.70 * activationPeakStrength);
        double saturation = (1.0 - (M / Math.max(1e-6, parameters.memoryMaximum))) * formation;
        double activityForgettingFactor = 2.0 + 3.0 * (1.0 - Math.min(1.0, Math.max(0.0, A)));
        double forgetting = -0.4 * computeMemoryDecayRate(D) * M * activityForgettingFactor;
        double antiDiffusion = -0.15 * parameters.memoryFormationGain * stencil.memoryLaplacian;
        double diffusionM = 0.25 * parameters.memoryDiffusionGain * stencil.memoryLaplacian;
        double dM = saturation + forgetting + antiDiffusion + diffusionM;

        // Depth equation: accumulated structural maturity driven by instantaneous organization
        // production and eroded by instantaneous structural disorder.
        double organization = computeFieldOrganization(A, B, D, stencil, external, transport);
        double erosion = computeFieldErosion(A, B, D, stencil, external, transport);
        double depthGrowth = parameters.depthGrowthGain * organization;
        double depthDecay = parameters.depthErosionGain * erosion;
        double depthDiffusion = parameters.v3FieldDepthDiffusionGain * stencil.depthLaplacian;
        double dD = couplingContribution[2]
                + depthGrowth
                - depthDecay
                + depthDiffusion;

        // Baseline equation: dB/dt = couplingB + decayB + gradual resting recovery
        // Baseline recovers toward resting state
        double restingRecovery = parameters.v2BaselineResting * (1.0 - B);
        double dB = couplingContribution[3] 
                  + decayB 
                  + restingRecovery;

        return new FieldDerivative(dA, dM, dD, dB);
    }
}
