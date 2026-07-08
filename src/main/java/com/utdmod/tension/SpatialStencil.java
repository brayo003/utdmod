package com.utdmod.tension;

import net.minecraft.util.math.ChunkPos;

import java.util.Map;

/**
 * Reusable spatial operator that computes all local derivatives simultaneously.
 * Ported from simulation_lab SpatialStencil with chunk-neighbor lookup.
 */
public class SpatialStencil {
    public double activationGradient;
    public double memoryGradient;
    public double depthGradient;
    public double baselineGradient;

    public double activationLaplacian;
    public double memoryLaplacian;
    public double depthLaplacian;
    public double baselineLaplacian;

    public double activationCurvature;
    public double memoryCurvature;
    public double depthCurvature;
    public double baselineCurvature;

    public SpatialStencil() {
        zero();
    }

    private void zero() {
        activationGradient = 0.0;
        memoryGradient = 0.0;
        depthGradient = 0.0;
        baselineGradient = 0.0;
        activationLaplacian = 0.0;
        memoryLaplacian = 0.0;
        depthLaplacian = 0.0;
        baselineLaplacian = 0.0;
        activationCurvature = 0.0;
        memoryCurvature = 0.0;
        depthCurvature = 0.0;
        baselineCurvature = 0.0;
    }

    /**
     * Von Neumann 4-neighbor stencil from cardinal chunk neighbors.
     */
    public static SpatialStencil fromChunkNeighbors(ChunkPos pos, SubstrateState center,
                                                    Map<ChunkPos, SubstrateState> substrateStates) {
        double A = center.getActivation();
        double M = center.getMemory();
        double D = center.getFieldDepth();
        double B = center.getBaseline();

        double sumA = 0.0;
        double sumM = 0.0;
        double sumD = 0.0;
        double sumB = 0.0;
        double maxDiffA = 0.0;
        double maxDiffM = 0.0;
        double maxDiffD = 0.0;
        double maxDiffB = 0.0;
        int count = 0;

        int[][] neighbors = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] dir : neighbors) {
            ChunkPos neighborPos = new ChunkPos(pos.x + dir[0], pos.z + dir[1]);
            SubstrateState neighbor = substrateStates.get(neighborPos);
            if (neighbor == null) {
                continue;
            }
            double nA = neighbor.getActivation();
            double nM = neighbor.getMemory();
            double nD = neighbor.getFieldDepth();
            double nB = neighbor.getBaseline();

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

        SpatialStencil stencil = new SpatialStencil();
        stencil.activationGradient = maxDiffA;
        stencil.memoryGradient = maxDiffM;
        stencil.depthGradient = maxDiffD;
        stencil.baselineGradient = maxDiffB;

        if (count > 0) {
            stencil.activationLaplacian = (sumA / count) - A;
            stencil.memoryLaplacian = (sumM / count) - M;
            stencil.depthLaplacian = (sumD / count) - D;
            stencil.baselineLaplacian = (sumB / count) - B;
        }

        stencil.activationCurvature = -stencil.activationLaplacian;
        stencil.memoryCurvature = -stencil.memoryLaplacian;
        stencil.depthCurvature = -stencil.depthLaplacian;
        stencil.baselineCurvature = -stencil.baselineLaplacian;
        return stencil;
    }

    @Override
    public String toString() {
        return String.format("Stencil[grad=%.4f/%.4f/%.4f/%.4f, lap=%.4f/%.4f/%.4f/%.4f]",
                activationGradient, memoryGradient, depthGradient, baselineGradient,
                activationLaplacian, memoryLaplacian, depthLaplacian, baselineLaplacian);
    }
}
