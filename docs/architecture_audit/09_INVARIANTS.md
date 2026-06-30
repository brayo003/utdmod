# Invariants

## Verified invariants
### 1. Presentation does not change gameplay state
Verified: presentation code in [src/main/java/com/utdmod/client/visual/ClientRegionPresentation.java](../../src/main/java/com/utdmod/client/visual/ClientRegionPresentation.java) and [src/main/java/com/utdmod/client/visual/ClientRegionalAtmosphere.java](../../src/main/java/com/utdmod/client/visual/ClientRegionalAtmosphere.java) only spawns particles, sounds, and overlay effects. It does not mutate server-side tension or world blocks.

### 2. Networking is server-authoritative
Verified: the server sends snapshots from [src/main/java/com/utdmod/network/TensionSyncPacket.java](../../src/main/java/com/utdmod/network/TensionSyncPacket.java), and the client only applies them through [src/main/java/com/utdmod/client/TensionSyncState.java](../../src/main/java/com/utdmod/client/TensionSyncState.java).

### 3. The local field is the primary simulation state
Verified: the main update loop in [src/main/java/com/utdmod/core/TensionServerTick.java](../../src/main/java/com/utdmod/core/TensionServerTick.java) updates per-chunk tension every 5 ticks and uses that state to build regions and drive global feedback.

### 4. Region IDs are unique within the current rebuild pass
Verified: `RegionManager.refresh(...)` assigns IDs incrementally from `nextId` when building the new region list.

### 5. Chunk tension is clamped to a bounded range
Verified: `TensionServerTick.tickChunkTension(...)` clamps final `localTension` to $[0, 5]$.

## Rejected or unverified invariants
### 1. Storms never inject tension
Rejected: storm logic does not directly inject tension into chunk fields, but the chunk field update can still be influenced by storm-active state through `stormActive` and `STORM_DRAIN_RATE` in [src/main/java/com/utdmod/core/TensionServerTick.java](../../src/main/java/com/utdmod/core/TensionServerTick.java).

### 2. Ecology observes only region state
Partially verified: `RegionEcologyManager` uses region state and history, but it also directly reads mining history that is recorded from events.

### 3. Global aggregation never edits chunks directly
Verified: `TensionManager.tickGlobalDynamics(...)` updates global state only; it does not mutate chunks directly. The chunk field is updated by `TensionServerTick.tickChunkTension(...)`.
