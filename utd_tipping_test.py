import numpy as np
import math

# Minimal nonlinear update:
# x_{t+1} = x_t + dt*(r*x_t - b*x_t**3) + noise
# This is enough to check if the system has a tipping point character.

r = 1.2       # control parameter to provoke tipping
b = 1.0
sigma = 0.3
dt = 0.1
steps = 500

rng = np.random.RandomState(42)
x = 0.01

trajectory = []

for t in range(steps):
    det = r*x - b*(x**3)
    x = x + dt*det + sigma*math.sqrt(dt)*rng.randn()
    trajectory.append(x)

print("Final value:", x)
print("Peak:", max(trajectory))
print("Minimum:", min(trajectory))
print("Trajectory length:", len(trajectory))
