# PHASE 2: Findings and Decisions

**Synthesis of Investigations A, B, C, and D**

**Date:** 2026-10-21  
**Baseline commit:** 74d1f24  
**Investigators:** Independent forensic audit  
**No production source code was modified.**

---

## Findings Ranked by Severity and Confidence

### Finding 1: RuntimeFidelityTest encodes the wrong clamp contract

**Severity:** Medium (test correctness; no production impact)  
**Confidence:** HIGH — confirmed by source inspection, documentation, and telemetry

**Evidence:**
- `ParameterSet.java` line 135: `v2ClampMax = 4.0` (constructor default, comment: "Safety valve only")
- `RuntimeFidelityTest.java` line 37: `assertEquals(1.5, runtime.v2ClampMax, 1e-9)` — asserts 1.5
- `UTDConfig.substrateGameplayTuningEnabled = false` → `applyConfigOverrides()` never called
- Run 3 telemetry: maxA=3.2404 (exceeds 1.5, proves runtime used 4.0)
- Run 4 telemetry: maxM=1.8978 (exceeds 1.5, proves runtime used 4.0)
- All documentation (PARAMETER_CATALOG.md, EQUATIONS.md, SUBSTRATE_AUDIT.md) say 4.0
- `FieldDerivativeParityTest.java` line 79 explicitly overrides to 1.5, confirming the author knew the default was not 1.5

**Status:** CONFIRMED defect in test code.

**Proposed change:** Fix `RuntimeFidelityTest.java` line 37: assert `4.0` instead of `1.5`.

**Regression test:** The corrected test itself is the regression test. After the fix, `./gradlew test --tests RuntimeFidelityTest` should pass.

**Compatibility risk:** None. Test-only change. No production code modified. No migration needed.

---

### Finding 2: Telemetry records the wrong clamp value

**Severity:** Low (telemetry accuracy; misleading configuration metadata)  
**Confidence:** HIGH — confirmed by source inspection

**Evidence:**
- `ExperimentLogger.buildConfigurationMap()` line 928: `config.put("substrate_clamp_max", UTDConfig.substrateClampMax)` — records 1.5
- `UTDConfig.substrateClampMax = 1.5` but effective runtime `v2ClampMax = 4.0`
- `substrateGameplayTuningEnabled` is NOT recorded in telemetry
- All 4 experiment runs show `substrate_clamp_max: 1.5` in configuration.json, but all used 4.0 at runtime

**Status:** CONFIRMED defect in telemetry logging.

**Proposed change:** In `ExperimentLogger.buildConfigurationMap()`:
- Replace `config.put("substrate_clamp_max", UTDConfig.substrateClampMax)` with `config.put("effective_v2_clamp_max", ParameterSet.runtime().v2ClampMax)`
- Add `config.put("substrate_gameplay_tuning_enabled", UTDConfig.substrateGameplayTuningEnabled)`

**Regression test:** Start a server, run one experiment, verify `configuration.json` contains `"effective_v2_clamp_max": 4.0` and `"substrate_gameplay_tuning_enabled": false`.

**Compatibility risk:** Low. Telemetry format change only. Historical telemetry retains the old field. No gameplay impact.

---

### Finding 3: ResourceRegenerationActuator returns SUCCESS without changing world state

**Severity:** Low (misleading telemetry; no gameplay impact)  
**Confidence:** HIGH — confirmed by source inspection and telemetry reconstruction

**Evidence:**
- `ResourceRegenerationActuator.java` line 55: `acted = true` set when branch entered, not when block changed
- `ActuatorOutcome.java`: SUCCESS = "Actuator executed and made changes"
- `FieldActuator.java` contract: SUCCESS = "actuator executed and changed world state"
- Telemetry: 229,888 invocations, ALL return SUCCESS (100%)
- 116,870 invocations with A=M=D=0 (pristine) return SUCCESS — no crops or coarse dirt to modify
- `regenerateCrops()` scans for WHEAT/CARROTS/POTATOES → none found in pristine → no block changes
- `regenerateSurfaceRecovery()` scans for COARSE_DIRT → none found in pristine → no block changes
- `FieldActuatorRegistry.actuateAll()` returns void — outcome only used for logging
- No downstream code consumes ActuatorOutcome for gameplay decisions
- Same pattern in `SettlementStructureActuator` (acted=true on branch entry)

