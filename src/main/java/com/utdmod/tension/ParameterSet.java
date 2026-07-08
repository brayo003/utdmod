package com.utdmod.tension;

import com.utdmod.config.UTDConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Validated field parameters — ported from simulation_lab ParameterSet.
 */
public class ParameterSet {
    public static final double DEFAULT_D = 0.012;
    public static final double DEFAULT_LAMBDA = 0.0006;
    public static final double DEFAULT_ALPHA = 0.009;
    public static final double DEFAULT_BETA = 0.055;
    public static final double DEFAULT_GAMMA = 0.045;
    public static final double DEFAULT_STORM_DRAIN = 0.002;
    public static final double DEFAULT_STORM_THRESHOLD = 0.95;
    public static final double DEFAULT_STORM_END_THRESHOLD = 0.25;
    public static final double DEFAULT_REGION_ACTIVE_TENSION_THRESHOLD = 0.35;
    public static final double DEFAULT_T1 = 0.95;
    public static final double DEFAULT_T2 = 1.28;
    public static final double DEFAULT_T3 = 3.0;

    public double D;
    public double lambda;
    public double alpha;
    public double beta;
    public double gamma;
    public double stormDrain;
    public double stormThreshold;
    public double stormEndThreshold;
    public double regionActiveThreshold;
    public double t1;
    public double t2;
    public double t3;
    public boolean diffusionEnabled;
    public boolean decayEnabled;
    public boolean globalCouplingEnabled;
    public boolean nonlinearEnabled;
    public boolean stormEnabled;
    public boolean clampEnabled;
    public double v2TransportGain;
    public double v2ActivationDecay;
    @Deprecated
    public double v2MemoryGain;
    @Deprecated
    public double v2MemoryDecay;
    public double v2MemoryInfluence;
    public double v2MemoryEffectK;
    public double memoryFormationGain;
    public double memoryMaximum;
    public double memoryFormationThreshold;
    public double memoryDiffusionGain;
    public double memoryDepthRetentionGain;
    public double memoryDecayRate;
    public double v2ClampMax;
    public double v3FieldDepthGain;
    public double depthGrowthGain;
    public double depthErosionGain;
    public double v3FieldDepthDecay;
    public double v3FieldDepthRadius;
    public double v3DistanceDecay;
    public double v3FieldDepthEffectK;
    public double v3FieldDepthDiffusionGain;
    public double v3DepthSensitivity;
    public double v3DepthRecoveryGain;
    public double v3DepthMemoryRetentionGain;
    public double v3DepthTransportGain;
    public double v3DepthGradientGain;
    public double v3ActivationBistabilityGain;
    public double v3MemorySynchronizationGain;
    public double v3TransportStateGain;
    public boolean v2AdaptiveRelaxationEnabled;
    public double v2RelaxationLambdaMin;
    public double v2RelaxationLambdaGain;
    public double v2RelaxationK;
    public double v2RelaxationTc;
    public double v2RelaxationMemoryBeta;
    public double v2RelaxationDepthGamma;
    public double v2ActivationFastGain;
    public double v2BaselineGain;
    public double v2BaselineRelaxation;
    public double v2BaselineResting;
    public double v2ActivationRelaxationGain;
    public double v2BaselineAdaptationRate;
    public double v2BaselineHealingRate;
    public double v2DepthGain;
    public double v2DepthRelaxation;
    public String transportBalanceMode;
    public double readinessMemoryScale;
    public double readinessFieldScale;
    public double readinessTransitionWidth;
    public long seed;
    public double validationMaxHotspotRadius;
    public CouplingMatrix couplingMatrix;

    private static ParameterSet runtimeInstance;

    public ParameterSet() {
        this(DEFAULT_D, DEFAULT_LAMBDA, DEFAULT_ALPHA, DEFAULT_BETA, DEFAULT_GAMMA,
                DEFAULT_STORM_DRAIN, DEFAULT_STORM_THRESHOLD, DEFAULT_STORM_END_THRESHOLD,
                DEFAULT_REGION_ACTIVE_TENSION_THRESHOLD, DEFAULT_T1, DEFAULT_T2, DEFAULT_T3,
                true, true, true, true, true, true, 12345L);
    }

