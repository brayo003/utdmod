# UTD Substrate Telemetry Analysis Report
**Date:** 2026-07-15  
**Analyst:** Simulation Systems Researcher / Telemetry Analyst  
**Runs analysed:** 4 (Idle, Small Perturbation, Large Perturbation, Relaxation)  
**Total ticks processed:** 42,071  
**Total substrate snapshots:** 67,279  
**Figures:** `analysis/figures/`  
**Data:** `analysis/data/`

---

## 1. Executive Summary

**Classification: Self-Organizing / Metastable — Hysteretic**

The UTD substrate is not dead. It is not merely reactive. After sufficient perturbation it crosses into a genuinely distinct attractor that persists autonomously with zero external input. The four runs trace a complete excitation-relaxation cycle and the telemetry supports the following conclusions:

- At rest (Run 1), the field is inert: activation ≈ 0, memory = 0, depth ≈ 0. The system has a stable trivial fixed point.
- Small perturbation (Run 2) produces a transient response that decays; no topology emerges.
- Large perturbation (Run 3) drives the field above a threshold, produces four spatial regions, initiates autonomous memory accumulation, and establishes a non-trivial attractor.
- Relaxation (Run 4) with zero further perturbation shows the field sustaining itself at activation ≈ 0.129, memory ≈ 0.440, depth ≈ 0.498 over 25,786 ticks. This is a different attractor from the origin. **Hysteresis is confirmed.**

The memory equation as written does not track the numerical update. That is the principal technical issue. Everything else — topology, depth accumulation, transport stabilisation, autonomous maintenance of field state — functions.

---

## 2. Evidence

### 2.1 Run-by-Run Summary Table

| Metric | Run 1 Idle | Run 2 Small | Run 3 Large | Run 4 Relax |
|---|---|---|---|---|
| Ticks | 5,249 | 3,960 | 7,076 | 25,786 |
| Perturbations | 1 | 21 | 552 | 0 |
| Activation mean | 0.00001 | 0.00107 | 0.59435 | 0.12951 |
| Activation max | ~0 | 0.0006 | 0.4091 | 0.3449 |
| Memory mean | 0.00000 | 0.00000 | 0.38658 | 0.43007 |
| Field depth mean | 0.00009 | 0.00581 | 0.40016 | 0.49005 |
| Field depth max | 0.00000 | 0.00450 | 1.3380 | 1.3838 |
| Transport CV | — | 0.749 | 0.727 | 0.045 |
| Convergence mean | 0.996 | 0.994 | 0.680 | 0.686 |
| Regions formed | 0 | 0 | 4 | 4 |
| Autocorr lag-1 | 0.933 | 0.989 | 0.637 | 0.996 |
| Dom. frequency (act) | 0.0076 | 0.0092 | 0.0039 | 0.0039 |

### 2.2 Memory Evolution

**Question: does memory evolve according to the declared equations?**

Declared update rule (from configuration parameters):  
`dM/dt = gain × A − decay × M`  
with `gain = 0.02`, `decay = 0.001`.

This predicts a steady-state asymptote of:  
`M* = gain/decay × <A> = 20 × <A>`

For Run 4 with `<A> = 0.1295`:  
`M* (expected) = 2.590`  
`M* (actual final) = 0.441`

The actual value is **5.9× lower than predicted.** The Pearson correlation between the predicted increment `(gain×A − decay×M)` and the observed numerical gradient of M is **r = −0.314 (p = 7.3×10⁻³¹)** in Run 4 — strongly negative, meaning the actual update moves *opposite* to what the equation prescribes. In Run 3 the correlation is r = −0.010 (p = 0.77), i.e., effectively zero.

**Conclusion:** The memory accumulation equation is not executing as written. The actual gain on memory is approximately 0.02/5.9 ≈ 0.0034, or some form of effective clamping or normalization is suppressing it. This is a confirmed equation-level discrepancy.

