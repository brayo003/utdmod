# Equations

> All equations in this file are derived from direct code inspection of the current source.
> Every value and formula has a verified line-of-code counterpart.
> The old scalar ODE system (D·T̄_nbr + α·T² − β·T³ + γ·G − λ·T for chunks; G += −λG + αG² + I for global)
> was removed during the substrate refactor. See `docs/archive/` for historical equations.

---

## 1. Primary State Variables

The only evolving dynamical field is the per-chunk `SubstrateState` object.
Tension is not stored; it is computed on demand.

| Variable | Symbol | Class field | Range |
|---|---|---|---|
| Activation | A | `SubstrateState.activation` | [0, v2ClampMax=4.0] |
| Memory | M | `SubstrateState.memory` | [0, v2ClampMax=4.0] |
| Field Depth | D | `SubstrateState.fieldDepth` | [0, v2ClampMax=4.0] |
| Baseline | B | `SubstrateState.baseline` | [0, v2ClampMax=4.0] |

Source: `com.utdmod.tension.SubstrateState`, `com.utdmod.tension.ParameterSet`

---

## 2. Local Tension Metric T(x)

`TensionManager` and all threshold comparisons use this derived metric, not a stored value.

$$
T(x) = \frac{w_A \cdot \frac{A}{1+A} + w_M \cdot \frac{M}{1+M} + w_D \cdot \frac{D}{1+D}}{w_A + w_M + w_D}
$$

**Default weights** (from `ParameterSet`):

| Weight | Symbol | Default value |
|---|---|---|
| Activation weight | w_A | 0.40 |
| Memory weight | w_M | 0.25 |
| Depth weight | w_D | 0.20 |

Range of T(x): [0, 1) by construction (each term is bounded in [0,1)).

Source: `com.utdmod.tension.SubstrateObservationModel.computeLocalTension()`

---

## 3. Canonical Substrate Update — FieldDerivative.computeUnified()

Applied every 5 server ticks via `ChunkTensionData.integrateAllSubstrateStates()`.
Double-buffer semantics: all derivatives read the same tick-start snapshot.

Source: `com.utdmod.tension.FieldDerivative.computeUnified()`

### 3.1 Activation (A)

$$
\Delta A = R_g \cdot E_{\text{eff}} + 0.75 \cdot (T_{\text{in}} + 0.35 \cdot \nabla^2 A) - \text{diss}(A,M,D)
$$

where:

$$
\text{susceptibility} = 0.15 + 0.35 \cdot \frac{M}{1+M} + 0.20 \cdot \frac{D}{1+D} + 0.20 \cdot \frac{B}{1+B}
$$

$$
E_{\text{eff}} = E_{\text{ext}} \cdot \text{susceptibility}
$$

$$
R_g = 0.70 + 0.20 \cdot \frac{M}{1+M} + 0.15 \cdot \frac{D}{1+D}
$$

$$
\text{diss}(A,M,D) = \left(0.015 + 0.025 \cdot \frac{M}{1+M} + 0.020 \cdot \frac{D}{1+D}\right) A + 0.04 A^2
$$

### 3.2 Memory (M)

$$
\Delta M = \text{sat} + \text{forgetting} + \text{anti-diffusion} + \text{diffusion}
$$

$$
\text{formation}(A,B,D) = g_M \cdot \frac{\max(0, A - \theta_M)}{1 + \max(0, A - \theta_M)} \cdot \left(1 + \gamma_{DM} \cdot \min\!\left(1, \frac{D}{1+D}\right)\right)
$$

$$
\text{sat} = \left(1 - \frac{M}{M_{\max}}\right) \cdot \text{formation} \cdot (0.30 + 0.70 \cdot \text{peak\_strength})
$$

$$
\text{forgetting} = -0.4 \cdot r_M(D) \cdot M \cdot \left(2 + 3 \cdot (1 - \min(1, A))\right)
$$

$$
r_M(D) = \frac{r_{M0}}{1 + \gamma_{DM} \cdot D}
$$

Key defaults: g_M = `memoryFormationGain` = 0.02, θ_M = `memoryFormationThreshold` = 0.05, M_max = 2.0, r_M0 = `memoryDecayRate` = 0.001

### 3.3 Field Depth (D)

$$
\Delta D = \text{coupling}_D + \text{growth}_D \cdot a_D \cdot g_D \cdot f_{\text{cap}} - \text{decay}_D + \text{diffusion}_D
$$

$$
a_D = \frac{A}{1+A}, \quad m_D = \frac{M}{1+M}
$$

$$
D_{\max}^{\text{eff}} = D_{\max} + \delta_D \cdot m_D, \quad f_{\text{cap}} = \max\!\left(0, 1 - \frac{D}{D_{\max}^{\text{eff}}}\right)
$$

$$
\text{coupling}_D = 0.04 \cdot a_D \cdot f_{\text{cap}} + 0.02 \cdot m_D \cdot f_{\text{cap}} - 0.008 D + 0.01 \cdot \frac{B}{1+B} \cdot \text{persistence} \cdot f_{\text{cap}}
$$

