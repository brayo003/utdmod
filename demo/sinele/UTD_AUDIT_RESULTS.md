# UTD Audit Results
**Date:** 2026-10-21  
**Project:** UTD Mod — Unified Tension Dynamics (Minecraft 1.20.4, Fabric, Java 17)  
**Baseline:** `/home/lenovo/Git/new_world`, branch `main`, source files unmodified  
**Work directory:** `/home/lenovo/Git/new_world/demo/sinele`  
**Tests executed:** `utd_audit_tests.py` (16/16 pass), `utd_extension_tests.py` (5/5 pass), `./gradlew test` (25/26 pass, 1 failure documented below)

---

## 1. Baseline and Repository State

Git branch: `main`, up to date with `origin/main`.  
Modified files: only compiled `.class` artifacts in `bin/`. All `.java` source files are clean.  
No source file was modified during this audit.

**Demo variant access:** The previous report (`FORENSIC_VERIFICATION_AUDIT.md`) stated demo variant directories were "inaccessible due to filesystem issues." This was wrong. All variants are regular directories with normal permissions and are fully accessible:

- `utdmod-corrected-dynamics/` — accessible, read and diffed
- `utdmod-corrected-dynamics (1)/` — identical to above (verified with `diff -rq`)
- `utdmod-corrected-dynamics-1/` — identical to above
- `utdmod-tension-threshold-fix/` — accessible, differs from corrected-dynamics in 13 files + 2 new files
- `utdmod-structured-docs/` — accessible
- `utdmmod-main/` — accessible

---

## 2. Runtime Configuration for Telemetry Runs 3 and 4

**Key discovery: v2ClampMax was 4.0, not 1.5.**

The `run_start` event in both JSONL logs shows `"substrate_clamp_max": 1.5` in the `configuration` object. This is the `UTDConfig.substrateClampMax` override value. However, `UTDConfig.substrateGameplayTuningEnabled = false` in both runs, which means `ParameterSet.applyConfigOverrides()` was never called. The actual runtime `v2ClampMax` was the hardcoded constructor default: **4.0**.

**Proof from telemetry:** Run 3, chunk (5, -23), tick 6874 — `activation = 3.2404`. This exceeds 1.5 but is ≤ 4.0. A true clamp of 1.5 makes this value impossible.

**Confirmed runtime parameters for Runs 3 and 4** (all ParameterSet constructor defaults, tuning disabled):

| Parameter | Value |
|-----------|-------|
| `memoryFormationGain` | 0.02 |
| `memoryMaximum` | 2.0 |
| `depthMaximum` | 2.0 |
| `memoryFormationThreshold` | 0.05 |
| `memoryDecayRate` | 0.001 |
| `memoryDepthRetentionGain` | 0.8 |
| `memoryDiffusionGain` | 0.002 |
| `v2ClampMax` | **4.0** (not 1.5 as previously reported) |
| `v3DepthTransportGain` | 0.20 |
| `tensionActivationWeight` | 0.40 |
| `tensionMemoryWeight` | 0.25 |
| `tensionDepthWeight` | 0.20 |

---

## 3. Telemetry Sampling Semantics

Substrate snapshots are logged **after** the update. In `ChunkTensionData.integrateAllSubstrateStates()`, `ExperimentLogger.logSubstrateSnapshot()` is called inside the loop that writes `nextStates` back to `substrateStates` — i.e., after `integrateCopy()` and after all state variables have been updated and clamped. There are no pre-update snapshots in the telemetry.

---

## 4. Memory Equation Investigation

### 4.1 Exact implementation confirmed

`FieldDerivative.computeMemoryFormation(A, B, D)`:
```
drive = max(0, A - 0.05)          // threshold = 0.05
if drive == 0: return 0
depth_mod = 1 + 0.8 * min(1, D/(1+D))
return 0.02 * (drive/(1+drive)) * depth_mod
```

