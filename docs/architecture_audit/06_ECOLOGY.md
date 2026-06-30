# Ecology

## Purpose
The ecology subsystem applies lightweight block-level world changes based on region state and history. It is a decorative and stress-driven world mutation system rather than a core simulation engine.

## Inputs
### Caller
- Initialization in [src/main/java/com/utdmod/UTDMod.java](../../src/main/java/com/utdmod/UTDMod.java) registers `RegionEcologyManager`.
- The server tick handler in [src/main/java/com/utdmod/ecology/RegionEcologyManager.java](../../src/main/java/com/utdmod/ecology/RegionEcologyManager.java) runs it every `REGION_STEP` ticks.

### Data sources
- It reads region objects from `RegionManager`.
- It tracks `RegionHistory` and per-region stress in memory.
- It records mining events through `recordMiningHistory(...)` from the event injection layer.

## Outputs
- Mutates blocks in the world, for example grass, dead bushes, tall grass, flowers, coarse dirt, cobblestone, vines, and amethyst clusters.
- Updates in-memory history and stress maps.
- Emits diagnostics for cooling, recovery, and healed transitions.

## Algorithms
- It selects a small number of chunks per region and a small number of surface blocks per chunk.
- It then chooses an ecology policy based on `RegionState`, history, average tension, and stress.
- The policies are: `GROWING`, `STRAINED`, `FRACTURED`, `ANCIENT`, and `RECOVERY`.

## Tick rate
- Runs every 4 ticks, but only acts on one region per cycle based on the current tick index.
- The actual block mutations are sparse and bounded by `CHUNKS_PER_REGION` and `BLOCKS_PER_CHUNK`.

## Dependencies
- Depends on region state and history.
- Does not feed back into the chunk field directly in the verified code path.

## Important observation
Ecology is an observed side-effect system. It consumes region state and changes blocks, but no verified inverse path from ecology back into chunk tension exists in the audited code.
