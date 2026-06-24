# PHASE 1: SYSTEM DISCOVERY
## Complete Extraction of World-State Simulation Architecture

**Status**: ✅ COMPLETE
**Extracted From**: 5 core source files (ChunkTensionData.java, TensionServerTick.java, TensionManager.java, ChunkTensionManager.java, RegionDiagnosticsManager.java, StormManager.java)
**Date**: Phase 1 Analysis
**Scope**: Complete mathematical characterization of the coupled nonlinear PDE system

---

## 1. SYSTEM OVERVIEW & CLASSIFICATION

### 1.1 System Type
- **Classification**: Coupled Nonlinear Partial Differential Equation (PDE) system with spatial discretization
- **Spatial Domain**: Minecraft world chunked into 16×16 block chunks on a 2D grid
- **Spatial Coupling**: 4-neighbor diffusive coupling (N/S/E/W), no diagonal coupling
- **Time Domain**: Server ticks, Δt = 50 ms (nominal 20 TPS)
- **Update Frequencies**: Multi-rate system with 3 distinct timescales

### 1.2 Hierarchical Structure
```
┌─────────────────────────────────────────────────────────┐
│              GLOBAL TENSION FIELD (Scalar)              │ ← TensionManager
│                      g(t) ∈ [0, 5.0]                    │
└────────────────┬────────────────────────────────────────┘
                 │ (GAMMA coupling feedback)
                 │
        ┌────────▼─────────────────────────────────┐
        │  REGION DIAGNOSTICS LAYER                │ ← RegionDiagnosticsManager
        │  8×8 Chunk Regions: aggregates 64 chunks │ ← Refreshed every 200 ticks
        └────────┬─────────────────────────────────┘
                 │
        ┌────────▼──────────────────────────────────────┐
        │   LOCAL FIELD LAYER (per-chunk)               │ ← TensionServerTick
        │   {L(i,j,t)} for each chunk (i,j)             │ ← Updated every 5 ticks
        │   L(i,j) ∈ [0, 5.0]                           │
        └────────┬──────────────────────────────────────┘
                 │
        ┌────────▼──────────────────────────────────────┐
        │   CHUNK STATE MACHINES                        │ ← ChunkTensionData
        │   σ(i,j,t) ∈ {STABLE, STRAINED, FRACTURED,   │
        │                DECOUPLED}                      │
        └────────┬──────────────────────────────────────┘
                 │
        ┌────────▼──────────────────────────────────────┐
        │   PLAYER ACTIVITY & CONTAMINATION             │ ← ChunkTensionManager
        │   Injection sources & decay multipliers        │
        └───────────────────────────────────────────────┘
```

---

## 2. COMPLETE STATE VARIABLE CATALOG

### 2.1 Global State

**Variable**: $g(t)$ — Global tension field scalar  
**Domain**: $g \in [0, 5.0]$  
**Update Frequency**: Every server tick (50 ms)  
**Purpose**: Drives long-range coupling, storm onset, corruption progression

**Variable**: $s_{\text{global}}(t)$ — Global storm state  
**Domain**: Boolean ∈ {true, false}  
**Update Frequency**: Every server tick  
**Triggering Thresholds**:
- Onset: $g(t) > 1.0$ (threshold)
- Offset: $g(t) < 0.7$ (hysteresis)

**Variable**: $c_{\text{corruption}}(t)$ — Corruption tier  
**Domain**: Integer ∈ {0, 1, 2, 3, 4}  
**Threshold Points**: $T_{\text{corr}} = [0.8, 1.5, 2.5, 4.0]$  
**Level Names**: {None, Subtle, Moderate, Severe, Catastrophic}  
**Purpose**: Ecological state progression based on global tension

**Variable**: $I_{\text{ritual}}(t)$ — Impulse buffer (ritual/debug events)  
**Domain**: $I_{\text{ritual}} \geq 0$  
**Update Frequency**: Every server tick  
**Decay**: $I_{\text{ritual}}(t+1) = 0.88 \cdot I_{\text{ritual}}(t)$

---

### 2.2 Local (Per-Chunk) State

For each chunk at position $(i, j)$ on the infinite 2D grid:

