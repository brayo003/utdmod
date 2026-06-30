# UTD Simulation Test Harness

Production-grade in-mod scenario runner under `com.utdmod.test`.

## A. Architecture summary

```
/utdtest (TestCommands)
    → ScenarioManager (single active runtime, registry)
        → ScenarioRuntime (step index, active action, state machine)
            → ScenarioScheduler (END_SERVER_TICK)
        → TestBootstrap (baseline reset + chunk preload)
        → Scenario + ScenarioAction[] + Assertion[]
        → ScenarioTelemetryCapture → ScenarioCsvWriter
        → ScenarioReport → ReportFileWriter
```

**Mining path:** `MineBlockAction` → `TestMiningBridge` → `ChunkTensionManager.addMiningTension` + `TensionLogger.traceWithGlobals` (same as `TensionEvents` block break handler).

**No simulation bypass** during scenario ticks. Only `TestBootstrap.resetSimulationState` at start (documented isolation).

## B. Tick lifecycle

1. Minecraft `END_SERVER_TICK`
2. `TensionServerTick` runs (movement, chunk PDE every 5t, global coupling, sync every 20t)
3. `ScenarioScheduler` calls `ScenarioRuntime.tick()` if a scenario is running
4. Runtime advances `scenarioTick`, writes telemetry row, runs current `ScenarioAction.tick()` once
5. `WaitTicksAction` returns RUNNING until elapsed ticks consumed

## C. Scenario execution lifecycle

1. `/utdtest run <id>`
2. `TestBootstrap.prepareScenario` — clear all chunk tension + `TensionManager.setTension(0)`
3. `ScenarioRuntime.start` — open CSV, build action/assertion lists, `scenario.onPrepare`
4. Each server tick: execute active action or start next action
5. All actions complete → run assertions → write report + summary chat
6. `ScenarioManager.onRuntimeComplete` — store last report

## D. Telemetry schema

**File:** `logs/utd_sessions/scenario_<name>_<timestamp>.csv`

| Column | Description |
|--------|-------------|
| real_tick | Server overworld time |
| scenario_tick | Ticks since scenario start |
| scenario_name | Scenario id |
| scenario_phase | Current phase label |
| phase_marker | Last `PhaseMarkerAction` |
| event_marker | Last `EventMarkerAction` |
| global_tension | Current global |
| peak_global_so_far | Running max global |
| peak_local_so_far | Running max local (tracked chunks) |
| peak_state_so_far | Highest chunk state ordinal seen |
| corruption_tier | Integer tier |
| storm_active | Global storm flag |
| chunk_locals / chunk_states / chunk_storms | Per-chunk pipe map |
| current_action | Active action name |
| assertion_status | PENDING / ALL_PASS / FAIL_* |
| detail | PHASE / EVENT / snapshot label |

**Peak assertions** use `ScenarioMetrics` (not end-state). See `PeakLocalRangeAssertion`, `PeakChunkStateAssertion`, `TimeToThresholdAssertion`.

**Session mirror (optional):** every 20 scenario ticks → `SessionTelemetryExporter` event `SCENARIO_TICK` and `SCENARIO_ASSERT` on completion.

## E. Assertion pipeline

Assertions run **once** after all actions complete. Each returns `AssertionResult` (expected, actual, tolerance, ticks). Results feed chat summary, report file, and CSV assertion_status.

## F. Known limitations

- **Baseline reset** uses `setTension(0)` at scenario boundary only; global `stormActive` clears on next `tickGlobalDynamics` (may need 1+ ticks after reset).
- **Mining simulation** does not break physical blocks (no block state change); tension path is identical to break events.
- **Single player** — command issuer is the scenario operator; no multiplayer orchestration.
- **Long scenarios** (`idle_drift` 5000t) run in real time unless `/tick rate` is raised externally.
- **RitualMitigationScenario** sets global to 0.75 in `onPrepare` (boundary bootstrap exception).
- **Regional StormManager** is not asserted separately from global storm.
- Assertions are end-loaded, not continuous (use snapshots + custom assertions for mid-run checks).

## G. Extension points

| Goal | Approach |
|------|----------|
| Parameter sweeps | Register scenarios programmatically; loop `/utdtest run` with different `onPrepare` seeds or external script |
| CI | Headless dedicated server + RCON/command block firing `/utdtest run idle_drift`; parse `*_report.txt` exit code via wrapper |
| Headless server | Same; ensure one op player online at test coords |
| Graph generation | Parse scenario CSV columns `global_tension`, `chunk_locals` with Python/pandas |

## H. Example scenario report

```
UTD Scenario Report
===================
scenario=single_chunk_fracture
description=Controlled mining in one chunk...
result=PASS
seed=625253230994
scenario_ticks=641
peak_global=0.12450
final_global=0.09820
peak_local=1.05200 at 10240,10240
...
Assertions
----------
PASS chunk_strained_or_fractured
  expected: one of [STRAINED, FRACTURED, DECOUPLED]
  actual:   STRAINED
```

## I. Example CSV rows

```csv
real_tick,scenario_tick,scenario_name,scenario_phase,global_tension,...
1200,1,single_chunk_fracture,MINING:10240,10240,0.04200,0,...
1201,2,single_chunk_fracture,MINING:10240,10240,0.08400,0,...
1800,600,single_chunk_fracture,post_mine_stabilize,0.11000,0,...
```

## Commands

| Command | Description |
|---------|-------------|
| `/utdtest list` | List scenarios |
| `/utdtest run <scenario>` | Start scenario |
| `/utdtest status` | Active scenario tick/phase |
| `/utdtest abort` | Stop current scenario |
| `/utdtest report` | Print last report to chat |
| `/utdtest telemetry on\|off` | Scenario CSV capture |

## Registered scenarios

- `idle_drift`
- `single_chunk_fracture`
- `multi_chunk_contamination`
- `post_storm_recovery`
- `ritual_mitigation`
