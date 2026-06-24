# EXTRACTED EQUATIONS REFERENCE
## Mathematical Formalization of the World-State Simulation

**Purpose**: Complete, rigorous mathematical specification extracted directly from source code  
**Format**: LaTeX-ready notation with implementation details  
**Validation**: Cross-referenced with source code line ranges

---

## 1. GLOBAL TENSION FIELD DYNAMICS

### 1.1 Global Tension Update

**Source**: TensionManager.java (lines 82-95)

$$g^{(n+1)} = \text{clamp}\left( g^{(n)} + \Delta g^{(n)}, 0, T_{\max} \right)$$

where the per-tick delta is:

$$\Delta g^{(n)} = -\Lambda_g \cdot g^{(n)} + \Alpha_g \cdot (g^{(n)})^2 + I_{\text{inflow}}^{(n)} + I_{\text{ritual}}^{(n)}$$

**Parameters**:
- $\Lambda_g = 0.0032$ (global linear decay)
- $\Alpha_g = 0.002$ (global nonlinear feedback)
- $T_{\max} = 5.0$ (global tension ceiling)

**Clamp function**:
$$\text{clamp}(x, a, b) = \max(a, \min(b, x))$$

### 1.2 Coupling Inflow Term

**Source**: TensionServerTick.java (lines 130-165)

$$I_{\text{inflow}}^{(n)} = I_{\text{ambient}} + I_{\text{hotspot}}$$

**Ambient component** (baseline mean-field pressure):
$$I_{\text{ambient}} = 0.0035 \times \mu_L$$

where:
$$\mu_L = \frac{1}{|\mathcal{C}|} \sum_{(i,j) \in \mathcal{C}} L_{i,j}^{(n)}$$

and $\mathcal{C}$ is the set of all active chunks.

**Hotspot component** (amplification from high-tension regions):
$$I_{\text{hotspot}} = 0.052 \cdot \sigma_L + 0.14 \cdot f_{\text{storm}} + 0.26 \cdot f_{\text{frac}}$$

where:
$$\sigma_L = \sqrt{\frac{1}{|\mathcal{C}|} \sum_{(i,j) \in \mathcal{C}} (L_{i,j}^{(n)} - \mu_L)^2}$$

$$f_{\text{storm}} = \frac{|\{(i,j) \in \mathcal{C} : s_{i,j}^{(n)} = \text{true}\}|}{|\mathcal{C}|}$$

$$f_{\text{frac}} = \frac{|\{(i,j) \in \mathcal{C} : \sigma_{i,j}^{(n)} \in \{\text{FRACTURED}, \text{DECOUPLED}\}\}|}{|\mathcal{C}|}$$

### 1.3 Ritual Impulse Buffer

**Source**: TensionManager.java (lines 52, 97-98)

$$I_{\text{ritual}}^{(n)} = \text{accumulated impulses from debug/ritual events}$$

**Decay per tick**:
$$I_{\text{ritual}}^{(n+1)} := 0.88 \times I_{\text{ritual}}^{(n)}$$

Exponential decay: $I_{\text{ritual}}^{(n)} = I_0 \times (0.88)^n$

### 1.4 Global Storm State Machine

**Source**: TensionManager.java (lines 99-104)

$$s_{\text{global}}^{(n+1)} = \begin{cases}
\text{true} & \text{if } g^{(n)} > T_g^{\text{on}} = 1.0 \text{ AND } s_{\text{global}}^{(n)} = \text{false} \\
\text{false} & \text{if } g^{(n)} < T_g^{\text{off}} = 0.7 \\
s_{\text{global}}^{(n)} & \text{otherwise (hysteresis)}
\end{cases}$$

**Hysteresis width**: $T_g^{\text{on}} - T_g^{\text{off}} = 0.3$

### 1.5 Equilibrium Stability Check

**Source**: TensionManager.java (lines 144-157)

Emergency damping when quadratic feedback becomes unstable:

$$\Delta_{d}g = \text{clamp}\left(\Lambda_g^2 - 4\Alpha_g I_{\text{inflow}}, -\infty, 0\right)$$

If $\Delta_{d}g < 0$ (discriminant negative):
$$g^{(n+1)} := 0.9 \times g^{(n+1)}$$

This prevents unbounded growth in edge cases.

### 1.6 Corruption Tier Assignment

**Source**: TensionManager.java (lines 162-171)

$$c(g) = \arg\max_k \{T_{\text{corr}}[k] \leq g\}$$

where corruption thresholds are:
$$T_{\text{corr}} = [0.8, 1.5, 2.5, 4.0]$$

