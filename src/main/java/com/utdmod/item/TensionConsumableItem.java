package com.utdmod.item;

import com.utdmod.signals.TensionManager;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class TensionConsumableItem extends Item {
    private final String itemId;
    
    public TensionConsumableItem(String id, FabricItemSettings settings) {
        super(settings);
        this.itemId = id;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);
        
        if (!world.isClient) {
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) user;

            switch (itemId) {
                case "volatile_insight_potion":
                    serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 600, 0));
                    TensionManager.setTension(TensionManager.getTension() + 0.05);
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5F, 1.0F);
                    break;

                case "hushed_lure":
                    TensionManager.setTension(Math.max(0, TensionManager.getTension() - 0.02));
                    serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 200, 0));
                    world.getPlayers().forEach(p -> {
                        if (p.distanceTo(user) < 32 && p != user) {
                            TensionManager.setTension(Math.max(0, TensionManager.getTension() - 0.01));
                        }
                    });
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.AMBIENT, 0.5F, 0.1F);
                    break;
                    
                case "kinetic_phial":
                    serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 1));
                    System.out.println("[TENSION LOCK] Kinetic Phial activated. Tension reduction effects are now disabled for 60 seconds.");
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7F, 1.5F);
                    break;
            }

            if (!user.getAbilities().creativeMode) {
                itemStack.decrement(1);
            }
        }
        
        return TypedActionResult.success(itemStack, world.isClient());
    }
}
