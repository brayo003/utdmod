# UTD Mod — System Overview (As Built)

This document describes the **actual running architecture** of `com.utdmod` as of the audit date. It is reverse-engineered from code, not design intent.

## Mod entry and registration

| Entry | Class | Role |
|-------|--------|------|
| Server | `UTDMod` | Registers blocks/items, events, networking, `TensionServerTick`, `SessionTelemetryExporter` |
| Client | `UTDModClient` | Sync packet receiver, HUD, audio, overlay, experiment client reports |

**Registered at runtime:** `TensionEvents`, `TensionMobHooks`, `TensionSpawnEcology`, `ExperimentCommands`, packets (`UseCrystalPacket`, `DebugSpikeTensionPacket`, `ExperimentClientReportPacket`), `TensionServerTick`, `SessionTelemetryExporter`.

**Not registered:** tick stubs (`OptimizedTickHandler`, `CompositeTickHandler`), `WorldgenHooks`, legacy signal HUD classes, duplicate client initializers, most entity AI mixins (see CRITICAL_FILES.md).

## Overall gameplay loop

1. **Player actions** (mining, movement, combat, rituals, sleep, portals, bulk build) inject **local chunk tension** via `ChunkTensionManager` / `ChunkTensionData`.
2. Every **5 server ticks**, loaded chunks undergo **local PDE-style update**: diffusion, decay, nonlinear growth, cubic saturation, global feedback (`TensionServerTick.tickChunkTension`).
3. Every server tick, **distributed coupling** aggregates stressed chunks into global inflow (`GlobalFieldCoupling.compute` → `TensionManager.tickGlobalDynamics`).
4. **Global storm** flips when `globalTension > 0.9` (clears below `0.7` hysteresis). **Chunk-local storms** flip when local `> 0.95` (clears below `0.25`).
5. **Regional ambience storm** (`StormManager`) is separate from global `stormActive` — driven by max(global, regional×0.52).
6. **Client** receives snapshots every **20 ticks** (`TensionSyncPacket`) → smoothing → audio/overlay/HUD.
7. **Recovery** is passive (decay, natural global recovery in pressure regime) plus rituals/items/warding crystal; contamination memory slows chunk decay.

## Core simulation flow (server)

```
END_SERVER_TICK (TensionServerTick)
  ├─ TensionTraceClock.setServerTick
  ├─ TensionActivityLedger.reset
  ├─ Per-player: PlayerStateIntegrator + movement → ChunkTensionManager
  ├─ Every 5t: tickChunkTension (diffusion, states, local storms, contamination)
  ├─ GlobalFieldCoupling.compute → TensionManager.tickGlobalDynamics
  ├─ Every 200t: RegionDiagnosticsManager.refresh
  ├─ Global storm effects (lightning on players, exhaustion)
  ├─ Every 20t: weather/ritual secondary, TensionSyncPacket, action bar messages
  └─ Per-world: StormManager.tick, ExperimentTelemetry.tick
```

Parallel event hooks (`TensionEvents`) fire on block break, death, sleep, use block, etc.

## Tick / update flow

| Cadence | System |
|---------|--------|
| Every tick | Movement integration, global dynamics, activity ledger reset |
| Every 5 ticks | Chunk tension field update (sparse stride for calm chunks) |
| Every 20 ticks | Global `[GLOBAL_FLOW]` log + CSV, client sync, weather secondary |
| Every 40 ticks | Player action bar status, global storm lightning cadence |
| Every 200 ticks | Regional diagnostics `[REGION]`, ecology budget |
| Every 400 ticks | Cumulative source totals to stdout |
| Every 5 min | Chunk data prune (45 min TTL, extended for hot/contaminated) |

## Client vs server responsibilities

