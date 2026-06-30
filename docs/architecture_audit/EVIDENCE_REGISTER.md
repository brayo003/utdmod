# Evidence Register

## Template
- Claim:
- Evidence:
- References:
- Confidence:
- Notes:

## Initial entries
### Claim: the server tick loop is the main execution driver
- Evidence: [src/main/java/com/utdmod/core/TensionServerTick.java](../../src/main/java/com/utdmod/core/TensionServerTick.java) registers the end-of-tick handler and calls local field updates, region refresh, global dynamics, storm updates, sync, and ecology hooks.
- References: `TensionServerTick.register()`, `TensionServerTick.onEndServerTick(...)`
- Confidence: Verified

### Claim: player actions are converted into chunk-local tension
- Evidence: [src/main/java/com/utdmod/event/TensionEvents.java](../../src/main/java/com/utdmod/event/TensionEvents.java) calls `ChunkTensionManager.addMiningTension(...)`, `addForestryTension(...)`, `addSlaughterTension(...)`, and `addLocalTension(...)` from Fabric event handlers.
- References: `TensionEvents.registerEvents()`
- Confidence: Verified

### Claim: the chunk field update uses a nonlinear reaction-diffusion style rule
- Evidence: [src/main/java/com/utdmod/core/TensionServerTick.java](../../src/main/java/com/utdmod/core/TensionServerTick.java) computes diffusion, nonlinear reinforcement, saturation, decay, global feedback, and storm drain terms.
- References: `TensionServerTick.tickChunkTension(...)`
- Confidence: Verified

### Claim: regions are rebuilt from chunk state rather than persisted independently
- Evidence: [src/main/java/com/utdmod/core/RegionManager.java](../../src/main/java/com/utdmod/core/RegionManager.java) rebuilds `Region` objects from `ChunkTensionData` every 20 ticks.
- References: `RegionManager.refresh(...)`
- Confidence: Verified

### Claim: the client presentation layer is non-authoritative
- Evidence: [src/main/java/com/utdmod/client/visual/ClientRegionPresentation.java](../../src/main/java/com/utdmod/client/visual/ClientRegionPresentation.java) and [src/main/java/com/utdmod/client/visual/ClientRegionalAtmosphere.java](../../src/main/java/com/utdmod/client/visual/ClientRegionalAtmosphere.java) only emit particles, sounds, and overlay effects; they do not mutate simulation state.
- References: `ClientRegionPresentation.tick(...)`, `ClientRegionalAtmosphere.tick(...)`
- Confidence: Verified
