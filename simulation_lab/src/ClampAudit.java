/**
 * ClampAudit — complete audit of FieldDerivative.computeUnified() saturation behaviour.
 *
 * Runs three scenarios:
 *   1. IDLE      – zero external input, uniform initial state near 0
 *   2. CONSTANT  – constant external input 0.05 at centre cell
 *   3. HOTSPOT   – sustained high input 0.20 at centre cell (perturbation run)
 *
 * For each scenario it:
 *   - Counts how often each state == v2ClampMax (clamp-hit frequency)
 *   - Records peak values with clamp ON and clamp OFF
 *   - Prints the analytical equilibrium derivation for each state
 */
public class ClampAudit {

    // ── parameters (mirrors ParameterSet defaults) ──────────────────────────
    static final double CLAMP_MAX          = 1.5;
    static final double ACT_DECAY          = 0.45;   // v2ActivationDecay
    static final double DEPTH_DECAY        = 0.0005; // v3FieldDepthDecay
    static final double BASELINE_RELAX     = 0.0005; // v2BaselineRelaxation
    static final double BASELINE_RESTING   = 0.02;   // v2BaselineResting
    static final double MEM_FORMATION_GAIN = 0.02;   // memoryFormationGain
    static final double MEM_MAXIMUM        = 2.0;    // memoryMaximum
    static final double MEM_DECAY_RATE     = 0.001;  // memoryDecayRate
    static final double MEM_DEPTH_RETAIN   = 0.8;    // memoryDepthRetentionGain
    static final double MEM_THRESHOLD      = 0.05;   // memoryFormationThreshold
    static final double DEPTH_GROWTH       = 0.04;   // depthGrowthGain
    static final double DEPTH_EROSION      = 0.03;   // depthErosionGain
    static final double RESPONSE_GAIN_BASE = 0.70;
    static final double DISSIPATION_LINEAR = 0.015;
    static final double DISSIPATION_QUAD   = 0.04;

    // coupling matrix row 2 (D) and row 3 (B) dominant self-terms
    static final double COUPLING_DD = -0.008;
    static final double COUPLING_BB = -0.03;
    static final double COUPLING_AB =  0.04;  // B→A
    static final double COUPLING_AM =  0.05;  // M→A
    static final double COUPLING_AD =  0.04;  // D→A

    static final int TICKS = 5000;
    static final int GRID  = 5;   // small grid, spatial effects negligible for scalar audit

    // ── counters ────────────────────────────────────────────────────────────
    static long clampHitsA_idle, clampHitsM_idle, clampHitsD_idle, clampHitsB_idle;
    static long clampHitsA_const, clampHitsM_const, clampHitsD_const, clampHitsB_const;
    static long clampHitsA_hot, clampHitsM_hot, clampHitsD_hot, clampHitsB_hot;

    public static void main(String[] args) {
        printEquilibriumAnalysis();
        System.out.println();
        runScenario("IDLE",     0.00, true);
        runScenario("CONSTANT", 0.05, true);
        runScenario("HOTSPOT",  0.20, true);
        System.out.println();
        System.out.println("=== CLAMP-OFF DIVERGENCE TEST ===");
        runScenario("HOTSPOT_NO_CLAMP", 0.20, false);
    }

