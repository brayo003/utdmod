# Tension threshold scale correction

**Status:** source change prepared in the supplied archive; not pushed to GitHub.
**Scope:** first bounded audit issue only. This is an initial calibration, not a claim that gameplay timing has been validated.

## Why this change is justified

`SubstrateObservationModel.computeLocalTension` computes a weighted average of `A/(1+A)`, `M/(1+M)`, and `D/(1+D)`. With non-negative values this is bounded below 1. The normal runtime clamp is 4, so each normalized term is at most 0.8 and the observation itself cannot exceed 0.8. The original chunk-state thresholds 0.95, 1.28, and 3.0 therefore made all three non-stable state-entry thresholds unreachable. Similar out-of-range comparisons prevented contamination levels, regional diagnostics, sleep blocking, and wraith aggression from responding to the derived metric.

## Changes made

- Added `TensionThresholds` as the shared source of thresholds for the bounded local observation. Initial chunk ladder: STRAINED 0.35, FRACTURED 0.60, DECOUPLED 0.72; downgrade hysteresis 0.08.
- Updated `ParameterSet` default T1/T2/T3 to match the shared ladder. Updated its storm default/end values to 0.55/0.40 to match the existing live global storm thresholds; the live global storm logic itself was not changed.
- Made contamination thresholds reachable: medium 0.55, heavy 0.68. Lowered the tension-based persistence extension trigger to 0.70.
- Made regional FRACTURED/STRAINED classification reachable at 0.60/0.45, and made the ecology strain gate reachable at 0.65. The independent maturity/age/density routes to region states remain unchanged.
- Made sleep blocking, wraith aggression, and the Tension Serpent global onset/despawn threshold reachable at 0.60. Wraith probability now rises from zero at its own onset threshold instead of using the obsolete >1.0 offset.
- Updated research narration, multi-region tension narration, the ecology tension gate, and the debug high-tension duration counter so their local/global tension comparisons are reachable.
- Normalized the storm manager's region-tension readiness denominator to the shared 0.72 top-tier threshold.
- Added `TensionThresholdsTest` to assert threshold ordering, range, and hysteresis separation.

## What was not changed

The observation equation, substrate dynamics, transport, and global live storm hysteresis (0.55 onset / 0.40 release) were not changed. This patch does not fix the separate resource-regeneration behavior or the non-reproducible Gradle setup.

## Verification and limitations

The source was edited by exact-match replacements that abort if expected source text does not match. The added constants and test are intended to catch future threshold-range regressions. This environment cannot successfully create a GitHub branch because the GitHub integration returned HTTP 403 for branch creation. A clean Gradle/Minecraft build and JUnit execution have **not** been verified here; do not treat this report as a passing CI result.

The values 0.35/0.60/0.72 are a coherent reachable starting ladder based on the runtime metric and prior numerical probes, not validated promises such as “fractures in 2–4 minutes.” Gameplay timing, regional propagation, and false-positive rates still require a controlled in-game test. Existing worlds may also contain persisted enum states from older builds; no data migration was added.

## Files changed

- `src/main/java/com/utdmod/tension/TensionThresholds.java` (new)
- `src/main/java/com/utdmod/tension/ChunkTensionData.java`
- `src/main/java/com/utdmod/tension/ParameterSet.java`
- `src/main/java/com/utdmod/core/Region.java`
- `src/main/java/com/utdmod/core/RegionDiagnosticsManager.java`
- `src/main/java/com/utdmod/event/TensionEvents.java`
- `src/main/java/com/utdmod/entity/TensionWraithEntity.java`
- `src/main/java/com/utdmod/storm/StormManager.java`
- `src/main/java/com/utdmod/entity/TensionSerpentEntity.java`
- `src/main/java/com/utdmod/research/ResearchObserver.java`
- `src/main/java/com/utdmod/ecology/RegionEcologyManager.java`
- `src/main/java/com/utdmod/debug/AIDebugLoggerTest.java`
- `src/test/java/com/utdmod/tension/TensionThresholdsTest.java` (new)
