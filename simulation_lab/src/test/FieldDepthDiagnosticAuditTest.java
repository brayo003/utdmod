import org.junit.jupiter.api.Test;

public class FieldDepthDiagnosticAuditTest {
    @Test
    void logCenterCellFieldDepthTransport() {
        int W = 11;
        int H = 11;
        int TICKS = 30;
        int centerX = W / 2;
        int centerY = H / 2;

        ParameterSet p = new ParameterSet();
        p.v3FieldDepthGain = 0.01;
        p.v3FieldDepthDecay = 0.0001;
        p.v3FieldDepthRadius = 3.0;
        p.v3FieldDepthEffectK = 0.1;
        p.v3DistanceDecay = 1.0;

        double[][] A = new double[H][W];
        double[][] M = new double[H][W];
        double[][] C = new double[H][W];
        A[centerY][centerX] = 1.0;

        System.out.println("=== FieldDepth Diagnostic Audit ===");
        System.out.println("Parameters: v3FieldDepthGain=" + p.v3FieldDepthGain +
                ", v3FieldDepthDecay=" + p.v3FieldDepthDecay +
                ", v3FieldDepthRadius=" + p.v3FieldDepthRadius +
                ", v3FieldDepthEffectK=" + p.v3FieldDepthEffectK +
                ", v3DistanceDecay=" + p.v3DistanceDecay);
        System.out.println("tick,centerA,centerM,centerC,centerEffectiveRadius,neighborsConsidered,ring1,ring2,ring3,ring4,propagationRadius");

        for (int t = 0; t < TICKS; t++) {
            double centerA = A[centerY][centerX];
            double centerM = M[centerY][centerX];
            double centerC = C[centerY][centerX];
            int centerEffectiveRadius = 1 + (int) Math.floor(p.v3FieldDepthRadius * centerC / (centerC + p.v3FieldDepthEffectK));
            int maxSearchRadius = 1 + (int) Math.floor(p.v3FieldDepthRadius);
            int neighborsVisited = 0;
            double[] ringTransport = new double[5];

            System.out.println();
            System.out.println("--- tick " + t + " pre-update ---");
            System.out.println("center A=" + centerA + " M=" + centerM + " C=" + centerC + " effectiveRadius=" + centerEffectiveRadius);

            for (int dy = -maxSearchRadius; dy <= maxSearchRadius; dy++) {
                for (int dx = -maxSearchRadius; dx <= maxSearchRadius; dx++) {
                    if (dx == 0 && dy == 0) continue;
                    int nx = centerX + dx;
                    int ny = centerY + dy;
                    if (nx < 0 || nx >= W || ny < 0 || ny >= H) continue;
                    double distance = Math.sqrt(dx * dx + dy * dy);
                    if (distance > maxSearchRadius) continue;
                    double nA = A[ny][nx];
                    double nM = M[ny][nx];
                    double nC = C[ny][nx];
                    int sourceRadius = 1 + (int) Math.floor(p.v3FieldDepthRadius * nC / (nC + p.v3FieldDepthEffectK));
                    boolean inside = distance <= sourceRadius;
                    double neighborStim = nA / (1.0 + nA);
                    neighborStim *= (1.0 + p.v2MemoryInfluence * (nM / (nM + p.v2MemoryEffectK)));
                    double weight;
                    if (p.v3DistanceDecay > 0.0) {
                        weight = Math.exp(-distance / p.v3DistanceDecay);
                    } else {
                        weight = 1.0 / (1.0 + distance);
                    }
                    double contributionBefore = neighborStim;
                    double contributionAfter = neighborStim * weight;
                    int ring = Math.min(4, Math.max(1, (int) Math.floor(distance)));
                    if (inside) {
                        neighborsVisited++;
                        ringTransport[ring] += contributionAfter;
                    }
                    System.out.println(String.format("source(dx=%d,dy=%d) nC=%.6f sourceRadius=%d inside=%s dist=%.3f pre=%.6f post=%.6f",
                            dx, dy, nC, sourceRadius, inside, distance, contributionBefore, contributionAfter));
                }
            }

            double maxA = 0.0;
            for (double[] row : A) {
                for (double v : row) {
                    maxA = Math.max(maxA, v);
                }
            }
            double propagationRadius = MetricEvaluator.diffusionRadius(A, 0.01, maxA, centerX, centerY);
            System.out.println(String.format("summary: neighborsVisited=%d ring1=%.6f ring2=%.6f ring3=%.6f ring4=%.6f propagationRadius=%.6f",
                    neighborsVisited, ringTransport[1], ringTransport[2], ringTransport[3], ringTransport[4], propagationRadius));
            System.out.println();
            System.out.println("--- performing full grid update ---");

            double[][] nextA = new double[H][W];
            double[][] nextM = new double[H][W];
            double[][] nextC = new double[H][W];
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
                            double distance = Math.sqrt(dx * dx + dy * dy);
                            if (distance > maxSearchRadius) continue;
                            double nA = A[ny][nx];
                            double nM = M[ny][nx];
                            double nC = C[ny][nx];
                            int sourceRadius = 1 + (int) Math.floor(p.v3FieldDepthRadius * nC / (nC + p.v3FieldDepthEffectK));
                            if (distance > sourceRadius) continue;
                            double neighborStim = nA / (1.0 + nA);
                            neighborStim *= (1.0 + p.v2MemoryInfluence * (nM / (nM + p.v2MemoryEffectK)));
                            double weight;
                            if (p.v3DistanceDecay > 0.0) {
                                weight = Math.exp(-distance / p.v3DistanceDecay);
                            } else {
                                weight = 1.0 / (1.0 + distance);
                            }
                            transport += neighborStim * weight;
                        }
                    }
                    transport *= p.v2TransportGain;
                    transport *= (1.0 - Axy / (1.0 + Axy));
                    double external = 0.0;
                    if (t == 0 && x == centerX && y == centerY) {
                        external = 1.0;
                    }
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
            A = nextA;
            M = nextM;
            C = nextC;
        }
    }
}
