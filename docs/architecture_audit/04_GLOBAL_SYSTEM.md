# Global System

## Purpose
The global system is the aggregate tension state that summarizes the local field and feeds back into the chunk update loop. It is not the primary driver of local behavior; it is a feedback term.

## Inputs
### Caller
- `TensionServerTick.onEndServerTick(...)` calls `TensionManager.tickGlobalDynamics(...)` every tick.

### Parameters
- The method receives `chunkCouplingInflow`, `stormDiagnosticsNote`, `serverTick`, `ambientInflowDiag`, and `localCouplingDiag`.

## Outputs
- Updates `globalTension`.
- Sets or clears the `stormActive` flag.
- Updates corruption level.
- Emits trace and diagnostics through `TensionTraceLogger` and `DiagnosticLogs`.

## Algorithms
The update is:

$$
G_{t+1} = G_t + (-\lambda G_t) + (\alpha G_t^2) + I_t
$$

where:
- $\lambda = 0.0032$
- $\alpha = 0.002$
- $I_t$ is the chunk-coupling inflow plus buffered impulses

The implementation also clamps the value to $[0, 5]$ and applies a damping rule if the equilibrium discriminant is negative.

## Tick rate
- Runs every server tick.
- Diagnostics are emitted on a 20-tick cadence.

## Dependencies
- Depends on `RegionManager` through the coupling split computed in `TensionServerTick.computeChunkCouplingSplit(...)`.
- Does not directly mutate regions or world blocks.

## Verified behavior
The global system is an aggregate feedback layer. The chunk field update uses `globalTension` as a feedback term, but the local chunk dynamics are still dominated by local diffusion, decay, and saturation terms.
