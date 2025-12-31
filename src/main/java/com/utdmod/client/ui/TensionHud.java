package com.utdmod.client.ui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class TensionHud {
    private static float currentTension = 0.0f;
    private static boolean stormActive = false;
    
    public static void register() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            renderHud(context);
        });
    }
    
    public static void updateTension(double tension, boolean storm) {
        currentTension = (float) tension;
        stormActive = storm;
    }
    
    private static void renderHud(DrawContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        
        // Tension display
        String tensionText = String.format("Tension: %.1f", currentTension);
        int tensionColor = currentTension > 50 ? 0xFF5555 : currentTension > 25 ? 0xFFFF55 : 0x55FF55;
        context.drawText(mc.textRenderer, tensionText, 10, 10, tensionColor, true);
        
        // Storm status
        if (stormActive) {
            String stormText = "STORM ACTIVE";
            context.drawText(mc.textRenderer, stormText, 10, 25, 0xFF0000, true);
        }
    }
}