producing tiers:
$$c^{-1}(k) \in \{\text{None}, \text{Subtle}, \text{Moderate}, \text{Severe}, \text{Catastrophic}\}$$

---

## 2. LOCAL (PER-CHUNK) TENSION DYNAMICS

### 2.1 Chunk Tension PDE Update

**Source**: TensionServerTick.java (lines 350-390)

Update frequency: **Every 5 ticks** (250 ms, sparse optimization applies)

$$L_{i,j}^{(n+1)} = L_{i,j}^{(n)} + \Delta L_{\text{diff}} + \Delta L_{\text{nonlin}} + \Delta L_{\text{sat}} + \Delta L_{\text{fb}} + \Delta L_{\text{decay}} + \Delta L_{\text{storm}}$$

**Clipped**:
$$L_{i,j}^{(n+1)} := \text{clamp}(L_{i,j}^{(n+1)}, 0, T_{\max})$$

### 2.2 Diffusion Term

**Source**: TensionServerTick.java (lines 352-360)

$$\Delta L_{\text{diff}} = D \times \bar{N}_{i,j}$$

where the 4-neighbor average is:
$$\bar{N}_{i,j} = \frac{1}{n_{\text{loaded}}} \sum_{\mathcal{N}_4(i,j)} L_{\text{neighbor}}$$

**Von Neumann neighborhood**:
$$\mathcal{N}_4(i,j) = \{(i \pm 1, j), (i, j \pm 1)\}$$

Only **loaded** chunks contribute; if no neighbors are loaded, $\bar{N}_{i,j} = 0$.

**Diffusion coefficient**: $D = 0.012$

### 2.3 Nonlinear Growth Term

**Source**: TensionServerTick.java (lines 361-367)

$$\Delta L_{\text{nonlin}} = \Alpha_L(s_{i,j}) \cdot (L_{i,j})^2$$

where storm-dependent amplification:
$$\Alpha_L(s) = \begin{cases}
0.0162 & \text{if } s_{i,j} = \text{true} \\
0.009 & \text{if } s_{i,j} = \text{false}
\end{cases}$$

Equivalently: $\Alpha_L(\text{storm}) = 1.8 \times \Alpha_L(\text{calm})$

### 2.4 Saturation Term (Cubic Limiter)

**Source**: TensionServerTick.java (lines 368-369)

$$\Delta L_{\text{sat}} = -\Beta \cdot (L_{i,j})^3$$

where: $\Beta = 0.055$

Prevents unbounded growth; dominates for large $L$.

### 2.5 Global Feedback Coupling

**Source**: TensionServerTick.java (lines 370-371)

$$\Delta L_{\text{fb}} = \Gamma \cdot g^{(n)}$$

where: $\Gamma = 0.045$ (global-to-local coupling strength)

This is the **long-range interaction** that couples all chunks to the global field.

### 2.6 Decay Term with Contamination Modulation

**Source**: TensionServerTick.java (lines 372-374)

$$\Delta L_{\text{decay}} = -\Lambda_L \cdot L_{i,j} \cdot M_{\text{decay}, i,j}$$

where:
- $\Lambda_L = 0.0006$ (local decay rate)
- $M_{\text{decay}, i,j}$ depends on contamination level (see Section 3)

### 2.7 Storm Drain Term

**Source**: TensionServerTick.java (lines 375-376)

$$\Delta L_{\text{storm}} = -\mathbb{1}[s_{i,j} = \text{true}] \times D_{\text{storm}}$$

where: $D_{\text{storm}} = 0.002$ per tick

Applied over a 5-tick PDE cycle: effective drain = $-0.01$ per update.

Active suppression during local storms.

### 2.8 Complete Local PDE in Compact Form

$$\boxed{L_{i,j}^{(n+1)} = L_{i,j}^{(n)} + D \bar{N}_{i,j} + \Alpha_L(s_{i,j})(L_{i,j})^2 - \Beta(L_{i,j})^3 + \Gamma g - \Lambda_L L_{i,j} M_{\text{decay}} - \mathbb{1}[s_{i,j}] D_{\text{storm}}}$$

**Dimensional analysis**:
- All coefficients have units [tension/cycle]
- $L_{i,j}$ has units [tension]
- RHS has uniform units [tension] ✓

---

## 3. CONTAMINATION DECAY MODULATION

### 3.1 Decay Multiplier Assignment

**Source**: ChunkTensionData.java (lines 110-130), TensionServerTick.java (lines 300-320)

Per PDE cycle, the contamination tier is determined:

