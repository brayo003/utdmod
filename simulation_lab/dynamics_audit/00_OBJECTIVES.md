# Objectives

## Purpose
This laboratory isolates the field dynamics implemented in the Minecraft mod and evaluates them as a standalone numerical system.

## Method
The simulator directly encodes the update rule extracted from the implementation in [src/main/java/com/utdmod/core/TensionServerTick.java](../../src/main/java/com/utdmod/core/TensionServerTick.java) and [src/main/java/com/utdmod/core/TensionManager.java](../../src/main/java/com/utdmod/core/TensionManager.java).

## Evidence
- Chunk update terms are implemented in `TensionServerTick.tickChunkTension(...)`.
- Global feedback is implemented in `TensionManager.tickGlobalDynamics(...)`.
- The standalone simulator uses the same coefficients and clamp logic.

## Conclusions
The laboratory is deterministic and reproducible, and it gives quantitative evidence for the extracted model.

## Limitations
The simulator is a simplified numerical reconstruction of the production logic and does not include every Minecraft-specific system.
