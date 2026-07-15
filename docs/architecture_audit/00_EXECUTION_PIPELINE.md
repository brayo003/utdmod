# Execution Pipeline

> **[UPDATED 2026-07-15]** The dominant state variable is the per-chunk **substrate state** (A, M, D, B),
> not a stored tension scalar. Tension is a derived observation metric. The old chunk ODE
> (`TensionServerTick.tickChunkTension`) does not exist in current code.

---

## Verified runtime path

All server-side logic runs in a single `END_SERVER_TICK` handler registered in
`TensionServerTick.register()`.

### Step-by-step sequence (per server tick)

1. **Player activity → substrate impulses**  
   `TensionServerTick.trackPlayerMovement()` and `PlayerStateIntegrator.onServerPlayerTick()`
   accumulate impulses in `ChunkTensionData.instantaneousImpulses`. Event hooks in
   `TensionEvents` (mining, combat, portal use, etc.) also inject impulses via
   `ChunkTensionManager.addMiningTension()` → `ChunkTensionData.addInstantaneousImpulse()`.

2. **Substrate integration** (every 5 ticks)  
   `TensionServerTick.tickSubstrate()` → `ChunkTensionData.integrateAllSubstrateStates()`.  
   Applies `FieldDerivative.computeUnified()` (Euler step) for each loaded substrate chunk
   using a double-buffer gather/apply pattern.  
   After integration, runs `InterpretationService.interpretWorld()` and
   `FieldActuatorRegistry.actuateAll()` per chunk.

3. **Region refresh** (every 20 ticks)  
   `RegionManager.refresh()` rebuilds connected-component regions from all chunks where
   `T(x) > ACTIVE_TENSION_THRESHOLD (0.15)`.

4. **Global metric recompute** (every tick)  
   `TensionManager.recompute()` aggregates T(x) over all loaded substrate chunks into
   globalMeanTension, globalMaxTension, globalVariance, globalActiveFraction, etc.
   Sets stormActive and corruptionLevel from thresholds.

5. **Region diagnostics** (every 200 ticks)  
   `RegionDiagnosticsManager.refresh()` aggregates per-region snapshot data.

6. **Player region events** (every 20 ticks)  
   Tracks player chunk→region associations and emits enter/exit diagnostics.

7. **HUD diagnostics** (every 40 ticks)  
   Computes local T(x) at player position, sends actionbar message:
   `Tension: T(x) (ChunkState) | Global: G (CorruptionLevel) | Storm/Calm`.

8. **Field summary diagnostics** (every 20 ticks)  
   `DiagnosticLogs.fieldSummary()` emits region count, storm/fractured counts, maturity stats.

9. **Region summary diagnostics** (every 100 ticks)  
   `DiagnosticLogs.regionSummary()`.

10. **Client sync** (every 20 ticks)  
    `TensionSyncPacket.sendToAllPlayers()` sends globalMeanTension and stormActive to all clients.

11. **Storm tick** (every tick, per world)  
    `StormManager.tick()` runs region-based storm selection and lightning logic.

12. **Research / experiment infrastructure** (every tick)  
    `ResearchBotManager.tick()`, `ResearchObserver.tick()`, `ExperimentTelemetry.tick()`,
    `ExperimentLogger.maybeLogFieldSummary()`.

---

## What the pipeline actually does

- **Primary state variable:** `SubstrateState` per chunk (activation, memory, fieldDepth, baseline).
- **Primary update rule:** `FieldDerivative.computeUnified()` — a 4-variable coupled nonlinear system.
- **Tension:** derived from substrate variables on demand, never stored.
- **Regions:** derived from connected active chunks (T(x) > 0.15), rebuilt every 20 ticks.
- **Global tension:** spatial mean of T(x) over loaded chunks, recomputed every tick.
- **Storms:** downstream consumers of region state; do not inject tension directly.
- **Ecology and presentation:** consume state, mutate the world or client UI; do not feed back into substrate.

---

## Source

- `com.utdmod.core.TensionServerTick` — main tick orchestrator
- `com.utdmod.tension.ChunkTensionData` — substrate storage and integration
- `com.utdmod.tension.FieldDerivative` — update equations
- `com.utdmod.core.TensionManager` — global metric aggregation
- `com.utdmod.core.RegionManager` — region building
- `com.utdmod.storm.StormManager` — storm logic
- `com.utdmod.ecology.RegionEcologyManager` — block mutations
- `com.utdmod.network.TensionSyncPacket` — client sync
- `com.utdmod.client.TensionSyncState` — client mirror (never runs simulation)
