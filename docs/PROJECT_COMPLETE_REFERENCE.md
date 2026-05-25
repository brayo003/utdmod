# UTD Mod — Complete Project Reference (Paste-Ready)

**Purpose of this document:** Give a third party (human or AI) full context on what this Minecraft Fabric mod is, what it is trying to achieve, how it is built, every major parameter, and what is wired vs placeholder.

**Repository:** `new_world`  
**Mod ID:** `utdmod`  
**Minecraft:** 1.20.4  
**Loader:** Fabric 0.15.10 + Fabric API 0.92.1  
**Language:** Java 17  
**Main package:** `com.utdmod`

---

## 1. PROJECT VISION — WHAT WE ARE TRYING TO DO

### Core concept

**UTD (Unstable Tension Dimension / world tension)** is a Fabric mod that treats the world as a **coupled physical field**:

- **Local tension** — per-chunk scalar stored on the server, driven by player actions (mining, movement, combat, rituals).
- **Global tension** — single world scalar representing ambient instability; rises when many chunks are stressed, can trigger **global storm** mode.
- **Feedback loops** — local stress couples upward to global; global feeds back into local chunk dynamics; diffusion spreads stress to neighbors.

### Design goals (from code + `DesignRules.java`)

| Rule | Intent |
|------|--------|
| Player-bound tension | Tension originates from player/world activity, not arbitrary global timers |
| Distributed coupling | One mining hotspot should not instantly max global; uses region count + excess tension |
| Dual regimes | **Pressure** (calm): slow coupling + natural recovery. **Catastrophe** (storm): strong coupling + weak decay |
| Observability-first | Heavy logging (`[GLOBAL_FLOW]`, `[TENSION_STATE]`, CSV session export) for tuning |
| Client isolation | Client never reads server `TensionManager`; uses `TensionSyncPacket` → `TensionSyncState` |
| Controlled engineering | Test harness (`/utdtest`) + `docs/system_audit/` for balancing without guesswork |

### Player-facing fantasy

Mining and violence **damage the fabric of the chunk**. Sustained activity → chunk states **STRAINED → FRACTURED → DECOUPLED**. Regional and global fields can enter **storm**, with lightning, weather, audio, spawn ecology shifts, and corruption tiers. Rituals and items can reduce tension. The world should feel **locally dangerous before globally apocalyptic**.

### Current engineering phase

- Core simulation architecture is **implemented and stable** (do not rewrite).
- **Calibration/tuning** is active (coupling constants, thresholds, decay).
- **Test harness** validates scenarios automatically.
- Some content (wraith/serpent spawn from tension, inactive mixins) is **partial or disconnected**.

---

## 2. TECH STACK & BUILD

```
build.gradle       — Fabric Loom 1.4.6, MC 1.20.4, Java 17
fabric.mod.json    — entrypoints: UTDMod (server), UTDModClient (client)
utdmod.mixins.json — active: SpawnHelperMixin (server), WorldRendererMixin (client)
run/               — eula.txt, server.properties for ./gradlew runServer
```

**Compile excludes (inactive / broken):**  
`AIDebugLoggerTest.java`, `PiglinBrainMixin`, `VillagerEntityTradeMixin`, `CatEntityPhantomMixin`, `EndermanEntityMixin`, `LootTableMixin`, `RaidManagerMixin` (not in mixin JSON; wrong 1.20.4 targets)

---

## 3. REGISTRATION — WHAT ACTUALLY RUNS AT STARTUP

### `UTDMod.onInitialize()` (server)

```
ModBlocks, ModBlockEntities, ModItems, ModItemGroups
ModEntityAttributes
TensionEvents.registerEvents()
TensionMobHooks.register()
ExperimentCommands.register()          → /utdexp
ExperimentClientReportPacket.registerServer()
TensionSpawnEcology.register()
UseCrystalPacket.registerReceiver()
DebugSpikeTensionPacket.registerServerReceiver()
TensionServerTick.register()           → MAIN SIMULATION LOOP
SessionTelemetryExporter.register()    → logs/utd_sessions/*.csv
TestBootstrap.register()               → scenario scheduler
TestCommands.register()                → /utdtest
```

