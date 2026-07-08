package com.utdmod.research.player;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Locale;

public final class ResearchPlayerInventoryManager {
  private final ServerPlayerEntity player;

  public ResearchPlayerInventoryManager(ServerPlayerEntity player) {
    this.player = player;
  }

  public int count(Item item) {
    int total = 0;
    for (int i = 0; i < player.getInventory().size(); i++) {
      ItemStack stack = player.getInventory().getStack(i);
      if (stack.isOf(item)) {
        total += stack.getCount();
      }
    }
    return total;
  }

  public int countLogs() {
    int total = 0;
    for (int i = 0; i < player.getInventory().size(); i++) {
      ItemStack stack = player.getInventory().getStack(i);
      if (stack.isEmpty()) {
        continue;
      }
      String id = Registries.ITEM.getId(stack.getItem()).getPath();
      if (id.endsWith("_log")) {
        total += stack.getCount();
      }
    }
    return total;
  }

  public boolean has(Item item, int amount) {
    return count(item) >= amount;
  }

  public boolean hasPickaxe() {
    return has(Items.WOODEN_PICKAXE, 1) || has(Items.STONE_PICKAXE, 1) || has(Items.IRON_PICKAXE, 1);
  }

  public boolean hasWoodenPickaxe() {
    return has(Items.WOODEN_PICKAXE, 1);
  }

  public boolean hasStonePickaxe() {
    return has(Items.STONE_PICKAXE, 1);
  }

  public void selectBestTool(Item preferred) {
    for (int i = 0; i < 9; i++) {
      ItemStack stack = player.getInventory().getStack(i);
      if (stack.isOf(preferred)) {
        player.getInventory().selectedSlot = i;
        return;
      }
    }
    for (int i = 0; i < 9; i++) {
      ItemStack stack = player.getInventory().getStack(i);
      if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.MiningToolItem) {
        player.getInventory().selectedSlot = i;
        return;
      }
    }
  }

  public void selectWeapon() {
    for (int i = 0; i < 9; i++) {
      ItemStack stack = player.getInventory().getStack(i);
      if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.SwordItem) {
        player.getInventory().selectedSlot = i;
        return;
      }
    }
  }

  public String equippedToolName() {
    ItemStack stack = player.getMainHandStack();
    if (stack.isEmpty()) {
      return "empty";
    }
    return Registries.ITEM.getId(stack.getItem()).getPath();
  }

  public String summary() {
    return String.format(Locale.ROOT,
        "logs=%d planks=%d cobble=%d coal=%d iron=%d",
        countLogs(), count(Items.OAK_PLANKS), count(Items.COBBLESTONE),
        count(Items.COAL), count(Items.IRON_INGOT));
  }

  public boolean consume(Item item, int amount) {
    int remaining = amount;
    for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
      ItemStack stack = player.getInventory().getStack(i);
      if (!stack.isOf(item)) {
        continue;
      }
      int take = Math.min(stack.getCount(), remaining);
      stack.decrement(take);
      remaining -= take;
    }
    return remaining <= 0;
  }

  public void give(Item item, int count) {
    player.getInventory().insertStack(new ItemStack(item, count));
  }
}
