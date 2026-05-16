package com.utdmod.storm;

import com.utdmod.core.RegionDiagnosticsManager;
import com.utdmod.core.TensionManager;
import com.utdmod.core.TensionTraceClock;
import com.utdmod.diag.DiagnosticLogs;
import com.utdmod.ritual.RitualHandler;
import com.utdmod.tension.ChunkTensionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

/**
 * Lightweight regional storm ambience driven by global and local chunk tension (throttled to limit lag).
 */
public final class StormManager {

    private static final boolean DEBUG_LOGGING = false;

    private static int stormTickCounter = 0;
    private static boolean stormActive = false;
    private static int stormDuration = 0;
    private static int heavyEffectCounter = 0;
    private static final int HEAVY_EFFECT_INTERVAL = 40;

    private static int stormRegionRx = 0;
    private static int stormRegionRz = 0;
    private static double stormPeakTension = 0.0;

    private StormManager() {}

    /**
     * Peak tension in a 3×3 chunk neighborhood around each online player (loaded chunks only).
     */
    private static double maxRegionalTension(ServerWorld world) {
        double max = 0.0;
        for (PlayerEntity p : world.getPlayers()) {
            ChunkPos cp = new ChunkPos(p.getBlockPos());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    ChunkPos n = new ChunkPos(cp.x + dx, cp.z + dz);
                    if (!world.isChunkLoaded(n.x, n.z)) continue;
                    max = Math.max(max, ChunkTensionManager.getLocalTension(world, n));
                }
            }
        }
        return max;
    }

    private static void captureStormHotspot(ServerWorld world, double tensionMetric) {
        stormPeakTension = tensionMetric;
        int bestRx = 0;
        int bestRz = 0;
        double bestT = -1.0;
        for (PlayerEntity p : world.getPlayers()) {
            ChunkPos cp = new ChunkPos(p.getBlockPos());
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    ChunkPos n = new ChunkPos(cp.x + dx, cp.z + dz);
                    if (!world.isChunkLoaded(n.x, n.z)) continue;
                    double t = ChunkTensionManager.getLocalTension(world, n);
                    if (t > bestT) {
                        bestT = t;
                        bestRx = Math.floorDiv(n.x, 8);
                        bestRz = Math.floorDiv(n.z, 8);
                    }
                }
            }
        }
        stormRegionRx = bestRx;
        stormRegionRz = bestRz;
    }

    public static void tick(ServerWorld world) {
        stormTickCounter++;
        double global = TensionManager.getTension();
        double regional = maxRegionalTension(world);
        double tension = Math.max(global, regional * 0.52);

        if (stormActive) {
            stormPeakTension = Math.max(stormPeakTension, tension);
        }

        if (tension < 0.35) {
            if (stormActive) {
                RegionDiagnosticsManager.RegionSnapshot snap = RegionDiagnosticsManager.get(
                    stormRegionRx * RegionDiagnosticsManager.REGION_SIZE + 2,
                    stormRegionRz * RegionDiagnosticsManager.REGION_SIZE + 2
                );
                double endCut = snap != null && snap.max > 1.35 ? 0.10 : 0.20;
                if (tension < endCut) {
                    endStorm(world, tension, "LOW_TENSION");
                }
            }
            return;
        }

        applyLightEffects(world, tension);

        heavyEffectCounter++;
        if (heavyEffectCounter >= HEAVY_EFFECT_INTERVAL) {
            heavyEffectCounter = 0;
            applyHeavyEffects(world, tension);
        }

        if (!stormActive && tension > 0.85 && world.random.nextInt(120) == 0) {
            triggerStorm(world, tension, "maxRegionalTension");
        }
        if (stormActive) {
            stormDuration++;
            if (stormTickCounter % 60 == 0) {
                ChunkPos drift = RegionDiagnosticsManager.movingTowardHotterRegion(stormRegionRx, stormRegionRz);
                stormRegionRx = drift.x;
                stormRegionRz = drift.z;
            }
            if (world.getRegistryKey() == World.OVERWORLD && world.getTime() % 400 == 0 && world.random.nextFloat() < 0.22f) {
                world.setWeather(800 + world.random.nextInt(800), 400 + world.random.nextInt(400), true, tension > 1.05 && world.random.nextBoolean());
            }
        }
    }

    private static void triggerStorm(ServerWorld world, double tension, String reason) {
        if (stormActive) {
            if (DEBUG_LOGGING) {
                System.out.printf("[STORM] Storm already active in %s%n", world.getRegistryKey().getValue());
            }
            return;
        }
        stormActive = true;
        stormDuration = 0;
        captureStormHotspot(world, tension);
        ChunkPos toward = RegionDiagnosticsManager.movingTowardHotterRegion(stormRegionRx, stormRegionRz);
        double strength = Math.min(1.0, tension / 1.85);
        DiagnosticLogs.stormStart(
            world.getTime(),
            reason,
            stormRegionRx,
            stormRegionRz,
            tension,
            TensionManager.getTension(),
            world.getPlayers().size(),
            strength,
            toward.x,
            toward.z
        );
        if (DEBUG_LOGGING) {
            System.out.printf("[STORM] Storm started in %s (tension: %.2f, reason: %s)%n",
                world.getRegistryKey().getValue(), tension, reason);
        }
    }

    private static void endStorm(ServerWorld world, double tension, String reason) {
        if (!stormActive) return;
        stormActive = false;
        DiagnosticLogs.stormEnd(
            stormDuration,
            stormPeakTension,
            stormRegionRx,
            stormRegionRz,
            TensionTraceClock.getServerTick()
        );
        if (DEBUG_LOGGING) {
            System.out.printf("[STORM] Storm ended in %s after %d ticks (tension: %.2f, reason: %s)%n",
                world.getRegistryKey().getValue(), stormDuration, tension, reason);
        }
    }

    private static void applyLightEffects(ServerWorld world, double tension) {
        if (world.random.nextInt(220) > (int) (tension * 18)) {
            return;
        }
        if (world.getPlayers().isEmpty()) return;
        PlayerEntity player = world.getPlayers().get(world.random.nextInt(world.getPlayers().size()));
        world.playSound(
            player,
            player.getBlockPos(),
            SoundEvents.AMBIENT_CAVE.value(),
            SoundCategory.AMBIENT,
            0.15f + (float) tension * 0.12f,
            0.6f + world.random.nextFloat() * 0.2f
        );
    }

    private static void applyHeavyEffects(ServerWorld world, double tension) {
        if (tension < 0.75) return;
        if (world.getPlayers().isEmpty()) return;
        PlayerEntity player = world.getPlayers().get(world.random.nextInt(world.getPlayers().size()));
        if (world.random.nextFloat() < 0.35f) {
            world.playSound(
                player,
                player.getBlockPos(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.WEATHER,
                0.4f + (float) tension * 0.15f,
                0.7f
            );
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
            triggerStorm(world, 1.5, "RITUAL_TRIGGER");
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
