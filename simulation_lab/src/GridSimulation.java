import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Stack;

public class GridSimulation {
    public final int width;
    public final int height;
    public final int ticks;
    public final ParameterSet parameters;
    public final GridCell[][] grid;
    public final List<Double> globalTensionHistory;
    public final List<Double> maxTensionHistory;
    public final List<Double> minTensionHistory;
    public final List<Double> avgTensionHistory;
    public final List<Double> varianceHistory;
    public final List<Double> energyHistory;
    public final List<Double> entropyHistory;
    public final List<Double> hotspotCountHistory;
    public final List<Double> propagationRadiusHistory;
    public final List<Double> halfLifeHistory;
    public final List<Double> overshootHistory;
    public final List<Double> activeCellsHistory;
    public final List<Double> centerOfMassHistory;
    public final List<Double> gradientHistory;
    public final List<Double> largestRegionHistory;
    public final List<Double> energyDecayRateHistory;
    public final List<double[][]> snapshots;
    public final List<Integer> snapshotTicks;
    public final double[][] diffusionTerms;
    public double[][] decayTerms;
    public double[][] globalTerms;
    public double[][] nonlinearTerms;
    public double[][] externalTerms;
    public double[][] stormTerms;
    public double[][] finalTerms;
    public final Random random;

    public GridSimulation(int width, int height, int ticks, ParameterSet parameters) {
        this.width = width;
        this.height = height;
        this.ticks = ticks;
        this.parameters = parameters;
        this.grid = new GridCell[height][width];
        this.globalTensionHistory = new ArrayList<>();
        this.maxTensionHistory = new ArrayList<>();
        this.minTensionHistory = new ArrayList<>();
        this.avgTensionHistory = new ArrayList<>();
        this.varianceHistory = new ArrayList<>();
        this.energyHistory = new ArrayList<>();
        this.entropyHistory = new ArrayList<>();
        this.hotspotCountHistory = new ArrayList<>();
        this.propagationRadiusHistory = new ArrayList<>();
        this.halfLifeHistory = new ArrayList<>();
        this.overshootHistory = new ArrayList<>();
        this.activeCellsHistory = new ArrayList<>();
        this.centerOfMassHistory = new ArrayList<>();
        this.gradientHistory = new ArrayList<>();
        this.largestRegionHistory = new ArrayList<>();
        this.energyDecayRateHistory = new ArrayList<>();
        this.snapshots = new ArrayList<>();
        this.snapshotTicks = new ArrayList<>();
        this.diffusionTerms = new double[height][width];
        this.decayTerms = new double[height][width];
        this.globalTerms = new double[height][width];
        this.nonlinearTerms = new double[height][width];
        this.externalTerms = new double[height][width];
        this.stormTerms = new double[height][width];
        this.finalTerms = new double[height][width];
        this.random = new Random(parameters.seed);
        initialize();
    }

