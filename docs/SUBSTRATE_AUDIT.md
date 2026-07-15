# UTD Substrate Implementation Audit
**Date:** 2026-07-15  
**Scope:** Memory implementation · Determinism  
**Method:** Direct source inspection of all Java files under `src/main/java/com/utdmod/`

---

## PART I — MEMORY IMPLEMENTATION

---

### 1. Memory Dependency Graph

```
DECLARATION
  SubstrateState.memory (double)          [SubstrateState.java]

INITIALISATION
  SubstrateState()              → memory = 0.0
  SubstrateState(A,M,T,D)       → memory = 0.0
  SubstrateState(A,M,T,D,B)     → memory = 0.0

WRITE — integration (every 5 ticks)
  SubstrateState.applyUnifiedFieldStep()
    memory = clamp(prevM + dMemory, 0.0, v2ClampMax=4.0)
    where dMemory = FieldDerivative.computeUnified(...).dMemory

WRITE — persistence load
  SubstrateState.restoreFromPersistence()    direct assignment from NBT double
  ChunkTensionData.fromNbt()                 reads key "sMemory"

WRITE — persistence save
  ChunkTensionData.writeNbt()                writes key "sMemory"

COMPUTE dMemory — all in FieldDerivative.computeUnified()
  formation  = computeMemoryFormation(A, B, D, params)
  peakStr    = activationPeakStrength from SpatialStencil.activationLaplacian
  saturation = (1 - M/Mmax) * formation * (0.30 + 0.70 * peakStr)
  forgetting = -0.4 * computeMemoryDecayRate(D) * M * (2 + 3*(1 - clamp01(A)))
  antiDiff   = -0.15 * g_M * (A/(1+A)) * stencil.memoryLaplacian
  diffusion  =  0.25 * memDiffGain * stencil.memoryLaplacian
  dMemory    = saturation + forgetting + antiDiff + diffusion

READS — consumers of memory

  FieldDerivative.computeUnified()
    memorySusceptibility = M/(1+M)
      → A.susceptibility, A.responseGain, A.dissipation
    M/Mmax                                → M.saturation ceiling
    M directly                            → M.forgetting rate
    memoryGate = M/(1+M)
      → D.dynamicDmax, D.couplingD, D.baselinePersistence
    M in computeMemoryFormation           → depth-retention factor

  SubstrateTransport.computeIncoming()
    nM in neighborStim:
      neighborStim *= (1 + v2MemoryInfluence * nM/(nM + v2MemoryEffectK))
    M in transportModifier:
      localState = A + 0.35*M + 0.20*D + 0.15*B

  SubstrateState.applyUnifiedFieldStep()
    convergence = 1/(1 + |ΔA| + M + D)   [diagnostic field, not fed back]
    refreshDerivedObservables()
      readiness = exp(-M / max(0.5, readinessMemoryScale))

  SubstrateObservationModel.computeLocalTension()
    T(x) += wM * M/(1+M)                 → chunk state, regions, HUD, sync

  SubstrateObservationModel.computeDanger()
    (M/(1+M)) * 0.3                       [diagnostic only]

  RegionManager.refresh()
    ss.getMemory() → Region.meanMemory    [aggregate statistic]

  ExperimentLogger.logSubstrateSnapshot()   → JSONL field "memory"
  ExperimentLogger.maybeLogFieldSummary()   → JSONL field "memory_mean"
  ResearchObserver.tick()                   → scarCount (M > 0), meanMemory

  ChunkTensionData.writeNbt()               → disk key "sMemory"
  ChunkTensionData.fromNbt()                → read disk key "sMemory"
```

---

### 2. Exact Implemented Memory Equation

All terms below are from `FieldDerivative.computeUnified()` and its helper methods.
Parameter defaults are from `ParameterSet` constructor (`substrateGameplayTuningEnabled = false`).

**Helper: computeMemoryFormation(A, B, D, params)**
```
activationDrive = max(0, A − θ_M)
  θ_M = memoryFormationThreshold = 0.05

if activationDrive == 0 → formation = 0  [hard zero below threshold]

depthModulation = 1 + γ_DM · min(1, D/(1+D))
  γ_DM = memoryDepthRetentionGain = 0.8

normalizedDrive = activationDrive / (1 + activationDrive)   [saturating in A]

formation = g_M · normalizedDrive · depthModulation
  g_M = memoryFormationGain = 0.02
```

