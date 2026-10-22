"""
PHASE 2A — Resource Regeneration Actuator: Branch-Logic Scenarios
===================================================================
Evidence type: FORMULA REPRODUCTION + SOURCE INSPECTION

This harness replicates the EXACT Java formulas from ResourceRegenerationActuator.actuate()
(lines 25-58) and the inner methods (regenerateCrops, regenerateSurfaceRecovery,
regenerateOres) to determine, for each of the six specified scenarios:

  - Exact computed potentials (miningPotential, successionPotential)
  - Which branches are entered
  - Whether setBlockState WOULD be called given the world state
  - What actuate() ACTUALLY returns (current production behavior)
  - What actuate() SHOULD return per the ActuatorOutcome enum contract

It DOES NOT execute the production Java actuator. A live ServerWorld cannot be
instantiated in this environment without the full Fabric/Minecraft toolchain.
The world-state assumptions (crops present/absent, block types at target positions)
are stated explicitly for each scenario — they are NOT inferred from substrate values.

SOURCE: src/main/java/com/utdmod/field/ResourceRegenerationActuator.java
SOURCE: src/main/java/com/utdmod/experiment/ActuatorOutcome.java
SOURCE: src/main/java/com/utdmod/field/FieldActuator.java
"""

import math
import json

# ── Replication of SubstrateState derived observables ──────────────────────────

READINESS_MEMORY_SCALE = 1.0


def clamp01(v):
    return max(0.0, min(1.0, v))


def compute_capacity(activation):
    """SubstrateState.computeDestinationCapacity(): 1.0 / (1.0 + 0.5 * max(0, A))"""
    return 1.0 / (1.0 + 0.5 * max(0.0, activation))


def compute_readiness(memory, field_depth):
    """SubstrateState.computeReadiness(): exp(-max(0, M) / max(0.5, scale))"""
    scale = max(0.5, READINESS_MEMORY_SCALE)
    return math.exp(-max(0.0, memory) / scale)


def compute_convergence(activation, prev_activation, memory, field_depth):
    """SubstrateState: convergence = 1.0 / (1.0 + |A - prevA| + M + D)"""
    return 1.0 / (1.0 + abs(activation - prev_activation) + memory + field_depth)


# ── Replication of SubstrateInterpretationEngine semantics ────────────────────

def compute_recovery_state(memory, capacity, convergence):
    """SubstrateInterpretationEngine: clamp01((1 - M/max(0.01, M+1)) * 0.40 + capacity * 0.35 + convergence * 0.25)"""
    memory_term = (1.0 - memory / max(0.01, memory + 1.0)) * 0.40
    return clamp01(memory_term + capacity * 0.35 + convergence * 0.25)


def compute_prosperity_potential(memory, capacity, field_depth, baseline):
    """SubstrateInterpretationEngine: clamp01(M*0.35 + capacity*0.30 + D*0.20 + B*0.15)"""
    return clamp01(memory * 0.35 + capacity * 0.30 + field_depth * 0.20 + baseline * 0.15)


# ── Core actuate() formulas ───────────────────────────────────────────────────

MINING_THRESHOLD = 0.45
SUCCESSION_THRESHOLD = 0.42


def compute_mining_potential(depth, memory, activation):
    """ResourceRegenerationActuator line 41: D*0.35 + M*0.40 + A*0.25"""
    return depth * 0.35 + memory * 0.40 + activation * 0.25


def compute_succession_potential(recovery_state, capacity, prosperity_potential):
    """ResourceRegenerationActuator line 42: recoveryState*0.45 + capacity*0.35 + prosperity*0.20"""
    return recovery_state * 0.45 + capacity * 0.35 + prosperity_potential * 0.20


# ── World-state-aware scenario runner ─────────────────────────────────────────

