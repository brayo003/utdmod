# Local Field

## Purpose
The local field is the core dynamical substrate. It stores per-chunk tension, tracks chunk state, and evolves that state on the server tick.

## Inputs
### Callers
- Event injection in [src/main/java/com/utdmod/event/TensionEvents.java](../../src/main/java/com/utdmod/event/TensionEvents.java) calls `ChunkTensionManager` mutators.
- Player movement in [src/main/java/com/utdmod/core/TensionServerTick.java](../../src/main/java/com/utdmod/core/TensionServerTick.java) adds local tension every tick for moving players.
- Player-state fatigue in [src/main/java/com/utdmod/signals/PlayerStateIntegrator.java](../../src/main/java/com/utdmod/signals/PlayerStateIntegrator.java) adds slow session-based leakage.
- The chunk update loop in `TensionServerTick.tickChunkTension(...)` updates all active chunks.

### Methods
- `ChunkTensionManager.getLocalTension(...)`
- `ChunkTensionManager.addLocalTension(...)`
- `ChunkTensionManager.addMiningTension(...)`
- `ChunkTensionManager.addForestryTension(...)`
- `ChunkTensionManager.addSlaughterTension(...)`
- `ChunkTensionData.setLocalTension(...)`
- `ChunkTensionData.addLocalTension(...)`
- `ChunkTensionData.addMiningTension(...)`

## Outputs
- Updates the in-memory chunk tension map.
- Updates chunk state (`STABLE`, `STRAINED`, `FRACTURED`, `DECOUPLED`).
- May set `stormActive` for a chunk.
- Persists chunk tension to NBT via `writeNbt(...)` and restores via `fromNbt(...)`.
- Emits diagnostics and experiment telemetry on state transitions.

## Persistent state
- The chunk field is stored in `ChunkTensionData`, which extends `PersistentState`.
- It survives world saves through `PersistentStateManager` and is keyed as `chunk_tension_data`.
- It also keeps contamination memory, mining-rate memory, storm flags, and access timestamps.

## Algorithms
The core chunk update in `TensionServerTick.tickChunkTension(...)` uses:

- Diffusion: `diffusion = D * neighborAvg`
- Decay: `decay = LAMBDA * localTension * decayMul`
- Nonlinear reinforcement: `nonlinear = effectiveAlpha * localTension^2`
- Saturation: `saturation = BETA * localTension^3`
- Global feedback: `globalFeedback = GAMMA * globalTension`
- Final update: `localTension += diffusion + nonlinear - saturation + globalFeedback - decay`
- Storm drain: `localTension -= STORM_DRAIN_RATE` if the chunk is storm-active

The update is clamped to $[0, 5]$.

## Tick rate
- The field update runs every 5 server ticks.
- Movement injection is per-player and per-tick if movement exceeds the threshold.
- State transition effects are triggered when state changes occur.

## Dependencies
- Depends on `TensionManager` for feedback from the global field.
- Depends on chunk neighbors and chunk state.
- Provides the input data consumed by region and storm systems.
