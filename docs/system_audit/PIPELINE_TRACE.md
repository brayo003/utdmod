# Runtime Pipeline Trace

Complete causal chains from player/world events through tension, storms, sync, and feedback. Format: **trigger → class.method → purpose**.

---

## A. Player mining

```
Player breaks block
→ PlayerBlockBreakEvents.AFTER (TensionEvents.registerEvents)
→ ChunkTensionManager.addMiningTension(world, ChunkPos, blockId)
→ ChunkTensionData.addMiningTension(pos, baseAmount, world)
   • recentMiningRate += 1, multiplier 1 + rate*0.12
   • TensionActivityLedger.addMining(applied)
   • addLocalTension → setLocalTension → calculateState → DiagnosticLogs.tensionState (on transition)
→ TensionLogger.traceWithGlobals(tick, "MINING", action, blockId, ...)
→ TensionTraceLogger.log (legacy [TRACE])
→ TensionTraceLogger.traceStructured ([TENSION_TRACE])
→ DiagnosticLogs.eventEffect ([EVENT_EFFECT])
→ SessionTelemetryExporter.recordEventEffect (CSV)
```

**Mining magnitudes** resolved in `ChunkTensionManager.addMiningTension` (stone 0.042, ores tiered, ancient debris 0.14).

---

## B. Player movement

```
Server END_SERVER_TICK (TensionServerTick.onEndServerTick)
→ per ServerPlayerEntity (skip creative/spectator)
→ distance² > 0.01 from last position
→ ChunkTensionManager.addLocalTension(world, chunk, amount)  // walk/sprint/swim/elytra/nether
→ TensionActivityLedger.addMovement(amount)
→ TensionLogger.log(tick, "MOVEMENT", moveAction, ...)
→ (same logging chain as mining)
```

**Also movement-related:**

| Trigger | Path |
|---------|------|
| Nether portal ignite | `TensionEvents.UseBlockCallback` → `addLocal` 0.05 |
| Sleep relief | `TensionEvents` server tick → `reduceLocal` 0.02 |
| Session fatigue | `PlayerStateIntegrator.onServerPlayerTick` after 72k ticks → leak 0.00015×hours |

---

## C. Combat

```
Entity dies (AFTER_DEATH)
→ TensionEvents → ChunkTensionManager.addSlaughterTension (0.015 default, villager 0.06, bosses 0.2)
→ TensionLogger.traceWithGlobals("COMBAT", killKind, ...)

Player damaged
→ LivingEntityDamageEvents.AFTER_DAMAGE
→ TensionLogger.log("COMBAT", player_damage, delta=0)  // logs only, no tension change

Hostile spawn buff
→ ServerEntityEvents.ENTITY_LOAD (TensionMobHooks)
→ attribute modifiers if chunk state STRAINED/FRACTURED/DECOUPLED
```

---

## D. Rituals and items

```
Ritual block right-click
→ RitualBlock.onUse
→ ChunkTensionManager.addLocalTension(+0.03)
→ TensionActivityLedger.addRitual(0.03)
→ TensionLogger.log("RITUAL", ritual_block_activation, ...)

RitualHandler.performRitual / performWardingRitual / performStormCalmingRitual
→ TensionManager.reduceTension(0.5 / 0.3 / 0.8)
→ TensionActivityLedger.addRitual

Consumable use (TensionConsumableItem.use)
→ TensionManager.setTension / reduceTension
→ TensionLogger.traceWithGlobals("RITUAL", ...)

Warding crystal held (every 20t)
→ TensionServerTick.runWeatherAndRitualSecondary
→ TensionManager.setTension(max(0, g-0.2))

UseCrystalPacket / RitualStabilizerItem
→ TensionLogger.traceWithGlobals (global+local trace)
```

---

## E. Chunk loading / field update

