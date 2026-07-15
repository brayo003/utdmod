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
     * Canonical unified field derivative.
     *
     * Saturation design:
     *   A  — existing nonlinear dissipation (0.04·A²) provides natural boundedness; unchanged.
     *   M  — logistic ceiling (1 - M/Mmax) determines equilibrium; v2ClampMax > Mmax ensures
     *        the hard clamp never fires in normal operation.
     *   D  — carrying-capacity factor max(0, 1 - D/Dmax) attenuates all positive growth terms
     *        so D approaches Dmax asymptotically; decay and diffusion are unaffected.
     *   B  — coupling inflow replaced with saturating forms x/(1+x) so A, M, D contribute
     *        progressively less additional baseline as they grow.
     */
    public static FieldDerivative computeUnified(double A, double M, double D, double B,
                                                  SpatialStencil stencil,
                                                  double external, double transport,
                                                  ParameterSet parameters) {
        // ---- Shared susceptibility terms (unchanged) ----
        double memorySusceptibility  = M / (1.0 + M);
        double depthSusceptibility   = D / (1.0 + D);
        double baselineSusceptibility = B / (1.0 + B);

        // ---- Activation (A) — unchanged ----
        double diffusionA   = parameters.v3DepthTransportGain * stencil.activationLaplacian;
        double susceptibility = 0.15
                + 0.35 * memorySusceptibility
                + 0.20 * depthSusceptibility
                + 0.20 * baselineSusceptibility;
        double inputDrive    = external * susceptibility;
        double transportDrive = transport + 0.35 * diffusionA;
        double responseGain  = 0.70 + 0.20 * memorySusceptibility + 0.15 * depthSusceptibility;
        double dissipation   = (0.015 + 0.025 * memorySusceptibility + 0.020 * depthSusceptibility)
                * A
                + 0.04 * A * A;
        double dA = responseGain * inputDrive
                + 0.75 * transportDrive
                - dissipation;

        // ---- Memory (M) — logistic saturation unchanged; v2ClampMax > memoryMaximum ----
        double activationPeakStrength = Math.max(0.0, -stencil.activationLaplacian)
                / (0.1 + Math.abs(stencil.activationLaplacian));
        double formation  = computeMemoryFormation(A, B, D, parameters)
                * (0.30 + 0.70 * activationPeakStrength);
        double saturation = (1.0 - (M / Math.max(1e-6, parameters.memoryMaximum))) * formation;
        double activityForgettingFactor = 2.0 + 3.0 * (1.0 - Math.min(1.0, Math.max(0.0, A)));
        double forgetting = -0.4 * computeMemoryDecayRate(D, parameters) * M * activityForgettingFactor;

        // Spatial sharpening should only occur while activation is present.
        // When A -> 0 the sharpening process stops, allowing diffusion and
        // forgetting to relax the memory field back toward zero.
        double activityGateForSpatialM = A / (1.0 + A);

        double antiDiffusion =
                -0.15 * parameters.memoryFormationGain
                * activityGateForSpatialM
                * stencil.memoryLaplacian;

        // Leave ordinary diffusion UNCHANGED.
        // Diffusion should continue smoothing spatial memory gradients even
        // after activation has ceased.
        double diffusionM =
                0.25 * parameters.memoryDiffusionGain
                * stencil.memoryLaplacian;

        double dM = saturation + forgetting + antiDiffusion + diffusionM;

        // ---- Field Depth (D) — memory-dependent carrying capacity ----
        // dynamicDmax = depthMaximum + depthCapacityFromMemory * memoryGate
        // When M=0: dynamicDmax = depthMaximum (original behaviour preserved).
        // As M increases: dynamicDmax grows smoothly, allowing deeper consolidation
        // in regions with perturbation history. Hard clamp (v2ClampMax) is unaffected.
        double organization  = computeFieldOrganization(A, B, D, stencil, external, transport);
        double erosion       = computeFieldErosion(A, B, D, stencil, external, transport);
        double activityGate  = A / (1.0 + A);
        double memoryGate    = M / (1.0 + M);
        double dynamicDmax   = parameters.depthMaximum
                             + parameters.depthCapacityFromMemory * memoryGate;
        double growthFactor  = Math.max(0.0, 1.0 - D / Math.max(1e-6, dynamicDmax));
        double depthGrowth   = organization * activityGate
                             * (parameters.v3FieldDepthGain + parameters.depthGrowthGain)
                             * growthFactor;
        // Coupling row 2 positive terms also attenuated by growthFactor.
        // B contributes to D only when there is evidence of recent perturbation.
        // baselinePersistence blends current activity (activityGate) with recent-activity
        // memory (memoryGate), so idle chunks (A=0, M=0) receive zero B→D inflow and D
        // decays to zero, while post-activity consolidation continues until M fades.
        // memoryGate already computed above for dynamicDmax; reuse here.
        double baselinePersistence = 0.2 * activityGate + 0.8 * memoryGate;
        double couplingD     = 0.04 * (A / (1.0 + A)) * growthFactor
                             + 0.02 * (M / (1.0 + M)) * growthFactor
                             - 0.008 * D
                             + 0.01  * (B / (1.0 + B)) * baselinePersistence * growthFactor;
        double depthDecay    = parameters.v3FieldDepthDecay * D
                             + parameters.depthErosionGain * erosion;
        double depthDiffusion = parameters.v3FieldDepthDiffusionGain * stencil.depthLaplacian;
        double dD = couplingD
                + depthGrowth
                - depthDecay
                + depthDiffusion;

        // ---- Baseline (B) — saturating coupling inflow ----
        // Linear coupling 0.05·A + 0.02·M + 0.01·D replaced with x/(1+x) forms so that
        // large A, M, D contribute progressively less additional baseline.
        // The negative self-coupling (-0.03·B) and resting recovery are unchanged.
        double baselineCouplingIn = 0.05 * (A / (1.0 + A))
                                  + 0.02 * (M / (1.0 + M))
                                  + 0.01 * (D / (1.0 + D));
        double decayB          = -parameters.v2BaselineRelaxation * B;
        double restingRecovery = parameters.v2BaselineResting * (1.0 - B);
        double dB = baselineCouplingIn
                - 0.03 * B
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
