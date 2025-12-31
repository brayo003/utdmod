package com.utdmod.registry;

import com.utdmod.UTDMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {

    // Block/VFX Sounds
    public static final SoundEvent BLOCK_RITUAL_DEGRADE = registerSoundEvent("block_ritual_degrade");

    // Villager/Penalty Sounds
    public static final SoundEvent PENALTY_VILLAGER_LOW = registerSoundEvent("penalty_villager_low");

    // Entity Sounds
    public static final SoundEvent ENTITY_SERPENT_AMBIENT = registerSoundEvent("entity_serpent_ambient");
    public static final SoundEvent ENTITY_SERPENT_ATTACK = registerSoundEvent("entity_serpent_attack");
    public static final SoundEvent ENTITY_WRAITH_ATTACK = registerSoundEvent("entity_wraith_attack");
    public static final SoundEvent ENTITY_WRAITH_DEATH = registerSoundEvent("entity_wraith_death");


    private static SoundEvent registerSoundEvent(String name) {
        Identifier id = new Identifier(UTDMod.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void registerModSounds() {
        UTDMod.LOGGER.info("Registering Mod Sounds for " + UTDMod.MOD_ID);
        // This method is just to force the class to load and register the static fields
    }
}
