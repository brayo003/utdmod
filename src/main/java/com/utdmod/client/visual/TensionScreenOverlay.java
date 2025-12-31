package com.utdmod.client.visual;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class TensionScreenOverlay {
    
    private static final Identifier METER_TEXTURE = new Identifier("utdmod", "textures/gui/tension_meter.png");
    private static final int METER_WIDTH = 128;
    private static final int METER_HEIGHT = 32;
    
    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        
        int x = screenWidth - METER_WIDTH - 10;
        int y = 10;
        
        context.drawTexture(METER_TEXTURE, x, y, 0, 0, METER_WIDTH, METER_HEIGHT);
        
        // Draw tension fill
        double tension = com.utdmod.signals.TensionManager.getTension();
        if (tension > 0) {
            int fillWidth = (int)((tension / 10.0) * (METER_WIDTH - 16));
            context.drawTexture(METER_TEXTURE, x + 8, y + 8, 0, METER_HEIGHT, fillWidth, 16);
        }
    }
}
