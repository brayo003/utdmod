# UTD Mod — Unified Tension Dynamics

**Minecraft 1.20.4** • **Fabric** • **Java 17** • **Research Prototype**

A server-authoritative substrate simulation for Minecraft that enables the world to accumulate memory, organize into persistent spatial regions, and evolve in response to player activity.

Unlike conventional sandbox mechanics that respond only to the current game state, UTD introduces a persistent dynamical field distributed across chunks. Every meaningful player action perturbs this field, producing long-lived spatial structure that can influence gameplay through ecology, weather, entities, resources, and presentation.

The project combines a nonlinear substrate simulation, a gameplay interpretation layer, and a complete telemetry pipeline for analysing field behaviour inside a live Minecraft world.

The theoretical foundation is described in **Substrate X — Theory of Information Gravity**:

https://github.com/brayo003/Substrate-X-Theory-of-Information-Gravity

<p align="center">
  <img src="docs/world_tension_map.png" alt="UTD world tension map" width="860">
</p>

---

# Motivation

Most sandbox worlds respond only to current player actions. Once an event ends, the world rapidly returns to its previous state.

UTD explores an alternative architecture in which the world itself possesses persistent state. Rather than scripting behaviours directly, gameplay systems observe an evolving substrate that accumulates memory, develops spatial organisation, and continuously influences the simulation.

Minecraft serves as the initial implementation and experimental environment for this architecture.

---

# Substrate Model

Each active chunk stores four coupled state variables.

| Variable | Purpose |
|----------|---------|
| **Activation (A)** | Short-term excitation produced by player activity. |
| **Memory (M)** | Persistent history of sustained activation. |
| **Depth (D)** | Long-term structural consolidation of disturbance. |
| **Baseline (B)** | Slowly adapting equilibrium state. |

From these variables the system derives:

- Local tension **T(x)**
- Readiness **R**
- Region maturity
- Global field statistics
- Storm and corruption metrics

The substrate integrates these variables every five server ticks using nonlinear activation dynamics, thresholded memory formation, logistic saturation, depth-dependent retention, spatial transport, diffusion, anti-diffusion sharpening, and adaptive baseline evolution.

Complete equations and parameter documentation are available in:

- `docs/architecture_audit/EQUATIONS.md`
- `docs/architecture_audit/PARAMETER_CATALOG.md`

---

# Architecture

The runtime executes on the server thread through a single `END_SERVER_TICK` handler.

```text
Player Actions
(mining / movement / combat / ritual)
        │
        ▼
Perturbation Injection
        │
        ▼
Substrate Integration
A, M, D, B
        │
        ▼
Interpretation Layer
        │
        ▼
Gameplay Actuators
Weather
Ecology
Resources
Entities
Villages
Particles
        │
        ▼
Chunk Classification
        │
        ▼
Region Reconstruction
        │
        ▼
Global Metrics
        │
        ▼
Client Synchronisation
```

The server is authoritative. Clients never simulate the substrate; they render synchronized state received from the server for HUD, audio, and visual presentation.

---

# Implemented Systems

## Simulation

- Persistent four-state substrate (A, M, D, B)
- Coupled nonlinear field integration
- Inter-chunk transport
- Region reconstruction
- Persistent save/load support

## Gameplay Integration

- Ecology mutations
- Weather coupling
- Spawn modifiers
- Loot modifiers
- Villager behaviour modifiers
- Interpretation layer connecting substrate state to gameplay systems

## Research Infrastructure

- JSONL telemetry pipeline
- Automated experiment player
- Region statistics
- Time-series analysis
- Python analysis toolkit
- Parameter experimentation framework

---

# Experimental Validation

Four controlled Minecraft server experiments were analysed using the integrated telemetry pipeline (42,071 ticks and 67,279 substrate snapshots).

The complete analysis is available in:

`analysis/UTD_TELEMETRY_REPORT.md`

Measured results include:

- **Bistability.** Small perturbations decay naturally, while sufficiently large perturbations move the substrate into a distinct stable attractor.
- **Hysteresis.** After 25,786 ticks of zero external input following a large perturbation run, the field remained in the non-trivial attractor rather than returning to baseline.
- **Autonomous organisation.** Transport variability decreased from 0.73 during externally driven activity to 0.045 during relaxation, indicating self-organised internal dynamics.
- **Stable spatial topology.** Four regions persisted throughout the relaxation experiment with no births, merges, splits, or deletions.
- **Temporal scale hierarchy.** Activation evolves over ticks, memory over hundreds of ticks, and depth over thousands of ticks.

Known implementation investigations are documented in:

`docs/SUBSTRATE_AUDIT.md`

---

# Research Questions

This project investigates whether a persistent nonlinear substrate can:

- produce stable long-term spatial organisation
- retain memory across gameplay sessions
- generate emergent regional behaviour
- support gameplay through continuous dynamics rather than scripted events
- remain computationally practical inside a real-time sandbox game

---

# Repository Layout

| Directory | Purpose |
|-----------|---------|
| `src/` | Minecraft implementation |
| `docs/` | Verified technical documentation |
| `analysis/` | Telemetry extraction, statistics, and plotting |
| `simulation_lab/` | Standalone parameter-characterisation environment |
| `tools/` | Development utilities |

Documentation begins with:

`docs/INDEX.md`

---

# Building

```bash
./gradlew build
```

Requires Java 17.

The compiled mod is produced in:

`build/libs/`

To start a development server:

```bash
./gradlew runServer
```

---

# Current Status

The substrate architecture is implemented and operational.

Current development focuses on:

- expanding gameplay systems
- parameter balancing
- presentation and visualisation
- optimisation
- large-scale evaluation
- external technical review

---

# Collaboration

The project welcomes contributions from several disciplines.

**Minecraft / Fabric developers**

Gameplay integration, UI, networking, entities, and engine integration.

**Game systems engineers**

Designing gameplay systems that respond to substrate state and balancing field parameters.

**Simulation and dynamical systems researchers**

Reviewing equations, analysing parameter space, and evaluating emergent behaviour.

**Artificial life and complex systems researchers**

Studying bistability, hysteresis, spatial self-organisation, and persistent memory.

**Engine programmers**

Performance optimisation, scalability, and deterministic replay.

Areas where technical review is particularly valuable:

- substrate architecture
- dynamical behaviour
- gameplay implications of persistent world memory
- relationship between the standalone simulation and the Minecraft implementation

Issues, pull requests, and technical discussion are welcome.