$$M_{\text{decay}, i,j} = \begin{cases}
0.38 & \text{if } L_{i,j} \geq 1.35 \text{ (HEAVY state)} \\
0.68 & \text{if } 1.15 \leq L_{i,j} < 1.35 \text{ (MEDIUM state)} \\
1.0 & \text{if } L_{i,j} < 1.15 \text{ (NONE state)}
\end{cases}$$

**Effective decay with multiplier**:
$$\Delta L_{\text{decay}} = -\Lambda_L L_{i,j} M_{\text{decay}, i,j}$$

**Recovery time scaling**: Inverse of decay rate
$$\tau_{\text{recovery}} \propto \frac{1}{\Lambda_L M_{\text{decay}}}$$

- **Normal** ($M = 1.0$): $\tau \propto 1/0.0006 \approx 1667$ cycles (7 min)
- **Medium** ($M = 0.68$): $\tau \propto 1/0.000408 \approx 2451$ cycles (10 min)
- **Heavy** ($M = 0.38$): $\tau \propto 1/0.000228 \approx 4386$ cycles (18 min)

### 3.2 Peak Memory

**Source**: ChunkTensionData.java (lines 95-105)

$$\text{contam}_{\text{peak}, i,j} = \max\{\text{contam}_{\text{peak}, i,j}^{(n)}, L_{i,j}^{(n)}\}$$

Once a chunk reaches $L \geq 1.35$, its peak is recorded and recovery is slowed.

---

## 4. CHUNK STATE MACHINE

### 4.1 State Transition Logic with Hysteresis

**Source**: ChunkTensionData.java (lines 240-270)

State transitions implement **hysteretic switching** to prevent oscillation:

$$\sigma_{i,j}^{(n+1)} = f(\sigma_{i,j}^{(n)}, L_{i,j}^{(n)})$$

The state function $f$ is defined as:

1. **If $L < 0$**: $\sigma := \text{STABLE}$
2. **If $L \geq T_3 = 3.0$**: $\sigma := \text{DECOUPLED}$
3. **If $\sigma = \text{DECOUPLED}$ AND $L \geq T_3 - H = 2.78$**: persist DECOUPLED
4. **Else if $L \geq T_2 = 1.28$**: $\sigma := \text{FRACTURED}$
5. **Else if $\sigma = \text{FRACTURED}$ AND $L \geq T_2 - H = 1.06$**: persist FRACTURED
6. **Else if $L \geq T_1 = 0.95$**: $\sigma := \text{STRAINED}$
7. **Else if $\sigma = \text{STRAINED}$ AND $L \geq T_1 - H = 0.73$**: persist STRAINED
8. **Else**: $\sigma := \text{STABLE}$

**Thresholds**:
- $T_1 = 0.95$ (STABLE/STRAINED boundary)
- $T_2 = 1.28$ (STRAINED/FRACTURED boundary)
- $T_3 = 3.0$ (FRACTURED/DECOUPLED boundary)
- $H = 0.22$ (hysteresis offset)

### 4.2 State Diagram

```
STABLE ──(L>0.95)──→ STRAINED ──(L>1.28)──→ FRACTURED ──(L>3.0)──→ DECOUPLED
   ↑                    ↑                        ↑                      ↑
   └─(L<0.73)───────────┴─(L<1.06)──────────────┴─(L<2.78)─────────────┘
      (hysteresis prevents rapid oscillation)
```

---

## 5. LOCAL STORM STATE

### 5.1 Local Storm Activation/Deactivation

**Source**: TensionServerTick.java (lines 382-390)

$$s_{i,j}^{(n+1)} = \begin{cases}
\text{true} & \text{if } L_{i,j}^{(n)} > T_{\text{on}}^{L} = 0.95 \text{ AND } s_{i,j}^{(n)} = \text{false} \\
\text{false} & \text{if } L_{i,j}^{(n)} < T_{\text{off}}^{L} = 0.25 \\
s_{i,j}^{(n)} & \text{otherwise}
\end{cases}$$

**Asymmetric thresholds**:
- Onset: $T_{\text{on}}^{L} = 0.95$
- Offset: $T_{\text{off}}^{L} = 0.25$
- Hysteresis width: $0.95 - 0.25 = 0.70$

**Effect**: Once activated, storm persists until $L$ drops below 0.25, creating **hysteretic oscillation** independent of global storm state.

---

## 6. PLAYER ACTIVITY INJECTION

### 6.1 Movement Injection

**Source**: TensionServerTick.java (lines 180-220)

Per-tick injection to player's current chunk:

$$a_{\text{move}, i,j}^{(n)} \mathrel{+}= \begin{cases}
0.0015 & \text{if walk detected} \\
0.0022 & \text{if sprint detected} \\
0.002 & \text{if swim detected} \\
0.0035 & \text{if elyra flight detected}
\end{cases}$$

