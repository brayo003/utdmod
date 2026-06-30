# DEPENDENCY GRAPH & INFORMATION FLOW
## Complete Map of Tension System Architecture

**Purpose**: Visualize how state variables propagate through the system  
**Format**: Dataflow diagrams, dependency tables, and timing analysis  
**Scope**: All 7 layers from players to UI

---

## 1. SYSTEM LAYERS

The tension system is organized in **7 functional layers**:

```
Layer 0: INPUT       → Player Actions (movement, mining, combat)
Layer 1: INJECTION   → Activity Ledgers & Managers
Layer 2: LOCAL       → Per-Chunk Tension Field & State Machines
Layer 3: AGGREGATION → Regional Summarization
Layer 4: GLOBAL      → World-Level Tension & Storms
Layer 5: OUTPUT      → Diagnostics & Logging
Layer 6: RENDERING   → Client-side HUD, Audio, Visuals
```

---

## 2. DETAILED DATAFLOW

### 2.1 Layer 0: Player Input

**Sources**:
- `PlayerEntity.vel` → Movement detection
- `BlockBreakEvent` → Mining detection  
- `LivingEntity.death` → Combat detection

**Data Points**:
- Player position (chunk coordinates)
- Movement type (walk, sprint, swim, elytra)
- Block ID (mined block)
- Entity type (killed entity)
- Current dimension (Overworld vs. Nether)

**Frequency**: Per-tick + event-driven

---

### 2.2 Layer 1: Injection & Ledgers

**Component 1A**: ChunkTensionManager (API)
- **Input**: Player activity from Layer 0
- **Output**: Injected tension values
- **Functions**:
  - `addLocalTension(world, chunk, amount)` → Direct injection to chunk
  - `addMiningTension(world, chunk, blockId)` → Block-type-scaled injection
  - `addSlaughterTension(world, chunk, entityType)` → Entity-type-scaled injection

**Component 1B**: TensionActivityLedger
- **Input**: All player activities
- **Output**: Accumulated totals for logging
- **State Variables**:
  - `movement` (total cumulative)
  - `mining` (total cumulative)
  - `combat` (total cumulative)
- **Purpose**: Diagnostics & trend analysis (not direct tension dynamics)

**Component 1C**: Dimension Multiplier
- **Rule**: Apply ×1.25 to movement if player in Nether
- **Implementation**: In ChunkTensionManager.addLocalTension()

**Data After Layer 1**:
- Per-chunk injection amount ΔL (tension/tick)
- Accumulated activity metrics

---

### 2.3 Layer 2: Local Field & State Machines

**Component 2A**: ChunkTensionData (Storage)
- **Input**: Injection from Layer 1
- **State Variables**:
  - `localTensions: Map<ChunkPos, Double>` ← $L_{i,j}(t)$
  - `chunkStates: Map<ChunkPos, ChunkState>` ← $\sigma_{i,j}(t)$
  - `stormActive: Map<ChunkPos, Boolean>` ← $s_{i,j}(t)$
  - `recentMiningRate: Map<ChunkPos, Double>` ← $m_{i,j}(t)$
  - `contaminationPeak, contaminationLevel` ← Memory maps
  - `lastAccessMs: Map<ChunkPos, Long>` ← TTL tracking
- **Persistence**: NBT serialization to world file
- **API**: getLocalTension, setLocalTension, getChunkState, etc.

**Component 2B**: TensionServerTick (PDE Solver)
- **Frequency**: Every 5 ticks (250 ms per cycle)
- **Input from Layer 2**:
  - $L_{i,j}^{(n)}$ from ChunkTensionData
  - $g^{(n)}$ from global field
  - Neighbor chunk tensions
- **Computation**:
  - Diffusion term: $D \bar{N}_{i,j}$
  - Nonlinear term: $\Alpha_L(s) L^2$
  - Saturation: $-\Beta L^3$
  - Feedback: $\Gamma g$
  - Decay: $-\Lambda_L L M_{\text{decay}}$
  - Storm drain (if $s_{i,j}$)
  - **Result**: $L_{i,j}^{(n+1)}$
- **Output**: Updated $L_{i,j}$ written back to ChunkTensionData
- **Side Effects**:
  - Computes coupling inflow to global field
  - Detects state transitions
  - Updates contamination tier

