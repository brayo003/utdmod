package com.utdmod.core;

import net.minecraft.util.math.ChunkPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * A connected component of active chunks.
 */
public final class Region {
    private final int id;
    private final RegistryKey<World> worldKey;
    private final Set<ChunkPos> chunks;
    private final int chunkCount;
    private final double averageTension;
    private final double maxTension;
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
        double averageTension,
        double maxTension,
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
        this.averageTension = averageTension;
        this.maxTension = maxTension;
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

    public int getId() {
        return id;
    }

    public RegistryKey<World> getWorldKey() {
        return worldKey;
    }

    public Set<ChunkPos> getChunks() {
        return chunks;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public double getAverageTension() {
        return averageTension;
    }

    public double getMaxTension() {
        return maxTension;
    }

    public int getFracturedChunks() {
        return fracturedChunks;
    }

    public int getDecoupledChunks() {
        return decoupledChunks;
    }

    public int getStormChunks() {
        return stormChunks;
    }

    public double getCentroidX() {
        return centroidX;
    }

    public double getCentroidZ() {
        return centroidZ;
    }

    public int getAge() {
        return age;
    }

    public long getLastUpdatedTick() {
        return lastUpdatedTick;
    }

    public double getMaturity() {
        return maturity;
    }

    public double getPotential() {
        return potential;
    }

    public double getFracturedDensity() {
        return chunkCount > 0 ? fracturedChunks / (double) chunkCount : 0.0;
    }

    public double getStormDensity() {
        return chunkCount > 0 ? stormChunks / (double) chunkCount : 0.0;
    }

    public double computePotential() {
        double sizeScale = Math.sqrt(Math.max(1, chunkCount));
        double densityBonus = 1.0 + FRACTURED_DENSITY_BONUS * getFracturedDensity() + STORM_DENSITY_BONUS * getStormDensity();
        return averageTension * sizeScale * densityBonus;
    }

    public void updateMaturity(long tick) {
    double potential = computePotential();
    this.potential = potential;

    this.maturity += MATURITY_ALPHA * (potential - this.maturity);

    this.lastUpdatedTick = tick;
    }

    public double getContribution() {
        double densityBonus = 1.0 + CONTRIBUTION_FRACTURED_BONUS * getFracturedDensity() + CONTRIBUTION_STORM_BONUS * getStormDensity();
        return maturity * densityBonus;
    }

    public RegionState getState() {
        if (maturity > 2.25 && age > 3200) {
            return RegionState.ANCIENT;
        }

        if (averageTension >= 1.0 && chunkCount >= 8 && fracturedChunks >= Math.max(1, chunkCount / 8)) {
            return RegionState.FRACTURED;
        }

        if (maturity > 1.3 || averageTension > 0.75 || getFracturedDensity() > 0.15 || getStormDensity() > 0.15) {
            return RegionState.STRAINED;
        }

        if (maturity > 0.6 || averageTension > 0.45 || age > 1200) {
            return RegionState.GROWING;
        }

        return RegionState.DORMANT;
    }
}
