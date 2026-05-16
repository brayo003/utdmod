package com.utdmod.client.ui;

import com.utdmod.client.TensionSyncState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class TensionHud {

    public static void register() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> renderHud(context));
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
    }
}
