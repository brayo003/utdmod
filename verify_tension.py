#!/usr/bin/env python3
"""
verify_tension.py
Headless 1D reaction-diffusion test harness.
Reads parameters from config.json if present, otherwise uses built-in defaults.
Writes a single JSON metrics object to stdout.
"""
import json, time, uuid, sys, os
import numpy as np

# --- default config (overridden by config.json) ---
DEFAULT_CFG = {
  "grid_n": 200,
  "dx": 1.0,
  "dt": 0.5,
  "steps": 20000,
  "base_rate": 0.0005,
  "ritual_reduction": 0.05,
  "wraith_threshold": 0.85,
  "serpent_threshold": 0.98,
  "D_rho": 1.0,
  "alpha_E": 1.5,
  "tau_E": 0.5,
  "alpha_F": 0.5,
  "beta_F": 0.8,
  "tau_F": 2.0,
  "measure_front_level": 0.5,
  "ritual_use_interval": 500  # simulated ritual usage interval
}

def load_config(path='config.json'):
    cfg = DEFAULT_CFG.copy()
    if os.path.exists(path):
        try:
            with open(path,'r') as f:
                user = json.load(f)
            cfg.update(user)
        except Exception as e:
            print(json.dumps({"error":"failed to read config.json","exc":str(e)}))
            sys.exit(1)
    return cfg

def laplacian(u, dx):
    return (np.roll(u, -1) - 2*u + np.roll(u, 1)) / (dx*dx)

def step(rho, E, F, cfg):
    D = cfg["D_rho"]
    dt = cfg["dt"]
    dx = cfg["dx"]
    lap = laplacian(rho, dx)
    diffusion = D * lap
    growth = cfg["base_rate"] + 0.2 * E * rho * (1.0 - rho)
    loss = cfg["ritual_reduction"] * F * rho
    drho = diffusion + growth - loss
    dE = (cfg["alpha_E"] * rho - E) / cfg["tau_E"]
    dF = (cfg["alpha_F"] * rho + cfg["beta_F"] * E - F) / cfg["tau_F"]
    rho_next = np.clip(rho + dt * drho, 0.0, 1.0)
    E_next = np.clip(E + dt * dE, 0.0, None)
    F_next = np.clip(F + dt * dF, 0.0, None)
    return rho_next, E_next, F_next

def compute_metrics(mean_rho_history, front_positions, spawn_counts, cfg):
    tsat = None
    for t,v in enumerate(mean_rho_history):
        if v >= 0.80:
            tsat = t
            break
    if tsat is None:
        tsat = cfg["steps"]
    arr = np.array(mean_rho_history)
    mask = (arr >= 0.2) & (arr <= 0.6)
    plateau = 0
    if mask.any():
        # compute lengths of true runs
        padded = np.concatenate(([0], mask.view(np.int8), [0]))
        changes = np.diff(padded)
        starts = np.where(changes==1)[0]
        ends = np.where(changes==-1)[0]
        lengths = ends - starts
        plateau = int(lengths.max()) if lengths.size>0 else 0
    if len(front_positions) >= 2:
        speeds = np.diff(front_positions)
        wave_speed = float(np.mean(speeds))
    else:
        wave_speed = 0.0
    final_mean = float(mean_rho_history[-1]) if len(mean_rho_history)>0 else 0.0
    wraith_rate = spawn_counts.get("wraith",0) / max(1.0, final_mean)
    serpent_rate = spawn_counts.get("serpent",0) / max(1.0, final_mean)
    return {
        "Tsat_ticks": int(tsat),
        "plateau_duration_ticks": int(plateau),
        "wave_front_speed_blocks_per_tick": float(wave_speed),
        "wraith_spawns_per_tension": float(wraith_rate),
        "serpent_spawns_per_tension": float(serpent_rate)
    }

def run(cfg):
    np.random.seed(1)
    n = cfg["grid_n"]
    rho = np.zeros(n) + 0.01
    E = np.zeros(n)
    F = np.zeros(n)
    rho[n//2-2:n//2+2] = 0.2
    mean_history = []
    front_positions = []
    spawn_counts = {"wraith":0, "serpent":0}
    steps = int(cfg["steps"])
    for step_i in range(steps):
        rho, E, F = step(rho, E, F, cfg)
        mean_rho = float(rho.mean())
        mean_history.append(mean_rho)
        indices = np.where(rho >= cfg["measure_front_level"])[0]
        if indices.size>0:
            front_positions.append(float(indices.mean()))
        if mean_rho >= cfg["wraith_threshold"]:
            spawn_counts["wraith"] += 1
        if mean_rho >= cfg["serpent_threshold"]:
            spawn_counts["serpent"] += 1
        if cfg.get("ritual_use_interval",0) and step_i % int(cfg["ritual_use_interval"]) == 0 and step_i>0:
            F *= 0.7
    metrics = compute_metrics(mean_history, front_positions, spawn_counts, cfg)
    metrics["suppression_efficiency"] = cfg["ritual_reduction"]
    out = {
        "run_id": time.strftime('%Y%m%dT%H%M%S') + "-" + str(uuid.uuid4())[:8],
        "params": cfg,
        "metrics": metrics,
        "pass": None,
        "notes": ""
    }
    # verification criteria (hard-coded to your targets)
    m = metrics
    checks = {
      "Tsat_ok": (4800 <= m["Tsat_ticks"] <= 7200),
      "suppression_ok": (0.15 <= metrics["suppression_efficiency"] <= 0.25),
      "wraith_ok": (1.0 <= m["wraith_spawns_per_tension"] <= 2.0),
      "serpent_ok": (0.05 <= m["serpent_spawns_per_tension"] <= 0.1),
      "plateau_ok": (m["plateau_duration_ticks"] >= 3600)
    }
    out["pass"] = all(checks.values())
    out["checks"] = checks
    out["notes"] = "auto-eval"
    return out

def main():
    cfg = load_config()
    result = run(cfg)
    print(json.dumps(result, indent=2))

if __name__ == '__main__':
    main()
