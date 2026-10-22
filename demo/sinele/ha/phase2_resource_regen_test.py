"""
FORMULA-SEAM TEST — ResourceRegenerationActuator
=================================================
Investigation B, Phase 2: Controlled formula-seam tests for ResourceRegenerationActuator.

This harness faithfully replicates the EXACT Java formulas used in actuate() for:
  - miningPotential
  - successionPotential
  - the branch conditions and return values

It DOES NOT replicate what happens inside regenerateCrops() or regenerateOres() —
those require a live Minecraft ServerWorld and cannot be run in this environment.

For each test case, we report:
  - Exact computed potentials
  - Which branches are entered (MINING, CROP_SUCCESSION, neither)
  - What actuate() returns (SUCCESS or THRESHOLD_NOT_MET)
  - Whether a world mutation WOULD occur if the sub-methods run vs an empty world
    (determined by static source trace, NOT live execution)

All Java source quoted inline for traceability.

SOURCE: src/main/java/com/utdmod/field/ResourceRegenerationActuator.java
"""

import math

# ── Replication of SubstrateState derived observables ──────────────────────────
# Source: SubstrateState.java (lines 97-99)
#   readiness = computeReadiness(memory, fieldDepth, parameters)
#   capacity  = computeDestinationCapacity(activation)
#
# Source: SubstrateState.java line ~133
#   public static double computeReadiness(double memoryValue, double fieldDepthValue,
#                                         ParameterSet parameters) {
#       double memoryScale = Math.max(0.5, parameters.readinessMemoryScale);
#       return Math.exp(-Math.max(0.0, memoryValue) / memoryScale);
#   }
#
# Source: SubstrateState.java line ~139
#   public static double computeDestinationCapacity(double activationValue) {
#       return 1.0 / (1.0 + 0.5 * Math.max(0.0, activationValue));
#   }
#
# Default readinessMemoryScale: from ParameterSet.runtime(); empirical default is 1.0
READINESS_MEMORY_SCALE = 1.0  # Math.max(0.5, parameters.readinessMemoryScale), default


def compute_readiness(memory: float, field_depth: float) -> float:
    """
    Java source (SubstrateState.java):
        double memoryScale = Math.max(0.5, parameters.readinessMemoryScale);
        return Math.exp(-Math.max(0.0, memoryValue) / memoryScale);
    Note: fieldDepth is passed but NOT used in this formula — this is noted in the analysis.
    """
    memory_scale = max(0.5, READINESS_MEMORY_SCALE)
    return math.exp(-max(0.0, memory) / memory_scale)


def compute_capacity(activation: float) -> float:
    """
    Java source (SubstrateState.java):
        return 1.0 / (1.0 + 0.5 * Math.max(0.0, activationValue));
    """
    return 1.0 / (1.0 + 0.5 * max(0.0, activation))


# ── Replication of SubstrateInterpretationEngine semantics ────────────────────
# Source: SubstrateInterpretationEngine.java lines 42-44
#   double recoveryState = clamp01((1.0 - memory / Math.max(0.01, memory + 1.0)) * 0.40
#       + capacity * 0.35 + convergence * 0.25);
#   double prosperityPotential = clamp01(memory * 0.35 + capacity * 0.30
#       + fieldDepth * 0.20 + baseline * 0.15);

def clamp01(v: float) -> float:
    return max(0.0, min(1.0, v))


def compute_recovery_state(memory: float, capacity: float, convergence: float) -> float:
    """
    Java source (SubstrateInterpretationEngine.java):
        double recoveryState = clamp01((1.0 - memory / Math.max(0.01, memory + 1.0)) * 0.40
            + capacity * 0.35 + convergence * 0.25);
    """
    memory_term = (1.0 - memory / max(0.01, memory + 1.0)) * 0.40
    return clamp01(memory_term + capacity * 0.35 + convergence * 0.25)