    public void initialize() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = new GridCell(0.0);
            }
        }
    }

    public void injectImpulse(int x, int y, double amount) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            grid[y][x].tension += amount;
        }
    }

    public void runExperiment(ExperimentConfig config) {
        initialize();
        applyScenarioInput(config);
        double globalTension = 0.0;
        for (int tick = 0; tick < ticks; tick++) {
            double sum = 0.0;
            double max = 0.0;
            double min = Double.POSITIVE_INFINITY;
            double energy = 0.0;
            int hotspotCount = 0;
            int activeCells = 0;
            double mass = 0.0;
            double centerX = 0.0;
            double centerY = 0.0;
            double gradientMax = 0.0;
            double activityThreshold = parameters.regionActiveThreshold;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    GridCell cell = grid[y][x];
                    double neighborAvg = neighborAverage(x, y);
                    double input = scenarioInputForTick(config, tick, x, y);
                    boolean stormActive = parameters.stormEnabled && cell.tension > parameters.stormThreshold;
                    double raw = FieldEquation.computeNextCell(cell.tension, neighborAvg, globalTension, parameters, stormActive, input);
                    diffusionTerms[y][x] = parameters.diffusionEnabled ? parameters.D * neighborAvg : 0.0;
                    decayTerms[y][x] = parameters.decayEnabled ? parameters.lambda * cell.tension : 0.0;
                    globalTerms[y][x] = parameters.globalCouplingEnabled ? parameters.gamma * globalTension : 0.0;
                    nonlinearTerms[y][x] = parameters.nonlinearEnabled ? parameters.alpha * cell.tension * cell.tension : 0.0;
                    externalTerms[y][x] = input;
                    stormTerms[y][x] = stormActive ? parameters.stormDrain : 0.0;
                    finalTerms[y][x] = raw;
                    sum += raw;
                    max = Math.max(max, raw);
                    min = Math.min(min, raw);
                    energy += raw * raw;
                    if (isHotspot(x, y, raw, activityThreshold)) {
                        hotspotCount++;
                    }
                    if (raw >= activityThreshold) {
                        activeCells++;
                    }
                    mass += raw;
                    centerX += raw * x;
                    centerY += raw * y;
                    gradientMax = Math.max(gradientMax, computeLocalGradient(x, y, raw));
                    cell.tension = raw;
                    cell.updateState(parameters);
                }
            }
            globalTension = Math.max(0.0, globalTension + 0.01 * sum / (width * height));
            double average = sum / (width * height);
            double variance = computeVariance(average);
            double centerOfMass = mass > 0.0 ? Math.sqrt(Math.pow(centerX / mass - width / 2.0, 2) + Math.pow(centerY / mass - height / 2.0, 2)) : 0.0;
            globalTensionHistory.add(globalTension);
            maxTensionHistory.add(max);
            minTensionHistory.add(min == Double.POSITIVE_INFINITY ? 0.0 : min);
            avgTensionHistory.add(average);
            varianceHistory.add(variance);
            energyHistory.add(energy);
            entropyHistory.add(computeEntropy());
            hotspotCountHistory.add((double) hotspotCount);
            propagationRadiusHistory.add(computePropagationRadius(initialSeedLocations(config)));
            halfLifeHistory.add(computeHalfLife());
            overshootHistory.add(max);
            activeCellsHistory.add((double) activeCells);
            centerOfMassHistory.add(centerOfMass);
            gradientHistory.add(gradientMax);
            largestRegionHistory.add(computeLargestRegion(activityThreshold));
            energyDecayRateHistory.add(computeEnergyDecayRate());
            if ((tick + 1) % 50 == 0 || tick + 1 == ticks) {
                snapshots.add(copyGrid());
                snapshotTicks.add(tick + 1);
            }
        }
    }

    public void runExperiment(String scenario, int impulseX, int impulseY, double externalInput) {
        ExperimentConfig config = new ExperimentConfig(scenario, scenario, width, height, ticks, parameters, impulseX, impulseY, externalInput, parameters.seed);
        runExperiment(config);
    }

    public void applyScenarioInput(ExperimentConfig config) {
        if ("single_impulse".equals(config.scenario)) {
            injectImpulse(config.impulseX, config.impulseY, 1.2);
        } else if ("continuous".equals(config.scenario)) {
            injectImpulse(config.impulseX, config.impulseY, 0.2);
        } else if ("two_impulses".equals(config.scenario)) {
            injectImpulse(config.impulseX, config.impulseY, 1.0);
            injectImpulse(width - 1 - config.impulseX, height - 1 - config.impulseY, 1.0);
        } else if ("recovery".equals(config.scenario)) {
            injectImpulse(config.impulseX, config.impulseY, 1.2);
        } else if ("diffusion_disabled".equals(config.scenario)) {
            injectImpulse(config.impulseX, config.impulseY, 1.0);
            parameters.diffusionEnabled = false;
        } else if ("decay_disabled".equals(config.scenario)) {
            injectImpulse(config.impulseX, config.impulseY, 1.0);
            parameters.decayEnabled = false;
        } else if ("global_coupling_disabled".equals(config.scenario)) {
            injectImpulse(config.impulseX, config.impulseY, 1.0);
            parameters.globalCouplingEnabled = false;
        } else if ("nonlinear_disabled".equals(config.scenario)) {
            injectImpulse(config.impulseX, config.impulseY, 1.0);
            parameters.nonlinearEnabled = false;
        } else if ("minecraft_events".equals(config.scenario)) {
            injectImpulse(config.impulseX, config.impulseY, 0.15);
        }
    }

    public double neighborAverage(int x, int y) {
        double sum = 0.0;
        int count = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    sum += grid[ny][nx].tension;
                    count++;
                }
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    public double computeLocalGradient(int x, int y, double value) {
        double max = 0.0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    max = Math.max(max, Math.abs(value - grid[ny][nx].tension));
                }
            }
        }
        return max;
    }

    public double computeVariance(double average) {
        double variance = 0.0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double diff = grid[y][x].tension - average;
                variance += diff * diff;
            }
        }
        return variance / (width * height);
    }

    public double computeEntropy() {
        double total = 0.0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                total += grid[y][x].tension;
            }
        }
        if (total <= 0.0) return 0.0;
        double entropy = 0.0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double p = grid[y][x].tension / total;
                if (p > 0.0) entropy -= p * Math.log(p);
            }
        }
        return entropy;
    }

    public double computePropagationRadius(List<int[]> seeds) {
        double max = 0.0;
        for (int[] seed : seeds) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (grid[y][x].tension >= parameters.regionActiveThreshold) {
                        double distance = Math.sqrt(Math.pow(x - seed[0], 2) + Math.pow(y - seed[1], 2));
                        max = Math.max(max, distance);
                    }
                }
            }
        }
        return max;
    }

    public double computeHalfLife() {
        if (maxTensionHistory.isEmpty()) return Double.NaN;
        double initial = maxTensionHistory.get(0);
        if (initial <= 0.0) return 0.0;
        for (int index = 0; index < maxTensionHistory.size(); index++) {
            if (maxTensionHistory.get(index) <= initial * 0.5) {
                return index;
            }
        }
        return Double.NaN;
    }

    public double computeLargestRegion(double threshold) {
        boolean[][] visited = new boolean[height][width];
        int best = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!visited[y][x] && grid[y][x].tension >= threshold) {
                    int size = floodFillSize(x, y, visited, threshold);
                    best = Math.max(best, size);
                }
            }
        }
        return best;
    }

    public int floodFillSize(int startX, int startY, boolean[][] visited, double threshold) {
        Stack<int[]> stack = new Stack<>();
        stack.push(new int[]{startX, startY});
        visited[startY][startX] = true;
        int size = 0;
        while (!stack.isEmpty()) {
            int[] cell = stack.pop();
            size++;
            int[][] directions = {{-1,0},{1,0},{0,-1},{0,1}};
            for (int[] direction : directions) {
                int nx = cell[0] + direction[0];
                int ny = cell[1] + direction[1];
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && !visited[ny][nx] && grid[ny][nx].tension >= threshold) {
                    visited[ny][nx] = true;
                    stack.push(new int[]{nx, ny});
                }
            }
        }
        return size;
    }

    public double computeEnergyDecayRate() {
        if (energyHistory.size() < 2) return 0.0;
        double total = 0.0;
        int count = 0;
        for (int index = 1; index < energyHistory.size(); index++) {
            double previous = energyHistory.get(index - 1);
            double current = energyHistory.get(index);
            if (previous > 1e-9 && current > 1e-9) {
                total += -Math.log(current / previous);
                count++;
            }
        }
        return count > 0 ? total / count : 0.0;
    }

    public boolean isHotspot(int x, int y, double value, double threshold) {
        if (value < threshold) {
            return false;
        }
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height && grid[ny][nx].tension >= value) {
                    return false;
                }
            }
        }
        return true;
    }

    public List<int[]> initialSeedLocations(ExperimentConfig config) {
        List<int[]> seeds = new ArrayList<>();
        seeds.add(new int[]{config.impulseX, config.impulseY});
        if ("two_impulses".equals(config.scenario)) {
            seeds.add(new int[]{width - 1 - config.impulseX, height - 1 - config.impulseY});
        }
        return seeds;
    }

    public double scenarioInputForTick(ExperimentConfig config, int tick, int x, int y) {
        if ("continuous".equals(config.scenario)) {
            return config.externalInput;
        }
        if ("recovery".equals(config.scenario) && tick > 25) {
            return 0.0;
        }
        if ("minecraft_events".equals(config.scenario) && tick % 20 == 0) {
            return 0.03;
        }
        return 0.0;
    }

    public double[][] copyGrid() {
        double[][] snapshot = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                snapshot[y][x] = grid[y][x].tension;
            }
        }
        return snapshot;
    }
}
