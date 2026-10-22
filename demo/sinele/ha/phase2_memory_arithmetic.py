#!/usr/bin/env python3
"""
Phase 2 Memory Arithmetic — Investigation A2
Reproduces every intermediate value for the memory derivative
at the Run 4 mean state, using ONLY the production equations
from FieldDerivative.java (commit 74d1f24).

Source: src/main/java/com/utdmod/tension/FieldDerivative.java
Production ParameterSet defaults (ParameterSet.java lines 127-135):
  memoryFormationGain      = 0.02     (line 127)
  memoryMaximum            = 2.0      (line 128)
  memoryFormationThreshold = 0.05     (line 131)
  memoryDiffusionGain      = 0.002    (line 132)
  memoryDepthRetentionGain = 0.8      (line 133)
  memoryDecayRate          = 0.001    (line 134)
  v2ClampMax               = 4.0      (line 135)
"""

import math

# ── Production parameters (ParameterSet.java constructor, lines 127-135) ──
memoryFormationGain      = 0.02
memoryMaximum            = 2.0
memoryFormationThreshold = 0.05
memoryDiffusionGain      = 0.002
memoryDepthRetentionGain = 0.8
memoryDecayRate          = 0.001
v2ClampMax               = 4.0  # line 135: "Safety valve only; must exceed memoryMaximum (2.0)"

# ── State inputs ──
# Run 4 mean state from run4_stats.json:
#   activation_mean.mean = 0.12951123348191948
#   memory_mean.mean     = 0.4300652691606079
#   field_depth_mean.mean = 0.4900480079807863
# Baseline not directly in stats; inferred from typical telemetry (~0.45)
A = 0.12951123348191948
M = 0.4300652691606079
D = 0.4900480079807863
B = 0.45  # approximate; baseline not in run4_stats directly

# Stencil: ZERO (all Laplacians and gradients = 0)
# This is the explicit test condition for A2.
activationLaplacian = 0.0
memoryLaplacian     = 0.0

print("=" * 65)
print("INVESTIGATION A2: Memory Derivative at Run 4 Mean State")
print("Production equations only — FieldDerivative.java commit 74d1f24")
print("Stencil: ZERO (stated explicitly)")
print("=" * 65)
print(f"\nInput state: A={A}, M={M}, D={D}, B={B}")
print(f"v2ClampMax={v2ClampMax}, memoryMaximum={memoryMaximum}")

print("\n--- computeMemoryFormation (lines 172-180 of FieldDerivative.java) ---")
# line 173: activationDrive = max(0, A - memoryFormationThreshold)
activationDrive = max(0.0, A - memoryFormationThreshold)
print(f"  activationDrive = max(0, {A} - {memoryFormationThreshold}) = {activationDrive:.15f}")

# line 174-176: if activationDrive <= 0 return 0
if activationDrive <= 0.0:
    rawFormation = 0.0
    print(f"  activationDrive <= 0 → rawFormation = 0")
else:
    # line 177: depthModulation = 1 + 0.8 * min(1, D/(1+D))
    depthModulation = 1.0 + memoryDepthRetentionGain * min(1.0, D / (1.0 + D))
    print(f"  D/(1+D)         = {D}/{1+D} = {D/(1+D):.15f}")
    print(f"  min(1, D/(1+D)) = {min(1.0, D/(1+D)):.15f}")
    print(f"  depthModulation = 1 + {memoryDepthRetentionGain} * {min(1.0, D/(1+D)):.15f}")
    print(f"                  = {depthModulation:.15f}")
    # line 178: normalizedDrive = activationDrive / (1 + activationDrive)
    normalizedDrive = activationDrive / (1.0 + activationDrive)
    print(f"  normalizedDrive = {activationDrive:.15f} / (1 + {activationDrive:.15f})")
    print(f"                  = {normalizedDrive:.15f}")
    # line 179: return memoryFormationGain * normalizedDrive * depthModulation
    rawFormation = memoryFormationGain * normalizedDrive * depthModulation
    print(f"  rawFormation    = {memoryFormationGain} * {normalizedDrive:.15f} * {depthModulation:.15f}")
    print(f"                  = {rawFormation:.15f}")

print("\n--- activationPeakStrength (lines 66-67 of FieldDerivative.java) ---")
# line 66-67: max(0, -lap_A) / (0.1 + |lap_A|)
activationPeakStrength = max(0.0, -activationLaplacian) / (0.1 + abs(activationLaplacian))
print(f"  activationLaplacian = {activationLaplacian} (zero stencil)")
print(f"  activationPeakStrength = max(0,-{activationLaplacian})/(0.1+{abs(activationLaplacian)})")
print(f"                        = {activationPeakStrength:.15f}")

print("\n--- formation (line 68-69 of FieldDerivative.java) ---")
# line 68-69: formation = computeMemoryFormation(...) * (0.30 + 0.70 * activationPeakStrength)
peakMultiplier = 0.30 + 0.70 * activationPeakStrength
formation = rawFormation * peakMultiplier
print(f"  peakMultiplier = 0.30 + 0.70 * {activationPeakStrength:.15f}")
print(f"                 = {peakMultiplier:.15f}")
print(f"  formation      = {rawFormation:.15f} * {peakMultiplier:.15f}")
print(f"                 = {formation:.15f}")

print("\n--- saturation (line 70 of FieldDerivative.java) ---")
# line 70: saturation = (1 - M/max(1e-6, memoryMaximum)) * formation
saturation = (1.0 - (M / max(1e-6, memoryMaximum))) * formation
print(f"  M/memoryMaximum = {M:.15f} / {memoryMaximum} = {M/memoryMaximum:.15f}")
print(f"  (1 - M/Mmax)    = {1.0 - M/memoryMaximum:.15f}")
print(f"  saturation      = {1.0 - M/memoryMaximum:.15f} * {formation:.15f}")
print(f"                  = {saturation:.15f}")

