# UTD Mod Documentation Index

**Minecraft 1.20.4 Fabric · DCII Tension System**

This index lists every document under `docs/` with its trust status.
Files marked ✅ are verified against the current source as of 2026-07-15.
Files marked ⚠ contain accurate information mixed with outdated sections (read the inline banners).
Files marked 🗄 are archived/historical and must not be used as a reference for the current system.

Canonical implementation reference:

architecture_audit/EQUATIONS.md and architecture_audit/PARAMETER_CATALOG.md define the current runtime substrate. When historical analysis or older reports disagree with these documents, the current Java implementation and these two documents take precedence.


---

## Start here

| File | Status | Description |
|---|---|---|
| `README.md` (repo root) | ✅ | High-level mod overview |
| `docs/AUDIT_REPORT.md` | ✅ | Full documentation audit (2026-07-15) |
| `docs/PROPOSED_STRUCTURE.md` | ✅ | Proposed clean documentation structure |
| `analysis/UTD_TELEMETRY_REPORT.md` | ✅ | Empirical substrate telemetry from 4 Minecraft runs |

---

## Architecture (current system)

| File | Status | Description |
|---|---|---|
| `architecture_audit/00_EXECUTION_PIPELINE.md` | ✅ | Server tick sequence, substrate integration pipeline |
| `architecture_audit/EQUATIONS.md` | ✅ | All verified current equations |
| `architecture_audit/PARAMETER_CATALOG.md` | ✅ | All ParameterSet defaults and threshold values |
| `architecture_audit/STATE_MACHINE.md` | ✅ | Chunk states, region states, storm flags, corruption tiers |
| `architecture_audit/SUMMARY.md` | ✅ | System overview and framing |
| `architecture_audit/01_EVENT_INJECTION.md` | ✅ | Player event → substrate impulse injection |
| `architecture_audit/02_LOCAL_FIELD.md` | ✅ | Per-chunk substrate state and FieldDerivative update |
| `architecture_audit/03_REGION_SYSTEM.md` | ✅ | Region building from active substrate chunks |
| `architecture_audit/04_GLOBAL_SYSTEM.md` | ✅ | TensionManager as derived metric aggregator |
| `architecture_audit/05_STORM_SYSTEM.md` | ✅ | StormManager, region-based storm selection |
| `architecture_audit/06_ECOLOGY.md` | ✅ | RegionEcologyManager, block mutation |
| `architecture_audit/07_NETWORKING.md` | ✅ | TensionSyncPacket → TensionSyncState |
| `architecture_audit/08_PRESENTATION.md` | ✅ | Client audio/visual presentation layer |
| `architecture_audit/09_INVARIANTS.md` | — | Not re-audited; may contain outdated invariants |
| `architecture_audit/10_FAILURE_MODES.md` | — | Not re-audited; may reference old system |
| `architecture_audit/11_PERFORMANCE.md` | — | Not re-audited; tick cadences may be outdated |

---

## Implementation notes

| File | Status | Description |
|---|---|---|
| `implementation/SUBSTRATE_IMPLEMENTATION_LEDGER.md` | ✅ | Substrate port milestone record |
| `implementation/ECOLOGICAL_STABILITY_IMPLEMENTATION.md` | ⚠ | Ecology changes (accurate); math section is historical (pre-refactor equations) |
| `implementation/IMPLEMENTATION_COMPLETE.md` | 🗄 | Historical debug build (25× mining, T1=0.08) — NOT production |
| `implementation/GAMEPLAY_INTERPRETER_IMPLEMENTATION_REPORT.md` | 🗄 | simulation_lab/ interpreter work — NOT Minecraft runtime |

---

## Audits

| File | Status | Description |
|---|---|---|
| `audits/CANONICAL_SUBSTRATE_AUDIT.md` | ⚠ | Lab↔runtime port audit; memory equation section updated |
| `audits/SUBSTRATE_PORT_AUDIT.md` | — | Overlaps canonical audit; recommend merging |

