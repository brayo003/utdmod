# PHASE 1: SYSTEM STATE MAP
## Complete Inventory of State Variables

---

## 1. GLOBAL STATE VARIABLES

### 1.1 Global Tension (`GLOBAL_TENSION`)
- **Source File**: `src/main/java/com/utdmod/core/GlobalTensionManager.java`
- **Type**: Persistent floating-point scalar
- **Range**: [0.0, ∞) (observed: 0.0 to ~2.5)
- **Update Frequency**: Every server tick (20 ticks = 1 second)
- **Update Location**: `GlobalTensionManager.tick()`
- **Dependencies**: 
  - Inflow from local chunk tensions (aggregated)
  - Self-decay with coefficient ~0.0002 per tick
  - Storm amplification (multiplicative, factor ~1.5 during storms)
- **Persistence**: NBT serialized to disk (key: `"global_tension"`)
- **Formula**: 
  ```
  G(t+1) = G(t) × (1 - decay) + inflow_from_regions + storm_boost
  ```

### 1.2 Global Storm Flag (`GLOBAL_STORM`)
- **Source File**: `src/main/java/com/utdmod/core/GlobalTensionManager.java`
- **Type**: Boolean flag
- **Update Frequency**: Evaluated every tick
- **Update Location**: `GlobalTensionManager.evaluateStorm()`
- **Threshold Condition**: 
  - Activated when `GLOBAL_TENSION > STORM_THRESHOLD` (threshold ≈ 1.5)
  - Deactivated when `GLOBAL_TENSION < STORM_DECAY_THRESHOLD` (threshold ≈ 0.8)
  - **Hysteresis present**: Activation ≠ Deactivation threshold
- **Effect When Active**: Multiplies regional tensions by ~1.5, amplifies decay
- **Persistence**: NBT serialized

### 1.3 Total Region Count (`NUM_REGIONS`)
- **Source File**: `src/main/java/com/utdmod/core/RegionDiagnosticsManager.java`
- **Type**: Integer counter
- **Value**: Dynamic (grows with player exploration)
- **Update Frequency**: Increments when new region created
- **Initial Value**: 0

### 1.4 Global Session Time (`WORLD_TIME`)
- **Source File**: Minecraft's `ServerWorld.getTime()`
- **Type**: Long integer (ticks)
- **Update Frequency**: Every tick
- **Range**: [0, ∞) (wraps at very large values)

---

## 2. REGIONAL STATE VARIABLES

### 2.1 Per-Region Tension (`region.tension`)
- **Source File**: `src/main/java/com/utdmod/core/RegionDiagnosticsManager.RegionSnapshot.java`
- **Type**: Persistent floating-point scalar
- **Range**: [0.0, ∞) (observed: 0.0 to ~2.0 per region)
- **Update Frequency**: Every server tick (computed from chunks)
- **Computation**: 
  ```
  region.tension = max(chunk.tension for chunk in region)
  ```
- **Update Location**: `RegionDiagnosticsManager.updateRegionStates()`
- **Dependencies**: All chunks in region
- **Persistence**: NBT serialized per region

### 2.2 Regional State Enum (`region.ecoState`)
- **Source File**: `src/main/java/com/utdmod/core/RegionDiagnosticsManager.EcoState.java`
- **Type**: Enumeration (CALM, STRAINED, FRACTURED, CRITICAL)
- **Thresholds**:
  - CALM: tension < 0.40
  - STRAINED: 0.40 ≤ tension < 1.00
  - FRACTURED: 1.00 ≤ tension < 1.80
  - CRITICAL: tension ≥ 1.80
- **Update Frequency**: Every tick
- **Update Location**: `RegionDiagnosticsManager.updateRegionStates()`
- **Persistence**: Derived from tension (not independently stored)

### 2.3 Regional Average Tension (`region.avgTension`)
- **Source File**: `src/main/java/com/utdmod/core/RegionDiagnosticsManager.RegionSnapshot.java`
- **Type**: Floating-point scalar
- **Computation**:
  ```
  region.avgTension = sum(chunk.tension) / NUM_CHUNKS_IN_REGION
  ```
- **Update Frequency**: Every tick
- **Dependencies**: All chunks in region

### 2.4 Regional Max Tension (`region.maxTension`)
- **Source File**: `src/main/java/com/utdmod/core/RegionDiagnosticsManager.RegionSnapshot.java`
- **Type**: Floating-point scalar
- **Computation**:
  ```
  region.maxTension = max(chunk.tension for chunk in region)
  ```
- **Update Frequency**: Every tick

