package com.utdmod.tension;

/**
 * Unified field derivative representation.
 * Computed synchronously from identical local state.
 * All four state variables have their time derivatives computed in parallel.
 */
public class FieldDerivative {
    public double dActivation;
    public double dMemory;
    public double dDepth;
    public double dBaseline;

    public FieldDerivative() {
        this.dActivation = 0.0;
        this.dMemory = 0.0;
        this.dDepth = 0.0;
        this.dBaseline = 0.0;
    }

    public FieldDerivative(double dA, double dM, double dD, double dB) {
        this.dActivation = dA;
        this.dMemory = dM;
        this.dDepth = dD;
        this.dBaseline = dB;
    }

    /**
     * Canonical unified field derivative — ported verbatim from
     * simulation_lab GridSimulationV2.computeUnifiedFieldDerivative.
     */
    public static FieldDerivative computeUnified(double A, double M, double D, double B,
                                                  SpatialStencil stencil,
                                                  double external, double transport,
                                                  ParameterSet parameters) {
        double[] stateVector = {A, M, D, B};
        double[] couplingContribution = parameters.couplingMatrix.applyToState(stateVector);

        double decayB = -parameters.v2BaselineRelaxation * B;

        double diffusionA = parameters.v3DepthTransportGain * stencil.activationLaplacian;

        double memorySusceptibility = M / (1.0 + M);
        double depthSusceptibility = D / (1.0 + D);
        double baselineSusceptibility = B / (1.0 + B);
        double susceptibility = 0.15
                + 0.35 * memorySusceptibility
                + 0.20 * depthSusceptibility
                + 0.20 * baselineSusceptibility;
        double inputDrive = external * susceptibility;
        double transportDrive = transport + 0.35 * diffusionA;
        double responseGain = 0.70 + 0.20 * memorySusceptibility + 0.15 * depthSusceptibility;
        double dissipation = (0.015 + 0.025 * memorySusceptibility + 0.020 * depthSusceptibility)
                * A
                + 0.04 * A * A;
        double dA = responseGain * inputDrive
                + 0.75 * transportDrive
                - dissipation;

        double activationPeakStrength = Math.max(0.0, -stencil.activationLaplacian)
                / (0.1 + Math.abs(stencil.activationLaplacian));
        double formation = computeMemoryFormation(A, B, D, parameters)
                * (0.30 + 0.70 * activationPeakStrength);
        double saturation = (1.0 - (M / Math.max(1e-6, parameters.memoryMaximum))) * formation;
        double activityForgettingFactor = 2.0 + 3.0 * (1.0 - Math.min(1.0, Math.max(0.0, A)));
        double forgetting = -0.4 * computeMemoryDecayRate(D, parameters) * M * activityForgettingFactor;
        double antiDiffusion = -0.15 * parameters.memoryFormationGain * stencil.memoryLaplacian;
        double diffusionM = 0.25 * parameters.memoryDiffusionGain * stencil.memoryLaplacian;
        double dM = saturation + forgetting + antiDiffusion + diffusionM;

        double organization = computeFieldOrganization(A, B, D, stencil, external, transport);
        double erosion = computeFieldErosion(A, B, D, stencil, external, transport);
        double depthGrowth = parameters.depthGrowthGain * organization;
        double depthDecay = parameters.depthErosionGain * erosion;
        double depthDiffusion = parameters.v3FieldDepthDiffusionGain * stencil.depthLaplacian;
        double dD = couplingContribution[2]
                + depthGrowth
                - depthDecay
                + depthDiffusion;

        double restingRecovery = parameters.v2BaselineResting * (1.0 - B);
        double dB = couplingContribution[3]
                + decayB
                + restingRecovery;

        return new FieldDerivative(dA, dM, dD, dB);
    }

    static double computeFieldOrganization(double activation, double baseline, double depth,
                                           SpatialStencil stencil, double external, double transport) {
        double spatialCoherence = 1.0 / (1.0 + Math.abs(stencil.activationLaplacian));
        double transportEfficiency = transport / (1.0 + transport);
        double flowAlignment = 1.0 / (1.0 + Math.abs(stencil.activationGradient - Math.max(0.0, transport)));
        double neighborhoodAgreement = 1.0 / (1.0 + Math.abs(stencil.activationGradient));
        double structuralSmoothness = 1.0 / (1.0 + Math.abs(stencil.depthGradient));

        double organization = 0.30 * spatialCoherence
                + 0.25 * transportEfficiency
                + 0.20 * flowAlignment
                + 0.15 * neighborhoodAgreement
                + 0.10 * structuralSmoothness;
        return clamp(organization, 0.0, 1.0);
    }

    static double computeFieldErosion(double activation, double baseline, double depth,
                                      SpatialStencil stencil, double external, double transport) {
        double localDisorder = Math.min(1.0, Math.abs(stencil.activationLaplacian) + Math.abs(stencil.memoryLaplacian));
        double fragmentation = Math.min(1.0, Math.abs(stencil.activationGradient) + Math.abs(stencil.memoryGradient));
        double rapidFluctuation = Math.min(1.0, Math.abs(stencil.activationCurvature) + Math.abs(stencil.memoryCurvature));

        double erosion = 0.45 * localDisorder
                + 0.35 * fragmentation
                + 0.20 * rapidFluctuation;
        return clamp(erosion, 0.0, 1.0);
    }

    static double computeMemoryFormation(double activation, double baseline, double depth, ParameterSet parameters) {
        double activationDrive = Math.max(0.0, activation - parameters.memoryFormationThreshold);
        if (activationDrive <= 0.0) {
            return 0.0;
        }
        double depthModulation = 1.0 + parameters.memoryDepthRetentionGain * Math.min(1.0, depth / (1.0 + depth));
        double normalizedDrive = activationDrive / (1.0 + activationDrive);
        return parameters.memoryFormationGain * normalizedDrive * depthModulation;
    }

    static double computeMemoryDecayRate(double depth, ParameterSet parameters) {
        double retention = 1.0 / (1.0 + parameters.memoryDepthRetentionGain * Math.max(0.0, depth));
        return parameters.memoryDecayRate * retention;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String toString() {
        return String.format("dA=%.6f, dM=%.6f, dD=%.6f, dB=%.6f",
                dActivation, dMemory, dDepth, dBaseline);
    }
}
