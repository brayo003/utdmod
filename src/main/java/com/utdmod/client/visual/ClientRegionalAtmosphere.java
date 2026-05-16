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
        if (client.world.random.nextInt(blend > 1.25 ? 14 : 38) != 0) return;
        var ppos = client.player.getPos();
        double ox = (client.world.random.nextDouble() - 0.5) * 10.0;
        double oz = (client.world.random.nextDouble() - 0.5) * 10.0;
        if (blend > 1.25) {
            client.world.addParticle(
                ParticleTypes.END_ROD,
                ppos.x + ox,
                client.player.getY() + client.world.random.nextDouble() * 2.0,
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
