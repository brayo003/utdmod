import java.util.ArrayList;
import java.util.List;

public class EcologicalScenarioResult {
    public final String scenarioName;
    public final String description;
    public final List<Double> meanActivation;
    public final List<Double> meanMemory;
    public final List<Double> meanBaseline;
    public final List<Double> meanFieldDepth;
    public final List<Double> maxActivation;
    public final List<Double> maxMemory;
    public final List<Double> maxBaseline;
    public final List<Double> maxFieldDepth;
    public final List<Double> organizationProduction;
    public final List<Double> erosion;
    public final List<Double> netDepthProduction;
    public final List<Double> spatialVariance;
    public final double correlationActivationMemory;
    public final double correlationMemoryDepth;
    public final double correlationTransportDepth;
    public final double totalOrganization;
    public final double totalErosion;
    public final double totalNetDepthProduction;
    public final double finalMeanDepth;
    public final double finalMaxDepth;
    public final String interpretation;

    public EcologicalScenarioResult(String scenarioName, String description,
                                    List<Double> meanActivation, List<Double> meanMemory, List<Double> meanBaseline,
                                    List<Double> meanFieldDepth, List<Double> maxActivation, List<Double> maxMemory,
                                    List<Double> maxBaseline, List<Double> maxFieldDepth,
                                    List<Double> organizationProduction, List<Double> erosion,
                                    List<Double> netDepthProduction, List<Double> spatialVariance,
                                    double correlationActivationMemory, double correlationMemoryDepth,
                                    double correlationTransportDepth, double totalOrganization, double totalErosion,
                                    double totalNetDepthProduction, double finalMeanDepth, double finalMaxDepth,
                                    String interpretation) {
        this.scenarioName = scenarioName;
        this.description = description;
        this.meanActivation = new ArrayList<>(meanActivation);
        this.meanMemory = new ArrayList<>(meanMemory);
        this.meanBaseline = new ArrayList<>(meanBaseline);
        this.meanFieldDepth = new ArrayList<>(meanFieldDepth);
        this.maxActivation = new ArrayList<>(maxActivation);
        this.maxMemory = new ArrayList<>(maxMemory);
        this.maxBaseline = new ArrayList<>(maxBaseline);
        this.maxFieldDepth = new ArrayList<>(maxFieldDepth);
        this.organizationProduction = new ArrayList<>(organizationProduction);
        this.erosion = new ArrayList<>(erosion);
        this.netDepthProduction = new ArrayList<>(netDepthProduction);
        this.spatialVariance = new ArrayList<>(spatialVariance);
        this.correlationActivationMemory = correlationActivationMemory;
        this.correlationMemoryDepth = correlationMemoryDepth;
        this.correlationTransportDepth = correlationTransportDepth;
        this.totalOrganization = totalOrganization;
        this.totalErosion = totalErosion;
        this.totalNetDepthProduction = totalNetDepthProduction;
        this.finalMeanDepth = finalMeanDepth;
        this.finalMaxDepth = finalMaxDepth;
        this.interpretation = interpretation;
    }
}
