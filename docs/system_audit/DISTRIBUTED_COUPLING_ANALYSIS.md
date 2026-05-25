# Distributed Coupling & Scar Memory — Code Audit (May 2026)

## Design intent (from product spec)

1. **Local** — fast response to activity; can fracture.
2. **Scar** — chunk remembers bruising; re-mining escalates faster; slow local heal.
3. **Global** — accumulates from *many* stressed/scarred chunks; **slow decay**; storms delayed but possible; rituals mitigate.

## What the code actually did BEFORE this pass

| Layer | Implemented? | Gap |
|-------|----------------|-----|
| Local PDE + states | Yes | — |
| `recentMiningRate` → `1 + rate×0.12` on re-mine | Yes | Only while rate > 0 in memory |
| `contaminationLevel` / `cPeak` in NBT | Yes | Survives save |
| Scar slows **local** decay (0.68 / 0.38 mul) | Yes | — |
| Scar weights **global** coupling | **No** | Only indirect via staying above 0.55 longer |
| Scar boosts **re-mining** via cLv | **No** | Only via mining rate counter |
| Global from distributed chunks | Yes | Under-tuned |
| Global slow decay | **No** | LAMBDA=0.0034 erased progress between chunks |

## Why single-chunk global > multi-chunk global (0.053 vs 0.017)

**File:** `GlobalFieldCoupling.compute()`

1. **Mean dilution:** `weightedExcessMean = sum / stressedN` — 9 chunks with moderate excess produce a *lower mean* than one chunk at 2.1 tension.
2. **Sublinear count:** `n^0.88` — 9 chunks ≈ 6.7× one chunk, not 9×.
3. **Single-chunk FRACTURED buckets:** `FRACTURE_PER_CHUNK × fractured` adds inflow independent of mean (multi may have fewer FRACTURED at once during spread mining).
4. **Test timing:** `multi_chunk_contamination` has **80 ticks wait** between each chunk mine burst — global decay (old LAMBDA 0.0034 + NATURAL_RECOVERY 0.00055) drained ~0.0004–0.0009/tick × 80 ≈ 0.03–0.07 per gap.
5. **COUPLING_PRESSURE 0.82** still applies to all inflow (unchanged this pass).

## Scar → global path (runtime)

```
mining → addMiningTension → local t ↑ → updateContaminationMemory (cLv at 1.15/1.35)
       → tickChunkTension: slower decay if cLv > 0
       → if t > 0.55 OR state STRAINED+: GlobalFieldCoupling contributes excess
```

Scar did **not** add terms to coupling formula directly (fixed this pass with `scarW` multiplier).

## Changes applied (tuning + minimal weighting only)

| Parameter | Was | Now | File |
|-----------|-----|-----|------|
| REGION_GAIN | 0.00188 | **0.00280** | GlobalFieldCoupling |
| REGION_EXPONENT | 0.88 | **0.93** | GlobalFieldCoupling |
| LAMBDA (global decay) | 0.0034 | **0.0018** | TensionManager |
| Contamination decay lv1 | 0.68 | **0.60** | ChunkTensionData |
| Contamination decay lv2 | 0.38 | **0.30** | ChunkTensionData |
| SCAR_COUPLING_LV1/LV2 | — | **0.06 / 0.12** | GlobalFieldCoupling (weight on excess) |
| SCAR_MINING_LV1/LV2 | — | **0.10 / 0.18** | ChunkTensionData (re-mine multiplier) |

**Not changed:** scheduler, telemetry, StormManager, rituals, local PDE (D, ALPHA, BETA), COUPLING_PRESSURE, STRESS_BASELINE, storm thresholds.

## Expected effect (casual solo player)

- Mine 3–5 chunks over a few minutes → global should climb into **0.03–0.12** band and hold longer after leaving.
- Re-visit a bruised chunk (cLv still on disk) → faster local spike from scar + mining rate.
- One chunk industrial test → global still **well below** storm (0.9); local fracture unchanged.
- Idle world → still **zero** coupling (no stressed chunks).

## Re-verify

```
/utdtest run idle_drift
/utdtest run single_chunk_fracture
/utdtest run multi_chunk_contamination
/utdtest run post_storm_recovery
```

Targets: multi `peak_global` ≥ 0.025; idle ≤ 0.005; single `peak_global` < 0.15; `global_warn` tick may fire before peak if sustained inflow crosses 0.5.