    public ParameterSet(double D, double lambda, double alpha, double beta, double gamma,
                        double stormDrain, double stormThreshold, double stormEndThreshold,
                        double regionActiveThreshold, double t1, double t2, double t3,
                        boolean diffusionEnabled, boolean decayEnabled, boolean globalCouplingEnabled,
                        boolean nonlinearEnabled, boolean stormEnabled, boolean clampEnabled, long seed) {
        this.D = D;
        this.lambda = lambda;
        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
        this.stormDrain = stormDrain;
        this.stormThreshold = stormThreshold;
        this.stormEndThreshold = stormEndThreshold;
        this.regionActiveThreshold = regionActiveThreshold;
        this.t1 = t1;
        this.t2 = t2;
        this.t3 = t3;
        this.diffusionEnabled = diffusionEnabled;
        this.decayEnabled = decayEnabled;
        this.globalCouplingEnabled = globalCouplingEnabled;
        this.nonlinearEnabled = nonlinearEnabled;
        this.stormEnabled = stormEnabled;
        this.clampEnabled = clampEnabled;
        this.seed = seed;
        this.v2TransportGain = 0.08;
        this.v2ActivationDecay = 0.45;
        this.v2MemoryGain = 0.02;
        this.v2MemoryDecay = 0.001;
        this.v2MemoryInfluence = 2.0;
        this.v2MemoryEffectK = 0.1;
        this.memoryFormationGain = 0.02;
        this.memoryMaximum = 2.0;
        this.memoryFormationThreshold = 0.05;
        this.memoryDiffusionGain = 0.002;
        this.memoryDepthRetentionGain = 0.8;
        this.memoryDecayRate = 0.001;
        this.v2ClampMax = 1.5;
        this.v3FieldDepthGain = 0.005;
        this.depthGrowthGain = 0.04;
        this.depthErosionGain = 0.03;
        this.v3FieldDepthDecay = 0.0005;
        this.v3FieldDepthRadius = 0.0;
        this.v3DistanceDecay = 1.0;
        this.v3FieldDepthEffectK = 0.1;
        this.v3FieldDepthDiffusionGain = 0.0002;
        this.v3DepthSensitivity = 0.35;
        this.v3DepthRecoveryGain = 0.25;
        this.v3DepthMemoryRetentionGain = 0.20;
        this.v3DepthTransportGain = 0.20;
        this.v3DepthGradientGain = 0.12;
        this.v3ActivationBistabilityGain = 0.65;
        this.v3MemorySynchronizationGain = 0.012;
        this.v3TransportStateGain = 0.20;
        this.v2AdaptiveRelaxationEnabled = true;
        this.v2RelaxationLambdaMin = 0.0006;
        this.v2RelaxationLambdaGain = 0.0024;
        this.v2RelaxationK = 15.0;
        this.v2RelaxationTc = 1.0;
        this.v2RelaxationMemoryBeta = 0.25;
        this.v2RelaxationDepthGamma = 0.30;
        this.v2ActivationFastGain = 0.18;
        this.v2BaselineGain = 0.002;
        this.v2BaselineRelaxation = 0.0005;
        this.v2BaselineResting = 0.02;
        this.v2ActivationRelaxationGain = 0.18;
        this.v2BaselineAdaptationRate = 0.002;
        this.v2BaselineHealingRate = 0.0005;
        this.v2DepthGain = 0.00002;
        this.v2DepthRelaxation = 0.00001;
        this.transportBalanceMode = "baseline";
        this.readinessMemoryScale = 1.0;
        this.readinessFieldScale = 1.0;
        this.readinessTransitionWidth = 1.0;
        this.validationMaxHotspotRadius = 10.0;
        this.couplingMatrix = new CouplingMatrix();
        initializeDefaultCouplingMatrix();
    }

