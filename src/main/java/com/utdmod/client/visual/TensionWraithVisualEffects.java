package com.utdmod.client.visual;

import com.utdmod.entity.TensionWraithEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

public class TensionWraithVisualEffects {
    
    public static void createSpawnEffect(TensionWraithEntity wraith) {
        ClientWorld world = MinecraftClient.getInstance().world;
        Vec3d pos = wraith.getPos();
        
        if (world == null) return;

        for (int i = 0; i < 20; i++) {
            double angle = Math.random() * Math.PI * 2;
            double radius = Math.random() * 2.0;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            double y = pos.y + Math.random() * 1.0;
            
            world.addParticle(ParticleTypes.SOUL, x, y, z, 0, 0, 0);
        }
    }
}