### 2.5 Region Coordinates (`region.regionX`, `region.regionZ`)
- **Type**: Integer pair
- **Meaning**: Region identifier in chunk-space
- **Relationship to Chunks**: Region = 8×8 array of chunks
  ```
  chunkX = regionX × 8 + localChunkX  (0 ≤ localChunkX < 8)
  chunkZ = regionZ × 8 + localChunkZ  (0 ≤ localChunkZ < 8)
  ```

---

## 3. PER-CHUNK STATE VARIABLES

### 3.1 Local Chunk Tension (`ChunkTensionData.tension`)
- **Source File**: `src/main/java/com/utdmod/tension/ChunkTensionData.java`
- **Type**: Persistent floating-point scalar
- **Range**: [0.0, ∞) (capped at ~3.0 in practice)
- **Update Frequency**: Every server tick (20 ticks per second)
- **Update Location**: `ChunkTensionData.tick()` called from `TensionServerTick.onWorldTickEnd()`
- **Update Equation**:
  ```
  T(i,j,t+1) = T(i,j,t) 
              + mining_inflow(i,j,t)
              + combat_inflow(i,j,t)
              - decay × T(i,j,t)
              + diffusion_from_neighbors
              + ritualEffect
  ```
- **Persistence**: NBT serialized to chunk

### 3.2 Chunk Scar Value (`ChunkTensionData.scar`)
- **Source File**: `src/main/java/com/utdmod/tension/ChunkTensionData.java`
- **Type**: Persistent floating-point scalar
- **Range**: [0.0, 1.0]
- **Meaning**: Memory of past disturbance; decays slowly
- **Update Location**: `ChunkTensionData.tick()`
- **Update Equation**:
  ```
  S(i,j,t+1) = max(S(i,j,t) × 0.98, T(i,j,t) / 10)
  ```
  (Scar decays 2% per tick OR maintains minimum fraction of current tension)

### 3.3 Chunk State (`ChunkTensionData.state`)
- **Source File**: `src/main/java/com/utdmod/tension/ChunkTensionData.java`
- **Type**: Enumeration (same as EcoState: CALM, STRAINED, FRACTURED, CRITICAL)
- **Thresholds**: Same as regional (see Section 2.2)
- **Update Frequency**: Every tick
- **Update Location**: `ChunkTensionData.tick()`
- **Purpose**: Local ecological state affecting spawning behavior

### 3.4 Chunk Coordinates (`ChunkPos`)
- **Type**: Integer pair (chunkX, chunkZ)
- **Minecraft Semantics**: 
  - Each chunk is 16×16 blocks horizontally
  - Chunks tile the world in a 2D grid

### 3.5 Chunk Coordinates in Region
- **Relation**:
  ```
  localChunkX = chunkX mod 8
  localChunkZ = chunkZ mod 8
  regionX = floor(chunkX / 8)
  regionZ = floor(chunkZ / 8)
  ```

---

## 4. MINING/COMBAT INFLOW STATE

### 4.1 Mining Injection Rate (`miningAmount`)
- **Source File**: `src/main/java/com/utdmod/tension/ChunkTensionManager.java`
- **Type**: Floating-point value
- **Computation**: Called from `ServerBlockEvent.BreakEvent` listener
- **Formula** (per block mined):
  ```
  miningAmount = BASE_AMOUNT_FOR_BLOCK_TYPE
  
  Examples:
  - Stone: 0.042
  - Deepslate: 0.051
  - Copper ore: 0.126
  - Diamond ore: 0.252
  - Obsidian: 0.105
  - Grass block: 0.015
  ```
- **Frequency**: Event-driven (when block broken)
- **Application**: Added directly to chunk tension `T(i,j,t) += miningAmount`
- **Persistence**: Transient (affects current tick only)

### 4.2 Combat Injection Rate (`combatAmount`)
- **Source File**: `src/main/java/com/utdmod/tension/ChunkTensionManager.java`
- **Type**: Floating-point value
- **Computation**: Called from entity death listener
- **Formula** (per entity killed):
  ```
  combatAmount = BASE_AMOUNT + ENTITY_TYPE_MODIFIER
  
  Examples:
  - Zombie: 0.025
  - Creeper: 0.032
  - Witch: 0.038
  - Dragon: 0.150
  ```
- **Frequency**: Event-driven (when mob dies)
- **Application**: Added to chunk where death occurred
- **Persistence**: Transient

---

## 5. DECAY AND RECOVERY STATE

### 5.1 Decay Coefficient (`DECAY_RATE`)
- **Source File**: `src/main/java/com/utdmod/tension/ChunkTensionData.java`
- **Type**: Constant floating-point
- **Value**: 0.00075 per tick
- **Update Frequency**: Applied every tick
- **Formula Application**:
  ```
  T(i,j,t+1) = T(i,j,t) × (1 - 0.00075)
  ```
  This gives half-life of approximately 920 ticks ≈ 46 seconds

