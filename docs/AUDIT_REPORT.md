# Documentation Audit Report — UTD Mod Fabric

**Date:** 2026-07-15  
**Auditor:** Kiro (automated source-truth audit)  
**Scope:** All Markdown files under `docs/` cross-referenced against `src/main/java/com/utdmod/`  
**Verdict format:** Implemented / Partially Implemented / Deprecated / Incorrect / Historical

---

## Summary of Findings

The repository contains two interleaved documentation histories:

1. **Pre-refactor docs** describing the original single-scalar tension ODE system (`TensionServerTick.tickChunkTension` with `D*neighborAvg + alpha*L^2 - beta*L^3 + gamma*G - lambda*L`, and `TensionManager.tickGlobalDynamics` as a G-ODE). These descriptions are **Incorrect** against the current code.

2. **Post-refactor docs** (mostly partial) describing the substrate port with `SubstrateState` (A, M, D, B) as the evolving field and `TensionManager` as a derived metric aggregator. These are broadly **Implemented** with some framing gaps.

The principal risk is that any external engineer reading `EQUATIONS.md`, `EXTRACTED_EQUATIONS.md`, `02_LOCAL_FIELD.md`, `04_GLOBAL_SYSTEM.md`, `PARAMETER_CATALOG.md`, or `STATE_MACHINE.md` will believe there is a running scalar-ODE tension system that does not exist in the current codebase.

---

## A. File-by-File Disposition

### docs/architecture_audit/

