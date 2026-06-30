# Call Graph

## Executive summary
The runtime path is server-first. Player actions and server ticks mutate chunk-local tension, then the server rebuilds regions, updates global tension, optionally triggers storms, applies ecological actions, synchronizes state to clients, and finally drives client-only presentation.

## Primary execution chain
1. Player action or server tick event fires in [src/main/java/com/utdmod/event/TensionEvents.java](../../src/main/java/com/utdmod/event/TensionEvents.java).
2. The event calls a mutator in [src/main/java/com/utdmod/tension/ChunkTensionManager.java](../../src/main/java/com/utdmod/tension/ChunkTensionManager.java), which forwards to [src/main/java/com/utdmod/tension/ChunkTensionData.java](../../src/main/java/com/utdmod/tension/ChunkTensionData.java).
3. Every 5 ticks, [src/main/java/com/utdmod/core/TensionServerTick.java](../../src/main/java/com/utdmod/core/TensionServerTick.java) runs `tickChunkTension(...)`, which applies diffusion, decay, nonlinear terms, global feedback, and storm drain logic to each active chunk.
4. Every 20 ticks, the same server tick handler calls `RegionManager.refresh(...)`, which rebuilds region objects from active chunk tension data.
5. The same tick loop calls `TensionManager.tickGlobalDynamics(...)`, which updates the global tension state and storm flags.
6. After that, [src/main/java/com/utdmod/storm/StormManager.java](../../src/main/java/com/utdmod/storm/StormManager.java) runs for each world and may start/end storms or spawn lightning.
7. Separately, [src/main/java/com/utdmod/ecology/RegionEcologyManager.java](../../src/main/java/com/utdmod/ecology/RegionEcologyManager.java) runs on the server tick loop and applies world-block changes based on region state and history.
8. Every 20 ticks, [src/main/java/com/utdmod/network/TensionSyncPacket.java](../../src/main/java/com/utdmod/network/TensionSyncPacket.java) sends the current server snapshot to clients.
9. Clients consume the packet in [src/main/java/com/utdmod/client/TensionSyncState.java](../../src/main/java/com/utdmod/client/TensionSyncState.java) and drive presentation in [src/main/java/com/utdmod/client/visual/ClientRegionalAtmosphere.java](../../src/main/java/com/utdmod/client/visual/ClientRegionalAtmosphere.java) and [src/main/java/com/utdmod/client/visual/ClientRegionPresentation.java](../../src/main/java/com/utdmod/client/visual/ClientRegionPresentation.java).

## Incoming and outgoing references
### TensionEvents
Incoming: initialization in [src/main/java/com/utdmod/UTDMod.java](../../src/main/java/com/utdmod/UTDMod.java).
Outgoing: `ChunkTensionManager.addMiningTension(...)`, `ChunkTensionManager.addForestryTension(...)`, `ChunkTensionManager.addSlaughterTension(...)`, `ChunkTensionManager.addLocalTension(...)`, `RegionEcologyManager.recordMiningHistory(...)`.

### ChunkTensionManager
Incoming: `TensionEvents`, `PlayerStateIntegrator`, `TensionServerTick`, `TensionManager`.
Outgoing: `ChunkTensionData.addLocalTension(...)`, `ChunkTensionData.addMiningTension(...)`, `ChunkTensionData.setLocalTension(...)`, `ChunkTensionData.getLocalTension(...)`.

### ChunkTensionData
Incoming: `ChunkTensionManager`, `TensionServerTick`.
Outgoing: state transitions, contamination memory updates, persistent NBT save/load, diagnostic logging.

### TensionServerTick
Incoming: registration in [src/main/java/com/utdmod/UTDMod.java](../../src/main/java/com/utdmod/UTDMod.java).
Outgoing: `PlayerStateIntegrator.onServerPlayerTick(...)`, `ChunkTensionManager.addLocalTension(...)`, `RegionManager.refresh(...)`, `TensionManager.tickGlobalDynamics(...)`, `StormManager.tick(...)`, `TensionSyncPacket.sendToAllPlayers(...)`, `RegionDiagnosticsManager.refresh(...)`, `ExperimentTelemetry.tick(...)`.

### RegionManager
Incoming: `TensionServerTick`.
Outgoing: constructs `Region` objects; does not mutate chunk tension directly.

### TensionManager
Incoming: `TensionServerTick`.
Outgoing: updates global tension and storm flags; no direct calls into ecology or presentation.

### StormManager
Incoming: `TensionServerTick`.
Outgoing: reads regions and chunk states, spawns lightning, changes weather, and can call `TensionManager.reduceTension(...)` through ritual calming.

### RegionEcologyManager
Incoming: initialization in [src/main/java/com/utdmod/UTDMod.java](../../src/main/java/com/utdmod/UTDMod.java) and server tick registration.
Outgoing: mutates blocks in the world and records mining history; it does not call tension managers.

### TensionSyncPacket
Incoming: `TensionServerTick` and packet-related handlers.
Outgoing: writes packets to clients and updates `TensionSyncState` on the client.

## Notes on the graph
- The dominant loop is `TensionEvents` -> `ChunkTensionManager` -> `ChunkTensionData` -> `TensionServerTick.tickChunkTension(...)`.
- The region and storm systems consume the results of local chunk evolution rather than directly controlling it.
- Ecology is a side-effect subsystem that observes region state and mutates blocks, but there is no verified reverse path from ecology back into tension.
