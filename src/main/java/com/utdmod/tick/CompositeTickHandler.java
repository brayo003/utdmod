package com.utdmod.tick;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class CompositeTickHandler {
    private static int ticks = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ticks++;
            if (ticks < 20) return;
            ticks = 0;
            // call any tick logic here
        });
    }
}
