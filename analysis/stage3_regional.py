"""
Stage 3: Regional dynamics analysis from field_summary data.
Computes: region count, largest region, mean maturity, centroid drift,
          state transitions, per-region trajectories.
Outputs: analysis/data/run{N}_regional_stats.json
"""
import os, json
import numpy as np
import pandas as pd
from scipy.stats import linregress

DATA = "/home/lenovo/Git/new_world/analysis/data"

RUNS = [
    (1, "Idle/Baseline"),
    (2, "Small Perturbation"),
    (3, "Large Perturbation"),
    (4, "Relaxation"),
]

all_regional = {}

for run_num, label in RUNS:
    print(f"\n[Run {run_num}] {label}")
    prefix = f"run{run_num}"

    df_field  = pd.read_parquet(os.path.join(DATA, f"{prefix}_field.parquet"))
    df_region = pd.read_parquet(os.path.join(DATA, f"{prefix}_regions.parquet"))

    stats = {}

    # ---- Field-level time series ----
    if len(df_field) > 0:
        df_field = df_field.sort_values("tick")
        print(f"  field_summary ticks: {df_field['tick'].tolist()}")
        print(f"  region_count:    {df_field['region_count'].tolist()}")
        print(f"  activation_mean: {[round(x,4) for x in df_field['activation_mean'].tolist()]}")
        print(f"  memory_mean:     {[round(x,4) for x in df_field['memory_mean'].tolist()]}")

        stats["field_ticks"]            = df_field["tick"].tolist()
        stats["region_count_series"]    = df_field["region_count"].tolist()
        stats["activation_mean_series"] = df_field["activation_mean"].tolist()
        stats["memory_mean_series"]     = df_field["memory_mean"].tolist()
        stats["stability_series"]       = df_field["interpretation_mean_stability"].tolist()
        stats["stress_series"]          = df_field["interpretation_mean_stress"].tolist()

    # ---- Per-region trajectories ----
    if len(df_region) > 0:
        df_region = df_region.sort_values(["region_id", "tick"])
        region_ids = sorted(df_region["region_id"].unique())
        print(f"  regions: {region_ids}")

        per_region = {}
        for rid in region_ids:
            rid = int(rid)  # ensure plain Python int
            sub = df_region[df_region["region_id"] == rid].sort_values("tick")
            ticks = sub["tick"].tolist()

            # Centroid drift
            cx_arr = sub["centroid_x"].values
            cz_arr = sub["centroid_z"].values
            drift = float(np.sqrt(np.sum(np.diff(cx_arr)**2 + np.diff(cz_arr)**2))) if len(cx_arr) > 1 else 0.0

            # State transitions
            states = sub["state"].tolist()
            transitions = sum(1 for i in range(1,len(states)) if states[i] != states[i-1])

            # Maturity trajectory slope
            mat = sub["average_maturity"].values
            if len(mat) > 2:
                sl, _, r, p, _ = linregress(np.arange(len(mat)), mat)
            else:
                sl, r, p = 0.0, 0.0, 1.0

            per_region[rid] = {
                "tick_range":        [ticks[0], ticks[-1]],
                "tick_count":        len(ticks),
                "chunk_count_final": int(sub["chunk_count"].iloc[-1]),
                "states":            states,
                "state_transitions": transitions,
                "centroid_x_series": [round(x,2) for x in cx_arr.tolist()],
                "centroid_z_series": [round(z,2) for z in cz_arr.tolist()],
                "centroid_drift":    round(drift, 4),
                "maturity_series":   [round(m,4) for m in mat.tolist()],
                "maturity_slope":    round(sl, 6),
                "maturity_r2":       round(r**2, 4),
                "depth_series":      [round(d,4) for d in sub["mean_depth"].tolist()],
                "memory_series":     [round(m,4) for m in sub["mean_memory"].tolist()],
                "activation_series": [round(a,4) for a in sub["mean_activation"].tolist()],
            }
            print(f"    Region {rid}: ticks={ticks[0]}-{ticks[-1]}  "
                  f"states={states}  drift={drift:.3f}  "
                  f"maturity_slope={sl:.4f}  final_maturity={mat[-1]:.4f}")

        stats["per_region"] = per_region

        # Largest region by chunk_count at last tick
        last_tick = df_region["tick"].max()
        last_snap = df_region[df_region["tick"] == last_tick]
        if len(last_snap) > 0:
            largest = last_snap.loc[last_snap["chunk_count"].idxmax()]
            stats["largest_region_final"] = {
                "region_id":   int(largest["region_id"]),
                "chunk_count": int(largest["chunk_count"]),
                "mean_depth":  round(float(largest["mean_depth"]), 4),
                "mean_activation": round(float(largest["mean_activation"]), 4),
            }
            print(f"  Largest region at tick {last_tick}: R{int(largest['region_id'])}  "
                  f"chunks={int(largest['chunk_count'])}")

        # Persistence: all 4 regions present throughout?
        if len(region_ids) > 0:
            ticks_per_region = {rid: df_region[df_region["region_id"]==rid]["tick"].nunique()
                                for rid in region_ids}
            total_ticks = df_field["tick"].nunique() if len(df_field) > 0 else 1
            persistence = {str(rid): round(cnt/max(total_ticks,1),3)
                           for rid, cnt in ticks_per_region.items()}
            stats["region_persistence_fraction"] = persistence
            print(f"  Region persistence (fraction of summaries): {persistence}")
    else:
        print("  No region data.")
        stats["per_region"] = {}

    all_regional[run_num] = stats

    with open(os.path.join(DATA, f"{prefix}_regional_stats.json"), "w") as fh:
        json.dump(stats, fh, indent=2, default=str)

with open(os.path.join(DATA, "all_regional.json"), "w") as fh:
    json.dump({str(k): v for k, v in all_regional.items()}, fh, indent=2, default=str)

print("\nStage 3 complete.")