### `UTDModClient.onInitializeClient()` (client)

```
TensionSyncPacket.registerReceiver()
TensionHud.register()
TensionKeybind.register()
Client tick: smoothing, regional atmosphere, UTDAudioManager (every 38t), ExperimentClientReport (every 200t)
```

---

## 4. ARCHITECTURE — DATA FLOW (END TO END)

```
[PLAYER ACTION]
  mining break     → PlayerBlockBreakEvents → ChunkTensionManager.addMiningTension
  movement         → TensionServerTick (per player, dist²>0.01)
  combat kill      → ServerLivingEntityEvents.AFTER_DEATH → addSlaughterTension
  sleep            → reduce local 0.02/tick; block sleep if max(g,local)>1.12
  portal/bulk/enchant/deep → TensionEvents UseBlockCallback / tick hooks
  rituals/items    → RitualHandler, RitualBlock, consumables, warding crystal

[LOCAL FIELD — every 5 server ticks]
  TensionServerTick.tickChunkTension()
    diffusion D·neighborAvg (cutoff if all <0.12)
    nonlinear α·t² (×1.8 if chunk storm)
    saturation β·t³
    global feedback γ·g (only if g>0.02)
    decay λ·t·contaminationMul
    calm chunks: decay every tick slot; reinjection sparse (1/8)
    local storm if t>0.95 → lightning burst; clear if t<0.25
    ChunkTensionData.setLocalTension → state machine + NBT persist

[GLOBAL FIELD — every server tick]
  GlobalFieldCoupling.compute() → CouplingSplit (0 if no stressed chunks)
  TensionManager.tickGlobalDynamics()
    inflow = coupling × split.total + impulseBuffer
    decay + natural recovery (pressure only)
    nonlinear α·g²
    storm hysteresis: on >0.9, off <0.7
    corruption tiers from g

[PARALLEL SYSTEMS each tick]
  StormManager.tick() — regional ambience storm (separate flag from global storm)
  RegionDiagnosticsManager.refresh() — every 200t, 8×8 regions
  TensionSpawnEcology — spawn cull/boost via SpawnHelperMixin context
  TensionSyncPacket — every 20t to clients

[CLIENT]
  TensionSyncState.applySnapshot → smoothing → UTDAudioManager, overlay, HUD
```

---

## 5. STATE MACHINES

### A. Chunk states (`ChunkTensionData.ChunkState`)

| State | Enter when tension | Notes |
|-------|-------------------|--------|
| STABLE | below thresholds | default |
| STRAINED | t ≥ **T1 = 0.95** | |
| FRACTURED | t ≥ **T2 = 1.28** | smoke log placeholder |
| DECOUPLED | t ≥ **T3 = 3.0** | catastrophic local |

**Hysteresis:** `STATE_HYSTERESIS = 0.22` — must fall 0.22 below tier to downgrade.

**Important:** `getChunkState()` re-derives from tension if map stale. `setLocalTension` always writes `chunkStates`.

### B. Global storm (`TensionManager.stormActive`)

| Transition | Threshold |
|------------|-----------|
| ON | global > **0.9** |
| OFF | global < **0.7** |

Effects: lightning on players every 200t cooldown, exhaustion every 40t, catastrophe coupling regime.

### C. Chunk-local storm (`ChunkTensionData.stormActive` per chunk)

| ON | local > **0.95** |
| OFF | local < **0.25** |

Effects: lightning in chunk, extra nonlinear growth, storm drain 0.002/tick.

### D. Regional ambience storm (`StormManager` — **separate** boolean)

