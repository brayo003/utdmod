package com.utdmod.client;

import com.utdmod.core.TensionManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class UTDModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        TensionKeybind.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TensionKeybind.tick();
        });
        System.out.println("[UTDMod] Client initialized. Keybind 'T' should spike tension.");
    }
}
