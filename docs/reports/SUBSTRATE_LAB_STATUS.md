> **[SIMULATION LAB — NOT MINECRAFT RUNTIME]**
> This document describes the `simulation_lab/` standalone Java simulation, not the Minecraft mod.
> Data, metrics, and conclusions here refer to `GridSimulationV2` grid runs, not server ticks.
> For runtime telemetry from the Minecraft mod, see `analysis/UTD_TELEMETRY_REPORT.md`.

# SUBSTRATE_LAB_STATUS

> AUTO-GENERATED: This file is the canonical laboratory status document. Do not edit by hand. All implementations or refactors that change substrate math, storage, update order, visualizations, or recorder mappings must update this document programmatically.

Generated: 2026-07-01

---

## Section 1: Mathematical Model Inventory

For each mathematical *state variable* present in the codebase as of this snapshot, the table below records symbol, meaning, storage, update function, tick rate, dependencies, output consumers, and whether the variable currently exists inside the Minecraft runtime.

- Activation

```
State VariableNameSymbol: Activation (local tension)
Meaning: Local substrate activation/tension value per chunk (unitless scalar)
Units: unitless (dimensionless tension)
Current Storage Location: `com.utdmod.tension.ChunkTensionData.localTensions` (Map<ChunkPos, Double>)
Current Update Function: `ChunkTensionData.setLocalTension` / `ChunkTensionData.addLocalTension`; long-run dynamics updated in `TensionServerTick.tickChunkTension()`
Tick Rate: sparse per-chunk update called from `TensionServerTick` every 5 server ticks (tickChunkTension invoked when tickCounter % 5 == 0); per-chunk stride scheduling reduces effective per-chunk frequency
Dependencies: neighbor tensions (diffusion), contamination decay multiplier, global tension feedback, local storm drain, mining/perturbation injections
Output Consumers: RegionManager (region detection), ExperimentTelemetry, DiagnosticLogs, TensionSyncPacket, ResearchObserver, client HUD/visualization (via ResearchSyncPacket/TensionSync), bots via ResearchBotManager perturbations
Currently Used In Minecraft?: YES
```

- Chunk State

```
State VariableNameSymbol: ChunkState (enum)
Meaning: Discrete chunk state (STABLE, STRAINED, FRACTURED, DECOUPLED)
Units: categorical
Current Storage Location: `com.utdmod.tension.ChunkTensionData.chunkStates` (Map<ChunkPos, ChunkState>)
Current Update Function: `ChunkTensionData.calculateState()` called from `setLocalTension()`; transitions logged and effects via `triggerStateTransitionEffects()`
Tick Rate: updated whenever local tension is set/changed (immediate) and maintained across ticks
Dependencies: local tension value, hysteresis thresholds
Output Consumers: ExperimentTelemetry.onChunkStateTransition, DiagnosticLogs, StormManager triggers, client visuals (via region summaries and per-chunk storm flag)
Currently Used In Minecraft?: YES
```

- Contamination Peak (memory)

```
State VariableNameSymbol: ContaminationPeak (cPeak)
Meaning: Historical peak tension for a chunk (used to compute memory/scar persistence)
Units: unitless
Current Storage Location: `com.utdmod.tension.ChunkTensionData.contaminationPeak` (Map<ChunkPos, Double>)
Current Update Function: `ChunkTensionData.updateContaminationMemory()` called from `setLocalTension()` whenever tension > 0
Tick Rate: updated on tension increases; decays/pruning via `tickContaminationMemory()` scheduled inside `TensionServerTick.tickChunkTension()` (slot-based, ~200t cadence per chunk)
Dependencies: current local tension
Output Consumers: DiagnosticLogs (recovery diagnostics), ResearchObserver (scar counting), pruning logic, visualization (scar overlays via ResearchObserver snapshot)
Currently Used In Minecraft?: YES
```

- Contamination Level (memory class)

```
State VariableNameSymbol: ContaminationLevel (cLv)
Meaning: Categorized memory intensity: 0 none, 1 medium, 2 heavy
Units: categorical
Current Storage Location: `com.utdmod.tension.ChunkTensionData.contaminationLevel` (Map<ChunkPos, Byte>)
Current Update Function: `ChunkTensionData.updateContaminationMemory()` and `tickContaminationMemory()`
Tick Rate: change on tension events and decay slot checks (~200t per-chunk schedule)
Dependencies: contaminationPeak, local tension thresholds (1.15, 1.35)
Output Consumers: local decay multiplier (`getContaminationDecayMultiplier`), pruning TTLs, ResearchObserver (scar detection), visuals
Currently Used In Minecraft?: YES
```

