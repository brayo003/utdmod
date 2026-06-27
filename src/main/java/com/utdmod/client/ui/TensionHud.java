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

        String tensionText = String.format("Tension: %.2f (local %.2f | feel %.2f)", tGlobal, tLocal, tFeel);
        int tensionColor = tFeel > 1.5f ? 0xFF5555 : tFeel > 1.0f ? 0xFFFF55 : 0x55FF55;
        context.drawText(mc.textRenderer, tensionText, 10, 10, tensionColor, true);

        if (storm) {
            context.drawText(mc.textRenderer, "STORM ACTIVE", 10, 25, 0xFF0000, true);
        }

        // Optional region HUD (disabled by default)
        if (com.utdmod.client.RegionHudConfig.SHOW_REGION_HUD) {
            int x = 10;
            int y = 40;
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
