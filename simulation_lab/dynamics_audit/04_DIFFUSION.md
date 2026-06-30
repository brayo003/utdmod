# Diffusion

## Purpose
Determine whether disturbances spread across the lattice under the extracted equation.

## Method
The simulator uses the diffusion term $D \cdot \bar{T}_{\text{nbr}}$ from the production code.

## Evidence
The single-impulse experiment reached a propagation radius of 11.31 and the continuous-injection experiment reached 21.21 in the same grid geometry. This indicates that diffusion visibly propagates a disturbance outward.

## Conclusions
Diffusion is active and produces measurable spatial spread.

## Limitations
The laboratory uses a simple four-neighbor or eight-neighbor style average; the Minecraft implementation uses chunk-neighbor interactions and loaded-chunk constraints.
