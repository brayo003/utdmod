package com.utdmod.items;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class WardingCrystalItem extends Item {

    public WardingCrystalItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient()) {
            com.utdmod.network.UseCrystalPacket.sendToServer();
            return TypedActionResult.success(player.getStackInHand(hand));
        }
        return TypedActionResult.success(player.getStackInHand(hand));
    }
}
