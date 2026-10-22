#!/usr/bin/env python3
"""
Phase 2 Investigation A3: Telemetry Validation
Predicts memory evolution for 5 representative chunk types from Run 4 telemetry,
comparing production-equation predictions against logged post-update snapshots.

Key facts:
  - Integration every 5 ticks (TensionServerTick.java line 107)
  - Snapshots every 20 ticks (SUBSTRATE_SNAPSHOT_PERIOD = 20, ExperimentLogger.java line 44)
  - Snapshots are POST-UPDATE (logged after substrateStates.put, ChunkTensionData.java)
  - Between consecutive snapshots: 4 integration steps (20/5 = 4)
  - v2ClampMax = 4.0 in production runtime (gameplay tuning disabled by default)
  - SpatialStencil: Von Neumann 4-neighbor. Laplacian = avg(neighbors) - center.
  - Missing neighbors are SKIPPED (not zero-filled) — count decreases, average uses available only.

Evidence type: Telemetry reconstruction + Independent numerical reproduction
"""

import json, math
from collections import defaultdict

TELEMETRY_PATH = '/home/lenovo/Git/new_world/run/logs/utd_experiments/2026-07-15T14-26-10/experiment.jsonl'

# Production parameters (ParameterSet.java constructor defaults)
memoryFormationGain      = 0.02
memoryMaximum            = 2.0
memoryFormationThreshold = 0.05
memoryDiffusionGain      = 0.002
memoryDepthRetentionGain = 0.8
memoryDecayRate          = 0.001
v2ClampMax              = 4.0   # default (gameplay tuning disabled)
depthMaximum            = 2.0
depthCapacityFromMemory  = 1.0

def clamp(x, lo, hi):
    return max(lo, min(hi, x))

def clamp01(x):
    return max(0.0, min(1.0, x))

def compute_memory_formation(A, B, D):
    activationDrive = max(0.0, A - memoryFormationThreshold)
    if activationDrive <= 0.0:
        return 0.0
    depthModulation = 1.0 + memoryDepthRetentionGain * min(1.0, D / (1.0 + D))
    normalizedDrive = activationDrive / (1.0 + activationDrive)
    return memoryFormationGain * normalizedDrive * depthModulation

def compute_memory_decay_rate(D):
    retention = 1.0 / (1.0 + memoryDepthRetentionGain * max(0.0, D))
    return memoryDecayRate * retention

def compute_stencil(center_state, neighbor_states):
    """Compute SpatialStencil from available neighbors. Missing neighbors are skipped."""
    A, M, D, B = center_state
    sumA = sumM = sumD = sumB = 0.0
    maxDiffA = maxDiffM = maxDiffD = maxDiffB = 0.0
    count = 0
    for nstate in neighbor_states:
        nA, nM, nD, nB = nstate
        sumA += nA; sumM += nM; sumD += nD; sumB += nB
        maxDiffA = max(maxDiffA, abs(nA - A))
        maxDiffM = max(maxDiffM, abs(nM - M))
        maxDiffD = max(maxDiffD, abs(nD - D))
        maxDiffB = max(maxDiffB, abs(nB - B))
        count += 1
    if count > 0:
        lapA = (sumA / count) - A
        lapM = (sumM / count) - M
        lapD = (sumD / count) - D
        lapB = (sumB / count) - B
    else:
        lapA = lapM = lapD = lapB = 0.0
    return {
        'activationGradient': maxDiffA,
        'memoryGradient': maxDiffM,
        'depthGradient': maxDiffD,
        'baselineGradient': maxDiffB,
        'activationLaplacian': lapA,
        'memoryLaplacian': lapM,
        'depthLaplacian': lapD,
        'baselineLaplacian': lapB,
        'activationCurvature': -lapA,
        'memoryCurvature': -lapM,
        'depthCurvature': -lapD,
        'baselineCurvature': -lapB,
    }

