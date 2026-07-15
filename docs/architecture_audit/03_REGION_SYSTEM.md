# Region System

> **[UPDATED 2026-07-15]** Minor correction: regions are built from computed derived tension T(x),
> not from stored chunk tension values. T(x) is computed on demand from substrate state.

---

## Purpose

The region system groups nearby substrate-active chunks into connected components and exposes
region-level summaries that feed global metric aggregation, storm selection, and ecology.

---

## Inputs

### Caller

`TensionServerTick.onEndServerTick()` calls `RegionManager.refresh(server, tick)` every 20 ticks.

### Data sources

For each `ServerWorld`:
- `ChunkTensionData.getSubstrateStatesView()` — substrate state per chunk
- `SubstrateObservationModel.computeLocalTension(ss)` — derives T(x) per chunk on demand
- `ChunkTensionData.getChunkState(pos)` — STABLE/STRAINED/FRACTURED/DECOUPLED enum
- `ChunkTensionData.isStormActive(pos)` — per-chunk storm flag
- `world.isChunkLoaded()` — only loaded chunks are included

---

## Active-chunk threshold

A chunk is included in region building when:

$$T(x) > \text{ACTIVE\_TENSION\_THRESHOLD} = 0.15$$

This is `SubstrateState.ACTIVE_TENSION_THRESHOLD`.

---

## Outputs

A new immutable `List<Region>` replaces the previous list every 20 ticks.
Each `Region` object carries:

- `id`, `worldKey`, `chunks` (set of ChunkPos)
- Derived tension stats: `meanTension`, `maxTension`, `varianceTension`, `activeFraction`
- Substrate field stats: `meanDepth`, `meanMemory`, `meanActivation`, `maxActivation`
- Geometry: `centroidX`, `centroidZ`, `chunkCount`
- History: `age`, `maturity`, `lastUpdatedTick`
- Damage: `fracturedChunks`, `decoupledChunks`, `stormChunks`

---

## Algorithms

**Flood-fill:** 4-neighbor BFS over the active-chunk graph. Each connected component becomes one `Region`.

**Region state:** `Region.getState()` computed from tension stats, maturity, fractured density, and age. See `STATE_MACHINE.md` section 6 for threshold values.

**Maturity:** Exponential smoothing: `M_r += 0.005 × (P_r − M_r)` each refresh, where `P_r = meanTension × sqrt(chunkCount) × densityBonus`.

**Persistence:** Previous-tick regions are matched by chunk overlap to assign age continuity and emit lifecycle diagnostics (regionCreated, regionStateChanged, regionMerged, regionDestroyed).

---

## Persistent state

Regions are not persisted to disk. They are rebuilt from chunk substrate state every 20 ticks.
Region identity (age, maturity) is carried forward in-memory only.

---

## Tick rate

- Region rebuild: every 20 server ticks.
- Region diagnostics: every 200 ticks (`RegionDiagnosticsManager.refresh()`).
- Region-per-player lookups: every 20 ticks.

---

## Dependencies

- Reads substrate state via `ChunkTensionData`.
- Computes T(x) via `SubstrateObservationModel`.
- Supplies data to `TensionManager` (indirect — via global mean computation), `StormManager` (storm selection), `RegionEcologyManager` (ecology policy).

## Important observation

The region system is a derived layer. It does not mutate chunk substrate state. It summarizes the already-integrated field.

---

## Source

`com.utdmod.core.RegionManager`, `com.utdmod.core.Region`, `com.utdmod.core.RegionState`
