package com.utdmod.mixin;

import com.utdmod.core.TensionManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PiglinEntity.class)
public abstract class PiglinBrainMixin {

    @Shadow
    public World world;

    @Inject(method = "acceptsItem", at = @At("HEAD"))
    private void utdmod$increaseHostilityChance(LivingEntity entity, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof PiglinEntity piglin) {
            if (!world.isClient()) {
                PlayerEntity nearestPlayer = world.getClosestPlayer(piglin, 32.0);
                
                if (nearestPlayer instanceof ServerPlayerEntity) {
                    double tension = TensionManager.getTension();
                    long tick = world.getTime();
                    if (deterministicFloat(piglin.getBlockPos(), tick, 0) < (tension * 0.1)) {
                        piglin.setTarget(nearestPlayer); 
                    }
                }
            }
        }
    }

    private static float deterministicFloat(BlockPos pos, long tick, int salt) {
        double value = Math.sin((pos.getX() * 31L + pos.getY() * 17L + pos.getZ() * 13L + tick * 7L + salt * 97L) * 0.6180339887498948) * 43758.5453123;
        return (float) (value - Math.floor(value));
    }
}
