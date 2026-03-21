package com.utdmod.debug;

import java.util.HashMap;
import java.util.Map;

public class AIDebugLogger {

    private static final Map<String, Integer> eventStats = new HashMap<>();
    private static boolean highTensionActive = false;

    public static void resetCounters() {
        eventStats.clear();
        highTensionActive = false;
    }

    private static void increment(String key) {
        eventStats.put(key, eventStats.getOrDefault(key, 0) + 1);
    }

    public static void logTensionChangeRateYLevelDelta(double tension, double y, double delta, String source) {
        increment("TENSION_CHANGE");
    }

    public static void logRitualBlockActivationTension(double tension, String type, String player) {
        increment("RITUAL_ACTIVATION");
    }

    public static void logSerpentSpawnTriggerTime(double tension, long time, String player) {
        increment("SERPENT_SPAWN");
    }

    public static void logWraithSpawnDistanceOnTrigger(
            double x1, double y1, double z1,
            double x2, double y2, double z2,
            String player) {
        increment("WRAITH_SPAWN");
    }

    public static void logVillagerTradeRewardAppliedCount(
            double tension, int total, int applied,
            String player, String profession) {
        increment("VILLAGER_TRADE");
    }

    public static void logGolemApathyTriggerCount(
            double tension, String player,
            double x, double y, double z) {
        increment("GOLEM_APATHY");
    }

    public static void logHighTensionDuration(double tension, String player) {
        if (tension >= 0.95 && !highTensionActive) {
            highTensionActive = true;
            increment("HIGH_TENSION_START");
        } else if (tension < 0.95 && highTensionActive) {
            highTensionActive = false;
            increment("HIGH_TENSION_END");
        }
    }

    public static void logEventSummary() {
        System.out.println("[AIDebugLogger] Summary: " + eventStats);
    }

    public static Map<String, Integer> getEventStats() {
        return new HashMap<>(eventStats);
    }
}
