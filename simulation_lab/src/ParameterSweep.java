import java.util.ArrayList;
import java.util.List;

public class ParameterSweep {
    public static List<Double> lambdaValues() {
        List<Double> values = new ArrayList<>();
        values.add(0.0005);
        values.add(0.0010);
        values.add(0.0020);
        values.add(0.0030);
        values.add(0.0050);
        values.add(0.0100);
        return values;
    }

    public static List<Double> betaValues() {
        List<Double> values = new ArrayList<>();
        values.add(0.010);
        values.add(0.030);
        values.add(0.055);
        values.add(0.080);
        return values;
    }

    public static List<Double> alphaValues() {
        List<Double> values = new ArrayList<>();
        values.add(0.003);
        values.add(0.006);
        values.add(0.009);
        values.add(0.012);
        return values;
    }

    public static List<Double> gammaValues() {
        List<Double> values = new ArrayList<>();
        values.add(0.010);
        values.add(0.020);
        values.add(0.045);
        values.add(0.080);
        return values;
    }

    public static List<ParameterSet> buildDefaultSet() {
        List<ParameterSet> sets = new ArrayList<>();
        for (double lambda : lambdaValues()) {
            for (double beta : betaValues()) {
                for (double gamma : gammaValues()) {
                    sets.add(new ParameterSet(ParameterSet.DEFAULT_D, lambda, ParameterSet.DEFAULT_ALPHA, beta, gamma,
                            ParameterSet.DEFAULT_STORM_DRAIN, ParameterSet.DEFAULT_STORM_THRESHOLD, ParameterSet.DEFAULT_STORM_END_THRESHOLD,
                            ParameterSet.DEFAULT_REGION_ACTIVE_TENSION_THRESHOLD, ParameterSet.DEFAULT_T1, ParameterSet.DEFAULT_T2, ParameterSet.DEFAULT_T3,
                            true, true, true, true, true, true, 12345L + sets.size()));
                }
            }
        }
        return sets;
    }
}