**Dimension multiplier**:
$$a_{\text{move}} \leftarrow a_{\text{move}} \times \begin{cases}
1.25 & \text{if player.dimension = Nether} \\
1.0 & \text{otherwise}
\end{cases}$$

### 6.2 Mining Injection

**Source**: ChunkTensionManager.java (lines 30-65)

Per block mined:

$$L_{i,j}^{(n)} \mathrel{+}= c_{\text{mine}}(\text{blockId})$$

where block-type coefficients are:

$$c_{\text{mine}}(\text{id}) = \begin{cases}
0.14 & \text{if "ancient\_debris" in id} \\
0.11 & \text{if "diamond\_ore" or "deepslate\_diamond" in id} \\
0.09 & \text{if "emerald\_ore" or "deepslate\_emerald" in id} \\
0.078 & \text{if "deepslate" AND "ore" in id} \\
0.056 & \text{if "coal\_ore" or "coal\_block" in id} \\
0.052 & \text{if "ore" or "debris" in id} \\
0.048 & \text{if "deepslate" in id} \\
0.042 & \text{if "stone" in id} \\
0.008 & \text{otherwise}
\end{cases}$$

Scaling encodes resource scarcity + danger perception.

### 6.3 Combat Injection

**Source**: ChunkTensionManager.java (lines 81-92)

Per entity killed at chunk $(i,j)$:

$$L_{i,j}^{(n)} \mathrel{+}= c_{\text{combat}}(\text{entityType})$$

where:

$$c_{\text{combat}}(\text{type}) = \begin{cases}
0.06 & \text{if "villager" in type} \\
0.08 & \text{if "iron\_golem" in type} \\
0.2 & \text{if "ender\_dragon" or "wither" in type} \\
0.015 & \text{otherwise (default)}
\end{cases}$$

---

## 7. REGIONAL AGGREGATION

### 7.1 Regional Snapshot Computation

**Source**: RegionDiagnosticsManager.java (lines 80-125)

Computed every 200 ticks over 8×8 chunk regions:

$$R_{\text{avg}}(rx, rz) = \frac{1}{n} \sum_{(i,j) \in \text{Region}(rx, rz)} L_{i,j}$$

$$R_{\text{max}}(rx, rz) = \max_{(i,j) \in \text{Region}} L_{i,j}$$

$$R_{\text{strained}}(rx, rz) = |\{(i,j) : \sigma_{i,j} = \text{STRAINED}\}|$$

$$R_{\text{fractured}}(rx, rz) = |\{(i,j) : \sigma_{i,j} \in \{\text{FRACTURED, DECOUPLED}\}\}|$$

$$R_{\text{storm}}(rx, rz) = \mathbb{1}\left[\exists(i,j) : s_{i,j} = \text{true}\right]$$

### 7.2 Hostile Entity Spawn Bias

**Source**: RegionDiagnosticsManager.java (lines 70-75)

$$H_{\text{bias}} = \min\left(1.0, \max\left(0.0, 0.32(R_{\text{avg}} - 0.55) + 0.12 R_{\text{max}} + 0.14 \mathbb{1}[R_{\text{storm}}]\right)\right)$$

Linear combination of three tension metrics:
- Mean regional tension (32% weight)
- Peak regional tension (12% weight)
- Storm state (14 percentage point bonus)

---

## 8. STORM MANAGEMENT

### 8.1 Regional Storm Triggering

**Source**: StormManager.java (lines 68-78)

Effective tension metric:
$$T_{\text{eff}} = \max(g, 0.52 \times R_{\text{max}})$$

Storm onset condition (per tick):
$$P_{\text{trigger}} = \mathbb{1}[\neg s_{\text{storm}}] \times \mathbb{1}[T_{\text{eff}} > 0.85] \times \mathbb{1}[\text{rand}(120) = 0]$$

Probability ≈ $1/120 \approx 0.83\%$ per tick when conditions met.

### 8.2 Storm Hotspot Tracking

**Source**: StormManager.java (lines 43-64)

Peak tension in 3×3 neighborhood around player:
$$T_{\text{regional}} = \max_{(i,j) \in \mathcal{N}_9(\text{player})} L_{i,j}$$

Stored as region coordinates: $(R_x, R_z)$

