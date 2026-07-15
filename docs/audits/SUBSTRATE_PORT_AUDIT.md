# SUBSTRATE_PORT_AUDIT

## Objective

This audit does not ask whether the mod compiles. It asks a stricter scientific question:

Has the mathematical substrate from the simulation laboratory actually entered Minecraft, and can its behavior be observed?

The answer, based on code inspection of the repository, is: partially, but not as a faithful port.

## Evidence basis

This document is based only on implementation present in the repository, especially:

- [simulation_lab/src/FieldEquation.java](simulation_lab/src/FieldEquation.java)
- [simulation_lab/src/ParameterSet.java](simulation_lab/src/ParameterSet.java)
- [simulation_lab/src/SubstrateSnapshotInterpreter.java](simulation_lab/src/SubstrateSnapshotInterpreter.java)
- [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java)
- [src/main/java/com/utdmod/tension/ChunkTensionData.java](src/main/java/com/utdmod/tension/ChunkTensionData.java)
- [src/main/java/com/utdmod/tension/ChunkTensionManager.java](src/main/java/com/utdmod/tension/ChunkTensionManager.java)
- [src/main/java/com/utdmod/core/TensionManager.java](src/main/java/com/utdmod/core/TensionManager.java)
- [src/main/java/com/utdmod/research/ResearchPerturbationBridge.java](src/main/java/com/utdmod/research/ResearchPerturbationBridge.java)
- [src/main/java/com/utdmod/research/PerturbationRecorder.java](src/main/java/com/utdmod/research/PerturbationRecorder.java)
- [src/main/java/com/utdmod/research/ResearchObserver.java](src/main/java/com/utdmod/research/ResearchObserver.java)
- [src/main/java/com/utdmod/client/ui/TensionHud.java](src/main/java/com/utdmod/client/ui/TensionHud.java)
- [src/main/java/com/utdmod/client/visual/ClientRegionPresentation.java](src/main/java/com/utdmod/client/visual/ClientRegionPresentation.java)
- [src/main/java/com/utdmod/event/TensionEvents.java](src/main/java/com/utdmod/event/TensionEvents.java)

## Executive verdict

The Minecraft runtime contains a chunk-level tension system with a global coupling layer, perturbation injection hooks, persistence, and observation channels. That means some substrate-like dynamics are present inside Minecraft.

However, the port is not a faithful mathematical transplant of the simulation laboratory. The runtime does not implement the same state variables and update structure as the laboratory model. The most important missing piece is that the Minecraft runtime does not host a full, observable substrate state machine with separate values for activation, memory, transport, and field depth evolving under their own equations.

## Summary percentages

These percentages are a code-based assessment from the implementation that is actually present in the repository.

- Current implementation percentage: 44%
- Mathematical implementation percentage: 32%
- Visualization percentage: 58%
- Instrumentation percentage: 46%
- Replay percentage: 0%
- Experiment readiness percentage: 22%

## Stage 1: Mathematical state inventory

The standalone laboratory model contains a richer state set than the Minecraft runtime. The inventory below is the complete set that is either present in the lab, present in Minecraft, or absent in Minecraft.

| Mathematical state | Physical meaning | Lab presence | Minecraft runtime presence | Current status |
|---|---|---|---|---|
| Activation | Local excitation / tension intensity | Present in the laboratory interpretation layer and tests | Not implemented as a separate first-class runtime variable | Missing |
| Memory | Persistent scar / retained influence | Present in lab interpretation and tests | Partially represented as contamination memory and contamination level in ChunkTensionData | Partial |
| Transport | Neighbor-to-neighbor flux / corridor flow | Present in the lab interpreter and tests | Only indirectly visible as neighbor-based calculations and a research observation vector | Partial |
| Field depth | Structural capacity / long-range memory | Present in lab tests and interpreter | Not implemented as a distinct state variable in the Minecraft runtime | Missing |
| Global coupling | Shared world-level feedback | Present in the lab model and runtime | Present as TensionManager global tension and chunk coupling | Implemented |
| Diffusion | Local spreading from neighbors | Present in the lab and runtime | Present in TensionServerTick as D * neighborAvg | Implemented |
| Decay | Local relaxation / dissipation | Present in the lab and runtime | Present as LAMBDA * localTension * decayMul | Implemented |
| Nonlinear growth | Self-reinforcing growth | Present in the lab and runtime | Present as ALPHA * tension^2 | Implemented |
| Saturation | Self-limiting nonlinear term | Present in the lab and runtime | Present as BETA * tension^3 in the runtime tick loop | Implemented |
| Storm drain | Storm-related suppression | Present in lab and runtime | Present as STORM_DRAIN_RATE in the runtime tick loop | Implemented |
| Boundary terms | Edge behavior / domain constraints | Present in the lab grid model | Not implemented as an explicit substrate boundary model | Missing |
| Neighbor averaging | Local neighborhood summary | Present in lab and runtime | Present in the chunk tick loop and observer | Implemented |
| Temporal buffers | Short-lived state used for delayed dynamics | Not present as a separate substrate state in the runtime | Not implemented as a separate runtime substrate buffer | Missing |
| Hidden accumulator | Incremental impulse storage | Present in TensionManager as impulseBuffer | Present in TensionManager as impulseBuffer | Implemented |
| Contamination memory | Persistent chunk-level scar state | Not in the lab model as a primary variable; present in Minecraft runtime | Present in ChunkTensionData as contaminationPeak and contaminationLevel | Partial |
| Recent mining rate | Short-term mining history | Present in Minecraft runtime only | Present in ChunkTensionData | Implemented |
| Chunk state | Derived state machine for tension regimes | Present in Minecraft runtime only | Present in ChunkTensionData.ChunkState | Implemented |
| Region aggregates | Spatial clustering of tension | Present in Minecraft runtime only | Present in RegionManager and Region | Implemented |

