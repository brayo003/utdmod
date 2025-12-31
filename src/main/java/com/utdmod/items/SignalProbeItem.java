package com.utdmod.items;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.text.Text;

import com.utdmod.core.TensionManager;
import com.utdmod.storm.StormManager;

public class SignalProbeItem extends Item {
    
    public SignalProbeItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient()) {
            return TypedActionResult.success(player.getStackInHand(hand));
        }
        
        // Get current tension from server
        double tension = TensionManager.getTension();
        boolean storm = StormManager.isStormActive();
        
        // Send message to player
        player.sendMessage(Text.literal("§6UTD Probe: §fTension = " + 
            String.format("%.1f", tension) + " | Storm: " + (storm ? "§cACTIVE" : "§aCALM")), false);
        
        return TypedActionResult.success(player.getStackInHand(hand));
    }
}
