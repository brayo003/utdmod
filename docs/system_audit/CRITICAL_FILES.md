# Critical Files — Categorization

92 Java sources under `com.utdmod`. This list focuses on tension/storm architecture and adjacent systems.

---

## A. CORE STABLE SYSTEMS (do not rewrite)

Architecture-complete, wired, and forming the verified simulation spine. Changes should be **calibration-only** unless fixing bugs.

| File | What it does | Why stable |
|------|----------------|------------|
| `tension/ChunkTensionData.java` | Per-chunk tension map, state machine, contamination, local storm flags, NBT persistence | Single source of local truth; hysteresis and prune logic are integrated |
| `tension/ChunkTensionManager.java` | Facade for mining/combat/local add/reduce | All player sinks go through here |
| `core/GlobalFieldCoupling.java` | Distributed chunk→global inflow; idle-stable when no stressed chunks | Replaces hotspot coupling; `STRESS_BASELINE` gate is structural |
| `core/CouplingSplit.java` | Decomposition DTO for diagnostics | Pure data; no behavior |
| `core/TensionManager.java` | Global scalar dynamics, dual regime, storm hysteresis, corruption tiers | Central global ODE; equilibrium guard |
| `core/TensionServerTick.java` | Server tick orchestration, chunk PDE, sync cadence, global storm FX | Main loop; local storm threshold lives here |
| `core/TensionActivityLedger.java` | Per-tick movement/mining/combat/ritual sums for GLOBAL_FLOW | Attribution only |
| `core/TensionTraceClock.java` | Server tick counter for logs | Trivial, widely referenced |
| `event/TensionEvents.java` | Fabric hooks: break, death, sleep, portal, bulk build, deep layer | Primary player→local ingress |
| `tick/TensionLogger.java` | Bridges actions → trace + DiagnosticLogs + CSV | Standard observability path |
| `core/TensionTraceLogger.java` | `[TRACE]` + `[TENSION_TRACE]` structured stdout | Human tuning trail |
| `diag/DiagnosticLogs.java` | Tagged stdout blocks + SessionTelemetry hooks | Canonical log format |
| `core/RegionDiagnosticsManager.java` | 8×8 region aggregates, hostile bias estimate | Storm drift + sync packet + ecology |
| `network/TensionSyncPacket.java` | Server→client tension snapshot | Client isolation contract |
| `client/TensionSyncState.java` | Client mirror + smoothing + perceivedTension() | Required for feel layer |
| `telemetry/SessionTelemetryExporter.java` | Append-only session CSV + summary | New; non-gameplay |

**Supporting stable (smaller but wired):**

- `signals/PlayerStateIntegrator.java` — long-session fatigue leak
- `event/TensionMobHooks.java` — hostile attribute buffs in stressed chunks
- `ecology/TensionSpawnEcology.java` + `mixin/SpawnHelperMixin.java` — spawn weighting
- `core/TensionEvent.java` — log record type

---

## B. CALIBRATION FILES (safe to tune)

Constants and thresholds expected to change during balancing. **Do not restructure** without cause.

| File | Tunables | Gameplay impact |
|------|----------|-----------------|
| `core/TensionManager.java` | `LAMBDA`, `NATURAL_RECOVERY`, `ALPHA_*`, `STORM_THRESHOLD`, `STORM_HYSTERESIS`, `COUPLING_*`, `CORRUPTION_THRESHOLDS`, `T_MAX` | Global rise/fall, storm flip, corruption names, equilibrium |
| `core/GlobalFieldCoupling.java` | `STRESS_BASELINE`, `REGION_GAIN`, `REGION_EXPONENT`, `WEIGHT_*`, `*_PER_CHUNK` | How fast local stress becomes global; idle drift fix |
| `core/TensionServerTick.java` | `D`, `LAMBDA`, `ALPHA`, `BETA`, `GAMMA`, `STORM_THRESHOLD`, `STORM_END_THRESHOLD`, `STORM_DRAIN_RATE`, movement amounts | Local spread, local storm, chunk feel |
| `tension/ChunkTensionData.java` | `T1`, `T2`, `T3`, `STATE_HYSTERESIS`, contamination thresholds | State labels, fracture timing |
| `tension/ChunkTensionManager.java` | Mining tier `baseAmount` table | Mining danger curve |
| `storm/StormManager.java` | Trigger 0.85, end cuts, random 120, tension blend 0.52 | Ambient storm frequency |
| `event/TensionEvents.java` | Portal, sleep, bulk build, deep mining constants | Secondary sources |
| `ritual/RitualHandler.java` | Reduction amounts 0.3–0.8 | Ritual recovery strength |
| `signals/PlayerStateIntegrator.java` | 72_000 tick threshold, leak rate | Long-session drift |
| `client/TensionSyncState.java` | Smoothing `aG`, `aL`, perceived blend | Client feel lag |
| `client/audio/UTDAudioManager.java` | Thresholds for cues | Audio density |
| `client/visual/*` | Overlay intensities | Visual noise |
| `ecology/TensionSpawnEcology.java` | passive/hostile multipliers | Mob density feel |

