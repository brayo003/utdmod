"""
Stage 1: Stream experiment.jsonl files and build reduced dataframes.
Outputs: analysis/data/run{N}_substrate.parquet, run{N}_interp.parquet, run{N}_field_summary.parquet
"""
import json, os, sys
import pandas as pd

BASE = "/home/lenovo/Git/new_world/run/logs/utd_experiments"
OUT  = "/home/lenovo/Git/new_world/analysis/data"
os.makedirs(OUT, exist_ok=True)

RUNS = [
    ("2026-07-15T14-08-14", 1, "Idle/Baseline"),
    ("2026-07-15T14-14-14", 2, "Small Perturbation"),
    ("2026-07-15T14-18-49", 3, "Large Perturbation"),
    ("2026-07-15T14-26-10", 4, "Relaxation"),
]

SUBSTRATE_FIELDS = [
    "tick", "chunk_x", "chunk_z",
    "activation", "memory", "transport", "field_depth",
    "readiness", "radius", "capacity", "peak_activation",
    "convergence", "baseline", "external_input",
]

INTERP_FIELDS = [
    "tick", "chunk_x", "chunk_z",
    "ecological_stress", "environmental_stability",
    "migration_pressure", "corridor_strength",
    "prosperity_potential", "recovery_state",
    "collapse_tendency", "local_gradient",
    "local_density", "transport_bias_x", "transport_bias_z",
]

for run_id, run_num, label in RUNS:
    path = os.path.join(BASE, run_id, "experiment.jsonl")
    print(f"\n[Run {run_num}] {label}  ({run_id})")
    print(f"  File: {path}  ({os.path.getsize(path)/1e6:.1f} MB)")

    snap_rows   = []
    interp_rows = []
    field_rows  = []
    region_rows = []

    with open(path, "r") as fh:
        for raw in fh:
            raw = raw.strip()
            if not raw or raw.startswith("="):
                continue
            try:
                d = json.loads(raw)
            except json.JSONDecodeError:
                continue

            ev = d.get("event")

            if ev == "substrate_snapshot":
                chunk = d.get("chunk", {})
                snap_rows.append({
                    "tick":           d.get("tick"),
                    "chunk_x":        chunk.get("x"),
                    "chunk_z":        chunk.get("z"),
                    "activation":     d.get("activation", 0.0),
                    "memory":         d.get("memory", 0.0),
                    "transport":      d.get("transport", 0.0),
                    "field_depth":    d.get("field_depth", 0.0),
                    "readiness":      d.get("readiness", 1.0),
                    "radius":         d.get("radius", 1.0),
                    "capacity":       d.get("capacity", 1.0),
                    "peak_activation":d.get("peak_activation", 0.0),
                    "convergence":    d.get("convergence", 0.0),
                    "baseline":       d.get("baseline", 0.0),
                    "external_input": d.get("external_input", 0.0),
                })

            elif ev == "interpretation_snapshot":
                chunk = d.get("chunk", {})
                interp_rows.append({
                    "tick":                   d.get("tick"),
                    "chunk_x":                chunk.get("x"),
                    "chunk_z":                chunk.get("z"),
                    "ecological_stress":      d.get("ecological_stress", 0.0),
                    "environmental_stability":d.get("environmental_stability", 0.0),
                    "migration_pressure":     d.get("migration_pressure", 0.0),
                    "corridor_strength":      d.get("corridor_strength", 0.0),
                    "prosperity_potential":   d.get("prosperity_potential", 0.0),
                    "recovery_state":         d.get("recovery_state", 1.0),
                    "collapse_tendency":      d.get("collapse_tendency", 0.0),
                    "local_gradient":         d.get("local_gradient", 0.0),
                    "local_density":          d.get("local_density", 0.0),
                    "transport_bias_x":       d.get("transport_bias_x", 0.0),
                    "transport_bias_z":       d.get("transport_bias_z", 0.0),
                })

            elif ev == "field_summary":
                tick = d.get("tick")
                field_rows.append({
                    "tick":                     tick,
                    "region_count":             d.get("region_count", 0),
                    "stable_regions":           d.get("stable_regions", 0),
                    "recovering_regions":       d.get("recovering_regions", 0),
                    "activation_mean":          d.get("activation_mean", 0.0),
                    "activation_std":           d.get("activation_std", 0.0),
                    "memory_mean":              d.get("memory_mean", 0.0),
                    "capacity_mean":            d.get("capacity_mean", 1.0),
                    "readiness_mean":           d.get("readiness_mean", 1.0),
                    "interpretation_mean_stress":    d.get("interpretation_mean_stress", 0.0),
                    "interpretation_mean_stability": d.get("interpretation_mean_stability", 0.0),
                    "interpretation_mean_corridor":  d.get("interpretation_mean_corridor", 0.0),
                })
                # Per-region rows
                for reg in d.get("regions", []):
                    region_rows.append({
                        "tick":             tick,
                        "region_id":        reg.get("region_id"),
                        "chunk_count":      reg.get("chunk_count"),
                        "state":            reg.get("state"),
                        "mean_tension":     reg.get("mean_tension", 0.0),
                        "max_tension":      reg.get("max_tension", 0.0),
                        "variance_tension": reg.get("variance_tension", 0.0),
                        "active_fraction":  reg.get("active_fraction", 0.0),
                        "mean_depth":       reg.get("mean_depth", 0.0),
                        "mean_memory":      reg.get("mean_memory", 0.0),
                        "mean_activation":  reg.get("mean_activation", 0.0),
                        "max_activation":   reg.get("max_activation", 0.0),
                        "average_maturity": reg.get("average_maturity", 0.0),
                        "fractured_chunks": reg.get("fractured_chunks", 0),
                        "storm_chunks":     reg.get("storm_chunks", 0),
                        "centroid_x":       reg.get("centroid", {}).get("x", 0.0),
                        "centroid_z":       reg.get("centroid", {}).get("z", 0.0),
                    })

    # Build DataFrames
    df_snap   = pd.DataFrame(snap_rows)
    df_interp = pd.DataFrame(interp_rows)
    df_field  = pd.DataFrame(field_rows)
    df_region = pd.DataFrame(region_rows)

    print(f"  substrate_snapshots : {len(df_snap):,} rows")
    print(f"  interp_snapshots    : {len(df_interp):,} rows")
    print(f"  field_summaries     : {len(df_field):,} rows")
    print(f"  region_rows         : {len(df_region):,} rows")

    prefix = f"run{run_num}"
    df_snap.to_parquet(  os.path.join(OUT, f"{prefix}_substrate.parquet"), index=False)
    df_interp.to_parquet(os.path.join(OUT, f"{prefix}_interp.parquet"),   index=False)
    df_field.to_parquet( os.path.join(OUT, f"{prefix}_field.parquet"),    index=False)
    df_region.to_parquet(os.path.join(OUT, f"{prefix}_regions.parquet"),  index=False)

print("\nStage 1 complete. Files written to", OUT)