```
Every 5 server ticks: TensionServerTick.tickChunkTension
→ ChunkTensionData.getTensionMap() for each ServerWorld
→ skip if unloaded; sparse stride if local < 0.22 and no storm
→ neighbor 4-way average → diffusion = D * neighborAvg  (D=0.012)
→ decay = LAMBDA * local * contaminationDecayMul
→ nonlinear = ALPHA * local² (×1.8 if chunk storm)
→ saturation = BETA * local³
→ globalFeedback = GAMMA * globalTension
→ local += diff + nonlinear - saturation + feedback - decay [- storm drain]
→ clamp [0, 5]
→ if local > 0.95 && !storm: setStormActive, triggerLocalStorm (lightning)
→ if local < 0.25: clear chunk storm
→ ChunkTensionData.setLocalTension → state machine + persistence markDirty
→ tickContaminationMemory (staggered)
→ DiagnosticLogs.recovery / test3 (conditional)
```

---

## F. Global coupling and dynamics

```
Every server tick after chunk tick (or same tick if not %5):
→ GlobalFieldCoupling.compute(server)
   • iterate loaded chunks in tension map
   • contribute if STRAINED/FRACTURED/DECOUPLED or t > 0.55
   • excess = max(0, t - 0.55), weighted by state
   • distributed = REGION_GAIN * n^0.88 * meanExcess
   • buckets: fracture/strain/storm per-chunk adds
   • return CouplingSplit(total, ambient, local, stats)
→ TensionManager.tickGlobalDynamics(split.total, stormDiag, tick, ambient, local)
   • regimeCoupling = 0.82 or 1.62
   • inflow = total * regimeCoupling + impulseBuffer
   • ambientDecay = -lambdaEff * g [- NATURAL_RECOVERY if pressure]
   • nonlinear = alphaEff * g²
   • update stormActive hysteresis 0.9 / 0.7
   • updateCorruptionState (tiers 0.8, 1.5, 2.5, 4.0)
   • checkEquilibriumStability (discriminant damp)
   • every 20t: DiagnosticLogs.globalFlowLine → SessionTelemetryExporter.recordGlobalFlow
```

---

## G. Global storm effects (server)

```
TensionManager.isStormActive()
→ TensionServerTick: lightning every 200t cooldown at each player
→ exhaustion every 40t
→ catastrophe stormDiag nonzero in global tick
```

---

## H. Regional storm (ambience)

```
StormManager.tick(ServerWorld)  // per world each server tick
→ maxRegionalTension (3×3 around players)
→ tension = max(global, regional*0.52)
→ applyLightEffects / applyHeavyEffects (sounds)
→ if tension > 0.85 && random: triggerStorm
→ DiagnosticLogs.stormStart → SessionTelemetryExporter
→ drift region via RegionDiagnosticsManager.movingTowardHotterRegion
→ on low tension: endStorm → DiagnosticLogs.stormEnd
```

---

## I. Regional diagnostics and ecology

```
Every 200 ticks: RegionDiagnosticsManager.refresh
→ aggregate 8×8 regions from loaded chunk data
→ DiagnosticLogs.region → (no CSV unless added separately)

TensionSpawnEcology
→ SpawnHelperMixin pushes spawn context
→ onEntityLoad: cull/passive/hostile weight by RegionSnapshot.ecoState
→ DiagnosticLogs.spawnEcology (throttled)
```

---

## J. Client sync and feedback

```
Every 20 ticks: TensionSyncPacket.sendToAllPlayers
→ per player: local at chunk, RegionDiagnosticsManager.get avg/max
→ Client: TensionSyncPacket.onClientPacket
→ TensionSyncState.applySnapshot

Every client tick (UTDModClient):
→ TensionSyncState.tickSmoothing
→ ClientRegionalAtmosphere.tick
→ every 38t: UTDAudioManager.updateAmbientSounds
→ every 200t: ExperimentClientReportPacket.send → DiagnosticLogs.test4
```

---

## K. Persistence

```
ChunkTensionData.setLocalTension / addLocalTension / prune
→ markDirty()
→ writeNbt on save: localTensions list (x,z,tension,state,miningRate,cPeak,cLv)
→ fromNbt on load

Global tension: NOT saved — re-initializes to 0 on server start
```

---

## L. Experiment / debug paths

```
/utdexp commands → ExperimentTelemetry
→ test1/test2 state timing, DiagnosticLogs

DebugSpikeTensionPacket → TensionManager.addEvent / setTension (debug)

F3 action bar (every 40t): TensionServerTick sends chunk/global/state to player
```
