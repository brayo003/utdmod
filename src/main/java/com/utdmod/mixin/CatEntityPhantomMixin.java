package com.utdmod.mixin;

import com.utdmod.signals.TensionManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CatEntity.class)
public abstract class CatEntityPhantomMixin {

    private static final float STABLE_TENSION_THRESHOLD = 0.20f;
    private static final double MAX_DETERRENCE_RADIUS_BOOST = 8.0;

    @Shadow
    public World world;

    @Inject(method = "canBeScared", at = @At("HEAD"), cancellable = true)
    private void utdmod$boostPhantomDeterrence(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        CatEntity cat = (CatEntity)(Object)this;

        if (entity.getType() == EntityType.PHANTOM && !world.isClient()) {
            PlayerEntity nearestPlayer = world.getClosestPlayer(cat, 64.0);
            if (nearestPlayer instanceof ServerPlayerEntity serverPlayer) {
                double tension = TensionManager.getTension();
                if (tension < STABLE_TENSION_THRESHOLD) {
                    float stabilityFactor = (float) ((STABLE_TENSION_THRESHOLD - tension) / STABLE_TENSION_THRESHOLD);
                    double bonusRadius = stabilityFactor * MAX_DETERRENCE_RADIUS_BOOST;
                    double effectiveRadius = 16.0 + bonusRadius;
                    if (cat.distanceTo(entity) < effectiveRadius) {
                        System.out.println("[CAT REWARD] Phantom deterred by stable Cat/Tension. Bonus Radius: " + bonusRadius);
                        cir.setReturnValue(true);
                        cir.cancel();
                    }
                } else if (tension < 0.4) {
                    cir.setReturnValue(false);
                    System.out.println("[CAT BUFF] Low tension enhances phantom deterrence");
                }
            }
        }
    }
}