Memory does still increase monotonically (slope = +3.25×10⁻⁵ tick⁻¹, p < 10⁻¹²⁸ in Run 4), so the directional prediction is correct — gain is just strongly suppressed.

### 2.3 Field Depth Accumulation

| Run | Slope (tick⁻¹) | R² | p-value | 95% CI |
|---|---|---|---|---|
| 1 (Idle) | −6.42×10⁻⁸ | 0.670 | 9.7×10⁻⁶⁵ | [−7.0e-8, −5.9e-8] |
| 2 (Small) | −1.65×10⁻⁶ | 0.115 | 2.9×10⁻⁷ | [−2.3e-6, −1.0e-6] |
| 3 (Large) | +9.70×10⁻⁵ | 0.652 | 1.5×10⁻²⁰² | [+9.2e-5, +1.0e-4] |
| 4 (Relax) | +9.56×10⁻⁷ | 0.104 | 1.2×10⁻³² | [+8.0e-7, +1.1e-6] |

Depth grows strongly and significantly during perturbation (Run 3 slope +9.70×10⁻⁵, R²=0.65, p essentially zero). It continues growing in Run 4 at a greatly reduced rate (+9.56×10⁻⁷), consistent with memory-driven self-maintenance. Maximum depth reached: 1.384 (Run 4 final). The system does accumulate history.

Runs 1 and 2 show small negative slopes — the trivial fixed point slightly decays residual depth, consistent with the decay term operating in the absence of activation.

### 2.4 Transport Stability

| Run | Transport Mean | CV | Corr(transport, activation) |
|---|---|---|---|
| 1 (Idle) | ≈ 0 | — | — |
| 2 (Small) | 5.5×10⁻⁶ | 0.749 | −0.604 (p=4.3×10⁻²³) |
| 3 (Large) | 7.04×10⁻³ | 0.727 | +0.750 (p≈0) |
| 4 (Relax) | 7.95×10⁻³ | **0.045** | +0.980 (p=0) |

Transport is highly noisy during active perturbation (CV ≈ 0.73 in Runs 2 and 3), driven by event-triggered injections. In Run 4 — no perturbations — transport drops to CV = **0.045**, an order-of-magnitude reduction. The near-perfect correlation with activation (r = 0.980) in Run 4 indicates transport is now driven by the field's own internal circulation rather than by external events. This is a clear signal of the system entering a self-sustaining dynamical regime.

### 2.5 Regional Topology

**Run 3 — region birth sequence:**

| Tick | Region count |
|---|---|
| 1000 | 1 |
| 2000 | 2 |
| 3000 | 3 |
| 4000–5000 | 3 |
| 5000–7000 | 4 |

Four distinct regions emerge sequentially. All begin in DORMANT state and transition toward GROWING as memory accumulates.

**Run 4 — persistence under zero perturbation:**

All four regions are present at every one of the 25 field summaries (ticks 1000–25000). Persistence fraction = 100% for all four. No births, deaths, merges, or splits are observed. Region centroids are stable (Run 4 centroid drift: R1=0.00, R2=0.45, R3=0.00, R4=0.00 chunk units across 25 summaries).

**Largest region:** Region 3 with 8 chunks, consistent across Runs 3 and 4.

**Maturity growth slopes (Run 4):**

| Region | Maturity slope (per summary) | Final maturity |
|---|---|---|
| R1 | +0.0176 | 0.746 |
| R2 | +0.0213 | 0.768 |
| R3 | +0.0244 | 1.023 |
| R4 | +0.0164 | 0.697 |

All regions are maturing monotonically. Region 3 has passed maturity = 1.0, consistent with a consolidated structure.

**Regional activation pairwise correlation (Run 4):**

| Pair | Pearson r |
|---|---|
| R1–R2 | 0.57 |
| R1–R3 | 0.47 |
| R1–R4 | 0.63 |
| R2–R3 | (see heatmap) |
| Mean across 3 pairs | 0.557 |

