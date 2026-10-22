# Phase 2A — Resource Regeneration Actuator: Forensic Verification

**Date:** 2026-10-22
**Baseline commit:** `74d1f24`
**Investigator:** Independent forensic audit
**No production source code, tests, or configuration were modified.**

---

## Executive Summary

`ResourceRegenerationActuator.actuate()` returns `SUCCESS` whenever a branch is **entered**, not when a Minecraft block is actually **changed**. This is confirmed by source inspection. In 4 of 6 specified scenarios, the actuator returns `SUCCESS` despite zero `setBlockState` calls — a contract violation.

`SettlementStructureActuator` has the identical premature-success pattern. This is reported separately and does not expand the implementation scope.

Execution of the real Java actuator is impossible in the current environment because `ServerWorld` cannot be instantiated without a full Minecraft server bootstrap, and no mocking framework exists in the project. The runtime claim is therefore **explicitly unresolved** — all conclusions below are established from source inspection and formula reproduction, not from running the production actuator.

---

## Evidence Types Used

| Label | Meaning |
|-------|---------|
| **Source inspection** | Direct reading of production `.java` source code |
| **Formula reproduction** | Python replication of exact Java formulas from source |
| **Unit test** | JUnit test executing real production Java classes |
| **Mock-world test** | Test with a mock/fake `ServerWorld` instance |
| **Actual Minecraft integration test** | Test with a live `ServerWorld` from a running server |

No unit tests, mock-world tests, or integration tests were executed in this phase. All conclusions are from **source inspection** and **formula reproduction**.

---

## 1. Production Source Inspection

### 1.1 ActuatorOutcome Enum

**File:** `src/main/java/com/utdmod/experiment/ActuatorOutcome.java`
**Evidence:** Source inspection

```java
public enum ActuatorOutcome {
    /** Actuator executed and made changes. */
    SUCCESS,
    /** Actuator executed but made no changes (already in desired state). */
    NO_CHANGE,
    /** Actuator could not find a target (e.g., no crops to grow). */
    NO_TARGET,
    /** Actuator execution failed due to validation or error. */
    FAILED,
    /** Block/entity already in correct state. */
    ALREADY_PRESENT,
    /** Threshold not met; actuator did not execute. */
    THRESHOLD_NOT_MET,
    /** Unknown or unspecified outcome. */
    UNKNOWN
}
```

### 1.2 FieldActuator Interface Contract

**File:** `src/main/java/com/utdmod/field/FieldActuator.java`
**Evidence:** Source inspection

```java
/**
 * Return values:
 *   SUCCESS          — actuator executed and changed world state
 *   NO_CHANGE        — actuator ran but world was already in desired state
 *   NO_TARGET        — no valid target found (e.g. no crops to grow)
 *   THRESHOLD_NOT_MET — preconditions not satisfied; actuator did not execute
 *   FAILED           — execution error
 */
public interface FieldActuator {
  ActuatorOutcome actuate(ServerWorld world, ChunkInterpretation interpretation);
  String getId();
}
```

The contract is unambiguous: `SUCCESS` means "changed world state," not "threshold was met."

### 1.3 ResourceRegenerationActuator.actuate() — The Defect

