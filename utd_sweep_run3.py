#!/usr/bin/env python3
import numpy as np
import math
import csv
import sys
from time import time

# Model: x_{t+1} = x_t + dt*(r*x_t + a*x_t**2 - b*x_t**3) + sigma*sqrt(dt)*N(0,1)
def run_trial(r, a, b, sigma, dt, steps, seed):
    rng = np.random.RandomState(seed)
    x = 0.01
    for t in range(steps):
        det = r*x + a*(x**2) - b*(x**3)
        x = x + dt*det + sigma*math.sqrt(dt)*rng.randn()
        if abs(x) > 1e3:
            return True, t
    return (abs(x) > 1.0), steps

def sweep(r_values, trials_per_r, a, b, sigma, dt, steps):
    out = []
    start = time()
    for idx, r in enumerate(r_values):
        tipped = 0
        times = []
        for i in range(trials_per_r):
            seed = int((idx+1)*100000 + i)
            tip, t = run_trial(r, a, b, sigma, dt, steps, seed)
            if tip:
                tipped += 1
                times.append(t)
        prob = tipped / trials_per_r
        mean_time = np.mean(times) if times else ''
        out.append((r, prob, mean_time, tipped, trials_per_r))
        print(f"[{idx+1}/{len(r_values)}] r={r:.4f} prob={prob:.4f} mean_time={mean_time}")
    print("Elapsed:", time()-start)
    return out

if __name__ == '__main__':
    # editable parameters
    rmin, rmax = -1.5, 1.8
    steps = 41
    trials_per_r = 500
    a = 1.0
    b = 1.0
    sigma = 0.35
    dt = 0.05
    sim_steps = 2000

    r_values = np.linspace(rmin, rmax, steps)
    results = sweep(r_values, trials_per_r, a, b, sigma, dt, sim_steps)

    fname = 'utd_sweep_results_run3.csv'
    with open(fname, 'w', newline='') as f:
        w = csv.writer(f)
        w.writerow(['r','prob_tipping','mean_time','raw_tips','trials'])
        for row in results:
            w.writerow(row)
    print("Saved:", fname)
