package com.utdmod.research.experiment;

import com.utdmod.research.player.ResearchPlayerContext;
import com.utdmod.research.player.ResearchPlayerInventoryManager;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/**
 * Deterministic protocol library. Protocols read parameters from the injected
 * {@link ResearchPlayerContext#snapshot} and act through vanilla mechanics
 * via the context's navigation/mining/building/combat modules.
 */
public final class ResearchExperimentLibrary {

  private ResearchExperimentLibrary() {}

  public static ResearchExperiment create(String protocolId) {
    if (protocolId == null) {
      return new PassiveWanderExperiment();
    }
    return switch (protocolId) {
      case "PASSIVE_WANDER" -> new PassiveWanderExperiment();
      case "EXPLORE" -> new ExplorationExperiment();
      case "BUILD_SHELTER" -> new BuilderExperiment();
      case "MINE_COAL" -> new MiningExperiment(Items.COAL);
      case "MINE_IRON" -> new MiningExperiment(Items.IRON_INGOT);
      case "COMBAT" -> new CombatExperiment();
      case "FARMER" -> new FarmerExperiment();
      case "TRADE" -> new TradeExperiment();
      case "RESOURCE_TRANSPORT" -> new ResourceTransportExperiment();
      default -> new PassiveWanderExperiment();
    };
  }

  /** Utility: inventory count for a given item. */
  static int count(ResearchPlayerContext ctx, net.minecraft.item.Item item) {
    ResearchPlayerInventoryManager inv = ctx.inventory;
    if (inv == null) return 0;
    return inv.count(item);
  }

  static boolean withinRadiusChunks(BlockPos a, BlockPos b, int radiusChunks) {
    if (a == null || b == null) return true;
    int dx = (a.getX() >> 4) - (b.getX() >> 4);
    int dz = (a.getZ() >> 4) - (b.getZ() >> 4);
    return (dx * dx + dz * dz) <= (radiusChunks * radiusChunks);
  }
}

