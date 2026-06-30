public class BehaviorClassifier {
    public static String classify(ResultSummary summary) {
        if (summary.peakTension > 3.0) return "Explosive";
        if (summary.oscillationAmplitude > 0.15) return "Oscillatory";
        if (summary.energyDecayRate < 0.001) return "Persistent";
        if (summary.halfLife > 200.0) return "Rapid Recovery";
        if (summary.diffusionRadius > 10.0) return "Diffusion Dominated";
        if (summary.averageTension > 0.5) return "Global Dominated";
        if (summary.peakTension > 1.0) return "Marginally Stable";
        return "Stable";
    }
}
