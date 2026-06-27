package com.utdmod.ecology;

/**
 * Lightweight, persistent memory for a region.
 * This is intentionally sparse and only used to bias decorative ecology.
 */
public final class RegionHistory {
    private int treesCut;
    private int blocksMined;
    private int firesStarted;
    private int structuresBuilt;
    private int animalsKilled;
    private int cropsHarvested;
    private long age;

    public RegionHistory() {
        this(0, 0, 0, 0, 0, 0, 0L);
    }

    public RegionHistory(int treesCut, int blocksMined, int firesStarted, int structuresBuilt, int animalsKilled, int cropsHarvested, long age) {
        this.treesCut = treesCut;
        this.blocksMined = blocksMined;
        this.firesStarted = firesStarted;
        this.structuresBuilt = structuresBuilt;
        this.animalsKilled = animalsKilled;
        this.cropsHarvested = cropsHarvested;
        this.age = age;
    }

    public RegionHistory copy() {
        return new RegionHistory(treesCut, blocksMined, firesStarted, structuresBuilt, animalsKilled, cropsHarvested, age);
    }

    public void recordTreeCut() {
        treesCut++;
    }

    public void recordBlocksMined() {
        blocksMined++;
    }

    public void recordFireStarted() {
        firesStarted++;
    }

    public void recordStructureBuilt() {
        structuresBuilt++;
    }

    public void recordAnimalKilled() {
        animalsKilled++;
    }

    public void recordCropHarvested() {
        cropsHarvested++;
    }

    public void setAge(long age) {
        this.age = age;
    }

    public void advanceAge() {
        age++;
    }

    public int getTreesCut() {
        return treesCut;
    }

    public int getBlocksMined() {
        return blocksMined;
    }

    public int getFiresStarted() {
        return firesStarted;
    }

    public int getStructuresBuilt() {
        return structuresBuilt;
    }

    public int getAnimalsKilled() {
        return animalsKilled;
    }

    public int getCropsHarvested() {
        return cropsHarvested;
    }

    public long getAge() {
        return age;
    }
}
