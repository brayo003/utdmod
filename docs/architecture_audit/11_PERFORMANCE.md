# Performance

## Core loop
### Chunk update
- Frequency: every 5 server ticks.
- Complexity: O(number of active chunk entries) per world.
- Worst case: all loaded chunk entries in the tension map are iterated every update.
- Hotspot: neighbor lookup and repeated `ChunkTensionData.getLocalTension(...)` calls.

### Region rebuild
- Frequency: every 20 ticks.
- Complexity: O(number of active chunks + region connectivity work).
- Hotspot: flood-fill over connected active chunks and repeated lookups for chunk state.

### Global update
- Frequency: every tick.
- Complexity: O(number of regions) because `computeChunkCouplingSplit(...)` iterates regions.

### Storm evaluation
- Frequency: once per world per tick.
- Complexity: O(number of regions) for region selection.

### Ecology
- Frequency: every 4 ticks on one selected region.
- Complexity: low, but it mutates blocks and allocates lists.

### Networking
- Frequency: every 20 ticks.
- Complexity: O(number of players) for packet dispatch.

## Potential bottlenecks
- The chunk field update scales with the number of stored chunk entries, including inactive or old data.
- Region rebuilds can be expensive if many chunks are active.
- The local field uses `HashMap` and repeated state lookups, which is acceptable but can become expensive at scale.

## Optimization opportunities
- Prune inactive chunks more aggressively.
- Limit the chunk update pass to loaded chunks with meaningful tension.
- Cache region summaries or update them incrementally instead of rebuilding from scratch every 20 ticks.
