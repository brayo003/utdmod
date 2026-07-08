package com.utdmod.client.visual;

import com.utdmod.client.TensionSyncState;
import com.utdmod.client.ClientFeelingCounters;
import com.utdmod.core.RegionState;
import com.utdmod.research.ResearchConfig;
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

        if (transition > 0.02 && shouldSpawnStateParticle(client.world.getTime())) {
            spawnStateParticle(client.player);
        }

        if (transition > 0.15 && shouldPlayAmbientCue(client.world.getTime())) {
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
        fogStrength += (float) (TensionSyncState.CLIENT_PRESENTATION_STRESS * 0.28);
        vignette += (float) (TensionSyncState.CLIENT_PRESENTATION_STRESS * 0.18);
        if (TensionSyncState.CLIENT_PRESENTATION_PROSPERITY > 0.55) {
            tint = blendColor(tint, 0x5533AA55, 0.25);
        }
        String visualMode = ResearchConfig.getVisualMode();
        if ("calm".equals(visualMode)) {
            fogStrength *= 0.55f;
            vignette *= 0.7f;
        } else if ("pulse".equals(visualMode)) {
            fogStrength *= 1.15f;
            vignette *= 1.1f;
        } else if ("heatmap".equals(visualMode)) {
            fogStrength *= 1.35f;
            vignette *= 1.2f;
        }

        String mode = TensionSyncState.CLIENT_RESEARCH_MODE;
        if ("activation".equals(mode)) {
            drawActivationOutline(context, width, height);
        } else if ("memory".equals(mode)) {
            int scarAlpha = Math.min(120, 20 + (int) (TensionSyncState.CLIENT_RESEARCH_SCAR_COUNT * 12));
            context.fill(0, 0, width, height, (scarAlpha << 24) | 0x6600CC);
        } else if ("transport".equals(mode)) {
            drawTransportArrows(context, width, height);
        } else if ("depth".equals(mode)) {
            drawDepthOutline(context, width, height);
        }

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

    private static void drawTransportArrows(DrawContext context, int width, int height) {
        int count = 18;
        double tx = TensionSyncState.CLIENT_RESEARCH_TRANSPORT_X;
        double tz = TensionSyncState.CLIENT_RESEARCH_TRANSPORT_Z;
        for (int i = 0; i < count; i++) {
            int x = (int) ((i * 97 + (int) (MinecraftClient.getInstance().world.getTime() % 200)) % width);
            int y = (int) (((i * 59) + (int) (MinecraftClient.getInstance().world.getTime() * 2 % height)) % height);
            int dx = (int) Math.round(Math.max(-18, Math.min(18, tx * 40.0 + i * 2)));
            int dy = (int) Math.round(Math.max(-18, Math.min(18, tz * 40.0 + i * 3)));
            if (dx == 0 && dy == 0) continue;
            context.fill(x, y, x + Math.max(2, Math.abs(dx)), y + 1, 0xAAFFFFFF);
            context.fill(x + Math.max(2, Math.abs(dx)) - 2, y - 2, x + Math.max(2, Math.abs(dx)), y + 3, 0xAAFFFFFF);
            context.fill(x, y, x + 1, y + 1, 0xAAFFFFFF);
        }
    }

    private static void drawActivationOutline(DrawContext context, int width, int height) {
        if (TensionSyncState.CLIENT_RESEARCH_LARGEST_REGION_CHUNKS <= 0) return;
        int size = Math.min(width, height) / 6 + TensionSyncState.CLIENT_RESEARCH_LARGEST_REGION_CHUNKS * 3;
        int centerX = width / 2;
        int centerY = height / 2;
        int left = centerX - size / 2;
        int top = centerY - size / 2;
        int right = left + size;
        int bottom = top + size;
        context.fill(left, top, right, top + 1, 0xFFFFFFFF);
        context.fill(left, bottom - 1, right, bottom, 0xFFFFFFFF);
        context.fill(left, top, left + 1, bottom, 0xFFFFFFFF);
        context.fill(right - 1, top, right, bottom, 0xFFFFFFFF);
        context.drawText(MinecraftClient.getInstance().textRenderer, "active=" + TensionSyncState.CLIENT_RESEARCH_ACTIVE_REGIONS, left, top - 12, 0xFFFFFF, true);
    }

    private static void drawDepthOutline(DrawContext context, int width, int height) {
        if (TensionSyncState.CLIENT_RESEARCH_REGION_CHUNK_COUNT <= 0) return;
        int size = Math.min(width, height) / 6 + (int) Math.sqrt(TensionSyncState.CLIENT_RESEARCH_REGION_CHUNK_COUNT) * 10;
        int centerX = width / 2;
        int centerY = height / 2;
        int left = centerX - size / 2;
        int top = centerY - size / 2;
        int right = left + size;
        int bottom = top + size;
        context.fill(left, top, right, top + 1, 0xFFFFFFFF);
        context.fill(left, bottom - 1, right, bottom, 0xFFFFFFFF);
        context.fill(left, top, left + 1, bottom, 0xFFFFFFFF);
        context.fill(right - 1, top, right, bottom, 0xFFFFFFFF);
        context.drawText(MinecraftClient.getInstance().textRenderer, "depth=" + TensionSyncState.CLIENT_RESEARCH_FIELD_DEPTH_MEAN, left, top - 12, 0xFFFFFF, true);
    }

    private static boolean shouldSpawnStateParticle(long worldTime) {
        int rate = (int) Math.max(1, Math.ceil(effectiveParticleRate() * 20.0));
        return (worldTime % 20) < rate;
    }

    private static boolean shouldPlayAmbientCue(long worldTime) {
        int rate = (int) Math.max(1, Math.ceil(effectiveAmbientCueChance() * 400.0));
        return (worldTime % 400) < rate;
    }

    private static void spawnStateParticle(ClientPlayerEntity player) {
        var world = player.getWorld();
        DefaultParticleType particleType = transition < 0.5 ? sourceProfile.particleType : targetProfile.particleType;
        if (particleType == null) return;

        double density = lerp(sourceProfile.particleDensity, targetProfile.particleDensity, transition);
        String visualMode = ResearchConfig.getVisualMode();
        if ("calm".equals(visualMode)) {
            density *= 0.65;
        } else if ("pulse".equals(visualMode)) {
            density *= 1.15;
        } else if ("heatmap".equals(visualMode)) {
            density *= 1.4;
        }

        long worldTime = world.getTime();
        int index = (int) ((worldTime / 20) % 6);
        double x = player.getX() + ((index % 3) - 1) * 1.5;
        double y = player.getY() + 1.0 + ((index / 3) * 0.5);
        double z = player.getZ() + (((index + 1) % 3) - 1) * 1.5;
        double wind = lerp(sourceProfile.windIntensity, targetProfile.windIntensity, transition);
        double vx = ((index % 2) - 0.5) * wind;
        double vz = (((index / 2) % 2) - 0.5) * wind;
        world.addParticle(particleType, x, y, z, vx * 0.02, 0.01, vz * 0.02);
    }

    private static void playAmbientCue(ClientPlayerEntity player) {
        var world = player.getWorld();
        SoundCategory category = transition < 0.5 ? sourceProfile.soundCategory : targetProfile.soundCategory;
        float volume = (float) lerp(sourceProfile.ambientVolume, targetProfile.ambientVolume, transition);
        float pitch = (float) lerp(sourceProfile.ambientPitch, targetProfile.ambientPitch, transition);
        if (volume <= 0.001f) return;

        long worldTime = world.getTime();
        if ((worldTime / 100) % 4 != 0) return;

        if (transition < 0.5 && sourceProfile.ambientSound != null) {
            world.playSound(player.getX(), player.getY(), player.getZ(), sourceProfile.ambientSound, category, volume, pitch, false);
        } else if (targetProfile.ambientSound != null) {
            world.playSound(player.getX(), player.getY(), player.getZ(), targetProfile.ambientSound, category, volume, pitch, false);
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
