# Region System

## Purpose
The region system groups nearby active chunks into connected regions and exposes region-level summaries that feed global coupling, storm selection, and ecology.

## Inputs
### Caller
- `TensionServerTick.onEndServerTick(...)` calls `RegionManager.refresh(...)` every 20 ticks.

### Data sources
- The refresh pass reads chunk tension values from `ChunkTensionData`.
- It also reads per-chunk state and storm flags.
- Regions are built only from chunks with tension above `UTDConfig.regionActiveTensionThreshold` and that are currently loaded.

## Outputs
- Builds a new immutable list of `Region` objects.
- Computes region-level aggregates: average tension, max tension, fractured count, decoupled count, storm count, centroid, age, maturity, and potential.
- Emits creation, state-change, merge, and destroy diagnostics.

## Algorithms
- Region generation is a flood-fill over the active-chunk graph using four-neighbor connectivity.
- Region state is computed from `averageTension`, `maturity`, `fracturedChunks`, `stormChunks`, and `age` in `Region.getState()`.
- Maturity is updated with an exponential smoothing rule in `Region.updateMaturity(...)` using `MATURITY_ALPHA`.
- Contribution is computed as `maturity * densityBonus` in `Region.getContribution()`.

## Persistent state
- Region objects are not persisted to disk.
- They are reconstructed from chunk state every 20 ticks.
- Previous-region identity is tracked in memory only for overlap and transition diagnostics.

## Tick rate
- Region rebuilds occur every 20 ticks.
- Region lookups for a chunk are O(n) over active regions and are used on the server side for per-player region queries.

## Dependencies
- Depends on the local field and chunk state.
- Supplies data to `TensionManager` for global coupling, to `StormManager` for storm selection, and to `RegionEcologyManager` for ecological policy.

## Important observation
The region system is a derived layer. It does not directly mutate chunk tension; it summarizes the already-evolved local field.