| ON | tension = max(g, regionalMax×**0.52**) > **0.85** and random 1/120 |
| OFF | tension < **0.10–0.20** (depends on regional max) |

Effects: cave/thunder sounds, weather nudge, drifts toward hotter 8×8 region.

### E. Global corruption tiers

| Tier | global ≥ | Name |
|------|----------|------|
| 0 | — | None |
| 1 | 0.8 | Subtle |
| 2 | 1.5 | Moderate |
| 3 | 2.5 | Severe |
| 4 | 4.0 | Catastrophic |

### F. Contamination memory (per chunk, slows recovery)

| Level | Set when t ≥ | Decay multiplier |
|-------|--------------|------------------|
| 1 | 1.15 | 0.68× |
| 2 | 1.35 | 0.38× |

Decays level when t < 0.42 (staggered tick).

### G. Regional ecology (`RegionDiagnosticsManager.EcoState`)

| State | Condition |
|-------|-----------|
| CALM | max ≤ 1.35 and avg ≤ 1.0 |
| STRAINED | avg > 1.0 |
| FRACTURED | max > 1.35 |

---

## 6. ALL TUNING PARAMETERS (NUMERIC REFERENCE)

### 6.1 Global — `TensionManager.java`

| Parameter | Value | Role |
|-----------|-------|------|
| T_MAX | 5.0 | hard cap |
| LAMBDA | 0.0034 | baseline global decay |
| NATURAL_RECOVERY | 0.00055 | extra decay in pressure regime (g>0.012) |
| ALPHA_PRESSURE | 0.00035 | g² reinjection calm |
| ALPHA_CATASTROPHE | 0.00115 | g² reinjection storm |
| STORM_THRESHOLD | 0.9 | global storm arm |
| STORM_HYSTERESIS | 0.7 | global storm release |
| COUPLING_PRESSURE | 0.82 | multiply chunk inflow when calm |
| COUPLING_CATASTROPHE | 1.62 | multiply chunk inflow when storm |
| impulseBuffer decay | ×0.88/tick | debug impulses fade |
| catastrophe lambda mult | ×0.22 | slower global decay in storm |
| CORRUPTION_THRESHOLDS | 0.8, 1.5, 2.5, 4.0 | tier boundaries |

**Equilibrium guard:** if discriminant of decay vs nonlinear < 0, global ×= 0.9.

### 6.2 Global coupling — `GlobalFieldCoupling.java`

| Parameter | Value | Role |
|-----------|-------|------|
| STRESS_BASELINE | 0.55 | chunks below don't contribute |
| WEIGHT_STRAINED | 1.0 | |
| WEIGHT_FRACTURED | 1.22 | |
| WEIGHT_DECOUPLED | 1.32 | |
| REGION_GAIN | 0.00175 | distributed inflow scale |
| REGION_EXPONENT | 0.88 | n^0.88 region factor |
| FRACTURE_PER_CHUNK | 0.00034 | bucket |
| STRAIN_PER_CHUNK | 0.00011 | bucket |
| STORM_PER_CHUNK | 0.00007 | bucket |

**Formula (stressed chunks only):**  
`total = REGION_GAIN × stressedN^0.88 × meanWeightedExcess + fracture/strain/storm buckets`  
If `stressedN == 0` → **total = 0** (fixes idle global drift).

### 6.3 Local chunk PDE — `TensionServerTick.java`

| Parameter | Value | Role |
|-----------|-------|------|
| D | 0.012 | diffusion |
| LAMBDA | 0.0006 | linear decay |
| ALPHA | 0.009 | quadratic growth |
| BETA | 0.055 | cubic saturation |
| GAMMA | 0.045 | global→local feedback |
| DIFFUSION_CUTOFF | 0.12 | no diffusion if all below |
| DECAY_ZERO_EPSILON | 1e-5 | clamp to zero |
| STORM_THRESHOLD | 0.95 | chunk lightning storm |
| STORM_END_THRESHOLD | 0.25 | clear chunk storm |
| STORM_DRAIN_RATE | 0.002 | drain while chunk storm |
| storm ALPHA mult | ×1.8 | |
| calm sparse stride | 8 | CPU skip for reinjection only |

