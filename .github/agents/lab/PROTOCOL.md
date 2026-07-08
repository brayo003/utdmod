# SXC-IGC Agent Protocol

## Shared Philosophy
Minecraft is the experimental environment. Gameplay exists to perturb the substrate. The substrate exists to produce observable world dynamics. The objective is not to replace gameplay but to study how gameplay and substrate influence one another. Every agent action must prioritize empirical evidence over elegant software abstractions.

## The Execution Workflow (Task Flow)
When implementing features or addressing research goals, agents must execute sequentially according to this pipeline. An agent may not begin work until its preceding dependency has yielded control.

[Scientific Auditor] (Establishes baseline discrepancy)
↓
[Experiment Designer] (Optional: Formulates test hypothesis/bot metrics)
↓
[Substrate Research Engineer] (Ports equations / updates runtime loop)
↓
[Visualization Engineer] (Renders real-time spatial indicators/HUD)
↓
[Instrumentation Engineer] (Generates logs, binary snapshots, and replays)
↓
[Scientific Auditor] (Executes DoD check: signs off or issues Rejection)


## Definition of Done (DoD)
An implementation ticket, subsystem, or mathematical state variable change is considered **Complete** if and only if it meets all the following criteria:
* [ ] **Runtime implementation exists:** Code matches the targeted authoritative equations perfectly.
* [ ] **Runtime executes:** The calculation layer compiles and updates properly inside the game tick loop.
* [ ] **Visualization exists:** The state changes can be visually inspected via heatmaps, overlays, or HUD markers.
* [ ] **Recorder captures it:** Telemetry reliably outputs to structured disk files (CSV/JSON/Snapshot).
* [ ] **Replay reproduces it:** The system can deterministically recreate the exact execution path from a seed.
* [ ] **Auditor verifies it:** The Scientific Auditor manually checks the code footprint and approves the merge.
* [ ] **Ledger updated:** The status changes are formally added to the repository's tracking markdown file.

## Scientific Principles & Determinism
1. Nothing exists scientifically unless it can be reproduced identically.
2. Every code modification, runtime execution path, and file change must preserve total determinism across identical world seeds.
3. Random number generators must be bound strictly to the simulation's deterministic seed tracking system.

## Mathematical Preservation Rules
1. Never rewrite, simplify, or rebalance authoritative laboratory equations.
2. Never merge, skip, or eliminate mathematical state variables.
3. If an equation or state variable cannot be directly integrated into the runtime execution loop, stop immediately and report the mathematical conflict.

## Standard Reporting Format
Every technical implementation or modification task must conclude with this exact structure:
- **Requested Goal:** Description of the assignment.
- **Implemented Changes:** Technical details of what was introduced or modified.
- **Files Modified:** Absolute or relative repository paths of changed files.
- **Runtime Entry Point & Path:** The exact execution path used by the system at runtime.
- **Verification Method:** Empirical proof that the change runs deterministically.
- **Known Limitations:** Edge cases, performance costs, or unverified behaviors.
- **Next Recommended Work:** The logical next step for the engineering team.

