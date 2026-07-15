"""
Stage 5c: Regional dynamics, hysteresis loop, transport stability,
          and regional correlation heatmap plots.
"""
import os, json
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from scipy.stats import pearsonr

DATA  = "/home/lenovo/Git/new_world/analysis/data"
FIGS  = "/home/lenovo/Git/new_world/analysis/figures"
os.makedirs(FIGS, exist_ok=True)

COLORS = {1:"#4e79a7", 2:"#f28e2b", 3:"#e15759", 4:"#59a14f"}
REG_COLORS = ["#e15759","#4e79a7","#59a14f","#f28e2b"]

# ── 1. Regional dynamics: Run 3 and Run 4 ─────────────────────────
for run_num in [3, 4]:
    df_field  = pd.read_parquet(os.path.join(DATA, f"run{run_num}_field.parquet"))
    df_region = pd.read_parquet(os.path.join(DATA, f"run{run_num}_regions.parquet"))

    if len(df_field) == 0:
        continue

    df_field  = df_field.sort_values("tick")
    df_region = df_region.sort_values(["region_id", "tick"])
    region_ids = sorted(df_region["region_id"].unique())

    fig, axes = plt.subplots(4, 1, figsize=(12, 14), sharex=False)
    label = "Large Perturbation" if run_num == 3 else "Relaxation"
    fig.suptitle(f"Run {run_num} — {label} — Regional Dynamics", fontsize=13, fontweight="bold")

    # Panel 1: region count + activation mean
    ax = axes[0]
    ax2 = ax.twinx()
    ax.bar(df_field["tick"], df_field["region_count"], width=800, color="#aec7e8", alpha=0.6, label="Region Count")
    ax2.plot(df_field["tick"], df_field["activation_mean"], color=COLORS[run_num], linewidth=1.5, label="Activation Mean")
    ax2.plot(df_field["tick"], df_field["memory_mean"], color="#9467bd", linewidth=1.5, linestyle="--", label="Memory Mean")
    ax.set_ylabel("Region Count", fontsize=9)
    ax2.set_ylabel("Activation / Memory", fontsize=9)
    ax.set_title("Region Count & Field Means", fontsize=10)
    lines1, labs1 = ax.get_legend_handles_labels()
    lines2, labs2 = ax2.get_legend_handles_labels()
    ax.legend(lines1 + lines2, labs1 + labs2, fontsize=7, loc="upper left")
    ax.grid(True, alpha=0.2)

    # Panel 2: per-region activation
    ax = axes[1]
    for i, rid in enumerate(region_ids):
        sub = df_region[df_region["region_id"] == rid].sort_values("tick")
        ax.plot(sub["tick"], sub["mean_activation"],
                color=REG_COLORS[i % len(REG_COLORS)], linewidth=1.4,
                label=f"Region {int(rid)}")
    ax.set_ylabel("Mean Activation", fontsize=9)
    ax.set_title("Per-Region Activation", fontsize=10)
    ax.legend(fontsize=7)
    ax.grid(True, alpha=0.2)

    # Panel 3: per-region depth
    ax = axes[2]
    for i, rid in enumerate(region_ids):
        sub = df_region[df_region["region_id"] == rid].sort_values("tick")
        ax.plot(sub["tick"], sub["mean_depth"],
                color=REG_COLORS[i % len(REG_COLORS)], linewidth=1.4,
                label=f"Region {int(rid)}")
    ax.set_ylabel("Mean Depth", fontsize=9)
    ax.set_title("Per-Region Field Depth", fontsize=10)
    ax.legend(fontsize=7)
    ax.grid(True, alpha=0.2)

    # Panel 4: per-region maturity
    ax = axes[3]
    for i, rid in enumerate(region_ids):
        sub = df_region[df_region["region_id"] == rid].sort_values("tick")
        ax.plot(sub["tick"], sub["average_maturity"],
                color=REG_COLORS[i % len(REG_COLORS)], linewidth=1.4,
                label=f"Region {int(rid)}")
    ax.set_ylabel("Average Maturity", fontsize=9)
    ax.set_title("Per-Region Maturity", fontsize=10)
    ax.set_xlabel("Tick", fontsize=9)
    ax.legend(fontsize=7)
    ax.grid(True, alpha=0.2)

    plt.tight_layout()
    out = os.path.join(FIGS, f"run{run_num}_regional.png")
    plt.savefig(out, dpi=150)
    plt.close()
    print(f"  Saved {out}")

# ── 2. Hysteresis loop: Activation vs Memory across runs ──────────
fig, axes = plt.subplots(1, 2, figsize=(13, 5))
fig.suptitle("Hysteresis: Activation–Memory and Activation–Depth Phase Portraits", fontsize=12, fontweight="bold")

