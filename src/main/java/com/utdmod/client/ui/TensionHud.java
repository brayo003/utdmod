package com.utdmod.client.ui;

import com.utdmod.client.TensionSyncState;
import com.utdmod.client.visual.ClientRegionPresentation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class TensionHud {

    public static void register() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            ClientRegionPresentation.renderOverlay(context, tickDelta);
            renderHud(context);
        });
    }

    /** Kept for callers that push state without going through {@link TensionSyncState} only. */
    public static void updateTension(double tension, boolean storm) {
        TensionSyncState.applySnapshot(tension, storm);
    }

    private static void renderHud(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        float tGlobal = (float) TensionSyncState.CLIENT_TENSION;
        float tLocal = (float) TensionSyncState.CLIENT_LOCAL_TENSION;
        float tFeel = (float) TensionSyncState.perceivedTension();
        boolean storm = TensionSyncState.CLIENT_STORM;

        int tensionColor = tFeel > 1.5f ? 0xFF5555 : tFeel > 1.0f ? 0xFFFF55 : 0x55FF55;
        String tensionText = String.format("Tension: %.2f (local %.2f | feel %.2f)", tGlobal, tLocal, tFeel);
        String researchMode = com.utdmod.client.TensionSyncState.CLIENT_RESEARCH_MODE;
        String narrative = com.utdmod.client.TensionSyncState.CLIENT_RESEARCH_NARRATIVE;
        context.drawText(mc.textRenderer, tensionText, 10, 10, tensionColor, true);
        context.drawText(mc.textRenderer, "Research: " + researchMode, 10, 25, 0x88CCFF, true);
        context.drawText(mc.textRenderer, String.format("Regions:%d Largest:%d Scars:%d Waves:%d Osc:%d", TensionSyncState.CLIENT_RESEARCH_ACTIVE_REGIONS, TensionSyncState.CLIENT_RESEARCH_LARGEST_REGION_CHUNKS, TensionSyncState.CLIENT_RESEARCH_SCAR_COUNT, TensionSyncState.CLIENT_RESEARCH_WAVE_COUNT, TensionSyncState.CLIENT_RESEARCH_OSCILLATION_COUNT), 10, 40, 0xFFFFFF, true);
        context.drawText(mc.textRenderer, String.format("Substrate depth:%.3f transport:(%.2f, %.2f)", TensionSyncState.CLIENT_RESEARCH_FIELD_DEPTH_MEAN, TensionSyncState.CLIENT_RESEARCH_TRANSPORT_X, TensionSyncState.CLIENT_RESEARCH_TRANSPORT_Z), 10, 54, 0xFFFFFF, true);
        if (!narrative.isBlank()) {
            context.drawText(mc.textRenderer, narrative, 10, 68, 0xFFFFFF, true);
        }

        if (storm) {
            context.drawText(mc.textRenderer, "STORM ACTIVE", 10, 25, 0xFF0000, true);
        }

        // Optional region HUD (disabled by default)
        if (com.utdmod.client.RegionHudConfig.SHOW_REGION_HUD) {
            int x = 10;
            int y = 60;
            String id = String.format("Region: %d", TensionSyncState.CLIENT_REGION_ID);
            String state = String.format("State: %s", com.utdmod.core.RegionState.values()[Math.max(0, Math.min(TensionSyncState.CLIENT_REGION_STATE, com.utdmod.core.RegionState.values().length - 1))]);
            String age = String.format("Age: %d", TensionSyncState.CLIENT_REGION_AGE);
            String mat = String.format("Maturity: %.3f", TensionSyncState.CLIENT_REGION_MATURITY);
            String avg = String.format("AvgTension: %.3f", TensionSyncState.CLIENT_REGION_AVG);
            String chunks = String.format("Chunks: %d", TensionSyncState.CLIENT_REGION_CHUNK_COUNT);
            context.drawText(mc.textRenderer, id, x, y, 0xFFFFFF, true);
            context.drawText(mc.textRenderer, state, x, y + 12, 0xFFFFFF, true);
            context.drawText(mc.textRenderer, age, x, y + 24, 0xFFFFFF, true);
            context.drawText(mc.textRenderer, mat, x, y + 36, 0xFFFFFF, true);
            context.drawText(mc.textRenderer, avg, x, y + 48, 0xFFFFFF, true);
            context.drawText(mc.textRenderer, chunks, x, y + 60, 0xFFFFFF, true);
        }
    }
}