**Movement amounts (per tick when moving):**

| Action | Delta |
|--------|-------|
| walk | 0.0015 |
| sprint | 0.0022 |
| swim | 0.002 |
| elytra | 0.0035 |
| nether | ×1.25 |

**Tick cadence:**

| Interval | Action |
|----------|--------|
| every 1t | movement, global dynamics, StormManager |
| every 5t | tickChunkTension |
| every 20t | GLOBAL_FLOW log, TensionSyncPacket, weather secondary |
| every 40t | player action bar status |
| every 200t | REGION log, global storm lightning batch |
| every 400t | cumulative source totals stdout |

### 6.4 Mining — `ChunkTensionManager.addMiningTension`

| Block class | baseAmount |
|-------------|------------|
| stone | 0.042 |
| deepslate (non-ore) | 0.048 |
| generic ore | 0.052 |
| coal | 0.056 |
| deepslate ore | 0.078 |
| emerald | 0.09 |
| diamond | 0.11 |
| ancient debris | 0.14 |
| other | 0.008 |

**Escalation:** `recentMiningRate` += 1 per break; applied × `(1 + rate×0.12)`.

### 6.5 Combat — `addSlaughterTension`

| Target | amount |
|--------|--------|
| default mob | 0.015 |
| villager | 0.06 |
| iron golem | 0.08 |
| dragon/wither | 0.2 |

### 6.6 Events — `TensionEvents.java`

| Event | Value |
|-------|-------|
| NETHER_PORTAL_TENSION | 0.05 |
| SLEEP_LOCAL_RELIEF | 0.02/tick |
| BULK_BUILD_TENSION | 0.01 (30 blocks / 5s) |
| ENCHANTING_TENSION | 0.01 |
| DEEP_MINING_TENSION | 0.0015 / 200t (y<20) |
| sleep block | max(g,local) > 1.12 |

### 6.7 Rituals — `RitualHandler.java`

| Ritual | global reduction |
|--------|------------------|
| performRitual | 0.5 |
| performWardingRitual | 0.3 |
| performStormCalmingRitual | 0.8 |

`canPerformRitual`: requires `!TensionManager.isStormActive()`.

**RitualBlock.onUse:** +0.03 **local** (raises tension, not lowers).

### 6.8 Consumables — `TensionConsumableItem`

| Item | Effect |
|------|--------|
| volatile_insight_potion | +0.05 global, night vision |
| hushed_lure | −0.02 self global, −0.01 per player <32m, blindness |
| kinetic_phial | speed only (tension lock message — not fully implemented) |

### 6.9 Warding crystal — `TensionServerTick.runWeatherAndRitualSecondary`

Held in main hand + global > 0.5 → **−0.2 global per 20t**.

### 6.10 Weather side effects (global secondary)

| global cross | Effect |
|--------------|--------|
| >0.62 | chance rain |
| >1.0 | forced rain / clear at 1.0 |
| >0.7 | 10% XP drip |

### 6.11 StormManager (regional)

| Parameter | Value |
|-----------|-------|
| regional blend | max(g, localMax×0.52) |
| start | >0.85, 1/120 random |
| end cut | 0.10 if regional max>1.35 else 0.20 |

### 6.12 Ecology — `TensionSpawnEcology`

| Eco | passiveMod | hostileMod (day/night) |
|-----|------------|------------------------|
| STRAINED | 0.65 cull | 1.40 / 1.55 |
| FRACTURED | 0.30 cull | 2.20 / 2.45 |

Extra hostile pack spawn in FRACTURED regions (~18% every 220t).

### 6.13 Hostile buffs — `TensionMobHooks`

