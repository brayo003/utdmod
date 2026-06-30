import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class V2ImplementationAuditTest {

    // Helper: apply one V2-like update to provided grids using ParameterSet defaults
    private void stepV2(double[][] A, double[][] M, ParameterSet p) {
        int height = A.length;
        int width = A[0].length;
        double[][] nextA = new double[height][width];
        double[][] nextM = new double[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double a = A[y][x];
                double m = M[y][x];
                double transport = 0.0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            double nA = A[ny][nx];
                            double nM = M[ny][nx];
                            double neighborStim = nA / (1.0 + nA);
                            neighborStim *= (1.0 + p.v2MemoryInfluence * (nM / (nM + p.v2MemoryEffectK)));
                            transport += neighborStim;
                        }
                    }
                }
                transport *= p.v2TransportGain;
                transport *= (1.0 - a / (1.0 + a));

                double decayFactor = p.v2ActivationDecay * (1.0 - (m / (m + p.v2MemoryEffectK)));
                double nextActivation = Math.max(0.0, a * Math.max(0.0, 1.0 - decayFactor) + transport);
                if (p.v2ClampMax > 0.0) nextActivation = Math.min(nextActivation, p.v2ClampMax);

                double deltaMemory = p.v2MemoryGain * (a / (1.0 + a));
                double nextMemory = Math.max(0.0, m + deltaMemory - p.v2MemoryDecay * m);

                nextA[y][x] = nextActivation;
                nextM[y][x] = nextMemory;
            }
        }
        for (int y = 0; y < A.length; y++) {
            System.arraycopy(nextA[y], 0, A[y], 0, A[0].length);
            System.arraycopy(nextM[y], 0, M[y], 0, M[0].length);
        }
    }

    @Test
    void stressScenariosProduceFiniteNonNegativeAndBoundedValues() {
        ParameterSet p = new ParameterSet();
        int H = 5, W = 5;

        // scenarios to test: very high activation, very large memory, zero input (all zeros), constant input (simulate by keeping one cell high), repeated impulses (periodic high A)
        double[][] A = new double[H][W];
        double[][] M = new double[H][W];

        // 1) very high activation
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) A[y][x] = 1e6;
        for (int t = 0; t < 10; t++) stepV2(A, M, p);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            double v = A[y][x];
            assertFalse(Double.isNaN(v), "NaN produced for activation in high-A scenario");
            assertTrue(v >= 0.0, "Negative activation encountered");
            assertTrue(v <= p.v2ClampMax + 1e-9, "Activation exceeded clamp after steps");
        }

        // 2) very large memory
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { A[y][x] = 0.0; M[y][x] = 1e8; }
        for (int t = 0; t < 50; t++) stepV2(A, M, p);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            double m = M[y][x];
            assertFalse(Double.isNaN(m), "NaN produced for memory in large-M scenario");
            assertTrue(m >= 0.0, "Negative memory encountered");
        }

        // 3) zero input
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { A[y][x] = 0.0; M[y][x] = 0.0; }
        for (int t = 0; t < 20; t++) stepV2(A, M, p);
        // expect all zeros remain zero
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) assertEquals(0.0, A[y][x], 1e-12);

        // 4) constant input (simulate by keeping one cell with high external each step via initial high A and re-inject)
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { A[y][x] = 0.0; M[y][x] = 0.0; }
        for (int t = 0; t < 30; t++) {
            A[2][2] += 1.0; // re-inject constant impulse
            stepV2(A, M, p);
        }
        // ensure finite
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            assertFalse(Double.isInfinite(A[y][x]) || Double.isNaN(A[y][x]));
            assertTrue(A[y][x] >= 0.0);
        }

        // 5) repeated impulses: impulse every 5 ticks
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) { A[y][x] = 0.0; M[y][x] = 0.0; }
        for (int t = 0; t < 100; t++) {
            if (t % 5 == 0) A[1][1] += 2.0;
            stepV2(A, M, p);
        }
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            assertFalse(Double.isNaN(A[y][x]));
            assertTrue(A[y][x] >= 0.0);
            assertFalse(Double.isInfinite(M[y][x]) || Double.isNaN(M[y][x]));
            assertTrue(M[y][x] >= 0.0);
        }
    }
}
