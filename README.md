# UTD Mod - DCII Tension System (Minecraft 1.20.4 Fabric)

**Technical implementation of Substrate X–style “world tension” dynamics**


UTD Mod adds a persistent world-state field to Minecraft. Player activity alters regional tension, tension diffuses through chunks, accumulates history, and drives world events.


For related work, check out https://github.com/brayo003/Substrate-X-Theory-of-Information-Gravity

This Minecraft mod implements a **server-authoritative tension field** with optional **per-chunk** dynamics and client **mirrored state** for HUD/audio.

## What the code actually implements

### Global tension (`com.utdmod.core.TensionManager`)

Each server tick the mod updates a scalar `T` with:

- **Decay:** linear term proportional to `T`
- **Nonlinear feedback:** a `T²` amplification term (capped by a hard max)
- **Inflow:** movement-derived continuous inflow plus an **event buffer** (mining, combat hooks, etc.) that decays after application

Storm and corruption tiers are driven from this same state (thresholds + hysteresis where applicable).

### Chunk field (`ChunkTensionData` + `TensionServerTick`)

On a slower cadence, the server walks chunks that carry tension and applies a **discrete reaction–diffusion–style** update: neighbor averaging (diffusion-like), local nonlinear growth, cubic damping, coupling from global `T`, optional local storm drain, and caps. Global `T` is then **weakly coupled** back toward the spatial average.

This is **not** the gradient–excitation–damping formula from older docs; it is an explicit **dynamical update rule** tuned for gameplay.

### Client mirror (`com.utdmod.client.TensionSyncState`)

The client **never** runs `core.TensionManager`. It only reads `CLIENT_TENSION` / `CLIENT_STORM`, updated from `TensionSyncPacket` sent by the server.

## Architecture (post-refactor)

| Layer | Responsibility |
|--------|------------------|
| `core.TensionManager` | Single server source of truth for global tension + storm flag |
| `TensionServerTick` | One `END_SERVER_TICK` pipeline: inflow → global tick → chunk field → weather/secondary hooks → broadcast sync |
| `TensionSyncState` | Client snapshot for overlays, audio, and any client-only consumers |
| `TensionSyncPacket` | Server → client snapshot |

## Status

- Mod loads; **single global tension + chunk map + sync** share one server pipeline.
- Content: blocks, items, entities, block entities register from `UTDMod` init.
- Further work: spawn rules, entity render registration, HUD polish, and balancing.

## Build

```bash
./gradlew build
```

## Collaboration

Looking for Fabric/Java developers to extend the DCII-style framework in Minecraft.
