# State Mutations

## Chunk tension
### Mutable object
- [src/main/java/com/utdmod/tension/ChunkTensionData.java](../../src/main/java/com/utdmod/tension/ChunkTensionData.java)

### Modified by
- Mining events: `ChunkTensionManager.addMiningTension(...)`
- Forestry events: `ChunkTensionManager.addForestryTension(...)`
- Combat kills: `ChunkTensionManager.addSlaughterTension(...)`
- Local movement: `ChunkTensionManager.addLocalTension(...)`
- Player fatigue: `PlayerStateIntegrator.onServerPlayerTick(...)`
- Chunk field update loop: `TensionServerTick.tickChunkTension(...)`

### Read by
- `RegionManager.refresh(...)`
- `TensionServerTick.tickChunkTension(...)`
- `StormManager.strikeLightning(...)`
- `TensionSyncPacket.sendToAllPlayers(...)`

### Persistence
- Persisted through `PersistentState` and NBT.

### Thread
- Server thread.

## Chunk state
### Mutable object
- `ChunkTensionData.chunkStates`

### Modified by
- `ChunkTensionData.setLocalTension(...)`

### Read by
- `RegionManager.refresh(...)`
- `StormManager.strikeLightning(...)`
- `TensionServerTick.tickChunkTension(...)`

## Region objects
### Mutable object
- Rebuilt each refresh pass in `RegionManager.refresh(...)`

### Modified by
- Reconstructed from data each 20 ticks.

### Read by
- `StormManager`
- `RegionEcologyManager`
- `TensionServerTick.computeChunkCouplingSplit(...)`

## Global tension
### Mutable object
- [src/main/java/com/utdmod/core/TensionManager.java](../../src/main/java/com/utdmod/core/TensionManager.java)

### Modified by
- `TensionManager.tickGlobalDynamics(...)`
- `TensionManager.reduceTension(...)`

### Read by
- `TensionServerTick.tickChunkTension(...)`
- `StormManager`
- `TensionSyncPacket`

## Storm flags
### Mutable object
- `StormManager` internal static fields

### Modified by
- `StormManager.triggerStorm(...)`
- `StormManager.endStorm(...)`

### Read by
- `TensionServerTick.onEndServerTick(...)`
- `TensionManager.tickGlobalDynamics(...)`

## Ecology history and stress
### Mutable object
- `RegionEcologyManager` maps of stress/history/previous state

### Modified by
- `RegionEcologyManager.onEndServerTick(...)`
- `RegionEcologyManager.recordMiningHistory(...)`