def run_scenario(label, A, M, D, B,
                 force_mining_intent=False, force_recover_intent=False,
                 world_has_crops=False, crop_ages=None,
                 world_has_coarse_dirt=False,
                 ore_target_block="STONE",
                 recovery_state_override=None):
    """
    Runs a complete scenario with explicit world-state assumptions.

    Parameters:
      world_has_crops: True if WHEAT/CARROTS/POTATOES exist in the 4x4 scan area
      crop_ages: list of ages for found crops (e.g. [7, 7] = all max growth)
      world_has_coarse_dirt: True if COARSE_DIRT exists at (startX+4, startZ+4) y=60-99
      ore_target_block: Block type at the computed ore target position ("STONE", "DEEPSLATE", "AIR", etc.)
      recovery_state_override: If set, uses this value instead of computing from substrate
    """
    capacity = compute_capacity(A)
    convergence = compute_convergence(A, A, M, D)  # static: delta=0
    recovery_state = recovery_state_override if recovery_state_override is not None \
        else compute_recovery_state(M, capacity, convergence)
    prosperity = compute_prosperity_potential(M, capacity, D, B)

    mining_pot = compute_mining_potential(D, M, A)
    succession_pot = compute_succession_potential(recovery_state, capacity, prosperity)

    mining_branch = force_mining_intent or (mining_pot > MINING_THRESHOLD)
    succession_branch = force_recover_intent or (succession_pot > SUCCESSION_THRESHOLD)

    # Track what WOULD happen inside the inner methods (source trace)
    setblock_calls = []

    # ── regenerateOres (lines 60-84) ──
    ore_placed = False
    if mining_branch:
        if ore_target_block in ("STONE", "DEEPSLATE"):
            ore_placed = True
            if mining_pot > 0.9:
                ore_type = "DIAMOND_ORE"
            elif mining_pot > 0.8:
                ore_type = "EMERALD_ORE"
            elif mining_pot > 0.7:
                ore_type = "GOLD_ORE"
            elif mining_pot > 0.6:
                ore_type = "LAPIS_ORE"
            else:
                ore_type = "IRON_ORE"
            setblock_calls.append(f"setBlockState(target_pos, {ore_type})")
        # else: no setBlockState — block is not STONE/DEEPSLATE

    # ── regenerateCrops (lines 74-96) ──
    crops_changed = False
    if succession_branch:
        if world_has_crops and crop_ages:
            growth_steps = min(5, max(1, int(math.floor(succession_pot * 10))))
            new_ages = []
            for age in crop_ages:
                new_age = min(7, age + growth_steps)
                if new_age > age:
                    crops_changed = True
                    setblock_calls.append(f"setBlockState(crop_pos, age={new_age})")
                new_ages.append(new_age)

    # ── regenerateSurfaceRecovery (lines 98-108) ──
    surface_recovery = False
    if succession_branch and recovery_state >= 0.5:
        if world_has_coarse_dirt:
            surface_recovery = True
            setblock_calls.append("setBlockState(coarse_dirt_pos, GRASS_BLOCK)")

    # Current production behavior
    acted = mining_branch or succession_branch
    actual_outcome = "SUCCESS" if acted else "THRESHOLD_NOT_MET"

    # Correct outcome per enum contract
    any_block_changed = ore_placed or crops_changed or surface_recovery
    if not acted:
        correct_outcome = "THRESHOLD_NOT_MET"
    elif any_block_changed:
        correct_outcome = "SUCCESS"
    elif world_has_crops and crop_ages and all(a >= 7 for a in crop_ages) and not ore_placed and not surface_recovery:
        correct_outcome = "NO_CHANGE"  # target exists but already in desired state
    elif not world_has_crops and not world_has_coarse_dirt and ore_target_block not in ("STONE", "DEEPSLATE") and not ore_placed:
        correct_outcome = "NO_TARGET"  # no eligible target found
    else:
        correct_outcome = "NO_TARGET"  # default: branch entered but no eligible target

    return {
        "label": label,
        "substrate_inputs": {"A": A, "M": M, "D": D, "B": B},
        "derived": {
            "capacity": round(capacity, 6),
            "convergence": round(convergence, 6),
            "recoveryState": round(recovery_state, 6),
            "prosperityPotential": round(prosperity, 6),
        },
        "potentials": {
            "miningPotential": round(mining_pot, 6),
            "successionPotential": round(succession_pot, 6),
        },
        "world_state_assumptions": {
            "has_crops": world_has_crops,
            "crop_ages": crop_ages,
            "has_coarse_dirt": world_has_coarse_dirt,
            "ore_target_block": ore_target_block,
        },
        "branch_decisions": {
            "mining_branch_entered": mining_branch,
            "succession_branch_entered": succession_branch,
        },
        "setBlockState_calls": setblock_calls,
        "world_changed": any_block_changed,
        "actual_outcome": actual_outcome,
        "correct_outcome_per_contract": correct_outcome,
        "contract_violation": actual_outcome != correct_outcome,
    }


# ── The Six Specified Scenarios ───────────────────────────────────────────────

scenarios = []

# Scenario 1: Succession threshold met, but scanned world contains no eligible crops
# or surface-recovery target.
# Substrate: pristine (A=M=D=B=0) → successionPotential = 0.86 > 0.42
# World: no crops, no coarse dirt
scenarios.append(run_scenario(
    "Scenario 1: Succession threshold met, no eligible targets",
    A=0.0, M=0.0, D=0.0, B=0.0,
    world_has_crops=False, world_has_coarse_dirt=False,
    ore_target_block="AIR",
))

