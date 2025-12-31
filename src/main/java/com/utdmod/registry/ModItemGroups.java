package com.utdmod.registry;

import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;

import com.utdmod.UTDMod;

public class ModItemGroups {
    public static final net.minecraft.item.ItemGroup UTD_GROUP = Registry.register(
        Registries.ITEM_GROUP,
        new Identifier(UTDMod.MOD_ID, "utd_group"),
        FabricItemGroup.builder()
            .displayName(Text.translatable("itemGroup." + UTDMod.MOD_ID + ".utd_group"))
            .icon(() -> new ItemStack(ModItems.SIGNAL_PROBE))
            .entries((context, entries) -> {
                entries.add(ModBlocks.RITUAL_BLOCK);
                entries.add(ModItems.SIGNAL_PROBE);
                entries.add(ModItems.WARDING_CRYSTAL);
            })
            .build()
    );
    
    public static void registerItemGroups() {
        // Item group registered
    }
}
