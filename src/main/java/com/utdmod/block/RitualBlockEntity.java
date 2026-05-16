package com.utdmod.block;

import com.utdmod.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public class RitualBlockEntity extends BlockEntity {

    public RitualBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RITUAL_BLOCK_ENTITY, pos, state);
    }
}