---

## Reports

| File | Status | Description |
|---|---|---|
| `reports/SUBSTRATE_PORT_REPORT.md` | ✅ | Short accurate port completion report |
| `reports/SUBSTRATE_LAB_STATUS.md` | 🗄 | simulation_lab/ status — NOT Minecraft runtime |
| `reports/WORLD_VALIDATION_FINAL_SUMMARY.md` | 🗄 | simulation_lab/ validation — NOT Minecraft runtime |
| `reports/WORLD_EVOLUTION_VERDICT.md` | 🗄 | simulation_lab/ world evolution — NOT Minecraft runtime |
| `reports/READINESS_CHARACTERIZATION_SUMMARY.md` | 🗄 | simulation_lab/ readiness sweep — NOT Minecraft runtime |
| `reports/READINESS_PARAMETER_QUICK_REFERENCE.txt` | 🗄 | simulation_lab/ lab sweep parameters |

---

## Research

| File | Status | Description |
|---|---|---|
| `research/RESEARCH_PLAYER_VALIDATION.md` | ✅ | In-Minecraft automated research player (ResearchBotManager + Baritone) |

---

## Gameplay

| File | Status | Description |
|---|---|---|
| `gameplay/PLAYER_EXPERIENCE_SNAPSHOT.md` | 🗄 | simulation_lab/ gameplay archetypes — NOT in-game observations |

---

## Testing (historical)

All files under `docs/testing/` describe a temporary debug build and are labeled `[HISTORICAL]`.
They are preserved as a record of the debugging process. Do not use them as build instructions.

| File | Status |
|---|---|
| `testing/DEBUG_DOCS_INDEX.md` | 🗄 historical |
| `testing/DEBUG_BUILD_SUMMARY.md` | 🗄 historical |
| `testing/DEBUG_READY.md` | 🗄 historical |
| `testing/QUICK_DEBUG_CHECKLIST.md` | 🗄 historical |
| `testing/DEBUG_TESTING_GUIDE.md` | 🗄 historical |
| `testing/REVERT_DEBUG_CHANGES.md` | 🗄 historical |

---

## Archive (pre-refactor, do not use)

Files under `docs/archive/` describe the old scalar ODE system.
See `docs/archive/README.md` for context on what was replaced and why.

---

## Key source files (for cross-referencing docs)

| Class | Package | Role |
|---|---|---|
| `TensionManager` | `com.utdmod.core` | Global derived metrics, storm flag, corruption tier |
| `TensionServerTick` | `com.utdmod.core` | Main server tick orchestrator |
| `ChunkTensionData` | `com.utdmod.tension` | Per-chunk substrate storage and integration |
| `SubstrateState` | `com.utdmod.tension` | Per-chunk state (A, M, D, B) |
| `FieldDerivative` | `com.utdmod.tension` | Canonical update equations |
| `SubstrateTransport` | `com.utdmod.tension` | Inter-chunk transport kernel |
| `SubstrateObservationModel` | `com.utdmod.tension` | T(x) and other derived metrics |
| `ParameterSet` | `com.utdmod.tension` | All numerical parameters |
| `RegionManager` | `com.utdmod.core` | Region building from active chunks |
| `Region` | `com.utdmod.core` | Region state and statistics |
| `RegionState` | `com.utdmod.core` | DORMANT / GROWING / STRAINED / FRACTURED / ANCIENT |
| `StormManager` | `com.utdmod.storm` | Region-based storm logic |
| `RegionEcologyManager` | `com.utdmod.ecology` | Block mutation from region state |
| `TensionSyncPacket` | `com.utdmod.network` | Server → client sync |
| `TensionSyncState` | `com.utdmod.client` | Client mirror (never runs simulation) |
| `TensionEvents` | `com.utdmod.event` | Fabric event hooks → impulse injection |
| `UTDConfig` | `com.utdmod.config` | Runtime config overrides |
