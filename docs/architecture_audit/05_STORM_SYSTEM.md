# Storm System

## Purpose
The storm system is a regional storm controller. It selects a region based on region readiness and can start or end storms, spawn lightning, and change weather.

## Inputs
### Caller
- `TensionServerTick.onEndServerTick(...)` calls `StormManager.tick(world)` for each world after syncing.

### Methods
- `StormManager.selectStormRegion(...)`
- `StormManager.computeReadiness(...)`
- `StormManager.lightningChance(...)`
- `StormManager.triggerStorm(...)`
- `StormManager.endStorm(...)`
- `StormManager.strikeLightning(...)`

## Outputs
- Sets global storm state in `StormManager`.
- Can spawn lightning bolts.
- Can alter weather through `ServerWorld.setWeather(...)`.
- Emits diagnostics via `DiagnosticLogs.stormStarted(...)` and `DiagnosticLogs.stormEnded(...)`.

## Algorithms
- Storm selection ranks regions by `Region.getContribution()` and adds state-specific bonuses for `FRACTURED`, `STRAINED`, and `ANCIENT` states.
- Readiness is computed from state, average tension, fractured density, and storm density.
- Storms trigger probabilistically when readiness exceeds 0.72.
- While active, lightning is emitted with a state-dependent chance and weather may be set.

## Tick rate
- Storm logic is evaluated once per world per server tick.
- It is not the main driver of the chunk field and does not inject tension directly.

## Dependencies
- Reads region objects from `RegionManager`.
- Reads chunk state from `ChunkTensionData`.
- Can reduce global tension through ritual calming.

## Important observation
Storms are downstream of region state and chunk state. They do not appear to be the primary source of local tension injection.
