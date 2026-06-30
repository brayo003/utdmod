# Execution Pipeline

## Verified runtime path
This project executes as a server-authoritative tension simulation with a client presentation layer. The actual execution chain is:

1. Player action or lifecycle event fires in [src/main/java/com/utdmod/event/TensionEvents.java](../../src/main/java/com/utdmod/event/TensionEvents.java).
2. The event injects local tension through [src/main/java/com/utdmod/tension/ChunkTensionManager.java](../../src/main/java/com/utdmod/tension/ChunkTensionManager.java).
3. The local tension is stored in [src/main/java/com/utdmod/tension/ChunkTensionData.java](../../src/main/java/com/utdmod/tension/ChunkTensionData.java), which persists chunk state across saves.
4. On the server tick, [src/main/java/com/utdmod/core/TensionServerTick.java](../../src/main/java/com/utdmod/core/TensionServerTick.java) applies the local field update loop every 5 ticks.
5. The same server tick rebuilds connected regions every 20 ticks in [src/main/java/com/utdmod/core/RegionManager.java](../../src/main/java/com/utdmod/core/RegionManager.java).
6. Global tension is updated every tick in [src/main/java/com/utdmod/core/TensionManager.java](../../src/main/java/com/utdmod/core/TensionManager.java).
7. Storms are evaluated every tick in [src/main/java/com/utdmod/storm/StormManager.java](../../src/main/java/com/utdmod/storm/StormManager.java).
8. Ecology is evaluated on a separate server-tick cadence in [src/main/java/com/utdmod/ecology/RegionEcologyManager.java](../../src/main/java/com/utdmod/ecology/RegionEcologyManager.java).
9. Server snapshots are sent to clients via [src/main/java/com/utdmod/network/TensionSyncPacket.java](../../src/main/java/com/utdmod/network/TensionSyncPacket.java).
10. Client presentation consumes those snapshots in [src/main/java/com/utdmod/client/TensionSyncState.java](../../src/main/java/com/utdmod/client/TensionSyncState.java), [src/main/java/com/utdmod/client/visual/ClientRegionalAtmosphere.java](../../src/main/java/com/utdmod/client/visual/ClientRegionalAtmosphere.java), and [src/main/java/com/utdmod/client/visual/ClientRegionPresentation.java](../../src/main/java/com/utdmod/client/visual/ClientRegionPresentation.java).

## What the pipeline actually does
- The dominant state variable is chunk-local tension.
- The dominant update rule is the chunk field update in `TensionServerTick.tickChunkTension(...)`.
- Region building is derived from chunk state, not the other way around.
- Global tension is a summary and feedback term, not the primary driver of each chunk.
- Storms and ecology are downstream consumers of region state and chunk state, not the core source of spatial dynamics.

## Verdict on the architecture
This is a genuine spatial field simulation in structure and execution, but the behavior is dominated by the server-side chunk field loop and the global coupling term. Ecology and presentation are real subsystems, but they do not dominate the simulation. They mostly observe state and mutate the world or the client UI.
