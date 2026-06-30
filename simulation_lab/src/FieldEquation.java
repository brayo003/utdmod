public class FieldEquation {
    public static double computeNextCell(double current, double neighborAvg, double globalTension,
                                         ParameterSet p, boolean stormActive, double externalInput) {
        double diffusion = p.diffusionEnabled ? p.D * neighborAvg : 0.0;
        double decay = p.decayEnabled ? p.lambda * current : 0.0;
        double nonlinear = p.nonlinearEnabled ? p.alpha * current * current : 0.0;
        double saturation = p.nonlinearEnabled ? p.beta * current * current * current : 0.0;
        double globalFeedback = p.globalCouplingEnabled ? p.gamma * globalTension : 0.0;
        double stormDrain = p.stormEnabled && stormActive ? p.stormDrain : 0.0;
        double next = current + diffusion + nonlinear - saturation + globalFeedback + externalInput - decay - stormDrain;
        if (p.clampEnabled) {
            next = Math.max(0.0, Math.min(next, 5.0));
        }
        return next;
    }
}
