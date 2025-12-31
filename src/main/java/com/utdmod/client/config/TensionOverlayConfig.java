package com.utdmod.client.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class TensionOverlayConfig {
    private static boolean vignetteEnabled = true;
    private static boolean distortionEnabled = true;
    private static boolean pulseEnabled = true;
    private static float intensityMultiplier = 1.0f;

    public static boolean isVignetteEnabled() { return vignetteEnabled; }
    public static void setVignetteEnabled(boolean enabled) { vignetteEnabled = enabled; }
    public static boolean isDistortionEnabled() { return distortionEnabled; }
    public static void setDistortionEnabled(boolean enabled) { distortionEnabled = enabled; }
    public static boolean isPulseEnabled() { return pulseEnabled; }
    public static void setPulseEnabled(boolean enabled) { pulseEnabled = enabled; }
    public static float getIntensityMultiplier() { return intensityMultiplier; }
    public static void setIntensityMultiplier(float multiplier) {
        intensityMultiplier = Math.max(0.0f, Math.min(2.0f, multiplier));
    }
    public static void disableAll() { vignetteEnabled=false; distortionEnabled=false; pulseEnabled=false; }
    public static void enableAll() { vignetteEnabled=true; distortionEnabled=true; pulseEnabled=true; }
    public static void resetToDefaults() { vignetteEnabled=true; distortionEnabled=true; pulseEnabled=true; intensityMultiplier=1.0f; }
}