### 5.2 Scar Decay Coefficient
- **Source File**: `src/main/java/com/utdmod/tension/ChunkTensionData.java`
- **Type**: Constant floating-point
- **Value**: 0.98 per tick (exponential decay)
- **Half-life**: ~280 ticks ≈ 14 seconds

### 5.3 Global Decay (`GLOBAL_DECAY_RATE`)
- **Source File**: `src/main/java/com/utdmod/core/GlobalTensionManager.java`
- **Type**: Constant floating-point
- **Value**: 0.0002 per tick
- **Half-life**: ~3460 ticks ≈ 173 seconds

---

## 6. DIFFUSION / COUPLING STATE

### 6.1 Neighbor Coupling Coefficient (`DIFFUSION_FACTOR`)
- **Source File**: `src/main/java/com/utdmod/tension/ChunkTensionData.java`
- **Type**: Constant floating-point
- **Value**: 0.045
- **Application**: Per-tick diffusion from each of 4 neighbors
- **Formula**:
  ```
  diffusion_in = DIFFUSION_FACTOR × (T_neighbor - T_self) / 4
  (applied for each of 4 orthogonal neighbors)
  ```
- **Mechanism**: Smoothing operator that reduces spatial gradients

### 6.2 Neighbor Set (4-connectivity)
- **Neighbors of chunk (i,j)**:
  ```
  {(i+1,j), (i-1,j), (i,j+1), (i,j-1)}
  ```
- **Boundary Handling**: Chunks at world boundary have fewer neighbors

### 6.3 Region Inflow Coefficient (`REGIONAL_INFLOW_FACTOR`)
- **Source File**: `src/main/java/com/utdmod/core/GlobalTensionManager.java`
- **Type**: Constant floating-point
- **Value**: 0.001
- **Application**: Aggregates regional tensions into global
- **Formula**:
  ```
  global_inflow = sum over all regions of (REGIONAL_INFLOW_FACTOR × region.tension)
  ```

---

## 7. STORM STATE VARIABLES

### 7.1 Storm Threshold (`STORM_THRESHOLD`)
- **Source File**: `src/main/java/com/utdmod/core/GlobalTensionManager.java`
- **Type**: Constant floating-point
- **Value**: 1.50

### 7.2 Storm Decay Threshold (`STORM_DECAY_THRESHOLD`)
- **Source File**: `src/main/java/com/utdmod/core/GlobalTensionManager.java`
- **Type**: Constant floating-point
- **Value**: 0.80

### 7.3 Storm Amplification Factor (`STORM_AMPLIFY`)
- **Source File**: `src/main/java/com/utdmod/core/GlobalTensionManager.java`
- **Type**: Constant floating-point
- **Value**: 1.5
- **Application**: Multiplicative boost to regional tensions when storm active
- **Formula**:
  ```
  if (GLOBAL_STORM) {
    region.effectiveTension = region.tension × 1.5
  }
  ```

---

## 8. RITUAL/PLAYER INTERVENTION STATE

### 8.1 Ritual Reset (`reset()`)
- **Source File**: `src/main/java/com/utdmod/tension/ChunkTensionData.java`
- **Type**: Method/action (not a stored variable)
- **Effect**: Sets local chunk tension to 0.0
- **Scope**: Per-chunk
- **Persistence**: Change persisted to NBT immediately

### 8.2 Global Stabilizer (`GlobalTensionManager.stabilize()`)
- **Source File**: `src/main/java/com/utdmod/core/GlobalTensionManager.java`
- **Type**: Method/action
- **Effect**: Forcefully resets global tension to 0.0
- **Scope**: Global (all regions affected through recomputation)
- **Persistence**: Change persisted to NBT

---

## 9. CLIENT-SIDE SYNC STATE

### 9.1 Client Perceived Tension (`TensionSyncState.CLIENT_TENSION`)
- **Source File**: `src/main/java/com/utdmod/client/TensionSyncState.java`
- **Type**: Volatile floating-point scalar
- **Range**: [0.0, ∞)
- **Synchronization**: Updated every 20 ticks from network packet
- **Update Mechanism**: `TensionSyncPacket.handle()` → `TensionSyncState.applySnapshot()`
- **Client-Side Smoothing**: Exponential averaging with alpha ≈ 0.07

### 9.2 Client Local Tension (`TensionSyncState.CLIENT_LOCAL_TENSION`)
- **Source File**: `src/main/java/com/utdmod/client/TensionSyncState.java`
- **Type**: Volatile floating-point scalar
- **Synchronization**: Updated every 20 ticks from network packet
- **Client-Side Smoothing**: Exponential averaging with alpha ≈ 0.11

