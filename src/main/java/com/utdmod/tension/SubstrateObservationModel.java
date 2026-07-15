package com.utdmod.tension;

/**
 * Observation model: computes measurable quantities from substrate state.
 * Contains NO dynamics. All methods are pure functions of SubstrateState.
 */
public final class SubstrateObservationModel {

    private SubstrateObservationModel() {}

    /**
     * T(x) = wA*A' + wM*M' + wD*D', normalized by sum of active weights.
     * A' = A/(1+A), M' = M/(1+M), D' = D/(1+D).
     * Only the three independent substrate variables contribute.
     */
    public static double computeLocalTension(SubstrateState state) {
        ParameterSet p = ParameterSet.runtime();
        double wA = p.tensionActivationWeight;
        double wM = p.tensionMemoryWeight;
        double wD = p.tensionDepthWeight;
        double wSum = wA + wM + wD;
        if (wSum <= 0.0) return 0.0;

        double A = Math.max(0.0, state.getActivation());
        double M = Math.max(0.0, state.getMemory());
        double D = Math.max(0.0, state.getFieldDepth());

        return (wA * (A / (1.0 + A))
              + wM * (M / (1.0 + M))
              + wD * (D / (1.0 + D))) / wSum;
    }

    /**
     * Readiness: how responsive the substrate is to new input.
     * High memory → low readiness (already saturated).
     */
    public static double computeReadiness(SubstrateState state) {
        ParameterSet p = ParameterSet.runtime();
        double memoryScale = Math.max(0.5, p.readinessMemoryScale);
        return Math.exp(-Math.max(0.0, state.getMemory()) / memoryScale);
    }

    /**
     * Potential: capacity for further activation.
     * High activation → low capacity (already stressed).
     */
    public static double computePotential(SubstrateState state) {
        return 1.0 / (1.0 + 0.5 * Math.max(0.0, state.getActivation()));
    }

    /**
     * Danger: composite of activation and memory saturation.
     * Indicates how close the chunk is to a critical transition.
     */
    public static double computeDanger(SubstrateState state) {
        double A = Math.max(0.0, state.getActivation());
        double M = Math.max(0.0, state.getMemory());
        double D = Math.max(0.0, state.getFieldDepth());
        return (A / (1.0 + A)) * 0.5
             + (M / (1.0 + M)) * 0.3
             + (D / (1.0 + D)) * 0.2;
    }
}