for run_num in [1, 3, 4]:
    df = pd.read_parquet(os.path.join(DATA, f"run{run_num}_ts.parquet"))
    act = df["activation_mean"].values
    mem = df["memory_mean"].values
    dep = df["field_depth_mean"].values
    lbl = {1:"R1 Idle", 3:"R3 Large", 4:"R4 Relax"}[run_num]
    c   = COLORS[run_num]
    axes[0].plot(act, mem, color=c, alpha=0.7, linewidth=1.0, label=lbl)
    axes[0].scatter([act[0]], [mem[0]], color=c, s=60, marker="o", zorder=5)
    axes[0].scatter([act[-1]], [mem[-1]], color=c, s=60, marker="s", zorder=5)
    axes[1].plot(act, dep, color=c, alpha=0.7, linewidth=1.0, label=lbl)
    axes[1].scatter([act[0]], [dep[0]], color=c, s=60, marker="o", zorder=5)
    axes[1].scatter([act[-1]], [dep[-1]], color=c, s=60, marker="s", zorder=5)

for ax, (xl, yl) in zip(axes, [("Activation","Memory"), ("Activation","Depth")]):
    ax.set_xlabel(xl, fontsize=10)
    ax.set_ylabel(yl, fontsize=10)
    ax.legend(fontsize=8)
    ax.grid(True, alpha=0.2)
    ax.set_title(f"{xl} vs {yl} Phase Portrait (○=start, □=end)", fontsize=9)

plt.tight_layout()
out = os.path.join(FIGS, "hysteresis_phase.png")
plt.savefig(out, dpi=150)
plt.close()
print(f"  Saved {out}")

# ── 3. Transport stability histogram ─────────────────────────────
fig, axes = plt.subplots(1, 3, figsize=(13, 4))
fig.suptitle("Transport Distribution Across Runs (nonzero only)", fontsize=11, fontweight="bold")
plot_runs = [2, 3, 4]
for ax, r in zip(axes, plot_runs):
    df = pd.read_parquet(os.path.join(DATA, f"run{r}_ts.parquet"))
    tr = df["transport_mean"].values
    tr = tr[tr > 0]
    if len(tr) == 0:
        ax.text(0.5, 0.5, "all-zero", ha="center", va="center", transform=ax.transAxes)
    else:
        ax.hist(tr, bins=40, color=COLORS[r], alpha=0.75, edgecolor="white", linewidth=0.3)
        ax.axvline(tr.mean(), color="black", linewidth=1.2, linestyle="--",
                   label=f"mean={tr.mean():.4f}")
        cv = tr.std() / tr.mean()
        ax.set_title(f"Run {r}  CV={cv:.3f}", fontsize=9, fontweight="bold")
        ax.legend(fontsize=7)
    ax.set_xlabel("Transport", fontsize=8)
    ax.set_ylabel("Count", fontsize=8)
    ax.grid(True, alpha=0.2)

plt.tight_layout()
out = os.path.join(FIGS, "transport_histogram.png")
plt.savefig(out, dpi=150)
plt.close()
print(f"  Saved {out}")

# ── 4. Regional correlation heatmap (Run 4) ───────────────────────
df_region4 = pd.read_parquet(os.path.join(DATA, "run4_regions.parquet"))
df_region4 = df_region4.sort_values(["region_id", "tick"])
region_ids = sorted(df_region4["region_id"].unique())

ticks_all = sorted(df_region4["tick"].unique())
mat = []
rid_labels = []
for rid in region_ids:
    sub = df_region4[df_region4["region_id"] == rid].set_index("tick")
    series = sub.reindex(ticks_all)["mean_activation"].interpolate().values
    mat.append(series)
    rid_labels.append(f"R{int(rid)}")

mat = np.array(mat)
corr = np.corrcoef(mat)

fig, ax = plt.subplots(figsize=(6, 5))
im = ax.imshow(corr, cmap="coolwarm", vmin=-1, vmax=1)
ax.set_xticks(range(len(rid_labels)))
ax.set_yticks(range(len(rid_labels)))
ax.set_xticklabels(rid_labels, fontsize=10)
ax.set_yticklabels(rid_labels, fontsize=10)
for i in range(len(rid_labels)):
    for j in range(len(rid_labels)):
        ax.text(j, i, f"{corr[i,j]:.2f}", ha="center", va="center", fontsize=9,
                color="white" if abs(corr[i,j]) > 0.5 else "black")
plt.colorbar(im, ax=ax, label="Pearson r")
ax.set_title("Run 4 — Regional Activation Correlation Heatmap", fontsize=10, fontweight="bold")
plt.tight_layout()
out = os.path.join(FIGS, "run4_regional_correlation.png")
plt.savefig(out, dpi=150)
plt.close()
print(f"  Saved {out}")

print("Stage 5c complete — regional / hysteresis / transport / heatmap plots.")
