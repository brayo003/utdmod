# State Machines

> **[UPDATED 2026-07-15]**  
> Corrected: global storm thresholds (was 0.9/0.7 in old doc; actual: 0.55/0.40).  
> Corrected: corruption tiers (were [0.8, 1.5, 2.5, 4.0] in old doc; actual: [0.20, 0.40, 0.60, 0.80]).  
> Corrected: chunk states are driven by derived tension T(x), not a stored tension scalar.

All discrete states and transitions in the tension architecture.

---

## 1. Chunk discrete states (`ChunkTensionData.ChunkState`)

| State | Meaning |
|---|---|
| `STABLE` | T(x) below strained threshold (with hysteresis) |
| `STRAINED` | Elevated derived tension |
| `FRACTURED` | High sustained tension |
| `DECOUPLED` | Catastrophic local level |

### Upgrade thresholds

| Constant | Value | Source |
|---|---|---|
| `T1` | 0.95 | `ChunkTensionData` |
| `T2` | 1.28 | `ChunkTensionData` |
| `T3` | 3.0 | `ChunkTensionData` |

### Hysteresis (downgrade)

`STATE_HYSTERESIS = 0.22` — chunk must drop 0.22 below its tier threshold to leave that state.

### Transition logic

`ChunkTensionData.calculateState(tension, current)`:

```
tension <= 0                                 → STABLE
tension >= T3 (3.0)                          → DECOUPLED
current DECOUPLED && tension >= T3 - 0.22   → stay DECOUPLED
tension >= T2 (1.28)                         → FRACTURED
current FRACTURED && tension >= T2 - 0.22   → stay FRACTURED
tension >= T1 (0.95)                         → STRAINED
current STRAINED && tension >= T1 - 0.22    → stay STRAINED
else                                         → STABLE
```

**Note:** tension here is the derived metric T(x) = `SubstrateObservationModel.computeLocalTension(ss)`, not any stored value.

**Side effects on transition:** `DiagnosticLogs.tensionState()`, `ExperimentTelemetry.onChunkStateTransition()`, stdout particle placeholder for FRACTURED.

---

## 2. Chunk local storm (`ChunkTensionData.stormActive` map)

| | Condition |
|---|---|
| Start | T(x) > 0.95 |
| End | T(x) < 0.25 |

Set by `TensionServerTick.updateChunkStateFromMetric()`.

Chunk storm is independent of the global storm flag. A chunk can be storm-active while global storm is off, and vice versa.

---

## 3. Global storm (`TensionManager.stormActive`)

Driven by `globalMeanTension` (spatial mean of T(x) over all loaded substrate chunks).

| Transition | Condition |
|---|---|
| ON | `globalMeanTension > 0.55` (`STORM_THRESHOLD`) |
| OFF | `globalMeanTension < 0.40` (`STORM_HYSTERESIS`) |

Source: `com.utdmod.core.TensionManager`

Global storm broadcast to clients via `TensionSyncPacket`.

---

## 4. Global corruption tiers

No hysteresis. Tier is assigned instantly from current `globalMeanTension`.

| Tier | `globalMeanTension >=` | Name |
|---|---|---|
| 0 | — | None |
| 1 | 0.20 | Subtle |
| 2 | 0.40 | Moderate |
| 3 | 0.60 | Severe |
| 4 | 0.80 | Catastrophic |

Source: `com.utdmod.core.TensionManager` (`CORRUPTION_THRESHOLDS = {0.20, 0.40, 0.60, 0.80}`)

---

## 5. Regional ecology state (`RegionDiagnosticsManager.EcoState`)

Derived per region, not stored:

| State | Condition |
|---|---|
| `CALM` | regionMax <= 1.35 AND regionAvg <= 1.0 |
| `STRAINED` | regionAvg > 1.0 (and not FRACTURED rule) |
| `FRACTURED` | regionMax > 1.35 |

Used by `TensionSpawnEcology` for spawn modifiers.

---

## 6. Region states (`RegionState` enum)

`Region.getState()` is computed from derived tension statistics on each rebuild (every 20 ticks).

| State | Condition |
|---|---|
| `ANCIENT` | maturity > 2.25 AND age > 3200 |
| `FRACTURED` | meanTension >= 1.05 AND chunkCount >= 6 AND fracturedChunks >= max(1, chunkCount/8) |
| `STRAINED` | meanTension >= 0.78 OR maturity > 1.15 OR fracturedDensity > 0.16 OR stormDensity > 0.12 |
| `GROWING` | meanTension >= 0.35 OR maturity > 0.5 OR age > 900 |
| `DORMANT` | (none of the above) |

Source: `com.utdmod.core.Region.getState()`

---

## 7. Contamination memory (per chunk, byte level)

| Level | Set when T(x) >= |
|---|---|
| 1 (medium) | 1.15 |
| 2 (heavy) | 1.35 |

Decay: `tickContaminationMemory()` — when T(x) < 0.42, level decrements (staggered, 1/200 chunk slots per tick).

Effect on effective decay multiplier:
- Level 0: 1.0 (normal)
- Level 1: 0.68 (slower recovery)
- Level 2: 0.38 (much slower recovery)

---

## 8. Ritual gating

`RitualHandler.canPerformRitual()`: `player != null AND world != null AND !TensionManager.isStormActive()`

Ritual blocked during global storm only.

---

## 9. Sleep gating

`EntitySleepEvents.ALLOW_SLEEPING`: deny if `max(globalMeanTension, localTension) > 1.12`.

---

## State interaction diagram

```
[Player action]
  → addInstantaneousImpulse(chunk)
       ↓
[integrateAllSubstrateStates / FieldDerivative.computeUnified()]
  → SubstrateState (A, M, D, B) updated
       ↓
[SubstrateObservationModel.computeLocalTension(ss)]
  → T(x) computed
       ↓
[updateChunkStateFromMetric()]
  → ChunkState: STABLE ↔ STRAINED ↔ FRACTURED ↔ DECOUPLED
  → chunk stormActive: T(x) > 0.95 → on, T(x) < 0.25 → off
       ↓
[TensionManager.recompute()]
  → globalMeanTension, corruptionLevel
  → globalStormActive: globalMean > 0.55 → on, < 0.40 → off
       ↓
[RegionManager.refresh()]
  → Region objects (DORMANT / GROWING / STRAINED / FRACTURED / ANCIENT)
       ↓
[StormManager.tick()]   [RegionEcologyManager]
  → regional storms        → block mutations
```
