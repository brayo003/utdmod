public class ResultSummary {
    public final String experiment;
    public final String classification;
    public final double peakTension;
    public final double minTension;
    public final double averageTension;
    public final double variance;
    public final double totalEnergy;
    public final double activeCells;
    public final double diffusionRadius;
    public final double centerOfMass;
    public final double halfLife;
    public final double timeToEquilibrium;
    public final double oscillationAmplitude;
    public final double hotspots;
    public final double hotspotLifetime;
    public final double maxGradient;
    public final double largestRegion;
    public final double energyDecayRate;
    public final String notes;

    public ResultSummary(String experiment, String classification, double peakTension, double minTension,
                         double averageTension, double variance, double totalEnergy, double activeCells,
                         double diffusionRadius, double centerOfMass, double halfLife, double timeToEquilibrium,
                         double oscillationAmplitude, double hotspots, double hotspotLifetime, double maxGradient,
                         double largestRegion, double energyDecayRate, String notes) {
        this.experiment = experiment;
        this.classification = classification;
        this.peakTension = peakTension;
        this.minTension = minTension;
        this.averageTension = averageTension;
        this.variance = variance;
        this.totalEnergy = totalEnergy;
        this.activeCells = activeCells;
        this.diffusionRadius = diffusionRadius;
        this.centerOfMass = centerOfMass;
        this.halfLife = halfLife;
        this.timeToEquilibrium = timeToEquilibrium;
        this.oscillationAmplitude = oscillationAmplitude;
        this.hotspots = hotspots;
        this.hotspotLifetime = hotspotLifetime;
        this.maxGradient = maxGradient;
        this.largestRegion = largestRegion;
        this.energyDecayRate = energyDecayRate;
        this.notes = notes;
    }
}
