import java.util.List;

public class MetricsCollector {
    public static String summarize(GridSimulation sim) {
        ResultSummary summary = summarize(sim, null);
        return String.format("max=%.6f avg=%.6f energy=%.6f entropy=%.6f hotspots=%.6f radius=%.6f half_life=%.6f", summary.peakTension, summary.averageTension, summary.totalEnergy, 0.0, summary.hotspots, summary.diffusionRadius, summary.halfLife);
    }

    public static ResultSummary summarize(GridSimulation sim, ExperimentConfig config) {
        double peakTension = sim.maxTensionHistory.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double minTension = sim.minTensionHistory.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double averageTension = sim.avgTensionHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = sim.varianceHistory.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double totalEnergy = sim.energyHistory.stream().mapToDouble(Double::doubleValue).sum();
        double activeCells = sim.activeCellsHistory.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double diffusionRadius = sim.propagationRadiusHistory.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double centerOfMass = sim.centerOfMassHistory.isEmpty() ? 0.0 : sim.centerOfMassHistory.get(sim.centerOfMassHistory.size() - 1);
        double halfLife = sim.halfLifeHistory.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
        double timeToEquilibrium = computeTimeToEquilibrium(sim);
        double oscillationAmplitude = computeOscillationAmplitude(sim);
        double hotspots = sim.hotspotCountHistory.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double hotspotLifetime = sim.hotspotCountHistory.stream().mapToDouble(Double::doubleValue).filter(value -> value > 0.0).count();
        double maxGradient = sim.gradientHistory.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double largestRegion = sim.largestRegionHistory.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double energyDecayRate = sim.energyDecayRateHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        String classification = BehaviorClassifier.classify(new ResultSummary(
                config != null ? config.experimentName : "unknown",
                "UNKNOWN",
                peakTension,
                minTension,
                averageTension,
                variance,
                totalEnergy,
                activeCells,
                diffusionRadius,
                centerOfMass,
                halfLife,
                timeToEquilibrium,
                oscillationAmplitude,
                hotspots,
                hotspotLifetime,
                maxGradient,
                largestRegion,
                energyDecayRate,
                ""
        ));
        return new ResultSummary(
                config != null ? config.experimentName : "unknown",
                classification,
                peakTension,
                minTension,
                averageTension,
                variance,
                totalEnergy,
                activeCells,
                diffusionRadius,
                centerOfMass,
                halfLife,
                timeToEquilibrium,
                oscillationAmplitude,
                hotspots,
                hotspotLifetime,
                maxGradient,
                largestRegion,
                energyDecayRate,
                buildNotes(sim, classification)
        );
    }

    public static double computeTimeToEquilibrium(GridSimulation sim) {
        if (sim.avgTensionHistory.size() < 3) return Double.NaN;
        double finalAvg = sim.avgTensionHistory.get(sim.avgTensionHistory.size() - 1);
        for (int index = 0; index < sim.avgTensionHistory.size(); index++) {
            double current = sim.avgTensionHistory.get(index);
            if (Math.abs(current - finalAvg) < 1e-4) {
                return index;
            }
        }
        return Double.NaN;
    }

    public static double computeOscillationAmplitude(GridSimulation sim) {
        if (sim.avgTensionHistory.size() < 3) return 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double value : sim.avgTensionHistory) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return max - min;
    }

    public static String buildNotes(GridSimulation sim, String classification) {
        return "Classification=" + classification + "; stability=" + StabilityAnalyzer.analyze(sim);
    }

    public static String format(double value) {
        if (Double.isNaN(value)) {
            return "UNKNOWN";
        }
        return String.format("%.6f", value);
    }
}
