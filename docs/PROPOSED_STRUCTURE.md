# Proposed Documentation Structure

**Principle:** Every file that can be read by an external engineer must contain only statements verifiable against the current source code. Historical and lab-specific material is preserved but clearly labeled.

```
docs/
├── README.md                        [NEW] top-level index
│
├── architecture/                    [canonical runtime docs]
│   ├── OVERVIEW.md                  [replaces SYSTEM_OVERVIEW + SUMMARY]
│   ├── EXECUTION_PIPELINE.md        [replaces 00_EXECUTION_PIPELINE]
│   ├── EQUATIONS.md                 [replaces EQUATIONS + EXTRACTED_EQUATIONS — REWRITTEN]
│   ├── STATE_MACHINES.md            [replaces STATE_MACHINE — CORRECTED]
│   ├── PARAMETERS.md                [replaces PARAMETER_CATALOG — REWRITTEN]
│   ├── 01_EVENT_INJECTION.md        [keep, minor update]
│   ├── 02_LOCAL_FIELD.md            [REWRITTEN for substrate]
│   ├── 03_REGION_SYSTEM.md          [keep, minor update]
│   ├── 04_GLOBAL_SYSTEM.md          [REWRITTEN for derived metric]
│   ├── 05_STORM_SYSTEM.md           [keep, accurate]
│   ├── 06_ECOLOGY.md                [keep, accurate]
│   ├── 07_NETWORKING.md             [keep, accurate]
│   ├── 08_PRESENTATION.md           [keep, accurate]
│   ├── 09_INVARIANTS.md             [review]
│   ├── 10_FAILURE_MODES.md          [review]
│   └── 11_PERFORMANCE.md            [review]
│
├── implementation/
│   ├── SUBSTRATE_IMPLEMENTATION_LEDGER.md   [keep, accurate]
│   └── ECOLOGICAL_STABILITY_IMPLEMENTATION.md [corrected paths + historical math banner]
│
├── audits/
│   └── SUBSTRATE_AUDIT.md           [merge of CANONICAL + PORT audits, updated]
│
├── reports/
│   └── SUBSTRATE_PORT_REPORT.md     [keep, accurate]
│
├── research/                        [research tooling + live validation]
│   ├── RESEARCH_PLAYER_VALIDATION.md [keep, accurate]
│   ├── TELEMETRY_REPORT.md          [symlink or copy of analysis/UTD_TELEMETRY_REPORT.md]
│   └── lab/                         [simulation_lab work, clearly labeled]
│       ├── README.md                [explains this is simulation_lab output, not Minecraft runtime]
│       ├── SUBSTRATE_LAB_STATUS.md
│       ├── WORLD_VALIDATION_FINAL_SUMMARY.md
│       ├── WORLD_EVOLUTION_VERDICT.md
│       ├── READINESS_CHARACTERIZATION_SUMMARY.md
│       ├── PLAYER_EXPERIENCE_SNAPSHOT.md
│       └── SIMULATION_LAB_INTERPRETER_REPORT.md
│
├── gameplay/                        [player-facing documentation]
│   └── (future player guides go here)
│
├── testing/                         [current test docs; all historical]
│   └── [all existing files + historical banner]
│
└── archive/                         [pre-refactor material, clearly labeled]
    ├── README.md                    [explains these describe the old ODE system]
    ├── EXTRACTED_EQUATIONS.md
    ├── PHASE_1_EXTRACTED_EQUATIONS.md
    ├── PHASE_1_SYSTEM_DISCOVERY.md
    ├── PHASE_1_SYSTEM_STATE_MAP.md
    ├── PROJECT_COMPLETE_REFERENCE.md
    ├── DYNAMICS_AND_STABILITY.md
    ├── SPATIAL_FIELD_ANALYSIS.md
    ├── PIPELINE_TRACE.md
    └── DISTRIBUTED_COUPLING_ANALYSIS.md
```

## What changed from the current layout

| Current location | New location | Reason |
|---|---|---|
| `architecture_audit/EQUATIONS.md` | `architecture/EQUATIONS.md` | Rewritten — old ODE equations replaced with current substrate equations |
| `architecture_audit/PARAMETER_CATALOG.md` | `architecture/PARAMETERS.md` | Rewritten — old parameters removed, ParameterSet defaults documented |
| `architecture_audit/STATE_MACHINE.md` | `architecture/STATE_MACHINES.md` | Corrected — global storm onset/corruption thresholds fixed |
| `architecture_audit/EXTRACTED_EQUATIONS.md` | `archive/EXTRACTED_EQUATIONS.md` | Incorrect against current code; preserved as historical |
| `architecture_audit/PHASE_1_*.md` | `archive/` | Pre-refactor discovery material |
| `architecture_audit/PROJECT_COMPLETE_REFERENCE.md` | `archive/` | Giant reference for old system |
| `architecture_audit/DYNAMICS_AND_STABILITY.md` | `archive/` | Old ODE stability analysis |
| `architecture_audit/SPATIAL_FIELD_ANALYSIS.md` | `archive/` | Old diffusion PDE analysis |
| `architecture_audit/PIPELINE_TRACE.md` | `archive/` | Traces old pipeline through deleted methods |
| `architecture_audit/DISTRIBUTED_COUPLING_ANALYSIS.md` | `archive/` | Old G↔L ODE coupling |
| `implementation/IMPLEMENTATION_COMPLETE.md` | stays + historical banner | Debug build record |
| `implementation/GAMEPLAY_INTERPRETER_IMPLEMENTATION_REPORT.md` | `research/lab/` | simulation_lab work, not Minecraft runtime |
| `reports/SUBSTRATE_LAB_STATUS.md` | `research/lab/` | simulation_lab output |
| `reports/WORLD_VALIDATION_FINAL_SUMMARY.md` | `research/lab/` | simulation_lab output |
| `reports/WORLD_EVOLUTION_VERDICT.md` | `research/lab/` | simulation_lab output |
| `reports/READINESS_CHARACTERIZATION_SUMMARY.md` | `research/lab/` | simulation_lab output |
| `gameplay/PLAYER_EXPERIENCE_SNAPSHOT.md` | `research/lab/` | simulation_lab gameplay archetypes |
| `audits/CANONICAL_SUBSTRATE_AUDIT.md` + `SUBSTRATE_PORT_AUDIT.md` | `audits/SUBSTRATE_AUDIT.md` | merged, updated |
