# Canonical Substrate Port Audit

## Scope and source of truth

This document is the canonical lab-to-Minecraft audit for the substrate port. The authoritative mathematical source is the laboratory implementation in [simulation_lab/src/GridSimulationV2.java](simulation_lab/src/GridSimulationV2.java) and the parameter set in [simulation_lab/src/ParameterSet.java](simulation_lab/src/ParameterSet.java). The Minecraft runtime implementation is the port target in [src/main/java/com/utdmod/tension/SubstrateState.java](src/main/java/com/utdmod/tension/SubstrateState.java), [src/main/java/com/utdmod/tension/ChunkTensionData.java](src/main/java/com/utdmod/tension/ChunkTensionData.java), and [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java).

The audit asks one strict question: does the Minecraft runtime implement the same substrate mathematics that the lab model uses, and where does it differ?

## Executive verdict

The port is partially faithful.

- The core state machine is present in Minecraft as a per-chunk substrate object with activation, memory, transport, and field-depth state.
- The activation update is directly aligned with the lab equation.
- The field-depth update is present and uses the same basic structure as the lab model.
- The transport contribution uses the same conceptual form as the lab model, but the Minecraft runtime adds a gameplay-oriented readiness/capacity/distance layer that is not present in the lab source.
- The memory update is the main remaining divergence: the lab implementation uses a saturating memory-growth term, while the current Minecraft runtime uses a simpler linear form.

## Canonical state variables

The lab model is a two-state field system with a slow structural variable. The port uses the same conceptual variables, with a small amount of gameplay-specific instrumentation added on top.

| Symbol | Meaning | Lab source | Minecraft runtime | Status |
|---|---|---|---|---|
| $A$ | Activation / local excitation | Present in [simulation_lab/src/GridSimulationV2.java](simulation_lab/src/GridSimulationV2.java) | Stored in SubstrateState as activation | Implemented |
| $M$ | Memory / persistence / historical influence | Present in [simulation_lab/src/GridSimulationV2.java](simulation_lab/src/GridSimulationV2.java) | Stored in SubstrateState as memory | Implemented |
| $C$ | Field depth / structural capacity | Present in [simulation_lab/src/GridSimulationV2.java](simulation_lab/src/GridSimulationV2.java) | Stored in SubstrateState as fieldDepth | Implemented |
| $T$ | Incoming transport contribution | Derived in the lab step loop | Stored in SubstrateState as transport | Implemented |
| $E$ | External input | Present as an injection term in the lab update | Passed into SubstrateState.advance as externalInput | Implemented |
| $R$ | Readiness | Not a lab state variable | Added in Minecraft as a derived runtime observability term | Runtime-specific |
| $\rho$ | Transport radius | Not a lab state variable | Derived from field depth and stored in SubstrateState | Runtime-specific |
| $K$ | Destination capacity | Not a lab state variable | Derived from activation and stored in SubstrateState | Runtime-specific |
| $P$ | Peak activation | Not a lab state variable | Stored in SubstrateState for diagnostics | Runtime-specific |
| $\Psi$ | Convergence | Not a lab state variable | Stored in SubstrateState for diagnostics | Runtime-specific |

## Canonical equations

The lab implementation uses synchronous updates over a grid: each cell is updated from the previous tick’s snapshot.

### 1. Transport contribution

For each neighbor $n$, the lab code forms a per-neighbor contribution

$$
\text{contrib}_n = \frac{A_n}{1 + A_n} \cdot \left(1 + w_M \frac{M_n}{M_n + k}\right)
$$

and then scales it by distance and gain:

$$
T = g_T \sum_{n \in \mathcal{N}(i)} \text{contrib}_n \cdot w_d(d_{in})
$$

The port uses the same conceptual structure in [src/main/java/com/utdmod/tension/ChunkTensionData.java](src/main/java/com/utdmod/tension/ChunkTensionData.java), where transport is accumulated from neighboring substrate states and then passed to the update step.

### 2. Activation update

The lab activation equation is:

$$
A_{t+1} = \max\left(0,\; A_t \cdot \max\left(0, 1 - d_A \left(1 - \frac{M_t}{M_t + k}\right)\right) + T_t + E_t\right)
$$

and then clamped if requested:

$$
A_{t+1} := \min(A_{t+1}, A_{\max})
$$