**Root cause:** At pristine state (A=M=D=0):
- `capacity = 1.0`, `convergence = 1.0`
- `recoveryState = clamp01(1.0 × 0.40 + 1.0 × 0.35 + 1.0 × 0.25) = 1.0`
- `prosperityPotential = clamp01(0 + 1.0 × 0.30 + 0 + 0) = 0.30`
- `successionPotential = 1.0 × 0.45 + 1.0 × 0.35 + 0.30 × 0.20 = 0.86`
- `0.86 > 0.42` → crop succession branch fires → `acted = true` → returns SUCCESS

**Status:** CONFIRMED defect (misleading outcome reporting, not gameplay bug).

**Proposed change:** Track whether any `setBlockState` was actually called. Return `NO_TARGET` when the branch fires but no blocks are modified. Return `SUCCESS` only when at least one block changes.

Specifically in `ResourceRegenerationActuator.actuate()`:
- Have `regenerateCrops()` return a boolean (whether any block was changed)
- Have `regenerateSurfaceRecovery()` return a boolean
- Have `regenerateOres()` return a boolean
- Set `acted = true` only when at least one method returns true

Apply the same pattern to `SettlementStructureActuator`.

**Regression test:** Create a unit test that calls `actuate()` with a pristine substrate and mock world (no crops, no coarse dirt, no stone at target positions). Assert the outcome is `NO_TARGET`, not `SUCCESS`.

**Compatibility risk:** Low. ActuatorOutcome is only used for logging. No gameplay system consumes it. Telemetry will show more accurate outcomes (mix of SUCCESS and NO_TARGET instead of 100% SUCCESS).

---

### Finding 4: Memory equation discrepancy was a labeling error, not a defect

**Severity:** None (no defect found)  
**Confidence:** HIGH — confirmed by source inspection, numerical reproduction, and telemetry validation

**Evidence:**
- Previous report stated `formation=0.00186`, `forgetting=-0.000570`, `dM=-0.000132`
- Actual `rawFormation = 0.00186068` (matches 0.00186 — but this is the return value of `computeMemoryFormation()`, not the `formation` variable)
- Actual `formation = rawFormation × (0.30 + 0.70 × activationPeakStrength) = 0.00055820` at zero stencil
- `forgetting = -0.00056988` (matches -0.000570)
- `dM = -0.00013171` (matches -0.000132)
- Telemetry validation: ΔM < 0.002 across all 5 chunk types over 4-step intervals
- The production equations are internally consistent and correctly implemented

**Status:** DISPROVEN as a defect. The discrepancy was in labeling (rawFormation vs formation), not in arithmetic.

**Proposed change:** None. Do not modify memory coefficients or equations.

---

### Finding 5: ChunkState thresholds are unreachable in observed telemetry

**Severity:** Information (design observation, not a defect)  
**Confidence:** HIGH — confirmed by telemetry analysis

**Evidence:**
- Tension metric T(x) uses saturating functions: T = (0.40 × A/(1+A) + 0.25 × M/(1+M) + 0.20 × D/(1+D)) / 0.85
- Maximum achievable T with clamp 4.0: ~0.80 (all variables at 4.0, but saturating functions cap each term below 1.0)
- Maximum observed T in Run 3: 0.6618 (with maxA=3.24, maxM=1.92, maxD=1.34)
- Maximum observed T in Run 4: 0.4135
- ChunkState thresholds: T1=0.95 (STRAINED), T2=1.28 (FRACTURED), T3=3.0 (DECOUPLED)
- No ChunkState transitions were logged in any run

**Status:** Not a defect. The tension metric's saturating design means ChunkState thresholds above 0.95 are unreachable with the current parameter set. This may be intentional (the thresholds exist as a safety ladder for future parameter changes).

