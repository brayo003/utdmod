# Archive — Pre-Refactor Documentation

> **These documents describe a prior version of the UTD Mod tension system.**
>
> The files in this directory were accurate when written but describe a system that no longer
> exists in the current codebase. Do not use them as a reference for the current implementation.

---

## What was the old system?

The pre-refactor system used two coupled scalar ODEs:

**Global tension (G):**
```
G += -λ·G + α·G² + I      (λ=0.0032, α=0.002)
```

**Local chunk tension (L):**
```
L += D·L̄_nbr + α·L² - β·L³ + γ·G - λ·L·m   (D=0.012, α=0.009, β=0.055, γ=0.045, λ=0.0006)
```

Both equations were run as explicit update rules. Global tension was an independent dynamical
variable owned by `TensionManager`. Local tension was stored per-chunk in `ChunkTensionData`
as a scalar `double`.

---

## What replaced it?

The current system is a 4-variable substrate field per chunk:

- **State:** activation (A), memory (M), field depth (D), baseline (B) per `SubstrateState`
- **Update:** `FieldDerivative.computeUnified()` — a coupled nonlinear Euler step
- **Tension:** derived metric T(x) = `SubstrateObservationModel.computeLocalTension()` — not stored
- **Global:** spatial mean of T(x) recomputed from loaded chunks each tick by `TensionManager.recompute()`

See `docs/architecture_audit/EQUATIONS.md` for the current equations.

---

## Files in this archive

| File | What it described |
|---|---|
| `EXTRACTED_EQUATIONS.md` | Complete pre-refactor equation set with parameter values |
| `PHASE_1_EXTRACTED_EQUATIONS.md` | Earlier version of the same |
| `PHASE_1_SYSTEM_DISCOVERY.md` | Initial reverse-engineering of the old system |
| `PHASE_1_SYSTEM_STATE_MAP.md` | State map for the old system |
| `PROJECT_COMPLETE_REFERENCE.md` | Giant consolidated reference for the old system |
| `DYNAMICS_AND_STABILITY.md` | Stability analysis of the old scalar ODEs |
| `SPATIAL_FIELD_ANALYSIS.md` | Diffusion analysis using old PDE parameters |
| `PIPELINE_TRACE.md` | Traces the old pipeline through `tickChunkTension()` |
| `DISTRIBUTED_COUPLING_ANALYSIS.md` | Coupling analysis of the old G↔L ODE pair |
