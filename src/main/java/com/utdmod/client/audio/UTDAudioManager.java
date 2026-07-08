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

        double t = Math.max(TensionSyncState.perceivedTension(),
            TensionSyncState.CLIENT_PRESENTATION_STRESS * 0.9 + TensionSyncState.CLIENT_REGION_AVG * 0.5);
        boolean storm = TensionSyncState.CLIENT_STORM || TensionSyncState.CLIENT_PRESENTATION_STRESS > 0.62;
        Vec3d pos = client.player.getPos();
        long worldTime = client.world.getTime();

        if (t < 0.32 && !storm) {
            return;
        }

        float baseChance = (float) Math.min(0.26, 0.03 + t * 0.055 + (storm ? 0.06f : 0f));
        if (deterministicFloat(worldTime, 0) > baseChance) {
            return;
        }

        float roll = deterministicFloat(worldTime, 1);
        float offsetX = deterministicFloat(worldTime, 2);
        float offsetZ = deterministicFloat(worldTime, 3);
        float pitchMod = deterministicFloat(worldTime, 4);
        float toneMod = deterministicFloat(worldTime, 5);

        if (TensionSyncState.CLIENT_PRESENTATION_RECOVERY > 0.55 && roll < 0.35f) {
            client.world.playSound(
                pos.x, pos.y, pos.z,
                SoundEvents.BLOCK_COMPOSTER_READY,
                SoundCategory.AMBIENT,
                0.05f + (float) TensionSyncState.CLIENT_PRESENTATION_RECOVERY * 0.08f,
                0.9f,
                false
            );
            ClientFeelingCounters.audioTriggers++;
            return;
        }

        if (t < 0.75f && roll < 0.55f) {
            double ox = (offsetX - 0.5) * 48.0;
            double oz = (offsetZ - 0.5) * 48.0;
            client.world.playSound(
                pos.x + ox, pos.y, pos.z + oz,
                SoundEvents.AMBIENT_CAVE.value(),
                SoundCategory.AMBIENT,
                0.1f + (float) t * 0.09f,
                0.72f + pitchMod * 0.08f,
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

        if (t < 1.35f && roll < 0.45f) {
            client.world.playSound(
                pos.x, pos.y, pos.z,
                SoundEvents.ENTITY_VEX_AMBIENT,
                SoundCategory.HOSTILE,
                0.06f + (float) t * 0.05f,
                0.82f + pitchMod * 0.12f,
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
                0.55f + toneMod * 0.25f,
                false
            );
            return;
        }

        if (t < 2.4f && (storm || roll < 0.5f)) {
            double ox = (offsetX - 0.5) * 80.0;
            double oz = (offsetZ - 0.5) * 80.0;
            client.world.playSound(
                pos.x + ox, Math.max(8, pos.y + 24), pos.z + oz,
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.WEATHER,
                0.12f + (float) t * 0.06f,
                0.58f + pitchMod * 0.1f,
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
            0.48f + toneMod * 0.08f,
            false
        );
        ClientFeelingCounters.audioTriggers++;
    }

    private static float deterministicFloat(long tick, int salt) {
        double value = Math.sin((tick + salt * 73L) * 0.6180339887498948) * 43758.5453123;
        return (float) (value - Math.floor(value));
    }
}
