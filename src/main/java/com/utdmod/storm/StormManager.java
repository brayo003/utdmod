package com.utdmod.storm;

import com.utdmod.core.Region;
import com.utdmod.core.RegionManager;
import com.utdmod.core.RegionState;
import com.utdmod.core.TensionManager;
import com.utdmod.core.TensionTraceClock;
import com.utdmod.diag.DiagnosticLogs;
import com.utdmod.ritual.RitualHandler;
import com.utdmod.tension.ChunkTensionData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight regional storm ambience driven by active ecological regions rather than nearby players.
 */
public final class StormManager {

    private static final boolean DEBUG_LOGGING = false;

    private static int stormTickCounter = 0;
    private static boolean stormActive = false;
    private static int stormDuration = 0;
    private static int stormRegionId = 0;
    private static int stormRegionRx = 0;
    private static int stormRegionRz = 0;
    private static double stormPeakTension = 0.0;

    private StormManager() {}

    private static Region selectStormRegion(ServerWorld world) {
        List<Region> regions = RegionManager.getRegions();
        Region best = null;
        double bestScore = -1.0;
        for (Region region : regions) {
            if (!region.getWorldKey().equals(world.getRegistryKey()) || region.getChunkCount() <= 0) {
                continue;
            }
            double score = region.getContribution();
            if (region.getState() == RegionState.FRACTURED) {
                score += 0.45;
            } else if (region.getState() == RegionState.STRAINED) {
                score += 0.25;
            } else if (region.getState() == RegionState.ANCIENT) {
                score += 0.08;
            }
            if (score > bestScore) {
                bestScore = score;
                best = region;
            }
        }
        return best;
    }

    private static double computeReadiness(Region region) {
        if (region == null) {
            return 0.0;
        }
        double stateBias = switch (region.getState()) {
            case FRACTURED -> 0.88;
            case STRAINED -> 0.60;
            case GROWING -> 0.24;
            case ANCIENT -> 0.12;
            default -> 0.06;
        };
        double tensionBias = Math.min(1.0, region.getAverageTension() / 1.35);
        double densityBias = Math.min(1.0, region.getFracturedDensity() * 1.8 + region.getStormDensity() * 0.8);
        return Math.min(1.0, 0.65 * stateBias + 0.25 * tensionBias + 0.10 * densityBias);
    }

    private static double lightningChance(Region region) {
        if (region == null) {
            return 0.0;
        }
        return switch (region.getState()) {
            case FRACTURED -> 0.0012;
            case STRAINED -> 0.00038;
            case GROWING -> 0.00006;
            case ANCIENT -> 0.00002;
            default -> 0.0;
        };
    }

    public static void tick(ServerWorld world) {
        stormTickCounter++;
        Region region = selectStormRegion(world);
        double readiness = computeReadiness(region);

        if (stormActive) {
            stormPeakTension = Math.max(stormPeakTension, readiness);
            stormDuration++;
            if (readiness < 0.18 && world.random.nextInt(900) == 0) {
                endStorm(world, readiness, "COOLING");
                return;
            }
            if (world.random.nextFloat() < lightningChance(region)) {
                strikeLightning(world, region);
            }
            if (world.getRegistryKey() == World.OVERWORLD && world.random.nextInt(2400) == 0) {
                world.setWeather(1000 + world.random.nextInt(800), 0, true, false);
            }
            return;
        }

        if (readiness > 0.72 && world.random.nextFloat() < 0.00022f + readiness * 0.00012f) {
            triggerStorm(world, region, readiness);
        }
    }

    private static void triggerStorm(ServerWorld world, Region region, double readiness) {
        if (stormActive) {
            if (DEBUG_LOGGING) {
                System.out.printf("[STORM] Storm already active in %s%n", world.getRegistryKey().getValue());
            }
            return;
        }
        stormActive = true;
        stormDuration = 0;
        stormPeakTension = readiness;
        stormRegionId = region != null ? region.getId() : 0;
        stormRegionRx = region != null ? (int) Math.round(region.getCentroidX()) : 0;
        stormRegionRz = region != null ? (int) Math.round(region.getCentroidZ()) : 0;
        DiagnosticLogs.stormStarted(world.getTime(), stormRegionId, region != null ? region.getState().name() : "UNKNOWN", readiness);
        if (DEBUG_LOGGING) {
            System.out.printf("[STORM] Storm started in %s (readiness: %.2f)%n", world.getRegistryKey().getValue(), readiness);
        }
    }

    private static void endStorm(ServerWorld world, double readiness, String reason) {
        if (!stormActive) {
            return;
        }
        stormActive = false;
        DiagnosticLogs.stormEnded(stormDuration, stormPeakTension, stormRegionId, reason);
        if (DEBUG_LOGGING) {
            System.out.printf("[STORM] Storm ended in %s after %d ticks (readiness: %.2f, reason: %s)%n",
                world.getRegistryKey().getValue(), stormDuration, readiness, reason);
        }
    }

    private static void strikeLightning(ServerWorld world, Region region) {
        if (world == null || region == null) {
            return;
        }
        ChunkTensionData chunkData = ChunkTensionData.getServerState(world);
        List<ChunkPos> candidates = new ArrayList<>();
        for (ChunkPos chunkPos : region.getChunks()) {
            if (world.isChunkLoaded(chunkPos.x, chunkPos.z) && chunkData.getChunkState(chunkPos) == ChunkTensionData.ChunkState.FRACTURED) {
                candidates.add(chunkPos);
            }
        }
        if (candidates.isEmpty()) {
            candidates.addAll(region.getChunks());
        }
        ChunkPos chosen = candidates.get(world.random.nextInt(candidates.size()));
        int x = (chosen.x << 4) + world.random.nextInt(16);
        int z = (chosen.z << 4) + world.random.nextInt(16);
        int y = world.getTopY();
        BlockPos pos = new BlockPos(x, y, z);
        while (y > world.getBottomY() && world.getBlockState(pos).isAir()) {
            y--;
            pos = pos.down();
        }
        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
        if (lightning != null) {
            lightning.refreshPositionAfterTeleport(new Vec3d(x, y + 1, z));
            world.spawnEntity(lightning);
        }
    }

    public static boolean isStormActive() {
        return stormActive;
    }

    public static int getStormDuration() {
        return stormDuration;
    }

    public static void triggerRitualStorm(ServerWorld world, PlayerEntity player) {
        if (RitualHandler.canPerformRitual(world, player)) {
            triggerStorm(world, selectStormRegion(world), 1.0);
            player.sendMessage(Text.literal("Storm ritual activated!"));
        }
    }

    public static void calmStorm(ServerWorld world, double calmAmount) {
        if (stormActive) {
            TensionManager.reduceTension(calmAmount);
            if (TensionManager.getTension() < 0.5) {
                endStorm(world, TensionManager.getTension(), "RITUAL_CALMING");
            }
        }
    }
}
