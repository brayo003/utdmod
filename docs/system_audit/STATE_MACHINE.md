# State Machines

All discrete states and transitions in the tension architecture.

---

## 1. Chunk discrete states (`ChunkTensionData.ChunkState`)

| State | Meaning |
|-------|---------|
| `STABLE` | Below strained threshold (with hysteresis) |
| `STRAINED` | Elevated local stress |
| `FRACTURED` | High sustained stress |
| `DECOUPLED` | Catastrophic local level |

### Thresholds (upgrade)

| Constant | Value | File |
|----------|-------|------|
| `T1` | 0.95 | `ChunkTensionData` |
| `T2` | 1.28 | `ChunkTensionData` |
| `T3` | 3.0 | `ChunkTensionData` |

### Hysteresis (downgrade)

`STATE_HYSTERESIS = 0.22` — must fall `0.22` below tier to leave FRACTURED/STRAINED/DECOUPLED.

### Transition logic

**Method:** `ChunkTensionData.calculateState(tension, current)`

```
tension <= 0        → STABLE
tension >= T3       → DECOUPLED
current DECOUPLED && tension >= T3 - 0.22 → stay DECOUPLED
tension >= T2       → FRACTURED
current FRACTURED && tension >= T2 - 0.22 → stay FRACTURED
tension >= T1       → STRAINED
current STRAINED && tension >= T1 - 0.22 → stay STRAINED
else                → STABLE
```

**Trigger:** Any `setLocalTension` / `addLocalTension` that changes computed state.

**Side effects:** `DiagnosticLogs.tensionState`, `ExperimentTelemetry.onChunkStateTransition`, FRACTURED stdout “smoke” placeholder.

### Log naming vs enum

`DiagnosticLogs` maps: STABLE→**CALM**, STRAINED→STRAINED, FRACTURED→FRACTURED, DECOUPLED→FRACTURED (display).

**Approximate ladder for EVENT_EFFECT** (`approximateState`): CALM `<0.7`, STRAINED `0.7–0.95`, FRACTURED `≥0.95` — **differs** from `calculateState` T1=0.95 upgrade path.

---

## 2. Chunk local storm (`ChunkTensionData.stormActive` map)

| Field | Start | End |
|-------|-------|-----|
| Condition | `localTension > 0.95` | `localTension < 0.25` |

**Methods:** `TensionServerTick.tickChunkTension` → `setStormActive`, `triggerLocalStorm`

**Effects:** Lightning entities in chunk; `effectiveAlpha × 1.8`; `STORM_DRAIN_RATE` decay while active.

**Independent of** chunk STRAINED/FRACTURED enum and global storm.

---

## 3. Global storm (`TensionManager.stormActive`)

| Transition | Condition | Method |
|------------|-----------|--------|
| ON | `globalTension > 0.9` | `tickGlobalDynamics` |
| OFF | `globalTension < 0.7` | `tickGlobalDynamics` (hysteresis) |

**Side effects:** Catastrophe regime (coupling 1.62, weaker lambda, higher alpha), player lightning/exhaustion in `TensionServerTick`.

**Trace:** `TensionTraceLogger.traceSystem("GLOBAL_STORM_START/END")`

---

## 4. Global corruption tiers

| Tier | Threshold (`globalTension >=`) | Name |
|------|-------------------------------|------|
| 0 | — | None |
| 1 | 0.8 | Subtle |
| 2 | 1.5 | Moderate |
| 3 | 2.5 | Severe |
| 4 | 4.0 | Catastrophic |

**Method:** `TensionManager.updateCorruptionState()` — no hysteresis (instant tier from current g).

**Trace:** `CORRUPTION_TIER_CHANGE` via `TensionTraceLogger.traceSystem`

---

## 5. Global dynamics regime (implicit, not stored)

| Regime | Condition |
|--------|-----------|
| Pressure | `!stormActive && g < STORM_THRESHOLD (0.9)` |
| Catastrophe | `stormActive \|\| g >= 0.9` |

Affects: `COUPLING_PRESSURE (0.82)` vs `COUPLING_CATASTROPHE (1.62)`, `lambdaEff`, `alphaEff`, `NATURAL_RECOVERY` application.

---

## 6. Regional ambience storm (`StormManager.stormActive`)

| Transition | Trigger |
|------------|---------|
| Start | `!stormActive && tension > 0.85 && random 1/120` |
| End | `stormActive && tension < endCut` (0.10 if regional max>1.35 else 0.20) |

`tension = max(global, regionalMax × 0.52)`

**Ritual:** `triggerRitualStorm` forces start at tension 1.5 if `RitualHandler.canPerformRitual`.

---

## 7. Contamination memory (per chunk, byte level)

| Level | Set when tension >= |
|-------|---------------------|
| 1 (medium) | 1.15 |
| 2 (heavy) | 1.35 |

**Decay:** `tickContaminationMemory` — when tension `< 0.42`, level decrements (staggered 1/200 chunks per tick slot).

**Effect:** Decay multiplier 0.68 (lv1) or 0.38 (lv2) on chunk linear decay.

---

## 8. Regional ecology state (`RegionDiagnosticsManager.EcoState`)

Derived from snapshot, not stored:

| State | Condition |
|-------|-----------|
| CALM | `max <= 1.35` and `avg <= 1.0` |
| STRAINED | `avg > 1.0` (and not FRACTURED rule) |
| FRACTURED | `max > 1.35` |

Used by `TensionSpawnEcology` for spawn modifiers.

---

## 9. Ritual gating

`RitualHandler.canPerformRitual`: `player != null && world != null && !TensionManager.isStormActive()`

Blocks ritual storm trigger during **global** storm only.

---

## 10. Sleep gating

`EntitySleepEvents.ALLOW_SLEEPING`: deny if `max(global, local) > 1.12`.

---

## State interaction diagram

```
[Player action] → local t ↑
       ↓
[calculateState] STABLE↔STRAINED↔FRACTURED↔DECOUPLED
       ↓
[GlobalFieldCoupling] if stressed → global g ↑
       ↓
[TensionManager] g>0.9 → global storm ON; corruption tier ↑
       ↓
[Chunk tick] GAMMA feeds global → local; local>0.95 → chunk storm
       ↓
[StormManager] parallel regional ambience
```
