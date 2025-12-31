package com.utdmod.client.visual;

import net.minecraft.particle.ParticleType;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class TensionParticleTypes {

    public static final DefaultParticleType RITUAL_DEGRADE = registerParticle("ritual_degrade", false);
    public static final DefaultParticleType RITUAL_REWARD = registerParticle("ritual_reward", false);
    
    private static DefaultParticleType registerParticle(String name, boolean alwaysSpawn) {
        return Registry.register(Registries.PARTICLE_TYPE, new Identifier("utdmod", name), 
            new DefaultParticleType(alwaysSpawn) {});
    }
    
    public static void initializeParticles() {
        // Force static initialization
    }
}
