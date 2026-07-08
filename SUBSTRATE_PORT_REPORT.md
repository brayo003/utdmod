# Substrate Port Report

## Stage 0 - Audit

### Mapping
- Lab model: simulation_lab/src/GridSimulationV2.java -> Minecraft destination: src/main/java/com/utdmod/tension/SubstrateState.java
- Lab parameter defaults: simulation_lab/src/ParameterSet.java -> Minecraft destination: src/main/java/com/utdmod/config/UTDConfig.java
- Lab transport architecture: simulation_lab/src/test/TransportPipelineAuditTest.java -> Minecraft destination: src/main/java/com/utdmod/tension/ChunkTensionData.java and src/main/java/com/utdmod/core/TensionServerTick.java
- Lab diagnostics: simulation_lab/src/test/TransportPipelineAuditTest.java -> Minecraft destination: src/main/java/com/utdmod/diag/DiagnosticLogs.java

## Stage 1 - Substrate State

### Equations implemented
- Activation update using retained activation, memory-dependent decay, transport contribution, and external input.
- Memory update using activation-driven gain and decay.
- Field-depth growth/decay with capacity and radius tracking.
- Transport contribution using source strength, readiness, destination capacity, distance attenuation, and gain.

### Files modified
- src/main/java/com/utdmod/tension/SubstrateState.java
- src/main/java/com/utdmod/tension/ChunkTensionData.java
- src/main/java/com/utdmod/core/TensionServerTick.java
- src/main/java/com/utdmod/config/UTDConfig.java
- src/main/java/com/utdmod/diag/DiagnosticLogs.java
- src/test/java/com/utdmod/tension/SubstrateStateTest.java

### Methods modified
- SubstrateState.updateFromLocalTension
- SubstrateState.advance
- SubstrateState.computeTransportContribution
- SubstrateState.computeTransportRadius
- SubstrateState.computeReadiness
- SubstrateState.computeDestinationCapacity
- ChunkTensionData.updateSubstrateState
- TensionServerTick.tickChunkTension
- DiagnosticLogs.substrateSnapshot

### Parameters added
- substrateTransportGain
- substrateActivationDecay
- substrateMemoryGain
- substrateMemoryDecay
- substrateReadinessScale
- substrateTransportDistanceDecay
- substrateMemoryEffectK
- substrateFieldDepthGain
- substrateFieldDepthDecay
- substrateFieldDepthRadius
- substrateFieldDepthEffectK
- substrateClampMax

### Mathematical assumptions
- The port uses the lab's validated frozen transport architecture.
- Readiness is based on memory only in this initial port.
- Field-depth radius remains derived from field-depth value and the lab's radius scaling.
- The Minecraft runtime uses the same synchronous state update order as the lab's validated implementation.

### Remaining differences from the lab
- The current port uses the mod's chunk-based runtime and does not yet expose the full validation experiment suite as a runnable Minecraft scenario.
- Readiness and transport are currently wired through the substrate state rather than through a separate full-grid simulator.
- Full deterministic parity across all lab scenarios remains to be verified with larger experiment harnesses.