**Component 2C**: Chunk State Machine
- **Input**: Updated $L_{i,j}$ from TensionServerTick
- **Logic**: Hysteretic transition (see PHASE_1_SYSTEM_DISCOVERY.md Section 4)
- **Output**: $\sigma_{i,j} \in \{\text{STABLE, STRAINED, FRACTURED, DECOUPLED}\}$

**Component 2D**: Contamination Decay Modulation
- **Input**: Current $L_{i,j}$
- **Rule**: Set $M_{\text{decay}}$ based on thresholds (1.35, 1.15)
- **Memory**: Peak recorded in `contaminationPeak`
- **Effect**: Modulates future decay rates (slower recovery after high tension)

**Data After Layer 2**:
- Updated $L_{i,j}$ field
- New state machine values $\sigma_{i,j}$
- Storm flags $s_{i,j}$
- Coupling inflow metric $I_{\text{inflow}}$

---

### 2.4 Layer 3: Regional Aggregation

**Component**: RegionDiagnosticsManager
- **Frequency**: Every 200 ticks (10 seconds)
- **Spatial Scale**: 8×8 chunk regions (128×128 blocks)
- **Input from Layer 2**:
  - All $L_{i,j}$ from ChunkTensionData
  - All $\sigma_{i,j}$ states
  - Storm flags
- **Computation** (per region):
  - $R_{\text{avg}} = \text{mean}(L_{i,j})$ over region
  - $R_{\text{max}} = \max(L_{i,j})$ over region
  - $R_{\text{strained}} = \#\{\sigma_{i,j} = \text{STRAINED}\}$
  - $R_{\text{fractured}} = \#\{\sigma_{i,j} \in \{\text{FRACTURED, DECOUPLED}\}\}$
  - $R_{\text{storm}} = \text{any}(s_{i,j})$
  - $H_{\text{bias}} = 0.32(R_{\text{avg}} - 0.55) + 0.12 R_{\text{max}} + 0.14 \mathbb{1}[R_{\text{storm}}]$
- **Ecological Classification**:
  - CALM: $R_{\text{max}} < 1.35$ AND $R_{\text{avg}} \leq 1.0$
  - STRAINED: $R_{\text{avg}} > 1.0$ AND $R_{\text{max}} < 1.35$
  - FRACTURED: $R_{\text{max}} \geq 1.35$
- **Cache**: CACHE map updated every 200 ticks
- **Output**: RegionSnapshot objects for logging + hostile spawn bias calculation

**Data After Layer 3**:
- Regional aggregates (8×8 chunk summaries)
- Ecological state classifications
- Spawn bias metrics

---

### 2.5 Layer 4: Global Field Dynamics

**Component 4A**: TensionServerTick (Coupling Computation)
- **Frequency**: Every tick (50 ms)
- **Input from Layer 2**:
  - Mean $\mu_L = \text{mean}(L_{i,j})$
  - Stdev $\sigma_L = \text{stdev}(L_{i,j})$
  - Storm fraction $f_{\text{storm}}$
  - Fractured fraction $f_{\text{frac}}$
- **Computation**:
  - $I_{\text{ambient}} = 0.0035 \mu_L$
  - $I_{\text{hotspot}} = 0.052 \sigma_L + 0.14 f_{\text{storm}} + 0.26 f_{\text{frac}}$
  - $I_{\text{inflow}} = I_{\text{ambient}} + I_{\text{hotspot}}$
- **Output**: $I_{\text{inflow}}$ passed to TensionManager

**Component 4B**: TensionManager (Global PDE)
- **Frequency**: Every tick (50 ms)
- **Input**:
  - Previous global tension $g^{(n)}$
  - Coupling inflow $I_{\text{inflow}}$ from Component 4A
  - Ritual impulse buffer $I_{\text{ritual}}$
- **Computation**:
  - Decay: $\Delta g_{\text{decay}} = -\Lambda_g g$
  - Nonlinearity: $\Delta g_{\text{nonlin}} = \Alpha_g g^2$
  - Total update: $\Delta g = \Delta g_{\text{decay}} + \Delta g_{\text{nonlin}} + I_{\text{inflow}} + I_{\text{ritual}}$
  - Clamp: $g^{(n+1)} = \text{clamp}(g + \Delta g, 0, 5.0)$
- **Side Effect**: Update corruption tier based on $g$
- **Output**: Updated $g^{(n+1)}$