def compute_prosperity_potential(memory: float, capacity: float, field_depth: float,
                                  baseline: float) -> float:
    """
    Java source (SubstrateInterpretationEngine.java):
        double prosperityPotential = clamp01(memory * 0.35 + capacity * 0.30
            + fieldDepth * 0.20 + baseline * 0.15);
    """
    return clamp01(memory * 0.35 + capacity * 0.30 + field_depth * 0.20 + baseline * 0.15)


def compute_convergence(activation: float, prev_activation: float,
                         memory: float, field_depth: float) -> float:
    """
    Java source (SubstrateState.java):
        convergence = 1.0 / (1.0 + Math.abs(activation - prevA) + memory + fieldDepth);
    For static states (no integration step in progress), activation == prevA so delta = 0.
    """
    return 1.0 / (1.0 + abs(activation - prev_activation) + memory + field_depth)


# ── Core actuate() formulas ───────────────────────────────────────────────────
# Source: ResourceRegenerationActuator.java lines 38-57
#
#   double depth = substrate.getFieldDepth();
#   double memory = substrate.getMemory();
#   double activation = substrate.getActivation();
#   double capacity = substrate.getCapacity();
#
#   double miningPotential = depth * 0.35 + memory * 0.40 + activation * 0.25;
#   double successionPotential = semantics.recoveryState * 0.45
#                                + capacity * 0.35
#                                + semantics.prosperityPotential * 0.20;
#
#   boolean acted = false;
#   if (intent == IntentType.CONTINUE_MINING || miningPotential > 0.45) {
#     regenerateOres(world, chunkPos, miningPotential, substrate, semantics);
#     acted = true;
#   }
#   if (intent == IntentType.RECOVER || successionPotential > 0.42) {
#     regenerateCrops(world, chunkPos, successionPotential, substrate);
#     regenerateSurfaceRecovery(world, chunkPos, semantics);
#     acted = true;
#   }
#   return acted ? ActuatorOutcome.SUCCESS : ActuatorOutcome.THRESHOLD_NOT_MET;

MINING_THRESHOLD    = 0.45   # miningPotential > 0.45
SUCCESSION_THRESHOLD = 0.42  # successionPotential > 0.42


def compute_mining_potential(depth: float, memory: float, activation: float) -> float:
    """
    Java source (ResourceRegenerationActuator.java line 41):
        double miningPotential = depth * 0.35 + memory * 0.40 + activation * 0.25;
    """
    return depth * 0.35 + memory * 0.40 + activation * 0.25


def compute_succession_potential(recovery_state: float, capacity: float,
                                  prosperity_potential: float) -> float:
    """
    Java source (ResourceRegenerationActuator.java line 42):
        double successionPotential = semantics.recoveryState * 0.45
                                   + capacity * 0.35
                                   + semantics.prosperityPotential * 0.20;
    """
    return recovery_state * 0.45 + capacity * 0.35 + prosperity_potential * 0.20