See `TUNING_SURFACE.md` for exhaustive parameter table.

---

## C. PARTIAL / EXPERIMENTAL SYSTEMS

Working or prototyped but incomplete integration.

| File | Status |
|------|--------|
| `storm/StormManager.java` | Runs; separate `stormActive` from global; weather/sound only |
| `ritual/RitualHandler.java` | API exists; not all items/blocks call it consistently |
| `block/RitualBlock.java` | Adds local tension on use (counter-intuitive for “ritual calm”) |
| `experiment/ExperimentTelemetry.java` | TEST1–3 timing; tied to `/utdexp` |
| `command/ExperimentCommands.java` | Debug commands |
| `network/ExperimentClientReportPacket.java` | TEST4 client feel loop |
| `entity/TensionWraithEntity.java`, `TensionSerpentEntity.java` | Registered; spawn hooks empty in `TensionManager` |
| `ai/*Goal.java` | Goals exist; limited entity wiring |
| `items/*`, `item/*` | Mixed global/local effects; kinetic phial “lock” is println only |
| `core/TensionManager.triggerWraithEvent/triggerSerpentEvent` | Empty stubs |
| `ChunkTensionData.triggerStateTransitionEffects` | FRACTURED smoke is stdout only, no particles |
| `runWeatherAndRitualSecondary` | Global weather side-effects; not a full “recovery system” |
| Mixins (Piglin, Villager, Cat, Enderman, LootTable, Raid) | **Compiled but NOT in `utdmod.mixins.json`** — inactive unless added |

---

## D. DEAD OR DISCONNECTED CODE

**Do not delete** per audit charter — identification only.

| File | Evidence |
|------|----------|
| `tick/OptimizedTickHandler.java` | Never referenced |
| `tick/CompositeTickHandler.java` | Never referenced |
| `tick/TickLogger.java` | Never referenced |
| `WorldgenHooks.java` | Never registered |
| `signals/TensionHUD.java` | Stub, no registration |
| `signals/SmallSignal.java` | Stub |
| `signals/PlayerStateTension.java` | Wrong package path in tools; stub |
| `player/PlayerStateTension.java` | Stub |
| `client/UTDModClientInitializer.java` | Not in `fabric.mod.json` entrypoints |
| `client/overlay/TensionMeterOverlay.java` | HudRenderCallback never registered in `UTDModClient` |
| `client/UTDSoundTickHandler.java` | Unused |
| `sound/UTDSoundManager.java` | Unused (audio is `UTDAudioManager`) |
| `UTDProfiler.java` | No callers |
| `spec/DesignRules.java` | Documentation constants only |
| `config/UTDConfig.java` | Not loaded by `UTDMod` |
| `core/TensionView.java` | Unused interface |
| `TensionManager.addMiningTension(String)` | `@Deprecated` empty |
| `TensionManager.addDeforestationTension()` | Empty |
| `network/TensionSyncPacket.write/read` | Helper only; sync uses inline buf |
| `registry/ModSounds.java` vs `ModSoundEvents.java` | Possible duplicate registration paths |
| Entity renderers | Client entity registration unclear vs `ModEntities` server registration |
| `debug/AIDebugLoggerTest.java` | Test harness, not production |
| `mixin/*` (except SpawnHelper + client WorldRenderer) | Not listed in mixin JSON |

**Duplicate / overlapping:**

- Multiple ritual paths (`RitualBlock` adds tension vs `RitualHandler` reduces global)
- Three storm flags: `TensionManager.stormActive`, chunk `stormActive`, `StormManager.stormActive`

---

## Registration reference (`UTDMod.onInitialize`)

```
ModBlocks, ModBlockEntities, ModItems, ModItemGroups, ModEntityAttributes
TensionEvents, TensionMobHooks, ExperimentCommands, ExperimentClientReportPacket
TensionSpawnEcology, UseCrystalPacket, DebugSpikeTensionPacket
TensionServerTick, SessionTelemetryExporter
```

**Client (`UTDModClient`):** `TensionSyncPacket`, `TensionHud`, `TensionKeybind`, client tick lambda.
