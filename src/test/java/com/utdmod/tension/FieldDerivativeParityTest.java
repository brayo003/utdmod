package com.utdmod.tension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies Minecraft substrate mathematics match the laboratory canonical equations.
 */
public class FieldDerivativeParityTest {

    @Test
    public void couplingMatrixMatchesLabDefaults() {
        ParameterSet params = new ParameterSet();
        assertEquals(-0.10, params.couplingMatrix.get(0, 0), 1e-9);
        assertEquals(0.05, params.couplingMatrix.get(0, 1), 1e-9);
        assertEquals(-0.008, params.couplingMatrix.get(2, 2), 1e-9);
        assertEquals(-0.03, params.couplingMatrix.get(3, 3), 1e-9);
    }

    @Test
    public void unifiedDerivativeMatchesLabStructure() {
        ParameterSet params = new ParameterSet();
        SpatialStencil stencil = new SpatialStencil();
        stencil.activationLaplacian = -0.2;
        stencil.memoryLaplacian = 0.05;
        stencil.depthLaplacian = 0.01;
        stencil.activationGradient = 0.3;
        stencil.memoryGradient = 0.1;
        stencil.depthGradient = 0.05;
        stencil.activationCurvature = 0.2;
        stencil.memoryCurvature = -0.05;

        FieldDerivative deriv = FieldDerivative.computeUnified(
                0.4, 0.2, 0.1, 0.02, stencil, 0.5, 0.15, params);

        assertTrue(deriv.dActivation > 0.0, "external input should drive activation");
        assertTrue(deriv.dMemory >= -0.5 && deriv.dMemory <= 0.5, "memory derivative should be bounded");
        assertTrue(deriv.dDepth != 0.0 || deriv.dBaseline != 0.0, "depth or baseline should evolve");
    }

    @Test
    public void memoryFormationRespectsThreshold() {
        ParameterSet params = new ParameterSet();
        SpatialStencil stencil = new SpatialStencil();
        FieldDerivative below = FieldDerivative.computeUnified(0.01, 0.0, 0.0, 0.0, stencil, 0.0, 0.0, params);
        FieldDerivative above = FieldDerivative.computeUnified(0.5, 0.0, 0.0, 0.0, stencil, 0.0, 0.0, params);
        assertTrue(above.dMemory > below.dMemory, "activation above threshold should form more memory");
    }

    @Test
    public void transportModifierIsWithinLabBounds() {
        ParameterSet params = new ParameterSet();
        double modifier = SubstrateTransport.computeTransportModifier(
                0.3, 0.2, 0.1, 0.02, 0.4, 0.25, 0.15, 0.03, params);
        assertTrue(modifier >= 0.10 && modifier <= 1.20, "transport modifier must stay in lab clamp range");
    }

    @Test
    public void vonNeumannStencilMatchesLabFormula() {
        SubstrateState center = new SubstrateState(0.5, 0.3, 0.0, 0.1, 0.02);
        SubstrateState east = new SubstrateState(0.7, 0.2, 0.0, 0.08, 0.01);
        SubstrateState west = new SubstrateState(0.3, 0.4, 0.0, 0.12, 0.03);
        java.util.Map<net.minecraft.util.math.ChunkPos, SubstrateState> map = new java.util.HashMap<>();
        net.minecraft.util.math.ChunkPos pos = new net.minecraft.util.math.ChunkPos(0, 0);
        map.put(pos, center);
        map.put(new net.minecraft.util.math.ChunkPos(1, 0), east);
        map.put(new net.minecraft.util.math.ChunkPos(-1, 0), west);

        SpatialStencil stencil = SpatialStencil.fromChunkNeighbors(pos, center, map);
        assertEquals(0.2, stencil.activationGradient, 1e-9);
        assertEquals(0.0, stencil.activationLaplacian, 1e-9);
        assertEquals(0.0, stencil.activationCurvature, 1e-9);
    }

    @Test
    public void eulerIntegrationClampsToParameterMax() {
        ParameterSet params = new ParameterSet();
        params.v2ClampMax = 1.5;
        SubstrateState state = new SubstrateState(1.4, 0.1, 0.0, 0.05);
        SpatialStencil stencil = new SpatialStencil();
        state.applyUnifiedFieldStep(2.0, 1.0, stencil, params);
        assertTrue(state.getActivation() <= params.v2ClampMax);
        assertTrue(state.getMemory() <= params.v2ClampMax);
        assertTrue(state.getFieldDepth() <= params.v2ClampMax);
        assertTrue(state.getBaseline() <= params.v2ClampMax);
    }
}