def run_actuate(depth: float, memory: float, activation: float, baseline: float,
                force_mining_intent: bool = False, force_recover_intent: bool = False,
                label: str = "") -> dict:
    """
    Replicates actuate() branch logic WITHOUT calling regenerateCrops/regenerateOres.
    intent=None means no special intent (uses threshold-only path).
    """
    capacity    = compute_capacity(activation)
    readiness   = compute_readiness(memory, depth)
    convergence = compute_convergence(activation, activation, memory, depth)  # static: delta=0

    recovery_state       = compute_recovery_state(memory, capacity, convergence)
    prosperity_potential = compute_prosperity_potential(memory, capacity, depth, baseline)

    mining_potential    = compute_mining_potential(depth, memory, activation)
    succession_potential = compute_succession_potential(recovery_state, capacity, prosperity_potential)

    mining_branch     = force_mining_intent or (mining_potential > MINING_THRESHOLD)
    succession_branch = force_recover_intent or (succession_potential > SUCCESSION_THRESHOLD)
    acted = mining_branch or succession_branch
    outcome = "SUCCESS" if acted else "THRESHOLD_NOT_MET"

    return {
        "label":                label,
        "inputs": {
            "A (activation)":   activation,
            "M (memory)":       memory,
            "D (field_depth)":  depth,
            "B (baseline)":     baseline,
        },
        "derived": {
            "capacity":              round(capacity, 6),
            "readiness":             round(readiness, 6),
            "convergence":           round(convergence, 6),
            "recoveryState":         round(recovery_state, 6),
            "prosperityPotential":   round(prosperity_potential, 6),
        },
        "potentials": {
            "miningPotential":       round(mining_potential, 6),
            "successionPotential":   round(succession_potential, 6),
        },
        "thresholds": {
            "MINING_THRESHOLD":     MINING_THRESHOLD,
            "SUCCESSION_THRESHOLD": SUCCESSION_THRESHOLD,
        },
        "branches": {
            "MINING_BRANCH_ENTERED":     mining_branch,
            "SUCCESSION_BRANCH_ENTERED": succession_branch,
        },
        "outcome": outcome,
        "world_mutation_note": (
            "regenerateOres() CALLED (block change conditional on STONE/DEEPSLATE at target pos)"
            if mining_branch else "regenerateOres() NOT called"
        ) + " | " + (
            "regenerateCrops() + regenerateSurfaceRecovery() CALLED (block change conditional on crop/coarse-dirt found)"
            if succession_branch else "regenerateCrops() NOT called"
        ),
    }


def print_result(r: dict):
    print(f"\n{'='*72}")
    print(f"  CASE: {r['label']}")
    print(f"{'='*72}")
    print(f"  Inputs:  A={r['inputs']['A (activation)']}, M={r['inputs']['M (memory)']}, "
          f"D={r['inputs']['D (field_depth)']}, B={r['inputs']['B (baseline)']}")
    print(f"  Derived: capacity={r['derived']['capacity']}, readiness={r['derived']['readiness']}, "
          f"convergence={r['derived']['convergence']}")
    print(f"           recoveryState={r['derived']['recoveryState']}, "
          f"prosperityPotential={r['derived']['prosperityPotential']}")
    print(f"  Potentials:")
    print(f"    miningPotential    = {r['potentials']['miningPotential']} "
          f"  (threshold > {r['thresholds']['MINING_THRESHOLD']}: "
          f"{'PASS' if r['branches']['MINING_BRANCH_ENTERED'] else 'FAIL'})")
    print(f"    successionPotential= {r['potentials']['successionPotential']} "
          f"  (threshold > {r['thresholds']['SUCCESSION_THRESHOLD']}: "
          f"{'PASS' if r['branches']['SUCCESSION_BRANCH_ENTERED'] else 'FAIL'})")
    print(f"  Branches entered:")
    print(f"    MINING_BRANCH     : {r['branches']['MINING_BRANCH_ENTERED']}")
    print(f"    SUCCESSION_BRANCH : {r['branches']['SUCCESSION_BRANCH_ENTERED']}")
    print(f"  actuate() returns  : {r['outcome']}")
    print(f"  World mutation note: {r['world_mutation_note']}")


# ── Test Cases ────────────────────────────────────────────────────────────────

print("=" * 72)
print("FORMULA-SEAM TEST — ResourceRegenerationActuator.actuate()")
print("Evidence type: FORMULA REPLICATION from Java source")
print("Limitation: Does NOT prove world mutations — only proves branch entry")
print("=" * 72)

# Case 1: Pristine substrate (A=M=D=B=0)
case1 = run_actuate(depth=0.0, memory=0.0, activation=0.0, baseline=0.0,
                    label="Case 1: Pristine substrate (A=M=D=B=0)")

# Case 2: Typical Run 4 mean state (A=0.1295, M=0.430, D=0.490, B~0)
# Readiness is computed from memory/fieldDepth
case2 = run_actuate(depth=0.490, memory=0.430, activation=0.1295, baseline=0.0,
                    label="Case 2: Typical Run 4 mean state (A=0.1295, M=0.430, D=0.490)")

