package com.utdmod.core;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A connected component of active chunks.
 * Regions are views over substrate history — they summarize, not own, state.
 * All tension values are derived metrics computed from substrate variables.
 */
public final class Region {
    private final int id;
    private final RegistryKey<World> worldKey;
    private final Set<ChunkPos> chunks;
    private final int chunkCount;

    // Derived tension statistics (T = computeLocalTension() per chunk)
    private final double meanTension;
    private final double maxTension;
    private final double varianceTension;
    private final double activeFraction;

    // Substrate field statistics
    private final double meanDepth;
    private final double meanMemory;
    private final double meanActivation;
    private final double maxActivation;

    // Legacy geometry
    private final int fracturedChunks;
    private final int decoupledChunks;
    private final int stormChunks;
    private final double centroidX;
    private final double centroidZ;

    private int age;
    private long lastUpdatedTick;
    private double maturity;
    private double potential;

    private static final double MATURITY_ALPHA = 0.005;
    private static final double FRACTURED_DENSITY_BONUS = 0.10;
    private static final double STORM_DENSITY_BONUS = 0.14;
    private static final double CONTRIBUTION_FRACTURED_BONUS = 0.08;
    private static final double CONTRIBUTION_STORM_BONUS = 0.12;

    public Region(
        int id,
        RegistryKey<World> worldKey,
        Set<ChunkPos> chunks,
        double meanTension,
        double maxTension,
        double varianceTension,
        double activeFraction,
        double meanDepth,
        double meanMemory,
        double meanActivation,
        double maxActivation,
        int fracturedChunks,
        int decoupledChunks,
        int stormChunks,
        double centroidX,
        double centroidZ,
        int age,
        long lastUpdatedTick,
        double maturity
    ) {
        this.id = id;
        this.worldKey = worldKey;
        this.chunks = Collections.unmodifiableSet(new HashSet<>(chunks));
        this.chunkCount = this.chunks.size();
        this.meanTension = meanTension;
        this.maxTension = maxTension;
        this.varianceTension = varianceTension;
        this.activeFraction = activeFraction;
        this.meanDepth = meanDepth;
        this.meanMemory = meanMemory;
        this.meanActivation = meanActivation;
        this.maxActivation = maxActivation;
        this.fracturedChunks = fracturedChunks;
        this.decoupledChunks = decoupledChunks;
        this.stormChunks = stormChunks;
        this.centroidX = centroidX;
        this.centroidZ = centroidZ;
        this.age = Math.max(1, age);
        this.lastUpdatedTick = lastUpdatedTick;
        this.maturity = Math.max(0.0, maturity);
        this.potential = 0.0;
    }

    public int getId() { return id; }
    public RegistryKey<World> getWorldKey() { return worldKey; }
    public Set<ChunkPos> getChunks() { return chunks; }
    public int getChunkCount() { return chunkCount; }

    /** Mean derived tension T = F(A,M,D,C,R) over all region chunks. */
    public double getMeanTension() { return meanTension; }
    /** Peak derived tension over all region chunks. */
    public double getMaxTension() { return maxTension; }
    /** Variance of derived tension — distinguishes uniform vs hotspot. */
    public double getVarianceTension() { return varianceTension; }
    /** Fraction of chunks above ACTIVE_TENSION_THRESHOLD. */
    public double getActiveFraction() { return activeFraction; }
    /** Mean substrate field depth — historical age of the region. */
    public double getMeanDepth() { return meanDepth; }
    /** Mean substrate memory — persistence of disturbance. */
    public double getMeanMemory() { return meanMemory; }
    /** Mean substrate activation. */
    public double getMeanActivation() { return meanActivation; }
    /** Peak substrate activation. */
    public double getMaxActivation() { return maxActivation; }

    /**
     * @deprecated Use {@link #getMeanTension()} — tension is now a derived metric.
     */
    @Deprecated
    public double getAverageTension() { return meanTension; }

    public int getFracturedChunks() { return fracturedChunks; }
    public int getDecoupledChunks() { return decoupledChunks; }
    public int getStormChunks() { return stormChunks; }
    public double getCentroidX() { return centroidX; }
    public double getCentroidZ() { return centroidZ; }
    public int getAge() { return age; }
    public long getLastUpdatedTick() { return lastUpdatedTick; }
    public double getMaturity() { return maturity; }
    public double getPotential() { return potential; }

    public double getFracturedDensity() {
        return chunkCount > 0 ? fracturedChunks / (double) chunkCount : 0.0;
    }

    public double getStormDensity() {
        return chunkCount > 0 ? stormChunks / (double) chunkCount : 0.0;
    }

    public double computePotential() {
        double sizeScale = Math.sqrt(Math.max(1, chunkCount));
        double densityBonus = 1.0 + FRACTURED_DENSITY_BONUS * getFracturedDensity()
                                  + STORM_DENSITY_BONUS * getStormDensity();
        return meanTension * sizeScale * densityBonus;
    }

    public void updateMaturity(long tick) {
        double p = computePotential();
        this.potential = p;
        this.maturity += MATURITY_ALPHA * (p - this.maturity);
        this.lastUpdatedTick = tick;
    }

    public double getContribution() {
        double densityBonus = 1.0 + CONTRIBUTION_FRACTURED_BONUS * getFracturedDensity()
                                  + CONTRIBUTION_STORM_BONUS * getStormDensity();
        return maturity * densityBonus;
    }

    public RegionState getState() {
        if (maturity > 2.25 && age > 3200) {
            return RegionState.ANCIENT;
        }
        if (meanTension >= 1.05 && chunkCount >= 6
                && fracturedChunks >= Math.max(1, chunkCount / 8)) {
            return RegionState.FRACTURED;
        }
        if (meanTension >= 0.78 || maturity > 1.15
                || getFracturedDensity() > 0.16 || getStormDensity() > 0.12) {
            return RegionState.STRAINED;
        }
        if (meanTension >= 0.35 || maturity > 0.5 || age > 900) {
            return RegionState.GROWING;
        }
        return RegionState.DORMANT;
    }
}
