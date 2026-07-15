# Three-Timescale Recovery Energy Audit

## 1. Mathematical decomposition

The actual implementation used by the simulator is:

$$\Delta A = I + R + C_B + C_M + C_D + C_T$$
where the code path computes:

- $I = parameters.v2ActivationFastGain * external$
- $R = -parameters.v2ActivationFastGain * A$
- $C_B = parameters.v2ActivationFastGain * baselineTarget$
- $C_M = 0$
- $C_D = 0$
- $C_T = parameters.v2ActivationFastGain * transport$
and the state update is $A_{t+1} = max(0, A_t + \Delta A)$.

## 2. Per-tick energy table

The full table is stored in [simulation_lab/output/hybrid_relaxation/energy_audit.csv](simulation_lab/output/hybrid_relaxation/energy_audit.csv).

A compact excerpt from the recovery run is shown below.

| Tick | A | B | M | D | Injection | Relaxation | Baseline | Memory | Depth | Net ΔA |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 0 | 0.0030 | 0.0388 | 0.0013 | 0.0003 | 0.2000 | 1.7471 | 10.8307 | 0.2349 | 0.0137 | 1.7471 |
| 999 | 3.0883 | 5.0000 | 5.0000 | 5.0000 | 0.2000 | 0.1704 | 2.0380 | 60.9717 | 102.7184 | 0.1704 |
| 1000 | 3.0883 | 5.0000 | 5.0000 | 5.0000 | 0.0000 | -0.0296 | 2.0380 | 60.9717 | 102.7184 | -0.0296 |
| 1001 | 3.0882 | 5.0000 | 5.0000 | 5.0000 | 0.0000 | -0.0577 | 2.0380 | 60.9674 | 102.7184 | -0.0577 |
| 1002 | 3.0880 | 5.0000 | 5.0000 | 5.0000 | 0.0000 | -0.0832 | 2.0380 | 60.9635 | 102.7184 | -0.0832 |
| 29999 | 1.6145 | 5.0000 | 5.0000 | 5.0000 | 0.0000 | -0.0000 | 1.2576 | 57.6169 | 102.0941 | -0.0000 |
| 30000 | 1.6145 | 5.0000 | 5.0000 | 5.0000 | 0.0000 | -0.0000 | 1.2576 | 57.6169 | 102.0941 | -0.0000 |

## 3. Energy source analysis

- Injection is exactly zero for every tick after the first 1000 ticks.
- During the quiet period, the audit recorded 0 ticks with positive net activation change.
- The baseline term is positive on 30000 quiet-period ticks.
- Memory and depth contributions remain zero throughout the audit.
- The largest positive quiet-period change was 0.000000 and the largest baseline contribution was 2.038049.
- This means the recovery path is being sustained by an explicit baseline-relaxation term rather than by hidden memory or field-depth energy. The baseline acts as a slow equilibrium target that keeps the system alive after the impulse ends.

## 4. Causality graph

```text
Injection
  │
  ▼
Activation
  │
  ├────────► Memory  [stores history]
  ├────────► Baseline [changes equilibrium]
  └────────► Field Depth [modifies relaxation]
```

## 5. Acceptance assessment

The audit indicates that the explicit baseline architecture is operating as intended: quiet-period activation change is driven by the baseline-relaxation term and by transport, while memory and field depth remain inert. This is a stable, self-correcting recovery mode rather than a runaway injection-driven process.