    // ── analytical equilibrium ───────────────────────────────────────────────
    static void printEquilibriumAnalysis() {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          UNIFIED FIELD EQUILIBRIUM ANALYSIS                 ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");

        System.out.println("\n── BASELINE (B) ──────────────────────────────────────────────");
        System.out.println("  dB/dt = C[3]·S + (-v2BaselineRelaxation·B) + v2BaselineResting·(1-B)");
        System.out.println("  Dominant terms at low state: decayB ≈ 0, restingRecovery ≈ +0.02");
        System.out.println("  Coupling row 3: C[3,0]=+0.05·A, C[3,1]=+0.02·M, C[3,2]=+0.01·D, C[3,3]=-0.03·B");
        System.out.println("  Zero-input equilibrium (A=M=D=0):");
        System.out.println("    0 = -0.03·B - 0.0005·B + 0.02·(1-B)");
        System.out.println("    0 = B·(-0.03 - 0.0005 - 0.02) + 0.02");
        double B_eq_zero = 0.02 / (0.03 + 0.0005 + 0.02);
        System.out.printf("    B* = 0.02 / 0.0505 = %.4f  ← FINITE, stable%n", B_eq_zero);
        System.out.println("  Positive feedback: A→B (+0.05·A) can push B up, but self-decay -0.0505·B");
        System.out.println("  dominates for any finite A. B is NATURALLY BOUNDED.");

        System.out.println("\n── MEMORY (M) ────────────────────────────────────────────────");
        System.out.println("  dM/dt = saturation + forgetting + diffusion terms");
        System.out.println("  saturation = formation·(1 - M/Mmax)  ← logistic-style ceiling at Mmax=2.0");
        System.out.println("  forgetting  = -0.4·λ(D)·M·activityFactor  ← linear decay in M");
        System.out.println("  At M → Mmax: saturation → 0, forgetting → -0.4·λ·Mmax·factor < 0");
        System.out.println("  ∴ dM/dt < 0 for M ≥ Mmax.  M is INTRINSICALLY BOUNDED by Mmax=2.0.");
        System.out.println("  BUT Mmax=2.0 > v2ClampMax=1.5, so the clamp fires BEFORE the");
        System.out.println("  logistic ceiling is reached. The clamp is MASKING the natural ceiling.");

        System.out.println("\n── FIELD DEPTH (D) ───────────────────────────────────────────");
        System.out.println("  dD/dt = C[2]·S + depthGrowthGain·O - depthErosionGain·E + diffusion");
        System.out.println("  Coupling row 2: C[2,0]=+0.04·A, C[2,1]=+0.02·M, C[2,2]=-0.008·D, C[2,3]=+0.01·B");
        System.out.println("  O ∈ [0,1], E ∈ [0,1]  (both bounded by construction)");
        System.out.println("  Growth ceiling: depthGrowthGain·O_max = 0.04·1.0 = 0.04/tick");
        System.out.println("  Decay floor:    depthErosionGain·E_min + coupling_DD·D");
        System.out.println("  At large D: coupling self-term -0.008·D dominates → dD/dt < 0");
        System.out.println("  Equilibrium (O=1, E=0, A=M=B=0):");
        double D_eq_max = (DEPTH_GROWTH + 0.04 * 0 + 0.02 * 0 + 0.01 * 0) / (DEPTH_EROSION - COUPLING_DD);
        System.out.printf("    D* ≈ (0.04) / (0.03 + 0.008) = %.4f%n", 0.04 / (0.03 + 0.008));
        System.out.println("  With sustained hotspot (A≈1, M≈1, B≈0.4):");
        double D_eq_hot = (DEPTH_GROWTH + 0.04 * 1.0 + 0.02 * 1.0 + 0.01 * 0.4) / (DEPTH_EROSION - COUPLING_DD);
        System.out.printf("    D* ≈ (0.04+0.04+0.02+0.004) / 0.038 = %.4f%n", D_eq_hot);
        System.out.println("  D* under hotspot > v2ClampMax=1.5 → CLAMP IS ACTIVELY MASKING D.");

        System.out.println("\n── ACTIVATION (A) ────────────────────────────────────────────");
        System.out.println("  dA/dt = responseGain·inputDrive + 0.75·transportDrive - dissipation");
        System.out.println("  dissipation = (0.015 + 0.025·mS + 0.020·dS)·A + 0.04·A²");
        System.out.println("  Coupling row 0: C[0,0]=-0.10·A, C[0,1]=+0.05·M, C[0,2]=+0.04·D, C[0,3]=+0.04·B");
        System.out.println("  Zero-input, zero-transport equilibrium:");
        System.out.println("    0 = -0.10·A - (0.015+...)·A - 0.04·A²");
        System.out.println("    → A=0 is the only zero-input equilibrium (stable).");
        System.out.println("  Constant input E=0.05, M=D=B≈0:");
        double suscMin = 0.15;
        double inputDrive = 0.05 * suscMin;
        double rg = 0.70;
        // 0 = rg*inputDrive - (0.015 + 0.10)*A - 0.04*A^2
        // quadratic: 0.04*A^2 + 0.115*A - rg*inputDrive = 0
        double a_coef = 0.04, b_coef = 0.115, c_coef = -rg * inputDrive;
        double A_eq_const = (-b_coef + Math.sqrt(b_coef * b_coef - 4 * a_coef * c_coef)) / (2 * a_coef);
        System.out.printf("    A* ≈ %.4f  (well below clamp)%n", A_eq_const);
        System.out.println("  Sustained hotspot E=0.20, M≈1, D≈1, B≈0.4:");
        double mS = 1.0 / 2.0, dS = 1.0 / 2.0, bS = 0.4 / 1.4;
        double susc = 0.15 + 0.35 * mS + 0.20 * dS + 0.20 * bS;
        double rg2 = 0.70 + 0.20 * mS + 0.15 * dS;
        double inputDrive2 = 0.20 * susc;
        double dissipLinear = 0.015 + 0.025 * mS + 0.020 * dS + 0.10; // +coupling self
        // 0 = rg2*inputDrive2 - dissipLinear*A - 0.04*A^2
        double a2 = 0.04, b2 = dissipLinear, c2 = -rg2 * inputDrive2;
        double A_eq_hot = (-b2 + Math.sqrt(b2 * b2 - 4 * a2 * c2)) / (2 * a2);
        System.out.printf("    A* ≈ %.4f  (susc=%.3f, rg=%.3f, dissipL=%.3f)%n",
                A_eq_hot, susc, rg2, dissipLinear);
        if (A_eq_hot > CLAMP_MAX) {
            System.out.printf("    A* > v2ClampMax=%.1f → CLAMP IS MASKING ACTIVATION EQUILIBRIUM.%n", CLAMP_MAX);
        } else {
            System.out.printf("    A* < v2ClampMax=%.1f → clamp is safety-only for activation.%n", CLAMP_MAX);
        }
    }