**Helper: computeMemoryDecayRate(D, params)**
```
r_eff(D) = r_M0 / (1 + γ_DM · max(0, D))
  r_M0  = memoryDecayRate = 0.001
  γ_DM  = 0.8
```

**Helper: activationPeakStrength (from SpatialStencil)**
```
∇²A = (mean of 4-cardinal-neighbor A values) − A_center   [SpatialStencil]

peakStr = max(0, −∇²A) / (0.1 + |∇²A|)
  → 0 at spatially flat or rising sites
  → approaches 1.0 at local activation maxima (∇²A strongly negative)
```

**Full dMemory:**
```
saturation  = (1 − M / max(1e-6, M_max))
            · formation
            · (0.30 + 0.70 · peakStr)
  M_max = memoryMaximum = 2.0

actFF  = 2.0 + 3.0 · (1 − clamp(A, 0, 1))
  [ranges: 2.0 when A≥1, up to 5.0 when A=0]

forgetting  = −0.4 · r_eff(D) · M · actFF

actGate  = A / (1 + A)

antiDiff = −0.15 · g_M · actGate · ∇²M
  [sharpens memory peaks while A > 0; stops when A → 0]

diffM    =  0.25 · memDiffGain · ∇²M
  memDiffGain = memoryDiffusionGain = 0.002

dMemory  = saturation + forgetting + antiDiff + diffM
```

**State update with hard clamp:**
```
M(t+1) = clamp(M(t) + dMemory, 0.0, 4.0)
```

**Expanded single-line form (all substitutions):**
```
M(t+1) = clamp(
  M(t)
  + (1 − M(t)/2.0)
    · 0.02
    · [(max(0, A(t)−0.05)) / (1 + max(0, A(t)−0.05))]
    · (1 + 0.8 · min(1, D(t)/(1+D(t))))
    · (0.30 + 0.70 · max(0, −∇²A) / (0.1 + |∇²A|))
  − 0.4
    · [0.001 / (1 + 0.8 · D(t))]
    · M(t)
    · (2.0 + 3.0 · (1 − clamp(A(t), 0, 1)))
  − 0.15 · 0.02 · (A(t)/(1+A(t))) · ∇²M
  + 0.25 · 0.002 · ∇²M
  ,
  0.0, 4.0
)
```

---

### 3. Difference Between Documented Equation and Implemented Equation

The telemetry analysis (and `CANONICAL_SUBSTRATE_AUDIT.md`) compared against:

```
dM/dt = gain · A − decay · M        [simplified theoretical]
  gain = 0.02,  decay = 0.001
```

**Answer: Category C — implemented with nonlinear modifiers.**
The simplified formula is not implemented. The following modifiers are present in the
code and absent from the simplified formula:

| Modifier | Mathematical form | Effect direction |
|---|---|---|
| Activation threshold | `max(0, A−0.05)` before formation | Suppresses gain when A < 0.05 |
| Saturating drive | `(A−θ)/(1+(A−θ))` instead of bare `A` | Caps contribution per unit A; bounded above by g_M |
| Logistic ceiling | `(1 − M/2.0)` multiplies formation | Strong suppression as M grows toward 2.0 |
| Peak-strength gate | `(0.30 + 0.70·peakStr)` multiplies formation | Suppresses to 0.30× at uniform/rising sites |
| Depth-retention of formation | `1 + 0.8·min(1,D/(1+D))` multiplies formation | Small amplification (up to 1.8×) |
| Activity-weighted forgetting | factor `2.0 + 3.0·(1−A)` on decay | Up to 5× decay at low A, 2× at high A |
| Depth-retention of decay | `1/(1+0.8·D)` divides decay rate | Slows forgetting as D accumulates |
| Anti-diffusion | `−0.15·g_M·(A/(1+A))·∇²M` | Spatially sharpens memory peaks while active |
| Diffusion | `+0.25·0.002·∇²M` | Spatially smooths memory gradients |

---

### 4. Explanation of the Telemetry Discrepancy

**Telemetry findings (Run 4, relaxation state):**
- Effective gain ≈ 0.0034 vs configured gain = 0.02
- Correlation between simplified theoretical increment and observed gradient: r = −0.314

