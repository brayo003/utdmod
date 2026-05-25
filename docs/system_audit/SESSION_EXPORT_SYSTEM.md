# Session Export System

Implemented in `com.utdmod.telemetry.SessionTelemetryExporter`.

---

## Purpose

One **append-only, machine-readable** log per dedicated server session for offline analysis — without changing gameplay equations.

---

## Directory layout

```
<minecraft game directory>/
  logs/
    utd_sessions/
      session_2026-05-16_18-42-11.csv
      session_2026-05-16_18-42-11_summary.txt
```

Path resolution: `FabricLoader.getInstance().getGameDir().resolve("logs/utd_sessions")`.

---

## Lifecycle

| Event | Action |
|-------|--------|
| `SERVER_STARTED` | Create new CSV (`CREATE_NEW`), write header, `SESSION_START` row |
| During play | `append(...)` on diagnostic hooks; flush every 8 rows |
| `SERVER_STOPPING` | `SESSION_END` row, flush, write `_summary.txt`, close writer |

**Registration:** `UTDMod.onInitialize()` → `SessionTelemetryExporter.register()`.

---

## CSV schema

```csv
tick,event_type,player,chunk_x,chunk_z,local_before,local_after,global_before,global_after,local_delta,global_delta,state_before,state_after,storm_active,corruption_tier,detail
```

- Numeric fields: 5 decimal places (US locale)
- Strings: quoted if they contain comma/quote/newline
- Empty cells allowed for N/A

---

## Event sources (wired)

| event_type | Source |
|------------|--------|
| `SESSION_START` / `SESSION_END` | Exporter lifecycle |
| `TENSION_STATE` | `DiagnosticLogs.tensionState` |
| `GLOBAL_FLOW` | `DiagnosticLogs.globalFlowLine` (every 20t) |
| `EVENT_EFFECT` | `DiagnosticLogs.eventEffect` via `TensionLogger` |
| `STORM` | `DiagnosticLogs.stormStart` |
| `STORM_END` | `DiagnosticLogs.stormEnd` |
| `RECOVERY` | `DiagnosticLogs.recovery` |

**Not yet mirrored:** REGION, SPAWN_ECOLOGY, TEST1–4, `TensionTraceLogger` SYSTEM lines.

---

## Summary file (`*_summary.txt`)

Generated on shutdown:

| Field | Meaning |
|-------|---------|
| `peak_global` | Max global seen in any append |
| `global_storm_starts/ends` | Edge detect on `storm_active` |
| `ticks_in_global_storm` | Count of rows where storm active (biased high if many GLOBAL_FLOW rows) |
| `peak_local` + chunk coords | Max `local_after` |
| `estimated_*_local_sum` | Sum of positive `local_delta` by event_type substring (MINING/COMBAT/MOVEMENT/RITUAL) |
| `ritual_event_count` | RITUAL-tagged positive deltas |
| `final_global`, `final_storm_active`, `final_corruption_tier` | End state |

**Note:** Corruption tier **durations** should be computed offline by histogramming `corruption_tier` on GLOBAL_FLOW rows (or extend exporter later).

---

## Safety

- **Append-only:** no rewrite of session file mid-game
- **Flush:** every 8 rows + shutdown flush — limits data loss on crash
- **Threading:** intended for server thread only (same as diagnostics)
- **Failure:** errors print `[UTD_SESSION]` to stderr; gameplay continues (writer nulled on open failure)

---

## API (for future hooks)

```java
SessionTelemetryExporter.append(tick, eventType, player, chunkX, chunkZ,
    localBefore, localAfter, globalBefore, globalAfter,
    stateBefore, stateAfter, stormActive, corruptionTier, detail);

SessionTelemetryExporter.recordStateTransition(chunk, oldState, newState, tension, tick, note);
SessionTelemetryExporter.recordEventEffect(...);
SessionTelemetryExporter.recordGlobalFlow(...);
```

---

## Analysis tips

1. Filter `event_type=GLOBAL_FLOW` → reconstruct global ODE terms from `detail` string.
2. Filter `event_type=EVENT_EFFECT` + `detail` containing block id → mining A/B tests.
3. Join `TENSION_STATE` rows to measure time between CALM→STRAINED→FRACTURED.
4. Compare `session_*_summary.txt` across builds after single-knob changes (see `TUNING_SURFACE.md`).

---

## Constraints honored

- No rebalance of thresholds in this implementation
- No changes to `TensionManager` / coupling equations
- Observability-only addition