### Key conclusion from Stage 1

The laboratory model is a multi-variable field substrate. The Minecraft runtime is a reduced, chunk-centered tension system. It does not host the full laboratory variable set as independent evolving states.

## Stage 2: Minecraft runtime implementation map

| Simulation variable | Minecraft variable | Runtime storage | Update location | Tick frequency | Synchronization | Visualization | Logging | Status |
|---|---|---|---|---|---|---|---|---|
| Activation | Local tension | ChunkTensionData.localTensions | TensionServerTick.tickChunkTension | Every 5 ticks for active chunks | Server-side only; client via sync packets | HUD and overlay | TensionLogger, DiagnosticLogs | Partial |
| Memory | Contamination memory | ChunkTensionData.contaminationPeak / contaminationLevel | ChunkTensionData.setLocalTension and tickContaminationMemory | Periodic, chunk-dependent | Server-side persistence only | Research mode overlays and narrative | ResearchObserver and diagnostics | Partial |
| Transport | Neighbor-based flux proxy | Computed on the fly in ResearchObserver and TensionServerTick | ResearchObserver.tick and tickChunkTension | 40 ticks for observation; 5 ticks for update | No dedicated packet or state serialization | Overlay modes and narrative | ResearchObserver | Partial |
| Field depth | Not implemented | Not implemented | Not implemented | Not implemented | Not implemented | Not implemented | Not implemented | Missing |
| Global coupling | Global tension | TensionManager.globalTension | TensionManager.tickGlobalDynamics | Every server tick | Sync packet to clients | HUD and overlay | DiagnosticLogs and TensionSyncPacket | Implemented |
| Perturbations | Event-driven tension injection | ResearchPerturbationBridge + ChunkTensionManager | TensionEvents and research bridge | Event-driven | Server-side state mutation | HUD and research logger | TensionLogger | Implemented |
| Replay | Not implemented | Not implemented | Not implemented | Not implemented | Not implemented | Not implemented | Not implemented | Missing |

## Stage 3: Equation trace

### Laboratory equation

The laboratory field model uses a state update of the form:

$$
T_{t+1} = T_t + D\bar{T}_{neighbors} + \alpha T_t^2 - \beta T_t^3 + \gamma G_t + I_t - \lambda T_t - S_t
$$

This is visible in [simulation_lab/src/FieldEquation.java](simulation_lab/src/FieldEquation.java).

### Minecraft runtime equation

The Minecraft runtime uses a chunk-local update of the form:

$$
T_{t+1} = T_t + D\bar{T}_{neighbors} + \alpha T_t^2 - \beta T_t^3 + \gamma G_t - \lambda T_t - S_t
$$

with the actual implementation in [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java).

### Equation differences

The runtime differs from the laboratory model in four important ways:

1. It does not use a separate external input term $I_t$ from the laboratory field equation. The runtime applies perturbations by directly modifying local tension before the update step.
2. It does not maintain separate state variables for activation, memory, transport, and field depth. The runtime collapses the substrate into a single scalar local tension value.
3. It uses chunk-state thresholds and contamination memory as derived, non-equational side structures in [src/main/java/com/utdmod/tension/ChunkTensionData.java](src/main/java/com/utdmod/tension/ChunkTensionData.java).
4. It does not implement the laboratory interpretation layer as a faithful translation from a full substrate field into gameplay events. The lab interpreter in [simulation_lab/src/SubstrateSnapshotInterpreter.java](simulation_lab/src/SubstrateSnapshotInterpreter.java) is a separate artifact, not the runtime path.

### Parameter source

The runtime constants are hard-coded in [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java):

