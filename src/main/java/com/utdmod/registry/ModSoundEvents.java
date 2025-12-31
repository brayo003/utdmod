package com.utdmod.registry;

import com.utdmod.UTDMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSoundEvents {
    
    // Define custom sound event identifiers here (if not already done in ModSounds.java)
    // Example: public static final SoundEvent RITUAL_ACTIVATE = registerSoundEvent("ritual_activate");
    
    private static SoundEvent registerSoundEvent(String name) {
        Identifier id = new Identifier(UTDMod.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
    
    public static void registerAllSoundEvents() {
        // If ModSounds.java only defined the identifiers, this method would register them.
        // Assuming ModSounds.java already calls Registry.register, this function serves as the hook.
        UTDMod.LOGGER.info("Sound events registered and initialized.");
    }
}
