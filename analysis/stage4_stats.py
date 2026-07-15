"""
Stage 4: Statistical tests, cross-run comparisons, hysteresis analysis,
         attractor comparison, observer statement validation.
Outputs: analysis/data/stats_tests.json
"""
import os, json
import numpy as np
import pandas as pd
from scipy.stats import linregress, pearsonr, ttest_ind, f_oneway
from scipy.signal import welch

DATA = "/home/lenovo/Git/new_world/analysis/data"

RUNS = [1, 2, 3, 4]
LABELS = {1:"Idle", 2:"Small", 3:"Large", 4:"Relax"}

results = {}

# ─── Load time series ───────────────────────────────────────────
ts = {}
for r in RUNS:
    ts[r] = pd.read_parquet(os.path.join(DATA, f"run{r}_ts.parquet"))

# ─── 1. Memory evolution: does it follow integral-of-activation? ──
# Theory: dM/dt = gain*activation - decay*M
# We test: slope of memory is positive under activation load (Run3),
#          and memory decays toward an asymptote under no input (Run4)

print("=== Memory Evolution ===")
for r in [3, 4]:
    df = ts[r]
    act = df["activation_mean"].values
    mem = df["memory_mean"].values
    ticks = df["tick"].values
    
    # Expected increment: gain*act - decay*mem  (gain=0.02, decay=0.001)
    gain, decay = 0.02, 0.001
    expected_dM = gain * act - decay * mem
    actual_dM   = np.gradient(mem)
    
    # Correlation between predicted and actual increment
    if len(expected_dM) > 10:
        r_val, p_val = pearsonr(expected_dM[1:-1], actual_dM[1:-1])
    else:
        r_val, p_val = 0.0, 1.0
    
    print(f"  Run {r}: corr(predicted_dM, actual_dM) = {r_val:.4f}  p={p_val:.4e}")
    results[f"run{r}_memory_equation_r"] = float(r_val)
    results[f"run{r}_memory_equation_p"] = float(p_val)
    
    # Asymptotic test for Run 4 (should approach gain*act/decay = 20*act)
    if r == 4:
        expected_asymptote = gain / decay * np.mean(act)
        actual_final_memory = float(mem[-10:].mean())
        results["run4_memory_expected_asymptote"] = float(expected_asymptote)
        results["run4_memory_actual_final"]        = float(actual_final_memory)
        print(f"  Run 4 expected memory asymptote = {expected_asymptote:.4f}  actual = {actual_final_memory:.4f}")

# ─── 2. Depth accumulation ───────────────────────────────────────
print("\n=== Field Depth Accumulation ===")
for r in RUNS:
    df = ts[r]
    depth = df["field_depth_mean"].values
    ticks = df["tick"].values
    if len(depth) < 3:
        continue
    sl, _, r2, p, se = linregress(ticks, depth)
    ci95 = 1.96 * se
    print(f"  Run {r}: slope={sl:.2e}  r²={r2**2:.4f}  p={p:.4e}  CI95=[{sl-ci95:.2e},{sl+ci95:.2e}]")
    results[f"run{r}_depth_slope"]  = float(sl)
    results[f"run{r}_depth_r2"]     = float(r2**2)
    results[f"run{r}_depth_p"]      = float(p)
    results[f"run{r}_depth_ci95"]   = float(ci95)

# ─── 3. Transport stability ──────────────────────────────────────
print("\n=== Transport Stability ===")
for r in RUNS:
    df = ts[r]
    tr = df["transport_mean"].dropna().values
    if len(tr) == 0 or tr.mean() == 0:
        results[f"run{r}_transport_CV"] = None
        results[f"run{r}_transport_mean"] = 0.0
        print(f"  Run {r}: transport all-zero")
        continue
    cv = tr.std() / tr.mean()
    # Is transport correlated with activation? (reactive vs autonomous)
    act = df["activation_mean"].values[:len(tr)]
    if len(act) > 5:
        r_ta, p_ta = pearsonr(act, tr)
    else:
        r_ta, p_ta = 0.0, 1.0
    results[f"run{r}_transport_CV"]   = float(cv)
    results[f"run{r}_transport_mean"] = float(tr.mean())
    results[f"run{r}_transport_act_corr"] = float(r_ta)
    results[f"run{r}_transport_act_p"]    = float(p_ta)
    print(f"  Run {r}: CV={cv:.3f}  mean={tr.mean():.4e}  corr(transport,activation)={r_ta:.3f}  p={p_ta:.4e}")

# ─── 4. Hysteresis comparison ─────────────────────────────────────
# Compare Run1(baseline), Run3(perturbed), Run4(relaxation) at matching metrics
print("\n=== Hysteresis: Attractor Comparison ===")

