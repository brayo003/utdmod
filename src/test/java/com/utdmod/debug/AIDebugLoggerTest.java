package com.utdmod.debug;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AI Debug Logger optimization metrics
 * Validates the logging system for metrics M-1 through M-6
 */
public class AIDebugLoggerTest {

    @BeforeEach
    void setUp() {
        AIDebugLogger.resetCounters();
    }

    @Test
    void testMetricM1_TensionChangeRateYLevelDelta() {
        // Test M-1: tension_change_rate_Y_level_delta
        AIDebugLogger.logTensionChangeRateYLevelDelta(0.8, 25.0, 0.05, "CAVE_PRESSURE");
        
        var stats = AIDebugLogger.getEventStats();
        assertEquals(1L, stats.get("m1_y_level_delta_events"));
    }

    @Test
    void testMetricM2_RitualBlockActivationTension() {
        // Test M-2: ritual_block_activation_tension
        AIDebugLogger.logRitualBlockActivationTension(0.9, "CHAOS", "TestPlayer");
        
        var stats = AIDebugLogger.getEventStats();
        assertEquals(1L, stats.get("m2_ritual_activation_tension_events"));
    }

    @Test
    void testMetricM3_SerpentSpawnTriggerTime() {
        // Test M-3: serpent_spawn_trigger_time
        long testTime = System.currentTimeMillis() - 5000; // 5 seconds ago
        AIDebugLogger.logSerpentSpawnTriggerTime(0.96, testTime, "TestPlayer");
        
        var stats = AIDebugLogger.getEventStats();
        assertEquals(1L, stats.get("m3_serpent_spawn_trigger_events"));
    }

    @Test
    void testMetricM4_WraithSpawnDistanceOnTrigger() {
        // Test M-4: wraith_spawn_distance_on_trigger
        AIDebugLogger.logWraithSpawnDistanceOnTrigger(100.0, 64.0, 200.0, 
                                                      105.0, 64.0, 195.0, "TestPlayer");
        
        var stats = AIDebugLogger.getEventStats();
        assertEquals(1L, stats.get("m4_wraith_spawn_distance_events"));
    }

    @Test
    void testMetricM5_VillagerTradeRewardAppliedCount() {
        // Test M-5: villager_trade_reward_applied_count
        AIDebugLogger.logVillagerTradeRewardAppliedCount(0.1, 10, 8, "TestPlayer", "FARMER");
        
        var stats = AIDebugLogger.getEventStats();
        assertEquals(1L, stats.get("m5_villager_trade_reward_events"));
    }

    @Test
    void testMetricM6_GolemApathyTriggerCount() {
        // Test M-6: golem_apathy_trigger_count
        AIDebugLogger.logGolemApathyTriggerCount(0.8, "TestPlayer", 100.0, 64.0, 200.0);
        
        var stats = AIDebugLogger.getEventStats();
        assertEquals(1L, stats.get("m6_golem_apathy_trigger_events"));
    }

    @Test
    void testHighTensionDurationTracking() {
        // Test high tension duration tracking
        AIDebugLogger.logHighTensionDuration(0.96, "TestPlayer");
        AIDebugLogger.logHighTensionDuration(0.94, "TestPlayer"); // Should end duration
        
        var stats = AIDebugLogger.getEventStats();
        assertNotNull(stats.get("event_context"));
    }

    @Test
    void testEventSummary() {
        // Test all metrics and summary generation
        AIDebugLogger.logTensionChangeRateYLevelDelta(0.8, 25.0, 0.05, "CAVE_PRESSURE");
        AIDebugLogger.logRitualBlockActivationTension(0.9, "CHAOS", "TestPlayer");
        AIDebugLogger.logSerpentSpawnTriggerTime(0.96, System.currentTimeMillis() - 5000, "TestPlayer");
        AIDebugLogger.logWraithSpawnDistanceOnTrigger(100.0, 64.0, 200.0, 105.0, 64.0, 195.0, "TestPlayer");
        AIDebugLogger.logVillagerTradeRewardAppliedCount(0.1, 10, 8, "TestPlayer", "FARMER");
        AIDebugLogger.logGolemApathyTriggerCount(0.8, "TestPlayer", 100.0, 64.0, 200.0);
        
        // This should generate a comprehensive summary
        assertDoesNotThrow(() -> AIDebugLogger.logEventSummary());
        
        var stats = AIDebugLogger.getEventStats();
        assertEquals(1L, stats.get("m1_y_level_delta_events"));
        assertEquals(1L, stats.get("m2_ritual_activation_tension_events"));
        assertEquals(1L, stats.get("m3_serpent_spawn_trigger_events"));
        assertEquals(1L, stats.get("m4_wraith_spawn_distance_events"));
        assertEquals(1L, stats.get("m5_villager_trade_reward_events"));
        assertEquals(1L, stats.get("m6_golem_apathy_trigger_events"));
    }

    @Test
    void testCounterReset() {
        // Test counter reset functionality
        AIDebugLogger.logTensionChangeRateYLevelDelta(0.8, 25.0, 0.05, "CAVE_PRESSURE");
        AIDebugLogger.resetCounters();
        
        var stats = AIDebugLogger.getEventStats();
        assertEquals(0L, stats.get("m1_y_level_delta_events"));
    }
}
