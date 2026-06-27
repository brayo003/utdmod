package com.utdmod.client;

import com.utdmod.network.DebugSpikeTensionPacket;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class TensionKeybind {
    private static KeyBinding tensionKey;
    private static KeyBinding toggleRegionHudKey;

    public static void register() {
        tensionKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.utdmod.spike",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "category.utdmod"
        ));
        toggleRegionHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.utdmod.toggle_region_hud",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "category.utdmod"
        ));
    }

    public static void tick() {
        while (tensionKey.wasPressed()) {
            DebugSpikeTensionPacket.sendToServer();
        }
        while (toggleRegionHudKey.wasPressed()) {
            com.utdmod.client.RegionHudConfig.SHOW_REGION_HUD = !com.utdmod.client.RegionHudConfig.SHOW_REGION_HUD;
        }
    }
}
