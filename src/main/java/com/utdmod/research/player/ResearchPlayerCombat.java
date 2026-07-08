package com.utdmod.research.player;

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

  public boolean tickCombat(ResearchPlayerInventoryManager inventory, ResearchPlayerNavigation navigation) {
    HostileEntity hostile = new ResearchPlayerVision(world, player).findNearestHostile();
    if (hostile == null) {
      return true;
    }
    inventory.selectWeapon();
    navigation.setTarget(hostile.getBlockPos());
    navigation.face(hostile.getBlockPos());
    if (player.squaredDistanceTo(hostile) > 2.5) {
      navigation.tick();
      return false;
    }
    player.attack(hostile);
    player.swingHand(Hand.MAIN_HAND);
    if (player.getHealth() < 8.0f) {
      navigation.stop();
      return true;
    }
    return !hostile.isAlive();
  }
}
