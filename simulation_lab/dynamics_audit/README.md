# Dynamics Audit Index

## Verified output
The current simulator run completed successfully and wrote the metrics table to [RESULTS.csv](RESULTS.csv).

## Key results
| Scenario | Max tension | Average tension | Energy | Hotspots | Radius |
| --- | ---: | ---: | ---: | ---: | ---: |
| single_impulse | 1.115211 | 0.012931 | 1.243719 | 0.000000 | 11.313708 |
| continuous | 1.358469 | 1.112094 | 472.396884 | 256.000000 | 21.213203 |
| two_impulses | 0.952909 | 0.023503 | 2.071927 | 0.000000 | 11.313708 |
| remove_input | 1.115211 | 0.012931 | 1.243719 | 0.000000 | 11.313708 |
| minecraft_events | 0.762426 | 0.294046 | 143.648301 | 0.000000 | 21.213203 |

## Documents
- [00_OBJECTIVES.md](00_OBJECTIVES.md) — purpose and scope.
- [01_MODEL_EXTRACTION.md](01_MODEL_EXTRACTION.md) — extracted update rule.
- [02_PARAMETER_TABLE.md](02_PARAMETER_TABLE.md) — parameter values.
- [03_TERM_CONTRIBUTIONS.md](03_TERM_CONTRIBUTIONS.md) — qualitative term analysis.
- [04_DIFFUSION.md](04_DIFFUSION.md) — diffusion behavior.
- [05_DECAY.md](05_DECAY.md) — decay behavior.
- [06_GLOBAL_COUPLING.md](06_GLOBAL_COUPLING.md) — global coupling analysis.
- [07_NONLINEARITY.md](07_NONLINEARITY.md) — nonlinear response.
- [08_STABILITY.md](08_STABILITY.md) — stability observations.

## Interpretation
The continuous and minecraft_events scenarios produced the largest aggregate energy and spatial spread, while the single impulse and remove_input cases remained more localized. This is consistent with the extracted equation being a bounded nonlinear diffusion-coupling system rather than an unbounded growth process.