`FieldDerivative.computeMemoryDecayRate(D)`:
```
retention = 1 / (1 + 0.8 * max(0, D))
return 0.001 * retention
```

Memory derivative in `computeUnified()`:
```
activationPeakStrength = max(0, -lap_A) / (0.1 + |lap_A|)
formation = computeMemoryFormation(A,B,D) * (0.30 + 0.70 * activationPeakStrength)
saturation = (1 - M/memoryMaximum) * formation
activityForgettingFactor = 2 + 3 * (1 - clamp(A, 0, 1))
forgetting = -0.4 * computeMemoryDecayRate(D) * M * activityForgettingFactor
activityGate = A/(1+A)
antiDiffusion = -0.15 * 0.02 * activityGate * memoryLaplacian
diffusion = 0.25 * 0.002 * memoryLaplacian
dM = saturation + forgetting + antiDiffusion + diffusion
```

Integration: single Euler step, `M_new = clamp(M + dM, 0, v2ClampMax)`.

### 4.2 Unit test results (all pass)

| Test | Result |
|------|--------|
| formation = 0 when A=0.04 < threshold=0.05 | 0.000000 — PASS |
| formation = 0 at A=0.05 exactly | 0.000000 — PASS |
| formation at A=0.3, D=0: expected 0.02×(0.25/1.25)×1.0 = 0.004000 | 0.004000 — PASS |
| depth modulation ratio at D=1.0: expected 1.40 | 1.400000 — PASS |
| decay rate at D=0: expected 0.001000 | 0.001000 — PASS |
| decay rate at D=1.0: expected 0.001/1.8 = 0.000556 | 0.000556 — PASS |
| decay rate at D=10: expected 0.001/9 = 0.000111 | 0.000111 — PASS |
| Euler clamp: large external input clamped to ≤ v2ClampMax=4.0 | PASS |
| dM=0 when A=0, M=0 | 0.000000 — PASS |

### 4.3 Ordinary state test (Run 4 telemetry, chunk (-1,0), tick 20)

Input state: A=0.000583, M=0.0, D=0.004288, B=0.397426, external=0, transport=0

```
A < threshold (0.000583 < 0.05) → formation = 0
M = 0 → forgetting term = 0
dM = 0 exactly
```

Post-step: A=0.000574, M=0.000000, D=0.004295, B=0.397428

### 4.4 20-step forward prediction test (Run 4 telemetry, chunk (-1,0))

Starting from each logged post-update state, run 20 Euler steps with zero stencil and log transport, compare to the next logged state (20 ticks later). Results:

| Start tick | A observed | A predicted | M observed | M predicted | dM (step 1) |
|-----------|-----------|------------|-----------|------------|-------------|
| 20 | 0.25151 | 0.25141 | 0.80767 | 0.81102 | +0.000213 |
| 40 | 0.25150 | 0.25140 | 0.80851 | 0.81185 | +0.000212 |
| 60 | 0.25149 | 0.25139 | 0.80935 | 0.81267 | +0.000210 |
| 80 | 0.25147 | 0.25138 | 0.81019 | 0.81349 | +0.000209 |
| 100 | 0.25146 | 0.25136 | 0.81102 | 0.81430 | +0.000208 |
| 120 | 0.25145 | 0.25135 | 0.81185 | 0.81511 | +0.000207 |

Mean relative error: A = **0.04%**, M = **0.41%**.

Residual error is attributable to the spatial stencil contribution (neighbors excluded in this test). The implementation is correct.

### 4.5 Memory derivative at Run 4 mean state

Mean state from run4_stats.json: A=0.1295, M=0.430, D=0.490

Step-0 derivatives (zero stencil, zero external):
```
dA = +0.001524
dM = -0.000132   ← memory is DECREASING at mean state with zero stencil
dD = +0.007868
dB = +0.012310
formation = 0.00186,  forgetting = -0.000570
effective dM = 0.00186*(0.30 + 0.70*0) - 0.000570 ≈ -0.000132
```