# Case 3: State that should qualify for ore regen (miningPotential > 0.45)
# We need D*0.35 + M*0.40 + A*0.25 > 0.45
# Try D=0.5, M=0.5, A=0.5: 0.175 + 0.200 + 0.125 = 0.500 > 0.45
case3 = run_actuate(depth=0.5, memory=0.5, activation=0.5, baseline=0.0,
                    label="Case 3: Ore regen candidate (D=0.5, M=0.5, A=0.5) → miningPotential=0.500")

# Case 4: State just at/below succession threshold
# We need successionPotential <= 0.42
# recoveryState * 0.45 + capacity * 0.35 + prosperityPotential * 0.20
# With A=0.0, M=0.0, D=0.0, B=0.0:
#   capacity=1.0, convergence=1/(1+0+0+0)=1.0
#   recoveryState = clamp01((1.0 - 0)*0.40 + 1.0*0.35 + 1.0*0.25) = 1.0
#   prosperityPotential = 0.30  (only capacity=1.0 contributes 0.30)
#   successionPotential = 1.0*0.45 + 1.0*0.35 + 0.30*0.20 = 0.45 + 0.35 + 0.06 = 0.86
# Pristine is ABOVE threshold. Try to get BELOW 0.42:
# High memory suppresses recoveryState; high activation reduces capacity
# A=2.0, M=3.0, D=0.0, B=0.0:
#   capacity=1/(1+1.0)=0.5
#   convergence=1/(1+0+3.0+0.0)=0.25
#   mem_term=(1 - 3/(3+1))*0.40=(1-0.75)*0.40=0.10
#   recoveryState=0.10+0.5*0.35+0.25*0.25=0.10+0.175+0.0625=0.3375
#   prosperityPotential=3.0*0.35+0.5*0.30+0.0*0.20+0.0*0.15=1.05+0.15=1.20→clamp=1.0
#   successionPotential=0.3375*0.45+0.5*0.35+1.0*0.20=0.1519+0.175+0.20=0.5269 still > 0.42
# Try A=5.0, M=10.0, D=0.0, B=0.0:
#   capacity=1/(1+2.5)=0.2857
#   convergence=1/(1+0+10.0+0)=0.0909
#   mem_term=(1-10/11)*0.40=0.0909*0.40=0.0364
#   recoveryState=0.0364+0.2857*0.35+0.0909*0.25=0.0364+0.100+0.0227=0.159
#   prosperityPotential=10*0.35+0.2857*0.30+0*0.20+0*0.15=3.5+0.0857=3.5857→clamp=1.0
#   successionPotential=0.159*0.45+0.2857*0.35+1.0*0.20=0.0716+0.100+0.20=0.372 < 0.42!
case4 = run_actuate(depth=0.0, memory=10.0, activation=5.0, baseline=0.0,
                    label="Case 4: Just below succession threshold (A=5.0, M=10.0, D=0, B=0)")

# Case 5: recoveryState=0, capacity=1 — tests which term drives succession
# recoveryState≈0 requires: memory_term≈0, capacity≈0, convergence≈0
# but we want capacity=1 here. That's contradictory in the convergence term.
# We can get recoveryState≈0 by making capacity=0 and convergence=0, but we want capacity=1.
# Best approximation: high memory (memory_term→0), capacity=1 (activation=0),
# convergence low requires high memory+depth.
# A=0, M=100, D=0: capacity=1.0, convergence=1/(1+0+100+0)=0.0099
#   mem_term=(1-100/101)*0.40=0.0099*0.40=0.00396
#   recoveryState=0.00396+1.0*0.35+0.0099*0.25=0.00396+0.35+0.00248=0.356
# Can't get recoveryState=0 exactly with capacity=1, since capacity=1 contributes +0.35.
# We set A=0, M=1000, D=0 to get recoveryState near 0.35 (capacity term dominates)
case5 = run_actuate(depth=0.0, memory=1000.0, activation=0.0, baseline=0.0,
                    label="Case 5: recoveryState≈0, capacity=1.0 (A=0, M=1000, D=0) — capacity dominates succession")

