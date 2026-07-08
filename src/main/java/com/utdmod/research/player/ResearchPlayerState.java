package com.utdmod.research.player;

import java.util.Locale;

public enum ResearchPlayerState {
  IDLE,
  INITIALIZE,
  SURVEY,
  GATHER_WOOD,
  CRAFT_WOODEN_TOOLS,
  GATHER_STONE,
  CRAFT_STONE_TOOLS,
  BUILD_SHELTER,
  SLEEP,
  EXPLORE,
  MINE_COAL,
  MINE_IRON,
  SMELT,
  RETURN_HOME,
  STORE_ITEMS,
  COMBAT,
  RECOVER,
  RESUME_PREVIOUS_TASK;

  public static ResearchPlayerState parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    for (ResearchPlayerState state : values()) {
      if (state.name().equals(normalized)) {
        return state;
      }
    }
    return null;
  }
}