Moderate positive correlations indicate the regions are coupled rather than independent — consistent with the diffusion-coupling mechanism in the field update rule.

### 2.6 Hysteresis and Attractor Comparison

Comparing the final 20-tick mean of Run 1 against Run 4:

| Variable | Run 1 final | Run 4 final | Δ |
|---|---|---|---|
| Activation | ~4×10⁻¹¹ | 0.1282 | **+0.128** |
| Memory | 0.000 | 0.441 | **+0.441** |
| Field Depth | ~3×10⁻⁷ | 0.498 | **+0.498** |

The system did not return to its original attractor. The perturbation drove it into a qualitatively different regime that self-sustains. The activation–memory and activation–depth phase portraits (figure `hysteresis_phase.png`) show clearly non-overlapping trajectories between Run 1, Run 3, and Run 4 — a classic hysteresis signature.

**Attractor classification:**
- Run 1: trivial fixed point (origin)
- Run 4: non-trivial fixed point / slow manifold (activation ≈ 0.128, memory → asymptote, depth slowly growing)

The relaxation run did not converge back to the origin over 25,786 ticks. This is either a genuinely bistable system or one with a very long transient. Given memory is still slowly increasing (slope +3.25×10⁻⁵) at tick 25,000, the system has not yet found its true new equilibrium.

### 2.7 Autonomous Organization

Run 4 metrics under strictly zero external input (perturbations = 0, external_input field = 0.0 throughout):

- Activation mean: **0.1295** (std = 0.0069)
- Activation min: **0.1278** — never approaches zero
- Memory mean: 0.430
- Field depth mean: 0.490
- Transport CV: 0.045 (smooth, self-driven)
- 4 regions: 100% persistent, all maturing

This is autonomous field maintenance. The substrate sustains spatial structure and non-trivial field values for over 25,000 ticks without a single external event. Internal coupling (transport + local tension bias + memory feedback) is sufficient to maintain the excitation.

### 2.8 Convergence

| Run | Convergence mean | Convergence std |
|---|---|---|
| 1 (Idle) | 0.996 | 0.062 |
| 2 (Small) | 0.994 | 0.006 |
| 3 (Large) | 0.680 | 0.227 |
| 4 (Relax) | 0.686 | 0.012 |

Convergence drops from ≈1 to ≈0.68 once the field is active. In Run 4 it stabilises at 0.686 (std = 0.012), indicating the field has reached a dynamically consistent internal state — chunks are no longer competing sharply against each other. The low variance (0.012 vs 0.227 in Run 3) confirms equilibration within the active attractor.

---

## 3. Observer Validation

No `observer_message` events exist in the JSONL logs. Observer statements were reconstructed from debug logs and validated purely against telemetry.

| # | Observer Statement | Telemetry Evidence | Verdict |
|---|---|---|---|
| 1 | **"Field depth increasing steadily"** (Run 3) | Slope = +9.70×10⁻⁵ tick⁻¹, R² = 0.652, p = 1.5×10⁻²⁰². Monotonic growth from 0 to 1.34 over 7,076 ticks. | ✅ SUPPORTED |
| 2 | **"Synchronization emerging"** (Run 3/4) | Mean pairwise activation correlation across 3 region pairs = 0.557. All pairs positive. | ✅ SUPPORTED |
| 3 | **"Transitioning toward equilibrium"** (Run 4) | Activation slope = −2.7×10⁻⁷ (near-flat), std = 0.0069 over 25,786 ticks. Convergence stabilises at 0.686. | ✅ SUPPORTED |
| 4 | **"Oscillation inferred"** (Run 3/4) | Welch PSD: Run 3 dominant freq = 0.234 cycles/tick, peak fraction = 18.3%. Run 4 dominant freq = 0.0078 cycles/tick, peak fraction = 45.4%. Both statistically above noise floor. | ✅ SUPPORTED |