def compute_dM(A, M, D, B, stencil):
    """Compute memory derivative from production equations."""
    activationPeakStrength = max(0.0, -stencil['activationLaplacian']) / (0.1 + abs(stencil['activationLaplacian']))
    rawFormation = compute_memory_formation(A, B, D)
    peakMultiplier = 0.30 + 0.70 * activationPeakStrength
    formation = rawFormation * peakMultiplier
    saturation = (1.0 - M / max(1e-6, memoryMaximum)) * formation
    activityForgettingFactor = 2.0 + 3.0 * (1.0 - clamp01(A))
    decayRate = compute_memory_decay_rate(D)
    forgetting = -0.4 * decayRate * M * activityForgettingFactor
    activityGateForSpatialM = A / (1.0 + A)
    antiDiffusion = -0.15 * memoryFormationGain * activityGateForSpatialM * stencil['memoryLaplacian']
    diffusionM = 0.25 * memoryDiffusionGain * stencil['memoryLaplacian']
    dM = saturation + forgetting + antiDiffusion + diffusionM
    return dM, {
        'rawFormation': rawFormation, 'peakMultiplier': peakMultiplier,
        'formation': formation, 'saturation': saturation,
        'forgetting': forgetting, 'antiDiffusion': antiDiffusion,
        'diffusionM': diffusionM, 'dM': dM,
        'activationPeakStrength': activationPeakStrength
    }

def compute_dA(A, M, D, B, stencil, external, transport):
    """Compute activation derivative."""
    memorySusceptibility = M / (1.0 + M)
    depthSusceptibility = D / (1.0 + D)
    baselineSusceptibility = B / (1.0 + B)
    diffusionA = 0.20 * stencil['activationLaplacian']  # v3DepthTransportGain=0.20
    susceptibility = 0.15 + 0.35 * memorySusceptibility + 0.20 * depthSusceptibility + 0.20 * baselineSusceptibility
    inputDrive = external * susceptibility
    transportDrive = transport + 0.35 * diffusionA
    responseGain = 0.70 + 0.20 * memorySusceptibility + 0.15 * depthSusceptibility
    dissipation = (0.015 + 0.025 * memorySusceptibility + 0.020 * depthSusceptibility) * A + 0.04 * A * A
    dA = responseGain * inputDrive + 0.75 * transportDrive - dissipation
    return dA

def compute_dD(A, M, D, B, stencil, external, transport):
    """Compute depth derivative (simplified - key terms only)."""
    activityGate = A / (1.0 + A)
    memoryGate = M / (1.0 + M)
    dynamicDmax = depthMaximum + depthCapacityFromMemory * memoryGate
    growthFactor = max(0.0, 1.0 - D / max(1e-6, dynamicDmax))
    baselinePersistence = 0.2 * activityGate + 0.8 * memoryGate
    couplingD = (0.04 * (A / (1.0 + A)) * growthFactor
               + 0.02 * (M / (1.0 + M)) * growthFactor
               - 0.008 * D
               + 0.01 * (B / (1.0 + B)) * baselinePersistence * growthFactor)
    # Organization and erosion are simplified approximations
    organization = 0.5  # approximate midpoint
    erosion = 0.0       # approximate for low-gradient chunks
    depthGrowth = organization * activityGate * (0.005 + 0.04) * growthFactor
    depthDecay = 0.0005 * D + 0.03 * erosion
    depthDiffusion = 0.0002 * stencil['depthLaplacian']
    dD = couplingD + depthGrowth - depthDecay + depthDiffusion
    return dD

def compute_dB(A, M, D, B):
    """Compute baseline derivative."""
    baselineCouplingIn = 0.05 * (A / (1.0 + A)) + 0.02 * (M / (1.0 + M)) + 0.01 * (D / (1.0 + D))
    decayB = -0.0005 * B
    restingRecovery = 0.02 * (1.0 - B)
    dB = baselineCouplingIn - 0.03 * B + decayB + restingRecovery
    return dB

def integrate_one_step(A, M, D, B, stencil, external, transport):
    """One Euler integration step (one 5-tick interval)."""
    dA = compute_dA(A, M, D, B, stencil, external, transport)
    dM, mdetails = compute_dM(A, M, D, B, stencil)
    dD = compute_dD(A, M, D, B, stencil, external, transport)
    dB = compute_dB(A, M, D, B)
    A_next = clamp(A + dA, 0.0, v2ClampMax)
    M_next = clamp(M + dM, 0.0, v2ClampMax)
    D_next = clamp(D + dD, 0.0, v2ClampMax)
    B_next = clamp(B + dB, 0.0, v2ClampMax)
    return A_next, M_next, D_next, B_next, {'dA': dA, 'dM': dM, 'dD': dD, 'dB': dB}

def predict_4_steps(A, M, D, B, neighbor_states_func, external_func, transport_func):
    """Predict 4 integration steps (20 ticks) from tick N to tick N+20."""
    for step in range(4):
        neighbors = neighbor_states_func(step)
        stencil = compute_stencil((A, M, D, B), neighbors)
        external = external_func(step)
        transport = transport_func(step)
        A, M, D, B, details = integrate_one_step(A, M, D, B, stencil, external, transport)
    return A, M, D, B