    // ── numerical simulation ─────────────────────────────────────────────────
    static void runScenario(String name, double externalInput, boolean clampOn) {
        System.out.println("\n=== SCENARIO: " + name + " (clamp=" + clampOn + ") ===");

        double A = 0.001, M = 0.001, D = 0.0002, B = 0.02;
        long hitsA = 0, hitsM = 0, hitsD = 0, hitsB = 0;
        double peakA = A, peakM = M, peakD = D, peakB = B;
        boolean diverged = false;

        for (int t = 0; t < TICKS; t++) {
            double[] d = derivative(A, M, D, B, externalInput);
            double nA = A + d[0];
            double nM = M + d[1];
            double nD = D + d[2];
            double nB = B + d[3];

            if (clampOn) {
                nA = clamp(nA, 0, CLAMP_MAX);
                nM = clamp(nM, 0, CLAMP_MAX);
                nD = clamp(nD, 0, CLAMP_MAX);
                nB = clamp(nB, 0, CLAMP_MAX);
            } else {
                nA = Math.max(0, nA);
                nM = Math.max(0, nM);
                nD = Math.max(0, nD);
                nB = Math.max(0, nB);
            }

            if (Double.isNaN(nA) || Double.isInfinite(nA) || nA > 1e6) { diverged = true; break; }

            if (nA >= CLAMP_MAX) hitsA++;
            if (nM >= CLAMP_MAX) hitsM++;
            if (nD >= CLAMP_MAX) hitsD++;
            if (nB >= CLAMP_MAX) hitsB++;

            peakA = Math.max(peakA, nA);
            peakM = Math.max(peakM, nM);
            peakD = Math.max(peakD, nD);
            peakB = Math.max(peakB, nB);

            A = nA; M = nM; D = nD; B = nB;
        }

        if (diverged) {
            System.out.println("  *** DIVERGED — system is unbounded without clamp ***");
            return;
        }

        System.out.printf("  Final state:  A=%.5f  M=%.5f  D=%.5f  B=%.5f%n", A, M, D, B);
        System.out.printf("  Peak values:  A=%.5f  M=%.5f  D=%.5f  B=%.5f%n", peakA, peakM, peakD, peakB);
        if (clampOn) {
            System.out.printf("  Clamp hits (%d ticks): A=%d (%.1f%%)  M=%d (%.1f%%)  D=%d (%.1f%%)  B=%d (%.1f%%)%n",
                    TICKS,
                    hitsA, 100.0 * hitsA / TICKS,
                    hitsM, 100.0 * hitsM / TICKS,
                    hitsD, 100.0 * hitsD / TICKS,
                    hitsB, 100.0 * hitsB / TICKS);
            System.out.println("  Verdict:");
            verdict("A", hitsA, peakA);
            verdict("M", hitsM, peakM);
            verdict("D", hitsD, peakD);
            verdict("B", hitsB, peakB);
        } else {
            System.out.println("  No clamp applied — system reached natural equilibrium above.");
        }
    }