- Recent Mining Rate

```
State VariableNameSymbol: RecentMiningRate
Meaning: Short-term mining activity magnitude for a chunk
Units: unitless rate (arbitrary per-tick magnitude)
Current Storage Location: `com.utdmod.tension.ChunkTensionData.recentMiningRate` (Map<ChunkPos, Double>)
Current Update Function: `ChunkTensionData.addMiningTension()` increments; `tickChunkTension()` decays it by factor every cycle
Tick Rate: increments on mining events; decays in `TensionServerTick.tickChunkTension()` every 5 server ticks (practical)
Dependencies: mining events via `ChunkTensionManager.addMiningTension` and ResearchPerturbationBridge.applyMiningPerturbation
Output Consumers: modifies applied mining tension multiplier, DiagnosticLogs, ResearchObserver
Currently Used In Minecraft?: YES
```

- Chunk Storm Flag

```
State VariableNameSymbol: StormActive_chunk
Meaning: Boolean marker whether the chunk is currently in a local storm state
Units: boolean
Current Storage Location: `com.utdmod.tension.ChunkTensionData.stormActive` (Map<ChunkPos, Boolean>)
Current Update Function: set in `TensionServerTick.tickChunkTension()` when local tension exceeds `STORM_THRESHOLD` and cleared when below `STORM_END_THRESHOLD`; `triggerLocalStorm()` spawns lightning
Tick Rate: evaluated in `tickChunkTension()` every 5 ticks
Dependencies: local tension, state transitions
Output Consumers: StormManager effects, lightning spawning, ResearchObserver, client sync
Currently Used In Minecraft?: YES
```

- Global Tension (G)

```
State VariableNameSymbol: Global Tension (G)
Meaning: World-level scalar summarizing aggregated substrate activation
Units: unitless
Current Storage Location: `com.utdmod.core.TensionManager.globalTension` (private static double)
Current Update Function: `TensionManager.tickGlobalDynamics(chunkCouplingInflow, ...)` called from `TensionServerTick` each server tick
Tick Rate: 1 server tick (updated each tick when TensionServerTick.onEndServerTick runs)
Dependencies: chunk coupling split (ambient/regional), impulseBuffer (ritual), nonlinear self-feedback, decay LAMBDA
Output Consumers: TensionSyncPacket (to clients), DiagnosticLogs, ResearchObserver triggers, world weather/rain effects, client HUD
Currently Used In Minecraft?: YES
```

- Impulse Buffer

```
State VariableNameSymbol: ImpulseBuffer
Meaning: Buffered global impulse for scripted events or debug spikes
Units: unitless
Current Storage Location: `com.utdmod.core.TensionManager.impulseBuffer`
Current Update Function: `TensionManager.addEvent()` or `tickGlobalDynamics()` decays it by factor 0.88 per tick
Tick Rate: modified when events call `addEvent()`; decays each tick inside `tickGlobalDynamics`
Dependencies: Event sources (debug, rituals)
Output Consumers: adds to global tension inflow
Currently Used In Minecraft?: YES (legacy / debug pathways present)
```

- Region-level aggregates (per region)

```
State VariableNameSymbol: Region.average, Region.max, Region.chunkCount, Region.centroidX/Z, Region.maturity, Region.potential
Meaning: Connected-component aggregates used for regional coupling and diagnostics
Units: unitless (averages of activation); centroid in chunk coordinates
Current Storage Location: `com.utdmod.core.Region` instances created/managed by `RegionManager.regions`
Current Update Function: RegionManager.refresh(server, tick) constructs Regions from active chunk set; Region.updateMaturity(tick) advances maturity
Tick Rate: RegionManager.refresh called every 20 ticks (tickCounter % 20 == 0)
Dependencies: Chunk activations, chunk states, storm counts, contamination memory
Output Consumers: TensionManager coupling computation, DiagnosticLogs, ResearchObserver, client sync (region metadata via TensionSyncPacket)
Currently Used In Minecraft?: YES
```

