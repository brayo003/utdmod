package com.utdmod.block;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.mojang.serialization.MapCodec;

public class RitualBlock extends BlockWithEntity {

    public static final BooleanProperty DEGRADED = BooleanProperty.of("degraded");

    public RitualBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(DEGRADED, false));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return null; // Simplified for now
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(DEGRADED);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return null; // Simplified for now
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient() && hand == Hand.MAIN_HAND && player instanceof ServerPlayerEntity) {
            world.updateListeners(pos, state, state, 3);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
}
