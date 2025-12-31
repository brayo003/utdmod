package com.utdmod.client;

public class UTDModClientInitializer {
    private static boolean networkingRegistered = false;

    public static void registerClient() {
        System.out.println("UTD Mod client components registered");
    }

    public static void registerNetworking() {
        if (!networkingRegistered) {
            networkingRegistered = true;
        }
    }
}
