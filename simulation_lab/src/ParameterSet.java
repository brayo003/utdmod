import java.util.ArrayList;
import java.util.List;

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
    // V2 model parameters
    public double v2TransportGain; // transport strength from neighbors to activation
    public double v2ActivationDecay; // per-tick fractional decay of activation
    public double v2MemoryGain; // how much memory grows per unit activation
    public double v2MemoryDecay; // per-tick fractional decay of memory
    public double v2MemoryInfluence; // how memory amplifies incoming activation
    public double v2MemoryEffectK; // small constant for memory scaling
    public double v2ClampMax; // optional clamp for activation
    // Field Depth (structural memory / capacity) V3 parameters
    public double v3FieldDepthGain; // per-tick capacity growth coefficient (small)
    public double v3FieldDepthDecay; // per-tick capacity decay (very small)
    public double v3FieldDepthRadius; // maximum additional transport rings
    public double v3DistanceDecay; // distance attenuation for transport beyond base radius
    public double v3FieldDepthEffectK; // small constant for depth radius scaling
    public String transportBalanceMode;
    public double readinessMemoryScale;
    public double readinessFieldScale;
    public double readinessTransitionWidth;
    public long seed;
    // Validation configuration
    public double validationMaxHotspotRadius;

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
        // V2 defaults
        this.v2TransportGain = 0.08;
        this.v2ActivationDecay = 0.45; // fast decay per tick
        this.v2MemoryGain = 0.02; // slow accumulation
        this.v2MemoryDecay = 0.001; // very slow decay
        this.v2MemoryInfluence = 2.0; // amplifies incoming activation
        this.v2MemoryEffectK = 0.1;
        this.v2ClampMax = 5.0;
        // FieldDepth defaults: disabled by default, radius effect inactive
        this.v3FieldDepthGain = 0.0; // set >0 to enable slow growth
        this.v3FieldDepthDecay = 0.0001; // very slow decay
        this.v3FieldDepthRadius = 0.0; // no extra radius by default
        this.v3DistanceDecay = 1.0; // moderate distance attenuation
        this.v3FieldDepthEffectK = 0.1;
        this.transportBalanceMode = "baseline";
        this.readinessMemoryScale = 1.0;
        this.readinessFieldScale = 1.0;
        this.readinessTransitionWidth = 1.0;
        // validation default
        this.validationMaxHotspotRadius = 10.0;
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
        copy.v2ClampMax = this.v2ClampMax;
        copy.v3FieldDepthGain = this.v3FieldDepthGain;
        copy.v3FieldDepthDecay = this.v3FieldDepthDecay;
        copy.v3FieldDepthRadius = this.v3FieldDepthRadius;
        copy.v3DistanceDecay = this.v3DistanceDecay;
        copy.v3FieldDepthEffectK = this.v3FieldDepthEffectK;
        copy.transportBalanceMode = this.transportBalanceMode;
        copy.readinessMemoryScale = this.readinessMemoryScale;
        copy.readinessFieldScale = this.readinessFieldScale;
        copy.readinessTransitionWidth = this.readinessTransitionWidth;
        copy.validationMaxHotspotRadius = this.validationMaxHotspotRadius;
        return copy;
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