### 9.3 Smoothing State Variables
- **`smoothedGlobalTension`**: Running exponential average of global tension
- **`smoothedLocalTension`**: Running exponential average of local tension
- **Update Frequency**: Client-side every tick (applied when packet received)
- **Formula**:
  ```
  smoothedValue(t+1) = smoothedValue(t) + alpha × (newValue - smoothedValue(t))
  ```

---

## 10. TELEMETRY / DIAGNOSTIC STATE

### 10.1 Event Counter (`audioTriggers`)
- **Source File**: `src/main/java/com/utdmod/client/ClientFeelingCounters.java`
- **Type**: Integer counter
- **Frequency**: Increments with audio events
- **Purpose**: Diagnostics only (not affecting simulation)

### 10.2 Regional Diagnostics Snapshots
- **Source File**: `src/main/java/com/utdmod/core/RegionDiagnosticsManager.RegionSnapshot.java`
- **Stored**: Per-region diagnostic data (time, tensions, states)
- **Update Frequency**: Every tick
- **Purpose**: Performance monitoring and analysis

---

## 11. RITUAL CAPABILITY STATE (Advanced)

### 11.1 Ritual Effect Transient (`ritualEffect`)
- **Source File**: `src/main/java/com/utdmod/ritual/RitualSystem.java`
- **Type**: Floating-point value
- **Frequency**: Transient (applied once when ritual triggered)
- **Formula**: Depends on ritual type:
  ```
  - Stabilization Ritual: additive -0.5 to local chunk
  - Amplification Ritual: additive +0.3 to local chunk
  - Purification Ritual: multiplicative ×0.7 to local chunk
  ```
- **Duration**: Applies for duration_ticks (typically 1-100 ticks depending on ritual)
- **Persistence**: NOT persisted (transient effect per play session)

---

## SUMMARY TABLE: STATE VARIABLE INVENTORY

| Variable | Type | Update Freq | Source | Persistent | Range |
|----------|------|-------------|--------|------------|-------|
| GLOBAL_TENSION | Float | 1 tick | GlobalTensionManager | Yes | [0, ∞) |
| GLOBAL_STORM | Boolean | 1 tick | GlobalTensionManager | Yes | {T,F} |
| region.tension | Float | 1 tick | RegionDiagnosticsManager | Yes | [0, ∞) |
| region.avgTension | Float | 1 tick | RegionDiagnosticsManager | Derived | [0, ∞) |
| chunk.tension | Float | 1 tick | ChunkTensionData | Yes | [0, ∞) |
| chunk.scar | Float | 1 tick | ChunkTensionData | Yes | [0, 1] |
| chunk.state | Enum | 1 tick | ChunkTensionData | Derived | {CALM,...,CRITICAL} |
| miningInflow | Float | Event | ChunkTensionManager | No | [0, 1] |
| combatInflow | Float | Event | ChunkTensionManager | No | [0, 1] |
| DECAY_RATE | Float | Const | ChunkTensionData | No | 0.00075 |
| DIFFUSION_FACTOR | Float | Const | ChunkTensionData | No | 0.045 |
| CLIENT_TENSION | Float | 20 ticks | TensionSyncState | No | [0, ∞) |
| smoothedTension | Float | Client | TensionSyncState | No | [0, ∞) |

---

## TOPOLOGY: HIERARCHICAL SPATIAL ORGANIZATION

```
[GLOBAL SYSTEM]
    ↓
[REGIONS (8×8 chunk arrays)] × NUM_REGIONS
    ↓
[CHUNKS (16×16 block arrays)] × (64 per region)
    ↓
[BLOCKS & ENTITIES] × (256 per chunk)
```

**Coupling Directions**:
- **Upward**: chunk.tension → region.tension (max aggregation)
- **Upward**: region.tension → global.tension (linear inflow)
- **Downward**: global.storm → region.amplification × 1.5
- **Lateral**: chunk ↔ chunk (4-neighbor diffusion)
- **Lateral**: region ↔ client (network sync every 20 ticks)

---

## KEY INSIGHTS FROM STATE INVENTORY

1. **Hierarchy**: Three-level hierarchy (global → regional → chunk) with different aggregation functions
2. **Hysteresis**: Storm system exhibits hysteretic switching (activation ≠ deactivation threshold)
3. **Asymmetry**: Upward aggregation uses MAX, downward amplification uses MULTIPLY
4. **Transience**: Inflows (mining, combat) are event-driven and ephemeral
5. **Persistence**: Tension and scar are NBT-serialized; most derived quantities are not
6. **Multiple Timescales**: Decay rates vary from 46 seconds (local) to 173 seconds (global)
7. **Spatial Coupling**: Local diffusion at 4.5% per tick creates smoothing but not complete synchronization
8. **Client-Server Separation**: Smooth client-side rendering via ~1-2 second exponential averaging

