public class StabilityAnalyzer {
    public static String analyze(GridSimulation sim) {
        double max = sim.maxTensionHistory.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double finalValue = sim.maxTensionHistory.isEmpty() ? 0.0 : sim.maxTensionHistory.get(sim.maxTensionHistory.size() - 1);
        if (max > 3.0) return "Unstable";
        if (max > 1.5) return "Marginal";
        if (finalValue < 0.01) return "Recovered";
        return "Bounded";
    }
}