**Component 4C**: Global Storm State Machine
- **Input**: Updated $g^{(n+1)}$
- **Logic**: 
  - Onset if $g > 1.0$
  - Offset if $g < 0.7$
- **Output**: $s_{\text{global}} \in \{\text{true, false}\}$

**Data After Layer 4**:
- Updated global field $g(t)$
- Global storm flag
- Corruption tier
- Feedback term $\Gamma g$ (sent back to Layer 2)

---

### 2.6 Layer 5: Diagnostics & Logging

**Component 5A**: TensionTraceLogger
- **Input**: System events (state transitions, storms, equilibrium damps, etc.)
- **Frequency**: Event-driven + periodic (every 20 ticks)
- **Output**: Trace log file with timestamps

**Component 5B**: DiagnosticLogs
- **Input**: 
  - Per-20-tick global flow summary
  - Regional snapshots (every 200 ticks)
  - Storm start/end events
- **Output**: Diagnostic CSV/JSON files for analysis

**Component 5C**: TensionActivityLedger (Activity Tracking)
- **Input**: Accumulated movement, mining, combat metrics
- **Output**: Included in global flow logs

**Data After Layer 5**:
- Log files with complete trace history
- Performance metrics
- Event journals

---

### 2.7 Layer 6: Client-Side Rendering & Audio

**Component 6A**: Packet Network
- **Input**: TensionSyncPacket (every 20 ticks from server)
  - Global tension $g$
  - Global storm active flag
  - Corruption tier
- **Frequency**: Every 20 ticks (1 second) sync interval
- **Protocol**: Server → Client unicast to all players

**Component 6B**: Client-Side HUD (TensionHud.java + enhancements)
- **Input**: Synced global/local tension from packet
- **Rendering**:
  - Color progression: 🟢 (STABLE) → 🟡 (STRAINED) → 🟠 (FRACTURED) → 🔴 (DECOUPLED)
  - Numerical tension display
  - Storm indicator
  - Corruption level text
- **Frequency**: Every frame (client-side)

**Component 6C**: Audio Coupling (TensionAudioCoupling.java)
- **Input**: Local chunk tension + global field
- **Audio Effects**:
  - Ambient sound intensity scales with local tension
  - Pitch/frequency modulation
  - Spatialization (tension gradient affects audio direction)
- **Frequency**: Per-tick client-side

**Component 6D**: Debug Overlay (TensionDebugOverlay.java)
- **Input**: Raw tension metrics (toggle F8)
- **Display**: Detailed per-chunk stats, coupling terms, state info
- **Frequency**: Every frame (when enabled)

**Component 6E**: StormManager (Client Storm Effects)
- **Input**: 
  - Global + regional tension
  - Region coordinates
  - Hot region tracking
- **Visual Effects**:
  - Lightning strikes (intensity ∝ tension)
  - Weather intensity
  - Thunder sounds
- **Frequency**: Per-tick throttled

**Data After Layer 6**:
- Visual HUD on screen
- Audio cues to player
- Particle effects
- Console/debug output

---

## 3. DEPENDENCY TABLE

**Who depends on what?**

| Component | Depends On | Type | Frequency |
|-----------|-----------|------|-----------|
| **Layer 1** | | | |
| ChunkTensionManager | Player actions | Event | Per-action |
| TensionActivityLedger | Player actions | Event | Per-action |
| **Layer 2** | | | |
| ChunkTensionData | ChunkTensionManager | Direct | Per-injection |
| TensionServerTick (PDE) | ChunkTensionData, TensionManager | Direct | 5 ticks |
| State Machine | TensionServerTick output | Direct | 5 ticks |
| Contamination Decay | TensionServerTick output | Direct | 5 ticks |
| **Layer 3** | | | |
| RegionDiagnosticsManager | ChunkTensionData (full map) | Query | 200 ticks |
| **Layer 4** | | | |
| TensionServerTick (Coupling) | ChunkTensionData (stats) | Query | Every tick |
| TensionManager | TensionServerTick output | Direct | Every tick |
| Global Storm State | TensionManager output | Direct | Every tick |
| **Layer 5** | | | |
| TensionTraceLogger | All system components | Event-driven | Various |
| DiagnosticLogs | Regional, Global, Activity | Aggregation | Every 20–200 ticks |
| **Layer 6** | | | |
| TensionSyncPacket | TensionManager | Serialize | Every 20 ticks |
| TensionHud | TensionSyncPacket | Direct | Frame-rate |
| TensionAudioCoupling | ChunkTensionData, TensionManager | Direct | Frame-rate |
| TensionDebugOverlay | ChunkTensionData, TensionManager | Direct | Frame-rate |
| StormManager | TensionManager, RegionDiagnosticsManager | Direct | Every tick |