- Region State / Contribution

```
State VariableNameSymbol: RegionState (enum) / Region.getContribution()
Meaning: Discrete category of a region (DORMANT, GROWING, STRAINED, FRACTURED, ANCIENT) and contribution scalar used by global coupling
Units: categorical / unitless
Current Storage Location: `com.utdmod.core.Region.getState()` computed from Region fields; contribution via `Region.getContribution()`
Current Update Function: computed where needed; maturity updated in `Region.updateMaturity`
Tick Rate: effectively at RegionManager.refresh cadence (20 ticks)
Dependencies: Region aggregates (average tension, fractured density, maturity)
Output Consumers: client visuals (region presentation), TensionManager coupling, DiagnosticLogs
Currently Used In Minecraft?: YES
```

- Activity ledger (movement/mining/combat/ritual)

```
State VariableNameSymbol: A_mov, A_mine, A_comb, A_rit
Meaning: Per-tick accumulated magnitudes used for global flow diagnostics (not directly applied to G)
Units: unitless
Current Storage Location: `com.utdmod.core.TensionActivityLedger` (static doubles)
Current Update Function: `TensionActivityLedger.reset()` at tick start; `addMovement/addMining/addCombat/addRitual` accumulate during player/event handling; consumed in `TensionManager.tickGlobalDynamics()` for logging
Tick Rate: reset per server tick; sampled in tickGlobalDynamics diagnostics (20-tick logging)
Dependencies: player actions, event hooks
Output Consumers: Diagnostic logs, ResearchObserver for composite signals
Currently Used In Minecraft?: YES
```

- Perturbation history (recorded events)

```
State VariableNameSymbol: PerturbationHistory
Meaning: Bounded FIFO of recorded substrate perturbations with tick/time/position/type
Units: logged records
Current Storage Location: `com.utdmod.research.PerturbationRecorder.history` (Deque<SubstratePerturbation>)
Current Update Function: `PerturbationRecorder.record()` called by ResearchPerturbationBridge on every perturbation injection
Tick Rate: append on events; size bounded by MAX_HISTORY (2048)
Dependencies: event handlers (mining, movement, forestry, combat, portals, bots)
Output Consumers: research dump, export, offline analysis
Currently Used In Minecraft?: YES
```

- Derived transport field (not stored persistently)

```
State VariableNameSymbol: Transport vector (T_x, T_z) [derived]
Meaning: Local estimate of directional flow computed from neighbor tension gradients (screen-space visualization uses aggregated values)
Units: unitless vector per chunk (chunk tension difference per neighbor)
Current Storage Location: transient computations in `ResearchObserver.detectPhenomena()` (not stored globally) and sent in `ResearchSnapshot`
Current Update Function: computed in ResearchObserver tick from `ChunkTensionData.getLocalTension(neighbor)` differences
Tick Rate: ResearchObserver.tick invoked every 40 ticks (tickCounter % 40 == 0)
Dependencies: chunk tensions
Output Consumers: ResearchSyncPacket (client visualization arrows), ResearchObserver analytics
Currently Used In Minecraft?: PARTIAL (computed on server and sent as aggregated snapshot, but no persistent per-chunk vector field is stored)
```

- Field depth (derived)

```
State VariableNameSymbol: FieldDepth (derived measure)
Meaning: Aggregate measure of field depth/penetration (prototype: mean activation used as proxy)
Units: unitless
Current Storage Location: computed in `ResearchObserver` as meanActivation and sent in `ResearchSnapshot`
Current Update Function: ResearchObserver.tick aggregation
Tick Rate: ResearchObserver.tick every 40 ticks
Dependencies: chunk tensions, region sizes
Output Consumers: ResearchSyncPacket, client depth visualization (simple outline)
Currently Used In Minecraft?: PARTIAL (prototype calculation present; not a faithful port from simulation lab unless additional fields added)
```

- Experiment telemetry anchors and markers

```
State VariableNameSymbol: Experiment anchors (anchorWorldTick, test2Origin, TEST1 map)
Meaning: Bookmarks and per-chunk event markers used for targeted tests and diagnostics
Units: tick/coordinates
Current Storage Location: `com.utdmod.experiment.ExperimentTelemetry` (static fields/test maps)
Current Update Function: ExperimentCommands handlers and ExperimentTelemetry listeners (onChunkStateTransition, tick)
Tick Rate: anchor set by commands; telemetry hooks invoked on transitions and on 200-tick cadence
Dependencies: chunk state transitions
Output Consumers: DiagnosticLogs, ResearchObserver
Currently Used In Minecraft?: YES
```

