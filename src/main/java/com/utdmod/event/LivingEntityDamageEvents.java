package com.utdmod.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;

public final class LivingEntityDamageEvents {
    @FunctionalInterface
    public interface AfterDamageCallback {
        void afterDamage(LivingEntity entity, net.minecraft.entity.damage.DamageSource source, float amount, ActionResult result);
    }

    public static final Event<AfterDamageCallback> AFTER_DAMAGE = EventFactory.createArrayBacked(
        AfterDamageCallback.class,
        (listeners) -> (entity, source, amount, result) -> {
            for (AfterDamageCallback listener : listeners) {
                listener.afterDamage(entity, source, amount, result);
            }
        }
    );

    public static void initialize() {
        // Register the damage event handler
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (amount > 0) {
                AFTER_DAMAGE.invoker().afterDamage(entity, source, amount, ActionResult.PASS);
            }
            return true;
        });
    }
}