| File | Verdict | Issue |
|------|---------|-------|
| `EQUATIONS.md` | **Incorrect** | Both equations describe the old ODE system. Chunk equation `T += D·T̄_nbr + α·T² - β·T³ + γ·G - λ·T` is gone. Global equation `G += -λ·G + α·G² + I` is gone. See replacement below. |
| `EXTRACTED_EQUATIONS.md` | **Incorrect** | Full old-system equation set. λ_g=0.0032, α_g=0.002, global ODE, chunk PDE with alpha/beta/gamma constants — none of these exist in TensionManager or TensionServerTick. |
| `PHASE_1_EXTRACTED_EQUATIONS.md` | **Incorrect** | Same as EXTRACTED_EQUATIONS. Pre-refactor snapshot. |
| `PARAMETER_CATALOG.md` | **Incorrect** | Corruption thresholds listed as [0.8, 1.5, 2.5, 4.0]; actual code: [0.20, 0.40, 0.60, 0.80]. Global storm onset listed as 1.0/0.9; actual: 0.55/0.40. Local PDE parameters (D=0.012, α=0.009, β=0.055, γ=0.045) do not exist. |
| `STATE_MACHINE.md` | **Incorrect** | Section 3 "Global storm" says onset >0.9, offset <0.7. Actual: onset >0.55, offset <0.40. Section 4 "Corruption tiers" says thresholds [0.8, 1.5, 2.5, 4.0]. Actual: [0.20, 0.40, 0.60, 0.80]. Chunk storm thresholds (>0.95, <0.25) are **Implemented** correctly. ChunkState thresholds (T1=0.95, T2=1.28, T3=3.0) are **Implemented** correctly. |
| `04_GLOBAL_SYSTEM.md` | **Incorrect** | Claims `TensionManager.tickGlobalDynamics` runs every tick with ODE `G += -λG + αG² + I`. That method is a `@Deprecated` stub (no-op). Global tension is now recomputed by `TensionManager.recompute()` as weighted spatial mean of substrate T(x). |
| `02_LOCAL_FIELD.md` | **Incorrect** | Describes chunk update using `TensionServerTick.tickChunkTension(...)` with old reaction-diffusion PDE. That method no longer exists. Actual: `ChunkTensionData.integrateAllSubstrateStates()` runs `FieldDerivative.computeUnified()`. Also references `ChunkTensionManager.addForestryTension()` and `addSlaughterTension()` — these should be verified as present or absent. |
| `00_EXECUTION_PIPELINE.md` | **Partially Implemented** | Step-by-step sequence is broadly correct in structure. Specific claims needing update: (a) "dominant state variable is chunk-local tension" — actually substrate state (A,M,D,B) is the primary variable; T(x) is derived. (b) Step 3 "local tension stored in ChunkTensionData" — partially true; substrate state is stored, tension is computed on demand. (c) Step 6 "Global tension updated every tick in TensionManager" — correct but framing is wrong: it is a recomputation, not an ODE step. |
| `SUMMARY.md` | **Partially Implemented** | Correct in overall framing that the system is server-authoritative spatial simulation. Incorrect in calling chunk-local tension "the dominant runtime subsystem updated every 5 ticks." The dominant subsystem is the substrate integration via `integrateAllSubstrateStates`. |
| `03_REGION_SYSTEM.md` | **Partially Implemented** | Mostly accurate. Minor: "reads chunk tension values from ChunkTensionData" — regions are built from computed T(x) via `SubstrateObservationModel.computeLocalTension`, not stored tension values. |
| `01_EVENT_INJECTION.md` | **Partially Implemented** | Broadly accurate. Note: references `ChunkTensionManager.addForestryTension(...)` and `addSlaughterTension(...)` — verify these exist in `ChunkTensionManager.java`. The event injection path now routes through `ChunkTensionData.addInstantaneousImpulse()` in most cases. |
| `05_STORM_SYSTEM.md` | **Implemented** | Accurate. StormManager, region-based storm selection, readiness scoring, lightning targeting — all confirmed against source. |
| `06_ECOLOGY.md` | **Implemented** | Accurate. RegionEcologyManager, block mutations, tick cadence all confirmed. |
| `07_NETWORKING.md` | **Implemented** | Accurate. TensionSyncPacket sends every 20 ticks; TensionSyncState client mirror confirmed. |
| `08_PRESENTATION.md` | **Implemented** | Accurate. Client presentation via ClientRegionalAtmosphere, RegionPresentationProfile confirmed. |
| `09_INVARIANTS.md` | **Not verified** — Not read in detail. Likely contains references to old system invariants. Needs review. |
| `10_FAILURE_MODES.md` | **Not verified** — May reference old ODE failure modes. Needs review. |
| `11_PERFORMANCE.md` | **Not verified** — Likely discusses old tick cadences. Needs review. |
| `CALL_GRAPH.md` | **Partially Implemented** | Contains call graph referencing `TensionServerTick.tickChunkTension()` which does not exist. Method was replaced by `tickSubstrate()` and `ChunkTensionData.integrateAllSubstrateStates()`. |
| `STATE_MUTATIONS.md` | **Partially Implemented** | May reference deprecated mutation paths. Needs update for substrate-first mutation model. |
| `EVIDENCE_REGISTER.md` | **Historical** | Describes evidence gathered during initial audit; now superseded by this audit. Keep as historical. |
| `CRITICAL_FILES.md` | **Partially Implemented** | File list is mostly still valid but may reference deprecated paths. |
| `DEPENDENCY_GRAPH.md` | **Partially Implemented** | Dependency relationships broadly hold but some edges (e.g., TensionManager→ODE, TensionServerTick→tickChunkTension) are wrong. |
| `PHASE_1_SYSTEM_DISCOVERY.md` | **Historical** | Original discovery document. Describes pre-refactor system. |
| `PHASE_1_SYSTEM_STATE_MAP.md` | **Historical** | Pre-refactor system state map. |
| `DYNAMICS_AND_STABILITY.md` | **Incorrect** | Stability analysis references old ODE. Full replacement needed. |
| `SPATIAL_FIELD_ANALYSIS.md` | **Incorrect** | References old diffusion PDE. Needs full replacement. |
| `CONTROL_AND_INTERVENTION.md` | **Partially Implemented** | Intervention hooks may still be valid; equations referenced are not. |
| `SYSTEM_OVERVIEW.md` | **Partially Implemented** | High-level overview broadly valid; equation references are wrong. |
| `PIPELINE_TRACE.md` | **Incorrect** | Traces old pipeline through tickChunkTension. Needs replacement. |
| `DISTRIBUTED_COUPLING_ANALYSIS.md` | **Incorrect** | Coupling analysis references old G↔L ODE coupling. Actual coupling is substrate transport via SubstrateTransport. |
| `LOGGER_AUDIT.md` | **Partially Implemented** | Logger audit details may still be valid. |
| `PROJECT_COMPLETE_REFERENCE.md` | **Incorrect** | Giant reference doc for old system. Do not update — archive. |
| `TUNING_SURFACE.md` | **Incorrect** | Tuning references old parameters (D, alpha, beta, gamma, lambda). Actual tunable surface is ParameterSet (v2TransportGain, v2ActivationDecay, memoryFormationGain, etc.). |
| `SESSION_EXPORT_SYSTEM.md` | **Partially Implemented** | Export system described may still be valid for telemetry. |
| `TEST_HARNESS.md` | **Partially Implemented** | Test harness references may be outdated. |
| `EXTENSION_POINTS.md` | **Partially Implemented** | Extension point list needs review. |
| `INTEGRATION_GUIDE.md` | **Partially Implemented** | Integration guide may reference old API. |
| `PERCEPTUAL_SYNTHESIS_SUMMARY.md` | **Implemented** | Perceptual layer description appears valid. |
| `PERCEPTUAL_CALIBRATION_GUIDE.md` | **Implemented** | Calibration guide for audio/visual. Valid. |
| `README_PERCEPTUAL_SYNTHESIS.md` | **Implemented** | Valid. |
| `IMPLEMENTATION_MAP.md` | **Partially Implemented** | File map may reference deprecated class paths. |

