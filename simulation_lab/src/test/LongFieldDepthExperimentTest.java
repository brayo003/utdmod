import org.junit.jupiter.api.Test;

public class LongFieldDepthExperimentTest {
    @Test
    void longRunFieldDepthGrowth() {
        int W = 11;
        int H = 11;
        int TICKS = 10000;
        int LOG_EVERY = 100;
        int centerX = W/2;
        int centerY = H/2;

        ParameterSet p = new ParameterSet();
        // use the modest growth parameters from the diagnostic (do not tune higher)
        p.v3FieldDepthGain = 0.01;
        p.v3FieldDepthDecay = 0.0001;
        p.v3FieldDepthRadius = 3.0;
        p.v3FieldDepthEffectK = 0.1;
        p.v3DistanceDecay = 1.0;

        double[][] A = new double[H][W];
        double[][] M = new double[H][W];
        double[][] C = new double[H][W];

        System.out.println("=== Long FieldDepth Growth Experiment ===");
        System.out.println("Parameters: v3FieldDepthGain=" + p.v3FieldDepthGain +
                ", v3FieldDepthDecay=" + p.v3FieldDepthDecay +
                ", v3FieldDepthRadius=" + p.v3FieldDepthRadius +
                ", v3FieldDepthEffectK=" + p.v3FieldDepthEffectK);
        System.out.println("tick,centerA,centerM,centerC,centerEffectiveRadius");

        // initial state: empty grids
        A[centerY][centerX] = 1.0; // initial seed

        double firstLoggedC = Double.NaN;
        double lastLoggedC = Double.NaN;
        int firstLoggedTick = -1;
        int lastLoggedTick = -1;
        boolean reached = false;

        for (int t = 0; t < TICKS; t++) {
            // Build phase: repeated injection each tick at center
            A[centerY][centerX] += 1.0; // repeated activity injection

            // perform one update (copied small-step from implementation)
            double[][] nextA = new double[H][W];
            double[][] nextM = new double[H][W];
            double[][] nextC = new double[H][W];
            int maxSearchRadius = 1 + (int) Math.floor(p.v3FieldDepthRadius);
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    double Axy = A[y][x];
                    double Mxy = M[y][x];
                    double Cxy = C[y][x];
                    double transport = 0.0;
                    for (int dy = -maxSearchRadius; dy <= maxSearchRadius; dy++) {
                        for (int dx = -maxSearchRadius; dx <= maxSearchRadius; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = x + dx;
                            int ny = y + dy;
                            if (nx < 0 || nx >= W || ny < 0 || ny >= H) continue;
                            double distance = Math.sqrt(dx*dx + dy*dy);
                            if (distance > maxSearchRadius) continue;
                            double nA = A[ny][nx];
                            double nM = M[ny][nx];
                            double nC = C[ny][nx];
                            int sourceRadius = 1 + (int) Math.floor(p.v3FieldDepthRadius * nC / (nC + p.v3FieldDepthEffectK));
                            if (distance > sourceRadius) continue;
                            double neighborStim = nA / (1.0 + nA);
                            neighborStim *= (1.0 + p.v2MemoryInfluence * (nM / (nM + p.v2MemoryEffectK)));
                            double weight = 1.0;
                            if (p.v3DistanceDecay > 0.0) weight = Math.exp(-distance / p.v3DistanceDecay);
                            else weight = 1.0 / (1.0 + distance);
                            transport += neighborStim * weight;
                        }
                    }
                    transport *= p.v2TransportGain;
                    transport *= (1.0 - Axy / (1.0 + Axy));
                    double external = 0.0;
                    double memTerm = (1.0 - (Mxy / (Mxy + p.v2MemoryEffectK)));
                    double decayFactor = p.v2ActivationDecay * memTerm;
                    double nextActivation = Math.max(0.0, Axy * Math.max(0.0, 1.0 - decayFactor) + transport + external);
                    if (p.v2ClampMax > 0.0) nextActivation = Math.min(nextActivation, p.v2ClampMax);
                    double deltaMemory = p.v2MemoryGain * (Axy / (1.0 + Axy));
                    double nextMemory = Math.max(0.0, Mxy + deltaMemory - p.v2MemoryDecay * Mxy);
                    double deltaDepth = p.v3FieldDepthGain * (Axy * Mxy);
                    if (deltaDepth > 1.0) deltaDepth = 1.0;
                    double nextDepth = Math.max(0.0, Cxy + deltaDepth - p.v3FieldDepthDecay * Cxy);
                    nextA[y][x] = nextActivation;
                    nextM[y][x] = nextMemory;
                    nextC[y][x] = nextDepth;
                }
            }
            A = nextA; M = nextM; C = nextC;

            double centerA = A[centerY][centerX];
            double centerM = M[centerY][centerX];
            double centerC = C[centerY][centerX];
            int centerEffectiveRadius = 1 + (int) Math.floor(p.v3FieldDepthRadius * centerC / (centerC + p.v3FieldDepthEffectK));

            if (t % LOG_EVERY == 0) {
                System.out.println(String.format("%d,%.6f,%.6f,%.6f,%d", t, centerA, centerM, centerC, centerEffectiveRadius));
                if (Double.isNaN(firstLoggedC)) {
                    firstLoggedC = centerC;
                    firstLoggedTick = t;
                }
                lastLoggedC = centerC;
                lastLoggedTick = t;
            }

            if (centerEffectiveRadius >= 2) {
                System.out.println("Effective radius increased to >=2 at tick " + t + ", centerC=" + centerC);
                reached = true;
                break;
            }
        }

        // compute empirical growth rate and prediction
        System.out.println("--- Summary and prediction ---");
        if (Double.isNaN(firstLoggedC) || lastLoggedTick == firstLoggedTick) {
            System.out.println("Insufficient logged data to compute growth rate.");
            return;
        }
        double deltaC = lastLoggedC - firstLoggedC;
        int deltaT = lastLoggedTick - firstLoggedTick;
        double growthPerTick = deltaC / deltaT;
        System.out.println(String.format("Observed center C: first( tick=%d C=%.6f ), last( tick=%d C=%.6f )", firstLoggedTick, firstLoggedC, lastLoggedTick, lastLoggedC));
        System.out.println(String.format("Observed growth rate (deltaC/deltaT) = %.8e per tick", growthPerTick));

        double R = p.v3FieldDepthRadius;
        double k = p.v3FieldDepthEffectK;
        double nC_required = k / (R - 1.0); // derived threshold so that R*nC/(nC+k) >= 1
        System.out.println(String.format("Threshold nC required for effective radius >=2 = %.6f", nC_required));

        if (growthPerTick <= 0.0) {
            System.out.println("Growth rate is zero or negative; predicted ticks required is effectively infinite.");
        } else {
            double currentC = lastLoggedC;
            double ticksNeeded = Math.ceil((nC_required - currentC) / growthPerTick);
            if (ticksNeeded < 0) ticksNeeded = 0;
            System.out.println(String.format("Predicted additional ticks to reach radius 2 (approx) = %.0f", ticksNeeded));
            // assume Minecraft 20 ticks per second
            double seconds = ticksNeeded / 20.0;
            double hours = seconds / 3600.0;
            System.out.println(String.format("Predicted real Minecraft play time = %.2f seconds (%.4f hours)", seconds, hours));
        }

        if (!reached) System.out.println("Effective radius did not reach 2 within " + TICKS + " ticks.");
    }
}
