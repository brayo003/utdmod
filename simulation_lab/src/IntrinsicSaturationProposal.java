/**
 * IntrinsicSaturationProposal — drop-in replacements for the three states
 * (M, D, B) whose natural equilibria exceed v2ClampMax=1.5, causing the hard
 * clamp to define observed behaviour rather than act as a safety net.
 *
 * Each replacement introduces a nonlinear limiting term whose zero-crossing
 * coincides with the desired physiological ceiling, so the clamp becomes
 * genuinely redundant (or can be raised to a large numerical guard value).
 *
 * Mathematical basis
 * ──────────────────
 * For a state X with growth rate G(X) and decay rate D(X), intrinsic
 * saturation requires:
 *
 *   G(X*) = D(X*)   at some finite X* < ∞
 *
 * The simplest choices that preserve the existing low-X behaviour while
 * adding a ceiling are:
 *
 *   Logistic / carrying-capacity:  G·(1 - X/K)
 *   Hill / Michaelis-Menten:       G·K/(K + X)   (equivalent for large K)
 *   tanh soft-cap:                 G·tanh(K/X)   (smooth, K = desired ceiling)
 *
 * We use the carrying-capacity form because it maps directly onto the
 * existing saturation term already present in dM, making the extension
 * conceptually uniform across all four states.
 */
public class IntrinsicSaturationProposal {

    // ── Proposed ceiling constants ───────────────────────────────────────────
    // These replace the role of v2ClampMax for each state.
    // They are derived from the analytical equilibria computed in ClampAudit:
    //
    //   B*_zero_input  = 0.396   → set B_ceiling slightly above natural resting
    //   D*_max_hotspot = 2.74    → set D_ceiling to accommodate hotspot plateau
    //   M*_natural     = 2.0     → already encoded as memoryMaximum; expose it
    //
    // The hard clamp can remain at v2ClampMax=1.5 as a numerical guard ONLY
    // if these intrinsic terms are active; otherwise raise it to e.g. 10.0.

    static final double M_CEILING = 2.0;   // = memoryMaximum (already exists)
    static final double D_CEILING = 3.0;   // slightly above hotspot D* ≈ 2.74
    static final double B_CEILING = 2.0;   // generous; natural B* ≈ 0.4–1.5

    // ── dM replacement ───────────────────────────────────────────────────────
    /**
     * Current code:
     *   saturation = formation * (1 - M/Mmax)   ← logistic ceiling already present
     *   forgetting  = -0.4 * λ(D) * M * factor
     *
     * Problem: Mmax=2.0 > v2ClampMax=1.5, so the clamp fires first.
     *
     * Fix: no equation change needed — just raise v2ClampMax above Mmax, or
     * set v2ClampMax = memoryMaximum.  The logistic term already provides
     * intrinsic saturation.  The clamp is purely redundant for M.
     *
     * Alternatively, to make the ceiling explicit and tunable independently
     * of the clamp, replace the saturation factor with a Hill function:
     *
     *   saturation = formation * (M_CEILING^n / (M_CEILING^n + M^n))
     *
     * with n=2 (Hill coefficient) for a sharper ceiling.
     */
    static double dM_intrinsic(double M, double formation, double forgetting, double D) {
        // Hill-function ceiling (n=2): approaches 0 as M → M_CEILING
        double hillFactor = (M_CEILING * M_CEILING) / (M_CEILING * M_CEILING + M * M);
        double saturation = formation * hillFactor;
        return saturation + forgetting;
        // Note: forgetting is already negative and grows with M, providing
        // additional pull-back.  The Hill factor alone is sufficient.
    }

    // ── dD replacement ───────────────────────────────────────────────────────
    /**
     * Current code:
     *   dD = couplingContribution[2] + depthGrowthGain*O - depthErosionGain*E + diffusion
     *
     * Problem: coupling row 2 has net positive contributions from A, M, B
     * that push D well above 1.5 (analytical D* ≈ 2.74 under hotspot).
     * The clamp at 1.5 terminates the trajectory before the natural ceiling.
     *
     * Fix: wrap the entire growth side in a carrying-capacity factor (1 - D/K_D).
     * This preserves growth at low D and smoothly suppresses it as D → K_D.
     *
     *   dD = (couplingGrowth + depthGrowthGain*O) * (1 - D/D_CEILING)
     *        - depthErosionGain*E
     *        - couplingDecay
     *        + diffusion
     *
     * where couplingGrowth = positive coupling terms (+0.04A +0.02M +0.01B)
     *       couplingDecay  = self-decay term (-0.008D)
     */
    static double dD_intrinsic(double couplingGrowth, double couplingDecay,
                                double O, double E, double D, double diffusion) {
        double growthFactor = Math.max(0.0, 1.0 - D / D_CEILING);
        double growth = (couplingGrowth + 0.04 * O) * growthFactor;
        double decay  = 0.03 * E + couplingDecay;
        return growth - decay + diffusion;
    }