| Chunk state | speed + | follow range + |
|-------------|---------|----------------|
| STRAINED | 0.03 | 3.0 |
| FRACTURED/DECOUPLED | 0.06 | 6.0 |

### 6.14 Session fatigue — `PlayerStateIntegrator`

After **72000** ticks (~1h): every **1200** ticks add `0.00015 × hours` local.

### 6.15 Client smoothing — `TensionSyncState`

| Parameter | Value |
|-----------|-------|
| smooth global α | 0.07 |
| smooth local α | 0.11 |
| perceivedTension | max(smoothedG, smoothedL×0.92) |

### 6.16 Diagnostic log ladder — `DiagnosticLogs` (approximate for EVENT_EFFECT)

| Label | tension range |
|-------|---------------|
| CALM | <0.7 |
| STRAINED | 0.7–0.95 |
| FRACTURED | ≥0.95 |

(Note: chunk **state** machine uses T1=0.95 for STRAINED — aligned at high end.)

---

## 7. PERSISTENCE

| Data | Stored? | Location |
|------|---------|----------|
| Per-chunk local tension, state, mining rate, contamination, storm flag | **YES** | World save `chunk_tension_data` NBT |
| Global tension | **NO** | Resets on server restart |
| stormActive global | **NO** | Recalculated from global each tick |
| Session/scenario CSV | **YES** | `<gameDir>/logs/utd_sessions/` |

**NBT per chunk:** x, z, tension, state ordinal, miningRate, cPeak, cLv, (stormActive read but not written in current writeNbt — verify if bug).

---

## 8. NETWORKING

| Packet | ID | Direction | Payload |
|--------|-----|-----------|---------|
| TensionSyncPacket | utdmod:tension_sync | S→C | global, storm, local, regionAvg, regionMax |
| UseCrystalPacket | utdmod:use_crystal | C→S | crystal use |
| DebugSpikeTensionPacket | utdmod:debug_tension_spike | C→S | debug spike |
| ExperimentClientReportPacket | utdmod:exp_client_feel | C→S | perceived, audio, overlay, hostiles |

---

## 9. LOGGING & TELEMETRY

### Terminal tags

| Tag | Cadence | Source |
|-----|---------|--------|
| [GLOBAL_FLOW] | 20t | TensionManager |
| [REGION] | 200t | RegionDiagnosticsManager |
| [TENSION_STATE] | on chunk state change | ChunkTensionData |
| [EVENT_EFFECT] | per player action | TensionLogger |
| [TENSION_TRACE] | per action | TensionTraceLogger |
| [TRACE] | legacy one-liner | TensionTraceLogger |
| [STORM] / [STORM_END] | regional storm | StormManager |
| [RECOVERY] | contamination cool | TensionServerTick |
| [SPAWN_ECOLOGY] | throttled | TensionSpawnEcology |
| [TEST1-4] | experiments | ExperimentTelemetry |
| [UTDMod] | status 40t | TensionServerTick |

### Session CSV — `SessionTelemetryExporter`

Path: `logs/utd_sessions/session_YYYY-MM-DD_HH-mm-ss.csv`  
Summary: `session_*_summary.txt` on stop.

Columns: tick, event_type, player, chunk_x/z, local/global before/after, deltas, states, storm_active, corruption_tier, detail.

---

## 10. COMMANDS

### `/utdtest` (op level 2) — simulation harness

| Subcommand | Purpose |
|------------|---------|
| list | List scenarios |
| run \<scenario\> | Run scripted test |
| status | Active scenario tick/phase |
| abort | Cancel |
| report | Last PASS/FAIL report |
| telemetry on\|off | Scenario CSV |

**Scenarios:**

