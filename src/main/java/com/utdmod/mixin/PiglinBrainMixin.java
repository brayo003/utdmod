package com.utdmod.mixin;

import com.utdmod.signals.TensionManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
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
                
                if (nearestPlayer instanceof ServerPlayerEntity serverPlayer) {
                    double tension = TensionManager.getTension();
                    
                    if (tension > 0.0 && world.random.nextFloat() < (tension * 0.1)) {
                        System.out.println("[AI LOG] Piglin: Tension-induced hostility triggered (Trade Volatility). Tension: " + tension);
                        piglin.setTarget(serverPlayer); 
                    }
                }
            }
        }
    }
}
