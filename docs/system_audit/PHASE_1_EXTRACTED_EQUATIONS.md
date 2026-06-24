# PHASE 1: EXTRACTED EQUATIONS
## Complete Mathematical Formulation

---

## LEVEL 1: PER-CHUNK LOCAL DYNAMICS

### Equation 1.1: Local Chunk Tension Update (Primary PDE)

**Source**: `TensionServerTick.java` → `ChunkTensionData.tick()`

**Mathematical Form**:

```
T(i,j,t+1) = T(i,j,t) × (1 - λ_decay)
           + M(i,j,t)                          [mining inflow]
           + C(i,j,t)                          [combat inflow]
           + D(i,j,t)                          [diffusion from neighbors]
           + R(i,j,t)                          [ritual effects]
           + S_storm(i,j,t)                    [storm amplification]
```

**Parameter Dictionary**:

| Symbol | Value | Meaning | Source |
|--------|-------|---------|--------|
| λ_decay | 0.00075 | Exponential decay rate per tick | ChunkTensionData.DECAY_RATE |
| M(i,j,t) | [0, 0.25] | Mining-induced tension at chunk (i,j) at tick t | ChunkTensionManager (event-driven) |
| C(i,j,t) | [0, 0.15] | Combat-induced tension at chunk (i,j) at tick t | ChunkTensionManager (event-driven) |
| D(i,j,t) | ℝ | Net diffusion inflow from 4 neighbors | Computed (see Eq. 1.2) |
| R(i,j,t) | ℝ | Ritual effect (usually 0 unless active) | RitualSystem (transient) |
| S_storm(i,j,t) | 0 or ~T(i,j,t)×0.5 | Storm amplification (when GLOBAL_STORM=true) | GlobalTensionManager flag |

**Component Analysis**:

**Decay Component**:
- First-order exponential decay
- Half-life: τ = ln(2) / ln(1 / (1-λ)) ≈ 920 ticks ≈ 46 seconds
- **Nature**: Linear damping term

**Inflow Components** (Mining & Combat):
- Event-driven (discrete impulses, not continuous)
- Occur when player mines block or mob dies
- **Nature**: Stochastic excitation (Poisson process in typical gameplay)

**Diffusion Component** (see detailed derivation below):
- Smooths spatial gradients between neighboring chunks
- **Nature**: Linear coupling to neighbor states

**Storm Amplification Component**:
- Only active when GLOBAL_STORM flag = TRUE
- Approximately doubles effective tension growth during storm periods
- **Nature**: Multiplicative nonlinearity via external flag

---

### Equation 1.2: Diffusion/Coupling Inflow (4-Neighbor Laplacian)

**Source**: `ChunkTensionData.tick()`

**Mathematical Form**:

```
D(i,j,t) = α_diff × [ (T(i+1,j,t) - T(i,j,t)) 
                     + (T(i-1,j,t) - T(i,j,t))
                     + (T(i,j+1,t) - T(i,j,t))
                     + (T(i,j-1,t) - T(i,j,t)) ]

         = α_diff × [ ∇²T(i,j,t) ]           [discrete Laplacian]
```

**Parameter Dictionary**:

| Symbol | Value | Meaning |
|--------|-------|---------|
| α_diff | 0.045 | Diffusion coupling coefficient per neighbor |
| ∇²T | — | Discrete Laplacian operator (von Neumann stencil) |

**Interpretation**:
- This is a **discretized heat equation** / **diffusion operator**
- Reduces spatial gradients: high-tension chunks spread to low-tension neighbors
- Each chunk "sees" 4 orthogonal neighbors
- Coupling strength α_diff = 0.045 per neighbor means:
  - If one neighbor is Δ higher than current, inflow = 0.045 × Δ
  - Maximum inflow from one neighbor: 0.045 × (T_neighbor - T_current)
  - **Total diffusion per tick limited by all 4 neighbors**

**Stability Characteristic**:
- The discrete Laplacian with positive diffusion coefficient creates a **smoothing filter**
- Suppresses high-frequency spatial oscillations
- Drives system toward spatial uniformity (locally)

---

### Equation 1.3: Scar Memory Dynamics

