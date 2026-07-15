"""
Stage 2: Compute per-tick aggregated time series, derivatives, rolling stats,
         autocorrelation, and FFT for all 4 runs.
Outputs: analysis/data/run{N}_ts.parquet  (time series)
         analysis/data/run{N}_stats.json  (summary statistics)
"""
import os, json
import numpy as np
import pandas as pd
from scipy import signal as sig
from scipy.stats import linregress

DATA = "/home/lenovo/Git/new_world/analysis/data"

RUNS = [
    (1, "Idle/Baseline"),
    (2, "Small Perturbation"),
    (3, "Large Perturbation"),
    (4, "Relaxation"),
]

WINDOW = 50   # rolling window (ticks)

def first_diff(series):
    """Finite difference derivative (forward)."""
    arr = series.values.astype(float)
    d   = np.gradient(arr)
    return pd.Series(d, index=series.index)

def autocorr_lags(series, nlags=200):
    """Return autocorrelation array for lags 0..nlags."""
    s = series.dropna().values
    s = s - s.mean()
    n = len(s)
    result = []
    for lag in range(min(nlags+1, n)):
        if n - lag < 2:
            result.append(np.nan)
        else:
            c = np.corrcoef(s[:n-lag], s[lag:])[0, 1]
            result.append(c)
    return np.array(result)

def dominant_frequency(series, dt=1.0):
    """Return dominant frequency (cycles/tick) via Welch PSD."""
    s = series.dropna().values
    if len(s) < 10:
        return None, None
    f, psd = sig.welch(s, fs=1.0/dt, nperseg=min(256, len(s)//2))
    if len(f) < 2:
        return None, None
    idx = np.argmax(psd[1:]) + 1   # skip DC
    return float(f[idx]), float(psd[idx])

all_stats = {}

for run_num, label in RUNS:
    print(f"\n[Run {run_num}] {label}")
    prefix = f"run{run_num}"
    df = pd.read_parquet(os.path.join(DATA, f"{prefix}_substrate.parquet"))

    # --- Per-tick mean across all chunks ---
    ts = (df.groupby("tick")
            .agg(
                activation_mean  = ("activation",    "mean"),
                activation_max   = ("activation",    "max"),
                activation_std   = ("activation",    "std"),
                memory_mean      = ("memory",        "mean"),
                memory_max       = ("memory",        "max"),
                field_depth_mean = ("field_depth",   "mean"),
                field_depth_max  = ("field_depth",   "max"),
                transport_mean   = ("transport",     "mean"),
                transport_std    = ("transport",     "std"),
                convergence_mean = ("convergence",   "mean"),
                readiness_mean   = ("readiness",     "mean"),
                capacity_mean    = ("capacity",      "mean"),
                baseline_mean    = ("baseline",      "mean"),
                external_input   = ("external_input","sum"),
                chunk_count      = ("chunk_x",       "count"),
            )
            .reset_index()
            .sort_values("tick")
    )

    # ---------- Derivatives ----------
    ts["d_activation"]  = first_diff(ts["activation_mean"])
    ts["d2_activation"] = first_diff(ts["d_activation"])
    ts["d_memory"]      = first_diff(ts["memory_mean"])
    ts["d_depth"]       = first_diff(ts["field_depth_mean"])
    ts["d_convergence"] = first_diff(ts["convergence_mean"])

    # ---------- Rolling stats ----------
    w = WINDOW
    for col in ["activation_mean", "memory_mean", "field_depth_mean",
                "transport_mean", "convergence_mean"]:
        ts[f"{col}_roll_mean"] = ts[col].rolling(w, min_periods=1).mean()
        ts[f"{col}_roll_var"]  = ts[col].rolling(w, min_periods=1).var()

    # ---------- Summary statistics ----------
    stats = {}
    for col in ["activation_mean","memory_mean","field_depth_mean",
                "transport_mean","convergence_mean","readiness_mean"]:
        arr = ts[col].dropna().values
        if len(arr) == 0:
            continue
        slope, intercept, r, p, se = linregress(np.arange(len(arr)), arr)
        stats[col] = {
            "mean":     float(arr.mean()),
            "std":      float(arr.std()),
            "min":      float(arr.min()),
            "max":      float(arr.max()),
            "slope":    float(slope),
            "slope_r2": float(r**2),
            "slope_p":  float(p),
            "slope_se": float(se),
        }

    # ---------- Autocorrelation of activation ----------
    ac = autocorr_lags(ts["activation_mean"], nlags=min(200, len(ts)//3))
    stats["activation_autocorr_lag1"]  = float(ac[1])  if len(ac) > 1  else None
    stats["activation_autocorr_lag10"] = float(ac[10]) if len(ac) > 10 else None
    stats["activation_autocorr_lag50"] = float(ac[50]) if len(ac) > 50 else None

    # ---------- Dominant frequency ----------
    dom_f, dom_psd = dominant_frequency(ts["activation_mean"])
    stats["activation_dominant_freq"]    = dom_f
    stats["activation_dominant_psd"]     = dom_psd

    dom_f2, dom_psd2 = dominant_frequency(ts["memory_mean"])
    stats["memory_dominant_freq"]        = dom_f2

    # ---------- Transport noise (CV) ----------
    tr = ts["transport_mean"].dropna().values
    if tr.mean() > 0:
        stats["transport_CV"] = float(tr.std() / tr.mean())
    else:
        stats["transport_CV"] = None

    print(f"  ticks:  {ts['tick'].min()} – {ts['tick'].max()}  ({len(ts)} samples)")
    print(f"  activation mean={stats.get('activation_mean',{}).get('mean',0):.4f}  slope={stats.get('activation_mean',{}).get('slope',0):.2e}")
    print(f"  depth    mean={stats.get('field_depth_mean',{}).get('mean',0):.4f}  slope={stats.get('field_depth_mean',{}).get('slope',0):.2e}")
    print(f"  memory   mean={stats.get('memory_mean',{}).get('mean',0):.4f}  slope={stats.get('memory_mean',{}).get('slope',0):.2e}")
    print(f"  transport CV={stats.get('transport_CV')}")
    print(f"  ac(lag1)={stats.get('activation_autocorr_lag1'):.3f}   dom_freq={dom_f}")

    ts.to_parquet(os.path.join(DATA, f"{prefix}_ts.parquet"), index=False)
    all_stats[run_num] = stats
    with open(os.path.join(DATA, f"{prefix}_stats.json"), "w") as fh:
        json.dump(stats, fh, indent=2, default=str)

# Save combined stats
with open(os.path.join(DATA, "all_stats.json"), "w") as fh:
    json.dump({str(k): v for k, v in all_stats.items()}, fh, indent=2, default=str)

print("\nStage 2 complete.")