print("\n--- activityForgettingFactor (line 71) ---")
# line 71: 2.0 + 3.0*(1 - min(1, max(0, A)))
A_clamped01 = min(1.0, max(0.0, A))
activityForgettingFactor = 2.0 + 3.0 * (1.0 - A_clamped01)
print(f"  clamp(A,0,1)            = {A_clamped01:.15f}")
print(f"  activityForgettingFactor = 2 + 3*(1 - {A_clamped01:.15f})")
print(f"                          = {activityForgettingFactor:.15f}")

print("\n--- computeMemoryDecayRate (lines 182-184) ---")
# line 183: retention = 1/(1 + 0.8 * max(0, D))
retention = 1.0 / (1.0 + memoryDepthRetentionGain * max(0.0, D))
decayRate = memoryDecayRate * retention
print(f"  retention = 1 / (1 + {memoryDepthRetentionGain} * max(0, {D:.15f}))")
print(f"            = 1 / (1 + {memoryDepthRetentionGain * D:.15f})")
print(f"            = {retention:.15f}")
print(f"  decayRate = {memoryDecayRate} * {retention:.15f}")
print(f"            = {decayRate:.15f}")

print("\n--- forgetting (line 72) ---")
# line 72: -0.4 * computeMemoryDecayRate(D) * M * activityForgettingFactor
forgetting = -0.4 * decayRate * M * activityForgettingFactor
print(f"  forgetting = -0.4 * {decayRate:.15f} * {M:.15f} * {activityForgettingFactor:.15f}")
print(f"             = {forgetting:.15f}")

print("\n--- activityGateForSpatialM (line 77) ---")
activityGateForSpatialM = A / (1.0 + A)
print(f"  A/(1+A) = {A:.15f} / {1+A:.15f} = {activityGateForSpatialM:.15f}")

print("\n--- antiDiffusion (lines 79-82) ---")
# -0.15 * memoryFormationGain * activityGateForSpatialM * memoryLaplacian
antiDiffusion = -0.15 * memoryFormationGain * activityGateForSpatialM * memoryLaplacian
print(f"  memoryLaplacian = {memoryLaplacian} (zero stencil)")
print(f"  antiDiffusion   = -0.15 * {memoryFormationGain} * {activityGateForSpatialM:.15f} * {memoryLaplacian}")
print(f"                  = {antiDiffusion:.15f}")

print("\n--- diffusionM (lines 87-89) ---")
# 0.25 * memoryDiffusionGain * memoryLaplacian
diffusionM = 0.25 * memoryDiffusionGain * memoryLaplacian
print(f"  diffusionM = 0.25 * {memoryDiffusionGain} * {memoryLaplacian}")
print(f"             = {diffusionM:.15f}")

print("\n--- dM (line 91) ---")
dM = saturation + forgetting + antiDiffusion + diffusionM
print(f"  dM = saturation + forgetting + antiDiffusion + diffusionM")
print(f"     = {saturation:.15f}")
print(f"     + {forgetting:.15f}")
print(f"     + {antiDiffusion:.15f}")
print(f"     + {diffusionM:.15f}")
print(f"     = {dM:.15f}")

print("\n--- M_next (SubstrateState.applyUnifiedFieldStep, lines 101) ---")
M_next = max(0.0, min(v2ClampMax, M + dM))
print(f"  M_next = clamp({M:.15f} + {dM:.15f}, 0, {v2ClampMax})")
print(f"         = clamp({M + dM:.15f}, 0, {v2ClampMax})")
print(f"         = {M_next:.15f}")

print("\n" + "=" * 65)
print("RECONCILIATION with previous report values:")
print(f"  Previous: formation=0.00186, forgetting=-0.000570, dM=-0.000132")
print()
print(f"  ACTUAL (this script):")
print(f"    rawFormation = {rawFormation:.8f}")
print(f"    formation    = {formation:.8f}  (rawFormation * {peakMultiplier:.4f})")
print(f"    saturation   = {saturation:.8f}")
print(f"    forgetting   = {forgetting:.8f}")
print(f"    dM           = {dM:.8f}")
print()
if abs(rawFormation - 0.00186) < 1e-5:
    print("  rawFormation MATCHES previous 0.00186 ✓")
else:
    print(f"  rawFormation DIFFERS from previous 0.00186")
    print(f"  Difference: {rawFormation - 0.00186:.8f}")
if abs(forgetting - (-0.000570)) < 1e-5:
    print("  forgetting MATCHES previous -0.000570 ✓")
else:
    print(f"  forgetting DIFFERS from previous -0.000570")
    print(f"  Difference: {forgetting - (-0.000570):.8f}")
print()
print(f"  KEY FINDING:")
print(f"  The previous report stated formation=0.00186.")
print(f"  ACTUAL rawFormation = {rawFormation:.8f}")
print(f"  With zero-stencil activationPeakStrength=0:")
print(f"    formation = rawFormation * (0.30 + 0.70*0) = rawFormation * 0.30 = {rawFormation*0.30:.8f}")
print(f"  The previous report appears to have used formation = rawFormation (before the peak multiplier)")
print(f"  The CORRECT value with zero stencil is formation = rawFormation * 0.30 = {rawFormation*0.30:.8f}")
print(f"  This is NOT the same as rawFormation=0.00186")
print()
print(f"  Actual dM with zero stencil = {dM:.8f}")
print(f"  This confirms dM is negative (memory decays) at this mean state with zero stencil.")
