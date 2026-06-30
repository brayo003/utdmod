# Networking

## Purpose
Networking relays server tension state to clients so the presentation layer can render atmosphere and HUD feedback.

## Inputs
### Server-side callers
- `TensionServerTick.onEndServerTick(...)` calls `TensionSyncPacket.sendToAllPlayers(...)` every 20 ticks.
- `UseCrystalPacket` also calls `TensionSyncPacket.sendToAllPlayers(...)` in its receiver path.

### Client-side receivers
- `UTDModClient.onInitializeClient()` registers `TensionSyncPacket.registerReceiver()`.
- The client receiver calls `TensionSyncState.applySnapshot(...)`.

## Outputs
- Server sends a packet containing global tension, storm state, local tension, region averages, region maxima, region metadata, and state ordinal.
- Client stores the values in `TensionSyncState`.

## Persistent state
- The client keeps a volatile mirror of server values in `TensionSyncState`.
- This is not persisted to disk.

## Tick rate
- Server sync occurs every 20 server ticks.
- Client smoothing runs every client tick.

## Dependencies
- Depends on the chunk-local tension and region summaries already computed on the server.
- Client presentation depends on this packet stream.

## Important observation
The networking layer is presentation-oriented. It does not participate in the simulation loop itself; it only mirrors server state.
