public class OperatingRegionCharacterizer {
    public static String classifyEquilibrium(double finalActivation, double finalBaseline, double finalMemory, double finalFieldDepth, double halfLife) {
        boolean dead = finalActivation < 0.01 && finalBaseline < 0.01 && finalMemory < 0.01 && finalFieldDepth < 0.01 && halfLife < 10.0;
        if (dead) {
            return "Dead";
        }
        boolean quiet = finalActivation <= 0.1 && finalBaseline <= 0.1 && finalMemory <= 0.2 && halfLife < 100.0;
        if (quiet) {
            return "Quiet";
        }
        boolean living = finalActivation >= 0.1 && finalActivation < 2.0 && finalBaseline >= 0.1 && finalBaseline < 3.0 && finalMemory < 3.0 && halfLife < 2000.0;
        if (living) {
            return "Living";
        }
        boolean persistent = finalActivation >= 2.0 && finalActivation < 6.0 && finalBaseline >= 2.0 && finalBaseline < 6.0 && finalMemory >= 3.0 && finalMemory < 8.0 && halfLife >= 2000.0;
        if (persistent) {
            return "Persistent";
        }
        return "Saturated";
    }
}
