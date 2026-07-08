package com.utdmod.tension;

import net.minecraft.util.math.ChunkPos;

import java.util.Map;
import java.util.function.Function;

/**
 * Transport kernel ported verbatim from simulation_lab GridSimulationV2.computeTransport.
 */
public final class SubstrateTransport {
    private SubstrateTransport() {}

    @FunctionalInterface
    public interface NeighborLookup {
        SubstrateState get(ChunkPos pos);
    }

    public static double computeIncoming(ChunkPos pos, SubstrateState center, ParameterSet parameters,
                                         NeighborLookup lookup) {
        double A = center.getActivation();
        double M = center.getMemory();
        double C = center.getFieldDepth();
        double B = center.getBaseline();

        double transport = 0.0;
        int maxSearchRadius = 1 + (int) Math.floor(parameters.v3FieldDepthRadius);
        for (int dz = -maxSearchRadius; dz <= maxSearchRadius; dz++) {
            for (int dx = -maxSearchRadius; dx <= maxSearchRadius; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                double distance = Math.hypot(dx, dz);
                if (distance > maxSearchRadius) {
                    continue;
                }
                ChunkPos neighborPos = new ChunkPos(pos.x + dx, pos.z + dz);
                SubstrateState neighborState = lookup.get(neighborPos);
                if (neighborState == null) {
                    continue;
                }
                double nA = neighborState.getActivation();
                double nM = neighborState.getMemory();
                double nC = neighborState.getFieldDepth();
                double nB = neighborState.getBaseline();
                int sourceRadius = 1 + (int) Math.floor(parameters.v3FieldDepthRadius * nC / (nC + parameters.v3FieldDepthEffectK));
                if (distance > sourceRadius) {
                    continue;
                }
                double neighborStim = nA / (1.0 + nA);
                neighborStim *= (1.0 + parameters.v2MemoryInfluence * (nM / (nM + parameters.v2MemoryEffectK)));
                double weight;
                if (parameters.v3DistanceDecay > 0.0) {
                    weight = Math.exp(-distance / parameters.v3DistanceDecay);
                } else {
                    weight = 1.0 / (1.0 + distance);
                }
                double depthDifference = Math.max(0.0, nC - C);
                double gradientBoost = 1.0 + parameters.v3DepthGradientGain * depthDifference;
                double stateModifier = computeTransportModifier(A, M, C, B, nA, nM, nC, nB, parameters);
                transport += neighborStim * weight * gradientBoost * stateModifier;
            }
        }
        transport *= parameters.v2TransportGain;
        transport *= (1.0 - A / (1.0 + A));
        double depthModulation = computeDepthModulation(C, parameters);
        transport *= (1.0 + parameters.v3DepthTransportGain * depthModulation);
        return transport;
    }

    public static double computeIncoming(ChunkPos pos, SubstrateState center, ParameterSet parameters,
                                         Map<ChunkPos, SubstrateState> substrateStates) {
        return computeIncoming(pos, center, parameters, substrateStates::get);
    }

    static double computeTransportModifier(double activation, double memory, double depth, double baseline,
                                           double neighborActivation, double neighborMemory, double neighborDepth,
                                           double neighborBaseline, ParameterSet parameters) {
        double localState = activation + 0.35 * memory + 0.20 * depth + 0.15 * baseline;
        double neighborState = neighborActivation + 0.35 * neighborMemory + 0.20 * neighborDepth + 0.15 * neighborBaseline;
        double stateCompatibility = 1.0 + 0.20 * Math.tanh(neighborState - localState);
        double localEnergy = sigmoid(localState);
        double neighborEnergy = sigmoid(neighborState);
        double depthFeedback = computeDepthFeedback(depth, parameters);
        double stateTerm = 0.20 + 0.20 * localEnergy + 0.20 * neighborEnergy + 0.10 * stateCompatibility - 0.08 * depthFeedback;
        return clamp(stateTerm, 0.10, 1.20);
    }

    static double computeDepthModulation(double depth, ParameterSet parameters) {
        double saturatingDepth = Math.max(0.0, depth);
        double depthScale = Math.max(1e-6, parameters.v3FieldDepthEffectK);
        return Math.min(1.0, saturatingDepth / (saturatingDepth + depthScale));
    }

    static double computeDepthFeedback(double depth, ParameterSet parameters) {
        double depthScale = Math.max(1e-6, parameters.v3FieldDepthEffectK);
        return Math.min(1.0, depth / (depth + depthScale));
    }

    public static int computeTransportRadius(double fieldDepth, ParameterSet parameters) {
        double effectiveDepth = Math.max(0.0, fieldDepth);
        return 1 + (int) Math.floor(parameters.v3FieldDepthRadius * effectiveDepth / (effectiveDepth + parameters.v3FieldDepthEffectK));
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