| ID | Goal | Duration hint |
|----|------|---------------|
| idle_drift | No activity → global/local stay ~0 | 5000t |
| single_chunk_fracture | One chunk peaks strained+ without global storm | ~650t |
| multi_chunk_contamination | 3×3 mining → regional/global warning | ~long |
| post_storm_recovery | Mine → decay → ritual calming | long |
| ritual_mitigation | g=0.75 → ritual −0.5 | short |

**Test region:** chunk **(10240, 10240)** — far from spawn.  
**Mining in tests:** `TestMiningBridge` — same code path as real block break, no physical block break.

### `/utdexp` — experiment timing

| Subcommand | Purpose |
|------------|---------|
| anchor | Set world tick anchor |
| origin / origin_here | TEST2 spread origin |
| reset | Reset experiment telemetry |

---

## 11. ITEMS & BLOCKS

| Registry name | Class | Effect |
|---------------|-------|--------|
| signal_probe | SignalProbeItem | probe tool |
| warding_crystal | WardingCrystalItem | hold to drain global |
| ritual_block | RitualBlock + BlockEntity | use → +0.03 local |
| tension_stabilizer | RitualStabilizerItem | hand ritual sink |
| volatile_insight_potion | TensionConsumableItem | +0.05 global |
| hushed_lure | TensionConsumableItem | area calm |
| kinetic_phial | TensionConsumableItem | speed only |

---

## 12. ENTITIES (partial)

| Entity | Registered | Spawn from tension? |
|--------|------------|---------------------|
| tension_wraith | ModEntities | **NO** — `TensionManager.triggerWraithEvent` empty |
| tension_serpent | ModEntities | **NO** — `triggerSerpentEvent` empty |

Renderers/models exist on client; AI goals exist but limited wiring.

---

## 13. COMPLETE FILE INVENTORY (135 Java files)

### 13.1 CORE — DO NOT REWRITE (simulation spine)

| File | Role |
|------|------|
| `core/TensionManager.java` | Global scalar ODE, storm, corruption, regimes |
| `core/TensionServerTick.java` | Main server tick orchestrator, chunk PDE, movement |
| `core/GlobalFieldCoupling.java` | Chunk→global distributed inflow |
| `core/CouplingSplit.java` | Coupling decomposition DTO |
| `core/TensionActivityLedger.java` | Per-tick mining/move/combat/ritual sums for logs |
| `core/TensionTraceClock.java` | Server tick counter for traces |
| `core/TensionTraceLogger.java` | [TRACE] / [TENSION_TRACE] stdout |
| `core/TensionEvent.java` | Log event record |
| `core/RegionDiagnosticsManager.java` | 8×8 region aggregates, hostile bias |

### 13.2 TENSION — local field

| File | Role |
|------|------|
| `tension/ChunkTensionData.java` | Per-chunk maps, state machine, NBT, contamination |
| `tension/ChunkTensionManager.java` | Public API: mining, combat, add/reduce |

### 13.3 EVENTS

| File | Role |
|------|------|
| `event/TensionEvents.java` | Block break, death, sleep, portal, bulk, deep layer |
| `event/TensionMobHooks.java` | Hostile attribute buffs in stressed chunks |
| `event/LivingEntityDamageEvents.java` | Custom damage event bus |

### 13.4 STORM / RITUAL

| File | Role |
|------|------|
| `storm/StormManager.java` | Regional ambience storm (separate from global) |
| `ritual/RitualHandler.java` | Global tension reduction rituals |
| `block/RitualBlock.java` | Block use +0.03 local |
| `block/RitualBlockEntity.java` | BE stub |

### 13.5 DIAGNOSTICS / TELEMETRY

| File | Role |
|------|------|
| `diag/DiagnosticLogs.java` | All structured stdout blocks |
| `telemetry/SessionTelemetryExporter.java` | Session CSV |
| `tick/TensionLogger.java` | Action → trace + EVENT_EFFECT + CSV hooks |
| `experiment/ExperimentTelemetry.java` | TEST1-3 timing |
| `command/ExperimentCommands.java` | /utdexp |

