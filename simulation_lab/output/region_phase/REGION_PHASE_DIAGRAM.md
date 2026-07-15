# Region Phase Diagram

This report characterizes the current equations over a parameter sweep while keeping the implementation fixed.

## Summary table

| Run | Parameter | Value | Classification | Connected regions | Mean activation | Mean baseline | Mean memory | Mean depth | Equilibrium tick |
|---|---|---:|---|---:|---:|---:|---:|---:|---:|
| baseline | null | 0.000000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2ActivationRelaxationGain | v2ActivationRelaxationGain | 0.090000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2ActivationRelaxationGain | v2ActivationRelaxationGain | 0.180000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2ActivationRelaxationGain | v2ActivationRelaxationGain | 0.360000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2BaselineAdaptationRate | v2BaselineAdaptationRate | 0.001000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2BaselineAdaptationRate | v2BaselineAdaptationRate | 0.002000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2BaselineAdaptationRate | v2BaselineAdaptationRate | 0.004000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2BaselineHealingRate | v2BaselineHealingRate | 0.000250 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2BaselineHealingRate | v2BaselineHealingRate | 0.000500 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2BaselineHealingRate | v2BaselineHealingRate | 0.001000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2MemoryGain | v2MemoryGain | 0.010000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2MemoryGain | v2MemoryGain | 0.020000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2MemoryGain | v2MemoryGain | 0.050000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2MemoryDecay | v2MemoryDecay | 0.000500 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2MemoryDecay | v2MemoryDecay | 0.001000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2MemoryDecay | v2MemoryDecay | 0.002000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2TransportGain | v2TransportGain | 0.040000 | Metastable regions that eventually merge | 1 | 1.455035 | 4.997897 | 5.000000 | 5.000000 | 18000 |
| v2TransportGain | v2TransportGain | 0.080000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v2TransportGain | v2TransportGain | 0.160000 | Metastable regions that eventually merge | 1 | 1.668942 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v3FieldDepthGain | v3FieldDepthGain | 0.000000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v3FieldDepthGain | v3FieldDepthGain | 0.000010 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v3FieldDepthGain | v3FieldDepthGain | 0.000020 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v3FieldDepthDecay | v3FieldDepthDecay | 0.000000 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v3FieldDepthDecay | v3FieldDepthDecay | 0.000001 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |
| v3FieldDepthDecay | v3FieldDepthDecay | 0.000010 | Metastable regions that eventually merge | 1 | 1.491532 | 5.000000 | 5.000000 | 5.000000 | 18000 |

## Interpretation

- The current mathematical architecture appears to possess only a single attractor over the explored parameter space.
- No parameter regime in this sweep produced multiple stable regional equilibria; the initially separated regions always collapsed toward one connected equilibrium.

## Output files

- Heatmaps and regional plots were written under the per-run directories in the output folder.