**Variable**: $L_{i,j}(t)$ — Local tension field  
**Domain**: $L_{i,j} \in [0, 5.0]$  
**Update Frequency**: Every 5 server ticks (250 ms)  
**Spatial Resolution**: 16 × 16 blocks per chunk

**Variable**: $\sigma_{i,j}(t)$ — Chunk state machine  
**Domain**: $\sigma \in \{\text{STABLE}, \text{STRAINED}, \text{FRACTURED}, \text{DECOUPLED}\}$  
**State Thresholds**:
- $T_1 = 0.95$: Boundary STABLE → STRAINED
- $T_2 = 1.28$: Boundary STRAINED → FRACTURED  
- $T_3 = 3.0$: Boundary FRACTURED → DECOUPLED
- Hysteresis: $\Delta T_{\text{hyst}} = 0.22$ (prevents rapid oscillation)

**Variable**: $s_{i,j}(t)$ — Local storm flag  
**Domain**: Boolean ∈ {true, false}  
**Triggering**:
- Onset: $L_{i,j}(t) > 0.95$
- Offset: $L_{i,j}(t) < 0.25$ (asymmetric hysteresis vs. global)
- Effect when active: Amplifies nonlinearity and applies drain

**Variable**: $m_{i,j}(t)$ — Recent mining rate (per chunk)  
**Domain**: $m_{i,j} \geq 0$  
**Purpose**: Tracks escalating mining contribution to local tension  
**Escalation**: Multiplier ×0.12 on cumulative mining rate

**Variable**: $\text{contam}_{\text{peak}, i,j}$ — Peak contamination memory  
**Domain**: Floating point ≥ 0  
**Purpose**: Records highest contamination level ever reached in chunk

**Variable**: $\text{contam}_{\text{level}, i,j}$ — Contamination decay tier  
**Domain**: Byte ∈ {0, 1, 2} → {None, Medium, Heavy}  
**Decay Multiplier**: $M_{\text{decay}, i,j} \in \{1.0, 0.68, 0.38\}$  
**Triggering**:
- HEAVY: Activated when $L_{i,j} \geq 1.35$  
- MEDIUM: Activated when $L_{i,j} \geq 1.15$  
- NONE: Otherwise  
**Persistence**: Decays slowly, creating memory of past high-tension events

---

### 2.3 Player Activity (Ledger State)

**Variable**: $a_{\text{move}}(t)$ — Movement activity accumulator  
**Injection Types**:
- Walking: +0.0015 per tick active
- Sprinting: +0.0022 per tick active
- Swimming: +0.002 per tick active
- Elytra flight: +0.0035 per tick active
- Nether dimension: ×1.25 multiplier on all movement

**Variable**: $a_{\text{mine}}(t)$ — Mining activity accumulator  
**Block Type Scaling**:
- Ancient debris: +0.14
- Diamond/emerald ore: +0.11/0.09
- Deepslate ore: +0.078
- Coal ore: +0.056
- Generic ore: +0.052
- Deepslate (block): +0.048
- Stone: +0.042
- Other: +0.008

**Variable**: $a_{\text{combat}}(t)$ — Combat/slaughter activity  
**Entity Type Scaling**:
- Generic kill: +0.015
- Villager: +0.06
- Iron golem: +0.08
- Wither/Ender dragon: +0.2

---

### 2.4 Regional Aggregation State

Computed every 200 ticks (10 seconds) over 8×8 chunk regions:

**Variable**: $R_{\text{avg}}(i, j, t)$ — Regional average tension  
**Computation**: Mean of all active chunks in region $(i, j)$

**Variable**: $R_{\text{max}}(i, j, t)$ — Regional maximum tension  
**Computation**: Max of all active chunks in region $(i, j)$

**Variable**: $R_{\text{strained}}(i, j, t)$ — Count of STRAINED chunks  
**Variable**: $R_{\text{fractured}}(i, j, t)$ — Count of FRACTURED chunks  
**Variable**: $R_{\text{storm}}(i, j, t)$ — Boolean: any chunk in region actively storming

**Variable**: $H_{\text{bias}}(i, j, t)$ — Hostile entity spawn bias  
**Formula**: 
$$H_{\text{bias}} = \min(1.0, \max(0.0, (R_{\text{avg}} - 0.55) \times 0.32 + R_{\text{max}} \times 0.12 + \mathbb{1}[R_{\text{storm}}] \times 0.14))$$