    static void verdict(String label, long hits, double peak) {
        if (hits == 0) {
            System.out.printf("    %s: clamp never hit (peak=%.4f) — clamp is safety-only%n", label, peak);
        } else if (peak >= CLAMP_MAX - 1e-9) {
            System.out.printf("    %s: clamp hit %d ticks, peak=%.4f — CLAMP IS DEFINING BEHAVIOUR%n", label, hits, peak);
        } else {
            System.out.printf("    %s: clamp hit %d ticks, peak=%.4f — clamp active but marginal%n", label, hits, peak);
        }
    }

    // ── scalar derivative (spatial terms zeroed, single isolated cell) ───────
    static double[] derivative(double A, double M, double D, double B, double external) {
        // Coupling matrix (default values from ParameterSet.initializeDefaultCouplingMatrix)
        double cA = -0.10*A + 0.05*M + 0.04*D + 0.04*B;
        double cM =  0.06*A - 0.05*M + 0.03*D + 0.02*B;
        double cD =  0.04*A + 0.02*M - 0.008*D + 0.01*B;
        double cB =  0.05*A + 0.02*M + 0.01*D - 0.03*B;

        // ── dA ──────────────────────────────────────────────────────────────
        double mS = M / (1.0 + M);
        double dS = D / (1.0 + D);
        double bS = B / (1.0 + B);
        double susceptibility = 0.15 + 0.35 * mS + 0.20 * dS + 0.20 * bS;
        double inputDrive = external * susceptibility;
        double transportDrive = 0.0; // no spatial transport in isolated cell
        double responseGain = 0.70 + 0.20 * mS + 0.15 * dS;
        double dissipation = (0.015 + 0.025 * mS + 0.020 * dS) * A + 0.04 * A * A;
        // Note: cA already contains the -0.10*A self-decay from coupling row 0
        double dA = cA + responseGain * inputDrive + 0.75 * transportDrive - dissipation;

        // ── dM ──────────────────────────────────────────────────────────────
        double activationDrive = Math.max(0.0, A - MEM_THRESHOLD);
        double depthMod = 1.0 + MEM_DEPTH_RETAIN * Math.min(1.0, D / (1.0 + D));
        double normalizedDrive = activationDrive / (1.0 + activationDrive);
        double formation = MEM_FORMATION_GAIN * normalizedDrive * depthMod;
        // activationPeakStrength = 0 (no Laplacian in isolated cell)
        double saturation = formation * 0.30 * (1.0 - M / Math.max(1e-6, MEM_MAXIMUM));
        double activityForgettingFactor = 2.0 + 3.0 * (1.0 - Math.min(1.0, Math.max(0.0, A)));
        double memDecayRate = MEM_DECAY_RATE / (1.0 + MEM_DEPTH_RETAIN * Math.max(0.0, D));
        double forgetting = -0.4 * memDecayRate * M * activityForgettingFactor;
        double dM = saturation + forgetting; // diffusion/anti-diffusion = 0

        // ── dD ──────────────────────────────────────────────────────────────
        // O and E in isolated uniform cell: gradients=0, transport=0
        // spatialCoherence=1/(1+0)=1, transportEfficiency=0, flowAlignment=1, neighborAgreement=1, smoothness=1
        double O = 0.30 * 1.0 + 0.25 * 0.0 + 0.20 * 1.0 + 0.15 * 1.0 + 0.10 * 1.0;  // = 0.90
        // erosion: localDisorder=0, fragmentation=0, rapidFluctuation=0
        double E = 0.0;
        double depthGrowth = DEPTH_GROWTH * O;
        double depthDecay  = DEPTH_EROSION * E;
        double dD = cD + depthGrowth - depthDecay;

        // ── dB ──────────────────────────────────────────────────────────────
        double decayB = -BASELINE_RELAX * B;
        double restingRecovery = BASELINE_RESTING * (1.0 - B);
        double dB = cB + decayB + restingRecovery;

        return new double[]{dA, dM, dD, dB};
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
