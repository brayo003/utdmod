package com.utdmod.registry;

import com.utdmod.UTDMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.attribute.EntityAttributes;

public class ModEntityAttributes {

    public static void registerTensionSerpentAttributes() {
        // Attributes will be registered when entity is properly defined
        // Serpent attributes registered
    }

    public static void registerTensionWraithAttributes() {
        FabricDefaultAttributeRegistry.register(
            ModEntities.TENSION_WRAITH,
            net.minecraft.entity.LivingEntity.createLivingAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.7)
        );
        // Wraith attributes registered
    }

    public static void registerAllAttributes() {
        registerTensionSerpentAttributes();
        registerTensionWraithAttributes();
    }
}