**Proposed change:** None in this phase. If the design intent is for STRAINED/FRACTURED/DECOUPLED to be reachable, the tension weights or thresholds would need adjustment — but that is a design decision, not a bug fix.

---

## Summary Table

| # | Finding | Severity | Confidence | Status | Production change needed? |
|---|---------|----------|------------|--------|--------------------------|
| 1 | RuntimeFidelityTest wrong clamp | Medium | HIGH | CONFIRMED | No (test-only fix) |
| 2 | Telemetry records wrong clamp | Low | HIGH | CONFIRMED | Yes (ExperimentLogger) |
| 3 | Resource regen misleading SUCCESS | Low | HIGH | CONFIRMED | Yes (actuator) |
| 4 | Memory equation discrepancy | None | HIGH | DISPROVEN | No |
| 5 | ChunkState thresholds unreachable | Info | HIGH | OBSERVED | No |

---

## Investigation D: Interactions

### Clamp value → threshold reachability

The clamp value (4.0 vs 1.5) does NOT affect ChunkState threshold reachability in observed telemetry because the tension metric uses saturating functions (A/(1+A)). Maximum tension with clamp 4.0 is ~0.80, well below T1=0.95. Changing the clamp to 1.5 would reduce the max tension further but would not change the fact that STRAINED/FRACTURED/DECOUPLED are unreachable.

### ChunkState → actuator behavior

ResourceRegenerationActuator does NOT read ChunkState classification. It depends only on direct substrate potentials (A, M, D), derived semantics (recoveryState, prosperityPotential), and capacity. The misleading SUCCESS issue (Finding 3) occurs regardless of ChunkState.

### Clamp → resource regeneration

The crop succession threshold (successionPotential > 0.42) fires at pristine state (successionPotential = 0.86) regardless of clamp value. Changing the clamp would not affect this behavior.

### Telemetry → actuator paths

All 6 actuators fire in telemetry (229,888 invocations each in Run 4). The production actuator paths are exercised. The misleading SUCCESS affects telemetry analysis but not gameplay.

### Correcting clamp to 1.5 — BREAKING CHANGE

If the production clamp were changed to 1.5:
- Existing worlds with A > 1.5 (up to 3.24 in Run 3) would be clamped on the next tick
- This could cause abrupt state drops and destabilize existing saves
- Migration would be needed (gradual clamp reduction or one-time adjustment)
- **This correction is NOT recommended.** The evidence shows 4.0 is the intended production value.

### Correcting RuntimeFidelityTest to expect 4.0 — SAFE

If the test is corrected to assert 4.0:
- No production code changes needed (constructor already has 4.0)
- No migration needed (existing worlds have values consistent with 4.0)
- Only test code changes
- **This correction IS recommended.**

---

## Compatibility and Migration Risks

| Change | Risk level | Migration needed? | Existing worlds affected? |
|--------|-----------|-------------------|--------------------------|
| Fix RuntimeFidelityTest (1.5 → 4.0) | None | No | No |
| Fix telemetry clamp recording | Low | No (historical telemetry keeps old format) | No |
| Fix ResourceRegenerationActuator outcome reporting | Low | No | No (outcome only used for logging) |
| Change production clamp to 1.5 | **HIGH** | **Yes** | **Yes** (abrupt clamping of existing states) |
| Change memory coefficients | HIGH | Yes | Yes |

---

## Information Needed Before Devin Can Implement

### For Finding 1 (RuntimeFidelityTest fix)

**Devin has everything needed:**
- File: `src/test/java/com/utdmod/tension/RuntimeFidelityTest.java`
- Line 37: Change `assertEquals(1.5, runtime.v2ClampMax, 1e-9)` to `assertEquals(4.0, runtime.v2ClampMax, 1e-9)`
- Verify: `./gradlew test --tests RuntimeFidelityTest`

### For Finding 2 (Telemetry clamp recording fix)

