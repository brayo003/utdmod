package com.utdmod.research;

public final class ResearchObservationEntry {
    public final int x;
    public final int z;
    public final double tension;
    public final int state;
    public final double activation;
    public final double memory;
    public final double transportX;
    public final double transportZ;
    public final double fieldDepth;
    public final double baseline;
    public final int regionId;

    public ResearchObservationEntry(
        int x,
        int z,
        double tension,
        int state,
        double activation,
        double memory,
        double transportX,
        double transportZ,
        double fieldDepth,
        double baseline,
        int regionId
    ) {
        this.x = x;
        this.z = z;
        this.tension = tension;
        this.state = state;
        this.activation = activation;
        this.memory = memory;
        this.transportX = transportX;
        this.transportZ = transportZ;
        this.fieldDepth = fieldDepth;
        this.baseline = baseline;
        this.regionId = regionId;
    }
}
