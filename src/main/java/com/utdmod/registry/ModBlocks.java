package com.utdmod.registry;

import com.utdmod.UTDMod;
import com.utdmod.block.RitualBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class ModBlocks {

    public static final Block RITUAL_BLOCK = new RitualBlock(
        AbstractBlock.Settings.create()
            .mapColor(MapColor.STONE_GRAY)
            .strength(3.0F, 3.0F)
            .sounds(BlockSoundGroup.STONE)
    );

    public static void registerModBlocks() {
        Registry.register(Registries.BLOCK, new Identifier(UTDMod.MOD_ID, "ritual_block"), RITUAL_BLOCK);
    }
}
