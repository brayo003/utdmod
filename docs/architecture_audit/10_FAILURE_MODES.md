# Failure Modes

## Chunk field
### Failure: NaN or unstable local tension
- Evidence: the chunk update uses arithmetic on `localTension` and clamps at the end, but no explicit guard against NaN is present before the clamp.
- Consequence: a bad input or corrupted state could propagate to region and global systems.
- Severity: medium.

### Failure: stale chunk entries persist
- Evidence: `ChunkTensionData.pruneInactive(...)` exists, but the main loop only prunes every 5 minutes.
- Consequence: inactive chunk state may accumulate and distort region rebuilds.
- Severity: medium.

## Region system
### Failure: region rebuild is O(active-chunk-count) and may be expensive
- Evidence: `RegionManager.refresh(...)` scans active chunk data and flood-fills every region.
- Consequence: large regions or many chunks may produce tick cost spikes.
- Severity: medium.

## Storm system
### Failure: storms are probabilistic and can be under-triggered
- Evidence: `StormManager.tick(...)` uses random thresholds and readiness values rather than deterministic triggers.
- Consequence: storm cadence may be inconsistent across worlds or sessions.
- Severity: low.

## Ecology
### Failure: ecology mutates the world in a sparse, non-transactional way
- Evidence: `RegionEcologyManager` directly calls block setters, and there is no visible rollback or guard against unsupported world states.
- Consequence: world edits may be surprising or inconsistent under load.
- Severity: medium.

## Networking
### Failure: client state can lag behind server state
- Evidence: sync happens every 20 ticks while presentation runs every client tick.
- Consequence: visual feedback may appear delayed or smoothed.
- Severity: low.