The Minecraft port matches this equation in [src/main/java/com/utdmod/tension/SubstrateState.java](src/main/java/com/utdmod/tension/SubstrateState.java).

### 3. Memory update

> **[UPDATED 2026-07-15]** The claim below that the Minecraft runtime uses "a simpler linear form"
> is **outdated**. The current implementation uses `FieldDerivative.computeUnified()` which
> implements a significantly more complex nonlinear memory equation. See below.

The lab memory update is:

$$
M_{t+1} = \max\left(0,\; M_t + g_M \frac{A_t}{1 + A_t} - r_M M_t\right)
$$

The **current** Minecraft runtime (`FieldDerivative.computeUnified()`) uses:

$$
\Delta M = \underbrace{\left(1 - \frac{M}{M_{\max}}\right) \cdot \text{formation}(A,D) \cdot (0.30 + 0.70 \cdot \text{peakStrength})}_{\text{logistic saturation formation}}
           + \underbrace{-0.4 \cdot r_M(D) \cdot M \cdot (2 + 3 \cdot (1 - \min(1,A)))}_{\text{activity-weighted forgetting}}
           + \underbrace{\text{anti-diffusion} + \text{diffusion}}_{\text{spatial terms}}
$$

where `formation(A,D)` is itself nonlinear (saturating in A, depth-modulated), and `r_M(D)` is
depth-dependent. This is substantially more complex than the lab's saturating form and more
complex than any linear form. The principal divergence from the lab version is the logistic
ceiling `(1 - M/M_max)`, the activity-gated anti-diffusion, and the depth-retention modulation of
the decay rate. Telemetry in `analysis/UTD_TELEMETRY_REPORT.md` confirms the effective memory
gain is suppressed relative to the configured parameter value, likely due to the capacity factor.

This remains the main area where runtime behavior differs from the simple theoretical formula
but the divergence is more complex than previously noted.

### 4. Field-depth update

The lab field-depth update is:

$$
C_{t+1} = \max\left(0,\; C_t + g_C (A_t M_t) - r_C C_t\right)
$$

with saturating growth if needed. The Minecraft runtime uses the same structure in [src/main/java/com/utdmod/tension/SubstrateState.java](src/main/java/com/utdmod/tension/SubstrateState.java).

## Dependency graph

The runtime dependency path is:

1. Player movement, mining, or other gameplay events increase local tension in [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java).
2. The chunk pipeline calls [src/main/java/com/utdmod/tension/ChunkTensionData.java](src/main/java/com/utdmod/tension/ChunkTensionData.java) to build neighbor transport and update the substrate state.
3. [src/main/java/com/utdmod/tension/SubstrateState.java](src/main/java/com/utdmod/tension/SubstrateState.java) applies the activation, memory, transport, and field-depth update equations.
4. The updated state feeds diagnostics, persistence, and the larger chunk/global tension loop.

A compact view is:

```text
Gameplay input
  -> chunk tension injection
  -> substrate transport assembly
  -> SubstrateState.advance
  -> activation / memory / field-depth update
  -> chunk persistence + diagnostics + global coupling
```

## Parameter table

The lab parameter names and the Minecraft port names are aligned by purpose.

| Lab parameter | Meaning | Minecraft field | Default |
|---|---|---|---|
| $g_T$ | transport gain | substrateTransportGain | 0.08 |
| $d_A$ | activation decay | substrateActivationDecay | 0.45 |
| $g_M$ | memory gain | substrateMemoryGain | 0.02 |
| $r_M$ | memory decay | substrateMemoryDecay | 0.001 |
| $w_M$ | memory influence on transport | substrateReadinessScale / runtime readiness logic | 2.0 scale in current runtime |
| $k$ | memory effect constant | substrateMemoryEffectK | 0.1 |
| $A_{\max}$ | activation clamp | substrateClampMax | 5.0 |
| $g_C$ | field-depth gain | substrateFieldDepthGain | 0.0 |
| $r_C$ | field-depth decay | substrateFieldDepthDecay | 0.0001 |
| $R_C$ | field-depth radius effect | substrateFieldDepthRadius | 0.0 |
| $\epsilon$ | field-depth effect constant | substrateFieldDepthEffectK | 0.1 |

## Execution order in the Minecraft runtime

The effective runtime order is:

1. Server tick increments.
2. Player movement and other gameplay actions inject local tension.
3. [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java) processes chunk tension every few ticks.
4. For each relevant chunk, [src/main/java/com/utdmod/tension/ChunkTensionData.java](src/main/java/com/utdmod/tension/ChunkTensionData.java) gathers neighboring substrate states.
5. Transport is assembled and passed to [src/main/java/com/utdmod/tension/SubstrateState.java](src/main/java/com/utdmod/tension/SubstrateState.java).
6. The substrate state updates activation, memory, transport, and field depth.
7. The updated values are persisted, logged, and used by the broader chunk/global tension system.

## Gameplay inputs and outputs

| Gameplay input | Entry point | Translation into substrate state | Output effect |
|---|---|---|---|
| Player movement | [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java) | Added as local tension and then as external input to the substrate update | Raises activation and can trigger transport propagation |
| Mining | [src/main/java/com/utdmod/tension/ChunkTensionData.java](src/main/java/com/utdmod/tension/ChunkTensionData.java) | Adds local tension and contamination memory | Elevates chunk tension and substrate activity |
| Storms and world events | [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java) | Feed into the chunk/global tension loop | Can amplify or suppress substrate activity |
| Chunk persistence | [src/main/java/com/utdmod/tension/ChunkTensionData.java](src/main/java/com/utdmod/tension/ChunkTensionData.java) | Substrate state is stored per chunk | Enables continuity across saves and subsequent ticks |
| Diagnostics | [src/main/java/com/utdmod/diag/DiagnosticLogs.java](src/main/java/com/utdmod/diag/DiagnosticLogs.java) | Reads substrate values | Exposes activation, memory, depth, readiness, radius, capacity, and convergence |

## Missing or incomplete subsystems

The following lab-side capabilities are not fully present in the current Minecraft runtime:

- Full deterministic replay from a recorded substrate snapshot.
- A full grid-based transport simulation with explicit per-cell traces and exportable time series.
- A literal lab-style memory-growth term using the saturating $A/(1+A)$ form.
- A complete readiness/transport pipeline that is identical to the lab’s transport logic rather than a gameplay-adapted variant.
- Full client-side rendering or visualization of the substrate field as a first-class simulation surface.
- A separate experiment harness that can replay scripted impulse scenarios against the runtime state.

## Files involved in the port

### Canonical lab sources
- [simulation_lab/src/GridSimulationV2.java](simulation_lab/src/GridSimulationV2.java)
- [simulation_lab/src/ParameterSet.java](simulation_lab/src/ParameterSet.java)
- [simulation_lab/src/test/TransportPipelineAuditTest.java](simulation_lab/src/test/TransportPipelineAuditTest.java)
- [simulation_lab/src/test/WorldValidationExperiments.java](simulation_lab/src/test/WorldValidationExperiments.java)

### Minecraft runtime port
- [src/main/java/com/utdmod/tension/SubstrateState.java](src/main/java/com/utdmod/tension/SubstrateState.java)
- [src/main/java/com/utdmod/tension/ChunkTensionData.java](src/main/java/com/utdmod/tension/ChunkTensionData.java)
- [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java)
- [src/main/java/com/utdmod/config/UTDConfig.java](src/main/java/com/utdmod/config/UTDConfig.java)
- [src/main/java/com/utdmod/diag/DiagnosticLogs.java](src/main/java/com/utdmod/diag/DiagnosticLogs.java)
- [src/test/java/com/utdmod/tension/SubstrateStateTest.java](src/test/java/com/utdmod/tension/SubstrateStateTest.java)

## Verification evidence

The current audit is grounded in direct code inspection and an executable regression test:

- The lab equations were read from [simulation_lab/src/GridSimulationV2.java](simulation_lab/src/GridSimulationV2.java).
- The Minecraft implementation was read from [src/main/java/com/utdmod/tension/SubstrateState.java](src/main/java/com/utdmod/tension/SubstrateState.java), [src/main/java/com/utdmod/tension/ChunkTensionData.java](src/main/java/com/utdmod/tension/ChunkTensionData.java), and [src/main/java/com/utdmod/core/TensionServerTick.java](src/main/java/com/utdmod/core/TensionServerTick.java).
- The regression suite for the substrate state was executed with:

```bash
./gradlew test --tests com.utdmod.tension.SubstrateStateTest
```

The current workspace state should be treated as a faithful core-port of the activation and field-depth logic, with transport wired into the chunk runtime and a small number of runtime-specific extensions, but not yet a complete literal copy of the laboratory model in every detail.