Telemetry shows mean memory slope = +3.25×10⁻⁵ per tick (slowly increasing). This is consistent: chunks at spatial activation peaks have negative Laplacian, boosting `activationPeakStrength` to ~1.0, which increases formation 3.3× and turns dM positive. The spatial mean is positive because more chunks are at peaks than in valleys.

**Conclusion on the previously reported "5.9× suppression" defect: not confirmed.** The implementation is self-consistent. No defect in the memory equation.

---

## 5. Confirmed Implementation Defects

### Defect 1: v2ClampMax default mismatch (also causes test failure)

**Location:** `ParameterSet.java` constructor, `RuntimeFidelityTest.java`

The `ParameterSet` constructor sets `v2ClampMax = 4.0`. The Java test `RuntimeFidelityTest.runtimeUsesLaboratoryDefaultsWhenGameplayTuningDisabled` asserts it should be `1.5` and **fails** (`expected: 1.5 but was: 4.0`). One of these is wrong. Given that the telemetry data contains activation=3.24 (impossible if clamp were 1.5), the constructor value of 4.0 is what was actually used. The test expectation is incorrect relative to the actual constructor code.

**Build result:** `./gradlew test` → 26 tests, **1 FAILED**, 25 passed.

### Defect 2: All three ChunkState thresholds are unreachable

**Location:** `ChunkTensionData.java` (T1=0.95, T2=1.28, T3=3.0), `SubstrateObservationModel.java`

The tension formula:
```
T(x) = (0.40·A/(1+A) + 0.25·M/(1+M) + 0.20·D/(1+D)) / (0.40+0.25+0.20)
     = (0.40·A/(1+A) + 0.25·M/(1+M) + 0.20·D/(1+D)) / 0.85
```

As A, M, D → ∞, each term approaches 1.0, so **T_max = 0.85/0.85 = 1.0** (proven analytically).

Therefore:
- T3 = 3.0 > 1.0: **permanently impossible by formula**
- T2 = 1.28 > 1.0: **permanently impossible by formula**
- T1 = 0.95 < 1.0: theoretically possible, but requires each `x/(1+x) ≈ 0.95`, meaning x ≈ 19 for all three variables. At `v2ClampMax=4.0`, `T(4.0, 4.0, 4.0) = 0.80` — T1 still unreachable.

**Verified against all telemetry snapshots:**

| Run | Snapshots | T_max | T_mean | Above T1=0.95 | Above T2=1.28 | Above T3=3.0 |
|-----|-----------|-------|--------|--------------|--------------|-------------|
| Run 3 | 9,039 | 0.6618 | 0.1689 | 0 | 0 | 0 |
| Run 4 | 57,466 | 0.4135 | 0.1698 | 0 | 0 | 0 |

**Consequence:** No `ChunkState` can ever transition from `STABLE` to `STRAINED`, `FRACTURED`, or `DECOUPLED`. All downstream effects gated on these states are permanently inactive:
- Contamination levels (thresholds 1.15, 1.35 — both > T_max=1.0)
- Regional FRACTURED/STRAINED classification
- Ecology strain gate
- Sleep blocking (`> 1.0`)
- Wraith aggression (`> 1.0`)
- ChunkState-based particle/sound effects
- `STATE_HYSTERESIS = 0.22` (irrelevant; no transitions occur)

### Defect 3: ResourceRegenerationActuator returns SUCCESS without world change

**Location:** `ResourceRegenerationActuator.java`

For a pristine chunk (A=0, M=0, D=0):
```
capacity = 1 / (1 + 0.5×0) = 1.0
recoveryState = exp(-0 / 1.0) = 1.0
prosperityPotential = 0 (no history)
successionPotential = 1.0×0.45 + 1.0×0.35 + 0×0.20 = 0.80
threshold = 0.42
0.80 ≥ 0.42 → crop succession branch TRIGGERS
```

