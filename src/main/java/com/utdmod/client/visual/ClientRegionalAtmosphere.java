package com.utdmod.client.visual;

import com.utdmod.client.ClientFeelingCounters;
import com.utdmod.client.TensionSyncState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;

/**
 * Sparse regional atmosphere (particles) driven by synced regional tension proxies.
 */
public final class ClientRegionalAtmosphere {

    private ClientRegionalAtmosphere() {}

    public static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null) return;
        double reg = Math.max(TensionSyncState.CLIENT_REGION_MAX, TensionSyncState.CLIENT_REGION_AVG);
        double p = TensionSyncState.perceivedTension();
        double blend = Math.max(reg * 0.85, p);
        if (blend < 0.72) return;
        long tick = client.world.getTime();
        int index = (int) (tick % 4);
        var ppos = client.player.getPos();
        double offset = 1.5 + (blend - 0.72) * 3.5;
        double ox = ((index * 2) - 3) * offset;
        double oz = ((index % 2) * 2 - 1) * offset;

        if (blend > 1.25) {
            client.world.addParticle(
                ParticleTypes.END_ROD,
                ppos.x + ox,
                client.player.getY() + 1.0 + (index * 0.25),
                ppos.z + oz,
                0.0, 0.02, 0.0
            );
        } else {
            client.world.addParticle(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                ppos.x + ox,
                client.player.getY() + 0.2,
                ppos.z + oz,
                0.0, 0.015, 0.0
            );
        }
    }
}
