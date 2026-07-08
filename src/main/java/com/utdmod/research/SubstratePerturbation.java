package com.utdmod.research;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public final class SubstratePerturbation {
    public final long tick;
    public final String worldId;
    public final String dimension;
    public final ChunkPos chunk;
    public final BlockPos blockPos;
    public final String playerName;
    public final PerturbationType type;
    public final String detail;
    public final double magnitude;

    public SubstratePerturbation(
        long tick,
        ServerWorld world,
        ChunkPos chunk,
        BlockPos blockPos,
        ServerPlayerEntity player,
        PerturbationType type,
        String detail,
        double magnitude
    ) {
        this.tick = tick;
        this.worldId = world == null ? "unknown" : world.getRegistryKey().getValue().toString();
        this.dimension = world == null ? "unknown" : world.getRegistryKey().getValue().toString();
        this.chunk = chunk;
        this.blockPos = blockPos;
        this.playerName = player != null ? player.getName().getString() : "SYSTEM";
        this.type = type;
        this.detail = detail != null ? detail : "";
        this.magnitude = magnitude;
    }

    @Override
    public String toString() {
        return String.format(
            "[PERTURB] tick=%d world=%s chunk=%d,%d pos=%d,%d,%d player=%s type=%s detail=%s mag=%.5f",
            tick,
            worldId,
            chunk.x,
            chunk.z,
            blockPos.getX(),
            blockPos.getY(),
            blockPos.getZ(),
            playerName,
            type.name(),
            detail,
            magnitude
        );
    }
}
