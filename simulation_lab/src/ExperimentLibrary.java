import java.util.ArrayList;
import java.util.List;

public class ExperimentLibrary {
    public static List<String> scenarios() {
        List<String> scenarios = new ArrayList<>();
        scenarios.add("single_impulse");
        scenarios.add("continuous");
        scenarios.add("two_impulses");
        scenarios.add("recovery");
        scenarios.add("diffusion_disabled");
        scenarios.add("decay_disabled");
        scenarios.add("global_coupling_disabled");
        scenarios.add("nonlinear_disabled");
        scenarios.add("minecraft_events");
        return scenarios;
    }

    public static List<ExperimentConfig> defaultExperiments() {
        List<ExperimentConfig> experiments = new ArrayList<>();
        int width = 64;
        int height = 64;
        int ticks = 500;
        long seed = 12345L;
        for (String scenario : scenarios()) {
            ParameterSet p = new ParameterSet();
            p.seed = seed;
            experiments.add(new ExperimentConfig(scenario, scenario, width, height, ticks, p, width / 2, height / 2, 0.05, seed));
        }
        return experiments;
    }
}