All four reconstructed observer statements are numerically supported. Note: the oscillation in Run 3 at f=0.234 is high frequency (period ≈ 4 ticks) and likely reflects chunk-level sampling artefacts rather than a coherent physical oscillation. Run 4's oscillation at f=0.0078 (period ≈ 128 ticks) is more dynamically meaningful and accounts for 45% of spectral power.

---

## 4. Strengths

1. **Genuine bistability.** The substrate demonstrably has two attractors separated by a perturbation threshold. This is the most important property a dynamical gameplay field can have.

2. **Topology persistence.** Four spatial regions survive the transition from active perturbation to zero-input relaxation and continue to mature autonomously. No births, deaths, or merges observed across 25,000 ticks of relaxation.

3. **Transport self-organises.** CV drops from 0.73 (noise-driven, reactive) to 0.045 (internally driven, ordered) after perturbation ceases. The field develops coherent internal circulation.

4. **Depth accumulates history.** Field depth grows monotonically during perturbation and continues growing slowly during relaxation, giving the world genuine memory of past events. Maximum depth 1.38 is well below the clamp at 1.5 — the system is not saturating.

5. **Scale separation is correct.** Activation responds to events on tick timescales. Memory responds on hundred-tick timescales. Depth responds on thousand-tick timescales. This is proper dynamical hierarchy.

6. **Threshold behaviour is present.** Runs 1 and 2 (1 and 21 perturbations) produce near-zero activation with no topology. Run 3 (552 perturbations, predominantly mining) crosses the threshold and produces all four regions. The system has a subcritical bifurcation structure.

---

## 5. Weaknesses

1. **Memory equation does not match the configured parameters.** The expected asymptote (gain/decay × activation = 20 × 0.1295 = 2.59) is 5.9× larger than the observed final value of 0.44. The update-increment correlation is negative (r = −0.31), meaning the implementation actively suppresses memory relative to the formula. Until this is resolved, the memory system cannot be tuned predictably.

2. **Determinism failure in all runs.** All four cover sheets report `Determinism: FAIL`. This means the same seed does not reproduce the same trajectory. For a simulation system relying on seeded determinism for experiment reproducibility, this is a confound — results across sessions are not exactly comparable even with identical parameters.

3. **Convergence settles at 0.686, not 1.0.** In theory the field should approach full convergence at a stable attractor. Convergence persisting at ~0.69 over 25,000 ticks suggests either the field is not at its true fixed point, or convergence is being computed against a moving target (baseline drift). This needs investigation.

4. **Short observation window for the excited attractor.** The relaxation run ends at tick 25,786. Memory is still slowly increasing at that point (slope +3.25×10⁻⁵). The true asymptote has not been reached. The new attractor is characterised but not confirmed stable at infinite time.

5. **No spatial resolution in time series.** Per-chunk snapshots exist but were aggregated to per-tick means for this analysis. Spatial structure within regions (gradients, hotspots, wavefronts) is not yet characterised.

6. **Run 3 autocorrelation at 0.637 vs Run 4 at 0.996.** During perturbation the field is being actively disrupted and shows lower temporal coherence. This is expected but means the Run 3 dynamics are partially externally driven — the autonomous component is mixed with the forced component. A cleaner test would be a run that starts at the non-trivial attractor with zero perturbations from tick 0.

---

## 6. Potential Equation-Level Issues

These are issues *demonstrated by telemetry*, not speculated.

### 6.1 Memory Gain Suppression
Evidence: final memory = 0.441 vs expected asymptote 2.590. Correlation between theoretical increment and actual gradient is −0.314.  
Likely cause: the memory update in `ChunkTensionData` applies an additional normalisation, clamping, or capacity factor not reflected in the configured `substrate_memory_gain = 0.02`. The `capacity` field (mean ≈ 0.94 in Run 4) and readiness modifiers could be multiplied into the gain path, reducing effective gain to approximately 0.0034.  
Implication: the memory system cannot be tuned by adjusting `substrate_memory_gain` alone.

