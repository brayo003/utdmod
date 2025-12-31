package com.utdmod.registry;

import com.utdmod.UTDMod;
import com.utdmod.entity.TensionWraithEntity;
import com.utdmod.entity.TensionSerpentEntity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {

    public static final EntityType<TensionWraithEntity> TENSION_WRAITH = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(UTDMod.MOD_ID, "tension_wraith"),
            EntityType.Builder.create(TensionWraithEntity::new, SpawnGroup.MONSTER)
                    .setDimensions(0.75f, 1.5f)
                    .maxTrackingRange(8)
                    .build("tension_wraith")
    );

    public static final EntityType<TensionSerpentEntity> TENSION_SERPENT = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(UTDMod.MOD_ID, "tension_serpent"),
            EntityType.Builder.create(TensionSerpentEntity::new, SpawnGroup.MONSTER)
                    .setDimensions(1.0f, 2.0f)
                    .maxTrackingRange(8)
                    .build("tension_serpent")
    );

    public static void registerModEntities() {
        // Mod entities registered
    }
}
