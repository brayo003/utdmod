"""
Lightweight config loader for the Nether Overhaul mod.
Handles worldgen flags, hazard multipliers, loot tiers.
"""

class NetherOverhaulConfig:
def init(self):
self.worldgen_enabled = True
self.hazard_multiplier = 2.0
self.loot_tier = 3

def load(self):
    return {
        "worldgen_enabled": self.worldgen_enabled,
        "hazard_multiplier": self.hazard_multiplier,
        "loot_tier": self.loot_tier
    }


