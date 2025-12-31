package com.utdmod.mixin;

import com.utdmod.signals.TensionManager;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EndermanEntity.class)
public abstract class EndermanEntityMixin {

    @Shadow
    public World world;

    @Inject(method = "teleport", at = @At("HEAD"))
    private void utdmod$addAggressiveTeleport(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        EndermanEntity enderman = (EndermanEntity)(Object)this;
        
        if (!world.isClient()) {
            PlayerEntity target = world.getClosestPlayer(enderman, 64.0);
            
            if (target instanceof ServerPlayerEntity serverPlayer) {
                double tension = TensionManager.getTension();
                if (tension > 0.4) { 
                    if (world.random.nextFloat() < (tension * 0.1)) { 
                        Vec3d targetPos = target.getPos();
                        double offset = 4.0;
                        double newX = targetPos.getX() + (world.random.nextDouble() - 0.5) * offset;
                        double newY = targetPos.getY() + world.random.nextDouble() * 2.0;
                        double newZ = targetPos.getZ() + (world.random.nextDouble() - 0.5) * offset;

                        if (enderman.teleport(newX, newY, newZ, true)) {
                            world.getOtherEntities(enderman, enderman.getBoundingBox().expand(2.0)).forEach(entity -> {
                                entity.damage(enderman.getDamageSources().magic(), 3.0f * (float)tension);
                            });
                            System.out.println("[AI LOG] Enderman: Unstable teleport triggered. Tension: " + tension);
                        }
                    }
                }
            }
        }
    }
}
