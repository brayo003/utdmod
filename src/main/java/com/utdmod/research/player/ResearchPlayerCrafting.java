package com.utdmod.research.player;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

/** Deterministic recipe application — consumes ingredients, produces outputs. No creative spawning. */
public final class ResearchPlayerCrafting {
  private final ServerPlayerEntity player;
  private final ResearchPlayerInventoryManager inventory;

  public ResearchPlayerCrafting(ServerPlayerEntity player) {
    this.player = player;
    this.inventory = new ResearchPlayerInventoryManager(player);
  }

  public boolean craftPlanks(int plankSets) {
    int logsNeeded = plankSets;
    if (inventory.countLogs() < logsNeeded) {
      return false;
    }
  // Consume any log type
    int remaining = logsNeeded;
    for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
      var stack = player.getInventory().getStack(i);
      if (stack.isEmpty()) continue;
      String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
      if (id.endsWith("_log")) {
        int take = Math.min(stack.getCount(), remaining);
        stack.decrement(take);
        remaining -= take;
      }
    }
    inventory.give(Items.OAK_PLANKS, plankSets * 4);
    return true;
  }

  public boolean craftSticks(int pairs) {
    if (!inventory.consume(Items.OAK_PLANKS, pairs * 2)) {
      return false;
    }
    inventory.give(Items.STICK, pairs * 4);
    return true;
  }

  public boolean craftCraftingTable() {
    if (!inventory.consume(Items.OAK_PLANKS, 4)) {
      return false;
    }
    inventory.give(Items.CRAFTING_TABLE, 1);
    return true;
  }

  public boolean craftWoodenPickaxe() {
    if (!inventory.consume(Items.OAK_PLANKS, 3) || !inventory.consume(Items.STICK, 2)) {
      return false;
    }
    inventory.give(Items.WOODEN_PICKAXE, 1);
    return true;
  }

  public boolean craftStonePickaxe() {
    if (!inventory.consume(Items.COBBLESTONE, 3) || !inventory.consume(Items.STICK, 2)) {
      return false;
    }
    inventory.give(Items.STONE_PICKAXE, 1);
    return true;
  }

  public boolean craftFurnace() {
    if (!inventory.consume(Items.COBBLESTONE, 8)) {
      return false;
    }
    inventory.give(Items.FURNACE, 1);
    return true;
  }

  public boolean craftTorches(int count) {
    int sets = (count + 3) / 4;
    if (!inventory.consume(Items.COAL, sets) || !inventory.consume(Items.STICK, sets)) {
      return false;
    }
    inventory.give(Items.TORCH, sets * 4);
    return true;
  }

  public boolean craftBed() {
    if (!inventory.consume(Items.OAK_PLANKS, 3) || !inventory.consume(Items.WHITE_WOOL, 3)) {
      // wool may not exist — use planks only fallback for deterministic test worlds
      if (!inventory.consume(Items.OAK_PLANKS, 6)) {
        return false;
      }
    }
    inventory.give(Items.RED_BED, 1);
    return true;
  }

  public boolean craftIronPickaxe() {
    if (!inventory.consume(Items.IRON_INGOT, 3) || !inventory.consume(Items.STICK, 2)) {
      return false;
    }
    inventory.give(Items.IRON_PICKAXE, 1);
    return true;
  }

  public boolean craftItem(Item result, int resultCount, Item... ingredients) {
    for (Item ingredient : ingredients) {
      if (!inventory.consume(ingredient, 1)) {
        return false;
      }
    }
    inventory.give(result, resultCount);
    return true;
  }
}
