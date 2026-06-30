# Event Injection

## Purpose
Event injection is the entry point that turns player and world events into local chunk tension. It exists to convert gameplay actions such as mining, combat, movement, sleep, and portal ignition into inputs for the field.

## Inputs
### Direct callers
- Initialization in [src/main/java/com/utdmod/UTDMod.java](../../src/main/java/com/utdmod/UTDMod.java) calls `TensionEvents.registerEvents()`.
- Fabric callbacks registered inside [src/main/java/com/utdmod/event/TensionEvents.java](../../src/main/java/com/utdmod/event/TensionEvents.java):
  - `PlayerBlockBreakEvents.AFTER`
  - `ServerLivingEntityEvents.AFTER_DEATH`
  - `EntitySleepEvents.ALLOW_SLEEPING`
  - `UseBlockCallback.EVENT`
  - `ServerTickEvents.END_SERVER_TICK`

### Parameters
- Block break events pass `World`, `PlayerEntity`, `BlockPos`, `BlockState`, and optional `BlockEntity`.
- Death events pass the entity and damage source.
- Block-use events pass `PlayerEntity`, `World`, hand, and `BlockHitResult`.
- End-of-tick events pass the server object.

## Outputs
- Mutates chunk-local tension through `ChunkTensionManager`.
- Records mining history through `RegionEcologyManager.recordMiningHistory(...)`.
- Emits diagnostic logs through `TensionLogger.traceWithGlobals(...)` and `TensionLogger.log(...)`.
- May prevent sleep by returning `PlayerEntity.SleepFailureReason.OTHER_PROBLEM`.

## Important algorithms
- Mining events choose a tension amount by block classification in `ChunkTensionManager.addMiningTension(...)`.
- Forestry events inject tension into the broken chunk and its eight neighbors with different weights.
- Combat kills use fixed amounts with larger values for villagers, iron golems, and bosses.
- Bulk placement is rate-limited by `blocksPlacedRecently` and `PLACEMENT_WINDOW_MS`.
- Deep-layer movement uses a tick-based trigger every 200 ticks for players below Y 20.

## Tick rate and context
- Event injection is mostly event-driven, not periodic.
- The only periodic injections are the end-of-tick loops for sleep relief and deep-layer mining, both tied to server ticks.
- All event injection runs on the server thread.

## Verified downstream effects
- Mining and combat events reach chunk tension and then the chunk update loop.
- Sleep relief reduces local tension in the chunk of the sleeping player.
- Portal ignition and enchantment-table use both inject local tension.
