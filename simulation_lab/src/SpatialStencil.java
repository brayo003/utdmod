/**
 * Reusable spatial operator that computes all local derivatives simultaneously.
 * Gradient magnitude, Laplacian, and curvature for all four state variables.
 * Computed once per cell, then used by all equations that need spatial information.
 */
public class SpatialStencil {
    public double activationGradient;    // ||∇A||
    public double memoryGradient;        // ||∇M||
    public double depthGradient;         // ||∇D||
    public double baselineGradient;      // ||∇B||

    public double activationLaplacian;   // ∇²A (average of neighbors - center)
    public double memoryLaplacian;       // ∇²M
    public double depthLaplacian;        // ∇²D
    public double baselineLaplacian;     // ∇²B

    public double activationCurvature;   // scalar curvature indicator
    public double memoryCurvature;
    public double depthCurvature;
    public double baselineCurvature;

    /**
     * Construct from complete state grids and current position.
     * Computes all spatial derivatives from 4-neighbor (von Neumann) stencil.
     */
    public SpatialStencil(int x, int y,
                          double[][] activation, double[][] memory, 
                          double[][] depth, double[][] baseline) {
        int height = activation.length;
        int width = activation[0].length;

        // Current cell values
        double A = activation[y][x];
        double M = memory[y][x];
        double D = depth[y][x];
        double B = baseline[y][x];

        // Compute 4-neighbor averages and sums
        double sumA = 0, sumM = 0, sumD = 0, sumB = 0;
        double maxDiffA = 0, maxDiffM = 0, maxDiffD = 0, maxDiffB = 0;
        int count = 0;

        // Von Neumann neighborhood: up, down, left, right
        int[][] neighbors = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] dir : neighbors) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                double nA = activation[ny][nx];
                double nM = memory[ny][nx];
                double nD = depth[ny][nx];
                double nB = baseline[ny][nx];

                sumA += nA;
                sumM += nM;
                sumD += nD;
                sumB += nB;

                maxDiffA = Math.max(maxDiffA, Math.abs(nA - A));
                maxDiffM = Math.max(maxDiffM, Math.abs(nM - M));
                maxDiffD = Math.max(maxDiffD, Math.abs(nD - D));
                maxDiffB = Math.max(maxDiffB, Math.abs(nB - B));

                count++;
            }
        }

        // Gradients: maximum difference to any neighbor
        this.activationGradient = maxDiffA;
        this.memoryGradient = maxDiffM;
        this.depthGradient = maxDiffD;
        this.baselineGradient = maxDiffB;

        // Laplacians: neighbor average - center value
        if (count > 0) {
            this.activationLaplacian = (sumA / count) - A;
            this.memoryLaplacian = (sumM / count) - M;
            this.depthLaplacian = (sumD / count) - D;
            this.baselineLaplacian = (sumB / count) - B;
        } else {
            this.activationLaplacian = 0;
            this.memoryLaplacian = 0;
            this.depthLaplacian = 0;
            this.baselineLaplacian = 0;
        }

        // Curvature: second derivative, negative Laplacian indicates local maximum
        this.activationCurvature = -this.activationLaplacian;
        this.memoryCurvature = -this.memoryLaplacian;
        this.depthCurvature = -this.depthLaplacian;
        this.baselineCurvature = -this.baselineLaplacian;
    }

    @Override
    public String toString() {
        return String.format("Stencil[grad=%.4f/%.4f/%.4f/%.4f, lap=%.4f/%.4f/%.4f/%.4f]",
                activationGradient, memoryGradient, depthGradient, baselineGradient,
                activationLaplacian, memoryLaplacian, depthLaplacian, baselineLaplacian);
    }
}