**Devin has everything needed:**
- File: `src/main/java/com/utdmod/experiment/ExperimentLogger.java`
- Method: `buildConfigurationMap()` (around line 928)
- Replace `config.put("substrate_clamp_max", UTDConfig.substrateClampMax)` with recording the effective value
- Add `substrateGameplayTuningEnabled` to the config map
- Verify: Start server, run experiment, check configuration.json

### For Finding 3 (ResourceRegenerationActuator outcome fix)

**Devin needs to know:**
- File: `src/main/java/com/utdmod/field/ResourceRegenerationActuator.java`
- The `acted` flag should be set to `true` only when a block is actually changed, not when a branch is entered
- `regenerateCrops()`, `regenerateSurfaceRecovery()`, and `regenerateOres()` should return boolean (changed/not-changed)
- The same pattern applies to `SettlementStructureActuator.java`
- Return `NO_TARGET` (not `NO_CHANGE`) when the branch fires but finds no eligible blocks
- Return `SUCCESS` only when at least one `setBlockState` is called
- Do NOT change the threshold values (0.45, 0.42) or the branch logic
- Do NOT change the ore regeneration block placement logic
- Verify: Unit test with mock world, or integration test with a Minecraft server

**Remaining uncertainty for Finding 3:** A full integration test requires a Minecraft `ServerWorld` instance. The closest faithful test seam is a unit test with a mock `ServerWorld` that tracks `setBlockState` calls. Devin should verify that the test environment supports this. If not, a source-level test that verifies the `acted` flag logic is the minimum.

---

## Final Question

**If Devin were given the findings today, would Devin know exactly what to change, why it must change, how to verify the correction, and what existing behavior must remain unchanged?**

| Finding | What to change | Why | How to verify | What must not change |
|---------|---------------|-----|--------------|---------------------|
| 1 | RuntimeFidelityTest line 37: 1.5 → 4.0 | Test encodes wrong contract | `./gradlew test --tests RuntimeFidelityTest` | Constructor default (4.0), all production code |
| 2 | ExperimentLogger: record effective v2ClampMax + tuning flag | Telemetry is misleading | Check configuration.json after experiment run | All other telemetry fields, all production code |
| 3 | ResourceRegenerationActuator: track actual block changes, return NO_TARGET when no-op | SUCCESS violates contract (SUCCESS = "changed world state") | Unit test: pristine substrate → NO_TARGET, not SUCCESS | Threshold values, branch conditions, ore placement logic |
| 4 | Nothing | No defect found | N/A | Memory equations, parameters, integration logic |
| 5 | Nothing | Design observation, not a defect | N/A | Tension formula, ChunkState thresholds |

**Answer: YES for Findings 1 and 2. YES for Finding 3 with the caveat that a mock-world test framework is needed. NO remaining uncertainty for Findings 4 and 5 (no changes needed).**

---

## Test Harnesses and Raw Outputs

| File | Purpose | Location |
|------|---------|----------|
| `phase2_memory_arithmetic.py` | A2: Numerical reproduction of memory derivative at zero stencil | `demo/sinele/phase2_memory_arithmetic.py` |
| `phase2_telemetry_validation.py` | A3: Telemetry validation for 5 chunk types | `demo/sinele/phase2_telemetry_validation.py` |
| `phase2_telemetry_output.txt` | A3: Raw output from telemetry validation | `demo/sinele/phase2_telemetry_output.txt` |
| `PHASE2_MEMORY_INVESTIGATION.md` | Full Investigation A report | `demo/sinele/PHASE2_MEMORY_INVESTIGATION.md` |
| `PHASE2_RESOURCE_REGEN_INVESTIGATION.md` | Full Investigation B report | `demo/sinele/PHASE2_RESOURCE_REGEN_INVESTIGATION.md` |
| `PHASE2_CONFIGURATION_CONTRACT.md` | Full Investigation C report | `demo/sinele/PHASE2_CONFIGURATION_CONTRACT.md` |
| `PHASE2_FINDINGS_AND_DECISIONS.md` | This synthesis report | `demo/sinele/PHASE2_FINDINGS_AND_DECISIONS.md` |

**No production source code was modified during this investigation.**  
**All artifacts are in the isolated `demo/sinele/` directory.**
