# PHASE 2: Configuration Contract Report

**Investigation C — Establish the intended clamp contract**

**Date:** 2026-10-21  
**Baseline commit:** 74d1f24  
**Evidence type:** Source inspection + Telemetry reconstruction + Unit test inspection

---

## C1: Origin and Purpose of Both Values

### v2ClampMax = 4.0 (ParameterSet constructor default)

**Source:** `src/main/java/com/utdmod/tension/ParameterSet.java`, line 135

```java
this.v2ClampMax = 4.0;  // Safety valve only; must exceed memoryMaximum (2.0) and depthMaximum (2.0)
```

**Purpose:** A safety-valve hard clamp applied to all four substrate variables (A, M, D, B) after each Euler integration step. The comment explicitly states it must exceed the dynamical saturation bounds (`memoryMaximum = 2.0`, `depthMaximum = 2.0`) so it never fires in normal operation. The logistic saturation `(1 - M/Mmax)` naturally bounds M below `memoryMaximum` (2.0), and the carrying-capacity factor `max(0, 1 - D/dynamicDmax)` bounds D below `depthMaximum` (2.0). The 4.0 clamp exists only to prevent numerical overflow in pathological cases.

**Documentation consensus:** All three verified documentation files record 4.0 as the production clamp:

- `docs/architecture_audit/PARAMETER_CATALOG.md` line 50: `"Activation hard clamp: v2ClampMax = 4.0 (applies to all four state variables)"`
- `docs/architecture_audit/EQUATIONS.md` lines 17–20: `"| Activation | A | SubstrateState.activation | [0, v2ClampMax=4.0] |"` (same for M, D, B)
- `docs/SUBSTRATE_AUDIT.md` line 25: `"memory = clamp(prevM + dMemory, 0.0, v2ClampMax=4.0)"` and line 147: `"M(t+1) = clamp(M(t) + dMemory, 0.0, 4.0)"`

### substrateClampMax = 1.5 (UTDConfig)

**Source:** `src/main/java/com/utdmod/config/UTDConfig.java`

```java
// Clamp (laboratory default: 1.5)
public static double substrateClampMax = 1.5;
```

**Purpose:** A gameplay-tuning override value. It is applied to `v2ClampMax` **only** when `substrateGameplayTuningEnabled = true`, via `ParameterSet.applyConfigOverrides()`:

```java
public void applyConfigOverrides() {
    // ...
    v2ClampMax = UTDConfig.substrateClampMax;  // sets to 1.5
    // ...
}
```

The comment says "laboratory default: 1.5." This suggests 1.5 was the clamp value used during early laboratory experiments, before the safety-valve design was finalized at 4.0.

### RuntimeFidelityTest expects 1.5

**Source:** `src/test/java/com/utdmod/tension/RuntimeFidelityTest.java`

```java
@Test
void runtimeUsesLaboratoryDefaultsWhenGameplayTuningDisabled() {
    UTDConfig.substrateGameplayTuningEnabled = false;
    UTDConfig.substrateClampMax = 5.0;       // Sets config to 5.0
    UTDConfig.substrateFieldDepthRadius = 1.0;
    UTDConfig.substrateReadinessScale = 2.0;
    ParameterSet.resetRuntimeCache();

    ParameterSet runtime = ParameterSet.runtime();
    assertEquals(1.5, runtime.v2ClampMax, 1e-9);    // Expects 1.5
    assertEquals(0.0, runtime.v3FieldDepthRadius, 1e-9);
    assertEquals(1.0, runtime.readinessMemoryScale, 1e-9);
}
```

**The test is wrong.** It sets `substrateClampMax = 5.0` and asserts `v2ClampMax = 1.5`. When `substrateGameplayTuningEnabled = false`, `applyConfigOverrides()` is never called, so `v2ClampMax` should remain at the constructor default of 4.0. The test expects 1.5, which is neither the constructor default (4.0) nor the configured override (5.0).

The test appears to assume that the constructor default for `v2ClampMax` is 1.5, but it is 4.0. The test encodes a contract that does not match the production code.

---

## C2: Contract Table

| Mode | substrateGameplayTuningEnabled | applyConfigOverrides called? | Effective v2ClampMax | Intended? | Evidence |
|------|-------------------------------|---------------------------|---------------------|-----------|----------|
| **Default production runtime** | false | No | **4.0** | **Yes** | Constructor line 135; docs; telemetry (A>1.5 observed) |
| **Gameplay tuning disabled** | false | No | **4.0** | **Yes** | Same as above |
| **Gameplay tuning enabled** | true | Yes | **1.5** | **Yes** (intentional override) | applyConfigOverrides() sets to UTDConfig.substrateClampMax |
| **Simulation-lab parity** | N/A (standalone) | N/A | N/A | N/A | Simulation_lab has no ParameterSet; stub only |
| **Historical Run 3** | false (inferred) | No | **4.0** | **Yes** | Telemetry: maxA=3.2404 (exceeds 1.5) |
| **Historical Run 4** | false (inferred) | No | **4.0** | **Yes** | Telemetry: maxM=1.8978 (exceeds 1.5) |
| **RuntimeFidelityTest expectation** | false | No | **1.5** (asserted) | **No** (wrong) | Test asserts 1.5; constructor is 4.0 |

### Telemetry configuration recording

**Source:** `src/main/java/com/utdmod/experiment/ExperimentLogger.java`, `buildConfigurationMap()` (lines 912–928)

```java
config.put("substrate_clamp_max", UTDConfig.substrateClampMax);  // Records 1.5
```

