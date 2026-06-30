# Tuning Surface — Primary Balancing Reference

**Observed behavior** reflects current code defaults. **Safe/dangerous ranges** are engineering estimates for iterative tuning — not playtest guarantees.

---

## Global field (`TensionManager.java`)

| Variable | Current | Gameplay effect | Safe range (est.) | Dangerous range (est.) |
|----------|---------|-----------------|-------------------|------------------------|
| `T_MAX` | 5.0 | Hard cap on global | 3–8 | >10 (overflow feel) |
| `LAMBDA` | 0.0034 | Baseline global decay | 0.002–0.006 | <0.001 (runaway), >0.01 (never storms) |
| `NATURAL_RECOVERY` | 0.00055 | Extra heal when calm regime | 0.0003–0.001 | >0.002 (trivializes mining) |
| `ALPHA_PRESSURE` | 0.00035 | Subtle global reinjection | 0.0002–0.0006 | >0.001 |
| `ALPHA_CATASTROPHE` | 0.00115 | Storm-era reinjection | 0.0006–0.002 | >0.003 |
| `STORM_THRESHOLD` | 0.9 | Global storm arm | 0.75–1.1 | <0.6 (perma-storm) |
| `STORM_HYSTERESIS` | 0.7 | Global storm release | 0.5–0.85 | >threshold |
| `COUPLING_PRESSURE` | 0.82 | Calm-world coupling gain | 0.5–1.2 | >1.5 |
| `COUPLING_CATASTROPHE` | 1.62 | Storm coupling gain | 1.0–2.2 | >3 |
| `impulseBuffer decay` | 0.88/tick | Debug/rare impulse fade | 0.8–0.95 | — |
| Catastrophe `lambdaEff` mult | 0.22× | Slower decay in storm | 0.15–0.35 | <0.1 |
| `CORRUPTION_THRESHOLDS` | 0.8, 1.5, 2.5, 4.0 | Named tiers / messaging | shift ±0.2 each | — |

**Equilibrium guard:** discriminant `<0` → `globalTension *= 0.9` — do not disable without replacement.

---

## Distributed coupling (`GlobalFieldCoupling.java`)

| Variable | Current | Gameplay effect | Safe range | Dangerous |
|----------|---------|-----------------|------------|-----------|
| `STRESS_BASELINE` | 0.55 | Chunks below don't feed global | 0.45–0.65 | <0.3 (idle heat), >0.8 (no coupling) |
| `REGION_GAIN` | 0.00175 | Scale of distributed inflow | 0.001–0.004 | >0.006 |
| `REGION_EXPONENT` | 0.88 | Multi-region scaling | 0.75–1.0 | >1.2 (runaway with many chunks) |
| `WEIGHT_STRAINED` | 1.0 | Strained weight | 0.8–1.2 | — |
| `WEIGHT_FRACTURED` | 1.22 | Fractured weight | 1.0–1.5 | — |
| `WEIGHT_DECOUPLED` | 1.32 | Decoupled weight | 1.1–1.6 | — |
| `FRACTURE_PER_CHUNK` | 0.00034 | Fracture bucket | 0.0001–0.0006 | >0.001 |
| `STRAIN_PER_CHUNK` | 0.00011 | Strain bucket | 0.00005–0.00025 | — |
| `STORM_PER_CHUNK` | 0.00007 | Local storm bucket | 0.00003–0.00015 | — |

---

## Chunk field (`TensionServerTick.java`)

| Variable | Current | Gameplay effect | Safe range | Dangerous |
|----------|---------|-----------------|------------|-----------|
| `D` | 0.012 | Diffusion from neighbors | 0.006–0.02 | >0.04 (instant spread) |
| `LAMBDA` | 0.0006 | Local linear decay | 0.0003–0.0012 | <0.0002 (sticky) |
| `ALPHA` | 0.009 | Local quadratic growth | 0.005–0.015 | >0.025 |
| `BETA` | 0.055 | Cubic saturation | 0.03–0.08 | <0.02 (unbounded) |
| `GAMMA` | 0.045 | Global→local feedback | 0.02–0.07 | >0.12 |
| `STORM_THRESHOLD` | 0.95 | Local lightning storm | 0.85–1.1 | <0.7 (constant storms) |
| `STORM_END_THRESHOLD` | 0.25 | Local storm clear | 0.15–0.4 | >0.5 |
| `STORM_DRAIN_RATE` | 0.002 | Drain while chunk storm | 0.001–0.004 | — |
| Storm `ALPHA` mult | 1.8× | Faster growth in chunk storm | 1.3–2.5 | — |
| Calm sparse stride | 8 | CPU skip for calm chunks | 4–16 | — |

### Movement amounts (same file)

