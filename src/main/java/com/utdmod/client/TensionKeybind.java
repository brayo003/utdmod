package com.utdmod.client;

import com.utdmod.network.DebugSpikeTensionPacket;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class TensionKeybind {
    private static KeyBinding tensionKey;
    private static KeyBinding toggleRegionHudKey;
    private static KeyBinding activationModeKey;
    private static KeyBinding memoryModeKey;
    private static KeyBinding transportModeKey;
    private static KeyBinding depthModeKey;

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
        activationModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.utdmod.microscope_activation",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "category.utdmod"
        ));
        memoryModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.utdmod.microscope_memory",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "category.utdmod"
        ));
        transportModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.utdmod.microscope_transport",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            "category.utdmod"
        ));
        depthModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.utdmod.microscope_depth",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
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
        while (activationModeKey.wasPressed()) {
            TensionSyncState.applyResearchSnapshot("activation", "Activation mode", TensionSyncState.CLIENT_RESEARCH_ACTIVE_REGIONS, TensionSyncState.CLIENT_RESEARCH_LARGEST_REGION_CHUNKS, TensionSyncState.CLIENT_RESEARCH_SCAR_COUNT, TensionSyncState.CLIENT_RESEARCH_WAVE_COUNT, TensionSyncState.CLIENT_RESEARCH_OSCILLATION_COUNT, TensionSyncState.CLIENT_RESEARCH_FIELD_DEPTH_MEAN, TensionSyncState.CLIENT_RESEARCH_TRANSPORT_X, TensionSyncState.CLIENT_RESEARCH_TRANSPORT_Z, TensionSyncState.CLIENT_RESEARCH_REGION_CENTER_X, TensionSyncState.CLIENT_RESEARCH_REGION_CENTER_Z, TensionSyncState.CLIENT_RESEARCH_REGION_CHUNK_COUNT);
        }
        while (memoryModeKey.wasPressed()) {
            TensionSyncState.applyResearchSnapshot("memory", "Memory mode", TensionSyncState.CLIENT_RESEARCH_ACTIVE_REGIONS, TensionSyncState.CLIENT_RESEARCH_LARGEST_REGION_CHUNKS, TensionSyncState.CLIENT_RESEARCH_SCAR_COUNT, TensionSyncState.CLIENT_RESEARCH_WAVE_COUNT, TensionSyncState.CLIENT_RESEARCH_OSCILLATION_COUNT, TensionSyncState.CLIENT_RESEARCH_FIELD_DEPTH_MEAN, TensionSyncState.CLIENT_RESEARCH_TRANSPORT_X, TensionSyncState.CLIENT_RESEARCH_TRANSPORT_Z, TensionSyncState.CLIENT_RESEARCH_REGION_CENTER_X, TensionSyncState.CLIENT_RESEARCH_REGION_CENTER_Z, TensionSyncState.CLIENT_RESEARCH_REGION_CHUNK_COUNT);
        }
        while (transportModeKey.wasPressed()) {
            TensionSyncState.applyResearchSnapshot("transport", "Transport mode", TensionSyncState.CLIENT_RESEARCH_ACTIVE_REGIONS, TensionSyncState.CLIENT_RESEARCH_LARGEST_REGION_CHUNKS, TensionSyncState.CLIENT_RESEARCH_SCAR_COUNT, TensionSyncState.CLIENT_RESEARCH_WAVE_COUNT, TensionSyncState.CLIENT_RESEARCH_OSCILLATION_COUNT, TensionSyncState.CLIENT_RESEARCH_FIELD_DEPTH_MEAN, TensionSyncState.CLIENT_RESEARCH_TRANSPORT_X, TensionSyncState.CLIENT_RESEARCH_TRANSPORT_Z, TensionSyncState.CLIENT_RESEARCH_REGION_CENTER_X, TensionSyncState.CLIENT_RESEARCH_REGION_CENTER_Z, TensionSyncState.CLIENT_RESEARCH_REGION_CHUNK_COUNT);
        }
        while (depthModeKey.wasPressed()) {
            TensionSyncState.applyResearchSnapshot("depth", "Depth mode", TensionSyncState.CLIENT_RESEARCH_ACTIVE_REGIONS, TensionSyncState.CLIENT_RESEARCH_LARGEST_REGION_CHUNKS, TensionSyncState.CLIENT_RESEARCH_SCAR_COUNT, TensionSyncState.CLIENT_RESEARCH_WAVE_COUNT, TensionSyncState.CLIENT_RESEARCH_OSCILLATION_COUNT, TensionSyncState.CLIENT_RESEARCH_FIELD_DEPTH_MEAN, TensionSyncState.CLIENT_RESEARCH_TRANSPORT_X, TensionSyncState.CLIENT_RESEARCH_TRANSPORT_Z, TensionSyncState.CLIENT_RESEARCH_REGION_CENTER_X, TensionSyncState.CLIENT_RESEARCH_REGION_CENTER_Z, TensionSyncState.CLIENT_RESEARCH_REGION_CHUNK_COUNT);
        }
    }
}
