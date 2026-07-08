package com.utdmod.research;

public final class ResearchSnapshot {
    public final String mode;
    public final String narrative;
    public final int activeRegions;
    public final int largestRegionChunks;
    public final int scarCount;
    public final int waveCount;
    public final int oscillationCount;
    public final double fieldDepthMean;
    public final double transportX;
    public final double transportZ;
    public final double regionCenterX;
    public final double regionCenterZ;
    public final int regionChunkCount;

    public ResearchSnapshot(
        String mode,
        String narrative,
        int activeRegions,
        int largestRegionChunks,
        int scarCount,
        int waveCount,
        int oscillationCount,
        double fieldDepthMean,
        double transportX,
        double transportZ,
        double regionCenterX,
        double regionCenterZ,
        int regionChunkCount
    ) {
        this.mode = mode;
        this.narrative = narrative;
        this.activeRegions = activeRegions;
        this.largestRegionChunks = largestRegionChunks;
        this.scarCount = scarCount;
        this.waveCount = waveCount;
        this.oscillationCount = oscillationCount;
        this.fieldDepthMean = fieldDepthMean;
        this.transportX = transportX;
        this.transportZ = transportZ;
        this.regionCenterX = regionCenterX;
        this.regionCenterZ = regionCenterZ;
        this.regionChunkCount = regionChunkCount;
    }
}