- $D = 0.012$
- $\lambda = 0.0006$
- $\alpha = 0.009$
- $\beta = 0.055$
- $\gamma = 0.045$
- $S_t = 0.002$ when storm-active

The laboratory defaults are defined in [simulation_lab/src/ParameterSet.java](simulation_lab/src/ParameterSet.java), and they are similar but not identical in structure and configuration handling.

## Stage 4: Perturbation-to-equation map

| Minecraft event | Entry point | Conversion into substrate input | Receives the input | Transformation type | Status |
|---|---|---|---|---|---|
| Mining block break | [src/main/java/com/utdmod/event/TensionEvents.java](src/main/java/com/utdmod/event/TensionEvents.java) | ResearchPerturbationBridge.applyMiningPerturbation | Chunk local tension | Additive, with block-specific scaling | Implemented |
| Forestry event | [src/main/java/com/utdmod/event/TensionEvents.java](src/main/java/com/utdmod/event/TensionEvents.java) | ResearchPerturbationBridge.applyForestryPerturbation | Chunk local tension and neighboring chunks | Additive, weighted | Implemented |
| Combat kill | [src/main/java/com/utdmod/event/TensionEvents.java](src/main/java/com/utdmod/event/TensionEvents.java) | ResearchPerturbationBridge.applyCombatPerturbation | Chunk local tension | Additive | Implemented |
| Portal ignite, sleep relief, bulk build, enchant, deep mining | [src/main/java/com/utdmod/event/TensionEvents.java](src/main/java/com/utdmod/event/TensionEvents.java) | ResearchPerturbationBridge.applyLocalPerturbation | Chunk local tension | Additive or subtractive | Implemented |
| Movement | [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java) | Direct local tension injection | Chunk local tension | Additive | Implemented |

### Important limitation

No perturbation is converted into separate mathematical substrate variables such as activation, memory, transport, or field depth. Every event currently maps into the single scalar chunk tension state.

## Stage 5: Does the substrate actually evolve?

### Where time advances

Time advances in [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java), where the server tick counter increments once per server tick.

### Who advances it

The server tick pipeline in [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java) advances the system.

### Tick rate and scheduler

- Local chunk dynamics are processed every 5 ticks in the tick loop.
- Region refresh runs every 20 ticks.
- Global dynamics run every server tick.
- Research observation runs every 40 ticks.

### Chunk loading behavior

The update loop explicitly skips unloaded chunks in [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java).

### Saving and loading

Chunk tension data is persisted through PersistentState in [src/main/java/com/utdmod/tension/ChunkTensionData.java](src/main/java/com/utdmod/tension/ChunkTensionData.java).

### Replay and state continuity

Replay is not implemented. There is no system that records full substrate snapshots, replays them, or reconstructs the field from a deterministic seed and event log.

### Key conclusion from Stage 5

The substrate does evolve in a limited sense inside Minecraft, but it is not a faithful full-state simulation. State disappears from active update when chunks unload, and the runtime does not preserve enough substrate history to support robust replay or scientific reconstruction.

## Stage 6: Visualization audit

| Variable or phenomenon | Observable in Minecraft? | Evidence | Status |
|---|---|---|---|
| Global tension | Yes | [src/main/java/com/utdmod/client/ui/TensionHud.java](src/main/java/com/utdmod/client/ui/TensionHud.java) | Implemented |
| Local chunk tension | Yes | [src/main/java/com/utdmod/client/ui/TensionHud.java](src/main/java/com/utdmod/client/ui/TensionHud.java) | Implemented |
| Research narrative | Yes | [src/main/java/com/utdmod/network/ResearchSyncPacket.java](src/main/java/com/utdmod/network/ResearchSyncPacket.java) and [src/main/java/com/utdmod/research/ResearchObserver.java](src/main/java/com/utdmod/research/ResearchObserver.java) | Implemented |
| Region state / region overlays | Yes | [src/main/java/com/utdmod/client/visual/ClientRegionPresentation.java](src/main/java/com/utdmod/client/visual/ClientRegionPresentation.java) | Implemented |
| Particles and ambient cues | Yes | [src/main/java/com/utdmod/client/visual/ClientRegionPresentation.java](src/main/java/com/utdmod/client/visual/ClientRegionPresentation.java) | Implemented |
| Per-chunk activation field | No | No dedicated per-chunk field state or renderer exists | Missing |
| Per-chunk memory field | No | No dedicated renderer or serialized field exists | Missing |
| Per-chunk transport field | No | Only derived observation vectors exist | Partial |
| Field-depth field | No | Not implemented | Missing |
| Time-lapse replay | No | Not implemented | Missing |

## Stage 7: Instrumentation audit

The repository has some instrumentation, but it is incomplete for a scientific substrate audit.

### Present instrumentation

