package com.utdmod.registry;

import com.utdmod.UTDMod;
import com.utdmod.item.RitualStabilizerItem;
import com.utdmod.items.SignalProbeItem;
import com.utdmod.items.WardingCrystalItem;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {

    public static final Item SIGNAL_PROBE = registerItem("signal_probe",
        new SignalProbeItem(new FabricItemSettings().maxCount(1)));

    public static final Item WARDING_CRYSTAL = registerItem("warding_crystal",
        new WardingCrystalItem(new FabricItemSettings().maxCount(16)));

    /** Placed {@link ModBlocks#RITUAL_BLOCK}. */
    public static final Item RITUAL_BLOCK = registerItem("ritual_block",
        new BlockItem(ModBlocks.RITUAL_BLOCK, new FabricItemSettings().maxCount(64)));

    /** Hand-held tension sink (former ritual block item behavior). */
    public static final Item TENSION_STABILIZER = registerItem("tension_stabilizer",
        new RitualStabilizerItem(new FabricItemSettings().maxCount(64)));

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(UTDMod.MOD_ID, name), item);
    }

    public static void registerModItems() {
        // static fields register on class load
    }
}