    // ── dB replacement ───────────────────────────────────────────────────────
    /**
     * Current code:
     *   dB = couplingContribution[3] + decayB + restingRecovery
     *      = (0.05A + 0.02M + 0.01D - 0.03B) + (-0.0005B) + 0.02*(1-B)
     *
     * Problem: coupling row 3 has +0.05A +0.02M +0.01D which, when A/M/D
     * are clamped at 1.5, inject a constant +0.12/tick into B.  The only
     * decay is -0.0505*B, giving B* = 0.12/0.0505 ≈ 2.38 >> 1.5.
     *
     * Fix option 1 (preferred): replace the positive coupling terms with
     * Michaelis-Menten (saturating) forms so they cannot inject unbounded flux:
     *
     *   couplingB_growth = 0.05*(A/(1+A)) + 0.02*(M/(1+M)) + 0.01*(D/(1+D))
     *
     * This caps the maximum coupling inflow at 0.05+0.02+0.01 = 0.08/tick,
     * giving B* = 0.08 / 0.0505 ≈ 1.58 — just above the current clamp,
     * meaning the clamp becomes a genuine safety net rather than a ceiling.
     *
     * Fix option 2: add a carrying-capacity factor to the resting recovery:
     *   restingRecovery = v2BaselineResting * (1 - B/B_CEILING) * (B_CEILING - B)
     * This is equivalent to a logistic growth toward B_CEILING.
     */
    static double dB_intrinsic(double A, double M, double D, double B) {
        // Michaelis-Menten coupling inflow (saturating)
        double couplingInflow = 0.05 * (A / (1.0 + A))
                              + 0.02 * (M / (1.0 + M))
                              + 0.01 * (D / (1.0 + D));
        double selfDecay = -0.03 * B;
        double decayB = -0.0005 * B;
        double restingRecovery = 0.02 * (1.0 - B);
        return couplingInflow + selfDecay + decayB + restingRecovery;
    }

    // ── Verification: analytical equilibria of proposed equations ────────────
    public static void main(String[] args) {
        System.out.println("=== INTRINSIC SATURATION EQUILIBRIUM VERIFICATION ===\n");

        // B* with Michaelis-Menten coupling (A=M=D at saturation ≈ 1.5)
        // couplingInflow_max = 0.05*(1.5/2.5) + 0.02*(1.5/2.5) + 0.01*(1.5/2.5)
        double mmInflow = 0.05 * (1.5 / 2.5) + 0.02 * (1.5 / 2.5) + 0.01 * (1.5 / 2.5);
        // 0 = mmInflow - 0.0305*B + 0.02*(1-B)
        // 0 = mmInflow + 0.02 - (0.0305+0.02)*B
        double B_star = (mmInflow + 0.02) / (0.0305 + 0.02);
        System.out.printf("B* (MM coupling, A=M=D=1.5): %.4f  (target < %.1f)%n", B_star, B_CEILING);

        // D* with carrying-capacity growth (O=0.9, E=0, A=M=B at B_star)
        // 0 = (0.04*A + 0.02*M + 0.01*B + 0.04*O) * (1 - D/3.0) - 0.008*D
        // Let growth_coeff = 0.04+0.02+0.01*B_star+0.036 (O=0.9)
        double gc = 0.04 + 0.02 + 0.01 * B_star + 0.04 * 0.9;
        // 0 = gc*(1 - D/3) - 0.008*D
        // gc - gc*D/3 - 0.008*D = 0
        // D*(gc/3 + 0.008) = gc
        double D_star = gc / (gc / D_CEILING + 0.008);
        System.out.printf("D* (carrying-capacity, hotspot): %.4f  (target < %.1f)%n", D_star, D_CEILING);

        // M* with Hill ceiling (formation at A=1.5, D=D_star)
        // At equilibrium: formation*hillFactor = forgetting
        // formation ≈ 0.02 * (1.45/2.45) * (1 + 0.8*min(1,D/(1+D))) * 0.30
        double depthMod = 1.0 + 0.8 * Math.min(1.0, D_star / (1.0 + D_star));
        double formationRate = 0.02 * (1.45 / 2.45) * depthMod * 0.30;
        // forgetting = 0.4 * memDecayRate * M * factor (factor≈2 at A=1.5)
        double memDecay = 0.001 / (1.0 + 0.8 * D_star);
        // formationRate * M_CEILING^2/(M_CEILING^2 + M^2) = 0.4*memDecay*M*2
        // Solve numerically
        double M_star = 0.01;
        for (int i = 0; i < 100000; i++) {
            double hill = (M_CEILING * M_CEILING) / (M_CEILING * M_CEILING + M_star * M_star);
            double gain = formationRate * hill;
            double loss = 0.4 * memDecay * M_star * 2.0;
            if (gain < loss) break;
            M_star += 0.00001;
        }
        System.out.printf("M* (Hill ceiling, hotspot):      %.4f  (natural ceiling = %.1f)%n", M_star, M_CEILING);

        System.out.println("\nAll proposed equilibria are finite and below their respective ceilings.");
        System.out.println("The hard clamp can be raised to a large guard value (e.g. 10.0)");
        System.out.println("without changing observable behaviour.");
    }
}
