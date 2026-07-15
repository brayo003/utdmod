# Global System

> **[UPDATED 2026-07-15]** This file replaces the pre-refactor version that described a global tension ODE.
> `TensionManager.tickGlobalDynamics()` is a `@Deprecated` no-op stub. The global system is now a
> derived metric aggregator. See `docs/archive/` for the old description.

---

## Purpose

`TensionManager` computes summary statistics over all loaded chunk substrate states. It does not
evolve an independent state variable. There is no differential equation. Global tension is a
**spatial mean of per-chunk derived tension T(x)**.

---

## What runs every tick

`TensionManager.recompute(MinecraftServer)` is called from `TensionServerTick.onEndServerTick()`
on every server tick (after substrate integration at step 2).

It:
1. Iterates all loaded worlds.
2. For each `ChunkTensionData`, iterates all substrate-bearing chunks that are currently loaded.
3. For each loaded chunk computes `T(x) = SubstrateObservationModel.computeLocalTension(ss)`.
4. Aggregates: sum, sum of squares, max, mean of memory, mean of activation, mean/max of fieldDepth.
5. Derives: globalMeanTension, globalMaxTension, globalVariance, globalActiveFraction, globalMean{Depth,Memory,Activation}.
6. Updates the storm flag and corruption tier.

No value is written to a substrate state. All output fields are static scalars on `TensionManager`.

---

## Outputs (static fields on TensionManager)

| Method | Description |
|---|---|
| `getTension()` | globalMeanTension — spatial mean of T(x) over loaded substrate chunks |
| `getMaxTension()` | globalMaxTension |
| `getVariance()` | globalVariance |
| `getActiveFraction()` | fraction of chunks with T(x) > ACTIVE_TENSION_THRESHOLD (0.15) |
| `getMeanDepth()` | mean fieldDepth across substrate chunks |
| `getMeanMemory()` | mean memory across substrate chunks |
| `getMeanActivation()` | mean activation across substrate chunks |
| `isStormActive()` | storm flag (hysteretic, driven by globalMeanTension) |
| `getCorruptionLevel()` | integer 0–4 |
| `getCorruptionLevelName()` | "None" / "Subtle" / "Moderate" / "Severe" / "Catastrophic" |

---

## Storm flag

```
stormActive = true   when globalMeanTension > 0.55  (STORM_THRESHOLD)
stormActive = false  when globalMeanTension < 0.40  (STORM_HYSTERESIS)
```

Hysteresis prevents rapid oscillation near threshold.

---

## Corruption tiers

| globalMeanTension >= | Level | Name |
|---|---|---|
| 0.20 | 1 | Subtle |
| 0.40 | 2 | Moderate |
| 0.60 | 3 | Severe |
| 0.80 | 4 | Catastrophic |

No hysteresis on tier transitions. Tier is assigned instantly from current globalMeanTension.

---

## Deprecated methods (stubs — do not use)

| Method | Status |
|---|---|
| `setTension(double)` | `@Deprecated` — no-op |
| `addEvent(double)` | `@Deprecated` — no-op |
| `addMiningTension(String)` | `@Deprecated` — no-op |
| `reduceTension(double)` | `@Deprecated` — no-op |
| `tickGlobalDynamics(...)` | `@Deprecated` — no-op |

These stubs exist for binary compatibility. Any code still calling them has no effect.

---

## Tick rate

`recompute()` runs every server tick. It is cheap: only loaded chunks are visited, and the
computation is O(n) in loaded substrate-bearing chunks.

---

## Dependencies

- Reads `ChunkTensionData.getSubstrateStatesView()` for each `ServerWorld`.
- Calls `SubstrateObservationModel.computeLocalTension(ss)` per chunk.
- `TensionSyncPacket.sendToAllPlayers()` broadcasts `getTension()` and `isStormActive()` to clients.
- `StormManager.tick()` reads region state independently of `TensionManager`.

---

## Source

`com.utdmod.core.TensionManager`
