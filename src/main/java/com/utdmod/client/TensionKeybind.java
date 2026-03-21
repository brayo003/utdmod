package com.utdmod.client;

import com.utdmod.core.TensionManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class TensionKeybind {
    private static KeyBinding tensionKey;

    public static void register() {
        tensionKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.utdmod.spike",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                "category.utdmod"
        ));
    }

    public static void tick() {
        while (tensionKey.wasPressed()) {
            System.out.println("[UTDMod-DEBUG] Keybind pressed! Adding 0.5 tension");
            TensionManager.addEvent(0.5);
            System.out.println("[UTDMod-DEBUG] Tension spiked by 0.5, now " + TensionManager.getTension());
        }
    }
}