Updated to "drift toward hotter" every 60 ticks:
$$R^{(n+1)} \leftarrow \arg\max_{R' \in \mathcal{N}_8(R^{(n)})} R'_{\text{avg}}$$

where $\mathcal{N}_8$ is the 8-neighbor (including diagonals) adjacent regions.

### 8.3 Storm Termination

**Source**: StormManager.java (lines 100-108)

Primary condition:
$$\text{end if } T_{\text{eff}} < 0.35$$

Secondary (fractured regions have higher threshold):
$$\text{end if } T_{\text{eff}} < 0.10 \text{ AND } R_{\text{max}} > 1.35 \text{ (fractured)}$$

---

## 9. OPTIMIZATION: SPARSE UPDATES

### 9.1 Sparse Update Condition

**Source**: TensionServerTick.java (lines 334-340)

Chunks with $L_{i,j} < 0.22$ (calm threshold) are **subsampled**:

$$\text{update}_{i,j} = \begin{cases}
\text{true} & \text{if } L_{i,j} \geq 0.22 \text{ (strained/fractured)} \\
\text{true} & \text{if } (i + j + \text{tick}) \bmod 8 = 0 \text{ (1-in-8 calm chunks)} \\
\text{false} & \text{otherwise}
\end{cases}$$

**Effect**: O(N) → O(N/8) computational load in quiescent regions.

**Correctness**: Diffusion timescale (D=0.012) is much longer than local dynamics, so subsampling introduces negligible error.

---

## 10. PERSISTENCE & SERIALIZATION

### 10.1 NBT Serialization

**Source**: ChunkTensionData.java (lines 310-340)

Per chunk, stored:
- $L_{i,j}$ (double)
- $\sigma_{i,j}$ (int, ordinal of enum)
- $m_{i,j}$ (double, mining rate)
- $s_{i,j}$ (boolean, storm flag)
- $\text{contam}_{\text{peak}}$ (double)
- $\text{contam}_{\text{level}}$ (byte)

On world save/load, full state is preserved.

---

## 11. PARAMETER SUMMARY TABLE

| Quantity | Symbol | Value | Type | Equation |
|----------|--------|-------|------|----------|
| Global decay | $\Lambda_g$ | 0.0032 | 1/tick | Eq. 1.1 |
| Global nonlinearity | $\Alpha_g$ | 0.002 | 1/tension | Eq. 1.1 |
| Global storm threshold | $T_g^{\text{on}}$ | 1.0 | tension | Eq. 1.4 |
| Global storm offset | $T_g^{\text{off}}$ | 0.7 | tension | Eq. 1.4 |
| Local diffusion | $D$ | 0.012 | 1/cycle | Eq. 2.2 |
| Local decay | $\Lambda_L$ | 0.0006 | 1/cycle | Eq. 2.6 |
| Local nonlinearity | $\Alpha_L$ | 0.009 | 1/tension | Eq. 2.3 |
| Local storm boost | $\times 1.8$ | 0.0162 | – | Eq. 2.3 |
| Saturation | $\Beta$ | 0.055 | 1/tension² | Eq. 2.4 |
| Global feedback | $\Gamma$ | 0.045 | 1/cycle | Eq. 2.5 |
| Local storm on | $T_{\text{on}}^{L}$ | 0.95 | tension | Eq. 5.1 |
| Local storm off | $T_{\text{off}}^{L}$ | 0.25 | tension | Eq. 5.1 |
| Storm drain | $D_{\text{storm}}$ | 0.002 | tension/tick | Eq. 2.7 |
| State threshold 1 | $T_1$ | 0.95 | tension | Eq. 4.1 |
| State threshold 2 | $T_2$ | 1.28 | tension | Eq. 4.1 |
| State threshold 3 | $T_3$ | 3.0 | tension | Eq. 4.1 |
| Hysteresis width | $H$ | 0.22 | tension | Eq. 4.1 |
| Contamination NONE | $M_{\text{decay}}$ | 1.0 | – | Eq. 3.1 |
| Contamination MEDIUM | $M_{\text{decay}}$ | 0.68 | – | Eq. 3.1 |
| Contamination HEAVY | $M_{\text{decay}}$ | 0.38 | – | Eq. 3.1 |
| Contamination threshold (HEAVY) | – | 1.35 | tension | Eq. 3.1 |
| Contamination threshold (MEDIUM) | – | 1.15 | tension | Eq. 3.1 |
| Ambient inflow coeff | – | 0.0035 | 1/tick | Eq. 1.2 |
| Hotspot stdev coeff | – | 0.052 | 1/tick | Eq. 1.2 |
| Hotspot storm coeff | – | 0.14 | 1/tick | Eq. 1.2 |
| Hotspot fractured coeff | – | 0.26 | 1/tick | Eq. 1.2 |

---

**Extraction Date**: Phase 1 Analysis  
**Status**: ✅ COMPLETE & SOURCE-VALIDATED  
**Next**: Phase 2 (Dynamical Analysis)
