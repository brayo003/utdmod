package com.utdmod.items;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.text.Text;

import com.utdmod.signals.TensionManager;
import com.utdmod.ritual.RitualHandler;

public class WardingCrystalItem extends Item {
    
    public WardingCrystalItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient()) {
            // Send packet to server to handle crystal use
            com.utdmod.network.UseCrystalPacket.sendToServer();
            return TypedActionResult.success(player.getStackInHand(hand));
        }
        
        // Server-side logic is handled by the packet receiver
        return TypedActionResult.success(player.getStackInHand(hand));
    }
    
    public static void performWardingEffect(PlayerEntity player) {
        // Apply warding effect - reduce tension
        TensionManager.reduceTension(0.3);
        
        // Send feedback to player
        player.sendMessage(Text.literal("§bWarding Crystal activated! Tension reduced."));
        
        // Warding crystal used
    }
}
