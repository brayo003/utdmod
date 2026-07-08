# Responsibility & Dependency Matrix

## 1. System Dependencies and Provisions Graph
This matrix details the explicit data and structural lines of dependency across the laboratory council. No agent may assume data inputs or cross-functional execution layers outside of these declared paths.

### Substrate Research Engineer
* **Depends on:** `Scientific Auditor` (for discrepancy targets), `Experiment Designer` (for boundary constraints).
* **Provides:** Mathematical fidelity, core field matrices, active simulation loop tick tracking, and perturbation entry hooks.

### Visualization Engineer
* **Depends on:** `Substrate Research Engineer` (requires direct access to the live mathematical arrays and tick state variables).
* **Provides:** Real-time visual access to substrate fields, overlays, microscope viewports, and on-screen HUD counters.

### Instrumentation Engineer
* **Depends on:** `Substrate Research Engineer` (requires access to cell state updates and variables for logging/snapshots).
* **Provides:** State logs, structured dataset files (CSV/JSON), binary time-slice snapshots, deterministic replays, and video processing hooks.

### Experiment Designer
* **Depends on:** `Scientific Auditor` (for validation metrics and logs of emergent world behavior histories).
* **Provides:** Test hypotheses, bot profiles, isolation constraints, and scheduled perturbation timelines.

### Scientific Auditor
* **Depends on:** `Nobody` (Operates independently to inspect absolute repository state).
* **Provides:** Repository truth, verification checks, discrepancy identification, and ledger management.

---

## 2. Subsystem Ownership
Only the designated owner may write code, manage configurations, or edit files within their assigned subsystems.

| Subsystem Component | Sole Owning Agent | Primary Responsibility |
| :--- | :--- | :--- |
| **Mathematics & State Variables** | Substrate Research Engineer | Authoritative equations, field calculations, matrix values |
| **Runtime Implementation** | Substrate Research Engineer | Game loop integration, tick synchronization, block updates |
| **Perturbation Integration** | Substrate Research Engineer | Mapping Minecraft events (mining, explosions) to the field |
| **HUD & On-Screen UI** | Visualization Engineer | In-game information rendering, text layouts, status updates |
| **World Rendering & Wireframes** | Visualization Engineer | Chunk boundary visualization, field topology meshes |
| **Particles & Heatmaps** | Visualization Engineer | Visual field density, particle clouds, block tint shifts |
| **Beacons & Transport Arrows** | Visualization Engineer | Directed vectors, flow paths, point-source beacon beams |
| **Microscope Viewports (F7-F10)**| Visualization Engineer | Contextual hotkey visualization overlays |
| **Telemetry & Log Files** | Instrumentation Engineer | Writing clean data streams to disk |
| **Data Formats (CSV / JSON)** | Instrumentation Engineer | Output serialization, structural accuracy, file formatting |
| **Binary Snapshots & State Replay**| Instrumentation Engineer | High-speed serialization of field states for playback |
| **Profiling & Performance Stats** | Instrumentation Engineer | Monitoring loop execution speeds, memory usage, tick rate drops |
| **FFmpeg & Video Export** | Instrumentation Engineer | Compiling captured frames into scientific video records |
| **Hypothesis & Experiment Setup** | Experiment Designer | Structuring scientific tests, parameters, control environments |
| **Bot Personas & Behaviors** | Experiment Designer | Outlining bot task profiles (Miner, Builder, Explorer) |
| **Perturbation Scheduling** | Experiment Designer | Timeline planning for external world inputs |
| **Data Interpretation Guidelines** | Experiment Designer | Clarifying differences between observations and conclusions |
| **SUBSTRATE_PORT_AUDIT.md** | Scientific Auditor | Tracking missing math or unmapped state variables |
| **SUBSTRATE_IMPLEMENTATION_LEDGER.md** | Scientific Auditor | Log of fully verified, operational codebase layers |
| **WORLD_EVOLUTION_STATUS.md** | Scientific Auditor | Summary of observed emergent behaviors vs. equations |

