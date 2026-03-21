#!/usr/bin/env python3
import csv
import matplotlib.pyplot as plt
import os

# Paths
results_dir = os.path.join(os.path.dirname(__file__), 'results/final_run')
log_file = os.path.join(results_dir, 'simulation_log.csv')
plot_file = os.path.join(results_dir, 'tension_curve.png')

# Read CSV
steps = []
wraiths = []
serpents = []
rituals = []

with open(log_file) as f:
    reader = csv.DictReader(f)
    for row in reader:
        steps.append(int(row['step']))
        wraiths.append(int(row['wraith_count']))
        serpents.append(int(row['serpent_count']))
        rituals.append(int(row['ritual_counter']))

# Plot
plt.figure(figsize=(12,6))
plt.plot(steps, wraiths, label='Wraiths', color='blue')
plt.plot(steps, serpents, label='Serpents', color='red')
plt.plot(steps, rituals, label='Rituals used', color='green', linestyle='--')
plt.xlabel('Step')
plt.ylabel('Count')
plt.title('Minecraft Mod Tension Curve')
plt.legend()
plt.grid(True)
plt.tight_layout()
plt.savefig(plot_file)
plt.show()
