package com.utdmod.client.overlay;

import com.utdmod.UTDMod;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class TensionMeterOverlay implements HudRenderCallback {

    private static final Identifier TENSION_METER_TEXTURE = new Identifier(UTDMod.MOD_ID, "textures/gui/tension_meter.png");
    private static final int WIDTH = 128;
    private static final int HEIGHT = 32;

    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int currentTensionLevel = 5;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        int x = (screenWidth / 2) - (WIDTH / 2);
        int y = screenHeight - HEIGHT - 10;

        context.drawTexture(TENSION_METER_TEXTURE, x, y, 0, 0, WIDTH, HEIGHT);

        if (currentTensionLevel > 0) {
            int fillHeight = 16;
            int fillWidth = (int)(((float)currentTensionLevel / 10.0F) * (WIDTH - 16)); 
            context.drawTexture(TENSION_METER_TEXTURE, x + 8, y + 8, 0, HEIGHT, fillWidth, fillHeight);
        }
    }
}
