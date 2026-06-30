# Stability

## Purpose
Estimate the operating regime of the extracted model.

## Method
The simulator applies the clamp $[0, 5]$ used in the production implementation and evaluates the resulting field metrics.

## Evidence
All experimental runs remained bounded within the implemented clamp range, and the system did not produce NaN or divergence in the tested setup. The highest measured peak tension was about 1.36 in the continuous injection run.

## Conclusions
The implemented dynamics are bounded and numerically stable for the tested parameter values and grid sizes.

## Limitations
The laboratory does not yet sweep the coefficients across a broad range to determine the full stable region.