**Reconstructed at Run 4 steady state (A ≈ 0.1295, M ≈ 0.441, D ≈ 0.490):**

Step through the formation path:
```
activationDrive  = 0.1295 − 0.05 = 0.0795
normalizedDrive  = 0.0795 / 1.0795 = 0.0737         [vs bare A = 0.1295, ratio 0.569]
depthModulation  = 1 + 0.8 · (0.490/1.490) = 1.263
formation        = 0.02 · 0.0737 · 1.263 = 0.001863

(1 − M/Mmax)     = 1 − 0.441/2.0 = 0.7795
peakStr          ≈ 0  [flat/uniform field in autonomous Run 4]
peak factor      = 0.30 + 0.70·0 = 0.30

saturation       = 0.7795 · 0.001863 · 0.30 = 0.000435
```

The simplified formula predicts: `gain · A = 0.02 · 0.1295 = 0.00259`

Ratio: `0.000435 / 0.00259 = 0.168` → effective gain ≈ `0.02 · 0.168 ≈ 0.0034`

This matches the telemetry finding exactly.

**Why the correlation is negative (r = −0.314):**

The simplified formula predicts `Δ_theory ↑` when A rises. But in the implementation,
a rise in A simultaneously:
- Reduces `actFF = 2+3·(1−A)` → forgetting slows (positive for M)
- Increases `saturation` only modestly due to the logistic ceiling at M = 0.441
- Increases `actGate = A/(1+A)` → anti-diffusion strengthens (negative contribution at local peaks)

The net implemented change can move opposite to the simplified prediction depending on
whether the spatial terms or the threshold nonlinearity dominate at a given moment. This
sign inversion across A is the direct cause of the negative correlation.

**Classification:**

The discrepancy is **expected**. It is a documentation error in the telemetry report:
the comparison formula does not represent the code. The memory system functions as
implemented. No bug is present.

---

### 5. Complete Memory Read/Write Location Table

| Operation | Class | Method | Detail |
|---|---|---|---|
| **WRITE** | `SubstrateState` | constructor (3 variants) | `memory = 0.0` |
| **WRITE** | `SubstrateState` | `applyUnifiedFieldStep()` | `memory = clamp(prevM + dMemory, 0, 4.0)` |
| **WRITE** | `SubstrateState` | `restoreFromPersistence()` | direct assignment from loaded value |
| **WRITE** | `ChunkTensionData` | `fromNbt()` | reads `"sMemory"` NBT double |
| **SAVE** | `ChunkTensionData` | `writeNbt()` | writes `"sMemory"` NBT double |
| **READ** | `FieldDerivative` | `computeUnified()` | memorySusceptibility, logistic ceiling, forgetting, memoryGate for D |
| **READ** | `FieldDerivative` | `computeMemoryFormation()` | D modulates gain via depth-retention |
| **READ** | `FieldDerivative` | `computeMemoryDecayRate()` | D modulates decay rate |
| **READ** | `SubstrateTransport` | `computeIncoming()` | `nM/(nM+k)` in neighborStim; `M` in state modifier |
| **READ** | `SubstrateState` | `applyUnifiedFieldStep()` | convergence diagnostic `1/(1+|ΔA|+M+D)` |
| **READ** | `SubstrateState` | `computeReadiness()` | `exp(-M/scale)` |
| **READ** | `SubstrateObservationModel` | `computeLocalTension()` | `wM·M/(1+M)` → T(x) |
| **READ** | `SubstrateObservationModel` | `computeDanger()` | `(M/(1+M))·0.3` (diagnostic) |
| **READ** | `RegionManager` | `refresh()` | `ss.getMemory()` → `Region.meanMemory` |
| **READ** | `ExperimentLogger` | `logSubstrateSnapshot()` | JSONL field `"memory"` |
| **READ** | `ExperimentLogger` | `maybeLogFieldSummary()` | JSONL field `"memory_mean"` |
| **READ** | `ResearchObserver` | `tick()` | `scarCount` (M>0), `meanMemory` |

---

## PART II — DETERMINISM

---

### 6. Determinism Audit — All Sources Checked

