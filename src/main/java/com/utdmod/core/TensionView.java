package com.utdmod.core;

import net.minecraft.world.World;

/**
 * Small convenience view to return an appropriate tension accessor for a world.
 * On the logical server this returns the authoritative TensionManager; on the
 * client it is intended to be implemented as a wrapper around the client mirror.
 */
public final class TensionView {
    private TensionView() {}

    public static Object getForWorld(World world) {
        if (world == null) return null;
        if (world.isClient()) {
            // Return the client mirror class reference so callers can cast accordingly
            try {
                return Class.forName("com.utdmod.client.TensionSyncState");
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        // On server return the authoritative manager class
        return TensionManager.class;
    }
}
