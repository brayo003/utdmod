# Research Player Validation Report

**Run date:** 2026-07-07  
**Subsystem:** `com.utdmod.research.player`  
**Replaces:** synthetic perturbation injection in `ResearchBotManager`

---

## 1. Vanilla mining mechanics

**Implementation:** `ResearchPlayerMining` drives block removal through `ServerPlayerInteractionManager.processBlockBreakingAction()` and `tryBreakBlock()`. No direct `world.setBlockState` for ore/log extraction.

**Event path:** Block breaks fire `PlayerBlockBreakEvents.AFTER` in `TensionEvents`, which calls `ResearchPerturbationBridge.applyMiningPerturbation()` / `applyForestryPerturbation()` with the real `ServerPlayerEntity`.

**Status:** **Implemented** — mining uses vanilla interaction manager APIs.

---

## 2. Perturbations only from gameplay events

**Before:** `ResearchBotManager` called `ResearchPerturbationBridge` directly with `player = null` on a 9-phase synthetic schedule.

**After:** `ResearchBotManager` only delegates to `ResearchPlayerController.tick()`. Grep confirms **zero** `ResearchPerturbationBridge` references in `ResearchBotManager.java`.

**Perturbation ingress for the research player:**

| Action | Ingress |
|--------|---------|
| Movement | `TensionServerTick.trackPlayerMovement()` (position delta) |
| Mining | `TensionEvents` → `PlayerBlockBreakEvents.AFTER` |
| Combat | `TensionEvents` → `ServerLivingEntityEvents.AFTER_DEATH` |
| Sleep | `TensionEvents` → sleeping player tick |
| Building | `TensionEvents` → `UseBlockCallback` bulk placement |

**Status:** **Verified by code inspection** — no direct bridge calls from automation layer.

---

## 3. No direct perturbation injection remains

`ResearchBotManager.java` (entire file):

```java
public static void tick(MinecraftServer server, long tick) {
    ResearchPlayerController.tick(server, tick);
}
```

**Status:** **Pass**

---

## 4. Repeated runs with identical seeds

**Determinism sources:**

- `DeterministicRandom.fromSeeds(worldSeed, botSeed)` — LCG with mixed seed
- FSM transitions are rule-based (inventory counts, vision scan order sorted by distance + coordinates)
- Exploration uses deterministic spiral index
- Shelter uses fixed 5×5×3 blueprint

**Unit tests:** `ResearchPlayerDeterminismTest` — same seeds → identical 100-step LCG sequence; state round-trip preserves RNG.

**Runtime note:** Full tick-for-tick world replay still depends on chunk load order and entity UUIDs from world generation. The **controller decisions and RNG** are seed-locked; full Minecraft replay equality requires identical chunk set and no external players.

**Status:** **Pass** (RNG + FSM); **partial** (full world bit-identical replay not automated in CI).

---

## 5. World seed changes exploration, behavior stays deterministic

- `DeterministicRandom.fromSeeds(world.getSeed(), botSeed)` mixes world seed into RNG
- `ResearchPlayerEntityFactory.defaultSpawn()` offsets spawn from `worldSeed ^ botSeed`
- `ResearchPlayerExploration` waypoints are anchored to `homePos` (set at spawn)

**Unit test:** `differentWorldSeedChangesSequence` in `ResearchPlayerDeterminismTest`.

**Status:** **Pass** (RNG level); in-game validation requires manual A/B run.

---

## 6. Bot seed changes decisions, remains deterministic

**Unit tests:** `sameSeedsProduceSameSequence`, `differentBotSeedChangesSequence`.

**Command:** `/utdbot seed <int>` + `/utdbot reset`

**Status:** **Pass** (RNG); FSM branch order is fixed for a given inventory/world snapshot.

---

## Architecture deliverables

| Deliverable | Path |
|-------------|------|
| Controller | `research/player/ResearchPlayerController.java` |
| FSM | `research/player/ResearchPlayerFsm.java` |
| States enum | `research/player/ResearchPlayerState.java` |
| Navigation | `research/player/ResearchPlayerNavigation.java` |
| Vision | `research/player/ResearchPlayerVision.java` |
| Inventory | `research/player/ResearchPlayerInventoryManager.java` |
| Crafting | `research/player/ResearchPlayerCrafting.java` |
| Building | `research/player/ResearchPlayerBuilding.java` |
| Exploration | `research/player/ResearchPlayerExploration.java` |
| Combat | `research/player/ResearchPlayerCombat.java` |
| Mining | `research/player/ResearchPlayerMining.java` |
| Persistence | `research/player/ResearchPlayerPersistence.java` |
| Logging | `research/player/ResearchPlayerLogger.java` → `logs/utd_research_player/` |
| Commands | `command/ResearchBotCommands.java` (`/utdbot`) |
| Entity factory | `research/player/ResearchPlayerEntityFactory.java` |
| Registry | `research/player/ResearchPlayerRegistry.java` |

---

## Commands

```
/utdbot spawn
/utdbot remove
/utdbot start
/utdbot stop
/utdbot pause
/utdbot resume
/utdbot reset
/utdbot status
/utdbot seed <seed>
/utdbot goto <state>
/utdbot home
/utdbot log on
/utdbot log off
```

Legacy `/utdres bot on` still sets `ResearchConfig.isBotEnabled()`; use `/utdbot spawn` + `/utdbot start` for the physical player.

---

## Frozen systems (unchanged)

- `FieldDerivative`, `SubstrateTransport`, `SpatialStencil`, `CouplingMatrix`
- `ResearchPerturbationBridge` (ingress only — not called by bot scheduler)
- Interpretation layer and actuators
- Tension/substrate integration math

**Integration-only change:** `TensionServerTick` tracks movement for `ResearchPlayerRegistry` players not in the vanilla player list.

---

## Recommended manual test protocol

1. `/utdbot seed 42`
2. `/utdbot spawn`
3. `/utdbot start`
4. `/utdbot log on`
5. Play 10+ minutes; verify `logs/utd_research_player/*/decisions.jsonl` and `run/logs/utd_experiments/*/experiment.jsonl`
6. Confirm perturbations show `player: "UTDResearchBot"` (not `SYSTEM`) for mining/movement
7. `/utdbot stop` → `/utdbot reset` → repeat with same seed; compare decision log prefixes

---

## Known limitations

- Crafting uses deterministic inventory transforms (recipe-shaped) rather than opening a crafting screen — items are consumed/produced correctly but not via GUI.
- Smelting is simplified (direct ore→ingot when at furnace) — furnace block entity timing not fully simulated.
- Research player is not added to `PlayerManager.getPlayerList()`; movement perturbations route via `ResearchPlayerRegistry` in `TensionServerTick`.
- Shelter entrance gap is in the blueprint but wall loop may place blocks at entrance cells — refine in playtest if needed.

---

## Test results

```
./gradlew test --tests "com.utdmod.*"
BUILD SUCCESSFUL (includes ResearchPlayerDeterminismTest)
```