| Source | Present | Location | Affects substrate A/M/D/B? |
|---|---|---|---|
| `Math.random()` | **Yes** | `RandomNoise.java:7` | **No** — `NoiseSignal` is the only consumer; `NoiseSignal` has no caller outside its own package. Dead code relative to current execution. |
| `world.random` | **Yes** | `RegionEcologyManager.java` (chunk selection, surface block positions, block placement decisions) | **No** — only affects which blocks are mutated |
| `world.random` | **Yes** | `ChunkTensionData.triggerStateTransitionEffects()` | **No** — only used for stdout particle position log; result not stored or fed back |
| `world.random` | **Yes** | `TensionSpawnEcology.java` | **No** — only affects spawn culling |
| `System.currentTimeMillis()` | **Yes** | `ChunkTensionData.touch()` and `pruneInactive()` | **No** on field values; **Yes** on which chunks are pruned |
| `System.currentTimeMillis()` | **Yes** | `ExperimentLogger.initialize()`, `logRunEnd()` | **No** — run ID and timestamps only |
| `System.currentTimeMillis()` | **Yes** | `TensionSerpentEntity` | **No** — entity duration tracking, not substrate |
| `System.nanoTime()` | **Yes** | `FieldActuatorRegistry`, `UTDProfiler` | **No** — timing measurement only, result logged, not fed into dynamics |
| `Instant.now()` | **Yes** | `ExperimentLogger.initialize()`, `ResearchPlayerLogger` | **No** — run ID only |
| `HashMap` iteration (`substrateStates`) | **Yes** | `ChunkTensionData.getSubstrateStatesView().keySet()` → `TensionServerTick.tickSubstrate()` | **No** — double-buffer design: all reads from tick-start snapshot; write order does not affect values |
| `HashMap` iteration (`substrateStates`) | **Yes** | `TensionManager.recompute()` | **No** — floating-point sum order varies, but the aggregated scalar is not fed back into per-chunk dynamics |
| `HashSet` iteration (`region.getChunks()`) | **Yes** | `RegionEcologyManager.selectChunks()` — but input is `new ArrayList<>(region.getChunks())` then shuffled via LCG | **No** — ecology only |
| `ThreadLocalRandom` | **No** | — | — |
| `new Random()` (unseeded) | **No** | — | — |
| Parallel streams | **No** | — | — |
| Async execution | **No** | All substrate updates on server thread | — |
| Concurrent collections | **No** | All maps are standard `HashMap`/`LinkedHashMap` | — |
| `server.getWorlds()` order | **Yes** | `TensionServerTick.tickSubstrate()`, `TensionManager.recompute()` | Only affects aggregated global mean order; double-buffer makes per-chunk output order-independent |
| `server.getPlayerManager().getPlayerList()` | **Yes** | `TensionServerTick.trackPlayerMovement()` | Only affects impulse accumulation order; since each player writes to its own chunk independently, no ordering dependency |

---

### 7. Root Causes of `Determinism: FAIL`

**Root cause 1 (primary): The cover sheet logic is a run-completion flag, not a physics test.**

```java
// ExperimentLogger.java — exportCoverSheet() — line 655 (before fix)
cover.append("Determinism: ").append("completed".equals(status) ? "PASS" : "FAIL").append('\n');
```

`"PASS"` is printed only when `logRunEnd(server, tick, "completed")` is called. All four
runs reported `"FAIL"`, which means **all four runs were terminated without the mod's
`logRunEnd` being called** — the server was stopped externally (kill signal, crash, or
IDE stop button) before the mod's shutdown hook fired. The word `"Determinism"` in the
label is misleading: it has no connection to whether the substrate field reproduces
across runs. The cover sheet is a run-health indicator written at process exit, not a
reproducibility test.

**Root cause 2 (secondary): Actual inter-run variation sources exist, but none affect substrate field values.**

The substrate integration path (`FieldDerivative.computeUnified`, `SubstrateTransport.computeIncoming`,
`ChunkTensionData.integrateAllSubstrateStates`) is purely deterministic given:
- Identical initial substrate state
- Identical `externalInput` impulse sequence (same player actions, same ticks)
- Identical chunk neighborhood layout

The double-buffer design (`integrateAllSubstrateStates` reads a full snapshot before writing)
makes chunk processing order irrelevant within a single integration step. No race condition,
no random draw, no wall-clock value enters the field equations.

