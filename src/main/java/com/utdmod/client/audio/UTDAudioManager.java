package com.utdmod.client.audio;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import com.utdmod.signals.TensionManager;

public class UTDAudioManager {
    
    public static void updateAmbientSounds() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        double tension = TensionManager.getTension();
        Vec3d pos = client.player.getPos();
        float volume = (float)(tension * 0.5);
        
        if (tension > 0.3 && client.world.random.nextFloat() < 0.1f) {
            client.world.playSound(pos.x, pos.y, pos.z,
                                  SoundEvents.ENTITY_WITHER_AMBIENT,
                                  SoundCategory.AMBIENT,
                                  volume, 0.8f, false);
        }
    }
}
