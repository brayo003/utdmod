package com.utdmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import com.utdmod.client.TensionKeybind;

public class UTDModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TensionKeybind.register();
        
        // Register keybind tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TensionKeybind.tick();
        });
        
        // Client initialized
    }
}
