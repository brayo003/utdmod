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
        // StormManager no longer generates weather or storms.
        // It only maintains diagnostics and state history for region-based field monitoring.
        stormTickCounter++;
        Region region = selectStormRegion(world);
        double readiness = computeReadiness(region);
        if (DEBUG_LOGGING) {
            System.out.printf("[STORM] tick=%d world=%s region=%s readiness=%.3f%n",
                stormTickCounter,
                world.getRegistryKey().getValue(),
                region != null ? region.getId() : -1,
                readiness
            );
        }
    }

    private static void logStormStart(ServerWorld world, Region region, double readiness) {
        int stormRegionId = region != null ? region.getId() : -1;
        DiagnosticLogs.stormStarted(world.getTime(), stormRegionId, region != null ? region.getState().name() : "UNKNOWN", readiness);
        if (DEBUG_LOGGING) {
            System.out.printf("[STORM] Storm start logged in %s region=%d readiness=%.2f%n",
                world.getRegistryKey().getValue(), stormRegionId, readiness);
        }
    }

    private static void strikeLightning(ServerWorld world, Region region) {
        // StormManager no longer chooses lightning positions deterministically.
    }

    public static void triggerRitualStorm(ServerWorld world, PlayerEntity player) {
        if (RitualHandler.canPerformRitual(world, player)) {
            Region region = selectStormRegion(world);
            double readiness = computeReadiness(region);
            logStormStart(world, region, readiness);
            player.sendMessage(Text.literal("Storm ritual activated!"));
        }
    }

    public static void calmStorm(ServerWorld world, double calmAmount) {
        TensionManager.reduceTension(calmAmount);
    }
}