# Use field_summary data to compare at same nominal "equilibrium" 
# Run1: 0 activation, 0 memory, 0 depth  (ground state)
# Run4 final: measure stable point
run1_stats = json.load(open(os.path.join(DATA, "run1_stats.json")))
run4_stats = json.load(open(os.path.join(DATA, "run4_stats.json")))
run3_stats = json.load(open(os.path.join(DATA, "run3_stats.json")))

for key in ["activation_mean","memory_mean","field_depth_mean"]:
    r1 = run1_stats.get(key, {})
    r3 = run3_stats.get(key, {})
    r4 = run4_stats.get(key, {})
    print(f"  {key}:")
    print(f"    Run1(idle)={r1.get('mean',0):.5f}  Run3(perturb)={r3.get('mean',0):.5f}  Run4(relax)={r4.get('mean',0):.5f}")

# Final-state comparison: did Run4 return to Run1's attractor?
df4 = ts[4]
df1 = ts[1]
r4_final_act  = float(df4["activation_mean"].iloc[-20:].mean())
r4_final_mem  = float(df4["memory_mean"].iloc[-20:].mean())
r4_final_dep  = float(df4["field_depth_mean"].iloc[-20:].mean())
r1_final_act  = float(df1["activation_mean"].iloc[-20:].mean())
r1_final_mem  = float(df1["memory_mean"].iloc[-20:].mean())
r1_final_dep  = float(df1["field_depth_mean"].iloc[-20:].mean())

print(f"\n  Run1 final 20 ticks: act={r1_final_act:.5f}  mem={r1_final_mem:.5f}  dep={r1_final_dep:.5f}")
print(f"  Run4 final 20 ticks: act={r4_final_act:.5f}  mem={r4_final_mem:.5f}  dep={r4_final_dep:.5f}")
print(f"  Delta act={r4_final_act-r1_final_act:.5f}  mem={r4_final_mem-r1_final_mem:.5f}  dep={r4_final_dep-r1_final_dep:.5f}")

hysteresis = {
    "run1_final": {"activation": r1_final_act, "memory": r1_final_mem, "depth": r1_final_dep},
    "run4_final": {"activation": r4_final_act, "memory": r4_final_mem, "depth": r4_final_dep},
    "delta_activation": r4_final_act - r1_final_act,
    "delta_memory":     r4_final_mem - r1_final_mem,
    "delta_depth":      r4_final_dep - r1_final_dep,
    "attractor_shifted": bool(abs(r4_final_act - r1_final_act) > 0.01 or
                               abs(r4_final_mem - r1_final_mem) > 0.01 or
                               abs(r4_final_dep - r1_final_dep) > 0.1),
}
results["hysteresis"] = hysteresis

# ─── 5. Autonomous organization test ─────────────────────────────
# Does activation continue in Run4 after ZERO perturbations?
print("\n=== Autonomous Organization (Run 4 — zero perturbations) ===")
df4 = ts[4].sort_values("tick")
act4 = df4["activation_mean"].values
mem4 = df4["memory_mean"].values
dep4 = df4["field_depth_mean"].values
ticks4 = df4["tick"].values

# External input should be ~0 throughout Run4
ext4 = df4["external_input"].values
print(f"  External input (Run4): mean={ext4.mean():.4e}  max={ext4.max():.4e}")
print(f"  Activation (Run4): mean={act4.mean():.4f}  std={act4.std():.4f}  min={act4.min():.4f}")
print(f"  Memory (Run4):     mean={mem4.mean():.4f}  std={mem4.std():.4f}")
print(f"  Depth (Run4):      mean={dep4.mean():.4f}  std={dep4.std():.4f}")
print(f"  -> Nonzero field state with zero external input: CONFIRMED" if act4.mean() > 0.05 else "  -> Field near zero")

results["autonomous_run4"] = {
    "external_input_mean": float(ext4.mean()),
    "external_input_max":  float(ext4.max()),
    "activation_mean":     float(act4.mean()),
    "activation_std":      float(act4.std()),
    "memory_mean":         float(mem4.mean()),
    "depth_mean":          float(dep4.mean()),
    "nonzero_field_with_zero_input": bool(act4.mean() > 0.05),
}

# ─── 6. Convergence analysis ─────────────────────────────────────
print("\n=== Convergence Analysis ===")
for r in [1, 2, 3, 4]:
    df = ts[r]
    conv = df["convergence_mean"].values
    print(f"  Run {r}: convergence mean={conv.mean():.4f}  std={conv.std():.4f}  min={conv.min():.4f}  max={conv.max():.4f}")
    results[f"run{r}_convergence_mean"] = float(conv.mean())
    results[f"run{r}_convergence_std"]  = float(conv.std())