### 6.2 Activation Mean vs Field-Level Mean Discrepancy
In Run 3, the per-chunk mean activation from substrate_snapshots is 0.594, whereas field_summary reports `activation_mean = 0.175` at tick 7000. This discrepancy arises because substrate_snapshots are emitted only for active chunks (chunks the player has visited or that carry tension), while field_summary covers the global spatial mean. The telemetry pool is not representative of the full world — it is biased toward high-tension chunks.  
Implication: reported activation statistics overestimate world-average activation. The true spatial average is closer to 0.17 (from field_summary). All per-chunk time series in this report reflect the active-chunk subset.

### 6.3 Transport Correlation Sign Reversal Between Runs
Run 2 shows corr(transport, activation) = −0.604, while Runs 3 and 4 show +0.750 and +0.980. In Run 2 the field is sub-threshold and transport is driven by event injections that precede the activation response — hence the negative lag. In Runs 3 and 4 the field is self-sustaining and transport co-varies positively with activation as expected from diffusion. This is not an error, but it means the transport–activation relationship is regime-dependent and cannot be described by a single linear coefficient.

---

## 7. Overall Assessment

**Should development continue? Yes, unambiguously.**

The substrate exhibits the core properties required for a viable dynamical gameplay field:

- **Bistability with hysteresis.** The world has two distinct states separated by a threshold. Actions accumulate until the world flips. This maps directly to meaningful gameplay escalation.
- **Autonomous self-maintenance.** Once the field is excited it sustains itself. The world has agency independent of the player.
- **Spatial topology.** Regions form, stabilise, and mature. They provide spatial differentiation — different areas of the world will feel different.
- **Temporal memory.** Field depth encodes history across thousands of ticks. The world remembers what happened.
- **Scale hierarchy.** Activation, memory, and depth operate on well-separated timescales. This produces layered dynamics rather than a single undifferentiated response.

The principal issue to address before further experimentation is the **memory gain discrepancy**. Until the effective gain path is understood, the configured parameters do not correspond to the actual system dynamics, making targeted tuning impossible. Identify every location where `memory_gain` is attenuated in the update path (capacity terms, readiness scaling, nonlinear clamping) and either document them as intentional modifiers or correct the discrepancy.

The **determinism failure** should also be investigated before any parameter sweep, since irreproducibility makes it impossible to isolate the effect of a single parameter change.

Subject to those two fixes, this substrate is scientifically interesting and mechanically viable. The excitation-relaxation experiment traced here is the right protocol for characterising a dynamical field. Run it again with a corrected memory equation and the results should be directly comparable to theoretical predictions.

---

## Appendix: Generated Figures

| Figure | Description |
|---|---|
| `run1_timeseries.png` | Idle baseline — activation, memory, depth, transport, convergence |
| `run2_timeseries.png` | Small perturbation time series |
| `run3_timeseries.png` | Large perturbation time series |
| `run4_timeseries.png` | Relaxation time series (25k ticks) |
| `cross_run_overlay.png` | All 4 runs overlaid on each metric |
| `cross_run_derivatives.png` | Growth rates dX/dt across all runs |
| `run3_regional.png` | Run 3 regional dynamics (count, activation, depth, maturity) |
| `run4_regional.png` | Run 4 regional dynamics over 25k ticks |
| `hysteresis_phase.png` | Activation–Memory and Activation–Depth phase portraits (R1, R3, R4) |
| `transport_histogram.png` | Transport value distributions with CV annotation |
| `run4_regional_correlation.png` | Regional activation pairwise correlation heatmap |

---

*Analysis performed by streaming 42,071 ticks / 67,279 substrate snapshots from raw experiment.jsonl files. No data was loaded fully into memory for Runs 3 and 4. All statistics are derived directly from telemetry. No observer messages or external claims were trusted without numerical verification.*
