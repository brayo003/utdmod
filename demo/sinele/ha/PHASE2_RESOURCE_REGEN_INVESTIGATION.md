# PHASE 2: Resource Regeneration Investigation Report

**Investigation B — Verify resource regeneration behavior**

**Date:** 2026-10-21  
**Baseline commit:** 74d1f24  
**Evidence type:** Source inspection + Telemetry reconstruction + Independent numerical reproduction

---

## B1: Complete Call Path

### Production source

`src/main/java/com/utdmod/field/ResourceRegenerationActuator.java`

### Call chain

```
TensionServerTick.tickSubstrate() (line 207)
  → ChunkTensionData.integrateAllSubstrateStates() — substrate PDE integration
  → InterpretationService.interpretWorld() — compute semantics + intents
  → FieldActuatorRegistry.actuateAll() (line 217)
    → for each actuator:
        → actuator.actuate(world, interpretation)
    → ExperimentLogger.logActuatorInvocation() — logs outcome
```

**Key:** `actuateAll()` returns `void`. The `ActuatorOutcome` from each actuator is passed only to `ExperimentLogger.logActuatorInvocation()` for telemetry logging. No caller consumes the outcome for gameplay decisions.

### ActuatorOutcome contract (FieldActuator.java)

```java
/**
 * Return values:
 *   SUCCESS          — actuator executed and changed world state
 *   NO_CHANGE        — actuator ran but world was already in desired state
 *   NO_TARGET        — no valid target found (e.g. no crops to grow)
 *   THRESHOLD_NOT_MET — preconditions not satisfied; actuator did not execute
 *   FAILED           — execution error
 */
```

### ActuatorOutcome enum (ActuatorOutcome.java)

```java
public enum ActuatorOutcome {
    SUCCESS,           // "Actuator executed and made changes."
    NO_CHANGE,         // "Actuator executed but made no changes (already in desired state)."
    NO_TARGET,         // "Actuator could not find a target (e.g., no crops to grow)."
    FAILED,            // "Actuator execution failed due to validation or error."
    ALREADY_PRESENT,   // "Block/entity already in correct state."
    THRESHOLD_NOT_MET, // "Threshold not met; actuator did not execute."
    UNKNOWN            // "Unknown or unspecified outcome."
}
```

### ResourceRegenerationActuator.actuate() logic (lines 25–58)

```java
public ActuatorOutcome actuate(ServerWorld world, ChunkInterpretation interpretation) {
    // Null checks → THRESHOLD_NOT_MET
    // Chunk loaded check → THRESHOLD_NOT_MET

    double miningPotential = depth * 0.35 + memory * 0.40 + activation * 0.25;
    double successionPotential = semantics.recoveryState * 0.45
                               + capacity * 0.35
                               + semantics.prosperityPotential * 0.20;

    boolean acted = false;
    if (intent == IntentType.CONTINUE_MINING || miningPotential > 0.45) {
        regenerateOres(world, chunkPos, miningPotential, substrate, semantics);
        acted = true;
    }
    if (intent == IntentType.RECOVER || successionPotential > 0.42) {
        regenerateCrops(world, chunkPos, successionPotential, substrate);
        regenerateSurfaceRecovery(world, chunkPos, semantics);
        acted = true;
    }
    return acted ? ActuatorOutcome.SUCCESS
                 : ActuatorOutcome.THRESHOLD_NOT_MET;
}
```

**Critical observation:** `acted = true` is set when a branch is **entered**, not when a block is actually **changed**. The methods `regenerateCrops()` and `regenerateSurfaceRecovery()` may execute without modifying any blocks (if no eligible crops or coarse dirt exist).

### regenerateCrops() (lines 74–96)

Scans a 4×4 area of the chunk for WHEAT, CARROTS, or POTATOES blocks. If found, advances their growth age. If no crops exist, the method does nothing — no blocks change.

### regenerateSurfaceRecovery() (lines 98–108)

Checks `recoveryState >= 0.5`. If true, scans a single column for COARSE_DIRT and converts it to GRASS_BLOCK. If no coarse dirt exists, nothing happens.