The telemetry records `UTDConfig.substrateClampMax` (1.5), **not** the effective runtime `v2ClampMax` (4.0). Additionally, `substrateGameplayTuningEnabled` is **not recorded** in telemetry at all.

**This means the telemetry configuration.json is misleading.** It shows `substrate_clamp_max = 1.5` for all runs, but the actual runtime clamp was 4.0 (because gameplay tuning was disabled and `applyConfigOverrides()` was never called).

### Telemetry evidence proving runtime used 4.0

| Run | maxA | maxM | maxD | Exceeds 1.5? | Below 4.0? |
|-----|------|------|------|-------------|-----------|
| Run 1 (14-08-14) | 0.0002 | 0.0000 | 0.0004 | No | Yes |
| Run 2 (14-14-14) | 0.0065 | 0.0000 | 0.0209 | No | Yes |
| Run 3 (14-18-49) | **3.2404** | **1.9234** | 1.3378 | **Yes (A, M)** | Yes |
| Run 4 (14-26-10) | 0.4041 | **1.8978** | 1.3838 | **Yes (M)** | Yes |

Run 3 activation reached 3.24 — this is impossible if the clamp were 1.5. Run 4 memory reached 1.90 — also impossible with clamp 1.5. Both are consistent with a 4.0 clamp.

### FieldDerivativeParityTest confirms 4.0 is known

`src/test/java/com/utdmod/tension/FieldDerivativeParityTest.java`, line 79:

```java
public void eulerIntegrationClampsToParameterMax() {
    ParameterSet params = new ParameterSet();
    params.v2ClampMax = 1.5;  // Explicitly overrides to 1.5 for this test only
    // ...
    assertTrue(state.getActivation() <= params.v2ClampMax);
}
```

This test explicitly overrides `v2ClampMax` to 1.5, confirming the author knew the default was not 1.5. The test creates a `new ParameterSet()` (which has `v2ClampMax = 4.0`) and then overrides it for the test scenario.

---

## C3: Minimal Justified Correction

### Finding 1: RuntimeFidelityTest asserts the wrong value

**Current:** Test asserts `v2ClampMax = 1.5` when gameplay tuning is disabled.  
**Should be:** Test should assert `v2ClampMax = 4.0` (the constructor default).

**Change required:**
- `RuntimeFidelityTest.java` line 37: Change `assertEquals(1.5, runtime.v2ClampMax, 1e-9)` to `assertEquals(4.0, runtime.v2ClampMax, 1e-9)`

**Risk:** None. This is a test-only change. No production code changes. No migration needed. Existing worlds have values consistent with 4.0.

### Finding 2: Telemetry records the wrong clamp value

**Current:** Telemetry `configuration.json` records `UTDConfig.substrateClampMax` (1.5), not the effective runtime `v2ClampMax` (4.0).  
**Should record:** The effective `v2ClampMax` from `ParameterSet.runtime()`, plus `substrateGameplayTuningEnabled`.

**Change required:**
- `ExperimentLogger.buildConfigurationMap()`: Replace `config.put("substrate_clamp_max", UTDConfig.substrateClampMax)` with `config.put("effective_v2_clamp_max", ParameterSet.runtime().v2ClampMax)` and add `config.put("substrate_gameplay_tuning_enabled", UTDConfig.substrateGameplayTuningEnabled)`

**Risk:** Low. This changes telemetry output format. Historical telemetry files will have the old field. New telemetry will have the correct field. No gameplay impact.

### Finding 3: No production code changes needed

The production code is correct as-is:
- `ParameterSet` constructor defaults `v2ClampMax = 4.0` — correct
- `ParameterSet.runtime()` only applies overrides when tuning is enabled — correct
- `UTDConfig.substrateGameplayTuningEnabled = false` by default — correct
- `SubstrateState.applyUnifiedFieldStep()` clamps to `parameters.v2ClampMax` — correct

The only issues are in the test (wrong assertion) and telemetry logging (wrong value recorded).

---

## C4: What Must NOT Change

1. **Do not change `v2ClampMax` to 1.5 in the ParameterSet constructor.** This would be a breaking change for existing persisted worlds (Run 3 has A up to 3.24, Run 4 has M up to 1.90). States above 1.5 would be abruptly clamped on the next integration step.

2. **Do not enable `substrateGameplayTuningEnabled` by default.** This would apply all gameplay-tuning overrides, not just the clamp, potentially changing substrate dynamics in ways not validated by the existing telemetry.

3. **Do not remove the `substrateClampMax = 1.5` field from UTDConfig.** It is a valid gameplay-tuning knob used when `substrateGameplayTuningEnabled = true`.

4. **Do not change the FieldDerivative or SubstrateState code.** The memory and depth equations are verified correct (see PHASE2_MEMORY_INVESTIGATION.md).

---

## Conclusion

The production runtime correctly uses `v2ClampMax = 4.0` as a safety-valve clamp. The `substrateClampMax = 1.5` in `UTDConfig` is a gameplay-tuning override that is only applied when `substrateGameplayTuningEnabled = true` (default: false). The `RuntimeFidelityTest` incorrectly asserts 1.5 instead of 4.0, encoding a contract that does not match the production code. Telemetry incorrectly records 1.5 instead of the effective 4.0.

The minimal justified correction is:
1. Fix `RuntimeFidelityTest` to assert 4.0 (test-only change, no risk)
2. Fix telemetry to record the effective clamp value and tuning flag (low risk)

No production source code changes are needed.