`regenerateCrops()` scans for WHEAT, CARROTS, or POTATOES blocks in a 4×4 surface area. A pristine chunk has none. The scan completes, finds nothing, makes zero world changes. `actuate()` then returns `ActuatorOutcome.SUCCESS`.

**Two defects:**
1. Crop succession triggers on any chunk with no substrate history whatsoever — it does not guard against virgin chunks.
2. The `SUCCESS` return value does not mean world modification occurred. It means the potential threshold was met. Callers cannot use the return value to confirm actual block changes.

Ore regeneration is correctly guarded: `miningPotential = 0 < 0.45` for pristine chunks. Only the crop path misfires.

### Defect 4: TensionManager has empty non-deprecated method bodies with no callers

**Location:** `TensionManager.java`

Full method audit (from source inspection + `grep -rn` across all source files):

| Method | Status | Body | Callers |
|--------|--------|------|---------|
| `setTension(double)` | `@Deprecated` | empty `{}` | none |
| `addEvent(double)` | `@Deprecated` | empty `{}` | none |
| `reduceTension(double)` | `@Deprecated` | empty `{}` | none |
| `tickGlobalDynamics(...)` | `@Deprecated` | empty `{}` | none |
| `addMiningTension(String)` | `@Deprecated` | empty `{}` | none |
| `addMiningTension(String, Player)` | functional | delegates to `ChunkTensionManager` | has callers |
| `triggerWraithEvent(World)` | **not deprecated** | **empty `{}`** | **none** |
| `triggerSerpentEvent(World)` | **not deprecated** | **empty `{}`** | **none** |
| `calculateBaseTension(Player)` | not deprecated | returns `0.005f` always | none |
| `recompute(MinecraftServer)` | functional | computes global metrics | called each tick |
| getters (`getTension` etc.) | functional | read snapshot from recompute | used |

`triggerWraithEvent` and `triggerSerpentEvent` are the concerning entries: active method signatures (not deprecated, not marked as stubs), empty implementations, and no callers anywhere in the codebase. Any code path that would call them produces a silent no-op. Since the thresholds that would trigger wraith/serpent events are themselves unreachable (Defect 2), these empty bodies have had no observable effect in any recorded run.

---

## 6. Build Health

### Java tests: 25 pass, 1 fail

```
./gradlew test
26 tests completed, 1 failed

FAILED: RuntimeFidelityTest > runtimeUsesLaboratoryDefaultsWhenGameplayTuningDisabled
  AssertionFailedError: expected: <1.5> but was: <4.0>
  at RuntimeFidelityTest.java:27
```

The test asserts `assertEquals(1.5, runtime.v2ClampMax)`. The actual constructor value is 4.0. The test expectation is wrong (see Defect 1).

All other 25 tests pass, including:
- `FieldDerivativeParityTest` (5 tests) — coupling matrix, unified derivative, memory threshold, transport bounds, stencil formula
- `SubstrateStateTest` — state accessors and integration
- `SubstrateFieldPortTest` — field porting
- `RuntimeFidelityTest::deprecatedIntegrationPathsAreBlocked` — passes
- `RegionEcologyManagerTest`, `InterpretationEngineTest`, `SpatialBehaviorHelperTest`, `ResearchPlayerDeterminismTest`

Compile warnings: 4 Mixin `@Inject` descriptor warnings and 4 `@Shadow` field warnings. These are annotation-processor warnings about mixin targets in external Minecraft classes, not build errors.

### Python analysis scripts: all syntax-clean

```
python3 -m py_compile analysis/stage1_extract.py    → OK
python3 -m py_compile analysis/stage2_timeseries.py → OK
python3 -m py_compile analysis/stage3_regional.py   → OK
python3 -m py_compile analysis/stage4_stats.py      → OK
python3 -m py_compile analysis/stage5a_plots_timeseries.py → OK
python3 -m py_compile analysis/stage5b_plots_crossrun.py   → OK
python3 -m py_compile analysis/stage5c_plots_regional.py   → OK
```