# ─── 7. Observer statement candidates from debug log ─────────────
# (No explicit observer_message events exist — derive from telemetry)
print("\n=== Observer Verification from Telemetry ===")

# "Field depth increasing steadily" — test Run 3
df3 = ts[3].sort_values("tick")
depth3 = df3["field_depth_mean"].values
ticks3 = df3["tick"].values
sl, ic, r_val, p_val, se = linregress(ticks3, depth3)
obs1 = {
    "statement": "Field depth increasing steadily (Run 3)",
    "slope": float(sl), "p_value": float(p_val), "r2": float(r_val**2),
    "ci95": float(1.96*se),
    "verdict": "SUPPORTED" if sl > 0 and p_val < 0.05 else "NOT SUPPORTED",
}
print(f"  'Field depth increasing steadily' (Run3): slope={sl:.2e}  p={p_val:.4e} → {obs1['verdict']}")

# "Synchronization emerging" — correlation between R3 regions
all_reg = json.load(open(os.path.join(DATA, "all_regional.json")))
reg3 = all_reg.get("3", {}).get("per_region", {})
sync_corrs = []
rids = [k for k in reg3 if reg3[k]["tick_count"] >= 5]
for i in range(len(rids)):
    for j in range(i+1, len(rids)):
        ai = reg3[rids[i]]["activation_series"]
        aj = reg3[rids[j]]["activation_series"]
        n  = min(len(ai), len(aj))
        if n >= 4:
            r_s, p_s = pearsonr(ai[:n], aj[:n])
            sync_corrs.append(float(r_s))
mean_sync = float(np.mean(sync_corrs)) if sync_corrs else 0.0
obs2 = {
    "statement": "Synchronization emerging between regions (Run 3/4)",
    "pairwise_mean_corr": mean_sync,
    "n_pairs": len(sync_corrs),
    "verdict": "SUPPORTED" if mean_sync > 0.5 else ("PARTIALLY SUPPORTED" if mean_sync > 0.2 else "NOT SUPPORTED"),
}
print(f"  'Synchronization emerging': mean_corr={mean_sync:.3f}  n_pairs={len(sync_corrs)} → {obs2['verdict']}")

# "Transitioning toward equilibrium" — Run 4 activation slope near 0
sl4, _, r4, p4, se4 = linregress(df4["tick"].values, df4["activation_mean"].values)
obs3 = {
    "statement": "Transitioning toward equilibrium (Run 4)",
    "slope": float(sl4), "p_value": float(p4), "r2": float(r4**2),
    "verdict": "SUPPORTED" if abs(sl4) < 1e-5 and df4["activation_mean"].std() < 0.05 else "PARTIALLY SUPPORTED",
}
print(f"  'Transitioning to equilibrium' (Run4): slope={sl4:.2e}  std={df4['activation_mean'].std():.4f} → {obs3['verdict']}")

# "Oscillation inferred" — check FFT peak significance
def oscillation_check(series, label):
    s = series.dropna().values
    if len(s) < 20:
        return {"verdict": "INSUFFICIENT_DATA"}
    f, psd = welch(s, fs=1.0, nperseg=min(128, len(s)//2))
    dc_power = psd[0]
    total    = psd.sum()
    peak_idx = np.argmax(psd[1:]) + 1
    peak_f   = float(f[peak_idx])
    peak_psd = float(psd[peak_idx])
    peak_frac = float(peak_psd / total) if total > 0 else 0.0
    return {
        "dominant_freq": peak_f,
        "peak_psd":      peak_psd,
        "peak_fraction": peak_frac,
        "verdict": "OSCILLATORY" if peak_frac > 0.1 and peak_f > 0.001 else "NO_CLEAR_OSCILLATION",
    }

for r in [3, 4]:
    osc = oscillation_check(ts[r]["activation_mean"], f"Run {r}")
    results[f"run{r}_oscillation"] = osc
    print(f"  Oscillation (Run {r}): freq={osc.get('dominant_freq'):.5f}  peak_frac={osc.get('peak_fraction'):.3f} → {osc['verdict']}")

obs4 = {
    "statement": "Oscillation inferred (Run 3/4)",
    "run3": results["run3_oscillation"],
    "run4": results["run4_oscillation"],
    "verdict": "NOT SUPPORTED" if results["run3_oscillation"]["verdict"] == "NO_CLEAR_OSCILLATION" else "SUPPORTED",
}

results["observer_verdicts"] = {
    "depth_increasing_run3":     obs1,
    "synchronization_emerging":  obs2,
    "equilibrium_run4":          obs3,
    "oscillation":               obs4,
}

# ─── Save ─────────────────────────────────────────────────────────
with open(os.path.join(DATA, "stats_tests.json"), "w") as fh:
    json.dump(results, fh, indent=2, default=str)

print("\nStage 4 complete.")
