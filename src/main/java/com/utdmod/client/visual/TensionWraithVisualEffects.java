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
        long seed = wraith.getId() * 23L + wraith.age * 11L;

        for (int i = 0; i < 20; i++) {
            double angle = ((i * 73 + seed) % 360) * Math.PI / 180.0;
            double radius = deterministicDouble(seed, i, 0) * 2.0;
            double x = pos.x + Math.cos(angle) * radius;
            double z = pos.z + Math.sin(angle) * radius;
            double y = pos.y + deterministicDouble(seed, i, 1) * 1.0;
            
            world.addParticle(ParticleTypes.SOUL, x, y, z, 0, 0, 0);
        }
    }

    private static double deterministicDouble(long seed, int index, int salt) {
        double value = Math.sin((seed + index * 31L + salt * 19L) * 0.517243) * 43758.5453123;
        return value - Math.floor(value);
    }
}
