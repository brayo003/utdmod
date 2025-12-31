package com.utdmod.tick;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class OptimizedTickHandler {
    private static int tickCounter = 0;
    private static final int UPDATE_INTERVAL = 20;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < UPDATE_INTERVAL) return;
            tickCounter = 0;
            // Example updates
            // globalSignal.update(server);
            // TensionWorldEffects.update(server.getOverworld(), tension);
        });
    }
}