    private void initializeDefaultCouplingMatrix() {
        couplingMatrix.set(0, 0, -0.10);
        couplingMatrix.set(0, 1, 0.05);
        couplingMatrix.set(0, 2, 0.04);
        couplingMatrix.set(0, 3, 0.04);
        couplingMatrix.set(1, 0, 0.06);
        couplingMatrix.set(1, 1, -0.05);
        couplingMatrix.set(1, 2, 0.03);
        couplingMatrix.set(1, 3, 0.02);
        couplingMatrix.set(2, 0, 0.04);
        couplingMatrix.set(2, 1, 0.02);
        couplingMatrix.set(2, 2, -0.008);
        couplingMatrix.set(2, 3, 0.01);
        couplingMatrix.set(3, 0, 0.05);
        couplingMatrix.set(3, 1, 0.02);
        couplingMatrix.set(3, 2, 0.01);
        couplingMatrix.set(3, 3, -0.03);
    }

    public void copyUnifiedDynamicsFrom(ParameterSet other) {
        this.couplingMatrix = new CouplingMatrix();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                this.couplingMatrix.set(i, j, other.couplingMatrix.get(i, j));
            }
        }
    }

    public ParameterSet copy() {
        ParameterSet copy = new ParameterSet(D, lambda, alpha, beta, gamma, stormDrain, stormThreshold,
                stormEndThreshold, regionActiveThreshold, t1, t2, t3,
                diffusionEnabled, decayEnabled, globalCouplingEnabled, nonlinearEnabled,
                stormEnabled, clampEnabled, seed);
        copy.v2TransportGain = this.v2TransportGain;
        copy.v2ActivationDecay = this.v2ActivationDecay;
        copy.v2MemoryGain = this.v2MemoryGain;
        copy.v2MemoryDecay = this.v2MemoryDecay;
        copy.v2MemoryInfluence = this.v2MemoryInfluence;
        copy.v2MemoryEffectK = this.v2MemoryEffectK;
        copy.memoryFormationGain = this.memoryFormationGain;
        copy.memoryMaximum = this.memoryMaximum;
        copy.memoryFormationThreshold = this.memoryFormationThreshold;
        copy.memoryDiffusionGain = this.memoryDiffusionGain;
        copy.memoryDepthRetentionGain = this.memoryDepthRetentionGain;
        copy.memoryDecayRate = this.memoryDecayRate;
        copy.v2ClampMax = this.v2ClampMax;
        copy.v3FieldDepthGain = this.v3FieldDepthGain;
        copy.depthGrowthGain = this.depthGrowthGain;
        copy.depthErosionGain = this.depthErosionGain;
        copy.v3FieldDepthDecay = this.v3FieldDepthDecay;
        copy.v3FieldDepthRadius = this.v3FieldDepthRadius;
        copy.v3DistanceDecay = this.v3DistanceDecay;
        copy.v3FieldDepthEffectK = this.v3FieldDepthEffectK;
        copy.v3FieldDepthDiffusionGain = this.v3FieldDepthDiffusionGain;
        copy.v3DepthSensitivity = this.v3DepthSensitivity;
        copy.v3DepthRecoveryGain = this.v3DepthRecoveryGain;
        copy.v3DepthMemoryRetentionGain = this.v3DepthMemoryRetentionGain;
        copy.v3DepthTransportGain = this.v3DepthTransportGain;
        copy.v3DepthGradientGain = this.v3DepthGradientGain;
        copy.v3ActivationBistabilityGain = this.v3ActivationBistabilityGain;
        copy.v3MemorySynchronizationGain = this.v3MemorySynchronizationGain;
        copy.v3TransportStateGain = this.v3TransportStateGain;
        copy.v2AdaptiveRelaxationEnabled = this.v2AdaptiveRelaxationEnabled;
        copy.v2RelaxationLambdaMin = this.v2RelaxationLambdaMin;
        copy.v2RelaxationLambdaGain = this.v2RelaxationLambdaGain;
        copy.v2RelaxationK = this.v2RelaxationK;
        copy.v2RelaxationTc = this.v2RelaxationTc;
        copy.v2RelaxationMemoryBeta = this.v2RelaxationMemoryBeta;
        copy.v2RelaxationDepthGamma = this.v2RelaxationDepthGamma;
        copy.v2ActivationFastGain = this.v2ActivationFastGain;
        copy.v2BaselineGain = this.v2BaselineGain;
        copy.v2BaselineRelaxation = this.v2BaselineRelaxation;
        copy.v2BaselineResting = this.v2BaselineResting;
        copy.v2ActivationRelaxationGain = this.v2ActivationRelaxationGain;
        copy.v2BaselineAdaptationRate = this.v2BaselineAdaptationRate;
        copy.v2BaselineHealingRate = this.v2BaselineHealingRate;
        copy.v2DepthGain = this.v2DepthGain;
        copy.v2DepthRelaxation = this.v2DepthRelaxation;
        copy.transportBalanceMode = this.transportBalanceMode;
        copy.readinessMemoryScale = this.readinessMemoryScale;
        copy.readinessFieldScale = this.readinessFieldScale;
        copy.readinessTransitionWidth = this.readinessTransitionWidth;
        copy.validationMaxHotspotRadius = this.validationMaxHotspotRadius;
        copy.copyUnifiedDynamicsFrom(this);
        return copy;
    }

    /**
     * Runtime parameters for the Minecraft substrate tick.
     * Uses validated laboratory defaults unless gameplay tuning is explicitly enabled.
     */
    public static ParameterSet runtime() {
        if (runtimeInstance == null) {
            runtimeInstance = new ParameterSet();
            if (com.utdmod.config.UTDConfig.substrateGameplayTuningEnabled) {
                runtimeInstance.applyConfigOverrides();
            }
        }
        return runtimeInstance;
    }

    /**
     * Clears cached runtime parameters (e.g. after toggling tuning mode).
     */
    public static void resetRuntimeCache() {
        runtimeInstance = null;
    }

    /**
     * Optional gameplay tuning overrides. Never applied unless
     * {@link com.utdmod.config.UTDConfig#substrateGameplayTuningEnabled} is true.
     */
    public void applyConfigOverrides() {
        v2TransportGain = UTDConfig.substrateTransportGain;
        v2ActivationDecay = UTDConfig.substrateActivationDecay;
        v2MemoryGain = UTDConfig.substrateMemoryGain;
        v2MemoryDecay = UTDConfig.substrateMemoryDecay;
        v2MemoryInfluence = UTDConfig.substrateMemoryInfluence;
        v2MemoryEffectK = UTDConfig.substrateMemoryEffectK;
        v3FieldDepthGain = UTDConfig.substrateFieldDepthGain;
        v3FieldDepthDecay = UTDConfig.substrateFieldDepthDecay;
        v3FieldDepthRadius = UTDConfig.substrateFieldDepthRadius;
        v3FieldDepthEffectK = UTDConfig.substrateFieldDepthEffectK;
        v3DistanceDecay = UTDConfig.substrateTransportDistanceDecay;
        v2ClampMax = UTDConfig.substrateClampMax;
        readinessMemoryScale = UTDConfig.substrateReadinessScale;
        readinessFieldScale = UTDConfig.substrateFieldDepthReadinessScale;
    }

    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add("D=" + D);
        lines.add("lambda=" + lambda);
        lines.add("alpha=" + alpha);
        lines.add("beta=" + beta);
        lines.add("gamma=" + gamma);
        lines.add("stormDrain=" + stormDrain);
        lines.add("stormThreshold=" + stormThreshold);
        lines.add("stormEndThreshold=" + stormEndThreshold);
        lines.add("regionActiveThreshold=" + regionActiveThreshold);
        lines.add("validationMaxHotspotRadius=" + validationMaxHotspotRadius);
        lines.add("t1=" + t1 + ", t2=" + t2 + ", t3=" + t3);
        return lines;
    }
}
