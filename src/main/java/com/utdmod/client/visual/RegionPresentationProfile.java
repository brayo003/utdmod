package com.utdmod.client.visual;

import com.utdmod.core.RegionState;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

public final class RegionPresentationProfile {

    public final RegionState state;
    public final float fogStrength;
    public final int tintColor;
    public final float ambientSoundIntensity;
    public final SoundEvent ambientSound;
    public final SoundCategory soundCategory;
    public final float ambientVolume;
    public final float ambientPitch;
    public final DefaultParticleType particleType;
    public final double particleDensity;
    public final float vignetteStrength;
    public final int hudAccentColor;
    public final float windIntensity;
    public final boolean lightningPulse;
    public final boolean pulseEffect;

    private RegionPresentationProfile(RegionState state,
                                      float fogStrength,
                                      int tintColor,
                                      float ambientSoundIntensity,
                                      SoundEvent ambientSound,
                                      SoundCategory soundCategory,
                                      float ambientVolume,
                                      float ambientPitch,
                                      DefaultParticleType particleType,
                                      double particleDensity,
                                      float vignetteStrength,
                                      int hudAccentColor,
                                      float windIntensity,
                                      boolean lightningPulse,
                                      boolean pulseEffect) {
        this.state = state;
        this.fogStrength = fogStrength;
        this.tintColor = tintColor;
        this.ambientSoundIntensity = ambientSoundIntensity;
        this.ambientSound = ambientSound;
        this.soundCategory = soundCategory;
        this.ambientVolume = ambientVolume;
        this.ambientPitch = ambientPitch;
        this.particleType = particleType;
        this.particleDensity = particleDensity;
        this.vignetteStrength = vignetteStrength;
        this.hudAccentColor = hudAccentColor;
        this.windIntensity = windIntensity;
        this.lightningPulse = lightningPulse;
        this.pulseEffect = pulseEffect;
    }

    public static RegionPresentationProfile forState(RegionState state) {
        return switch (state) {
            case DORMANT -> DORMANT;
            case GROWING -> GROWING;
            case STRAINED -> STRAINED;
            case FRACTURED -> FRACTURED;
            case ANCIENT -> ANCIENT;
        };
    }

    public static RegionPresentationProfile blend(RegionPresentationProfile source, RegionPresentationProfile target, double alpha) {
        float fog = (float) lerp(source.fogStrength, target.fogStrength, alpha);
        int tint = blendColor(source.tintColor, target.tintColor, alpha);
        float ambientIntensity = (float) lerp(source.ambientSoundIntensity, target.ambientSoundIntensity, alpha);
        SoundEvent sound = alpha > 0.45 ? target.ambientSound : source.ambientSound;
        SoundCategory category = alpha > 0.45 ? target.soundCategory : source.soundCategory;
        float volume = (float) lerp(source.ambientVolume, target.ambientVolume, alpha);
        float pitch = (float) lerp(source.ambientPitch, target.ambientPitch, alpha);
        DefaultParticleType particle = alpha > 0.5 ? target.particleType : source.particleType;
        double density = lerp(source.particleDensity, target.particleDensity, alpha);
        float vignette = (float) lerp(source.vignetteStrength, target.vignetteStrength, alpha);
        int hudColor = blendColor(source.hudAccentColor, target.hudAccentColor, alpha);
        float wind = (float) lerp(source.windIntensity, target.windIntensity, alpha);
        boolean lightning = alpha > 0.55 ? target.lightningPulse : source.lightningPulse;
        boolean pulse = alpha > 0.55 ? target.pulseEffect : source.pulseEffect;
        return new RegionPresentationProfile(
            target.state,
            fog,
            tint,
            ambientIntensity,
            sound,
            category,
            volume,
            pitch,
            particle,
            density,
            vignette,
            hudColor,
            wind,
            lightning,
            pulse
        );
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

    public String profileName() {
        return state.name();
    }

    public static RegionPresentationProfile forOrdinal(int ordinal) {
        RegionState[] values = RegionState.values();
        int safe = Math.max(0, Math.min(ordinal, values.length - 1));
        return forState(values[safe]);
    }

    public static final RegionPresentationProfile DORMANT = new RegionPresentationProfile(
        RegionState.DORMANT,
        0.00f,
        0xFFFFFF,
        0.00f,
        null,
        SoundCategory.AMBIENT,
        0.0f,
        1.0f,
        null,
        0.0,
        0.00f,
        0xFFFFFF,
        0.00f,
        false,
        false
    );

    public static final RegionPresentationProfile GROWING = new RegionPresentationProfile(
        RegionState.GROWING,
        0.10f,
        0x88E8D8,
        0.16f,
        SoundEvents.ENTITY_VEX_AMBIENT,
        SoundCategory.AMBIENT,
        0.05f,
        0.92f,
        ParticleTypes.HAPPY_VILLAGER,
        0.035,
        0.08f,
        0x88E8D8,
        0.08f,
        false,
        false
    );

    public static final RegionPresentationProfile STRAINED = new RegionPresentationProfile(
        RegionState.STRAINED,
        0.24f,
        0xD8A860,
        0.22f,
        SoundEvents.AMBIENT_CAVE.value(),
        SoundCategory.AMBIENT,
        0.06f,
        0.78f,
        ParticleTypes.ASH,
        0.075,
        0.18f,
        0xE1C574,
        0.05f,
        false,
        false
    );

    public static final RegionPresentationProfile FRACTURED = new RegionPresentationProfile(
        RegionState.FRACTURED,
        0.35f,
        0xCC5A55,
        0.32f,
        SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
        SoundCategory.WEATHER,
        0.08f,
        0.72f,
        ParticleTypes.LARGE_SMOKE,
        0.135,
        0.38f,
        0xD95455,
        0.14f,
        true,
        false
    );

    public static final RegionPresentationProfile ANCIENT = new RegionPresentationProfile(
        RegionState.ANCIENT,
        0.28f,
        0x8E82E6,
        0.26f,
        SoundEvents.ENTITY_ENDERMAN_STARE,
        SoundCategory.AMBIENT,
        0.05f,
        0.66f,
        ParticleTypes.WHITE_ASH,
        0.085,
        0.30f,
        0xB8A8FF,
        0.07f,
        false,
        true
    );
}