Key defaults: D_max = 2.0, depthGrowthGain = 0.04, v3FieldDepthDecay = 0.0005

### 3.4 Baseline (B)

$$
\Delta B = \underbrace{0.05 \cdot \frac{A}{1+A} + 0.02 \cdot \frac{M}{1+M} + 0.01 \cdot \frac{D}{1+D}}_{\text{coupling inflow}} - 0.03 B - \lambda_B B + r_B (1 - B)
$$

Key defaults: v2BaselineRelaxation = 0.0005, v2BaselineResting = 0.02

---

## 4. Transport

Incoming transport at chunk position `pos` is computed by `SubstrateTransport.computeIncoming()`:

$$
T_{\text{in}} = g_T \cdot \left(1 - \frac{A}{1+A}\right) \cdot (1 + g_{DT} \cdot \phi_D) \cdot \sum_{n \in \mathcal{N}(pos)} w_n \cdot \frac{A_n}{1+A_n} \cdot f_n \cdot \kappa_n
$$

where for each neighbor n at distance d:

$$
w_n = e^{-d / \ell}, \quad f_n = \text{memory modulation}, \quad \kappa_n = \text{state compatibility modifier}
$$

Key defaults: `v2TransportGain` = 0.08, `v3DistanceDecay` = 1.0

Source: `com.utdmod.tension.SubstrateTransport`

---

## 5. Global Tension (derived metric)

`TensionManager.recompute()` runs every server tick and aggregates chunk-level T(x).
There is no ODE. This is a spatial mean.

$$
G_{\text{mean}} = \frac{1}{|C|} \sum_{x \in C} T(x)
$$

$$
G_{\text{max}} = \max_{x \in C} T(x)
$$

$$
\sigma^2_G = \frac{1}{|C|} \sum_{x \in C} T(x)^2 - G_{\text{mean}}^2
$$

where C = set of all loaded chunks with substrate state.

Source: `com.utdmod.core.TensionManager.recompute()`

---

## 6. Storm Thresholds

### 6.1 Global storm flag

```
stormActive := true   when globalMeanTension > 0.55  (STORM_THRESHOLD)
stormActive := false  when globalMeanTension < 0.40  (STORM_HYSTERESIS)
```

Source: `com.utdmod.core.TensionManager`

### 6.2 Chunk-local storm flag

```
stormActive[chunk] := true   when T(x) > 0.95
stormActive[chunk] := false  when T(x) < 0.25
```

Source: `com.utdmod.core.TensionServerTick.updateChunkStateFromMetric()`

---

## 7. Chunk State Transitions

Driven by T(x), not by a stored tension value.

| Transition | T(x) threshold | Hysteresis (downgrade) |
|---|---|---|
| STABLE → STRAINED | > 0.95 | < 0.73 (0.95 - 0.22) |
| STRAINED → FRACTURED | > 1.28 | < 1.06 (1.28 - 0.22) |
| FRACTURED → DECOUPLED | > 3.0 | < 2.78 (3.0 - 0.22) |

`STATE_HYSTERESIS = 0.22` — all three upgrade thresholds use the same offset.

Source: `com.utdmod.tension.ChunkTensionData.calculateState()`

---

## 8. Corruption Tiers

| Tier | globalMeanTension >= | Name |
|---|---|---|
| 0 | — | None |
| 1 | 0.20 | Subtle |
| 2 | 0.40 | Moderate |
| 3 | 0.60 | Severe |
| 4 | 0.80 | Catastrophic |

Source: `com.utdmod.core.TensionManager` (`CORRUPTION_THRESHOLDS = {0.20, 0.40, 0.60, 0.80}`)

---

## 9. Region State

`Region.getState()` computed from derived metrics:

| RegionState | Condition |
|---|---|
| `ANCIENT` | maturity > 2.25 AND age > 3200 |
| `FRACTURED` | meanTension >= 1.05 AND chunkCount >= 6 AND fracturedChunks >= max(1, chunkCount/8) |
| `STRAINED` | meanTension >= 0.78 OR maturity > 1.15 OR fracturedDensity > 0.16 OR stormDensity > 0.12 |
| `GROWING` | meanTension >= 0.35 OR maturity > 0.5 OR age > 900 |
| `DORMANT` | (none of the above) |

Region maturity exponential smoothing:

$$
M_{r,t+1} = M_{r,t} + 0.005 \cdot (P_r - M_{r,t})
$$

where P_r = region potential = meanTension × sqrt(chunkCount) × densityBonus.

Source: `com.utdmod.core.Region`, `com.utdmod.core.RegionState`

---

## 10. Active Chunk Threshold

A chunk is counted as "active" (eligible for region formation, active fraction metric) when:

$$
T(x) > \text{ACTIVE\_TENSION\_THRESHOLD} = 0.15
$$

Source: `com.utdmod.tension.SubstrateState.ACTIVE_TENSION_THRESHOLD`