**Source**: `ChunkTensionData.tick()`

**Mathematical Form**:

```
S(i,j,t+1) = max( S(i,j,t) × β_scar, T(i,j,t) / k_scar_ratio )

where:
β_scar = 0.98
k_scar_ratio = 10
```

**Behavior**:
- Scar decays exponentially at rate β_scar = 0.98 per tick
- BUT: Scar floor is set to T(i,j,t) / 10
- If current tension rises, scar floor rises with it
- **Effect**: Scar never goes below 10% of current tension
- **Nature**: Hysteretic memory with floor

**Half-life of Scar** (if tension remains constant):
```
τ_scar = ln(2) / ln(1/0.98) ≈ 34 ticks ≈ 1.7 seconds
```

**Interpretation**:
- Scar represents "environmental scarring" or "memory of disturbance"
- Decays quickly (~1.7 sec) if no further disturbance
- BUT persists as long as tension remains high
- **This is a ratcheting mechanism**: tension can rise easily, scar falls slowly

---

## LEVEL 2: REGIONAL AGGREGATION DYNAMICS

### Equation 2.1: Regional State Computation

**Source**: `RegionDiagnosticsManager.updateRegionStates()`

**Mathematical Form**:

```
region.tension(t) = max{ T(i,j,t) : (i,j) ∈ Region }

region.avgTension(t) = (1/64) × Σ{ T(i,j,t) : (i,j) ∈ Region }

region.ecoState(t) = f_state( region.tension(t) )

where f_state is a thresholding function:

f_state(τ) = { CALM        if τ < 0.40
             { STRAINED    if 0.40 ≤ τ < 1.00
             { FRACTURED   if 1.00 ≤ τ < 1.80
             { CRITICAL    if τ ≥ 1.80
```

**Interpretation**:
- Regional tension = **maximum** of all chunk tensions in region
  - This means one "hot spot" can define the whole region
  - **Non-averaging aggregation**: asymmetric to high values
  
- Regional average = **arithmetic mean** of chunk tensions
  - Smooth aggregation
  - Used for diagnostics but NOT for main dynamics

- Regional state = **discrete classification** based on tension level
  - 4-level classification system
  - Each level has distinct gameplay effects (spawning, etc.)

**Implication**: 
- Regional state is driven by the **worst chunk** in that region
- This couples regions together implicitly (high chunks → high region → high global)

---

### Equation 2.2: Regional State Effect on Chunk Dynamics (Feedback)

**Source**: `TensionSpawnEcology.java`

**Mathematical Form**:

```
IF region.ecoState = STRAINED:
   hostile_spawn_multiplier = 1.40 (day) or 1.55 (night)
   passive_spawn_multiplier = 0.65
   
IF region.ecoState = FRACTURED:
   hostile_spawn_multiplier = 2.20 (day) or 2.45 (night)
   passive_spawn_multiplier = 0.30
   
IF region.ecoState = CRITICAL:
   hostile_spawn_multiplier = 2.45+ (very high)
   passive_spawn_multiplier ≈ 0.0 (effectively suppressed)
```

**Interpretation**:
- This creates a **feedback loop** from regional aggregation back to chunk dynamics
- High tension → aggressive spawning → more combat events → more tension inflow
- This can create **positive feedback** (runaway) or **limit cycle** behavior

---

## LEVEL 3: GLOBAL DYNAMICS

### Equation 3.1: Global Tension Update

**Source**: `GlobalTensionManager.tick()`

**Mathematical Form**:

```
G(t+1) = G(t) × (1 - λ_global)
       + κ × Σ_r { region.tension(t) }
       + ε_storm(t)
```

where:

```
κ = 0.001                                [regional inflow coefficient]

ε_storm(t) = { 0              if GLOBAL_STORM = FALSE
             { decay_penalty  if GLOBAL_STORM = TRUE
```

**Parameter Dictionary**:

| Symbol | Value | Meaning |
|--------|-------|---------|
| λ_global | 0.0002 | Global exponential decay rate per tick |
| κ | 0.001 | Regional inflow gain (weak coupling) |
| Σ_r | — | Sum over all NUM_REGIONS active regions |

