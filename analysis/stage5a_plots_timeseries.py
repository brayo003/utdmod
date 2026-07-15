"""
Stage 5a: Per-run time series plots.
Generates: activation, memory, field_depth, transport, convergence per run.
"""
import os
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.gridspec as gridspec

DATA  = "/home/lenovo/Git/new_world/analysis/data"
FIGS  = "/home/lenovo/Git/new_world/analysis/figures"
os.makedirs(FIGS, exist_ok=True)

RUNS   = [1, 2, 3, 4]
LABELS = {1:"Run 1 — Idle/Baseline", 2:"Run 2 — Small Perturbation",
          3:"Run 3 — Large Perturbation", 4:"Run 4 — Relaxation"}
COLORS = {1:"#4e79a7", 2:"#f28e2b", 3:"#e15759", 4:"#59a14f"}

COLS = [
    ("activation_mean",  "Activation",      COLORS),
    ("memory_mean",      "Memory",          {1:"#76b7b2",2:"#edc948",3:"#b07aa1",4:"#ff9da7"}),
    ("field_depth_mean", "Field Depth",     {1:"#9c755f",2:"#bab0ac",3:"#d4a6c8",4:"#8cd17d"}),
    ("transport_mean",   "Transport",       {1:"#499894",2:"#f1ce63",3:"#86bcb6",4:"#e15759"}),
    ("convergence_mean", "Convergence",     {1:"#79706e",2:"#d37295",3:"#fabfd2",4:"#b6992d"}),
]

for run_num in RUNS:
    df = pd.read_parquet(os.path.join(DATA, f"run{run_num}_ts.parquet"))
    ticks = df["tick"].values

    fig, axes = plt.subplots(5, 1, figsize=(12, 16), sharex=True)
    fig.suptitle(LABELS[run_num], fontsize=14, fontweight="bold", y=0.98)

    for ax, (col, ylabel, cmap) in zip(axes, COLS):
        raw  = df[col].values
        roll = df.get(f"{col}_roll_mean", pd.Series(raw)).values

        ax.plot(ticks, raw,  color=cmap[run_num], alpha=0.35, linewidth=0.7, label="raw")
        ax.plot(ticks, roll, color=cmap[run_num], alpha=0.9,  linewidth=1.4, label="rolling mean (w=50)")
        ax.set_ylabel(ylabel, fontsize=10)
        ax.tick_params(labelsize=8)
        ax.legend(fontsize=7, loc="upper right")
        ax.grid(True, alpha=0.25)

    axes[-1].set_xlabel("Tick", fontsize=10)
    plt.tight_layout(rect=[0, 0, 1, 0.97])
    out = os.path.join(FIGS, f"run{run_num}_timeseries.png")
    plt.savefig(out, dpi=150)
    plt.close()
    print(f"  Saved {out}")

print("Stage 5a complete — per-run time series plots.")
