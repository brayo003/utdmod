package com.utdmod.client.visual;

import com.utdmod.client.ClientFeelingCounters;
import com.utdmod.client.TensionSyncState;
import com.utdmod.UTDMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * HUD meter plus tiered escalation feedback (fog tint / vignette) keyed to synced global tension.
 */
public final class TensionScreenOverlay {

    private static final Identifier METER_TEXTURE = new Identifier(UTDMod.MOD_ID, "textures/gui/tension_meter.png");
    private static final int METER_WIDTH = 128;
    private static final int METER_HEIGHT = 32;
    private static final double TENSION_CAP = 5.0;

    private TensionScreenOverlay() {}

    private static String tierLabel(double t) {
        if (t < 0.4) return "Calm";
        if (t < 0.8) return "Uneasy";
        if (t < 1.5) return "Strained";
        if (t < 3.0) return "Fractured";
        return "Decoupled";
    }

    private static int vignetteAlpha(double t) {
        if (t < 0.4) return 0;
        if (t < 0.8) return (int) (20 + (t - 0.4) * 60);
        if (t < 1.5) return (int) (44 + (t - 0.8) * 40);
        if (t < 3.0) return (int) (72 + (t - 1.5) * 28);
        return 110;
    }

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        double tFeel = TensionSyncState.perceivedTension();
        double reg = TensionSyncState.perceivedRegionalFeel();
        double eff = Math.max(tFeel, reg * 0.78);
        boolean storm = TensionSyncState.CLIENT_STORM;

        if (eff > 0.38) {
            int fogA = (int) Math.min(58, 6 + (eff - 0.38) * 48);
            int fogRgb = eff > 1.22 ? 0x101820 : 0x151a28;
            context.fill(0, 0, screenWidth, screenHeight, (fogA << 24) | (fogRgb & 0xFFFFFF));
        }

        if (eff > 0.95) {
            int desat = (int) Math.min(40, (eff - 0.95) * 55);
            context.fill(0, 0, screenWidth, screenHeight, (desat << 24) | 0x808080);
        }

        if (eff > 1.08 && client.world != null) {
            long phase = client.world.getTime();
            int chromaA = (int) Math.min(22, (eff - 1.08) * 38);
            for (int i = 0; i < screenWidth; i += 86) {
                int shift = (int) ((phase + i * 3L) % 6L);
                context.fill(i + shift, 0, i + shift + 1, screenHeight, (chromaA << 24) | 0x660000);
                context.fill(i - shift, 0, i - shift + 1, screenHeight, (chromaA << 24) | 0x000066);
            }
        }

        if (reg > 1.32 && client.world != null && client.world.getTime() % 140 < 1) {
            context.fill(0, 0, screenWidth, screenHeight, 0x0CFFFFFF);
        }

        if (storm && client.world != null && client.world.getTime() % 18 < 3) {
            context.fill(0, 0, screenWidth, screenHeight, 0x12000000);
        }

        int va = Math.min(220, vignetteAlpha(eff));
        if (va > 0) {
            int c = (va << 24);
            int edge = Math.min(screenWidth, screenHeight) / 5;
            context.fill(0, 0, screenWidth, edge, c);
            context.fill(0, screenHeight - edge, screenWidth, screenHeight, c);
            context.fill(0, 0, edge, screenHeight, c);
            context.fill(screenWidth - edge, 0, screenWidth, screenHeight, c);
        }

        int x = screenWidth - METER_WIDTH - 10;
        int y = 10;

        context.drawTexture(METER_TEXTURE, x, y, 0, 0, METER_WIDTH, METER_HEIGHT);

        if (eff > 0) {
            int fillWidth = (int) ((eff / TENSION_CAP) * (METER_WIDTH - 16));
            fillWidth = Math.max(0, Math.min(fillWidth, METER_WIDTH - 16));
            context.drawTexture(METER_TEXTURE, x + 8, y + 8, 0, METER_HEIGHT, fillWidth, 16);
        }

        String label = tierLabel(eff);
        context.drawTextWithShadow(client.textRenderer, label, x, y + METER_HEIGHT + 4, 0xCCCCCC);

        ClientFeelingCounters.overlayIntensity = Math.min(1.0, eff / 2.1);
    }
}