---

## Section 2: Equation Coverage Matrix

Legend columns: Standalone (simulation_lab), Minecraft Runtime, Visualized (client), Logged (diagnostics/recorder), Replay/Tested (has deterministic test/replay integration)

```
Equation                  Standalone  Minecraft  Visualized  Logged  Replay/Tested
Activation (local tension) ✅          ✅         PARTIAL     ✅       PARTIAL
Memory (contamination)    ✅          ✅         PARTIAL     ✅       PARTIAL
Transport (vector field)  ✅          PARTIAL   PARTIAL     PARTIAL  PARTIAL
Global coupling (G)       ✅          ✅         PARTIAL     ✅       NO
Region aggregates         ✅          ✅         PARTIAL     ✅       NO
Storm dynamics            ✅          ✅         PARTIAL     ✅       NO
Perturbation recording    ✅          ✅         NO          ✅       YES
Field depth (derived)     ✅          PARTIAL   PARTIAL     PARTIAL  NO
Oscillation/wave detection✅          PARTIAL   NO          PARTIAL  NO
```

Notes:
- "Standalone" refers to the `simulation_lab` reference implementation. Most core equations exist there.
- "Minecraft" indicates a live implementation exists in the mod codebase (may be distributed across classes and mixins).
- "Visualized" marks whether an interactive client visualization currently exists (we implemented prototype overlays for activation outline, memory scars, transport arrows, depth outline). Those are coarse and partial; per-chunk heatmaps, vector fields over world coordinates, and outlines projected onto the world remain to complete.

---

## Section 3: Runtime Mapping

For every major equation/field, exact mapping to code:

- Activation

```
Current source: simulation_lab GridSimulationV2 (reference)
Minecraft equivalent: `ChunkTensionData.localTensions` + `TensionServerTick.tickChunkTension()`
Required bridge: `ChunkTensionManager` / `ResearchPerturbationBridge` for all event injections
Tick hook: `TensionServerTick.tickChunkTension()` (every 5 ticks scheduling)
Input: perturbations (mining/movement/forest/combat), neighbor tensions, global feedback
Output: region membership (RegionManager), client sync via TensionSyncPacket, ResearchObserver inputs
Status: IMPLEMENTED (live math in server tick)
```

- Memory (contamination)

```
Current source: simulation_lab memory modules
Minecraft equivalent: `ChunkTensionData.contaminationPeak` and `contaminationLevel` updated in `updateContaminationMemory()` and decayed in `tickContaminationMemory()`
Required bridge: none (embedded)
Tick hook: `tickChunkTension()` and per-slot decay (200-tick schedule)
Input: local tension events
Output: modifies local decay multiplier, informs ResearchObserver (scar counting), used in pruning
Status: IMPLEMENTED
```

- Transport

```
Current source: simulation_lab transport measurement routines
Minecraft equivalent: NOT persistently stored; derived on-demand in `ResearchObserver.detectPhenomena()` by computing neighbor tension gradients
Required bridge: either add a persistent per-chunk vector store (`ChunkTensionData.transport`) or continue computing aggregated snapshots in ResearchObserver
Tick hook: ResearchObserver.tick() (every 40 ticks)
Input: Chunk tensions
Output: `ResearchSnapshot` (transportX, transportZ) sent to clients for visualization
Status: PARTIAL (derived snapshots exist; no full per-chunk vector field stored)
```

- Global coupling (G)

```
Current source: simulation_lab global coupling module
Minecraft equivalent: `TensionManager.tickGlobalDynamics()` invoked from `TensionServerTick.onEndServerTick()`
Required bridge: `RegionManager.computeChunkCouplingSplit()` supplies chunk-based inflow
Tick hook: every server tick
Input: region contributions, ambient/regional splits
Output: `TensionManager.globalTension`, `TensionManager.stormActive`, DiagnosticLogs, client sync
Status: IMPLEMENTED
```

- Region detection

