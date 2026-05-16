package com.utdmod.registry;

import com.utdmod.UTDMod;
import com.utdmod.block.RitualBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {

    public static BlockEntityType<RitualBlockEntity> RITUAL_BLOCK_ENTITY;

    public static void registerBlockEntities() {
        RITUAL_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            new Identifier(UTDMod.MOD_ID, "ritual_block_entity"),
            FabricBlockEntityTypeBuilder.create(RitualBlockEntity::new, ModBlocks.RITUAL_BLOCK).build()
        );
    }
}
