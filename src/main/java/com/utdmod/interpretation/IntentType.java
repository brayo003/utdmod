package com.utdmod.interpretation;

/**
 * Semantic gameplay intents emitted by the interpretation engine.
 * Each maps laboratory event taxonomy to Minecraft actuation categories.
 */
public enum IntentType {
  EXPLORE,
  WANDER,
  CONTINUE_MINING,
  ENGAGE_COMBAT,
  CREATE_SETTLEMENT,
  CREATE_ROAD,
  INITIATE_TRADE,
  EXPAND_TERRITORY,
  RECOVER,
  COLLAPSE
}