**File:** `src/main/java/com/utdmod/field/ResourceRegenerationActuator.java`, lines 25–58
**Evidence:** Source inspection

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
      acted = true;                                    // ← set on BRANCH ENTRY
    }
    if (intent == IntentType.RECOVER || successionPotential > 0.42) {
      regenerateCrops(world, chunkPos, successionPotential, substrate);
      regenerateSurfaceRecovery(world, chunkPos, semantics);
      acted = true;                                    // ← set on BRANCH ENTRY
    }
    return acted ? ActuatorOutcome.SUCCESS
                 : ActuatorOutcome.THRESHOLD_NOT_MET;
}
```

**Root cause:** `acted = true` is set when a branch condition is satisfied (threshold exceeded), not when `setBlockState` is actually called. The inner methods (`regenerateCrops`, `regenerateSurfaceRecovery`, `regenerateOres`) are `void` — they return nothing, so the caller has no way to know whether any block changed.

### 1.4 Inner Methods — Conditional Block Changes

**Evidence:** Source inspection

#### regenerateOres() (lines 60–84)

```java
private void regenerateOres(ServerWorld world, ChunkPos chunkPos, double potential, ...) {
    // Computes target BlockPos from chunk coords + transport bias
    BlockPos pos = new BlockPos(x, y, z);
    if (!SpatialBehaviorHelper.isChunkLoaded(world, x, z)) { return; }
    Block existing = world.getBlockState(pos).getBlock();
    if (existing == Blocks.STONE || existing == Blocks.DEEPSLATE) {
        Block ore = selectOreType(potential);
        world.setBlockState(pos, ore.getDefaultState());
        ExperimentLogger.logActuatorDecision(..., "ORE_CRYSTALLIZATION", ...);
    }
    // If existing is NOT STONE/DEEPSLATE: method returns silently, no block change
}
```

- Block change occurs **only** when the target position contains `STONE` or `DEEPSLATE`.
- If the target is `AIR`, `DIRT`, or any other block: no change, no log, silent return.
- The method is `void` — no signal back to `actuate()`.

#### regenerateCrops() (lines 74–96)

```java
private void regenerateCrops(ServerWorld world, ChunkPos chunkPos, double potential, ...) {
    int growthSteps = Math.min(5, Math.max(1, (int) Math.floor(potential * 10)));
    for (int dx = 0; dx < 4; dx++) {
      for (int dz = 0; dz < 4; dz++) {
        // Scans downward from world.getTopY() to y > 48
        Block block = world.getBlockState(below).getBlock();
        if (block == Blocks.WHEAT || block == Blocks.CARROTS || block == Blocks.POTATOES) {
            // Finds age property, advances it
            for (int step = 0; step < growthSteps; step++) {
              int age = state.get(ageProperty);
              if (age < 7) {
                state = state.with(ageProperty, Math.min(7, age + 1));
                world.setBlockState(below, state);
              }
              // If age == 7: if-body skipped, no setBlockState
            }
            break;
        }
        // If no crop found in column: loop ends, no change
      }
    }
}
```

- Block change occurs **only** when a crop block (`WHEAT`, `CARROTS`, `POTATOES`) is found in the 4×4 scan area at age < 7.
- If no crops exist: no change.
- If all crops are at age 7 (max): no change.
- The method is `void` — no signal back to `actuate()`.

#### regenerateSurfaceRecovery() (lines 98–108)

```java
private void regenerateSurfaceRecovery(ServerWorld world, ChunkPos chunkPos, SubstrateSemantics semantics) {
    if (semantics.recoveryState < 0.5) { return; }
    int x = chunkPos.getStartX() + 4;
    int z = chunkPos.getStartZ() + 4;
    for (int y = 60; y < 100; y++) {
      BlockPos pos = new BlockPos(x, y, z);
      if (world.getBlockState(pos).getBlock() == Blocks.COARSE_DIRT) {
        world.setBlockState(pos, Blocks.GRASS_BLOCK.getDefaultState());
        break;
      }
    }
    // If no COARSE_DIRT found: no change
}
```

- Block change occurs **only** when `COARSE_DIRT` exists at (startX+4, startZ+4) in the y=60–99 range.
- If no coarse dirt: no change.
- The method is `void` — no signal back to `actuate()`.

---

## 2. ActuatorOutcome Caller Trace

**Evidence:** Source inspection (grep across all `.java` files in `src/`)

Every usage of `ActuatorOutcome` was traced. The outcome is consumed in exactly three places:

### 2.1 FieldActuatorRegistry.actuateAll()

**File:** `src/main/java/com/utdmod/field/FieldActuatorRegistry.java`, lines 38–55

```java
ActuatorOutcome outcome = ActuatorOutcome.UNKNOWN;
try {
    outcome = actuator.actuate(world, interpretation);
    if (outcome == null) outcome = ActuatorOutcome.UNKNOWN;
} catch (Throwable t) {
    outcome = ActuatorOutcome.FAILED;
} finally {
    ExperimentLogger.logActuatorInvocation(
        TensionTraceClock.getServerTick(), world, interpretation.chunkPos,
        actuator.getId(), interpretation.substrate,
        outcome.name(), ..., outcome);
}
```

- The outcome is passed to `ExperimentLogger.logActuatorInvocation()` in the `finally` block.
- No conditional logic branches on `SUCCESS` vs `NO_TARGET` vs `NO_CHANGE`.
- `actuateAll()` returns `void` — the outcome does not propagate upward.

### 2.2 ExperimentLogger.logActuatorInvocation()

**File:** `src/main/java/com/utdmod/experiment/ExperimentLogger.java`, lines 147–189

```java
if (outcome == ActuatorOutcome.SUCCESS || outcome == ActuatorOutcome.NO_CHANGE) {
    incrementActuatorSuccess(actuatorId);
} else if (outcome == ActuatorOutcome.FAILED) {
    incrementActuatorFailure(actuatorId);
}
```

- `SUCCESS` and `NO_CHANGE` both increment the success counter.
- `NO_TARGET`, `THRESHOLD_NOT_MET`, `ALREADY_PRESENT`, and `UNKNOWN` are neither success nor failure — they are counted as total invocations only.
- The outcome string is written to telemetry as `observed_outcome`.

### 2.3 ExperimentLogger.logActuatorDecision()

**File:** `src/main/java/com/utdmod/experiment/ExperimentLogger.java`, lines 189–193

```java
if (outcome == ActuatorOutcome.SUCCESS) {
    incrementActuatorSuccess(actuatorId);
} else if (outcome == ActuatorOutcome.FAILED) {
    incrementActuatorFailure(actuatorId);
}
```

- Used for individual decision events (e.g., `ORE_CRYSTALLIZATION`).
- `regenerateOres()` calls this with `ActuatorOutcome.SUCCESS` when it actually places an ore — this is correct.

### 2.4 No Gameplay Consumer

No code path anywhere in the codebase uses the actuator outcome to make gameplay decisions. The outcome is telemetry-only. A misleading `SUCCESS` does not cause incorrect gameplay behavior — it only corrupts telemetry accuracy and actuator success counters.

---

## 3. Outcome Semantics — Contractual Definitions

**Evidence:** Source inspection of `ActuatorOutcome.java` and `FieldActuator.java`

The enum defines seven outcomes with distinct contractual meanings. The following table maps each to the correct scenario for `ResourceRegenerationActuator`:

| Outcome | Contract meaning | When it should apply to resource regen |
|---------|-------------------|----------------------------------------|
| `SUCCESS` | "Actuator executed and made changes" | At least one `setBlockState` was called |
| `NO_CHANGE` | "Actuator executed but made no changes (already in desired state)" | Target found but already at max growth / already grass |
| `NO_TARGET` | "Actuator could not find a target" | No crops, no coarse dirt, ore target not STONE/DEEPSLATE |
| `FAILED` | "Execution error" | Exception thrown |
| `ALREADY_PRESENT` | "Block/entity already in correct state" | Similar to `NO_CHANGE` (unused in this actuator) |
| `THRESHOLD_NOT_MET` | "Threshold not met; actuator did not execute" | Neither branch condition satisfied |
| `UNKNOWN` | "Unknown or unspecified outcome" | Default in registry; not produced by this actuator |

### Key distinction: NO_TARGET vs NO_CHANGE

- **`NO_TARGET`**: The branch fired but the scanned world contains no eligible target. Example: succession branch fires, but no crops exist and no coarse dirt exists.
- **`NO_CHANGE`**: The branch fired, a target was found, but it is already in the desired state. Example: crops found but all at age 7.

These are distinct conditions with different diagnostic value. `NO_TARGET` means the actuator had nothing to act on. `NO_CHANGE` means the actuator found its target but no modification was needed.

---

## 4. Six Specified Scenarios

**Evidence:** Formula reproduction + source inspection

**Harness:** `demo/sinele/ha/phase2a_resource_regen_scenarios.py`
**Raw output:** `demo/sinele/ha/phase2a_resource_regen_scenarios_output.txt`

World-state assumptions are stated explicitly for each scenario. They are **not** inferred from substrate values. A pristine substrate (A=M=D=0) does not establish that the Minecraft chunk is empty — it only means the substrate has no memory of disturbance.

### Scenario 1: Succession threshold met, no eligible targets

| Field | Value |
|-------|-------|
| Substrate | A=0, M=0, D=0, B=0 (pristine) |
| Derived | capacity=1.0, convergence=1.0, recoveryState=1.0, prosperityPotential=0.30 |
| Potentials | mining=0.0 (≤ 0.45), succession=0.86 (> 0.42) |
| World assumptions | No crops, no coarse dirt, ore target = AIR |
| Branches entered | Mining: NO, Succession: YES |
| setBlockState calls | NONE |
| World changed | NO |
| **Actual outcome** | **SUCCESS** |
| **Correct outcome** | **NO_TARGET** |
| **Contract violation** | **YES** — returns SUCCESS, should return NO_TARGET |

### Scenario 2: Crops found at maximum growth (age 7)

| Field | Value |
|-------|-------|
| Substrate | A=0, M=0, D=0, B=0 (pristine) |
| Derived | capacity=1.0, convergence=1.0, recoveryState=1.0, prosperityPotential=0.30 |
| Potentials | mining=0.0 (≤ 0.45), succession=0.86 (> 0.42) |
| World assumptions | WHEAT at age 7 (×2), no coarse dirt, ore target = AIR |
| Branches entered | Mining: NO, Succession: YES |
| setBlockState calls | NONE (age < 7 guard prevents modification) |
| World changed | NO |
| **Actual outcome** | **SUCCESS** |
| **Correct outcome** | **NO_CHANGE** (target found but already in desired state) |
| **Contract violation** | **YES** — returns SUCCESS, should return NO_CHANGE |

### Scenario 3: Eligible crop changes (age 3 → advanced)

| Field | Value |
|-------|-------|
| Substrate | A=0.2, M=0.1, D=0.05, B=0.01 |
| Derived | capacity=0.909, convergence=0.870, recoveryState=0.899, prosperityPotential=0.319 |
| Potentials | mining=0.108 (≤ 0.45), succession=0.787 (> 0.42) |
| World assumptions | WHEAT at age 3, no coarse dirt, ore target = AIR |
| Branches entered | Mining: NO, Succession: YES |
| setBlockState calls | `setBlockState(crop_pos, age=7)` |
| World changed | YES |
| **Actual outcome** | **SUCCESS** |
| **Correct outcome** | **SUCCESS** |
| **Contract violation** | **NO** — correct |

### Scenario 4: Mining branch, invalid ore target (AIR at target pos)

| Field | Value |
|-------|-------|
| Substrate | A=0.5, M=0.5, D=0.5, B=0.0 |
| Derived | capacity=0.8, convergence=0.5, recoveryState=0.672, prosperityPotential=0.515 |
| Potentials | mining=0.50 (> 0.45), succession=0.685 (> 0.42) |
| World assumptions | No crops, no coarse dirt, ore target = AIR |
| Branches entered | Mining: YES, Succession: YES |
| setBlockState calls | NONE (AIR is not STONE/DEEPSLATE) |
| World changed | NO |
| **Actual outcome** | **SUCCESS** |
| **Correct outcome** | **NO_TARGET** |
| **Contract violation** | **YES** — returns SUCCESS, should return NO_TARGET |

### Scenario 5: Mining branch, successful ore placement (STONE at target)

| Field | Value |
|-------|-------|
| Substrate | A=0.5, M=0.5, D=0.5, B=0.0 |
| Derived | capacity=0.8, convergence=0.5, recoveryState=0.672, prosperityPotential=0.515 |
| Potentials | mining=0.50 (> 0.45), succession=0.685 (> 0.42) |
| World assumptions | No crops, no coarse dirt, ore target = STONE |
| Branches entered | Mining: YES, Succession: YES |
| setBlockState calls | `setBlockState(target_pos, IRON_ORE)` |
| World changed | YES |
| **Actual outcome** | **SUCCESS** |
| **Correct outcome** | **SUCCESS** |
| **Contract violation** | **NO** — correct |

### Scenario 6: Both branches, no change (no crops, no coarse dirt, DIRT at ore target)

| Field | Value |
|-------|-------|
| Substrate | A=0.5, M=0.5, D=0.5, B=0.0 |
| Derived | capacity=0.8, convergence=0.5, recoveryState=0.672, prosperityPotential=0.515 |
| Potentials | mining=0.50 (> 0.45), succession=0.685 (> 0.42) |
| World assumptions | No crops, no coarse dirt, ore target = DIRT |
| Branches entered | Mining: YES, Succession: YES |
| setBlockState calls | NONE |
| World changed | NO |
| **Actual outcome** | **SUCCESS** |
| **Correct outcome** | **NO_TARGET** |
| **Contract violation** | **YES** — returns SUCCESS, should return NO_TARGET |

### Summary

| Scenario | Actual | Correct | Violation? |
|----------|--------|---------|------------|
| 1: No eligible targets | SUCCESS | NO_TARGET | YES |
| 2: Crops at max growth | SUCCESS | NO_CHANGE | YES |
| 3: Eligible crop changes | SUCCESS | SUCCESS | NO |
| 4: Invalid ore target | SUCCESS | NO_TARGET | YES |
| 5: Successful ore placement | SUCCESS | SUCCESS | NO |
| 6: Both branches, no change | SUCCESS | NO_TARGET | YES |

**4 of 6 scenarios produce a contract violation.** In all violations, `actuate()` returns `SUCCESS` despite zero `setBlockState` calls.

---

## 5. Test Feasibility Investigation

**Evidence:** Source inspection of `build.gradle`, `settings.gradle`, `gradle.properties`, existing test files

### 5.1 Existing Test Framework

| Item | Value |
|------|-------|
| Test framework | JUnit 5 (Jupiter 5.10.0) |
| Mocking framework | **None** (no Mockito, no EasyMock, no PowerMock) |
| Build system | Fabric-loom 1.4.6, Gradle |
| Minecraft version | 1.20.4 |
| Java version | 17 |
| Existing actuator tests | **None** |

### 5.2 Existing Test Patterns

All existing tests operate on pure Java objects that do not require `ServerWorld`:

- `SubstrateStateTest` — tests `SubstrateState` math directly
- `InterpretationEngineTest` — tests `SubstrateInterpretationEngine.computeSemantics()` with `SubstrateState` and `HashMap` snapshots
- `SpatialBehaviorHelperTest` — tests `resolveCorridorDirection(null, ...)` (passes `null` for world) and `isChunkLoaded(null, ...)` (returns false)
- `RegionEcologyManagerTest` — tests `selectPolicy()` with pure data objects
- `RuntimeFidelityTest` — tests `ParameterSet.runtime()` configuration

No existing test instantiates or mocks `ServerWorld`.

### 5.3 ServerWorld Dependency Chain

`ResourceRegenerationActuator.actuate()` requires `ServerWorld` at multiple points:

1. **`SpatialBehaviorHelper.isChunkLoaded(world, chunkPos)`** → calls `world.isChunkLoaded(x, z)` — requires a real or mock `ServerWorld`
2. **`regenerateOres()`** → calls `InterpretationService.get(world, chunkPos)` (static map lookup keyed by `ServerWorld`), `SpatialBehaviorHelper.resolveCorridorDirection(world, ...)`, `world.getBlockState(pos)`, `world.setBlockState(pos, state)`
3. **`regenerateCrops()`** → calls `world.getTopY()`, `world.getBlockState(pos)`, `world.setBlockState(pos, state)`
4. **`regenerateSurfaceRecovery()`** → calls `world.getBlockState(pos)`, `world.setBlockState(pos, state)`

### 5.4 Why ServerWorld Cannot Be Instantiated

`ServerWorld` is a Minecraft server class that requires:
- A `MinecraftServer` instance (requires full server bootstrap)
- A `World` registry key, dimension type, chunk manager, etc.
- Fabric-loom does not provide a test harness for `ServerWorld`
- No Fabric API test utilities for mock worlds exist in this project

### 5.5 Options for Testing the Real Java Actuator

| Option | What it requires | Modifies production code? | Modifies test infrastructure? |
|--------|-----------------|--------------------------|------------------------------|
| A. Add Mockito dependency | Add `testImplementation 'org.mockito:mockito-core'` to `build.gradle` | No | Yes (adds dependency) |
| B. Create a test-double wrapper | Subclass `ServerWorld` or create an interface seam | Possibly (if seam added to actuator) | Yes (new test class) |
| C. Fabric test harness | Use Fabric's `AutoTestPlayer` or similar | No | Yes (significant setup) |
| D. Integration test with running server | Start `./gradlew runServer` and run scripted scenarios | No | Yes (test infrastructure) |

**Option A (Mockito)** is the narrowest path. It adds a test-only dependency, does not modify production code, and allows creating a mock `ServerWorld` that returns controlled `BlockState` values for given `BlockPos` arguments and tracks `setBlockState` calls. However, it requires mocking `world.isChunkLoaded()`, `world.getTopY()`, `world.getBlockState()`, `world.setBlockState()`, `world.getTime()`, and `InterpretationService.get()` (which uses a static map keyed by `ServerWorld`). This is a substantial mock setup but feasible.

**Option B (test-double)** would require extracting a `WorldAccessor` interface from the actuator, which is a production code change. This investigation does not recommend production code changes.

**Option D (integration test)** is the most faithful but requires a running Minecraft server and scripted world setup. It is outside the scope of a unit test but would provide "actual Minecraft integration test" evidence.

### 5.6 Conclusion on Test Feasibility

Testing the real Java actuator with a mock `ServerWorld` is **technically feasible** by adding Mockito as a test dependency, but it is **not possible with the current project configuration**. The project has no mocking framework, no Fabric test harness, and `ServerWorld` cannot be instantiated without a full server bootstrap.

The runtime claim (does the production actuator actually return SUCCESS when no blocks change?) is **confirmed from source inspection** — the `acted` flag is set on branch entry, not on block change — but is **not confirmed by runtime execution**. This remains explicitly unresolved.

---

## 6. SettlementStructureActuator — Same Pattern

**Evidence:** Source inspection

**File:** `src/main/java/com/utdmod/field/SettlementStructureActuator.java`, lines 22–50

```java
public ActuatorOutcome actuate(ServerWorld world, ChunkInterpretation interpretation) {
    // Null + chunk loaded checks → THRESHOLD_NOT_MET

    boolean acted = false;
    if (intent == IntentType.CREATE_SETTLEMENT || semantics.settlementTendency > 0.58) {
      placeSettlementMarker(world, chunkPos, semantics, interpretation.substrate);
      acted = true;                                    // ← set on BRANCH ENTRY
    }
    if (intent == IntentType.EXPAND_TERRITORY || semantics.expansionTendency > 0.55) {
      placeFrontierMarker(world, chunkPos, semantics);
      acted = true;                                    // ← set on BRANCH ENTRY
    }
    if (intent == IntentType.COLLAPSE || semantics.collapseTendency > 0.72) {
      markAbandonment(world, chunkPos);
      acted = true;                                    // ← set on BRANCH ENTRY
    }
    return acted ? ActuatorOutcome.SUCCESS
                 : ActuatorOutcome.THRESHOLD_NOT_MET;
}
```

### Identical defect pattern

The `acted` flag is set to `true` when a branch condition is met, not when `setBlockState` is actually called. The inner methods (`placeSettlementMarker`, `placeFrontierMarker`, `markAbandonment`) may find no valid surface or no eligible blocks:

- **`placeSettlementMarker()`**: Calls `findSurface()` which may return `null` → early return, no block change. Checks if block below surface is `GRASS_BLOCK` or `DIRT` — if not, no block change.
- **`placeFrontierMarker()`**: Calls `findSurface()` which may return `null` → early return. Checks if surface is air — if not, no block change.
- **`markAbandonment()`**: Calls `findSurface()` which may return `null` → early return. Checks if surface is `CAMPFIRE` or `HAY_BLOCK` — if not, no block change.

### Separate finding — does not expand implementation scope

This is reported as a separate finding. The implementation specification in Section 8 addresses `ResourceRegenerationActuator` only. The same fix pattern (returning boolean from inner methods, setting `acted` only when a block actually changes) would apply to `SettlementStructureActuator`, but that should be a separate recommendation with its own evidence and scope.

---

## 7. Other Actuators — Outcome Pattern Comparison

**Evidence:** Source inspection

| Actuator | Pattern | Correct? |
|----------|---------|----------|
| `WeatherActuator` | Always returns `NO_CHANGE` (delegated to `RegionalWeatherCoordinator`) | ✓ Correct (no-op by design) |
| `MobBehaviorActuator` | `acted = true` when a mob is found; returns `SUCCESS` or `NO_TARGET` if no mobs | ✓ Correct (returns `NO_TARGET` when no mobs found) |
| `VillageActuator` | Returns `NO_TARGET` when `villagers.isEmpty()`, `SUCCESS` otherwise | ✓ Correct (target existence checked before action) |
| `ParticleVisualActuator` | `spawned = true` when particle spawn conditions met | ✓ Correct (particle spawning is the "change") |
| `GrassWearActuator` | Returns `SUCCESS` after `wearCorridorSegment()` regardless of whether any block changed | ✗ Same defect pattern (but not in scope) |
| `ResourceRegenerationActuator` | `acted = true` on branch entry, not on block change | ✗ **Defect** |
| `SettlementStructureActuator` | `acted = true` on branch entry, not on block change | ✗ Same defect (separate finding) |

Note: `GrassWearActuator` also has the same pattern — `wearCorridorSegment()` returns an `int` (count of blocks worn), but the actuator ignores it and always returns `SUCCESS`. This is noted but not in scope for this investigation.

---

## 8. Minimal Implementation Specification for Devin

The behavior and outcome contract are sufficiently established from source inspection and formula reproduction. The following specification is provided for Devin.

### 8.1 What to Change

**File:** `src/main/java/com/utdmod/field/ResourceRegenerationActuator.java`

1. Change `regenerateOres()` from `void` to `boolean` — return `true` when `setBlockState` is called, `false` otherwise.

2. Change `regenerateCrops()` from `void` to `boolean` — return `true` when at least one `setBlockState` is called, `false` otherwise.

3. Change `regenerateSurfaceRecovery()` from `void` to `boolean` — return `true` when `setBlockState` is called, `false` otherwise.

4. In `actuate()`, replace the `acted` flag with a `changed` flag that is set to `true` only when at least one inner method returns `true`:

```java
boolean changed = false;
if (intent == IntentType.CONTINUE_MINING || miningPotential > 0.45) {
    changed |= regenerateOres(world, chunkPos, miningPotential, substrate, semantics);
}
if (intent == IntentType.RECOVER || successionPotential > 0.42) {
    changed |= regenerateCrops(world, chunkPos, successionPotential, substrate);
    changed |= regenerateSurfaceRecovery(world, chunkPos, semantics);
}
return changed ? ActuatorOutcome.SUCCESS
               : ActuatorOutcome.THRESHOLD_NOT_MET;
