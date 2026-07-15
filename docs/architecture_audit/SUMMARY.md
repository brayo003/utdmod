# Summary

> **[UPDATED 2026-07-15]** Corrected framing: the dominant state variable is per-chunk
> substrate state (A, M, D, B), not a chunk-local tension scalar.

---

## Overall conclusion

The codebase implements a server-authoritative spatial field simulation. The core dynamics are
driven by a 4-variable substrate field (activation, memory, field depth, baseline) that is
integrated per-chunk on the server tick using a coupled nonlinear update rule
(`FieldDerivative.computeUnified()`). Tension (T(x)) is a derived observation metric computed
from substrate variables on demand, not an independently stored or evolved quantity.

Regions are connected components of substrate-active chunks and are rebuilt every 20 ticks.
Global tension is the spatial mean of T(x) across loaded chunks, recomputed every tick.
Storms, ecology, and presentation are downstream consumers of this field.

---

## What dominates behavior

The dominant runtime subsystem is `ChunkTensionData.integrateAllSubstrateStates()`,
called every 5 ticks from `TensionServerTick.tickSubstrate()`. It applies
`FieldDerivative.computeUnified()` — a 4-variable Euler integration step — for every
loaded substrate-bearing chunk. This is where the simulation dynamics live.

---

## What does not dominate behavior

- **Global tension** is a derived summary. It does not have an ODE. It does not drive chunk dynamics directly.
- **Storms** are selected from regions and operate downstream of the substrate field.
- **Ecology** mutates world blocks but does not inject tension back into the substrate.
- **Presentation** is purely client-side feedback; it does not change simulation state.

---

## Framing note: tension vs substrate

Older documentation uses "tension" as the primary variable. In current code:

- The **primary variable** is `SubstrateState` (A, M, D, B).
- **Tension T(x)** = `(0.40·A/(1+A) + 0.25·M/(1+M) + 0.20·D/(1+D)) / 0.85` — a read-only observation.
- All thresholds, state machines, and region formation operate on T(x).
- Nothing stores or accumulates a "tension value" per chunk. Only substrate state is persisted.

---

## Evidence-based verdict

The system is a genuine spatial dynamical field simulation. It supports bistability (confirmed
by telemetry in `analysis/UTD_TELEMETRY_REPORT.md`), spatial topology, autonomous field
maintenance after perturbation, and temporal memory through field depth. The principal
scientific issue identified in telemetry is that effective memory gain is suppressed relative
to configured parameters — likely from the logistic saturation and depth-retention factors in
`FieldDerivative.computeUnified()`. The system is nonetheless functional and produces
non-trivial dynamical behavior.
