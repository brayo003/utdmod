package com.utdmod.init;

import com.utdmod.storm.StormManager;
import com.utdmod.ritual.RitualHandler;
import com.utdmod.probe.ProbeHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

public class UTDModInitializer implements ModInitializer, ClientModInitializer {

private final StormManager stormManager = new StormManager();
private final RitualHandler ritualHandler = new RitualHandler();
private final ProbeHandler probeHandler = new ProbeHandler();

@Override
public void onInitialize() {
    System.out.println("UTD Mod initialized (server logic).");
    probeHandler.checkTension();
    ritualHandler.performRitual();
    stormManager.triggerStorm();
}

@Override
public void onInitializeClient() {
    System.out.println("UTD Mod client initialized.");
}


}