All 10 Parquet/JSON data files in `analysis/data/` loaded without errors.

---

## 7. Extension Evaluation

### 7.1 Multi-aspect substrate

**Document:** `demo/sinele/UTD_Multi_Aspect_Substrate_Extension.md` (1082 lines).  
**Status:** Design proposal only. Not implemented in production.

**Test: forced-same-state proof**  
Two causal histories run for 200 steps: "thermal" (sustained external=0.3) and "arcane" (burst external=1.5). Natural states after 200 steps:

```
Thermal:  A=1.44897  M=0.69489  D=1.70662  B=1.25492  T=0.547
Arcane:   A=1.64587  M=1.34649  D=1.93873  B=1.46043  T=0.617
```

Both histories forced to the same initial state, then 20 steps of external=0.5 applied:

```
From thermal origin: A=1.99621639  M=0.76377059
From arcane origin:  A=1.99621639  M=0.76377059
Bit-identical: True
```

**Finding:** The current model's trajectory is fully determined by `(A, M, D, B)`. Any two histories that converge to the same scalar state are permanently indistinguishable.

**Test: minimum viable extension**  
Decaying provenance counters (2 floats per chunk, decay constant = 1/50 per step, no ODE):

```
Thermal history: thermal_tag=14.736  arcane_tag=0.000
Arcane history:  thermal_tag=0.000   arcane_tag=35.597
```

Discriminable: **True** (difference > 14). This is the minimum viable form — no new ODE, no dynamics change, no serialization impact beyond two extra floats. A full modal ODE layer (new evolving state variables with coupled dynamics) is optional and justified only if causal history is intended to produce distinct ongoing dynamics.

### 7.2 Stability variable S

**Document:** `demo/sinele/UTD_stability_mechanism_evidence/UTD_STABILITY_MECHANISM_EXPERIMENT.md`  
**CSV data:** `UTD_experimental_stability_results.csv` — read and verified.  
**Status in production:** Not implemented. No state variable, no update rule, no serialization, no callers.

**What S does:** `effectiveInput = externalInput × (1 - rho × S)` where S ∈ [0,1] is structural resistance and rho is a coefficient.

**Test results (production equations, rho=0.60, external=0.05 for 20 steps):**

| Case | S | rho | Input-attributable ΔA (20 steps) |
|------|---|-----|----------------------------------|
| Virgin | 0.00 | 0.60 | 0.25607 |
| Organized | 0.90 | 0.60 | 0.12118 |
| Ratio | — | — | **0.47× (53% less)** |

The simulation-lab experiment (different equations, 5-tick pulses) reported a ratio of 0.94× at rho=0.60. The production equations give 0.47× because the production susceptibility gain is larger, making the resistance effect proportionally stronger.

**S vs D comparison (same test, no S mechanism):**

```
High-D chunk (D=0.80): input-attributable ΔA = 0.25607
Low-D chunk  (D=0.05): input-attributable ΔA = 0.21967
Ratio (highD/lowD): 1.17×  ← high D INCREASES response
```

D amplifies susceptibility (through the `depthSusceptibility` term in `computeUnified`). S would resist it. They have opposite signs. **S is not redundant with D.**

**Decision:** Feasibility confirmed. Production integration requires: new evolving state variable for S, accumulation/erosion rules, serialization, calibration. None exist yet. No production change warranted before design.

Note: "stability" in this context means structural resistance to new disturbance. It is unrelated to the bistability/hysteresis terminology in the telemetry report.

### 7.3 Threshold-fix variant

**Document:** `demo/sinele/TENSION_THRESHOLD_SCALE_FIX.md`  
**New file:** `utdmod-tension-threshold-fix/src/main/java/com/utdmod/tension/TensionThresholds.java`