### 13.6 ECOLOGY

| File | Role |
|------|------|
| `ecology/TensionSpawnEcology.java` | Spawn cull/boost, extra packs |
| `mixin/SpawnHelperMixin.java` | Push spawn context for ecology |

### 13.7 CLIENT (feel layer)

| File | Role |
|------|------|
| `client/UTDModClient.java` | Client entry |
| `client/TensionSyncState.java` | Synced mirror + smoothing |
| `client/audio/UTDAudioManager.java` | Layered ambient sounds |
| `client/visual/TensionScreenOverlay.java` | Screen overlay |
| `client/visual/ClientRegionalAtmosphere.java` | Regional visual feel |
| `client/ui/TensionHud.java` | HUD |
| `client/ClientFeelingCounters.java` | audio/overlay counters for TEST4 |
| `client/TensionKeybind.java` | Keybind |
| `client/config/TensionOverlayConfig.java` | Overlay config |

### 13.8 NETWORK

| File | Role |
|------|------|
| `network/TensionSyncPacket.java` | Main sync |
| `network/UseCrystalPacket.java` | Crystal |
| `network/DebugSpikeTensionPacket.java` | Debug |
| `network/ExperimentClientReportPacket.java` | TEST4 client→server |

### 13.9 TEST HARNESS (`com.utdmod.test`)

| File | Role |
|------|------|
| `test/TestCommands.java` | /utdtest |
| `test/ScenarioManager.java` | Registry, single active runtime |
| `test/ScenarioRuntime.java` | Tick-driven action state machine |
| `test/ScenarioScheduler.java` | END_SERVER_TICK hook |
| `test/ScenarioContext.java` | Per-run context, metrics |
| `test/TestBootstrap.java` | Reset + chunk preload |
| `test/TestMiningBridge.java` | Simulated mining path |
| `test/scenarios/*.java` | 5 scenarios |
| `test/actions/*.java` | Mine, Wait, Teleport, Ritual, Snapshot, Phase, Event |
| `test/assertions/*.java` | Peak/end assertions |
| `test/telemetry/*.java` | Scenario CSV + metrics |
| `test/report/*.java` | PASS/FAIL reports |

### 13.10 REGISTRY

| File | Role |
|------|------|
| `registry/ModBlocks.java` | ritual block |
| `registry/ModItems.java` | items |
| `registry/ModEntities.java` | wraith, serpent |
| `registry/ModEntityAttributes.java` | attributes |
| `registry/ModBlockEntities.java` | ritual BE |
| `registry/ModItemGroups.java` | creative tab |
| `registry/ModSounds.java` | sounds |

### 13.11 DEAD / DISCONNECTED (do not assume these run)

| File | Why dead |
|------|----------|
| `tick/OptimizedTickHandler.java` | never registered |
| `tick/CompositeTickHandler.java` | never registered |
| `tick/TickLogger.java` | never registered |
| `WorldgenHooks.java` | never registered |
| `signals/TensionHUD.java` | stub |
| `signals/SmallSignal.java` | stub |
| `signals/PlayerStateTension.java` | wrong package / stub |
| `player/PlayerStateTension.java` | stub |
| `client/UTDModClientInitializer.java` | not in fabric.mod.json |
| `client/overlay/TensionMeterOverlay.java` | not registered |
| `client/UTDSoundTickHandler.java` | unused |
| `sound/UTDSoundManager.java` | unused (use UTDAudioManager) |
| `UTDProfiler.java` | no callers |
| `config/UTDConfig.java` | not loaded |
| `core/TensionView.java` | unused |
| `spec/DesignRules.java` | documentation constants only |
| `mixin/PiglinBrainMixin.java` etc. | excluded from compile, not in JSON |
| `TensionManager.triggerWraithEvent/triggerSerpentEvent` | empty methods |

---

## 14. THREE STORM SYSTEMS (CRITICAL DISTINCTION)

