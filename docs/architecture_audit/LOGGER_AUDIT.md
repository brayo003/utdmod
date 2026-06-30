# Logger Audit

All observability paths in `com.utdmod` as of audit.

---

## 1. SLF4J — `UTDMod.LOGGER`

| Aspect | Detail |
|--------|--------|
| Tag | `UTDMod` (LoggerFactory) |
| Destination | Log4j via Fabric (typically `latest.log`) |
| Usage | Mod init info, registry info, **debug-only** chunk/mining traces |
| Cadence | Sparse |
| Duplication | Overlaps with `System.out` diagnostics |

**Call sites:** `UTDMod`, `ModSounds`, `ModSoundEvents`, `TensionServerTick` (lightning debug), `ChunkTensionManager` (mining debug).

---

## 2. Structured stdout — `DiagnosticLogs`

| Tag | Cadence | Trigger | Fields |
|-----|---------|---------|--------|
| `[TENSION_STATE]` | On chunk state change | `ChunkTensionData.setLocalTension` | chunk, old/new, tension, tick, hysteresis |
| `[REGION]` | Every 200t per active region | `RegionDiagnosticsManager.refresh` | avg, max, strained, fractured, storm, hostile_bias |
| `[GLOBAL_FLOW]` | Every 20t | `TensionManager.tickGlobalDynamics` | global, coupling, inflow, decay, nonlinear, ritual buffer, storm_drive, move/mine/combat sums, net |
| `[EVENT_EFFECT]` | Per player action | `TensionLogger` | event, block, chunk, delta, before/after, threshold_crossed, new_state |
| `[STORM]` | Regional storm start | `StormManager.triggerStorm` | region, trigger, value, global, players, strength, toward |
| `[STORM_END]` | Regional storm end | `StormManager.endStorm` | duration, peak, region |
| `[SPAWN_ECOLOGY]` | Throttled ecology | `TensionSpawnEcology` | region, state, passive/hostile mods |
| `[RECOVERY]` | Contamination cool | `TensionServerTick` stagger | chunk, peaks, recovery_rate |
| `[TEST1]` | Experiment fracture timing | `ExperimentTelemetry` | phase, chunk, times, peak |
| `[TEST2]` | Experiment spread | `ExperimentTelemetry` | origin, affected, radius, avg |
| `[TEST3]` | Half-life estimate | `TensionServerTick` | peak, half_life_ticks |
| `[TEST4]` | Client feel report | `ExperimentClientReportPacket` | perceived, audio, overlay, hostiles |

**CSV mirror:** `SessionTelemetryExporter` hooks on TENSION_STATE, GLOBAL_FLOW, EVENT_EFFECT, STORM, STORM_END, RECOVERY.

**Gap:** REGION, SPAWN_ECOLOGY, TEST1–4 not written to session CSV by default.

---

## 3. Legacy trace — `TensionTraceLogger`

| Tag | Format |
|-----|--------|
| `[TRACE]` | Single line: tick, player, source, detail, chunk, delta, before, after |
| `[TENSION_TRACE]` | Multi-line block with local/global totals, state, propagation |
| SYSTEM actions | `traceSystem` — corruption, global storm, equilibrium damp, chunk prune |

**Cadence:** Every `TensionLogger.log` / `traceWithGlobals` call (can be very high during mining).

**Duplication:** Emitted **in addition to** `[EVENT_EFFECT]` for same events.

---

## 4. `TensionLogger` bridge

- `ENABLED = true` (toggle)
- Routes to `TensionTraceLogger` + `DiagnosticLogs.eventEffect`
- `traceWithGlobals` includes global before/after in structured trace

---

## 5. Ad-hoc `System.out` (un tagged blocks)

| Prefix | Source |
|--------|--------|
| `[UTDMod]` | Init, global status every 40t, FRACTURED smoke |
| `[UTD-CHUNK]` | Debug chunk PDE terms |
| `[STORM]` | StormManager when DEBUG_LOGGING |
| `[TENSION LOCK]` | Kinetic phial |
| `[AI LOG]` / `[CAT REWARD]` / etc. | Mixins (if enabled) |
| `[HIGH TENSION]` / `[SERPENT SPAWN]` | TensionSerpentEntity |
| `===== TENSION SOURCE TOTALS =====` | Every 400t cumulative movement/mining |

---

## 6. Session CSV — `SessionTelemetryExporter`

| Aspect | Detail |
|--------|--------|
| Path | `<gameDir>/logs/utd_sessions/session_YYYY-MM-DD_HH-mm-ss.csv` |
| Summary | `session_*_summary.txt` on `SERVER_STOPPING` |
| Mode | Append-only, flush every 8 rows |
| Header | tick, event_type, player, chunk_x/z, local/global before/after, deltas, states, storm_active, corruption_tier, detail |

**Event types:** SESSION_START/END, TENSION_STATE, GLOBAL_FLOW, EVENT_EFFECT, STORM, STORM_END, RECOVERY.

---

## 7. Experiment command output

`/utdexp` → `ExperimentCommands` → stdout from `ExperimentTelemetry` + DiagnosticLogs TEST tags.

---

## 8. `UTDProfiler`

- `UTDMod.LOGGER.info("[UTD-Profiler] ...")` — **no callers** in production path.

---

## Duplication matrix

| Event | TRACE | TENSION_TRACE | EVENT_EFFECT | CSV |
|-------|-------|---------------|--------------|-----|
| Mining | ✓ | ✓ | ✓ | ✓ |
| State change | — | — | ✓ (tensionState) | ✓ |
| Global tick | — | — | GLOBAL_FLOW | ✓ (20t) |

**Recommendation for tuning sessions:** Prefer **session CSV** for aggregates; use stdout for live debugging. Disable `TensionLogger.ENABLED` or reduce mining if log IO becomes limiting.

---

## Missing telemetry (gaps)

- Per-tier corruption **duration** in summary (tier only at end; use CSV `corruption_tier` column for offline histogram)
- REGION snapshots to CSV
- Global storm start/end as dedicated CSV rows (infer from `storm_active` on GLOBAL_FLOW rows)
- Client overlay/audio counters only via TEST4 every 200t client-side
- No unified log level / config file (`UTDConfig` unused)

---

## Cadence summary

| System | Period |
|--------|--------|
| Server tick | 1 |
| Chunk PDE | 5 |
| GLOBAL_FLOW + CSV | 20 |
| TensionSyncPacket | 20 |
| Player action bar | 40 |
| REGION log | 200 |
| Cumulative sources | 400 |
| Chunk prune | 5 min |
| Session summary | world stop |
