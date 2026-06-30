# Parameter Table

## Purpose
Document the coefficients and thresholds used by the simulator.

## Method
The simulator uses the values extracted from the source code and recorded in [src/main/java/com/utdmod/simulation_lab/src/ParameterSet.java](../src/ParameterSet.java).

## Evidence
| Parameter | Value | Source |
| --- | ---: | --- |
| D | 0.012 | TensionServerTick |
| lambda | 0.0006 | TensionServerTick |
| alpha | 0.009 | TensionServerTick |
| beta | 0.055 | TensionServerTick |
| gamma | 0.045 | TensionServerTick |
| stormDrain | 0.002 | TensionServerTick |
| stormThreshold | 0.95 | TensionServerTick |
| stormEndThreshold | 0.25 | TensionServerTick |
| regionActiveThreshold | 0.35 | UTDConfig / RegionManager |
| t1 | 0.95 | ChunkTensionData |
| t2 | 1.28 | ChunkTensionData |
| t3 | 3.0 | ChunkTensionData |

## Conclusions
The dominant coefficients are diffusion, nonlinear reinforcement, and saturation.

## Limitations
The laboratory uses a simplified single-grid representation rather than the full chunk/region graph from Minecraft.
