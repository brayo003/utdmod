import numpy as np
import math
import csv

# Same parameters as Java version
r = 0.153267
a = 1.0
b = 1.0
sigma = 0.35
dt = 0.05
steps = 2000

rng = np.random.RandomState(42)
x = 0.01

with open('python_reference.csv', 'w', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(['tick', 'x'])
    
    for t in range(steps):
        det = r * x + a * (x * x) - b * (x * x * x)
        x = x + dt * det + sigma * math.sqrt(dt) * rng.randn()
        writer.writerow([t, x])

print("Python reference completed. Results saved to python_reference.csv")
