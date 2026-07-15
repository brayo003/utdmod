# World Evolution Report

## Experiment setup

- Grid size: 128x128
- Duration: 100000 ticks
- Record interval: every 100 ticks
- Sources: 5
  - A amplitude=0.25 duration=500 at (16,16)
  - B amplitude=0.5 duration=1000 at (40,40)
  - C amplitude=0.75 duration=2000 at (72,72)
  - D amplitude=1.0 duration=5000 at (96,96)
  - E amplitude=0.6 duration=5000 at (24,96)

## Global time series summary

| Tick | Activation | Baseline | Memory | Depth | Transport | Regions | Avg size | Largest |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 0 | 47.108765 | 635.752667 | 21.865547 | 5.501147 | 3.100000 | 5 | 1.00 | 1 |
| 10000 | 19646.926912 | 81920.000000 | 81920.000000 | 81920.000000 | 0.000000 | 1 | 16297.00 | 16297 |
| 20000 | 19385.662507 | 81920.000000 | 81920.000000 | 81920.000000 | 0.000000 | 1 | 14845.00 | 14845 |
| 30000 | 19885.199803 | 81920.000000 | 81920.000000 | 81920.000000 | 0.000000 | 1 | 15945.00 | 15945 |
| 40000 | 20096.679627 | 81920.000000 | 81920.000000 | 81920.000000 | 0.000000 | 1 | 15801.00 | 15801 |
| 50000 | 20340.020783 | 81920.000000 | 81920.000000 | 81920.000000 | 0.000000 | 1 | 15844.00 | 15844 |
| 60000 | 19355.519371 | 81920.000000 | 81920.000000 | 81920.000000 | 0.000000 | 1 | 15796.00 | 15796 |
| 70000 | 20949.243672 | 81920.000000 | 81920.000000 | 81920.000000 | 0.000000 | 1 | 16070.00 | 16070 |
| 80000 | 22551.776496 | 81920.000000 | 81920.000000 | 81920.000000 | 0.000000 | 1 | 16384.00 | 16384 |
| 90000 | 18465.657593 | 81920.000000 | 81920.000000 | 81920.000000 | 0.000000 | 1 | 15540.00 | 15540 |

## Emergence analysis

- The simulation used only the existing transport, baseline, memory, and field-depth dynamics; no additional mechanics were added.
- The number of connected active regions at the final state was 1.
- The largest connected region contained 16235 cells.
- The final transport radius score was 16235.000000.
- The final overlap score was 16235.000000.
- The first sampled state at tick 0 contained 5 active sources, but the first 10000-tick sample already showed 1 connected active region and the count stayed at 1 through tick 100000.
- Time-series plots were written to output/world_evolution/plots.
- Heatmap snapshots were written to output/world_evolution/heatmaps.

## Equilibrium classification

- The measured activation layer converged to a single connected region rather than multiple stable regional equilibria under this world-scale run.

## Evidence-based answers

1. Does the substrate naturally create distinct long-lived regions? No in this run: the world started with 5 active sources at tick 0, but the measured region count collapsed to 1 by tick 10000 and remained 1 through tick 100000.
2. Do neighboring regions influence one another? Yes: the system did not keep them independent; the initial sources merged into one connected region through the implemented transport dynamics.
3. Does history remain visible after recovery? Yes, but only as a broad persistent memory/baseline imprint rather than as distinct regional scars. The final memory and baseline totals remained large and non-zero after the injections stopped.
4. Does the world evolve toward a single homogeneous equilibrium or multiple regional equilibria? This run evolved toward a single dominant equilibrium with one connected active region rather than multiple regional equilibria.
5. Would this architecture support gameplay based on territorial history and environmental evolution? It can support coarse environmental memory and long-lived state change, but the current measurements do not show strong multi-region territorial identity.