| System | Flag | Trigger | Purpose |
|--------|------|---------|---------|
| Global storm | `TensionManager.stormActive` | g>0.9 / g<0.7 | Catastrophe regime, lightning, exhaustion |
| Chunk storm | `ChunkTensionData.stormActive` map | local>0.95 | Lightning in chunk |
| Regional storm | `StormManager.stormActive` | regional tension>0.85 | Sounds, weather, drift |

These are **independent** booleans — can be out of sync.

---

## 15. DOCUMENTATION INDEX (`docs/system_audit/`)

| File | Contents |
|------|----------|
| SYSTEM_OVERVIEW.md | Gameplay loop, tick flow, lifecycles |
| PIPELINE_TRACE.md | Step-by-step causal chains |
| CRITICAL_FILES.md | Stable vs tune vs dead |
| TUNING_SURFACE.md | All parameters + safe ranges |
| STATE_MACHINE.md | All transitions |
| DATAFLOW_DIAGRAM.txt | ASCII graph |
| LOGGER_AUDIT.md | All log tags |
| SESSION_EXPORT_SYSTEM.md | CSV export spec |
| TEST_HARNESS.md | /utdtest documentation |

---

## 16. HOW TO RUN & DEMO

```bash
./gradlew compileJava
./gradlew runServer   # requires run/eula.txt (eula=true)
# In game (op):
/utdtest run single_chunk_fracture
/utdtest report
# Watch console: [GLOBAL_FLOW], [TENSION_STATE]
# Files: logs/utd_sessions/
```

**Good demo flow for stakeholder:**

1. `/utdtest run idle_drift` — prove calm world stays calm  
2. `/utdtest run single_chunk_fracture` — prove local catastrophe without global apocalypse  
3. Mine manually in survival — show [EVENT_EFFECT] lines  
4. Show `logs/utd_sessions/session_*.csv` in spreadsheet  

---

## 17. KNOWN ISSUES & TUNING STATUS

| Issue | Status |
|-------|--------|
| Idle chunk floor ~0.2 | **Fixed** — calm decay every PDE slot, diffusion cutoff 0.12 |
| State STABLE at high tension | **Fixed** — always sync chunkStates + derive on read |
| Global too hot / too cold | **Tuning** — adjust REGION_GAIN, COUPLING_*, NATURAL_RECOVERY |
| Ritual block adds local instead of calming | **Design quirk** — use RitualHandler for calm |
| Global not persisted | **By design** currently |
| Wraith/serpent not spawned from tension | **Not implemented** |
| Inactive mixins | **Excluded** until remapped |
| kinetic_phial lock | **Message only**, not enforced |

---

## 18. WHAT NOT TO DO (ENGINEERING RULES)

1. **Do not rewrite** `ChunkTensionData`, `GlobalFieldCoupling`, `TensionManager` tick structure without explicit architecture approval.  
2. **Do not bypass** event path for tuning (use parameters).  
3. **Do not read** `TensionManager` on client.  
4. **Do not delete** dead code without audit — mark disconnected only.  
5. **Tune one knob family per session**; compare CSV summaries.  
6. **Use peak assertions** in harness — end-state after recovery is not the escalation metric.

---

## 19. GLOSSARY

| Term | Meaning |
|------|---------|
| Local tension | Per-chunk scalar 0–5 |
| Global tension | Single world scalar 0–5 |
| Pressure regime | Calm global: slow coupling + natural recovery |
| Catastrophe regime | Storm or g≥0.9: fast coupling + weak decay |
| Coupling | Chunk stress → global inflow per tick |
| GAMMA feedback | Global → local push each chunk tick |
| Hysteresis | State/global storm don't flip instantly on small drops |
| perceivedTension | Client smoothed max(global, local×0.92) |

---

*End of complete reference. Generated from codebase inspection. For line-level changes, see git and `docs/system_audit/`.*