---

## 4. FEEDBACK LOOPS

### 4.1 Local-to-Global Loop

```
L_{i,j} (high) 
    ↓
f_storm ↑, f_frac ↑
    ↓
I_inflow ↑
    ↓
g ↑ (global)
    ↓
Γg term ↑ in PDE
    ↓
L_{i,j} increases further  [POSITIVE FEEDBACK]
```

**Effect**: Creates regions of mutual amplification where chunk + global tension reinforce.

### 4.2 Nonlinear Threshold Loop

```
L_{i,j} → 0.95 (crosses T_1)
    ↓
s_{i,j} = true (local storm)
    ↓
ALPHA_L × 1.8
    ↓
L_{i,j} growth accelerates
    ↓
Maintained until L < 0.25  [HYSTERETIC SWITCH]
```

**Effect**: Once a chunk storms, it maintains storm state even if global field drops, until local decay brings L below 0.25.

### 4.3 Contamination Memory Loop

```
L_{i,j} > 1.35
    ↓
M_decay = 0.38 (62% suppression)
    ↓
Recovery rate = LAMBDA_L × 0.38
    ↓
Slow recovery, tension persists
    ↓
If L drops < 1.15 → M_decay = 1.0  [GRADUAL RELEASE]
```

**Effect**: High-tension events leave a "scar" that slows subsequent recovery.

### 4.4 Global-Local-Regional Loop

```
Many chunks strained (σ_ij = STRAINED)
    ↓
f_frac ↑
    ↓
I_hotspot ↑ (0.26 × f_frac)
    ↓
g ↑
    ↓
Γg term affects all chunks
    ↓
Tendency toward global escalation
    ↓
Breaks only when regional relaxation > inflow  [EQUILIBRIUM COMPETITION]
```

---

## 5. TIMING & SYNCHRONIZATION

### 5.1 Multi-Rate Update Schedule

```
Tick 0 (50 ms)    │ Movement inject
                  │ TensionServerTick (coupling calc)
                  │ TensionManager (global PDE)
                  │ ...
                  │
Tick 1 (50 ms)    │ Mining inject
                  │ TensionServerTick (coupling calc)
                  │ TensionManager (global PDE)
                  │ ...
                  │
Tick 2 (50 ms)    │ ...
├─ Tick 4 (50 ms) │ ...
└─ Tick 5 (250 ms)│ CHUNK PDE UPDATE (L_ij = L_ij + ΔL)
                  │ State machine update
                  │ Contamination tier update
                  │
Tick 20 (1 s)     │ NETWORK SYNC
                  │ Client packets
                  │ Diagnostics log
                  │
Tick 200 (10 s)   │ REGIONAL AGGREGATION
                  │ Cache refresh (RegionDiagnosticsManager)
                  │ Regional diagnostics log
```

### 5.2 State Variable Update Frequencies

| State Variable | Update Frequency | Period | Source |
|---|---|---|---|
| $L_{i,j}$ (local tension) | Every 5 ticks | 250 ms | TensionServerTick PDE |
| $s_{i,j}$ (local storm) | Every 5 ticks | 250 ms | State machine |
| $\sigma_{i,j}$ (chunk state) | Every 5 ticks | 250 ms | State machine |
| $M_{\text{decay}, i,j}$ | Every 5 ticks | 250 ms | Contamination logic |
| Activity injection | Every tick | 50 ms | Player actions |
| $I_{\text{inflow}}$ (coupling) | Every tick | 50 ms | TensionServerTick coupling |
| $g$ (global tension) | Every tick | 50 ms | TensionManager |
| $s_{\text{global}}$ (global storm) | Every tick | 50 ms | Global state machine |
| $c$ (corruption tier) | Every tick | 50 ms | TensionManager |
| $R_{\text{avg}}, R_{\text{max}}, ...$ (regional) | Every 200 ticks | 10 s | RegionDiagnosticsManager |
| Client HUD | Every frame | ~16–50 ms | Renderer |
| Audio | Every frame | ~16–50 ms | Audio engine |
| Trace log | Per-event | Variable | TensionTraceLogger |
| Diagnostic log | Every 20 ticks | 1 s | DiagnosticLogs |

