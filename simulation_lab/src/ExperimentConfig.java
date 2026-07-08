public class ExperimentConfig {
    public final String experimentName;
    public final String scenario;
    public final int width;
    public final int height;
    public final int ticks;
    public final ParameterSet parameters;
    public final int impulseX;
    public final int impulseY;
    public final double externalInput;
    public final long seed;

    public ExperimentConfig(String experimentName, String scenario, int width, int height, int ticks,
                            ParameterSet parameters, int impulseX, int impulseY, double externalInput, long seed) {
        this.experimentName = experimentName;
        this.scenario = scenario;
        this.width = width;
        this.height = height;
        this.ticks = ticks;
        this.parameters = parameters;
        this.impulseX = impulseX;
        this.impulseY = impulseY;
        this.externalInput = externalInput;
        this.seed = seed;
    }

    public ExperimentConfig(String scenario, int impulseX, int impulseY) {
        this("default", scenario, 4, 4, 200, new ParameterSet(), impulseX, impulseY, 1.0, 12345L);
    }
}