What is not reproducible across runs:
- Which blocks are mutated by `RegionEcologyManager` (uses `world.random`)
- Which chunks survive TTL pruning (uses `System.currentTimeMillis()`)
- Floating-point accumulation order in `TensionManager.recompute()` global sum

None of these affect M, A, D, or B.

---

### 8. Whether Either Issue Requires Code Modification

**Memory gain discrepancy:** No modification required. The implemented equation is correct
as written. The discrepancy was between the implemented equation and a simplified formula
used in the telemetry analysis. The system is not broken.

**Determinism FAIL:**

- Root cause 1 (cover sheet label): requires a targeted one-line fix. **Fixed** (see §9).
- Root cause 2 (`Math.random()` in `RandomNoise`): requires a latent-risk fix. **Fixed** (see §9).
  The class is currently dead code but the unsafety should be removed before any future
  wiring into the hot path.
- Root cause 2 (ecology `world.random`, TTL wall-clock): these are correct uses of
  non-deterministic sources for decorative systems. No modification required unless
  byte-for-byte replay of block mutations is a research requirement.

---

### 9. Modifications Applied

---

#### Modification 1 — Cover sheet determinism label

**File:** `src/main/java/com/utdmod/experiment/ExperimentLogger.java`  
**Method:** `exportCoverSheet`  
**Original line 655:**
```java
cover.append("Determinism: ").append("completed".equals(status) ? "PASS" : "FAIL").append('\n');
cover.append("Warnings: none\n");
```

**Replacement:**
```java
// NOTE: "Determinism" previously reflected whether the run ended cleanly,
// not whether the substrate field reproduced bit-for-bit across runs.
// The substrate integration (A/M/D/B via FieldDerivative.computeUnified) is
// deterministic given fixed player inputs and chunk-loading sequence.
// Known inter-run variation sources (do NOT affect substrate field values):
//   - ecology block mutations: world.random (RegionEcologyManager)
//   - TTL pruning decision: System.currentTimeMillis() (ChunkTensionData)
//   - world/player list iteration order: varies with Minecraft chunk loading
cover.append("Run completed cleanly: ").append("completed".equals(status) ? "YES" : "NO").append('\n');
cover.append("Determinism: NOT_MEASURED\n");
cover.append("Determinism note: substrate field (A/M/D/B) is deterministic given fixed inputs; ")
     .append("ecology block mutations and TTL pruning use wall-clock time and world.random.\n");
cover.append("Warnings: none\n");
```

**Justification:** The old label caused the telemetry analysis to treat four server-stop
events as evidence of physics-level nondeterminism, triggering a misdirected investigation.
The new label correctly separates run-completion status from the question of reproducibility.

---

#### Modification 2 — `Math.random()` in `RandomNoise`

**File:** `src/main/java/com/utdmod/util/RandomNoise.java`  
**Original:**
```java
public double next() {
    double raw = Math.random() * 2 - 1;
    value = 0.95 * value + 0.05 * raw;
    return value;
}
```

**Replacement:** instance `Random` with optional seed constructor.
```java
import java.util.Random;

public class RandomNoise {
    private final Random rng;
    private double value = 0;

    public RandomNoise() {
        this.rng = new Random();           // non-reproducible, as before
    }

    public RandomNoise(long seed) {
        this.rng = new Random(seed);       // reproducible path for future use
    }

    public double next() {
        double raw = rng.nextDouble() * 2 - 1;
        value = 0.95 * value + 0.05 * raw;
        return value;
    }
}
```

**Justification:** `Math.random()` is backed by a global unseeded `Random` shared across
all callers in the JVM. If `RandomNoise` is ever connected to a path that feeds
`externalInput` or `incomingTransport`, it would silently introduce nondeterminism into
substrate field values. The fix eliminates the global shared state while preserving the
existing non-reproducible default behaviour. The seeded constructor provides a safe
wiring point for any future use in the research pipeline.

**Reach verification:** `RandomNoise` is only referenced by `NoiseSignal`. `NoiseSignal`
is only declared in `NoiseSignal.java`; it has no import in any other class. Neither class
participates in the substrate integration call graph. The fix eliminates a latent risk,
not an active bug.

**Build status:** `./gradlew compileJava` — `BUILD SUCCESSFUL` after both modifications.