# Case 6: recoveryState=1, capacity=0 — boundary test
# capacity=0 requires activation→∞ (1/(1+0.5*∞)→0); we use activation=1000
# A=1000, M=0, D=0:
#   capacity=1/(1+500)≈0.002
#   convergence=1/(1+0+0+0)=1.0 (memory=depth=0)
#   mem_term=(1-0)*0.40=0.40
#   recoveryState=0.40+0.002*0.35+1.0*0.25=0.40+0.0007+0.25=0.6507
#   prosperityPotential=0+0.002*0.30+0+0=0.0006
#   successionPotential=0.6507*0.45+0.002*0.35+0.0006*0.20=0.2928+0.0007+0.0001=0.294
# Note: "recoveryState=1, capacity=0" as stated is theoretically impossible via the formula
# because if capacity≈0 then the capacity term in recoveryState≈0 too, and only memory_term
# and convergence remain. This is documented below.
case6 = run_actuate(depth=0.0, memory=0.0, activation=1000.0, baseline=0.0,
                    label="Case 6: capacity≈0 (activation=1000, impossible perfect boundary) — recoveryState via non-capacity terms")

for r in [case1, case2, case3, case4, case5, case6]:
    print_result(r)

# ── World Mutation Analysis (from source trace) ───────────────────────────────
print("\n\n" + "=" * 72)
print("WORLD MUTATION ANALYSIS — FROM SOURCE TRACE (not live test)")
print("Evidence type: STATIC SOURCE INSPECTION")
print("=" * 72)