**Decay Interpretation**:
```
Half-life: τ_global = ln(2) / ln(1/(1-0.0002)) ≈ 3460 ticks ≈ 173 seconds
```

**Inflow Interpretation**:
- Each region contributes linearly to global growth
- With NUM_REGIONS = 100, average contribution ~0.1 to global tension per tick
- This is **very weak coupling** upward (regions barely affect global)
- **Implication**: Global dynamics are largely autonomous from regional

---

### Equation 3.2: Storm Triggering (Hysteretic Switch)

**Source**: `GlobalTensionManager.evaluateStorm()`

**Mathematical Form**:

```
GLOBAL_STORM(t+1) = { TRUE   if G(t) ≥ 1.50   [activation threshold]
                    { FALSE  if G(t) < 0.80   [deactivation threshold]
                    { HOLD   if 0.80 ≤ G(t) < 1.50  [hysteresis zone]

where HOLD means: GLOBAL_STORM(t+1) = GLOBAL_STORM(t)  [no change]
```

**Interpretation**:
- **Hysteretic switching** with **dead zone** between 0.80 and 1.50
- Activation requires crossing 1.50 threshold
- Deactivation requires dropping below 0.80 threshold
- **Between these thresholds**: state is "sticky" (doesn't flip)
- **This creates bistability** in the vicinity of thresholds
- **Effect**: Storm states persist even after tension drops below threshold

**Control-Theoretic Interpretation**:
- This is a **Schmitt trigger** or **comparator with hysteresis**
- Prevents chattering/oscillation near threshold
- Allows for sustained storm states despite decay

---

### Equation 3.3: Storm Amplification Effect

**Source**: `GlobalTensionManager.amplifyRegionsTension()` and regional coupling

**Mathematical Form**:

```
IF GLOBAL_STORM = TRUE:
   ε_storm(t) = decay_penalty_boost ≈ +0.00015  [additional decay penalty]
   PLUS: Each region's effective contribution multiplied ~1.5×

EFFECTIVE_REGION_EFFECT(t) = { region.tension(t)     if GLOBAL_STORM = FALSE
                              { 1.5 × region.tension(t) if GLOBAL_STORM = TRUE
```

**Interpretation**:
- Storm **doubles** the strength of regional feedback (×1.5)
- Storm **also penalizes decay**, making tension harder to shed
- **Combination**: Storm creates sustained high-tension regime
- **Stabilization**: Storm state makes it harder to drop below deactivation threshold

---

## LEVEL 4: CLIENT-SIDE RENDERING STATE (Smoothing)

### Equation 4.1: Client-Side Exponential Smoothing

**Source**: `TensionSyncState.tickSmoothing()`

**Mathematical Form**:

```
G_smooth(t+1) = G_smooth(t) + α_global × (G_raw(t) - G_smooth(t))

L_smooth(t+1) = L_smooth(t) + α_local × (L_raw(t) - L_smooth(t))

PERCEIVED_TENSION(t) = max( G_smooth(t), 0.92 × L_smooth(t) )
```

where:

```
α_global ≈ 0.07   [global smoothing alpha]
α_local ≈ 0.11    [local smoothing alpha]
0.92              [local emphasis factor]
```

**Interpretation**:
- **Exponential moving average** (EMA) filter on both global and local tensions
- Client receives raw synced value every 20 ticks
- Between syncs: client maintains smoothed estimate
- **Perceived tension** = **max** of smoothed global and weighted local
  - Emphasizes local perturbations (×0.92 damping on local means local is usually dominant)
  - Global acts as a "floor"

**Time Constant**:
```
τ = -1 / ln(1 - α)

τ_global = -1 / ln(0.93) ≈ 13 ticks ≈ 0.65 seconds
τ_local = -1 / ln(0.89) ≈ 8 ticks ≈ 0.4 seconds
```

**Effect**: 
- Smooths out discrete sync packets into continuous perceived flow
- Reduces perceptual jitter
- Makes world feel more continuous (gameplay UX optimization)

---

## LEVEL 5: NETWORK SYNCHRONIZATION

### Equation 5.1: Sync Packet Schedule

**Source**: `TensionServerTick.java` line 93

**Mathematical Form**:

```
IF world.getTime() % 20 == 0:
   BROADCAST TensionSyncPacket to all clients

PACKET_PAYLOAD = { 
  globalTension: G(t)
  stormFlag: GLOBAL_STORM(t)
  localTension: T_local_chunk(t)
  regionAverage: region.avgTension(t)
  regionMax: region.maxTension(t)
}

BROADCAST_FREQUENCY = 1 packet per 20 ticks = 1 second (60 Hz world, 1 Hz sync)
```

**Interpretation**:
- Coarse-grained synchronization at 1 Hz (low bandwidth)
- Client interpolates between packets using smoothing
- Each packet includes multiple tension values for diagnostics
- **Latency**: Up to 20 tick (1 second) delay before client sees server state update

---

## SUMMARY: COMPLETE SYSTEM EQUATIONS

### **System State Vector**:

```
X(t) = { G(t),                                  [global tension]
         [T(i,j,t)]_{(i,j)∈World},             [per-chunk tensions]
         [S(i,j,t)]_{(i,j)∈World},             [per-chunk scars]
         GLOBAL_STORM(t),                       [storm flag]
         [region.ecoState(t)]_{regions}        [regional states]
       }
```

### **Complete Nonlinear Coupled System**:

```
T(i,j,t+1) = T(i,j,t) × (1 - 0.00075)
           + M(i,j,t) 
           + C(i,j,t)
           + 0.045 × ∑_{neighbors} [T_neighbor(t) - T(i,j,t)]
           + R(i,j,t)
           + [+0.5 × T(i,j,t)] × 𝟙_{GLOBAL_STORM}  [storm boost]

S(i,j,t+1) = max( 0.98 × S(i,j,t), T(i,j,t)/10 )

G(t+1) = G(t) × (1 - 0.0002) 
       + 0.001 × Σ_regions max{T(i,j,t) in region}
       + [decay_penalty_term] × 𝟙_{GLOBAL_STORM}

GLOBAL_STORM(t+1) ∈ { TRUE if G(t) ≥ 1.50
                     { FALSE if G(t) < 0.80
                     { GLOBAL_STORM(t) otherwise

region.ecoState(t) = f_discrete(max{T(i,j,t)})

[Regional ecology effects feed back to spawn rates and thus future C(i,j,t)]
```

### **Key Observations**:

1. **Nonlinearity Sources**:
   - **MAX aggregation**: region.tension = max(chunks)
   - **Hysteretic switch**: Storm state exhibits bistability
   - **Multiplicative coupling**: Storm effect ×1.5 or ×0.5

2. **Feedback Loops**:
   - **Positive**: High chunk tension → high region state → high spawn rates → combat inflow → higher chunk tension
   - **Negative**: Decay and diffusion smooth and dampen tension growth
   - **Coupling**: Global state indirectly affects local through storm amplification

3. **Spatial Structure**:
   - Local 4-neighbor diffusion (discrete Laplacian)
   - Regional max aggregation (creates hot-spot dominance)
   - Global linear inflow from regions (weak upward coupling)

4. **Timescale Separation**:
   - **Fast (1-2 sec)**: Local diffusion and decay
   - **Medium (46 sec)**: Local chunk equilibration
   - **Slow (173 sec)**: Global tension dynamics
   - **Very slow (ritual effects)**: Weeks to months if persisted

---

## DIMENSIONLESS ANALYSIS

To understand scaling, define dimensionless time:

```
τ = t × λ_local = t × 0.00075

Then the local equation becomes:

dT/dτ ≈ - T + (M + C) / λ + 0.045/0.00075 × ∇²T + S_storm

This highlights that diffusion coefficient (60×) dominates decay
→ Spatial smoothing is MUCH stronger than temporal decay
```

---

## NEXT PHASE: DYNAMICAL ANALYSIS

With these equations extracted, Phase 2 will:
- Find equilibrium/fixed-point solutions
- Analyze stability via perturbation analysis
- Identify attractors and bifurcations
- Classify system dynamics (stable, oscillatory, chaotic, etc.)

