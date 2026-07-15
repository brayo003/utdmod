# Parameter Reference

> All values verified against `com.utdmod.tension.ParameterSet` constructor defaults.
> The old `PARAMETER_CATALOG.md` described the pre-refactor scalar ODE system; it has been
> archived. See `docs/archive/` for those values.
>
> Runtime parameters are read from `ParameterSet.runtime()`. If
> `UTDConfig.substrateGameplayTuningEnabled` is true, overrides from `utd-mod-config.properties`
> are applied via `ParameterSet.applyConfigOverrides()`.

---

## Tension metric weights

| Symbol | Field | Default | Description |
|---|---|---|---|
| w_A | `tensionActivationWeight` | 0.40 | Weight of activation term in T(x) |
| w_M | `tensionMemoryWeight` | 0.25 | Weight of memory term in T(x) |
| w_D | `tensionDepthWeight` | 0.20 | Weight of field depth term in T(x) |
| w_R | `tensionReadinessWeight` | 0.10 | (unused in current T(x) formula — defined but not applied) |
| w_K | `tensionCapacityWeight` | 0.05 | (unused in current T(x) formula — defined but not applied) |

T(x) = (w_A·A/(1+A) + w_M·M/(1+M) + w_D·D/(1+D)) / (w_A+w_M+w_D)

---

## Transport

| Symbol | Field | Default | Description |
|---|---|---|---|
| g_T | `v2TransportGain` | 0.08 | Global transport scale factor |
| ℓ | `v3DistanceDecay` | 1.0 | Distance decay length scale (exp(-d/ℓ)) |
| r_field | `v3FieldDepthRadius` | 0.0 | Field depth contribution to transport radius |
| k_eff | `v3FieldDepthEffectK` | 0.1 | Half-saturation constant for depth radius effect |
| g_DG | `v3DepthGradientGain` | 0.12 | Depth gradient boost to transport |
| g_DT | `v3DepthTransportGain` | 0.20 | Depth modulation of total transport |
| w_M | `v2MemoryInfluence` | 2.0 | Memory scaling in transport susceptibility |
| k_M | `v2MemoryEffectK` | 0.1 | Memory effect half-saturation constant |

---

## Activation (A)

| Symbol | Field | Default | Description |
|---|---|---|---|
| d_A | `v2ActivationDecay` | 0.45 | Base linear decay (part of dissipation) |
| g_FA | `v2ActivationFastGain` | 0.18 | Fast gain (not used in current main path, present in adaptive relaxation) |
| g_relax | `v2ActivationRelaxationGain` | 0.18 | Relaxation gain for adaptive mode |

Activation hard clamp: v2ClampMax = 4.0 (applies to all four state variables)

---

## Memory (M)

| Symbol | Field | Default | Description |
|---|---|---|---|
| g_M | `memoryFormationGain` | 0.02 | Memory formation gain |
| θ_M | `memoryFormationThreshold` | 0.05 | Activation threshold for memory formation |
| M_max | `memoryMaximum` | 2.0 | Logistic ceiling for memory |
| r_M0 | `memoryDecayRate` | 0.001 | Base memory decay rate |
| γ_DM | `memoryDepthRetentionGain` | 0.8 | Depth-based retention gain (slows decay, boosts formation) |
| g_Mdiff | `memoryDiffusionGain` | 0.002 | Spatial diffusion of memory |
| *(deprecated)* | `v2MemoryGain` | 0.02 | Legacy lab parameter — superseded by memoryFormationGain |
| *(deprecated)* | `v2MemoryDecay` | 0.001 | Legacy lab parameter — superseded by memoryDecayRate |

---

## Field Depth (D)

| Symbol | Field | Default | Description |
|---|---|---|---|
| g_D3 | `v3FieldDepthGain` | 0.005 | Depth growth gain (multiplied by organization and growthFactor) |
| g_DG | `depthGrowthGain` | 0.04 | Additional depth growth gain (added to v3FieldDepthGain) |
| g_DE | `depthErosionGain` | 0.03 | Depth erosion gain |
| r_D | `v3FieldDepthDecay` | 0.0005 | Linear depth decay |
| D_max | `depthMaximum` | 2.0 | Base carrying capacity for depth |
| δ_D | `depthCapacityFromMemory` | 1.0 | Memory-to-depth capacity expansion (dynamicDmax = D_max + δ_D·M/(1+M)) |
| g_Ddiff | `v3FieldDepthDiffusionGain` | 0.0002 | Spatial diffusion of depth |

---

## Baseline (B)

| Symbol | Field | Default | Description |
|---|---|---|---|
| λ_B | `v2BaselineRelaxation` | 0.0005 | Baseline decay |
| r_B | `v2BaselineResting` | 0.02 | Resting recovery rate |

---

## Global storm and corruption thresholds

| Name | Value | Source |
|---|---|---|
| Global storm onset | 0.55 | `TensionManager.STORM_THRESHOLD` |
| Global storm off | 0.40 | `TensionManager.STORM_HYSTERESIS` |
| Corruption tier 1 (Subtle) | 0.20 | `TensionManager.CORRUPTION_THRESHOLDS[0]` |
| Corruption tier 2 (Moderate) | 0.40 | `TensionManager.CORRUPTION_THRESHOLDS[1]` |
| Corruption tier 3 (Severe) | 0.60 | `TensionManager.CORRUPTION_THRESHOLDS[2]` |
| Corruption tier 4 (Catastrophic) | 0.80 | `TensionManager.CORRUPTION_THRESHOLDS[3]` |

---

## Chunk state thresholds

| Name | Value | Source |
|---|---|---|
| T1 (STRAINED) | 0.95 | `ChunkTensionData.T1` |
| T2 (FRACTURED) | 1.28 | `ChunkTensionData.T2` |
| T3 (DECOUPLED) | 3.0 | `ChunkTensionData.T3` |
| STATE_HYSTERESIS | 0.22 | `ChunkTensionData.STATE_HYSTERESIS` |
| Chunk storm onset | 0.95 | `TensionServerTick.updateChunkStateFromMetric()` |
| Chunk storm off | 0.25 | `TensionServerTick.updateChunkStateFromMetric()` |
| Active chunk threshold | 0.15 | `SubstrateState.ACTIVE_TENSION_THRESHOLD` |

---

## Contamination memory thresholds

| Level | T(x) >= |
|---|---|
| 1 (medium) | 1.15 |
| 2 (heavy) | 1.35 |

Decay: level decrements when T(x) < 0.42, staggered over 200-tick slots.

Effective decay multipliers: level 1 → 0.68, level 2 → 0.38.

---

## Coupling matrix (default)

The `CouplingMatrix` (4×4) represents linear cross-variable coupling terms in `FieldDerivative`.
Rows/columns: [A=0, M=1, D=2, B=3].

```
     A       M       D       B
A [ -0.10   0.05   0.04   0.04 ]
M [  0.06  -0.05   0.03   0.02 ]
D [  0.04   0.02  -0.008  0.01 ]
B [  0.05   0.02   0.01  -0.03 ]
```

Diagonal terms are self-decay; off-diagonal terms are cross-variable influence.

Source: `ParameterSet.initializeDefaultCouplingMatrix()`

---

## Tick cadence summary

| Operation | Every N ticks |
|---|---|
| Substrate integration | 5 |
| Region refresh | 20 |
| Client sync | 20 |
| Field summary diagnostics | 20 |
| HUD actionbar update | 40 |
| Region diagnostics | 100 |
| Ecology summary | 200 |
| Region detail diagnostics | 200 |
| TTL pruning | ~6000 (5 min) |