```
Current source: simulation_lab component lab
Minecraft equivalent: `RegionManager.refresh()` (connected component search over chunks thresholded by `UTDConfig.regionActiveTensionThreshold`)
Required bridge: Chunk tension map
Tick hook: every 20 ticks
Input: chunk activation map
Output: Region instances, DiagnosticLogs, ResearchObserver and client sync (region metadata)
Status: IMPLEMENTED
```

- Oscillation and wave detection

```
Current source: simulation_lab detection module
Minecraft equivalent: ResearchObserver heuristics (waveCount/oscillationCount); prototype only
Required bridge: ResearchObserver.compute and ResearchSyncPacket
Tick hook: ResearchObserver.tick() every 40 ticks
Input: aggregated activation/time series snapshots
Output: narrative events, ResearchSnapshot
Status: PARTIAL (heuristic detection implemented; formal detectors to be added)
```

---

## Section 4: Event Mapping (complete mapping)

Format columns: `Minecraft Event` ↓ `Substrate Perturbation` ↓ `Affected Equation` ↓ `Affected State Variables` ↓ `Visualization` ↓ `Recorder`

Partial mapping below (expand as new events are added):

- Player movement

```
Minecraft Event: Player moved / step/sprint/elytra
Substrate Perturbation: Movement perturbation
Affected Equation: Activation (local tension)
Affected State Variables: Activation (ChunkTensionData.localTensions), ActivityLedger.movement
Visualization: Heatmap/per-chunk activation (future); currently contributes to HUD/feel overlay
Recorder: PerturbationRecorder.record(SubstratePerturbation)
```

- Block break / mining

```
Minecraft Event: Block break (mining)
Substrate Perturbation: Mining perturbation
Affected Equation: Activation (local) + RecentMiningRate + Memory update
Affected State Variables: Activation, RecentMiningRate, ContaminationPeak/Level
Visualization: Scar/memory overlay (memory mode), activation increase (activation mode)
Recorder: PerturbationRecorder.record(SubstratePerturbation)
```

- Tree cut / forestry

```
Minecraft Event: Tree break / log removal
Substrate Perturbation: Forestry perturbation
Affected Equation: Activation (local), RecentMiningRate (smaller), Memory
Affected State Variables: Activation, RecentMiningRate, ContaminationPeak/Level
Visualization: activation + memory overlays
Recorder: PerturbationRecorder
```

- Combat (entity kill / slaughter)

```
Minecraft Event: Entity damage / kill
Substrate Perturbation: Combat perturbation
Affected Equation: Activation (local)
Affected State Variables: Activation, ActivityLedger.combat
Visualization: activation spike and local storm probability increase
Recorder: PerturbationRecorder
```

- Ritual / debug spikes

```
Minecraft Event: Ritual block usage or debug spike command
Substrate Perturbation: Ritual impulse (global or regional)
Affected Equation: Global coupling (impulseBuffer) and Activation (local, if localized ritual)
Affected State Variables: ImpulseBuffer, Activation, ActivityLedger.ritual
Visualization: HUD alert, region activation
Recorder: PerturbationRecorder / DiagnosticLogs
```

Notes: Every event routed through `ResearchPerturbationBridge` or `ChunkTensionManager` should append a `SubstratePerturbation` record.

---

## Section 5: Visualization Coverage

For every mathematical quantity: how to see it now, what the future answer should be, and current status.

- Activation
```
How do I see it? Current: small client HUD and region outlines; no full per-chunk heatmap overlay yet
Future answer: world-projected heatmap per chunk + per-block interpolation and opacity scaling
Status: PARTIAL
```

- Memory (contamination)
```
How do I see it? Current: semi-transparent purple full-screen tint (prototype) and scar count in HUD
Future answer: persistent per-chunk semi-transparent overlay (purple scar) rendered on world surfaces and in replay; time-decay animation
Status: PARTIAL
```

- Transport
```
How do I see it? Current: screen-space directional arrows (aggregate) driven by ResearchSnapshot transportX/Z
Future answer: vector field overlay projected onto world grid: arrows located at each active chunk, arrow length ~ flux magnitude, arrow color ~ sign/direction
Status: PARTIAL
```

- Global coupling (G)
```
How do I see it? Current: numeric HUD and feel overlay; storms trigger world weather
Future answer: timeline plots, realtime graph overlay, integration with dashboard
Status: IMPLEMENTED (numeric), VISUAL: PARTIAL
```

