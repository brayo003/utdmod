package com.utdmod;

import com.utdmod.command.ExperimentCommands;
import com.utdmod.command.ResearchBotCommands;
import com.utdmod.command.ResearchCommands;
import com.utdmod.core.TensionServerTick;
import com.utdmod.ecology.RegionEcologyManager;
import com.utdmod.ecology.TensionSpawnEcology;
import com.utdmod.event.TensionEvents;
import com.utdmod.event.TensionMobHooks;
import com.utdmod.experiment.ExperimentLogger;
import com.utdmod.field.FieldActuatorRegistry;
import com.utdmod.network.DebugSpikeTensionPacket;
import com.utdmod.network.ExperimentClientReportPacket;
import com.utdmod.network.ResearchAgentRegisterPacket;
import com.utdmod.network.ResearchAgentStatusPacket;
import com.utdmod.research.agent.ResearchClientAgentManager;
import com.utdmod.network.UseCrystalPacket;
import com.utdmod.registry.ModBlockEntities;
import com.utdmod.registry.ModBlocks;
import com.utdmod.registry.ModEntityAttributes;
import com.utdmod.registry.ModItemGroups;
import com.utdmod.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
        // Hostile pressure now driven by MobBehaviorActuator via interpreted ecological stress.
        // TensionMobHooks.register();

        ExperimentCommands.register();
        ResearchCommands.register();
        ResearchBotCommands.register();
        ExperimentClientReportPacket.registerServer();
        ResearchAgentRegisterPacket.registerServer();
        ResearchAgentStatusPacket.registerServer();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            ResearchClientAgentManager.onPlayerDisconnect(handler.player));
        // Independent ecology/supplemental spawn systems are disabled in the deterministic substrate validation path.
        // TensionSpawnEcology.register();
        // RegionEcologyManager.register();

        UseCrystalPacket.registerReceiver();
        DebugSpikeTensionPacket.registerServerReceiver();

        FieldActuatorRegistry.initialize();
        TensionServerTick.register();
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ExperimentLogger.logRunEnd(server, TensionServerTick.getTickCounter(), "server_stop"));

        System.out.println("[UTDMod] Mod initialized!");
    }
}
