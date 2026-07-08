package com.utdmod.research;

import com.utdmod.research.player.ResearchPlayerController;
import net.minecraft.server.MinecraftServer;

/**
 * Scheduler entry point — starts/stops the deterministic research player controller.
 * No direct perturbation injection occurs here.
 */
public final class ResearchBotManager {

  private ResearchBotManager() {}

  public static void tick(MinecraftServer server, long tick) {
    ResearchPlayerController.tick(server, tick);
  }
}
