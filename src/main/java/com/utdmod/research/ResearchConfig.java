package com.utdmod.research;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class ResearchConfig {
    private static final Set<PerturbationType> enabledEventTypes = EnumSet.allOf(PerturbationType.class);
    private static volatile boolean botEnabled = false;
    private static volatile String botType = "mixed";
    private static volatile int botSeed = 0;
    private static volatile boolean recordingEnabled = true;
    private static volatile String visualMode = "default";

    private ResearchConfig() {}

    public static boolean isEnabled(PerturbationType type) {
        return enabledEventTypes.contains(type);
    }

    public static void setEnabled(PerturbationType type, boolean enabled) {
        if (enabled) {
            enabledEventTypes.add(type);
        } else {
            enabledEventTypes.remove(type);
        }
    }

    public static boolean isBotEnabled() {
        return botEnabled;
    }

    public static void setBotEnabled(boolean enabled) {
        botEnabled = enabled;
    }

    public static String getBotType() {
        return botType;
    }

    public static void setBotType(String type) {
        if (type == null || type.isBlank()) {
            botType = "mixed";
            return;
        }
        botType = type.trim().toLowerCase(Locale.ROOT);
    }

    public static int getBotSeed() {
        return botSeed;
    }

    public static void setBotSeed(int seed) {
        botSeed = seed;
    }

    public static boolean isRecordingEnabled() {
        return recordingEnabled;
    }

    public static void setRecordingEnabled(boolean enabled) {
        recordingEnabled = enabled;
    }

    public static String getVisualMode() {
        return visualMode;
    }

    public static void setVisualMode(String mode) {
        if (mode == null || mode.isBlank()) {
            visualMode = "default";
            return;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        visualMode = switch (normalized) {
            case "calm", "pulse", "heatmap", "default" -> normalized;
            default -> "default";
        };
    }

    public static String getEnabledTypesSummary() {
        return enabledEventTypes.stream()
            .map(PerturbationType::name)
            .sorted()
            .collect(Collectors.joining(", "));
    }

    public static PerturbationType parseType(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (PerturbationType type : PerturbationType.values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
