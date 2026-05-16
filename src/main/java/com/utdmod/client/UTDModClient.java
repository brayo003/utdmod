package com.utdmod.client;

import com.utdmod.client.audio.UTDAudioManager;
import com.utdmod.client.ui.TensionHud;
import com.utdmod.client.visual.ClientRegionalAtmosphere;
import com.utdmod.network.ExperimentClientReportPacket;
import com.utdmod.network.TensionSyncPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.entity.mob.HostileEntity;

public class UTDModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TensionSyncPacket.registerReceiver();
        TensionHud.register();

        TensionKeybind.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            TensionKeybind.tick();
            TensionSyncState.tickSmoothing();
            ClientRegionalAtmosphere.tick(client);

            if (client.world != null && client.player != null && client.world.getTime() % 38 == 0L) {
                UTDAudioManager.updateAmbientSounds();
            }

            if (client.world != null && client.player != null && client.world.getTime() % 200 == 0L) {
                int hostiles = client.world.getEntitiesByClass(
                    HostileEntity.class,
                    client.player.getBoundingBox().expand(36.0),
                    e -> e.isAlive()
                ).size();
                ClientFeelingCounters.hostileNearLast = hostiles;
                if (client.getServer() != null) {
                    int audio = ClientFeelingCounters.audioTriggers;
                    ClientFeelingCounters.resetForReport();
                    ExperimentClientReportPacket.send(
                        client,
                        TensionSyncState.perceivedTension(),
                        audio,
                        ClientFeelingCounters.overlayIntensity,
                        hostiles
                    );
                }
            }
        });
    }
}
