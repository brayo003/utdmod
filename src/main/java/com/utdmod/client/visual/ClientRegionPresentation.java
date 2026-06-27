package com.utdmod.client.visual;

import com.utdmod.client.TensionSyncState;
import com.utdmod.client.ClientFeelingCounters;
import com.utdmod.core.RegionState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.sound.SoundCategory;

@Environment(EnvType.CLIENT)
public final class ClientRegionPresentation {

    private static int prevRegionId = 0;
    private static RegionPresentationProfile sourceProfile = RegionPresentationProfile.DORMANT;
    private static RegionPresentationProfile targetProfile = RegionPresentationProfile.DORMANT;
    private static double transition = 0.0; // 0..1
    private static final double TRANSITION_STEP = 1.0 / 120.0; // ~6s

    private ClientRegionPresentation() {}

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) return;

        int rid = TensionSyncState.CLIENT_REGION_ID;
        int stateOrdinal = TensionSyncState.CLIENT_REGION_STATE;
        RegionPresentationProfile desiredProfile = rid == 0
            ? RegionPresentationProfile.DORMANT
            : RegionPresentationProfile.forOrdinal(stateOrdinal);

        if (rid != prevRegionId) {
            if (prevRegionId != 0) {
                System.out.printf("[REGION_PRESENTATION_EXIT]id=%d%n", prevRegionId);
            }
            if (rid != 0) {
                System.out.printf("[REGION_PRESENTATION]id=%d state=%s profile=%s transition=%.2f%n",
                    rid,
                    RegionState.values()[stateOrdinal],
                    desiredProfile.profileName(),
                    transition
                );
            }
            prevRegionId = rid;
        }

        if (desiredProfile != targetProfile) {
            sourceProfile = RegionPresentationProfile.blend(sourceProfile, targetProfile, transition);
            targetProfile = desiredProfile;
            transition = 0.0;
        }

        transition = Math.min(1.0, transition + TRANSITION_STEP);

        if (transition > 0.02 && client.world.random.nextDouble() < effectiveParticleRate()) {
            spawnStateParticle(client.player);
        }

        if (transition > 0.15 && client.world.random.nextDouble() < effectiveAmbientCueChance()) {
            playAmbientCue(client.player);
        }

        ClientFeelingCounters.overlayIntensity = (float) transition;
    }

    public static void renderOverlay(DrawContext context, float tickDelta) {
        if (transition <= 0.01) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        float fogStrength = (float) lerp(sourceProfile.fogStrength, targetProfile.fogStrength, transition);
        int tint = blendColor(sourceProfile.tintColor, targetProfile.tintColor, transition);
        float vignette = (float) lerp(sourceProfile.vignetteStrength, targetProfile.vignetteStrength, transition);

        if (fogStrength > 0.001f) {
            int alpha = Math.min(180, (int) (fogStrength * 220));
            context.fill(0, 0, width, height, (alpha << 24) | (tint & 0xFFFFFF));
        }

        if (vignette > 0.01f) {
            int edge = Math.min(width, height) / 5;
            int alpha = Math.min(220, (int) (vignette * 190));
            int color = alpha << 24;
            context.fill(0, 0, width, edge, color);
            context.fill(0, height - edge, width, height, color);
            context.fill(0, 0, edge, height, color);
            context.fill(width - edge, 0, width, height, color);
        }

        if (targetProfile.lightningPulse && client.world.getTime() % 180 < 2) {
            context.fill(0, 0, width, height, (int) (40 * transition) << 24 | 0xFFFFFF);
        }

        if (targetProfile.pulseEffect && client.world.getTime() % 120 < 3) {
            int pulseAlpha = Math.min(72, (int) (40 * transition));
            context.fill(0, 0, width, height, (pulseAlpha << 24) | 0x7048E8);
        }
    }

    public static int currentHudAccentColor() {
        return blendColor(sourceProfile.hudAccentColor, targetProfile.hudAccentColor, transition);
    }

    public static float currentTransition() {
        return (float) transition;
    }

    private static void spawnStateParticle(ClientPlayerEntity player) {
        var world = player.getWorld();
        DefaultParticleType particleType = transition < 0.5 ? sourceProfile.particleType : targetProfile.particleType;
        if (particleType == null) return;

        double density = lerp(sourceProfile.particleDensity, targetProfile.particleDensity, transition);
        if (world.random.nextDouble() > density) return;

        double x = player.getX() + (world.random.nextDouble() - 0.5) * 6.0;
        double y = player.getY() + 1.0 + world.random.nextDouble() * 2.0;
        double z = player.getZ() + (world.random.nextDouble() - 0.5) * 6.0;
        double wind = lerp(sourceProfile.windIntensity, targetProfile.windIntensity, transition);
        double vx = (world.random.nextDouble() - 0.5) * wind;
        double vz = (world.random.nextDouble() - 0.5) * wind;
        world.addParticle(particleType, x, y, z, vx * 0.02, 0.01, vz * 0.02);
    }

    private static void playAmbientCue(ClientPlayerEntity player) {
        var world = player.getWorld();
        SoundCategory category = transition < 0.5 ? sourceProfile.soundCategory : targetProfile.soundCategory;
        SoundCategory soundCategory = category;
        float volume = (float) lerp(sourceProfile.ambientVolume, targetProfile.ambientVolume, transition);
        float pitch = (float) lerp(sourceProfile.ambientPitch, targetProfile.ambientPitch, transition);
        if (volume <= 0.001f) return;

        if (world.random.nextDouble() > effectiveAmbientCueChance()) return;

        if (transition < 0.5 && sourceProfile.ambientSound != null) {
            world.playSound(player.getX(), player.getY(), player.getZ(), sourceProfile.ambientSound, soundCategory, volume, pitch, false);
        } else if (targetProfile.ambientSound != null) {
            world.playSound(player.getX(), player.getY(), player.getZ(), targetProfile.ambientSound, soundCategory, volume, pitch, false);
        }
    }

    private static double effectiveParticleRate() {
        double base = lerp(sourceProfile.particleDensity, targetProfile.particleDensity, transition);
        return Math.min(0.12, base * transition * 0.8);
    }

    private static double effectiveAmbientCueChance() {
        double intensity = lerp(sourceProfile.ambientSoundIntensity, targetProfile.ambientSoundIntensity, transition);
        return Math.min(0.01, 0.0015 + intensity * 0.012);
    }

    private static double lerp(double start, double end, double alpha) {
        return start + (end - start) * alpha;
    }

    private static int blendColor(int source, int target, double alpha) {
        int sr = (source >> 16) & 0xFF;
        int sg = (source >> 8) & 0xFF;
        int sb = source & 0xFF;
        int tr = (target >> 16) & 0xFF;
        int tg = (target >> 8) & 0xFF;
        int tb = target & 0xFF;
        int rr = (int) (sr + (tr - sr) * alpha);
        int rg = (int) (sg + (tg - sg) * alpha);
        int rb = (int) (sb + (tb - sb) * alpha);
        return (rr << 16) | (rg << 8) | rb;
    }
}
