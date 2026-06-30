FIELD_MODEL_V2

Overview

This document describes the Version 2 field model implemented in GridSimulationV2.

States

- Activation A(x,t): fast state representing current disturbance. Non-negative, not conserved. Rises quickly with external input and neighbor stimulation, decays quickly.
- Memory M(x,t): slow state representing accumulated historical activity. Non-negative. Grows slowly in proportion to activation; decays very slowly.

Transport (neighbor stimulation)

- Neighboring activation stimulates a cell's activation; the source does not lose activation because of neighbor stimulation.
- Neighbor contribution: for each neighbor n, contribution = nA * (1 + w_M * nM / (nM + k)).
- Total transport = transportGain * sum_over_neighbors(contribution).

Activation update (per tick)

Let A = activation(x,t), M = memory(x,t), transport computed as above, external input E(x,t).
Let d_A = v2ActivationDecay, w_M = v2MemoryInfluence, k = v2MemoryEffectK.
Decay is reduced inside high-memory regions by factor (1 - M/(M + k)).

Equation:

A'(x) = max(0, A * max(0, 1 - d_A * (1 - M/(M + k))) + transport + E(x,t))

If `v2ClampMax` > 0, clamp A' to <= v2ClampMax.

Memory update (per tick)

Memory increases only from activation, not external input.

M'(x) = max(0, M + v2MemoryGain * A - v2MemoryDecay * M)

Notes

- Asymmetric reinforcement: neighbor contribution is amplified by neighbor memory. Thus when a fresh hotspot occurs near an old memory basin, the fresh activation receives a multiplicative boost, while the old memory is updated only slightly because memoryGain is small.
- The transport model is intentionally non-conservative: source activation is not reduced by stimulating neighbors.

Assumptions and limitations

- The numerical scheme uses synchronous updates (compute next state from current snapshots), then commit.
- Parameters (transportGain, memoryGain, etc.) are empirical and must be tuned for stability.
- This model is a phenomenological two-state model, not derived from first principles.

Documented defaults are in `ParameterSet` as `v2*` fields.