- Region outlines
```
How do I see it? Current: activation mode draws screen-centered bounding box for largest region only
Future answer: world-space polygonal outlines drawn on map, bounding polygon per region, clickable timeline markers
Status: PARTIAL
```

- Oscillation/wave detection
```
How do I see it? Current: narration lines in HUD and ResearchObserver logs when heuristics detect
Future answer: automated timeline markers with detected wave front speed/direction and replay camera jump
Status: PARTIAL
```

---

## Section 6: Laboratory Capability Checklist

Each item links to implementing classes (present in repository). Checkmark if at least a prototype exists.

```
[✓] Activation rendered -> ClientRegionPresentation.drawActivationOutline (partial)
[✓] Memory rendered -> ClientRegionPresentation memory tint (partial)
[✓] Transport rendered -> ClientRegionPresentation.drawTransportArrows (prototype)
[✓] Global coupling rendered -> TensionHud (numeric)
[✓] Field depth rendered -> ClientRegionPresentation.drawDepthOutline (prototype)
[ ] Oscillation detector -> ResearchObserver heuristics (prototype but needs formal detector) [PARTIAL]
[ ] Wave detector -> ResearchObserver wave heuristics (prototype) [PARTIAL]
[✓] Scar detector -> ResearchObserver scarCount (prototype)
[✓] Cluster detector -> RegionManager (connected components)
[ ] Boundary detector -> region outline polygonization not implemented (only bounding boxes)
[ ] Replay -> Not implemented (recording of full state trajectory required)
[ ] Timeline -> Not implemented (UI timeline widget)
[ ] Experiment browser -> Not implemented (commands exist for simple anchors)
[✓] Automatic narration -> ResearchObserver.addNarration
[ ] Report generation -> Not yet (Diagnostics exist; formal report exporter missing)
[ ] Comparison mode -> Not implemented (split-screen experimental harness missing)
[ ] Camera director -> Not implemented (client camera fly-to not implemented)
```

Class references: `ClientRegionPresentation`, `TensionHud`, `ResearchObserver`, `RegionManager`, `ChunkTensionData`, `TensionServerTick`, `TensionManager`, `PerturbationRecorder`.

---

## Section 7: Research Questions (catalog)

Each RQ is actionable and should map to an experiment in Section 8.

```
RQ-001 Does mining leave persistent substrate scars? Status: PARTIAL (memory logged, scars visualized crudely). Experiment: Mining Scar Test
RQ-002 Can transport create self-propagating waves? Status: PARTIAL (transport snapshots exist) Experiment: Wave Propagation Test
RQ-003 Do abandoned regions recover? Status: PARTIAL (contamination decay implemented) Experiment: Region Recovery Test
RQ-004 Do regions merge or repel at boundaries? Status: PARTIAL (RegionManager logs merges/destroyed) Experiment: Boundary Interaction Test
RQ-005 Are there attractors / oscillatory cycles? Status: UNTESTED (ResearchObserver heuristics exist) Experiment: Oscillation Detection Test
```

---

## Section 8: Experiment Catalogue (seed examples)

```
Experiment 001
Name: Mining Scar Test
World seed: 12345
Bot: Miner (ResearchBotManager type=miner)
Duration: 3 hours (in-game) / deterministic ticks
Question: Does mining generate persistent activation (memory) that survives 1 Minecraft day?
Expected measurements: scarCount time series; contaminationPeak per chunk; decay half-life
Status: Not run (prototype hooks present)

Experiment 002
Name: Wave Propagation Test
World seed: 12345
Bot: Mixed (miner + explorer) deterministic seed
Duration: 2 hours
Question: Do transport vectors form coherent propagating waves? Measure front speed and direction.
Expected measurements: waveCount, transportFlux, per-chunk transport vectors (future)
Status: Not run
```

---

## Section 9: Current Implementation Audit

For every claimed feature answer: Exists | Compiles | Connected | Actually Used | Verified

