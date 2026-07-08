package com.utdmod.mixin;

import com.utdmod.core.TensionManager;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
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
            
            if (target instanceof ServerPlayerEntity) {
                double tension = TensionManager.getTension();
                if (tension > 0.4) {
                    long tick = world.getTime();
                    if (deterministicFloat(enderman.getBlockPos(), tick, 0) < tension * 0.1) {
                        Vec3d targetPos = target.getPos();
                        double offset = 4.0;
                        double newX = targetPos.getX() + (deterministicFloat(targetPos, tick, 1) - 0.5) * offset;
                        double newY = targetPos.getY() + deterministicFloat(targetPos, tick, 2) * 2.0;
                        double newZ = targetPos.getZ() + (deterministicFloat(targetPos, tick, 3) - 0.5) * offset;

                        if (enderman.teleport(newX, newY, newZ, true)) {
                            world.getOtherEntities(enderman, enderman.getBoundingBox().expand(2.0)).forEach(entity -> {
                                entity.damage(enderman.getDamageSources().magic(), 3.0f * (float)tension);
                            });
                        }
                    }
                }
            }
        }
    }

    private static float deterministicFloat(BlockPos pos, long tick, int salt) {
        double value = Math.sin((pos.getX() * 31L + pos.getY() * 17L + pos.getZ() * 13L + tick * 7L + salt * 97L) * 0.6180339887498948) * 43758.5453123;
        return (float) (value - Math.floor(value));
    }

    private static float deterministicFloat(Vec3d pos, long tick, int salt) {
        double value = Math.sin((pos.x * 31.0 + pos.y * 17.0 + pos.z * 13.0 + tick * 7.0 + salt * 97.0) * 0.6180339887498948) * 43758.5453123;
        return (float) (value - Math.floor(value));
    }
}
