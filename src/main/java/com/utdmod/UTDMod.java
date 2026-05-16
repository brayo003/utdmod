package com.utdmod;

import com.utdmod.command.ExperimentCommands;
import com.utdmod.core.TensionServerTick;
import com.utdmod.ecology.TensionSpawnEcology;
import com.utdmod.event.TensionEvents;
import com.utdmod.event.TensionMobHooks;
import com.utdmod.network.DebugSpikeTensionPacket;
import com.utdmod.network.ExperimentClientReportPacket;
import com.utdmod.network.UseCrystalPacket;
import com.utdmod.registry.ModBlockEntities;
import com.utdmod.registry.ModBlocks;
import com.utdmod.registry.ModEntityAttributes;
import com.utdmod.registry.ModItemGroups;
import com.utdmod.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UTDMod implements ModInitializer {
    public static final String MOD_ID = "utdmod";
    public static final Logger LOGGER = LoggerFactory.getLogger("UTDMod");

    @Override
    public void onInitialize() {
        System.out.println("[UTDMod] Starting initialization...");

        ModBlocks.registerModBlocks();
        ModBlockEntities.registerBlockEntities();
        ModItems.registerModItems();
        ModItemGroups.registerItemGroups();

        ModEntityAttributes.registerAllAttributes();

        System.out.println("[UTDMOD_DIAG] terminal: [GLOBAL_FLOW]/20t [REGION]/200t [TENSION_STATE] [EVENT_EFFECT] transitions [STORM] on trigger/end");

        TensionEvents.registerEvents();
        TensionMobHooks.register();

        ExperimentCommands.register();
        ExperimentClientReportPacket.registerServer();
        TensionSpawnEcology.register();

        UseCrystalPacket.registerReceiver();
        DebugSpikeTensionPacket.registerServerReceiver();

        TensionServerTick.register();

        System.out.println("[UTDMod] Mod initialized!");
    }
}
