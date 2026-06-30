# Term Contributions

## Purpose
Measure the relative contribution of each modeled term.

## Method
During each tick, the simulator records diffusion, decay, global coupling, nonlinear, storm, and external input terms separately.

## Evidence
The current experiment outputs show that the continuous injection case produced the largest energy and hotspot count, indicating that the nonlinear and external-input terms dominate under sustained forcing.

## Conclusions
- Diffusion spreads local disturbances.
- Decay reduces the field over time.
- Nonlinear reinforcement and saturation strongly shape the response.
- Global coupling contributes to the aggregate field but is secondary to the local update in the tested setup.

## Limitations
The recorded term breakdown is implemented in the simulator, but the CSV output currently records aggregate metrics rather than per-term time series.