---

## 6. PERSISTENCE & RECOVERY

### 6.1 Serialization Points

**On World Save**:
1. ChunkTensionData → World NBT compound
   - `localTensions` map
   - `chunkStates` map
   - `recentMiningRate` map
   - `stormActive` map
   - `contaminationPeak, contaminationLevel`

**On World Load**:
1. NBT → ChunkTensionData (via fromNbt)
2. Game ticks begin, coupling dynamics restart

### 6.2 Recovery Guarantees

- ✅ Local tension field fully restored
- ✅ State machines restored to previous state
- ✅ Storm flags preserved
- ✅ Contamination memory preserved (slower recovery)
- ✅ Global tension: **NOT PERSISTED** (resets to 0.0 on load)

**Note**: Global tension is recomputed from local field statistics via coupling, so world reload can cause transient stability changes.

---

## 7. SUMMARY: INFORMATION FLOW MAP

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         LAYER 0: PLAYER INPUT                                   │
│  Movement | Mining | Combat | Dimension | Position                             │
└────────────────┬────────────────────────────────────────┬──────────────────────┘
                 │ Per-action                             │
                 │ (Event-driven)                        │
         ┌───────▼────────────────────────┐     ┌────────▼──────────────────┐
         │ LAYER 1: INJECTION & LEDGERS   │     │ LAYER 1: DIMENSION        │
         │ ChunkTensionManager            │     │ Multiplier (×1.25 Nether) │
         │ + Activity Ledger              │     └────────┬──────────────────┘
         └───────┬────────────────────────┘              │
                 │ Per-tick + Per-action                │
                 │ Injection amount ΔL                  │
         ┌───────▼──────────────────────────────────────▼──────────────────┐
         │ LAYER 2: LOCAL FIELD                                            │
         │ ChunkTensionData → L_ij, σ_ij, s_ij, M_decay                   │
         │                                                                   │
         │ ┌─────────────────────────────────────────────────────────────┐ │
         │ │ TensionServerTick PDE (every 5 ticks):                     │ │
         │ │ L = D·N_avg + α·L² - β·L³ + γ·g - λ·L·M + storm_drain    │ │
         │ │ + State Machine (hysteresis)                              │ │
         │ │ + Contamination Decay Modulation                          │ │
         │ └────────────┬──────────────────────────────┬────────────────┘ │
         │              │ Per-5-tick cycle              │                  │
         │              │ (Coupled to global)           │ Computes I_inflow│
         │              │ Result: L_ij(n+1)             │                  │
         └──────────────┼───────────────────────────────┼──────────────────┘
                        │                               │
                 ┌──────▼─────────────┐         ┌───────▼────────────────┐
                 │ LAYER 3:           │         │ LAYER 4: GLOBAL FIELD  │
                 │ REGIONAL AGGREG.   │         │ TensionManager         │
                 │ (every 200 ticks)  │         │ g = Δg_decay +         │
                 │ R_avg, R_max,      │         │     Δg_nonlin +        │
                 │ R_storm, H_bias    │         │     I_inflow + I_rit   │
                 │ EcoState classify  │         │ + Global storm state   │
                 │                    │         │ + Corruption tier      │
                 └──────┬─────────────┘         └──────┬─────────────────┘
                        │                              │
                 ┌──────▼──────────────────────────────▼──────────────┐
                 │ LAYER 5: DIAGNOSTICS & LOGGING                    │
                 │ TensionTraceLogger (events)                       │
                 │ DiagnosticLogs (periodic summaries)               │
                 │ TensionActivityLedger (trend data)                │
                 └──────┬───────────────────────────────────────────┘
                        │ Per-event + periodic
                        │ Log files
                 ┌──────▼──────────────────────────────────────┐
                 │ LAYER 6: CLIENT RENDERING                  │
                 │ TensionSyncPacket (every 20 ticks)         │
                 │  ├─ TensionHud (visual display)            │
                 │  ├─ TensionAudioCoupling (audio effects)   │
                 │  ├─ TensionDebugOverlay (F8 display)       │
                 │  └─ StormManager (visual storms)           │
                 └───────────────────────────────────────────┘
```

---

**Status**: ✅ PHASE 1 COMPLETE  
**Next**: Phase 2 (Dynamical Analysis, Equilibrium Investigation)
