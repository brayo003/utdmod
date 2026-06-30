#!/usr/bin/env python3
import numpy as np
import matplotlib.pyplot as plt
from pathlib import Path
import os

# Create output directory
outdir = Path("simulation_lab/run/readiness_sweep")
outdir.mkdir(parents=True, exist_ok=True)

# Readiness function implementations
def exponential_readiness(m, scale):
    return np.exp(-m / scale)

def rational_readiness(m, scale):
    return 1.0 / (1.0 + (m / scale) ** 2)

def logistic_readiness(m, scale, width):
    return 1.0 / (1.0 + np.exp((m - scale) / width))

def field_depth_readiness(d, scale):
    return np.exp(-d / scale)

def combined_readiness(m, d, m_scale, d_scale):
    return np.exp(-m / m_scale) * np.exp(-d / d_scale)

# Create memory axis
memory = np.linspace(0, 10, 100)
depth = np.linspace(0, 10, 100)

# Figure 1: Exponential variants
fig, axes = plt.subplots(2, 3, figsize=(15, 10))
fig.suptitle('Exponential Readiness: R(m) = exp(-m/scale)', fontsize=16, fontweight='bold')
scales = [0.2, 0.5, 1.0, 2.0, 5.0, 10.0]
for idx, scale in enumerate(scales):
    ax = axes[idx // 3, idx % 3]
    readiness = exponential_readiness(memory, scale)
    ax.plot(memory, readiness, 'b-', linewidth=2)
    ax.fill_between(memory, readiness, alpha=0.3)
    ax.set_xlabel('Memory', fontsize=10)
    ax.set_ylabel('Readiness', fontsize=10)
    ax.set_title(f'Scale = {scale}', fontsize=11)
    ax.grid(True, alpha=0.3)
    ax.set_ylim([0, 1])
    # Mark key points
    m_vals = [0.1, 1.0, 5.0]
    for m_val in m_vals:
        r_val = exponential_readiness(m_val, scale)
        ax.plot(m_val, r_val, 'ro', markersize=5)
        ax.text(m_val, r_val + 0.05, f'{r_val:.2f}', ha='center', fontsize=8)

plt.tight_layout()
plt.savefig(outdir / 'variant1_exponential_sweep.png', dpi=150, bbox_inches='tight')
plt.close()

# Figure 2: Rational variants
fig, axes = plt.subplots(2, 3, figsize=(15, 10))
fig.suptitle('Rational Readiness: R(m) = 1/(1 + (m/scale)²)', fontsize=16, fontweight='bold')
scales = [0.2, 0.5, 1.0, 2.0, 5.0, 10.0]
for idx, scale in enumerate(scales):
    ax = axes[idx // 3, idx % 3]
    readiness = rational_readiness(memory, scale)
    ax.plot(memory, readiness, 'g-', linewidth=2)
    ax.fill_between(memory, readiness, alpha=0.3, color='green')
    ax.set_xlabel('Memory', fontsize=10)
    ax.set_ylabel('Readiness', fontsize=10)
    ax.set_title(f'Scale = {scale}', fontsize=11)
    ax.grid(True, alpha=0.3)
    ax.set_ylim([0, 1])
    # Mark key points
    m_vals = [0.1, 1.0, 5.0]
    for m_val in m_vals:
        r_val = rational_readiness(m_val, scale)
        ax.plot(m_val, r_val, 'ro', markersize=5)
        ax.text(m_val, r_val + 0.05, f'{r_val:.2f}', ha='center', fontsize=8)

plt.tight_layout()
plt.savefig(outdir / 'variant2_rational_sweep.png', dpi=150, bbox_inches='tight')
plt.close()

# Figure 3: Logistic variants (4x4 grid of memory scales x transition widths)
fig, axes = plt.subplots(4, 4, figsize=(16, 12))
fig.suptitle('Logistic Readiness: R(m) = 1/(1 + exp((m - scale)/width))', fontsize=16, fontweight='bold')
mem_scales = [0.5, 1.0, 2.0, 5.0]
widths = [0.2, 0.5, 1.0, 2.0]
for i, m_scale in enumerate(mem_scales):
    for j, width in enumerate(widths):
        ax = axes[i, j]
        readiness = logistic_readiness(memory, m_scale, width)
        ax.plot(memory, readiness, 'r-', linewidth=2)
        ax.fill_between(memory, readiness, alpha=0.3, color='red')
        ax.set_xlabel('Memory', fontsize=9)
        ax.set_ylabel('Readiness', fontsize=9)
        ax.set_title(f'Scale={m_scale}, Width={width}', fontsize=9)
        ax.grid(True, alpha=0.3)
        ax.set_ylim([0, 1])

plt.tight_layout()
plt.savefig(outdir / 'variant3_logistic_sweep.png', dpi=150, bbox_inches='tight')
plt.close()

# Figure 4: Field-depth variants
fig, axes = plt.subplots(2, 3, figsize=(15, 10))
fig.suptitle('Field-Depth Readiness: R(d) = exp(-d/scale)', fontsize=16, fontweight='bold')
scales = [0.5, 1.0, 2.0, 5.0, 10.0, 20.0]
for idx, scale in enumerate(scales):
    ax = axes[idx // 3, idx % 3]
    readiness = field_depth_readiness(depth, scale)
    ax.plot(depth, readiness, 'm-', linewidth=2)
    ax.fill_between(depth, readiness, alpha=0.3, color='magenta')
    ax.set_xlabel('Field Depth', fontsize=10)
    ax.set_ylabel('Readiness', fontsize=10)
    ax.set_title(f'Scale = {scale}', fontsize=11)
    ax.grid(True, alpha=0.3)
    ax.set_ylim([0, 1])
    # Mark key points
    d_vals = [0.1, 1.0, 5.0]
    for d_val in d_vals:
        r_val = field_depth_readiness(d_val, scale)
        ax.plot(d_val, r_val, 'ro', markersize=5)
        ax.text(d_val, r_val + 0.05, f'{r_val:.2f}', ha='center', fontsize=8)

plt.tight_layout()
plt.savefig(outdir / 'variant4_field_depth_sweep.png', dpi=150, bbox_inches='tight')
plt.close()

# Figure 5: Combined variants (4x4 heatmaps)
fig, axes = plt.subplots(4, 4, figsize=(16, 12))
fig.suptitle('Combined Readiness: R(m,d) = exp(-m/m_scale) × exp(-d/d_scale)', fontsize=16, fontweight='bold')
mem_scales = [0.5, 1.0, 2.0, 5.0]
field_scales = [0.5, 1.0, 2.0, 5.0]
for i, m_scale in enumerate(mem_scales):
    for j, d_scale in enumerate(field_scales):
        ax = axes[i, j]
        M, D = np.meshgrid(memory, depth)
        Z = combined_readiness(M, D, m_scale, d_scale)
        cs = ax.contourf(M, D, Z, levels=20, cmap='viridis')
        ax.set_xlabel('Memory', fontsize=9)
        ax.set_ylabel('Field Depth', fontsize=9)
        ax.set_title(f'mscale={m_scale}, fscale={d_scale}', fontsize=9)
        plt.colorbar(cs, ax=ax, label='Readiness')

plt.tight_layout()
plt.savefig(outdir / 'variant5_combined_sweep.png', dpi=150, bbox_inches='tight')
plt.close()

# Figure 6: Comparison of all exponential scales on one plot
fig, ax = plt.subplots(figsize=(12, 8))
scales = [0.2, 0.5, 1.0, 2.0, 5.0, 10.0]
colors = plt.cm.Blues(np.linspace(0.4, 1.0, len(scales)))
for scale, color in zip(scales, colors):
    readiness = exponential_readiness(memory, scale)
    ax.plot(memory, readiness, linewidth=2.5, label=f'scale={scale}', color=color)
ax.set_xlabel('Memory', fontsize=12, fontweight='bold')
ax.set_ylabel('Readiness', fontsize=12, fontweight='bold')
ax.set_title('Exponential Readiness: Parameter Space Comparison', fontsize=14, fontweight='bold')
ax.legend(loc='best', fontsize=11)
ax.grid(True, alpha=0.3)
ax.set_ylim([0, 1.05])
plt.tight_layout()
plt.savefig(outdir / 'comparison_exponential.png', dpi=150, bbox_inches='tight')
plt.close()

# Figure 7: Comparison of all rational scales on one plot
fig, ax = plt.subplots(figsize=(12, 8))
for scale, color in zip(scales, colors):
    readiness = rational_readiness(memory, scale)
    ax.plot(memory, readiness, linewidth=2.5, label=f'scale={scale}', color=color)
ax.set_xlabel('Memory', fontsize=12, fontweight='bold')
ax.set_ylabel('Readiness', fontsize=12, fontweight='bold')
ax.set_title('Rational Readiness: Parameter Space Comparison', fontsize=14, fontweight='bold')
ax.legend(loc='best', fontsize=11)
ax.grid(True, alpha=0.3)
ax.set_ylim([0, 1.05])
plt.tight_layout()
plt.savefig(outdir / 'comparison_rational.png', dpi=150, bbox_inches='tight')
plt.close()

# Figure 8: Summary - all variants at reference points
fig, ax = plt.subplots(figsize=(14, 8))

# Reference points for each variant
variants = {
    'Exponential (m=1.0)': exponential_readiness(np.array([0.1, 1.0, 5.0]), 1.0),
    'Exponential (m=2.0)': exponential_readiness(np.array([0.1, 1.0, 5.0]), 2.0),
    'Rational (m=1.0)': rational_readiness(np.array([0.1, 1.0, 5.0]), 1.0),
    'Rational (m=2.0)': rational_readiness(np.array([0.1, 1.0, 5.0]), 2.0),
    'Logistic (m=1.0, w=1.0)': logistic_readiness(np.array([0.1, 1.0, 5.0]), 1.0, 1.0),
    'Logistic (m=2.0, w=1.0)': logistic_readiness(np.array([0.1, 1.0, 5.0]), 2.0, 1.0),
    'Field-Depth (f=1.0)': field_depth_readiness(np.array([0.1, 1.0, 5.0]), 1.0),
    'Combined (m=1.0, f=1.0)': combined_readiness(np.array([0.1, 1.0, 5.0]), np.array([1.0, 1.0, 1.0]), 1.0, 1.0),
}

x_pos = np.arange(len(variants))
memory_levels = ['m=0.1', 'm=1.0', 'm=5.0']
colors_bar = ['#1f77b4', '#ff7f0e', '#2ca02c']
width = 0.25

for i, (mem_level, color) in enumerate(zip(memory_levels, colors_bar)):
    values = [variants[v][i] for v in variants.keys()]
    ax.bar(x_pos + i*width, values, width, label=mem_level, color=color, alpha=0.8)

ax.set_xlabel('Readiness Variant', fontsize=12, fontweight='bold')
ax.set_ylabel('Readiness Value', fontsize=12, fontweight='bold')
ax.set_title('Readiness Variant Comparison at Reference Points', fontsize=14, fontweight='bold')
ax.set_xticks(x_pos + width)
ax.set_xticklabels(variants.keys(), rotation=45, ha='right', fontsize=10)
ax.legend(fontsize=11)
ax.grid(True, alpha=0.3, axis='y')
ax.set_ylim([0, 1.1])
plt.tight_layout()
plt.savefig(outdir / 'comparison_all_variants.png', dpi=150, bbox_inches='tight')
plt.close()

print("✓ Readiness sweep visualizations generated:")
print("  - variant1_exponential_sweep.png")
print("  - variant2_rational_sweep.png")
print("  - variant3_logistic_sweep.png")
print("  - variant4_field_depth_sweep.png")
print("  - variant5_combined_sweep.png")
print("  - comparison_exponential.png")
print("  - comparison_rational.png")
print("  - comparison_all_variants.png")
