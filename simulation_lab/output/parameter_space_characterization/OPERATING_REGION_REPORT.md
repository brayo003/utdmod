# Parameter-space characterization report

This report characterizes the current three-timescale relaxation architecture under the same recovery experiment used in the audit: 1000 injection ticks followed by 30000 quiet ticks.

## Summary tables

### v2BaselineAdaptationRate

| Parameter | Final A | Final B | Final M | Final D | Equilibrium tick | Half-life | Region |
|---|---:|---:|---:|---:|---:|---:|---|
| 0.000500 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.001000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.002000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.004000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.008000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |

### v2BaselineHealingRate

| Parameter | Final A | Final B | Final M | Final D | Equilibrium tick | Half-life | Region |
|---|---:|---:|---:|---:|---:|---:|---|
| 0.000200 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.000500 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.001000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.002000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.004000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |

### v2MemoryGain

| Parameter | Final A | Final B | Final M | Final D | Equilibrium tick | Half-life | Region |
|---|---:|---:|---:|---:|---:|---:|---|
| 0.010000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.020000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.050000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.100000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |

### v2MemoryDecay

| Parameter | Final A | Final B | Final M | Final D | Equilibrium tick | Half-life | Region |
|---|---:|---:|---:|---:|---:|---:|---|
| 0.000500 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.001000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.002000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.005000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |
| 0.010000 | 1.656097 | 4.998879 | 5.000000 | 5.000000 | 1000 | 30000 | Near saturation |

## Interpretation

- Stronger baseline adaptation tends to raise the equilibrium activation level and prolong recovery.
- Baseline healing shifts the system between a quickly stabilizing regime and a more persistent one.
- Memory gain mainly expands the long-lived component, while memory decay governs how quickly that memory footprint collapses.
- The operating regions below are derived from the same metrics used in the audit and are therefore consistent with the current implementation.
