#!/usr/bin/env python3
import json
import os
import random
import csv

# Load final tuned settings
tuned_file = os.path.join(os.path.dirname(__file__), 'final_tuned_settings.json')
with open(tuned_file) as f:
    tuned = json.load(f)

# Orchestrator parameters
base_rate = tuned['base_rate']
ritual_reduction = tuned['ritual_reduction']
wraith_threshold = tuned['wraith_threshold']
serpent_threshold = tuned['serpent_threshold']
grid_n = tuned['grid_n']
steps = tuned['steps']
ritual_use_interval = tuned['ritual_use_interval']

# Prepare results folder
results_dir = os.path.join(os.path.dirname(__file__), 'results/final_run')
os.makedirs(results_dir, exist_ok=True)
log_file = os.path.join(results_dir, 'simulation_log.csv')

# Initialize grid
grid = [[0 for _ in range(grid_n)] for _ in range(grid_n)]
wraith_count = 0
serpent_count = 0
ritual_counter = 0

# Open CSV for logging
with open(log_file, 'w', newline='') as csvfile:
    writer = csv.writer(csvfile)
    writer.writerow(['step', 'wraith_count', 'serpent_count', 'ritual_counter'])

    for step in range(1, steps + 1):
        # Spawn wraiths
        for i in range(grid_n):
            for j in range(grid_n):
                if random.random() < base_rate * (1 - wraith_threshold):
                    grid[i][j] = 1
                    wraith_count += 1

        # Spawn serpents (rare)
        if random.random() > serpent_threshold:
            serpent_count += 1

        # Ritual effect
        if step % ritual_use_interval == 0:
            ritual_counter += 1
            wraith_count = max(0, wraith_count - int(wraith_count * ritual_reduction))

        # Log step
        writer.writerow([step, wraith_count, serpent_count, ritual_counter])

print(f"[orchestrator] simulation completed. Log saved to {log_file}")