### regenerateOres() (lines 60–72)

Computes a target position from chunk coordinates and transport bias. If the existing block at that position is STONE or DEEPSLATE, replaces it with an ore type. This method **does** log an `actuator_decision` event when it actually places an ore (line 84: `ExperimentLogger.logActuatorDecision(...)`).

---

## B2: Controlled Tests

**Limitation:** A full Minecraft `ServerWorld` cannot be instantiated in this environment without the full Fabric toolchain. The tests below use source-level analysis and telemetry reconstruction rather than a runtime integration test. The substrate state computation and threshold logic are reproduced from the production source code.

**Evidence type:** Telemetry reconstruction + Independent numerical reproduction

### Case 1: Pristine world and pristine substrate (A=M=D=0)

**Computation:**

| Variable | Value | Source |
|----------|-------|--------|
| capacity | 1.0 | 1/(1 + 0.5×max(0, 0)) |
| convergence | 1.0 | 1/(1 + \|0-0\| + 0 + 0) |
| recoveryState | 1.0 | clamp01(1.0×0.40 + 1.0×0.35 + 1.0×0.25) |
| prosperityPotential | 0.30 | clamp01(0 + 1.0×0.30 + 0 + 0) |
| miningPotential | 0.0 | 0×0.35 + 0×0.40 + 0×0.25 |
| **successionPotential** | **0.86** | 1.0×0.45 + 1.0×0.35 + 0.30×0.20 |

**Branch evaluation:**
- miningPotential (0.0) > 0.45? **No** → ore branch skipped
- intent == RECOVER? **No** (intent is null/ambient)
- successionPotential (0.86) > 0.42? **Yes** → crop branch fires, `acted = true`

**Result:**
- `regenerateCrops()` scans for crops → none found → **no blocks changed**
- `regenerateSurfaceRecovery()` checks recoveryState ≥ 0.5 → 1.0 ≥ 0.5 → scans for COARSE_DIRT → none found → **no blocks changed**
- **Returns: `SUCCESS`** (because `acted = true`)

**World state delta: NONE. Outcome: SUCCESS. Contract violation: YES.**

### Telemetry confirmation

Run 4 telemetry:
- 229,888 total `resource_regeneration` invocations
- 229,888 return `SUCCESS` (100%)
- 116,870 invocations with A=M=D=0 (pristine) → all return SUCCESS
- 0 invocations return THRESHOLD_NOT_MET, NO_CHANGE, or NO_TARGET

### Case 2: Pristine substrate with existing crops

If the world contained WHEAT/CARROTS/POTATOES at various growth stages:
- `regenerateCrops()` would find them and advance their growth age
- The outcome would be `SUCCESS` — and in this case blocks **would** change
- This case is **not directly testable** without a Minecraft world instance
- **Hypothesis:** If crops exist, SUCCESS is correct (blocks change). If no crops exist, SUCCESS is incorrect (no blocks change).

### Case 3: Eligible crop succession

If substrate state genuinely satisfies succession conditions (e.g., memory > 0.05, capacity < 1.0):
- `successionPotential` would be higher than the pristine 0.86
- The branch would fire (as it always does with > 0.42)
- If crops exist, they would grow — SUCCESS would be correct
- If no crops exist, the result would be the same misleading SUCCESS

### Case 4: Ore regeneration

From telemetry: 56 `actuator_decision` events with `ORE_CRYSTALLIZATION` were logged in Run 4. These are genuine `setBlockState()` calls — the ore regeneration branch **does** modify the world when the target block is STONE or DEEPSLATE.

However, the ore branch fires only when `miningPotential > 0.45` or `intent == CONTINUE_MINING`. In the pristine case, `miningPotential = 0.0`, so the ore branch is correctly skipped.

**The ore regeneration outcome reporting is correct** — it logs an `actuator_decision` only when a block is actually placed, and the `ActuatorOutcome.SUCCESS` for the overall `actuate()` call is due to the crop succession branch, not the ore branch.

