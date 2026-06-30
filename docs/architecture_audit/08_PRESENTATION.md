# Presentation

## Purpose
Presentation converts server-authoritative tension state into feedback effects such as particles, overlay tinting, ambient sound, and HUD accent.

## Inputs
### Callers
- `UTDModClient.onInitializeClient()` registers presentation-related tick handlers.
- Every client tick, `ClientRegionalAtmosphere.tick(...)` and `ClientRegionPresentation.tick(...)` run.

### Data source
- Presentation reads `TensionSyncState` values rather than server-side simulation objects.

## Outputs
- Spawns particles and plays ambient cues.
- Updates overlay intensity and color transitions.
- Changes the HUD accent and vignette state.

## Algorithms
- `ClientRegionPresentation.tick(...)` uses a 120-tick transition state to blend between profiles.
- `RegionPresentationProfile` defines state-dependent fog, tint, particle density, sound, and wind values.
- Particle and sound emission are probabilistic and threshold-based.

## Tick rate
- Presentation runs every client tick.
- The transition and ambient emissions are time-based and probabilistic.

## Dependencies
- Depends on network state from `TensionSyncPacket`.
- Does not feed back into simulation state.

## Verified behavior
Presentation is purely perceptual and non-authoritative. It reflects server state but does not alter the gameplay simulation.
