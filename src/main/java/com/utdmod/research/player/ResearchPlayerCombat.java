package com.utdmod.research.player;

import com.utdmod.research.agent.ResearchAgentController;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;

public final class ResearchPlayerCombat {
  private final ServerWorld world;
  private final ServerPlayerEntity player;

  public ResearchPlayerCombat(ServerWorld world, ServerPlayerEntity player) {
    this.world = world;
    this.player = player;
  }

  public boolean tickCombat(ResearchPlayerInventoryManager inventory, ResearchAgentController controller) {
    HostileEntity hostile = new ResearchPlayerVision(world, player).findNearestHostile();
    if (hostile == null) {
      return true;
    }
    inventory.selectWeapon();
    controller.setTarget(hostile.getBlockPos());
    controller.face(hostile.getBlockPos());
    if (player.squaredDistanceTo(hostile) > 2.5) {
      controller.tick();
      return false;
    }
    player.attack(hostile);
    player.swingHand(Hand.MAIN_HAND);
    if (player.getHealth() < 8.0f) {
      controller.stop();
      return true;
    }
    return !hostile.isAlive();
  }
}