---

### docs/implementation/

| File | Verdict | Issue |
|------|---------|-------|
| `SUBSTRATE_IMPLEMENTATION_LEDGER.md` | **Implemented** | Accurate short ledger. No false claims. |
| `ECOLOGICAL_STABILITY_IMPLEMENTATION.md` | **Partially Implemented** | Describes correct high-level behavior but contains incorrect paths: `com/utdmod/events/TensionEvents.java` (actual: `com/utdmod/event/TensionEvents.java`), `com/utdmod/manager/StormManager.java` (actual: `com/utdmod/storm/StormManager.java`). "Mathematical Verification" section claims legacy tension equation `dT/dt = -λT + α·L + (β·T³/(1+β·T²)) + γ·G` is "UNTOUCHED" — this equation does not exist in current code. Mark that section as Historical. |
| `IMPLEMENTATION_COMPLETE.md` | **Historical** | Describes a temporary debug build: 25× mining multipliers, T1=0.08, T2=0.18. These were explicitly intended for reversion. The document must be labeled as historical and must not be read as describing the current production system. |
| `GAMEPLAY_INTERPRETER_IMPLEMENTATION_REPORT.md` | **Historical / Misplaced** | Describes work done in `simulation_lab/`, not the Minecraft mod. References `/home/lenovo/Git/new_world/simulation_lab/src/` paths. Move to `docs/research/` and label as simulation lab work. |

---

### docs/audits/

| File | Verdict | Issue |
|------|---------|-------|
| `CANONICAL_SUBSTRATE_AUDIT.md` | **Partially Implemented** | The port-status table and dependency graph remain broadly accurate. The "Memory update divergence" claim (linear vs saturating form) is **outdated**: the actual Minecraft runtime uses `FieldDerivative.computeUnified()` which implements a complex nonlinear memory formation equation (logistic saturation, depth retention, anti-diffusion, spatial sharpening). Update this claim. |
| `SUBSTRATE_PORT_AUDIT.md` | **Partially Implemented** | Broader port audit. Likely overlaps CANONICAL_SUBSTRATE_AUDIT. Should be merged. |

---

### docs/reports/

| File | Verdict | Issue |
|------|---------|-------|
| `SUBSTRATE_PORT_REPORT.md` | **Implemented** | Short accurate report on port completion. |
| `SUBSTRATE_LAB_STATUS.md` | **Historical — simulation lab** | This is a simulation lab status report, not a Minecraft runtime report. Must be labeled as such at the top of the file. |
| `WORLD_VALIDATION_FINAL_SUMMARY.md` | **Historical — simulation lab** | Describes simulation_lab world validation runs. Must be labeled at top. |
| `WORLD_EVOLUTION_VERDICT.md` | **Historical — simulation lab** | Simulation lab verdict on world evolution dynamics. Must be labeled at top. |
| `READINESS_CHARACTERIZATION_SUMMARY.md` | **Historical — simulation lab** | Simulation lab readiness sweep data. Must be labeled at top. |
| `READINESS_PARAMETER_QUICK_REFERENCE.txt` | **Historical — simulation lab** | Parameter reference from lab sweep. Must be labeled at top. |

---

### docs/testing/

| File | Verdict | Issue |
|------|---------|-------|
| `DEBUG_BUILD_SUMMARY.md` | **Historical** | Describes temporary debug build (25× mining, T1=0.08). Must be labeled historical. |
| `DEBUG_READY.md` | **Historical** | Same debug build. Must be labeled historical. |
| `QUICK_DEBUG_CHECKLIST.md` | **Historical** | Same debug build. Must be labeled historical. |
| `DEBUG_TESTING_GUIDE.md` | **Historical** | Same debug build. Must be labeled historical. |
| `REVERT_DEBUG_CHANGES.md` | **Historical** | Revert instructions for debug build. Must be labeled historical. |
| `DEBUG_DOCS_INDEX.md` | **Historical** | Index of debug docs. Must be labeled historical. |

---

### docs/gameplay/

| File | Verdict | Issue |
|------|---------|-------|
| `PLAYER_EXPERIENCE_SNAPSHOT.md` | **Historical — simulation lab** | Describes simulation_lab gameplay archetypes (miner, explorer, builder, trader) from lab runs, not Minecraft runtime observations. Must be labeled as simulation lab data. |

---

### docs/research/

| File | Verdict | Issue |
|------|---------|-------|
| `RESEARCH_PLAYER_VALIDATION.md` | **Implemented** | Describes the in-Minecraft automated research player system (ResearchBotManager, Baritone integration). Broadly accurate. |