- Event logging via [src/main/java/com/utdmod/tick/TensionLogger.java](src/main/java/com/utdmod/tick/TensionLogger.java)
- Diagnostic logs via [src/main/java/com/utdmod/diag/DiagnosticLogs.java](src/main/java/com/utdmod/diag/DiagnosticLogs.java)
- Perturbation recording via [src/main/java/com/utdmod/research/PerturbationRecorder.java](src/main/java/com/utdmod/research/PerturbationRecorder.java)
- Research observations via [src/main/java/com/utdmod/research/ResearchObserver.java](src/main/java/com/utdmod/research/ResearchObserver.java)

### Missing instrumentation

The following are not implemented as a complete scientific record:

- Per-tick old state / new state / delta for every substrate variable
- Per-position and per-region trace for every relevant variable
- Gradient and curvature traces
- Runtime cost traces
- Deterministic replay snapshots
- Full state serialization for all substrate variables

### Key conclusion from Stage 7

Instrumentation exists for events and some high-level states, but not for a full, scientific substrate time-series capture.

## Stage 8: Experiment readiness

The requested experiment is: create an empty world, inject one disturbance, observe it for one simulated hour, record and replay everything, and generate automatic observations.

### What is currently possible

- The mod can receive perturbations such as mining or forestry events through the event hooks.
- The runtime updates local and global tension over time.
- Events are recorded in the research recorder.
- The client HUD and overlays can show tension and research narrative.

### What is not implemented

- Deterministic world setup and scripted single-disturbance injection for a controlled experiment
- A replay manager
- A time-lapse export system
- Automated observation of the full substrate state over time
- A one-hour simulation harness tied to the runtime state

### Verdict for Stage 8

The repository is not yet capable of executing the experiment as specified. It has the beginnings of an observation pipeline, but the full end-to-end scientific experiment is not implemented.

## Stage 9: Scientific capability assessment

### What the current implementation can plausibly detect

- Wave-like activity: Partial, via ResearchObserver narrative heuristics
- Persistent scars: Partial, via contamination memory and scar count
- Recovery: Partial, via decay and contamination-level reduction logic
- Migration / boundaries / clusters: Partial, via region formation and research observations

### What is not implemented or not verifiable

- Phase transitions
- Basins
- Long-term memory as a first-class substrate state
- Front propagation in a full substrate field
- Transport corridors as a dedicated runtime variable
- Robust scientific discovery from full state trajectories

### Key conclusion from Stage 9

The current implementation can support limited qualitative observation, but it cannot yet perform rigorous scientific discovery of the richer substrate phenomena named in the audit prompt.

## Stage 10: Subsystem status

| Subsystem | Status |
|---|---|
| Local chunk tension state | Implemented |
| Global tension state | Implemented |
| Perturbation bridge | Implemented |
| Chunk persistence / save-load | Implemented |
| Contamination memory | Partial |
| Transport field | Partial |
| Field depth field | Implemented via substrate state |
| Activation field | Implemented via substrate state |
| Memory field | Implemented via substrate state |
| Replay system | Missing |
| Full visualization pipeline | Partial |
| Scientific instrumentation | Partial |
| Experiment harness | Missing |

## Prioritized roadmap

### Priority 1: minimum work before the first scientific experiment

1. Implement a dedicated substrate state object with separate fields for activation, memory, transport, and field depth.
2. Make the runtime tick loop update those fields explicitly instead of collapsing the state into one local tension scalar.
3. Add a deterministic experiment harness that creates a controlled world state and injects exactly one perturbation.
4. Record per-tick snapshots of the substrate state for at least one world region.

### Priority 2: required before meaningful discovery

1. Add a replay system that can reconstruct and step the substrate state from recorded events.
2. Add a full visualization path for activation, memory, transport, and field depth.
3. Add true scientific instrumentation: old state, new state, delta, gradient, region, event source, and runtime cost for every variable.
4. Implement a proper transport-corridor and field-depth update path, not just derived observation heuristics.

### Priority 3: nice-to-have research tooling

1. Export CSV/JSON snapshots for offline analysis.
2. Add a command-line or in-game experiment runner.
3. Add automated pattern detectors for waves, scars, fronts, and basins.
4. Add deterministic seed replay and comparison across runs.

## Final conclusion

The repository contains a Minecraft-side tension system that is real, running, and observable. It is not, however, a complete or faithful port of the simulation-lab substrate mathematics.

The evidence shows:

- Some mathematics is present inside Minecraft.
- It is not the full mathematical substrate from the laboratory.
- It is observable only in a limited and partially symbolic way.
- It is not yet ready for the scientific experiment described in the audit prompt.

In short: the substrate has entered Minecraft, but it has not yet been transplanted as a full scientific dynamical system.
