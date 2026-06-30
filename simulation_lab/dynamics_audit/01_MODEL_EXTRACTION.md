# Model Extraction

## Purpose
Extract the mathematical system implemented by the production code.

## Method
The simulator uses the update rule from [src/main/java/com/utdmod/core/TensionServerTick.java](../../src/main/java/com/utdmod/core/TensionServerTick.java):

$$
T_{i,t+1} = T_{i,t} + D \cdot \bar{T}_{\text{nbr}} + \alpha T_{i,t}^2 - \beta T_{i,t}^3 + \gamma G_t - \lambda T_{i,t} - S_t
$$

The implementation also clamps to $[0, 5]$ and uses a separate global update from [src/main/java/com/utdmod/core/TensionManager.java](../../src/main/java/com/utdmod/core/TensionManager.java):

$$
G_{t+1} = G_t - \lambda G_t + \alpha G_t^2 + I_t
$$

## Evidence
- $D = 0.012$
- $\lambda = 0.0006$ for the chunk field
- $\alpha = 0.009$
- $\beta = 0.055$
- $\gamma = 0.045$
- storm drain $= 0.002$

## Conclusions
The extracted model is a nonlinear diffusion-decay-coupling system with bounded saturation.

## Limitations
The production code contains additional stateful features such as contamination memory and storm activation thresholds, which are simplified in the laboratory.