```

5. If you want to distinguish `NO_TARGET` from `NO_CHANGE` (recommended for telemetry accuracy):
   - Track whether any eligible target was found (e.g., crops found but at max age → `NO_CHANGE`; no crops found at all → `NO_TARGET`).
   - This requires the inner methods to return a richer signal (e.g., an enum or a small result object) rather than a simple boolean.
   - **Minimum viable fix:** Return `NO_TARGET` when a branch fires but no block changed. This is simpler and covers the most common case (pristine world, no eligible targets).

### 8.2 What Must NOT Change

- Do not change the threshold values (`0.45`, `0.42`, `0.58`, `0.55`, `0.72`).
- Do not change the branch conditions or the potential formulas.
- Do not change the ore type selection logic (`selectOreType`).
- Do not change the crop scan pattern (4×4, downward from `getTopY()` to y>48).
- Do not change the `ExperimentLogger.logActuatorDecision()` call inside `regenerateOres()` — it correctly logs only when an ore is actually placed.
- Do not change `SettlementStructureActuator` in the same change — it should be a separate commit with its own evidence.

### 8.3 How to Verify

1. **Unit test with Mockito** (preferred if Mockito is added as a test dependency):
   - Create a mock `ServerWorld` that returns `true` for `isChunkLoaded()`.
   - Set up `getBlockState()` to return specific blocks at target positions.
   - Track `setBlockState()` calls.
   - Assert the returned `ActuatorOutcome` matches the correct outcome for each of the six scenarios.

2. **If Mockito is not available:**
   - A formula-reproduction test (like `phase2a_resource_regen_scenarios.py`) can verify the branch logic and potential computations.
   - A source-level assertion can verify that the `changed` flag is only set when inner methods return `true`.
   - This is weaker than a mock-world test but confirms the logic change.

3. **Integration test (most faithful):**
   - Start a development server, create a pristine world, trigger the actuator, and inspect telemetry for the `observed_outcome` field.
   - This requires `./gradlew runServer` and manual or scripted world setup.

### 8.4 Expected Outcome After Fix

| Scenario | Current | After fix (minimum) | After fix (with NO_TARGET/NO_CHANGE distinction) |
|----------|---------|---------------------|--------------------------------------------------|
| 1: No eligible targets | SUCCESS | NO_TARGET | NO_TARGET |
| 2: Crops at max growth | SUCCESS | NO_TARGET | NO_CHANGE |
| 3: Eligible crop changes | SUCCESS | SUCCESS | SUCCESS |
| 4: Invalid ore target | SUCCESS | NO_TARGET | NO_TARGET |
| 5: Successful ore placement | SUCCESS | SUCCESS | SUCCESS |
| 6: Both branches, no change | SUCCESS | NO_TARGET | NO_TARGET |

### 8.5 Risk Assessment

| Risk | Level | Rationale |
|------|-------|-----------|
| Gameplay behavior change | **None** | Outcome is telemetry-only; no caller uses it for gameplay |
| Telemetry format change | Low | `observed_outcome` field will show `NO_TARGET`/`NO_CHANGE` instead of `SUCCESS` |
| Existing world compatibility | **None** | No change to block placement logic or thresholds |
| Test compatibility | Low | No existing actuator tests to break |

---

## 9. Resolution Status

| Claim | Evidence type | Status |
|-------|--------------|--------|
| `actuate()` returns `SUCCESS` when no block changes | Source inspection | **CONFIRMED** — `acted` flag set on branch entry, not on block change |
| Inner methods are `void` — no signal back to caller | Source inspection | **CONFIRMED** |
| `SUCCESS` contract means "changed world state" | Source inspection | **CONFIRMED** — both enum javadoc and interface javadoc |
| No caller uses outcome for gameplay | Source inspection | **CONFIRMED** — `actuateAll()` returns void, outcome only logged |
| `SettlementStructureActuator` has same pattern | Source inspection | **CONFIRMED** — identical `acted` flag logic |
| 4 of 6 scenarios produce contract violation | Formula reproduction | **CONFIRMED** — all assumptions stated explicitly |
| Runtime behavior matches source analysis | — | **EXPLICITLY UNRESOLVED** — no mock-world or integration test executed |
| Mockito would enable mock-world testing | Source inspection | **FEASIBLE** — but not present in current project config |

### What is confirmed

The production source code unambiguously sets `acted = true` on branch entry and returns `SUCCESS` when `acted` is true. The inner methods conditionally call `setBlockState` but return `void`, so the caller cannot know whether any block changed. This is a contract violation by source inspection.

### What is not confirmed

Whether the production JVM actually executes this path and returns `SUCCESS` at runtime has not been verified by executing the actuator with a real or mock `ServerWorld`. This would require either adding Mockito as a test dependency or running an integration test with a live Minecraft server. Both are outside the scope of this investigation, which does not modify production code, tests, or configuration.

### Missing evidence

The precise missing evidence is:
1. A mock-world test (Mockito-based) that calls `ResourceRegenerationActuator.actuate()` with a controlled `ServerWorld` and asserts the returned `ActuatorOutcome`.
2. An actual Minecraft integration test that runs the actuator in a live world and inspects the telemetry `observed_outcome` field.

Without this evidence, the runtime claim remains unresolved. The source-level conclusion is sufficient to proceed with the implementation specification in Section 8.

---

## 10. Deliverables

| File | Description | Evidence type |
|------|-------------|---------------|
| `demo/sinele/ha/PHASE2A_RESOURCE_REGEN_VERIFICATION.md` | This report | Source inspection + formula reproduction |
| `demo/sinele/ha/phase2a_resource_regen_scenarios.py` | Six-scenario formula reproduction harness | Formula reproduction |
| `demo/sinele/ha/phase2a_resource_regen_scenarios_output.txt` | Raw output from scenario harness | Formula reproduction |

**No production source code, tests, or configuration were modified.**
**Baseline commit `74d1f24` preserved.**
**All artifacts are in the isolated `demo/sinele/ha/` directory.**