---

### docs/ root

| File | Verdict | Issue |
|------|---------|-------|
| `AGENT_PROTOCOL.md` | **Internal** | Agent coordination protocol. Not technical docs. No changes needed. |

---

## B. Files to Delete

None are recommended for hard deletion. The pre-refactor material has historical value as a record of the system's evolution. However, all of the following should be moved to `docs/archive/` and labeled **[HISTORICAL — PRE-REFACTOR]** at the top:

- `docs/architecture_audit/EXTRACTED_EQUATIONS.md`
- `docs/architecture_audit/PHASE_1_EXTRACTED_EQUATIONS.md`
- `docs/architecture_audit/PHASE_1_SYSTEM_DISCOVERY.md`
- `docs/architecture_audit/PHASE_1_SYSTEM_STATE_MAP.md`
- `docs/architecture_audit/PROJECT_COMPLETE_REFERENCE.md`
- `docs/architecture_audit/DYNAMICS_AND_STABILITY.md`
- `docs/architecture_audit/SPATIAL_FIELD_ANALYSIS.md`
- `docs/architecture_audit/PIPELINE_TRACE.md`
- `docs/architecture_audit/DISTRIBUTED_COUPLING_ANALYSIS.md`

The following should stay in place but receive a `> **[HISTORICAL — DEBUG BUILD — 2026-05-24]**` banner:

- All files under `docs/testing/`

---

## C. Files to Merge

- `docs/audits/CANONICAL_SUBSTRATE_AUDIT.md` + `docs/audits/SUBSTRATE_PORT_AUDIT.md` → merge into single `docs/audits/SUBSTRATE_AUDIT.md` after update.

---

## D. Files to Move

- `docs/implementation/GAMEPLAY_INTERPRETER_IMPLEMENTATION_REPORT.md` → `docs/research/SIMULATION_LAB_INTERPRETER_REPORT.md`
- `docs/reports/SUBSTRATE_LAB_STATUS.md` → `docs/research/lab/SUBSTRATE_LAB_STATUS.md`
- `docs/reports/WORLD_VALIDATION_FINAL_SUMMARY.md` → `docs/research/lab/WORLD_VALIDATION_FINAL_SUMMARY.md`
- `docs/reports/WORLD_EVOLUTION_VERDICT.md` → `docs/research/lab/WORLD_EVOLUTION_VERDICT.md`
- `docs/reports/READINESS_CHARACTERIZATION_SUMMARY.md` → `docs/research/lab/READINESS_CHARACTERIZATION_SUMMARY.md`
- `docs/gameplay/PLAYER_EXPERIENCE_SNAPSHOT.md` → `docs/research/lab/PLAYER_EXPERIENCE_SNAPSHOT.md`

---

## E. Files to Rewrite (see section C of deliverables)

The following files are rewritten in full and placed at their current paths:

- `docs/architecture_audit/EQUATIONS.md` — replaced with verified current equations
- `docs/architecture_audit/00_EXECUTION_PIPELINE.md` — updated framing
- `docs/architecture_audit/04_GLOBAL_SYSTEM.md` — replaced with derived-metric description
- `docs/architecture_audit/02_LOCAL_FIELD.md` — replaced with substrate/FieldDerivative description
- `docs/architecture_audit/03_REGION_SYSTEM.md` — minor framing correction
- `docs/architecture_audit/STATE_MACHINE.md` — corrected thresholds for global storm and corruption
- `docs/audits/CANONICAL_SUBSTRATE_AUDIT.md` — updated memory equation status
- `docs/implementation/ECOLOGICAL_STABILITY_IMPLEMENTATION.md` — correct package paths; mark math section historical
- `docs/implementation/IMPLEMENTATION_COMPLETE.md` — prepend historical banner

---

## F. High-Confidence Correct Files (no changes needed)

- `docs/architecture_audit/05_STORM_SYSTEM.md`
- `docs/architecture_audit/06_ECOLOGY.md`
- `docs/architecture_audit/07_NETWORKING.md`
- `docs/architecture_audit/08_PRESENTATION.md`
- `docs/architecture_audit/PERCEPTUAL_SYNTHESIS_SUMMARY.md`
- `docs/architecture_audit/PERCEPTUAL_CALIBRATION_GUIDE.md`
- `docs/architecture_audit/README_PERCEPTUAL_SYNTHESIS.md`
- `docs/implementation/SUBSTRATE_IMPLEMENTATION_LEDGER.md`
- `docs/reports/SUBSTRATE_PORT_REPORT.md`
- `docs/research/RESEARCH_PLAYER_VALIDATION.md`
- `analysis/UTD_TELEMETRY_REPORT.md` (accurate; not under docs/ but referenced by docs)