```
Activation (server math)         Exists:YES  Compiles:YES  Connected:YES  Actually Used:YES  Verified:PARTIAL (behavioral diagnostics present)
Activation (heatmap rendering)   Exists:PARTIAL Compiles:YES  Connected:PARTIAL Actually Used:PARTIAL Verified:NO
Memory (contamination)           Exists:YES  Compiles:YES  Connected:YES  Actually Used:YES  Verified:PARTIAL
Memory (visual scar rendering)   Exists:PARTIAL Compiles:YES  Connected:PARTIAL Actually Used:PARTIAL Verified:NO
Transport vector field (server)  Exists:PARTIAL Compiles:YES  Connected:PARTIAL Actually Used:PARTIAL Verified:NO
Transport visualization (client) Exists:PARTIAL Compiles:YES  Connected:YES  Actually Used:PARTIAL Verified:NO
Region detection (connected comp)Exists:YES  Compiles:YES  Connected:YES  Actually Used:YES  Verified:PARTIAL
Wave/oscillation detector       Exists:PARTIAL Compiles:YES  Connected:YES  Actually Used:PARTIAL Verified:NO
Perturbation recorder           Exists:YES  Compiles:YES  Connected:YES  Actually Used:YES  Verified:PARTIAL
Replay                          Exists:NO   Compiles:N/A  Connected:NO   Actually Used:NO   Verified:NO
Timeline UI                     Exists:NO   Compiles:N/A  Connected:NO   Actually Used:NO   Verified:NO
Dashboard export                Exists:PARTIAL Compiles:YES Connected:PARTIAL Actually Used:PARTIAL Verified:NO
```

Honest notes: "Exists:PARTIAL" means a prototype or scaffold exists but full functionality, integration, and verification are incomplete. Many features are present as server-side computations and logged, but the visual world-projection versions (per-chunk heatmaps, vector arrows in world coordinates, camera director) need additional implementation.

---

## Section 10: Next Required Work (concrete tasks, ordered)

1. Complete Mathematical Port (priority)
   - Identify every variable in `simulation_lab/GridSimulationV2` and match to the ones listed above. (Owner: Agent)
   - For each unmapped variable, add a server-side storage location (prefer `ChunkTensionData` or `Region` as appropriate). Implement `get/set` and persistence if long-lived.
   - Implement missing update equations inside `TensionServerTick.tickChunkTension()` with the exact update order used by the simulation (owner: agent).

2. Deterministic equivalence harness
   - Implement a test harness that can run `simulation_lab` and the Minecraft server loop for N ticks given identical RNG seeds and perturbation sequences; instrument to output per-variable time series and diff tolerances. (Owner: agent/test harness)

3. Per-chunk transport field
   - Add persistent per-chunk transport vector storage to `ChunkTensionData` and a clear API in `ChunkTensionManager` to update it from tick calculations; send full chunk-level vector snapshots to ResearchSync for client rendering. (Owner: agent)

4. Visual fidelity improvements
   - World-projection heatmap: render per-chunk quads with color from activation; implement client vertex buffer updates driven by ResearchSync chunk snapshots.
   - World vector arrows: render arrows at chunk centers pointing in transport direction and magnitude.
   - Region polygon outlines: from RegionManager.chunks produce border polygons and send boundaries to client.

5. Recorder & Replay
   - Extend `PerturbationRecorder` to optionally record full state snapshots (configurable frequency) and add a `ReplayManager` to step state forward/back.

6. Analytics & detectors
   - Formalize wave/oscillation detectors (signal processing on meanActivation/transportFlux), add unit tests and threshold calibration scripts.

7. Dashboard & timeline UI
   - Implement a client-side dashboard screen (in-game GUI) that lists experiments, seed, live metrics, timeline of narrative events, and playback controls.

8. Comparative runs
   - Add multi-run harness and split-screen playback to compare multiple bot types on identical seeds.

---

## Ownership / Automation guidance

- This file must be updated by the agent any time code touching the substrate math, storage, update order, or visualization changes. The agent will produce a single commit per change that updates this document.
- Recommended enforcement (future): add a Gradle check task `verifySubstrateStatus` that fails the build if `SUBSTRATE_LAB_STATUS.md` was not updated when classes in `com.utdmod.tension`, `com.utdmod.core`, `com.utdmod.research`, or `com.utdmod.client.visual` changed. (Not yet implemented — propose next.)

---

## Quick next action options

- I can (A) implement the Gradle verification task now so commits must update this file, or (B) run a precise inventory pass comparing `simulation_lab` variables to the runtime and auto-fill any unmapped fields. Which do you want me to do next?

---

(End of SUBSTRATE_LAB_STATUS.md)
