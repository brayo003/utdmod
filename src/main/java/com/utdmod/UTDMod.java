package com.utdmod;

import net.fabricmc.api.ModInitializer;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class UTDMod implements ModInitializer {
    public static final String MOD_ID = "utdmod";
    
    // Create simple test items
    public static final Item SIGNAL_PROBE = new Item(new Item.Settings());
    public static final Item WARDING_CRYSTAL = new Item(new Item.Settings());
    
    @Override
    public void onInitialize() {
        System.out.println("[UTDMod] Starting initialization...");
        
        // Register items
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "signal_probe"), SIGNAL_PROBE);
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "warding_crystal"), WARDING_CRYSTAL);
        
        System.out.println("[UTDMod] Items registered successfully!");
        System.out.println("[UTDMod] Mod initialized!");
    }
}