| Action | Amount |
|--------|--------|
| walk | 0.0015 |
| sprint | 0.0022 |
| swim | 0.002 |
| elytra | 0.0035 |
| nether mult | 1.25× |

---

## Chunk states (`ChunkTensionData.java`)

| Variable | Current | Gameplay effect | Safe range | Dangerous |
|----------|---------|-----------------|------------|-----------|
| `T1` | 0.95 | STRAINED entry | 0.85–1.05 | <0.7 |
| `T2` | 1.28 | FRACTURED entry | 1.1–1.5 | <1.0 |
| `T3` | 3.0 | DECOUPLED entry | 2.5–4.0 | <2.0 |
| `STATE_HYSTERESIS` | 0.22 | State stickiness | 0.12–0.3 | >0.4 |
| Mining rate mult | 0.12/rate | Escalating mining | 0.08–0.18 | >0.25 |
| Contamination lv1 | 1.15 | Medium memory | ±0.1 | — |
| Contamination lv2 | 1.35 | Heavy memory | ±0.1 | — |
| Contamination decay mult | 0.68 / 0.38 | Recovery speed | 0.5–0.85 | <0.25 |

---

## Mining bases (`ChunkTensionManager.java`)

| Block class | baseAmount |
|-------------|------------|
| stone | 0.042 |
| deepslate (non-ore) | 0.048 |
| generic ore | 0.052 |
| coal | 0.056 |
| deepslate ore | 0.078 |
| emerald | 0.09 |
| diamond | 0.11 |
| ancient debris | 0.14 |
| other | 0.008 |

**Safe:** ±30% per tier. **Dangerous:** 2× all tiers without lowering `BETA`/`REGION_GAIN`.

---

## Combat (`ChunkTensionManager` / `TensionEvents`)

| Variable | Current |
|----------|---------|
| default kill | 0.015 |
| villager | 0.06 |
| iron golem | 0.08 |
| dragon/wither | 0.2 |

---

## Secondary events (`TensionEvents.java`)

| Variable | Current |
|----------|---------|
| `NETHER_PORTAL_TENSION` | 0.05 |
| `SLEEP_LOCAL_RELIEF` | 0.02/tick |
| `BULK_BUILD_TENSION` | 0.01 |
| `ENCHANTING_TENSION` | 0.01 |
| `DEEP_MINING_TENSION` | 0.0015 / 200t |
| sleep block threshold | 1.12 effective |
| bulk threshold | 30 blocks / 5s |

---

## Regional storm (`StormManager.java`)

| Variable | Current | Safe | Dangerous |
|----------|---------|------|-----------|
| regional blend | 0.52× max local | 0.4–0.65 | — |
| start threshold | 0.85 | 0.75–0.95 | <0.7 |
| start random | 1/120/tick | 1/60–1/200 | 1/20 |
| end cut low/high | 0.20 / 0.10 | — | — |
| light effect divisor | tension×18 | — | — |

---

## Weather secondary (`TensionServerTick.runWeatherAndRitualSecondary`)

| Threshold | Behavior |
|-----------|----------|
| 0.62 cross | chance rain |
| 1.0 cross | forced rain / clear |
| 0.7 | XP drip 10% |
| warding crystal | −0.2 global/tick while held |

---

## Rituals

| Source | Amount |
|--------|--------|
| RitualBlock | +0.03 **local** |
| RitualHandler.perform | −0.5 global |
| Warding ritual | −0.3 global |
| Storm calming | −0.8 global |
| volatile insight | +0.05 global |
| hushed lure | −0.02 self, −0.01 nearby |

---

## Client feel (`TensionSyncState.java`)

| Variable | Current |
|----------|---------|
| smooth `aG` | 0.07 |
| smooth `aL` | 0.11 |
| perceived blend | max(g, l×0.92) |

---

## Diagnostics-only (`TensionServerTick` stormDiag)

| Formula | When |
|---------|------|
| `0.009×fractured + 0.0055×stressed + 0.0035×stormChunks` | catastrophe regime only |

Logged as `storm_drive` in GLOBAL_FLOW — **does not add to simulation** (diagnostic note).

---

## Session / experiment

| Knob | Location |
|------|----------|
| `TensionLogger.ENABLED` | true |
| Region refresh | 200 ticks |
| GLOBAL_FLOW cadence | 20 ticks |
| CSV flush | every 8 rows |

---

## Tuning workflow recommendation

1. Change **one family** (e.g. coupling OR chunk OR global decay) per session.
2. Compare `logs/utd_sessions/session_*_summary.txt` peaks and storm counts.
3. Use `[GLOBAL_FLOW]` net sign at equilibrium mining rate as sanity check.
4. Never tune `STORM_THRESHOLD` (local) and `T1` independently without noting they are both ~0.95 today.
