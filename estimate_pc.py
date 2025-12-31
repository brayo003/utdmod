#!/usr/bin/env python3
import csv
import numpy as np
import sys

def read_csv(fname):
    r, p = [], []
    with open(fname,'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            r.append(float(row['r']))
            p.append(float(row['prob_tipping']))
    return np.array(r), np.array(p)

def estimate_pc(r, p):
    # find where p crosses 0.5 via linear interpolation
    idx = np.where(p >= 0.5)[0]
    if len(idx) == 0:
        return None, None
    i = idx[0]
    if i == 0:
        return r[0], None
    r0, r1 = r[i-1], r[i]
    p0, p1 = p[i-1], p[i]
    pc = r0 + (0.5 - p0)*(r1-r0)/(p1-p0)
    # finite-difference slope at pc approximated from neighbors
    # find local slope using central difference
    if 1 <= i < len(r)-1:
        slope = (p[i+1]-p[i-1])/(r[i+1]-r[i-1])
    else:
        slope = (p1-p0)/(r1-r0)
    return pc, slope

if __name__ == '__main__':
    fname = 'utd_sweep_results.csv'
    r,p = read_csv(fname)
    pc, slope = estimate_pc(r,p)
    if pc is None:
        print("No crossing at p=0.5 found in data.")
    else:
        print(f"Estimated Pc (p=0.5): {pc:.6f}")
        print(f"Estimated slope at Pc: {slope:.6f}")