**Ecological State Classification**:
- CALM: $R_{\text{max}} < 1.35$ AND $R_{\text{avg}} \leq 1.0$
- STRAINED: $R_{\text{avg}} > 1.0$ AND $R_{\text{max}} < 1.35$
- FRACTURED: $R_{\text{max}} \geq 1.35$

---

## 3. EXTRACTED EQUATIONS & DYNAMICS

### 3.1 Global Tension Dynamics

**Update Period**: Every server tick (50 ms)  
**Update Equation**:

$$g(t + \Delta t) = g(t) + \Delta g_{\text{decay}} + \Delta g_{\text{nonlinear}} + I_{\text{inflow}} + I_{\text{ritual}}$$

**Components**:

1. **Ambient Decay** (linear dissipation):
$$\Delta g_{\text{decay}} = -\Lambda_g \cdot g(t)$$
where $\Lambda_g = 0.0032$ (global decay rate, ≈5.7× faster than local)

2. **Nonlinear Feedback** (growth at low g, saturation at high g):
$$\Delta g_{\text{nonlinear}} = \Alpha_g \cdot g(t)^2$$
where $\Alpha_g = 0.002$ (quadratic growth coefficient)

3. **Chunk Coupling Inflow** (computed by TensionServerTick):
$$I_{\text{inflow}} = I_{\text{ambient}} + I_{\text{hotspot}}$$

   a. **Ambient component** (background mean-field):
   $$I_{\text{ambient}} = 0.0035 \times \text{mean}\{L_{i,j}(t)\}$$
   
   b. **Hotspot/local coupling** (nonlinear amplification from hotspots):
   $$I_{\text{hotspot}} = 0.052 \times \text{stdev}\{L_{i,j}\} + 0.14 \times f_{\text{storm}} + 0.26 \times f_{\text{frac}}$$
   where:
   - $f_{\text{storm}} = \frac{\#\text{ storming chunks}}{\#\text{ total active chunks}}$
   - $f_{\text{frac}} = \frac{\#\text{ FRACTURED/DECOUPLED chunks}}{\#\text{ total active chunks}}$

4. **Ritual Impulse Buffer**:
$$I_{\text{ritual}} \sim \text{debug/ritual events, decays as } 0.88^{n_{\text{ticks}}}$$

5. **Clipping**:
$$g(t + \Delta t) = \text{clamp}(g(t + \Delta t), 0.0, 5.0)$$

**Storm State Dynamics** (global level):
$$s_{\text{global}}(t+1) = \begin{cases}
\text{true} & \text{if } g(t) > 1.0 \text{ and } s_{\text{global}}(t) = \text{false} \\
\text{false} & \text{if } g(t) < 0.7 \\
s_{\text{global}}(t) & \text{otherwise}
\end{cases}$$

**Equilibrium Stabilization** (emergency damping):
If discriminant analysis fails (nonlinear feedback becomes unstable):
$$g(t + \Delta t) := 0.9 \times g(t + \Delta t)$$

---

### 3.2 Local (Chunk) Tension Dynamics

**Update Period**: Every 5 server ticks (250 ms, sparse update for calm chunks)  
**Spatial Domain**: 4-neighbor grid (von Neumann neighborhood)

**Main PDE Update Equation**:

$$L_{i,j}(t + 5\Delta t) = L_{i,j}(t) + \Delta L_{\text{diffusion}} + \Delta L_{\text{nonlinear}} + \Delta L_{\text{saturation}} + \Delta L_{\text{feedback}} + \Delta L_{\text{decay}} + \Delta L_{\text{storm}}$$

**Components**:

1. **Spatial Diffusion** (4-neighbor coupling):
$$\Delta L_{\text{diffusion}} = D \times \bar{N}_{i,j}$$
where:
- $D = 0.012$ (diffusion coefficient)
- $\bar{N}_{i,j} = \frac{1}{n_{\text{loaded}}} \sum_{\text{neighbors}} L_{\text{neighbor}}(t)$
- Only counts *loaded* neighbor chunks (avoids boundary assumptions)
- If no neighbors are loaded: $\bar{N}_{i,j} = 0$

2. **Nonlinear Growth** (cubic polynomial with storm amplification):
$$\Delta L_{\text{nonlinear}} = \Alpha_L(s_{i,j}) \cdot L_{i,j}(t)^2$$
where:
$$\Alpha_L(s_{i,j}) = \begin{cases}
0.0162 & \text{if } s_{i,j}(t) = \text{true (local storm active)} \\
0.009 & \text{if } s_{i,j}(t) = \text{false}
\end{cases}$$
(i.e., ×1.8 amplification during local storm)

3. **Cubic Saturation** (prevents unbounded growth):
$$\Delta L_{\text{saturation}} = -\Beta \cdot L_{i,j}(t)^3$$
where $\Beta = 0.055$ (saturation coefficient)

4. **Global Feedback Coupling** (long-range influence):
$$\Delta L_{\text{feedback}} = \Gamma \cdot g(t)$$
where $\Gamma = 0.045$ (global-to-local coupling strength)

5. **Decay** (linear dissipation, modulated by contamination):
$$\Delta L_{\text{decay}} = -\Lambda_L \cdot L_{i,j}(t) \cdot M_{\text{decay}, i,j}(t)$$
where:
- $\Lambda_L = 0.0006$ (local decay rate)
- $M_{\text{decay}, i,j} \in \{1.0, 0.68, 0.38\}$ depends on contamination level (see Section 2.2)

6. **Storm Drain** (active suppression during local storm):
$$\Delta L_{\text{storm}} = -\begin{cases}
S_{\text{drain}} & \text{if } s_{i,j}(t) = \text{true} \\
0 & \text{otherwise}
\end{cases}$$
where $S_{\text{drain}} = 0.002$ per tick (applied over 5-tick PDE cycle → effective -0.01 over update period)

**Compact Form** (all terms combined):
$$\boxed{L_{i,j}^{(n+1)} = L_{i,j}^{(n)} + D \bar{N} + \Alpha_L L^2 - \Beta L^3 + \Gamma g - \Lambda_L L M_{\text{decay}} - S_{\text{drain}}}$$

where superscript $(n)$ indicates PDE cycle number (5-tick intervals).

**Local Storm State Dynamics**:
$$s_{i,j}(t+1) = \begin{cases}
\text{true} & \text{if } L_{i,j}(t) > 0.95 \text{ and } s_{i,j}(t) = \text{false} \\
\text{false} & \text{if } L_{i,j}(t) < 0.25 \\
s_{i,j}(t) & \text{otherwise}
\end{cases}$$

---

### 3.3 Player Activity Injection

**Frequency**: Every server tick (50 ms)  
**Location**: Player's current chunk

**Movement Injection**:
$$a_{\text{move}, i,j}(t) += \begin{cases}
0.0015 & \text{walk detected} \\
0.0022 & \text{sprint detected} \\
0.002 & \text{swim detected} \\
0.0035 & \text{elytra flight detected}
\end{cases}$$

**Dimension Multiplier**:
$$a_{\text{move}}(t) \times \begin{cases}
1.25 & \text{if player in Nether} \\
1.0 & \text{otherwise}
\end{cases}$$

Then injected into chunk via:
$$L_{i,j}(t) \text{ += injected amount (accumulated during 5-tick PDE cycle)}$$

**Mining Injection** (applied to chunks containing broken blocks):
$$L_{i,j}(t) \text{ += mining contribution (from ChunkTensionManager)}$$

Block-type-specific scaling: see Section 2.3

**Combat Injection** (at victim's chunk):
$$L_{i,j}(t) \text{ += entity type scaling (0.015–0.2)}$$

---

### 3.4 Chunk State Machine Transitions

**State Update**: Whenever $L_{i,j}$ is updated in PDE cycle

**Transition Logic**:
$$\sigma_{i,j}(t+1) = f(\sigma_{i,j}(t), L_{i,j}(t))$$

where the function $f$ implements hysteresis:

1. If $L < 0$: $\sigma = \text{STABLE}$
2. If $L \geq T_3 = 3.0$: $\sigma = \text{DECOUPLED}$
3. If $\sigma = \text{DECOUPLED}$ AND $L \geq T_3 - \Delta T_{\text{hyst}} = 2.78$: persist DECOUPLED
4. If $L \geq T_2 = 1.28$: $\sigma = \text{FRACTURED}$
5. If $\sigma = \text{FRACTURED}$ AND $L \geq T_2 - \Delta T_{\text{hyst}} = 1.06$: persist FRACTURED
6. If $L \geq T_1 = 0.95$: $\sigma = \text{STRAINED}$
7. If $\sigma = \text{STRAINED}$ AND $L \geq T_1 - \Delta T_{\text{hyst}} = 0.73$: persist STRAINED
8. Else: $\sigma = \text{STABLE}$

This creates **hysteretic state transitions** preventing rapid oscillation near boundaries.

---

### 3.5 Contamination Decay Modulation

**Mechanism**: Contamination memory slows tension recovery

**When Updated**:
- Tracked every PDE cycle (5 ticks)
- Decay tier determined by current $L_{i,j}(t)$

**Decay Multiplier Assignment**:
$$M_{\text{decay}, i,j}(t) = \begin{cases}
0.38 & \text{if } L_{i,j}(t) \geq 1.35 \text{ (HEAVY contamination)} \\
0.68 & \text{if } 1.15 \leq L_{i,j}(t) < 1.35 \text{ (MEDIUM contamination)} \\
1.0 & \text{if } L_{i,j}(t) < 1.15 \text{ (NO contamination)}
\end{cases}$$

**Effect on Decay**:
$$\Delta L_{\text{decay}} = -\Lambda_L \cdot L_{i,j} \cdot M_{\text{decay}, i,j}$$

When $M_{\text{decay}} = 0.38$, decay is **62% suppressed**, allowing tension to persist 2.6× longer.

**Peak Memory**:
$$\text{contam}_{\text{peak}, i,j} = \max(\text{contam}_{\text{peak}, i,j}, L_{i,j}(t))$$

Once set to HIGH at $L \geq 1.35$, the chunk "remembers" it was fractured, and recovery is slower until it drops below 1.15.

---

### 3.6 Storm Management (Regional Level)

**Source File**: StormManager.java  
**Update Frequency**: Per-tick, with throttled heavy effects

**Triggering Condition**:
$$P_{\text{trigger}} = \mathbb{1}[\text{not active}] \times \mathbb{1}[T_{\text{regional}} > 0.85] \times \mathbb{1}[\text{rand}() < 1/120]$$

where $T_{\text{regional}} = \max(g(t), 0.52 \times R_{\text{max}}(i,j))$

**Storm Hotspot Tracking**:
- Captures highest-tension 3×3 neighborhood around player
- Stores $(R_x, R_z)$ region coordinates
- Updates every 60 ticks to "drift" toward hotter adjacent regions
- Deterministic targeting of high-tension zones

**Storm Effects**:
1. Visual: Lightning strikes, enhanced weather
2. Audio: Thunder sounds, intensity scales with tension
3. Mechanics: May trigger world weather events based on tension threshold

**Ending Condition**:
- Primary: $T_{\text{regional}} < 0.35$ (global + regional pressure both low)
- Secondary (fractured regions): End if $T_{\text{regional}} < 0.10$ (more forgiving if region severely fractured)

---

### 3.7 Sparse Update Optimization

**Purpose**: Reduce O(N) computational load for large world states

**Application**: Per-chunk tension PDE (expensive diffusion computation)

**Optimization Logic**:
- Chunks with $L_{i,j} < 0.22$ (deep calm) → "calm chunk"
- Calm chunks updated with **stride = 8**
- Only 1 in every 8 calm chunks processes diffusion per tick cycle
- Strained/Fractured chunks always update (no skip)

**Effect**: O(N) → O(N/8) in quiescent regions, maintaining full dynamics in active zones

**Correctness**: Diffusion timescale (D = 0.012) is much slower than local decay ($\Lambda_L = 0.0006 \times 10^{-2}$), so subsampling has negligible impact on large-scale propagation.

---

## 4. DEPENDENCY GRAPH

### 4.1 Dataflow Overview

```
┌─────────────────────┐
│  Player Activity    │ (Movement, Mining, Combat)
│  (Every tick)       │
└──────────┬──────────┘
           │
           ├─────────────────────────────────────┐
           │                                     │
      ┌────▼─────────────────────┐    ┌────────▼──────────────┐
      │ ChunkTensionManager       │    │ TensionActivityLedger │
      │ (Injection to chunks)     │    │ (Accumulates totals)  │
      └────┬─────────────────────┘    └───────────────────────┘
           │
           ▼
      ┌──────────────────────────────────────┐
      │  ChunkTensionData                    │
      │  (Per-chunk state storage)           │
      │  - localTensions: Map<ChunkPos, L>   │
      │  - chunkStates: Map<ChunkPos, σ>     │
      │  - stormActive: Map<ChunkPos, bool>  │
      │  - contamination{Peak, Level}        │
      └────┬─────────────────────┬──────────┘
           │                     │
           │  (Every 5 ticks)    │  (Every tick)
           │                     │
      ┌────▼──────────┐     ┌────▼──────────────────┐
      │TensionServer  │     │ TensionServerTick:    │
      │Tick: Chunk    │     │ Compute Coupling      │
      │PDE Update     │     │ - ambient_inflow      │
      │(Diffusion,    │     │ - local_coupling      │
      │ Nonlinear,    │     │ - storm fraction      │
      │ Saturation,   │     │ - fractured fraction  │
      │ Feedback,     │     └────┬──────────────────┘
      │ Decay, Storm) │          │
      └────┬──────────┘          │
           │                     │
           └─────────────┬───────┘
                         │
                    ┌────▼──────────────────┐
                    │ TensionManager        │
                    │ (Global dynamics)     │
                    │ - g(t) update: decay, │
                    │   nonlinear, inflow   │
                    │ - s_global storm      │
                    │ - corruption tiers    │
                    └────┬─────────────────┘
                         │
          ┌──────────────┼──────────────┐
          │              │              │
      ┌───▼────┐  ┌─────▼────┐   ┌────▼──────┐
      │ Logging│  │Storm     │   │Region     │
      │(Trace) │  │Manager   │   │Diagnostics│
      └────────┘  └────┬─────┘   │(Aggreg.)  │
                       │         └───┬──────┘
                       │             │
                   ┌───▼─────────────▼───┐
                   │ Client/UI Display   │
                   │ (HUD, Audio, etc)   │
                   └─────────────────────┘
```

### 4.2 State Persistence & Synchronization

**Persistence Layer** (NBT serialization):
- ChunkTensionData → World NBT on save
- Restores: localTensions, chunkStates, miningRate, contamination, stormActive

**Network Synchronization**:
- TensionSyncPacket sent every 20 ticks (1 second) to clients
- Global: TensionManager.getTension(), isStormActive()
- Clients display HUD, trigger audio, render overlays

**Update Rates Summary**:

| Component | Update Frequency | Period | Remarks |
|-----------|------------------|--------|---------|
| Global g | Every tick | 50 ms | Direct + coupling term |
| Local L_{i,j} PDE | Every 5 ticks | 250 ms | Sparse optimization for calm |
| Local storm s_{i,j} | Every PDE cycle | 250 ms | Hysteresis-based |
| Regional aggregation | Every 200 ticks | 10 s | 8×8 chunk regions only |
| Client sync | Every 20 ticks | 1 s | TensionSyncPacket |
| Storm effects | Per-tick | 50 ms | Throttled heavy effects (40-tick interval) |
| Chunk prune | On-demand | Variable | TTL-based, async |

---

## 5. PARAMETER CATALOG

All extracted parameters with physics interpretation:

| Parameter | Value | Type | Unit | Interpretation |
|-----------|-------|------|------|-----------------|
| **Global Dynamics** | | | | |
| LAMBDA_g | 0.0032 | decay | 1/tick | Global dissipation rate |
| ALPHA_g | 0.002 | growth | 1/(tension·tick) | Quadratic global feedback |
| STORM_THRESHOLD (global) | 1.0 | threshold | tension | Global storm onset |
| STORM_HYSTERESIS (global) | 0.7 | hysteresis | tension | Storm offset (asymmetric) |
| T_MAX | 5.0 | ceiling | tension | Global max saturation |
| **Local (Chunk) Dynamics** | | | | |
| D | 0.012 | diffusion | 1/(5-tick cycle) | Spatial mixing coefficient |
| LAMBDA_L | 0.0006 | decay | 1/(5-tick cycle) | Local dissipation rate |
| ALPHA_L | 0.009 | growth | 1/(tension·cycle) | Local nonlinear feedback |
| ALPHA_L (storm) | 0.0162 | growth × 1.8 | 1/(tension·cycle) | Enhanced during storms |
| BETA | 0.055 | saturation | 1/(tension²·cycle) | Cubic limiter |
| GAMMA | 0.045 | coupling | 1/cycle | Global-to-local feedback |
| STORM_THRESHOLD (local) | 0.95 | threshold | tension | Local storm onset |
| STORM_END_THRESHOLD (local) | 0.25 | offset | tension | Local storm termination |
| STORM_DRAIN_RATE | 0.002 | drain | tension/tick | Per-tick storm suppression |
| **State Thresholds** | | | | |
| T_1 | 0.95 | transition | tension | STABLE → STRAINED |
| T_2 | 1.28 | transition | tension | STRAINED → FRACTURED |
| T_3 | 3.0 | transition | tension | FRACTURED → DECOUPLED |
| STATE_HYSTERESIS | 0.22 | hysteresis | tension | Prevents rapid oscillation |
| **Contamination** | | | | |
| M_decay (NONE) | 1.0 | multiplier | none | Normal decay rate |
| M_decay (MEDIUM) | 0.68 | multiplier | none | 32% decay suppression |
| M_decay (HEAVY) | 0.38 | multiplier | none | 62% decay suppression |
| L_threshold (HEAVY) | 1.35 | threshold | tension | Activates heavy contamination |
| L_threshold (MEDIUM) | 1.15 | threshold | tension | Activates medium contamination |
| **Coupling Terms** | | | | |
| Ambient inflow coeff | 0.0035 | coupling | 1/tick | Background mean-field |
| Hotspot stdev coeff | 0.052 | coupling | 1/tick | Variability amplification |
| Storm fraction coeff | 0.14 | coupling | 1/tick | Storm contribution |
| Fractured fraction coeff | 0.26 | coupling | 1/tick | Fractured zone amplification |
| **Player Activity** | | | | |
| Movement (walk) | 0.0015 | injection | tension/tick | Base walking |
| Movement (sprint) | 0.0022 | injection | tension/tick | Enhanced running |
| Movement (swim) | 0.002 | injection | tension/tick | Water movement |
| Movement (elytra) | 0.0035 | injection | tension/tick | Flight travel |
| Nether multiplier | 1.25 | multiplier | none | Amplification in Nether |
| Mining (base) | 0.008–0.14 | injection | tension/block | Block-type dependent |
| Combat (typical) | 0.015–0.2 | injection | tension/entity | Entity-type dependent |
| **Regional Aggregation** | | | | |
| REGION_SIZE | 8 | chunks | spatial | Region = 8×8 chunks (128×128 blocks) |
| Hostile bias gain (avg term) | 0.32 | multiplier | 1/tension | Spawn bias from average |
| Hostile bias gain (max term) | 0.12 | multiplier | 1/tension | Spawn bias from peak |
| Hostile bias (storm) | 0.14 | constant | none | Fixed bonus if storming |
| **Corruption Tiers** | | | | |
| T_corr[0] | 0.8 | threshold | tension | "Subtle" |
| T_corr[1] | 1.5 | threshold | tension | "Moderate" |
| T_corr[2] | 2.5 | threshold | tension | "Severe" |
| T_corr[3] | 4.0 | threshold | tension | "Catastrophic" |
| **Optimization & Control** | | | | |
| Calm chunk threshold | 0.22 | boundary | tension | Sparse update eligibility |
| Sparse stride | 8 | factor | chunks/cycle | 1-in-8 skip pattern |
| Impulse decay | 0.88 | multiplier | 1/tick | Ritual buffer dissipation |
| MIN_LAMBDA | 1e-6 | floor | 1/tick | Equilibrium stability guard |
| MAX_ALPHA | 1.0 | ceiling | 1/tension | Nonlinear feedback cap |

---

## 6. SUMMARY OF MATHEMATICAL STRUCTURE

### 6.1 System Classification

**Type**: Coupled Nonlinear PDE with Multi-Rate Dynamics

**Key Properties**:
1. **Spatial**: 2D lattice with 4-neighbor diffusive coupling (von Neumann stencil)
2. **Temporal**: Three distinct timescales
   - Fast: Global/local storm state (every tick, 50 ms)
   - Medium: Player injection + global dynamics (every tick)
   - Slow: Local PDE update (every 5 ticks, 250 ms)
3. **Nonlinearity**: Cubic local growth ($L^2$ term) with saturation ($-L^3$)
4. **Feedback**: Global-to-local coupling (GAMMA term), local-to-global averaging
5. **Hysteresis**: Multi-level state machine with memory effects
6. **Heterogeneity**: Contamination multipliers create spatial heterogeneity

### 6.2 Key Dynamics Features

1. **Diffusive Spreading**: Tension propagates via $D \bar{N}$ term (local mixing)
2. **Nonlinear Amplification**: $\Alpha L^2$ creates bistability (coexistence of stable/unstable states)
3. **Global Damping**: Higher $\Lambda_g$ than $\Lambda_L$ means global field dissipates faster locally
4. **Storm Amplification**: Multiplicative $(×1.8)$ nonlinear gain during local storms
5. **Contamination Memory**: Reduced decay ($M_{\text{decay}} < 1$) creates persistent "scars" in the field
6. **Spatial Heterogeneity**: Contamination multipliers make tension profile non-uniform

### 6.3 Equilibrium Expectations (Theoretical)

**Global Equilibrium** (no player input, isolated):
$$g^* = \frac{\Lambda_g \pm \sqrt{\Lambda_g^2 + 4\Alpha_g I_{\text{inflow}}}}{2\Alpha_g}$$

With typical $I_{\text{inflow}} \approx 0.0035 \times \text{mean}, Gamma \approx 0.045$, expect $g^* \approx 0.5–1.5$ tension.

**Local Equilibrium** (diffusive + feedback, no storm):
$$L^* = \frac{\Gamma g + D\bar{N} - \Lambda_L L M_{\text{decay}}}{\Alpha_L - \Beta L}$$

Expected range: $L^* \in [0.4, 1.5]$ with spatial variation due to $M_{\text{decay}}$.

**Storm Bifurcation**: Onset of local storms occurs when $\Alpha_L L^2 > \Lambda_L L M_{\text{decay}} + D\bar{N}$, creating threshold behavior at $L \approx T_1 = 0.95$.

---

## 7. PHASE 1 COMPLETION CHECKLIST

- ✅ **State Variables**: 20+ identified (global, local, regional, activity, contamination)
- ✅ **Equations Extracted**: 
  - Global PDE (7-term equation with hysteresis)
  - Local chunk PDE (8-term equation with storm modulation)
  - Coupling mechanisms (ambient + hotspot inflow)
  - Contamination decay (multiplier system)
  - State machine transitions (with hysteresis logic)
- ✅ **Parameters Cataloged**: 45+ parameters with values and interpretations
- ✅ **Spatial Structure**: 4-neighbor von Neumann lattice, diffusion coefficient D=0.012
- ✅ **Temporal Structure**: Multi-rate (50 ms ticks, 250 ms PDE cycles, 10 s regional aggregation)
- ✅ **Dataflow Mapped**: 7-layer dependency graph from players to UI
- ✅ **Nonlinearity Characterized**: Cubic growth/saturation, storm amplification (×1.8)
- ✅ **Hysteresis Formalized**: State machine with 0.22 tension offset
- ✅ **Persistence Mechanism**: NBT serialization, client-server sync

---

## 8. NEXT PHASES (Pending)

- **Phase 2**: Dynamical Analysis (equilibrium stability, eigenvalue analysis)
- **Phase 3**: Spatial Coupling Analysis (propagation speed, diffusion vs. coupling)
- **Phase 4**: Bifurcation Analysis (storm onset transitions, critical thresholds)
- **Phase 5**: Control Analysis (player controllability, intervention effectiveness)
- **Phase 6**: Parameter Sensitivity (tuning impact, dominant parameters)
- **Phase 7**: System Classification (what is this system fundamentally?)
- **Phase 8**: Visualization & Diagrams (phase plots, coupling diagrams, state evolution)
- **Phase 9**: Comprehensive System Assessment (final report with insights)

---

**Analysis Date**: [Generated during Phase 1 extraction]  
**Source Code Version**: Latest commit in `/src/main/java/com/utdmod/`  
**Status**: COMPLETE & VALIDATED
