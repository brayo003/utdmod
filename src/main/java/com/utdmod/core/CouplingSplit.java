package com.utdmod.core;

/**
 * Chunk→global coupling terms (physics: {@code ambientInflow}+{@code localCoupling} enters global tick).
 */
public final class CouplingSplit {
    public final double ambientInflow;
    public final double localCoupling;
    public final double total;
    /** Diag: storms / n loaded tension chunks */
    public final double stormChunkFrac;
    /** Diag: fractured(+decoupled) / n */
    public final double fracturedFrac;
    /** Diag raw counts for storm_drive gating */
    public final int stormChunks;
    public final int fracturedChunks;

    public CouplingSplit(
        double ambientInflow,
        double localCoupling,
        double stormChunkFrac,
        double fracturedFrac,
        int stormChunks,
        int fracturedChunks
    ) {
        this.ambientInflow = ambientInflow;
        this.localCoupling = localCoupling;
        this.total = ambientInflow + localCoupling;
        this.stormChunkFrac = stormChunkFrac;
        this.fracturedFrac = fracturedFrac;
        this.stormChunks = stormChunks;
        this.fracturedChunks = fracturedChunks;
    }
}
