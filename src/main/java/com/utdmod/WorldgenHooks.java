package com.utdmod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;

public class WorldgenHooks {

    public static void register() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            System.out.println("World loaded: safe to attach scanners later.");
        });
    }
}
