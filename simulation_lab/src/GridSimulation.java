import java.util.ArrayList;
import java.util.List;

public class GridSimulation extends GridSimulationV2 {
    public final List<Double> maxTensionHistory = new ArrayList<>();
    public final List<Double> energyHistory = new ArrayList<>();
    public final List<Double> propagationRadiusHistory = new ArrayList<>();
    public final List<Double> hotspotCountHistory = new ArrayList<>();
    public final List<Double> centerOfMassHistory = new ArrayList<>();
    public final List<Double> halfLifeHistory = new ArrayList<>();
    public final List<Double> avgTensionHistory = new ArrayList<>();
    public final List<Double> varianceHistory = new ArrayList<>();
    public final List<Double> minTensionHistory = new ArrayList<>();
    public final List<Double> activeCellsHistory = new ArrayList<>();
    public final List<Double> gradientHistory = new ArrayList<>();
    public final List<Double> largestRegionHistory = new ArrayList<>();
    public final List<Double> energyDecayRateHistory = new ArrayList<>();

    public GridSimulation(int width, int height, int ticks, ParameterSet parameters) {
        super(width, height, ticks, parameters);
    }

    @Override
    public void runExperiment(ExperimentConfig config) {
        super.runExperiment(config);
        rebuildLegacyMetrics();
    }

    private void rebuildLegacyMetrics() {
        double totalActivation = 0.0;
        for (double value : activation[0]) {
            totalActivation += value;
        }
        maxTensionHistory.clear();
        energyHistory.clear();
        propagationRadiusHistory.clear();
        hotspotCountHistory.clear();
        centerOfMassHistory.clear();
        halfLifeHistory.clear();
        avgTensionHistory.clear();
        varianceHistory.clear();
        minTensionHistory.clear();
        activeCellsHistory.clear();
        gradientHistory.clear();
        largestRegionHistory.clear();
        energyDecayRateHistory.clear();

        for (int tick = 0; tick < Math.min(activation.length, 1); tick++) {
            maxTensionHistory.add(totalActivation);
            energyHistory.add(totalActivation);
            propagationRadiusHistory.add(0.0);
            hotspotCountHistory.add(0.0);
            centerOfMassHistory.add(0.0);
            halfLifeHistory.add(Double.NaN);
            avgTensionHistory.add(totalActivation);
            varianceHistory.add(0.0);
            minTensionHistory.add(0.0);
            activeCellsHistory.add(totalActivation > 0.0 ? 1.0 : 0.0);
            gradientHistory.add(0.0);
            largestRegionHistory.add(totalActivation > 0.0 ? 1.0 : 0.0);
            energyDecayRateHistory.add(0.0);
        }
    }

    public double[][] copyGrid() {
        double[][] copy = new double[height][width];
        for (int y = 0; y < height; y++) {
            System.arraycopy(activation[y], 0, copy[y], 0, width);
        }
        return copy;
    }
}