| Concern | Server | Client |
|---------|--------|--------|
| Source of truth | `TensionManager`, `ChunkTensionData` | `TensionSyncState` only |
| Simulation | All tension math, storms, ecology modifiers | None |
| Feedback | Action bar, sounds (some), lightning | `UTDAudioManager`, `TensionScreenOverlay`, `TensionHud`, `ClientRegionalAtmosphere` |
| Logging | `DiagnosticLogs`, `TensionTraceLogger`, session CSV | `ExperimentClientReportPacket` → server `test4` |

**Rule:** Client code must not read `TensionManager` for gameplay feel; use `TensionSyncState.perceivedTension()`.

## Local vs global tension

- **Local** (`ChunkTensionData.localTensions`): per-chunk scalar, persisted in world save (`chunk_tension_data`), states STABLE→STRAINED→FRACTURED→DECOUPLED.
- **Global** (`TensionManager.globalTension`): single world scalar, not chunk-granular, capped at 5.0.
- **Coupling direction:** local stress → global inflow only (via `GlobalFieldCoupling`). Global → local via `GAMMA * globalTension` feedback each chunk tick.
- **Regimes:** Pressure (`g < 0.9`, storm off) vs catastrophe (storm on or `g ≥ 0.9`) change coupling multipliers, decay, and nonlinear alpha.

## Storm lifecycle (three layers)

### 1. Global storm (`TensionManager.stormActive`)

- **Start:** `globalTension > 0.9`
- **End:** `globalTension < 0.7` (hysteresis)
- **Effects:** Periodic lightning at player positions, hunger exhaustion, catastrophe coupling

### 2. Chunk-local storm (`ChunkTensionData.stormActive`)

- **Start:** local `> 0.95` → `triggerLocalStorm` (lightning burst in chunk)
- **End:** local `< 0.25`
- **Drain:** `STORM_DRAIN_RATE` while active

### 3. Regional ambience storm (`StormManager`)

- **Start:** `tension > 0.85` and random 1/120 when not active; `tension = max(global, regionalMax×0.52)`
- **End:** `tension < endCut` (0.10–0.20 depending on regional max)
- **Effects:** Cave/thunder sounds, weather pushes, drifts toward hotter 8×8 region

## Ritual lifecycle

- **Ritual block use:** +0.03 local, logged as RITUAL (`RitualBlock.onUse`)
- **RitualHandler:** direct global reductions (0.3–0.8) — called from items/commands, not auto-wired to block entity
- **Storm ritual:** `StormManager.triggerRitualStorm` if `RitualHandler.canPerformRitual` (requires `!TensionManager.isStormActive()`)
- **Consumables:** spike/calm global (`TensionConsumableItem`), warding crystal passive drain in `runWeatherAndRitualSecondary`
- **Ledger:** `TensionActivityLedger.addRitual` feeds `[GLOBAL_FLOW]` attribution

## Recovery lifecycle

- **Chunk decay:** `LAMBDA * local * contaminationMultiplier` (unless DECOUPLED)
- **Contamination:** peaks at 1.15/1.35 tiers slow decay (0.68× / 0.38×); decays when tension < 0.42
- **Global pressure recovery:** extra `-NATURAL_RECOVERY` when `!catastrophe && g > 0.012`
- **Diagnostics:** `[RECOVERY]` when contaminated chunk cools; `[TEST3]` half-life estimates
- **Sleep:** local −0.02/tick while sleeping; blocked if effective tension > 1.12

## Persistence

| Data | Mechanism | Key |
|------|-----------|-----|
| Chunk local tension, state, mining rate, contamination | `ChunkTensionData` extends `PersistentState` | `chunk_tension_data` |
| Global tension | **Not persisted** — resets on world load | in-memory only |
| Session telemetry | Append-only CSV | `<gameDir>/logs/utd_sessions/session_*.csv` |

## Networking

- `TensionSyncPacket` (every 20t): global, storm flag, player chunk local, region avg/max
- `UseCrystalPacket`, `DebugSpikeTensionPacket`, `ExperimentClientReportPacket` — utility/experiment paths