Proposed new thresholds:
```
CHUNK_STRAINED  = 0.35
CHUNK_FRACTURED = 0.60
CHUNK_DECOUPLED = 0.72
CHUNK_HYSTERESIS = 0.08
CONTAMINATION_MEDIUM = 0.55
CONTAMINATION_HEAVY  = 0.68
SLEEP_BLOCK          = 0.60
WRAITH_AGGRESSION    = 0.60
```

**Verified against telemetry:**

| Threshold | Run 3 (9039 snapshots) | Run 4 (57466 snapshots) |
|-----------|----------------------|------------------------|
| Old STRAINED 0.95 | 0 chunks (0.0%) | 0 chunks (0.0%) |
| Old FRACTURED 1.28 | 0 chunks (0.0%) | 0 chunks (0.0%) |
| Old DECOUPLED 3.0 | 0 chunks (0.0%) | 0 chunks (0.0%) |
| **New STRAINED 0.35** | **925 chunks (10.2%)** | **18360 chunks (31.9%)** |
| **New FRACTURED 0.60** | **123 chunks (1.4%)** | **0 chunks (0.0%)** |
| New DECOUPLED 0.72 | 0 chunks (0.0%) | 0 chunks (0.0%) |

The fix is mathematically correct. STRAINED and FRACTURED become reachable. DECOUPLED (0.72) is not reached in either run — it may need to be lower, or may be appropriate as a high-intensity-only threshold.

Limitations (acknowledged in fix document): values are not gameplay-validated. No data migration for persisted state. Does not fix the resource-regeneration defect or the empty wraith/serpent bodies.

---

## 8. What Previous Reports Got Wrong

| Previous claim | Actual finding |
|----------------|----------------|
| Demo variants "inaccessible due to filesystem issues" | All variants are accessible, normal permissions |
| `v2ClampMax = 1.5` during telemetry runs | **4.0** — proven by activation=3.24 in JSONL |
| "Memory equation suppressed 5.9×" — implementation defect | Not confirmed. 20-step prediction error is 0.04%–0.41% |
| T1=0.95 unreachable only due to v2ClampMax=1.5 | T_max formula = 1.0 regardless of clamp; T1 requires state≈19 |

---

## 9. Summary Table

| # | Item | Status | Action |
|---|------|--------|--------|
| 1 | v2ClampMax default: 4.0 vs test expectation 1.5 | **Defect** | Fix test OR fix constructor to match intent |
| 2 | T1/T2/T3 thresholds unreachable (T_max formula = 1.0) | **Defect** | Apply threshold-fix variant |
| 3 | Resource regen: false SUCCESS on pristine chunks | **Defect** | Guard crop path; return SUCCESS only on actual block change |
| 4 | triggerWraithEvent / triggerSerpentEvent: empty, no callers | **Gap** | Implement or remove |
| 5 | Threshold-fix variant (TensionThresholds.java) | **Ready** | Merge; DECOUPLED=0.72 may need gameplay validation |
| 6 | Multi-aspect substrate (modal ODE) | **Defer** | Start with provenance tags (2 floats, no ODE) |
| 7 | Stability variable S | **Defer** | Design accumulation/erosion rules before integration |
| 8 | Memory equation implementation | **Correct** | No action needed |
| 9 | Java test suite (25/26 pass) | **1 failure** | Fix RuntimeFidelityTest expectation (see item 1) |
| 10 | Python analysis scripts | **Clean** | No action needed |

---

## 10. Test Files

Both scripts are in `demo/sinele/` and can be run without Minecraft:

```bash
python3 /home/lenovo/Git/new_world/demo/sinele/utd_audit_tests.py
# 16/16 pass

python3 /home/lenovo/Git/new_world/demo/sinele/utd_extension_tests.py
# 5/5 pass
```

Neither script modifies any production file.
