import matplotlib.pyplot as plt
import csv
import os

PROTOCOLS = [
    ("protocol1_single_impulse", "/tmp/protocol1_single_impulse.csv"),
    ("protocol2_continuous_100_then_1000", "/tmp/protocol2_continuous_100_then_1000.csv"),
    ("protocol3_continuous_10000_then_5000", "/tmp/protocol3_continuous_10000_then_5000.csv"),
]

outdir = "/tmp/memory_plots"
os.makedirs(outdir, exist_ok=True)

for name, path in PROTOCOLS:
    if not os.path.exists(path):
        print('Missing', path)
        continue
    ticks = []
    A = []; M = []; B = []; D = []
    formation = []; saturation = []; forgetting = []; anti = []; diff = []; net = []
    activationDrive = []; baselineDrive = []; normalizedDrive = []; depthModulation = []
    retention = []; effectiveDecayRate = []

    with open(path,'r') as f:
            # manual robust CSV parsing to avoid issues with wrapped lines
            header = f.readline().strip().split(',')
            ncols = len(header)
            buf = ''
            for line in f:
                line = line.rstrip('\n')
                if not line:
                    continue
                parts = line.split(',')
                # if row was wrapped, read subsequent lines until columns match
                while len(parts) < ncols:
                    nxt = f.readline()
                    if not nxt:
                        break
                    line += nxt.rstrip('\n')
                    parts = line.split(',')
                if len(parts) != ncols:
                    continue
                try:
                    # map by position
                    ticks.append(int(parts[0]))
                    A.append(float(parts[1])); M.append(float(parts[2])); B.append(float(parts[3])); D.append(float(parts[4]))
                    formation.append(float(parts[5])); saturation.append(float(parts[6])); forgetting.append(float(parts[7]));
                    anti.append(float(parts[8])); diff.append(float(parts[9])); net.append(float(parts[10]));
                    activationDrive.append(float(parts[11])); baselineDrive.append(float(parts[12])); normalizedDrive.append(float(parts[13])); depthModulation.append(float(parts[14]));
                    retention.append(float(parts[15])); effectiveDecayRate.append(float(parts[16]));
                except Exception:
                    continue

    # states
    plt.figure(figsize=(8,4))
    plt.plot(ticks,A,label='activation (A)')
    plt.plot(ticks,M,label='memory (M)')
    plt.plot(ticks,B,label='baseline (B)')
    plt.plot(ticks,D,label='depth (D)')
    plt.legend(); plt.title(name + ' states'); plt.tight_layout()
    plt.savefig(os.path.join(outdir, name + '_states.png'))
    plt.close()

    # memory contributions
    plt.figure(figsize=(8,4))
    plt.plot(ticks,formation,label='formation')
    plt.plot(ticks,saturation,label='saturation')
    plt.plot(ticks,forgetting,label='forgetting')
    plt.plot(ticks,anti,label='antiDiffusion')
    plt.plot(ticks,diff,label='diffusion')
    plt.plot(ticks,net,label='netdM', linewidth=2, linestyle='--')
    plt.legend(); plt.title(name + ' memory contributions'); plt.tight_layout()
    plt.savefig(os.path.join(outdir, name + '_memory_contributions.png'))
    plt.close()

    # formation internals
    plt.figure(figsize=(8,4))
    plt.plot(ticks, activationDrive, label='activationDrive')
    plt.plot(ticks, baselineDrive, label='baselineDrive')
    plt.plot(ticks, normalizedDrive, label='normalizedDrive')
    plt.plot(ticks, depthModulation, label='depthModulation')
    plt.legend(); plt.title(name + ' formation components'); plt.tight_layout()
    plt.savefig(os.path.join(outdir, name + '_formation_components.png'))
    plt.close()

    # decay internals
    plt.figure(figsize=(8,4))
    plt.plot(ticks, retention, label='retention')
    plt.plot(ticks, effectiveDecayRate, label='effectiveDecayRate')
    plt.legend(); plt.title(name + ' decay components'); plt.tight_layout()
    plt.savefig(os.path.join(outdir, name + '_decay_components.png'))
    plt.close()

    print('Wrote plots for', name)
