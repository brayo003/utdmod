"""
Stage 5b: Cross-run overlay comparison plots.
Generates: 4-run overlays for each key metric.
"""
import os
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

DATA  = "/home/lenovo/Git/new_world/analysis/data"
FIGS  = "/home/lenovo/Git/new_world/analysis/figures"
os.makedirs(FIGS, exist_ok=True)

RUNS   = [1, 2, 3, 4]
LABELS = {1:"R1 Idle", 2:"R2 Small", 3:"R3 Large", 4:"R4 Relax"}
COLORS = {1:"#4e79a7", 2:"#f28e2b", 3:"#e15759", 4:"#59a14f"}
LW     = {1:1.0, 2:1.0, 3:1.4, 4:1.4}
ALPHA  = {1:0.7, 2:0.7, 3:0.9, 4:0.9}

ts = {r: pd.read_parquet(os.path.join(DATA, f"run{r}_ts.parquet")) for r in RUNS}

METRICS = [
    ("activation_mean",  "Mean Activation"),
    ("memory_mean",      "Mean Memory"),
    ("field_depth_mean", "Mean Field Depth"),
    ("transport_mean",   "Mean Transport"),
    ("convergence_mean", "Mean Convergence"),
    ("readiness_mean",   "Mean Readiness"),
]

fig, axes = plt.subplots(3, 2, figsize=(14, 12))
axes = axes.flatten()
fig.suptitle("Cross-Run Comparison — All 4 Runs", fontsize=13, fontweight="bold")

for ax, (col, title) in zip(axes, METRICS):
    for r in RUNS:
        df = ts[r]
        roll_col = f"{col}_roll_mean"
        y = df[roll_col].values if roll_col in df.columns else df[col].values
        ax.plot(df["tick"].values, y,
                color=COLORS[r], linewidth=LW[r], alpha=ALPHA[r], label=LABELS[r])
    ax.set_title(title, fontsize=10, fontweight="bold")
    ax.set_xlabel("Tick", fontsize=8)
    ax.tick_params(labelsize=7)
    ax.legend(fontsize=7)
    ax.grid(True, alpha=0.2)

plt.tight_layout()
out = os.path.join(FIGS, "cross_run_overlay.png")
plt.savefig(out, dpi=150)
plt.close()
print(f"  Saved {out}")

# ---- Derivatives panel ----
fig, axes = plt.subplots(2, 2, figsize=(14, 8))
axes = axes.flatten()
fig.suptitle("Growth Rates (dX/dt) — Cross-Run", fontsize=12, fontweight="bold")

DERIV_METRICS = [
    ("d_activation",  "d(Activation)/dt"),
    ("d_memory",      "d(Memory)/dt"),
    ("d_depth",       "d(Depth)/dt"),
    ("d_convergence", "d(Convergence)/dt"),
]

for ax, (col, title) in zip(axes, DERIV_METRICS):
    for r in RUNS:
        df = ts[r]
        if col not in df.columns:
            continue
        y = df[col].rolling(30, min_periods=1).mean().values
        ax.plot(df["tick"].values, y,
                color=COLORS[r], linewidth=LW[r], alpha=ALPHA[r], label=LABELS[r])
    ax.axhline(0, color="black", linewidth=0.6, linestyle="--", alpha=0.5)
    ax.set_title(title, fontsize=10, fontweight="bold")
    ax.set_xlabel("Tick", fontsize=8)
    ax.tick_params(labelsize=7)
    ax.legend(fontsize=7)
    ax.grid(True, alpha=0.2)

plt.tight_layout()
out = os.path.join(FIGS, "cross_run_derivatives.png")
plt.savefig(out, dpi=150)
plt.close()
print(f"  Saved {out}")

print("Stage 5b complete — cross-run overlay plots.")
