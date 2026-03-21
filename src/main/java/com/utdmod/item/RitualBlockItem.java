package com.utdmod.item;

import com.utdmod.core.TensionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class RitualBlockItem extends Item {
    public RitualBlockItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient) {
            // Reduce tension by 3 when used
            TensionManager.reduceTension(3.0);
            user.sendMessage(net.minecraft.text.Text.literal("§aRitual stabilized the area! -3.0 tension"), true);
            
            // Consume one item from stack
            ItemStack stack = user.getStackInHand(hand);
            if (!user.getAbilities().creativeMode) {
                stack.decrement(1);
            }
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }
}