print("""
SOURCE: ResourceRegenerationActuator.java regenerateCrops() lines 96-128

  private void regenerateCrops(ServerWorld world, ChunkPos chunkPos,
                                double potential, SubstrateState substrate) {
    int baseX = chunkPos.getStartX();
    int baseZ = chunkPos.getStartZ();
    int growthSteps = Math.min(5, Math.max(1, (int) Math.floor(potential * 10)));
    for (int dx = 0; dx < 4; dx++) {
      for (int dz = 0; dz < 4; dz++) {
        BlockPos surface = new BlockPos(baseX + dx, world.getTopY(), baseZ + dz);
        for (int y = surface.getY(); y > 48; y--) {
          BlockPos below = new BlockPos(surface.getX(), y, surface.getZ());
          Block block = world.getBlockState(below).getBlock();
          if (block == Blocks.WHEAT || block == Blocks.CARROTS || block == Blocks.POTATOES) {
            BlockState state = world.getBlockState(below);
            Property<Integer> ageProperty = findAgeProperty(state);
            if (ageProperty == null || !state.contains(ageProperty)) {
              break;
            }
            for (int step = 0; step < growthSteps; step++) {
              int age = state.get(ageProperty);
              if (age < 7) {
                state = state.with(ageProperty, Math.min(7, age + 1));
                world.setBlockState(below, state);
              }
            }
            break;
          }
        }
      }
    }
  }

SCAN PATTERN:
  - Scans a 4×4 sub-grid of the chunk: dx in [0,3], dz in [0,3]
  - For each (dx, dz): starts at world.getTopY() (world build height, ~320 in 1.20.4)
    and scans downward to y > 48 (stops at sea level +2)
  - First crop block found → attempt growth, then `break` (move to next column)
  - If no crop found in column: loop terminates silently, no return value, no flag

GUARD CONDITIONS (Age < 7 Check):
  - Line 108: `if (age < 7)` — YES, guard exists
  - If age == 7 (max): the `if (age < 7)` body is SKIPPED, state unchanged
  - The inner `for (step)` loop still runs growthSteps iterations but does nothing
  - world.setBlockState() is NOT called for a fully-grown crop

RETURN VALUE:
  - regenerateCrops() is void — returns nothing
  - The caller (actuate()) does NOT check any return from regenerateCrops()
  - `acted = true` is set BEFORE calling regenerateCrops(), not after
  - Therefore: even if regenerateCrops() makes zero world changes, actuate()
    still returns SUCCESS

Q1: What happens if regenerateCrops() is called on a chunk with no crops?
  → All 4×4 column scans find no WHEAT/CARROTS/POTATOES block
  → Each inner y-loop terminates at y > 48 without entering the crop branch
  → Method returns (void) silently
  → Caller still returns SUCCESS (acted was set to true before the call)

Q2: What happens if the for-loop finds a crop already at age 7?
  → ageProperty is found (not null), state.contains(ageProperty) is true
  → growthSteps iterations run but `if (age < 7)` is always false
  → world.setBlockState() is never called
  → Method returns (void) silently
  → Caller still returns SUCCESS

Q3: Can actuate() return SUCCESS with NO world change?
  → YES. Under these conditions:
     a) SUCCESSION branch entered but chunk has no crops AND no COARSE_DIRT y=60-99
     b) SUCCESSION branch entered but all found crops are at age 7 AND no COARSE_DIRT
     c) MINING branch entered but target pos contains neither STONE nor DEEPSLATE
     d) Any combination of (a-c) across both branches

Q4: Can actuate() return SUCCESS WITH a world change?
  → YES. Under these conditions:
     a) MINING branch: target position contains STONE or DEEPSLATE → ore placed
     b) SUCCESSION branch: a crop with age < 7 exists in the 4×4 scan area
     c) SUCCESSION branch: COARSE_DIRT exists at y=60-99 at (startX+4, startZ+4)

Q5: What do callers do with SUCCESS return value?
  → FieldActuatorRegistry.actuateAll() (lines 40-55) receives the outcome
  → Outcome is passed to ExperimentLogger.logActuatorInvocation() as outcome.name()
  → No conditional logic on SUCCESS vs THRESHOLD_NOT_MET in the registry loop
  → SUCCESS is logged as a string label — nothing breaks if returned spuriously
  → However: telemetry consumers interpreting SUCCESS as "world changed" will be misled

SOURCE: FieldActuatorRegistry.java lines 38-56
  outcome = actuator.actuate(world, interpretation);
  ...
  ExperimentLogger.logActuatorInvocation(
      TensionTraceClock.getServerTick(), world, interpretation.chunkPos,
      actuator.getId(), interpretation.substrate,
      outcome.name(),   // <-- logged as string
      ..., outcome);    // <-- passed to logger but not branched on
""")

print("\n" + "=" * 72)
print("CONTRACT MISMATCH SUMMARY")
print("=" * 72)
print("""
FieldActuator interface javadoc (FieldActuator.java):
  SUCCESS — "actuator executed and changed world state"

ActuatorOutcome enum javadoc (ActuatorOutcome.java):
  SUCCESS — "Actuator executed and made changes."
  NO_CHANGE — "Actuator executed but made no changes (already in desired state)."
  NO_TARGET — "Actuator could not find a target (e.g. no crops to grow)."

ResourceRegenerationActuator.actuate() actual behavior:
  Returns SUCCESS when: (miningPotential > 0.45) OR (successionPotential > 0.42)
  This is a THRESHOLD CHECK, not a world-state check.
  World mutations are CONDITIONAL inside regenerateCrops/regenerateOres:
    - regenerateCrops: only mutates if crops exist at age < 7 in the 4×4 scan
    - regenerateOres: only mutates if STONE/DEEPSLATE at the computed target position

  CORRECT return when crops exist but all at max age → should return NO_CHANGE or NO_TARGET
  CORRECT return when chunk has no crops → should return NO_TARGET
  CORRECT return when ore target has no STONE/DEEPSLATE → should return NO_CHANGE

ENUM VALUES THAT SHOULD BE USED BUT AREN'T:
  NO_CHANGE  — intended for "ran but world was already in desired state"
  NO_TARGET  — intended for "no crops to grow"
""")
