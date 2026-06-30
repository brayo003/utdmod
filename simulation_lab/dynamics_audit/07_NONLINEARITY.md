# Nonlinearity

## Purpose
Evaluate whether the nonlinear terms create behavior beyond simple linear diffusion and decay.

## Method
The simulator includes the nonlinear reinforcement term $\alpha T^2$ and saturation term $\beta T^3$ directly in the update rule.

## Evidence
The continuous injection case produced high energy and hotspot count in the output, which is consistent with nonlinear growth before eventual saturation. The single-impulse case remained limited in peak tension and energy, consistent with the bounded nonlinear response.

## Conclusions
The nonlinear terms are active and materially change the dynamics.

## Limitations
A dedicated parameter sweep is needed to isolate the exact sensitivity of the nonlinear coefficients.