# ── Load telemetry ──
print("=" * 70)
print("PHASE 2 A3: Telemetry Validation")
print("Run 4: 2026-07-15T14-26-10")
print("Integration: every 5 ticks, 4 steps per snapshot interval")
print("v2ClampMax = 4.0 (gameplay tuning disabled)")
print("=" * 70)

chunk_snapshots = defaultdict(list)
chunk_set = set()

with open(TELEMETRY_PATH) as f:
    for line in f:
        d = json.loads(line.strip())
        if d.get('event') == 'substrate_snapshot':
            cx = d['chunk']['x']; cz = d['chunk']['z']
            chunk_set.add((cx, cz))
            chunk_snapshots[(cx, cz)].append({
                'tick': d['tick'],
                'A': d.get('activation', 0), 'M': d.get('memory', 0),
                'D': d.get('field_depth', 0), 'B': d.get('baseline', 0),
                'T': d.get('transport', 0), 'E': d.get('external_input', 0),
                'R': d.get('readiness', 0), 'C': d.get('capacity', 0),
            })

# ── Define 5 representative chunks ──
test_chunks = [
    {
        'name': 'Case 1: Inactive chunk (A < 0.05, below formation threshold)',
        'chunk': (2, 0),
        'note': 'A=0, M=0, D=0 throughout — pure zero state'
    },
    {
        'name': 'Case 2: Sustained activation (A > 0.25)',
        'chunk': (7, -8),
        'note': 'A~0.34, M~1.1, D~0.9 — active mining area'
    },
    {
        'name': 'Case 3: Near activation peak with spatial gradient',
        'chunk': (6, -24),
        'note': 'A~0.35, M~0.9, D~0.8, has 3 neighbors — best stencil case'
    },
    {
        'name': 'Case 4: Substantial neighboring memory gradients',
        'chunk': (7, -8),
        'note': 'M ranges from ~1.07 to ~1.30, neighbors have different M'
    },
    {
        'name': 'Case 5: Relaxation period (late ticks, M slowly decaying)',
        'chunk': (6, 1),
        'note': 'A~0.18, M slowly rising then plateauing, D slowly decreasing'
    },
]

NEIGHBOR_DIRS = [(0, -1), (0, 1), (-1, 0), (1, 0)]  # N, S, W, E

