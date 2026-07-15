# Four-State Field Validation Suite

This document translates the six scientific hypotheses into concrete validation experiments for the four-state substrate field: Activation, Memory, Field Depth, and Baseline.

## Experiment 1 — Activation is transient

- Initial conditions: 1x1 cell, activation = 0, memory = 0, depth = 0, baseline = resting.
- Stimulation protocol: inject one activation pulse, then remove all future input and disable transport.
- Measurements: activation time series over 20 ticks; optionally track memory and depth for sanity.
- Expected behavior: activation rises immediately after the pulse and decays monotonically toward zero.
- Pass criteria: activation is positive after injection, does not increase after input cessation, and ends below a small threshold near zero.
- Why this validates the hypothesis: it isolates activation from memory and depth and checks whether the field behaves as a transient perturbation rather than a self-sustaining source.

## Experiment 2 — Memory stores history

- Initial conditions: 1x1 cell, all state variables at baseline.
- Stimulation protocol: apply repeated excitation for 10 ticks, then stop.
- Measurements: activation and memory trajectories during and after the stimulation period.
- Expected behavior: activation rises and then disappears quickly, while memory rises and decays more slowly.
- Pass criteria: memory remains above a non-trivial residual after activation is almost gone.
- Why this validates the hypothesis: it distinguishes a short-lived activation field from a slower, history-bearing state variable.

## Experiment 3 — Memory changes susceptibility

- Initial conditions: two identical 1x1 cells. One is given a history of prior stimulation; the other is left virgin.
- Stimulation protocol: let the history-bearing cell experience sustained excitation, then allow activation to decay. Apply the same impulse to both cells.
- Measurements: peak activation in response to the identical future pulse.
- Expected behavior: the primed cell responds more strongly than the virgin cell.
- Pass criteria: the primed cell’s peak activation is clearly larger than the virgin cell’s peak activation.
- Why this validates the hypothesis: it checks a causal relationship between past history and future responsiveness rather than merely verifying that memory exists.

## Experiment 4 — Depth changes identity

- Initial conditions: two identical 1x1 cells. One receives prolonged stimulation so depth can grow; the other remains untouched.
- Stimulation protocol: allow activation to decay and memory to fade substantially while keeping the depth-bearing history in place. Apply the same impulse again.
- Measurements: peak activation, memory, and depth after the final impulse.
- Expected behavior: the depth-bearing cell still responds differently even when memory is mostly gone.
- Pass criteria: the depth-bearing cell shows a distinct response despite low memory.
- Why this validates the hypothesis: it isolates depth as a structural modifier that persists after memory has decayed.

## Experiment 5 — Spatial memory

- Initial conditions: a 32x32 grid with all cells at baseline.
- Stimulation protocol: inject excitation in the upper-left region, wait, then inject excitation in the lower-right region.
- Measurements: spatial memory maps and local averages in both regions.
- Expected behavior: memory remains localized around the stimulation sites rather than spreading uniformly.
- Pass criteria: each stimulated region shows elevated memory and the center region remains comparatively low.
- Why this validates the hypothesis: it tests whether history becomes spatially encoded rather than only affecting a single cell.

## Experiment 6 — Spatial identity

- Initial conditions: two otherwise identical 16x16 worlds. One is primed with repeated local events; the other is left virgin.
- Stimulation protocol: stop stimulation, let activation decay, then apply the same uniform perturbation to both worlds.
- Measurements: activation response maps for each region after the perturbation.
- Expected behavior: historical regions respond more strongly than virgin regions even under identical future input.
- Pass criteria: primed regions show larger activation than comparably virgin regions under the same perturbation.
- Why this validates the hypothesis: it demonstrates that history is encoded into the world’s structure and changes global behavior in a spatially distributed way.
