import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class FieldDepthSweepTest {

    // Helper: deep copy
    private static double[][] copyGrid(double[][] src) {
        double[][] dst = new double[src.length][src[0].length];
        for (int i = 0; i < src.length; i++) System.arraycopy(src[i], 0, dst[i], 0, src[0].length);
        return dst;
    }

    // One-step update copied from GridSimulationV2 (kept identical to implementation)
    private static void stepOnce(double[][] activation, double[][] memory, double[][] depth, ParameterSet parameters, int tick, int injectX, int injectY, boolean inject) {
        int height = activation.length;
        int width = activation[0].length;
        double[][] nextA = new double[height][width];
        double[][] nextM = new double[height][width];
        double[][] nextC = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double A = activation[y][x];
                double M = memory[y][x];
                double C = depth[y][x];
                double transport = 0.0;
                int maxSearchRadius = 1 + (int) Math.floor(parameters.v3FieldDepthRadius);
                for (int dy = -maxSearchRadius; dy <= maxSearchRadius; dy++) {
                    for (int dx = -maxSearchRadius; dx <= maxSearchRadius; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            double distance = Math.sqrt(dx * dx + dy * dy);
                            if (distance > maxSearchRadius) continue;
                            double nA = activation[ny][nx];
                            double nM = memory[ny][nx];
                            double nC = depth[ny][nx];
                            int sourceRadius = 1 + (int) Math.floor(parameters.v3FieldDepthRadius * nC / (nC + parameters.v3FieldDepthEffectK));
                            if (distance > sourceRadius) continue;
                            double neighborStim = nA / (1.0 + nA);
                            neighborStim *= (1.0 + parameters.v2MemoryInfluence * (nM / (nM + parameters.v2MemoryEffectK)));
                            double weight = 1.0;
                            if (parameters.v3DistanceDecay > 0.0) {
                                weight = Math.exp(-distance / parameters.v3DistanceDecay);
                            } else {
                                weight = 1.0 / (1.0 + distance);
                            }
                            transport += neighborStim * weight;
                        }
                    }
                }
                transport *= parameters.v2TransportGain;
                transport *= (1.0 - A / (1.0 + A));

                double external = 0.0;
                if (inject && x == injectX && y == injectY) external = 1.0;

                double memTerm = (1.0 - (M / (M + parameters.v2MemoryEffectK)));
                double decayFactor = parameters.v2ActivationDecay * memTerm;
                double nextActivation = Math.max(0.0, A * Math.max(0.0, 1.0 - decayFactor) + transport + external);
                if (parameters.v2ClampMax > 0.0) nextActivation = Math.min(nextActivation, parameters.v2ClampMax);

                double deltaMemory = parameters.v2MemoryGain * (A / (1.0 + A));
                double nextMemory = Math.max(0.0, M + deltaMemory - parameters.v2MemoryDecay * M);

                double deltaDepth = parameters.v3FieldDepthGain * (A * M);
                if (deltaDepth > 1.0) deltaDepth = 1.0;
                double nextDepth = Math.max(0.0, C + deltaDepth - parameters.v3FieldDepthDecay * C);

                nextA[y][x] = nextActivation;
                nextM[y][x] = nextMemory;
                nextC[y][x] = nextDepth;
            }
        }
        for (int y = 0; y < height; y++) {
            System.arraycopy(nextA[y], 0, activation[y], 0, width);
            System.arraycopy(nextM[y], 0, memory[y], 0, width);
            System.arraycopy(nextC[y], 0, depth[y], 0, width);
        }
    }

    @Test
    void sweepFieldDepthParametersProduceCSVs() throws Exception {
        int W = 21, H = 21;
        int buildTicks = 200;
        int probeTicks = 100;
        int centerX = W/2, centerY = H/2;

        ParameterSet base = new ParameterSet();

        // Parameter sweeps (independent)
        double[] gains = new double[]{0.0, 1e-5, 1e-4, 1e-3, 1e-2};
        double[] decays = new double[]{1e-6, 1e-5, 1e-4, 1e-3};
        double[] radii = new double[]{0.0, 1.0, 2.0, 3.0};
        double[] distanceDecays = new double[]{0.1, 0.5, 1.0, 2.0};
        double[] effectKs = new double[]{0.01, 0.1, 1.0};

        File outDir = new File("run/fielddepth_sweeps");
        outDir.mkdirs();

        // Sweep gain
        FileWriter fwGain = new FileWriter(new File(outDir, "sweep_v3FieldDepthGain.csv"));
        fwGain.write("value,totalActivationPeak,totalMemoryTotal,fieldDepthTotal,effectiveRadiusPeak,propagationRadiusPeak,hotspotHalfLife,probeTimeToPeak,baselineTimeToPeak,reactivationDelay\n");
        for (double val : gains) {
            ParameterSet p = base.copy();
            p.v3FieldDepthGain = val;
            // build phase
            double[][] A = new double[H][W];
            double[][] M = new double[H][W];
            double[][] C = new double[H][W];
            for (int t = 0; t < buildTicks; t++) {
                boolean inject = (t % 10 == 0);
                if (inject) A[centerY][centerX] += 1.0; // persistent impulse
                stepOnce(A, M, C, p, t, centerX, centerY, false);
            }
            double totalMemory = 0.0; double totalDepth = 0.0;
            for (int y=0;y<H;y++) for (int x=0;x<W;x++) { totalMemory += M[y][x]; totalDepth += C[y][x]; }

            // probe withDepth
            double[][] Aprobe = new double[H][W];
            double[][] Mprobe = copyGrid(M);
            double[][] Cprobe = copyGrid(C);
            List<Double> maxAHistory = new ArrayList<>();
            double propagationRadiusPeak = 0.0;
            int effectiveRadiusPeak = 0;
            int probeTimeToPeak = 0;
            for (int t=0;t<probeTicks;t++) {
                boolean inject = (t==0);
                double currentC = Cprobe[centerY][centerX];
                int effectiveRadius = 1 + (int) Math.floor(p.v3FieldDepthRadius * currentC / (currentC + p.v3FieldDepthEffectK));
                effectiveRadiusPeak = Math.max(effectiveRadiusPeak, effectiveRadius);
                stepOnce(Aprobe, Mprobe, Cprobe, p, t, centerX, centerY, inject);
                double maxA = 0.0; for (int y=0;y<H;y++) for (int x=0;x<W;x++) maxA = Math.max(maxA, Aprobe[y][x]);
                maxAHistory.add(maxA);
                double radius = MetricEvaluator.diffusionRadius(Aprobe, p.regionActiveThreshold, maxA, centerX, centerY);
                propagationRadiusPeak = Math.max(propagationRadiusPeak, radius);
            }
            double totalActivationPeak = maxOfList(maxAHistory);
            int halfLife = (int) MetricEvaluator.halfLife(maxAHistory);
            probeTimeToPeak = indexOfMax(maxAHistory);

            // baseline (no depth/memory)
            double[][] Abase = new double[H][W];
            double[][] Mbase = new double[H][W];
            double[][] Cbase = new double[H][W];
            List<Double> baseMaxHist = new ArrayList<>();
            for (int t=0;t<probeTicks;t++) {
                boolean inject = (t==0);
                stepOnce(Abase, Mbase, Cbase, p, t, centerX, centerY, inject);
                double maxA = 0.0; for (int y=0;y<H;y++) for (int x=0;x<W;x++) maxA = Math.max(maxA, Abase[y][x]);
                baseMaxHist.add(maxA);
            }
            int baselinePeakIdx = indexOfMax(baseMaxHist);

            fwGain.write(String.format("%g,%.6f,%.6f,%.6f,%d,%.6f,%d,%d,%d,%d\n", val, totalActivationPeak, totalMemory, totalDepth, effectiveRadiusPeak, propagationRadiusPeak, halfLife, probeTimeToPeak, baselinePeakIdx, baselinePeakIdx - probeTimeToPeak));
        }
        fwGain.close();

        // Sweep decay
        FileWriter fwDecay = new FileWriter(new File(outDir, "sweep_v3FieldDepthDecay.csv"));
        fwDecay.write("value,totalActivationPeak,totalMemoryTotal,fieldDepthTotal,effectiveRadiusPeak,propagationRadiusPeak,hotspotHalfLife,probeTimeToPeak,baselineTimeToPeak,reactivationDelay\n");
        for (double val : decays) {
            ParameterSet p = base.copy();
            p.v3FieldDepthDecay = val;
            double[][] A = new double[H][W];
            double[][] M = new double[H][W];
            double[][] C = new double[H][W];
            for (int t = 0; t < buildTicks; t++) {
                boolean inject = (t % 10 == 0);
                if (inject) A[centerY][centerX] += 1.0;
                stepOnce(A, M, C, p, t, centerX, centerY, false);
            }
            double totalMemory = 0.0; double totalDepth = 0.0;
            for (int y=0;y<H;y++) for (int x=0;x<W;x++) { totalMemory += M[y][x]; totalDepth += C[y][x]; }
            double[][] Aprobe = new double[H][W];
            double[][] Mprobe = copyGrid(M);
            double[][] Cprobe = copyGrid(C);
            List<Double> maxAHistory = new ArrayList<>();
            double propagationRadiusPeak = 0.0;
            int effectiveRadiusPeak = 0;
            for (int t=0;t<probeTicks;t++) {
                boolean inject = (t==0);
                double currentC = Cprobe[centerY][centerX];
                int effectiveRadius = 1 + (int) Math.floor(p.v3FieldDepthRadius * currentC / (currentC + p.v3FieldDepthEffectK));
                effectiveRadiusPeak = Math.max(effectiveRadiusPeak, effectiveRadius);
                stepOnce(Aprobe, Mprobe, Cprobe, p, t, centerX, centerY, inject);
                double maxA = 0.0; for (int y=0;y<H;y++) for (int x=0;x<W;x++) maxA = Math.max(maxA, Aprobe[y][x]);
                maxAHistory.add(maxA);
                double radius = MetricEvaluator.diffusionRadius(Aprobe, p.regionActiveThreshold, maxA, centerX, centerY);
                propagationRadiusPeak = Math.max(propagationRadiusPeak, radius);
            }
            double totalActivationPeak = maxOfList(maxAHistory);
            int halfLife = (int) MetricEvaluator.halfLife(maxAHistory);
            int probeTimeToPeak = indexOfMax(maxAHistory);
            double[][] Abase = new double[H][W];
            double[][] Mbase = new double[H][W];
            double[][] Cbase = new double[H][W];
            List<Double> baseMaxHist = new ArrayList<>();
            for (int t=0;t<probeTicks;t++) {
                boolean inject = (t==0);
                stepOnce(Abase, Mbase, Cbase, p, t, centerX, centerY, inject);
                double maxA = 0.0; for (int y=0;y<H;y++) for (int x=0;x<W;x++) maxA = Math.max(maxA, Abase[y][x]);
                baseMaxHist.add(maxA);
            }
            int baselinePeakIdx = indexOfMax(baseMaxHist);
            fwDecay.write(String.format("%g,%.6f,%.6f,%.6f,%d,%.6f,%d,%d,%d,%d\n", val, totalActivationPeak, totalMemory, totalDepth, effectiveRadiusPeak, propagationRadiusPeak, halfLife, probeTimeToPeak, baselinePeakIdx, baselinePeakIdx - probeTimeToPeak));
        }
        fwDecay.close();

        // Sweep influence
        FileWriter fwRadius = new FileWriter(new File(outDir, "sweep_v3FieldDepthRadius.csv"));
        fwRadius.write("value,totalActivationPeak,totalMemoryTotal,fieldDepthTotal,effectiveRadiusPeak,propagationRadiusPeak,hotspotHalfLife,probeTimeToPeak,baselineTimeToPeak,reactivationDelay\n");
        for (double val : radii) {
            ParameterSet p = base.copy();
            p.v3FieldDepthRadius = val;
            double[][] A = new double[H][W];
            double[][] M = new double[H][W];
            double[][] C = new double[H][W];
            for (int t = 0; t < buildTicks; t++) {
                boolean inject = (t % 10 == 0);
                if (inject) A[centerY][centerX] += 1.0;
                stepOnce(A, M, C, p, t, centerX, centerY, false);
            }
            double totalMemory = 0.0; double totalDepth = 0.0;
            for (int y=0;y<H;y++) for (int x=0;x<W;x++) { totalMemory += M[y][x]; totalDepth += C[y][x]; }
            double[][] Aprobe = new double[H][W];
            double[][] Mprobe = copyGrid(M);
            double[][] Cprobe = copyGrid(C);
            List<Double> maxAHistory = new ArrayList<>();
            double propagationRadiusPeak = 0.0;
            int effectiveRadiusPeak = 0;
            for (int t=0;t<probeTicks;t++) {
                boolean inject = (t==0);
                double currentC = Cprobe[centerY][centerX];
                int effectiveRadius = 1 + (int) Math.floor(p.v3FieldDepthRadius * currentC / (currentC + p.v3FieldDepthEffectK));
                effectiveRadiusPeak = Math.max(effectiveRadiusPeak, effectiveRadius);
                stepOnce(Aprobe, Mprobe, Cprobe, p, t, centerX, centerY, inject);
                double maxA = 0.0; for (int y=0;y<H;y++) for (int x=0;x<W;x++) maxA = Math.max(maxA, Aprobe[y][x]);
                maxAHistory.add(maxA);
                double radius = MetricEvaluator.diffusionRadius(Aprobe, p.regionActiveThreshold, maxA, centerX, centerY);
                propagationRadiusPeak = Math.max(propagationRadiusPeak, radius);
            }
            double totalActivationPeak = maxOfList(maxAHistory);
            int halfLife = (int) MetricEvaluator.halfLife(maxAHistory);
            int probeTimeToPeak = indexOfMax(maxAHistory);
            double[][] Abase = new double[H][W];
            double[][] Mbase = new double[H][W];
            double[][] Cbase = new double[H][W];
            List<Double> baseMaxHist = new ArrayList<>();
            for (int t=0;t<probeTicks;t++) {
                boolean inject = (t==0);
                stepOnce(Abase, Mbase, Cbase, p, t, centerX, centerY, inject);
                double maxA = 0.0; for (int y=0;y<H;y++) for (int x=0;x<W;x++) maxA = Math.max(maxA, Abase[y][x]);
                baseMaxHist.add(maxA);
            }
            int baselinePeakIdx = indexOfMax(baseMaxHist);
            fwRadius.write(String.format("%g,%.6f,%.6f,%.6f,%d,%.6f,%d,%d,%d,%d\n", val, totalActivationPeak, totalMemory, totalDepth, effectiveRadiusPeak, propagationRadiusPeak, halfLife, probeTimeToPeak, baselinePeakIdx, baselinePeakIdx - probeTimeToPeak));
        }
        fwRadius.close();

        // Sweep distance decay
        FileWriter fwDistance = new FileWriter(new File(outDir, "sweep_v3DistanceDecay.csv"));
        fwDistance.write("value,totalActivationPeak,totalMemoryTotal,fieldDepthTotal,effectiveRadiusPeak,propagationRadiusPeak,hotspotHalfLife,probeTimeToPeak,baselineTimeToPeak,reactivationDelay\n");
        for (double val : distanceDecays) {
            ParameterSet p = base.copy();
            p.v3DistanceDecay = val;
            double[][] A = new double[H][W];
            double[][] M = new double[H][W];
            double[][] C = new double[H][W];
            for (int t = 0; t < buildTicks; t++) {
                boolean inject = (t % 10 == 0);
                if (inject) A[centerY][centerX] += 1.0;
                stepOnce(A, M, C, p, t, centerX, centerY, false);
            }
            double totalMemory = 0.0; double totalDepth = 0.0;
            for (int y=0;y<H;y++) for (int x=0;x<W;x++) { totalMemory += M[y][x]; totalDepth += C[y][x]; }
            double[][] Aprobe = new double[H][W];
            double[][] Mprobe = copyGrid(M);
            double[][] Cprobe = copyGrid(C);
            List<Double> maxAHistory = new ArrayList<>();
            double propagationRadiusPeak = 0.0;
            int effectiveRadiusPeak = 0;
            for (int t=0;t<probeTicks;t++) {
                boolean inject = (t==0);
                double currentC = Cprobe[centerY][centerX];
                int effectiveRadius = 1 + (int) Math.floor(p.v3FieldDepthRadius * currentC / (currentC + p.v3FieldDepthEffectK));
                effectiveRadiusPeak = Math.max(effectiveRadiusPeak, effectiveRadius);
                stepOnce(Aprobe, Mprobe, Cprobe, p, t, centerX, centerY, inject);
                double maxA = 0.0; for (int y=0;y<H;y++) for (int x=0;x<W;x++) maxA = Math.max(maxA, Aprobe[y][x]);
                maxAHistory.add(maxA);
                double radius = MetricEvaluator.diffusionRadius(Aprobe, p.regionActiveThreshold, maxA, centerX, centerY);
                propagationRadiusPeak = Math.max(propagationRadiusPeak, radius);
            }
            double totalActivationPeak = maxOfList(maxAHistory);
            int halfLife = (int) MetricEvaluator.halfLife(maxAHistory);
            int probeTimeToPeak = indexOfMax(maxAHistory);
            double[][] Abase = new double[H][W];
            double[][] Mbase = new double[H][W];
            double[][] Cbase = new double[H][W];
            List<Double> baseMaxHist = new ArrayList<>();
            for (int t=0;t<probeTicks;t++) {
                boolean inject = (t==0);
                stepOnce(Abase, Mbase, Cbase, p, t, centerX, centerY, inject);
                double maxA = 0.0; for (int y=0;y<H;y++) for (int x=0;x<W;x++) maxA = Math.max(maxA, Abase[y][x]);
                baseMaxHist.add(maxA);
            }
            int baselinePeakIdx = indexOfMax(baseMaxHist);
            fwDistance.write(String.format("%g,%.6f,%.6f,%.6f,%d,%.6f,%d,%d,%d,%d\n", val, totalActivationPeak, totalMemory, totalDepth, effectiveRadiusPeak, propagationRadiusPeak, halfLife, probeTimeToPeak, baselinePeakIdx, baselinePeakIdx - probeTimeToPeak));
        }
        fwDistance.close();
    }

    private static double maxOfList(List<Double> l) { double m=0.0; for (Double d: l) m = Math.max(m, d); return m; }
    private static int indexOfMax(List<Double> l) { double m=-Double.MAX_VALUE; int idx=0; for (int i=0;i<l.size();i++) if (l.get(i)>m) { m=l.get(i); idx=i; } return idx; }
}
