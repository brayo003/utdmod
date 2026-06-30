# Equations

## Chunk field update
Code: [src/main/java/com/utdmod/core/TensionServerTick.java](../../src/main/java/com/utdmod/core/TensionServerTick.java)

Equation:

$$
T_{i,t+1} = T_{i,t} + D \cdot \bar{T}_{\text{nbr}} + \alpha T_{i,t}^2 - \beta T_{i,t}^3 + \gamma G_t - \lambda T_{i,t} \cdot m_c - S_t
$$

Where:
- $D = 0.012$
- $\lambda = 0.0006$
- $\alpha = 0.009$
- $\beta = 0.055$
- $\gamma = 0.045$
- $S_t = 0.002$ when the chunk storm flag is active
- $m_c$ is the contamination decay multiplier from `ChunkTensionData.getContaminationDecayMultiplier(...)`

Interpretation: the chunk field is a nonlinear reaction-diffusion system with decay and global feedback.

Expected behavior: tension spreads to neighboring chunks, grows nonlinearly, decays over time, and is damped or drained when storms are active.

Stability: bounded by clamping to $[0, 5]$.

Failure cases: if the update receives invalid values or NaN, the clamp still protects the range but not the semantics.

## Global tension update
Code: [src/main/java/com/utdmod/core/TensionManager.java](../../src/main/java/com/utdmod/core/TensionManager.java)

Equation:

$$
G_{t+1} = G_t - \lambda G_t + \alpha G_t^2 + I_t
$$

Where:
- $\lambda = 0.0032$
- $\alpha = 0.002$
- $I_t$ is the supplied inflow plus buffered impulse

Interpretation: global tension is a damped nonlinear accumulator.

Expected behavior: the system tends to relax unless coupling inflow persists.

Failure cases: if inflow is large enough, the system can cross thresholds and enable storm activity.

## Region contribution
Code: [src/main/java/com/utdmod/core/Region.java](../../src/main/java/com/utdmod/core/Region.java)

Equation:

$$
C_r = M_r \cdot \left(1 + 0.08 \cdot f_r + 0.12 \cdot s_r \right)
$$

Where:
- $M_r$ is region maturity
- $f_r$ is fractured density
- $s_r$ is storm density

Interpretation: regions with more fractured or storm-active chunks contribute more to the global system.

## Region maturity smoothing
Code: [src/main/java/com/utdmod/core/Region.java](../../src/main/java/com/utdmod/core/Region.java)

Equation:

$$
M_{r,t+1} = M_{r,t} + 0.005 (P_r - M_{r,t})
$$

Where $P_r$ is the computed potential.