# Scenario 2: Crops found, but all already at maximum growth (age 7).
# Substrate: pristine → successionPotential = 0.86 > 0.42
# World: WHEAT at age 7, no coarse dirt
scenarios.append(run_scenario(
    "Scenario 2: Crops found at maximum growth (age 7)",
    A=0.0, M=0.0, D=0.0, B=0.0,
    world_has_crops=True, crop_ages=[7, 7],
    world_has_coarse_dirt=False,
    ore_target_block="AIR",
))

# Scenario 3: At least one crop eligible and actually changes.
# Substrate: moderate activity → successionPotential > 0.42
# World: WHEAT at age 3
scenarios.append(run_scenario(
    "Scenario 3: Eligible crop changes (age 3 → advanced)",
    A=0.2, M=0.1, D=0.05, B=0.01,
    world_has_crops=True, crop_ages=[3],
    world_has_coarse_dirt=False,
    ore_target_block="AIR",
))

# Scenario 4: Mining branch entered, but ore target is not a valid replaceable block.
# Substrate: high mining potential → miningPotential > 0.45
# World: target position contains AIR (not STONE/DEEPSLATE)
scenarios.append(run_scenario(
    "Scenario 4: Mining branch, invalid ore target (AIR at target pos)",
    A=0.5, M=0.5, D=0.5, B=0.0,
    world_has_crops=False, world_has_coarse_dirt=False,
    ore_target_block="AIR",
))

# Scenario 5: Mining branch successfully places an ore block.
# Substrate: high mining potential → miningPotential = 0.50 > 0.45
# World: target position contains STONE
scenarios.append(run_scenario(
    "Scenario 5: Mining branch, successful ore placement (STONE at target)",
    A=0.5, M=0.5, D=0.5, B=0.0,
    world_has_crops=False, world_has_coarse_dirt=False,
    ore_target_block="STONE",
))

# Scenario 6: Both branches run, but neither makes a change.
# Substrate: both miningPotential > 0.45 and successionPotential > 0.42
# World: no crops, no coarse dirt, ore target is DIRT (not STONE/DEEPSLATE)
scenarios.append(run_scenario(
    "Scenario 6: Both branches, no change (no crops, no coarse dirt, DIRT at ore target)",
    A=0.5, M=0.5, D=0.5, B=0.0,
    world_has_crops=False, world_has_coarse_dirt=False,
    ore_target_block="DIRT",
))


# ── Output ────────────────────────────────────────────────────────────────────

print("=" * 80)
print("PHASE 2A — RESOURCE REGENERATION ACTUATOR: BRANCH-LOGIC SCENARIOS")
print("Evidence type: FORMULA REPRODUCTION + SOURCE INSPECTION")
print("Limitation: Does NOT execute the production Java actuator.")
print("           World-state assumptions are stated explicitly per scenario.")
print("=" * 80)

for s in scenarios:
    print(f"\n{'─' * 80}")
    print(f"  {s['label']}")
    print(f"{'─' * 80}")
    print(f"  Substrate: A={s['substrate_inputs']['A']}, M={s['substrate_inputs']['M']}, "
          f"D={s['substrate_inputs']['D']}, B={s['substrate_inputs']['B']}")
    print(f"  Derived:   capacity={s['derived']['capacity']}, convergence={s['derived']['convergence']}")
    print(f"             recoveryState={s['derived']['recoveryState']}, "
          f"prosperityPotential={s['derived']['prosperityPotential']}")
    print(f"  Potentials: mining={s['potentials']['miningPotential']} (thr > {MINING_THRESHOLD}), "
          f"succession={s['potentials']['successionPotential']} (thr > {SUCCESSION_THRESHOLD})")
    print(f"  World assumptions:")
    for k, v in s['world_state_assumptions'].items():
        print(f"    {k}: {v}")
    print(f"  Branches: mining={s['branch_decisions']['mining_branch_entered']}, "
          f"succession={s['branch_decisions']['succession_branch_entered']}")
    print(f"  setBlockState calls: {s['setBlockState_calls'] if s['setBlockState_calls'] else 'NONE'}")
    print(f"  World changed: {s['world_changed']}")
    print(f"  ACTUAL outcome (production):  {s['actual_outcome']}")
    print(f"  CORRECT outcome (per contract): {s['correct_outcome_per_contract']}")
    if s['contract_violation']:
        print(f"  *** CONTRACT VIOLATION: returns {s['actual_outcome']} but should return {s['correct_outcome_per_contract']}")
    else:
        print(f"  ✓ No violation")

# JSON output for machine-readable consumption
print("\n\n" + "=" * 80)
print("JSON OUTPUT")
print("=" * 80)
print(json.dumps(scenarios, indent=2))
