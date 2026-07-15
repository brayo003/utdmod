# Local Field

> **[UPDATED 2026-07-15]** This file replaces the pre-refactor version that described a scalar
> reaction-diffusion ODE (`T += D·T̄_nbr + α·T² − β·T³ + γ·G − λ·T`). That update rule
> and `TensionServerTick.tickChunkTension()` no longer exist. See `docs/archive/` for the
> old description.

---

## Purpose

The local field is the core dynamical substrate. Each chunk carries a `SubstrateState` object
with four evolving variables: **activation (A), memory (M), field depth (D), baseline (B)**.
These are integrated every 5 server ticks by `ChunkTensionData.integrateAllSubstrateStates()`.

Tension is **not stored per chunk**. It is computed on demand as a derived metric
`T(x) = (wA·A/(1+A) + wM·M/(1+M) + wD·D/(1+D)) / (wA+wM+wD)`.

---

## Inputs

### Player actions (event injection)

| Source | Entry point | Injection form |
|---|---|---|
| Player movement | `TensionServerTick.trackPlayerMovement()` | `ChunkTensionData.addInstantaneousImpulse(pos, amount)` |
| Mining / block break | `TensionEvents` → `ChunkTensionManager.addMiningTension()` | impulse |
| Combat / entity death | `TensionEvents` → `ChunkTensionManager` | impulse |
| Portal / ritual use | `TensionEvents` | impulse |
| ResearchPerturbationBridge | automated research players | impulse |

All injections accumulate in `ChunkTensionData.instantaneousImpulses` (Map<ChunkPos, Double>).
They are consumed as `externalInput` at the start of the next substrate integration tick.

### Substrate integration inputs

Per-chunk per-integration-cycle:
- `externalInput` — consumed impulse for this chunk
- `incomingTransport` — computed by `SubstrateTransport.computeIncoming()` from neighbor substrate snapshot
- `stencil` — `SpatialStencil` (Laplacians and gradients of A, M, D from the tick-start snapshot)
- `parameters` — `ParameterSet.runtime()` (singleton, validated defaults unless gameplay tuning enabled)

---

## Outputs

- Updated `SubstrateState` per chunk written back to `ChunkTensionData`.
- Derived tension T(x) computed on demand for state classification, region aggregation, and HUD.
- Chunk state (`STABLE / STRAINED / FRACTURED / DECOUPLED`) updated by `updateChunkClassification()`.
- Storm flag per chunk updated by `updateChunkStateFromMetric()`.
- All state changes emit diagnostics via `DiagnosticLogs.tensionState()`.
- Experiment telemetry emitted via `ExperimentTelemetry.onChunkStateTransition()`.

---

## Persistent state

`ChunkTensionData` extends `PersistentState`. It persists:
- `SubstrateState` fields: activation, memory, transport, fieldDepth, baseline, externalInput, readiness, radius, capacity, peakActivation, convergence
- `ChunkState` enum per chunk
- recentMiningRate, stormActive, contaminationPeak, contaminationLevel
- lastAccessMs per chunk (used for TTL pruning)

NBT key: `substrateChunks` (legacy fallback: `localTensions` for forward/backward compat).
PersistentState key: `chunk_tension_data`.

---

## Algorithms

### Double-buffer integration (gather / apply)

`ChunkTensionData.integrateAllSubstrateStates()`:

1. Take a snapshot copy of all current substrate states (tick-start state).
2. For each loaded chunk in the scheduled list:
   a. Read `current` from snapshot.
   b. Compute `SpatialStencil` (Laplacians, gradients) from the snapshot.
   c. Compute `incomingTransport` from the snapshot.
   d. Consume `externalInput` impulse.
   e. Apply `FieldDerivative.computeUnified()` → Euler step on copy.
3. Write all next-states back to `substrateStates` (apply phase).

No chunk reads another chunk's mid-step state. All reads are from tick-start snapshot.

### Chunk state classification

`TensionServerTick.updateChunkStateFromMetric()` runs after each substrate tick:

```
T(x) > 0.95  → stormActive[chunk] = true
T(x) < 0.25  → stormActive[chunk] = false
chunkData.updateChunkClassification(pos, T(x), world)
```

`ChunkTensionData.calculateState(tension, currentState)` applies:

```
T(x) >= 3.0  (T3) → DECOUPLED
T(x) >= 1.28 (T2) → FRACTURED
T(x) >= 0.95 (T1) → STRAINED
else             → STABLE
```

With `STATE_HYSTERESIS = 0.22`: downgrade is deferred until `T(x) < threshold - 0.22`.

### Contamination memory

When T(x) >= 1.35 → contamination level 2 ("heavy").
When T(x) >= 1.15 → contamination level 1 ("medium").
Level decrements when T(x) < 0.42, staggered over 200-tick slots.

Contamination reduces the effective linear decay multiplier:
- Level 1: multiplier 0.68 (32% slower recovery)
- Level 2: multiplier 0.38 (62% slower recovery)

### TTL pruning

Every ~5 minutes (300 ticks × 20 s/min): `chunkData.pruneInactive(45 min)`.
Chunks with T(x) >= 0.85 or contamination level >= 2 have extended TTLs (4–6 hours).

---

## Tick rate

| Operation | Cadence |
|---|---|
| Substrate integration | every 5 ticks |
| Chunk state transitions | after every substrate tick |
| Contamination decay | staggered, ~1 chunk per tick slot |
| TTL pruning | every ~5 minutes |

---

## Source

- `com.utdmod.tension.ChunkTensionData` — storage, integration, state classification
- `com.utdmod.tension.SubstrateState` — per-chunk evolving state
- `com.utdmod.tension.FieldDerivative` — canonical update equations
- `com.utdmod.tension.SubstrateTransport` — inter-chunk transport kernel
- `com.utdmod.tension.SpatialStencil` — Laplacian and gradient computation from neighbors
- `com.utdmod.tension.SubstrateObservationModel` — T(x) and other derived metrics
- `com.utdmod.tension.ParameterSet` — all numerical parameters
- `com.utdmod.tension.ChunkTensionManager` — event injection entry points
