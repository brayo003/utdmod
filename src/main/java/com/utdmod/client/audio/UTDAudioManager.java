package com.utdmod.client.audio;

import com.utdmod.client.ClientFeelingCounters;
import com.utdmod.client.TensionSyncState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Layered ambient audio keyed to smoothed {@link TensionSyncState#perceivedTension()}.
 * Sparse random triggers per tick window to avoid spam.
 */
public final class UTDAudioManager {

    private UTDAudioManager() {}

    public static void updateAmbientSounds() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        double t = Math.max(TensionSyncState.perceivedTension(), TensionSyncState.CLIENT_REGION_AVG * 0.82);
        boolean storm = TensionSyncState.CLIENT_STORM;
        Vec3d pos = client.player.getPos();

        if (t < 0.32 && !storm) {
            return;
        }

        float baseChance = (float) Math.min(0.26, 0.03 + t * 0.055 + (storm ? 0.06f : 0f));
        if (client.world.random.nextFloat() > baseChance) {
            return;
        }

        float roll = client.world.random.nextFloat();

        // Low band: cave resonance + distant rumble (positional jitter reads as "wrong space")
        if (t < 0.75f && roll < 0.55f) {
            double ox = (client.world.random.nextDouble() - 0.5) * 48.0;
            double oz = (client.world.random.nextDouble() - 0.5) * 48.0;
            client.world.playSound(
                pos.x + ox, pos.y, pos.z + oz,
                SoundEvents.AMBIENT_CAVE.value(),
                SoundCategory.AMBIENT,
                0.1f + (float) t * 0.09f,
                0.72f + client.world.random.nextFloat() * 0.08f,
                false
            );
            ClientFeelingCounters.audioTriggers++;
            return;
        }

        if (t < 0.75f) {
            client.world.playSound(
                pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_MINECART_RIDING,
                SoundCategory.AMBIENT,
                0.04f + (float) t * 0.05f,
                0.35f,
                false
            );
            ClientFeelingCounters.audioTriggers++;
            return;
        }

        // Mid: whispers / stare + metallic resonance
        if (t < 1.35f && roll < 0.45f) {
            client.world.playSound(
                pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_VEX_AMBIENT,
                SoundCategory.HOSTILE,
                0.06f + (float) t * 0.05f,
                0.82f + client.world.random.nextFloat() * 0.12f,
                false
            );
            ClientFeelingCounters.audioTriggers++;
            return;
        }

        if (t < 1.35f) {
            client.world.playSound(
                pos.x, pos.y, pos.z,
                SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE.value(),
                SoundCategory.BLOCKS,
                0.07f + (float) t * 0.04f,
                0.55f + client.world.random.nextFloat() * 0.25f,
                false
            );
            return;
        }

        // High: thunder family (offset for "distant" feel) + ender drone
        if (t < 2.4f && (storm || roll < 0.5f)) {
            double ox = (client.world.random.nextDouble() - 0.5) * 80.0;
            double oz = (client.world.random.nextDouble() - 0.5) * 80.0;
            client.world.playSound(
                pos.x + ox, Math.max(8, pos.y + 24), pos.z + oz,
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.WEATHER,
                0.12f + (float) t * 0.06f,
                0.58f + client.world.random.nextFloat() * 0.1f,
                false
            );
            ClientFeelingCounters.audioTriggers++;
            return;
        }

        if (t < 2.4f) {
            client.world.playSound(
                pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_ENDERMAN_STARE,
                SoundCategory.HOSTILE,
                0.09f + (float) t * 0.05f,
                0.5f,
                false
            );
            ClientFeelingCounters.audioTriggers++;
            return;
        }

        client.world.playSound(
            pos.x, pos.y, pos.z,
            SoundEvents.ENTITY_WITHER_AMBIENT,
            SoundCategory.AMBIENT,
            0.16f + (float) t * 0.045f,
            0.48f + client.world.random.nextFloat() * 0.08f,
            false
        );
        ClientFeelingCounters.audioTriggers++;
    }
}