### Case 5: No-op boundary cases

- **Chunks meeting numerical threshold but no eligible blocks:** successionPotential > 0.42 fires the crop branch, but if no crops and no coarse dirt exist → no block changes → SUCCESS returned incorrectly. **Confirmed** by telemetry (116,870 pristine invocations).
- **Crops already at maximum growth:** `regenerateCrops()` checks `age < 7` before incrementing. If all crops are at age 7, no blocks change → SUCCESS returned incorrectly.
- **Repeated calls:** The actuator is called every 5 ticks per chunk. No state is tracked between calls to prevent repeated scans. Each call with eligible crops would advance growth by `growthSteps` (up to 5) per call. This is by design (rapid growth under high succession potential), not a defect.

---

## B3: Severity and Intended Behavior

### Severity assessment

| Category | Applies? | Evidence |
|----------|----------|----------|
| Misleading outcome reporting | **YES** | SUCCESS returned when no blocks change. Contract says SUCCESS = "executed and changed world state". |
| Unintended crop/ore modification | **No** | No blocks change in the pristine case. Ore branch only fires when miningPotential > 0.45. |
| Unnecessary scan/performance issue | **Minor** | 229,888 invocations per run, each scanning 4×4 area + single column. No significant impact given 5-tick cadence. |
| Downstream gameplay issue from callers treating SUCCESS as world change | **No** | `actuateAll()` returns void. Outcome is only logged, never consumed for gameplay. |
| Combination | **Yes** (misleading reporting only) | No gameplay impact, no unintended modifications, only telemetry/log accuracy affected. |

### Caller consequences

```
FieldActuatorRegistry.actuateAll() → void
  → ExperimentLogger.logActuatorInvocation(..., outcome.name(), ..., outcome)
```

The `ActuatorOutcome` is:
1. Converted to a string for the `observed_outcome` field in telemetry
2. Passed to `logActuatorInvocation` which increments success/failure counters
3. **Not consumed by any gameplay system**

No code path uses the actuator outcome to make gameplay decisions. The misleading SUCCESS affects only:
- Telemetry accuracy (all resource_regeneration invocations show SUCCESS)
- Actuator success counters in experiment summaries
- Developer analysis of actuator effectiveness

### Same pattern in other actuators

`SettlementStructureActuator` has the identical pattern: `acted = true` is set when a branch is entered (e.g., `settlementTendency > 0.58`), not when a block is actually placed. The inner methods (`placeSettlementMarker`, `placeFrontierMarker`, `markAbandonment`) may find no valid surface or no eligible blocks.

### Correct behavior

The actuator should distinguish between "branch entered" and "world changed." Options:

1. **Return `NO_TARGET`** when the branch fires but no blocks are modified (requires tracking whether any `setBlockState` was called).
2. **Return `NO_CHANGE`** when the branch fires but all targets are already in the desired state.
3. **Add a `changedWorld` flag** that is set only when `setBlockState` is actually called, and return `SUCCESS` only when `changedWorld` is true.

Option 1 is most consistent with the existing `ActuatorOutcome` enum (`NO_TARGET` = "no valid target found").

**No change should be made in this phase.** The investigation documents the issue; implementation is deferred.

---

## Conclusion

The `ResourceRegenerationActuator` returns `SUCCESS` 100% of the time (229,888 invocations in Run 4), including 116,870 cases with pristine (zero) substrate where no world blocks are modified. This violates the `FieldActuator` contract which defines `SUCCESS` as "actuator executed and changed world state."

**Root cause:** The `acted` flag is set to `true` when the succession branch is entered (`successionPotential > 0.42`), not when a block is actually changed. At pristine state, `successionPotential = 0.86` (because `recoveryState = 1.0` and `capacity = 1.0` at zero substrate), which exceeds the 0.42 threshold.

**Impact:** Misleading telemetry only. No downstream gameplay consequences because `actuateAll()` returns void and no caller consumes the outcome.

**Confirmed defect:** Misleading outcome reporting (not unintended modification, not gameplay bug).