for test in test_chunks:
    cx, cz = test['chunk']
    snaps = chunk_snapshots.get((cx, cz), [])
    if not snaps:
        print(f"\n--- {test['name']} --- NO DATA")
        continue

    print(f"\n{'='*70}")
    print(f"--- {test['name']} ---")
    print(f"    Chunk ({cx},{cz}): {test['note']}")
    print(f"    Snapshots: {len(snaps)}")

    # Available neighbors
    available_neighbors = [(dx, dz) for dx, dz in NEIGHBOR_DIRS if (cx+dx, cz+dz) in chunk_set]
    missing_neighbors = [(dx, dz) for dx, dz in NEIGHBOR_DIRS if (cx+dx, cz+dz) not in chunk_set]
    print(f"    Available neighbors: {[(cx+dx,cz+dz) for dx,dz in available_neighbors]}")
    print(f"    Missing neighbors: {[(cx+dx,cz+dz) for dx,dz in missing_neighbors]}")

    # Test 3 consecutive snapshot intervals (tick N → tick N+20 = 4 integration steps)
    # Pick intervals from early, middle, and late in the run
    interval_indices = [0, len(snaps)//4, len(snaps)//2, 3*len(snaps)//4, len(snaps)-2]
    interval_indices = [i for i in interval_indices if i+1 < len(snaps)]

    print(f"\n    {'Tick':>6s}  {'A_log':>10s} {'M_log':>10s} {'D_log':>10s}  |  "
          f"{'A_pred':>10s} {'M_pred':>10s} {'D_pred':>10s}  |  "
          f"{'ΔA':>10s} {'ΔM':>10s} {'ΔD':>10s}  | {'n_neigh':>7s}")

    for idx in interval_indices[:5]:
        s0 = snaps[idx]
        s1 = snaps[idx + 1]

        # Initial state (post-update at tick N)
        A, M, D, B = s0['A'], s0['M'], s0['D'], s0['B']
        transport = s0['T']
        external = s0['E']

        # Predict 4 steps
        def get_neighbors(step):
            """Get neighbor states at current time (use s0 snapshot — same tick)."""
            neighbors = []
            for dx, dz in available_neighbors:
                nkey = (cx+dx, cz+dz)
                nsnaps = chunk_snapshots.get(nkey, [])
                # Find snapshot at same tick
                for ns in nsnaps:
                    if ns['tick'] == s0['tick']:
                        neighbors.append((ns['A'], ns['M'], ns['D'], ns['B']))
                        break
            return neighbors

        def get_external(step):
            return external  # external_input from telemetry (typically 0)

        def get_transport(step):
            return transport  # transport from telemetry

        A_pred, M_pred, D_pred, B_pred = predict_4_steps(
            A, M, D, B, get_neighbors, get_external, get_transport
        )

        # Compare to logged next snapshot (post-update at tick N+20)
        dA = A_pred - s1['A']
        dM = M_pred - s1['M']
        dD = D_pred - s1['D']

        n_neigh = len(available_neighbors)
        print(f"    {s0['tick']:6d}  {s0['A']:10.6f} {s0['M']:10.6f} {s0['D']:10.6f}  |  "
              f"{A_pred:10.6f} {M_pred:10.6f} {D_pred:10.6f}  |  "
              f"{dA:+10.6f} {dM:+10.6f} {dD:+10.6f}  | {n_neigh:7d}")

    # Detailed breakdown for one interval
    print(f"\n    Detailed breakdown for tick {snaps[0]['tick']} → {snaps[1]['tick']}:")
    s0 = snaps[0]
    s1 = snaps[1]
    A, M, D, B = s0['A'], s0['M'], s0['D'], s0['B']
    print(f"    Initial: A={A:.8f}, M={M:.8f}, D={D:.8f}, B={B:.8f}")
    print(f"    External={s0['E']}, Transport={s0['T']}")

    neighbors = get_neighbors(0)
    stencil = compute_stencil((A, M, D, B), neighbors)
    print(f"    Stencil (from {len(neighbors)} neighbors):")
    print(f"      activationLaplacian={stencil['activationLaplacian']:.8f}")
    print(f"      memoryLaplacian={stencil['memoryLaplacian']:.8f}")
    print(f"      depthLaplacian={stencil['depthLaplacian']:.8f}")

    dM_val, mdetails = compute_dM(A, M, D, B, stencil)
    print(f"    dM components (step 1):")
    print(f"      rawFormation={mdetails['rawFormation']:.10f}")
    print(f"      peakMultiplier={mdetails['peakMultiplier']:.6f}")
    print(f"      formation={mdetails['formation']:.10f}")
    print(f"      saturation={mdetails['saturation']:.10f}")
    print(f"      forgetting={mdetails['forgetting']:.10f}")
    print(f"      antiDiffusion={mdetails['antiDiffusion']:.10f}")
    print(f"      diffusionM={mdetails['diffusionM']:.10f}")
    print(f"      dM={mdetails['dM']:.10f}")

    print(f"    Logged next: A={s1['A']:.8f}, M={s1['M']:.8f}, D={s1['D']:.8f}")

# ── Summary ──
print(f"\n{'='*70}")
print("SUMMARY OF STENCIL RECONSTRUCTION LIMITATIONS")
print(f"{'='*70}")
print(f"Total chunks in telemetry: {len(chunk_set)}")
has_all_4 = 0
has_3 = 0
has_2 = 0
has_1 = 0
has_0 = 0
for (cx, cz) in chunk_set:
    n = sum(1 for dx, dz in NEIGHBOR_DIRS if (cx+dx, cz+dz) in chunk_set)
    if n == 4: has_all_4 += 1
    elif n == 3: has_3 += 1
    elif n == 2: has_2 += 1
    elif n == 1: has_1 += 1
    else: has_0 += 1
print(f"Chunks with 4 neighbors: {has_all_4}")
print(f"Chunks with 3 neighbors: {has_3}")
print(f"Chunks with 2 neighbors: {has_2}")
print(f"Chunks with 1 neighbor:  {has_1}")
print(f"Chunks with 0 neighbors: {has_0}")
print(f"\nCONCLUSION: No chunk has all 4 neighbors → spatial stencil is always partial.")
print(f"Missing neighbors are SKIPPED (not zero-filled) in SpatialStencil.fromChunkNeighbors.")
print(f"The Laplacian is computed from available neighbors only (average/count).")
print(f"This means predictions with partial stencils will differ from the runtime")
print(f"which had access to ALL loaded chunk neighbors, not just those in telemetry.")
print(f"\nAdditionally, the runtime may have had neighbors not logged in telemetry")
print(f"(telemetry only logs chunks with existing substrate state at snapshot period).")
print(f"Transport values also change between steps but we use the logged value.")
