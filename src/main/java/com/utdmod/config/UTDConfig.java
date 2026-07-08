package com.utdmod.config;

public class UTDConfig {
    public static final int SIGNAL_SMOOTHING = 10;
    public static final boolean ENABLE_PROFILING = true;
    public static final double regionActiveTensionThreshold = 0.35;

    /**
     * When false (default), substrate integration uses validated laboratory ParameterSet defaults only.
     * When true, {@link com.utdmod.tension.ParameterSet#applyConfigOverrides()} may replace exposed knobs.
     */
    public static boolean substrateGameplayTuningEnabled = false;

    // Gameplay-tuning overrides (inactive unless substrateGameplayTuningEnabled is true)
    public static double substrateTransportGain = 0.08;
    public static double substrateActivationDecay = 0.45;
    public static double substrateMemoryGain = 0.02;
    public static double substrateMemoryDecay = 0.001;
    public static double substrateMemoryInfluence = 2.0;
    public static double substrateReadinessScale = 1.0;
    public static double substrateTransportDistanceDecay = 1.0;
    public static double substrateMemoryEffectK = 0.1;
    public static double substrateLocalTensionBias = 0.04;
    
    // Field depth tuning overrides (laboratory defaults: gain=0.005, decay=0.0005, radius=0.0)
    public static double substrateFieldDepthGain = 0.005;
    public static double substrateFieldDepthDecay = 0.0005;
    public static double substrateFieldDepthRadius = 0.0;
    public static double substrateFieldDepthEffectK = 0.1;

    // Clamp (laboratory default: 1.5)
    public static double substrateClampMax = 1.5;
    
    // Readiness: field-depth scale for variant_5_combined_memory_field_depth.
    // Currently unused because computeReadiness uses variant_1 (memory-only).
    // Set to a positive value and uncomment the field-depth term in
    // SubstrateState.computeReadiness to enable combined readiness.
    public static double substrateFieldDepthReadinessScale = 1.0;
}
