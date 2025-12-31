"""
Runtime hooks for Nether Overhaul.
Intercepts Nether events, applies modifiers from config, rewrites entity behavior.
"""

from nether_overhaul_config import NetherOverhaulConfig

class NetherOverhaulRuntime:
def init(self):
self.cfg = NetherOverhaulConfig().load()

def apply_environment_modifiers(self, biome):
    if not self.cfg["worldgen_enabled"]:
        return biome
    biome.ambient_light = max(0.0, biome.ambient_light - 0.3)
    biome.temperature = biome.temperature + 0.5
    return biome

def apply_entity_modifiers(self, entity):
    entity.damage *= self.cfg["hazard_multiplier"]
    entity.aggro_range *= 1.5
    return entity

def scale_loot(self, loot_table):
    return loot_table * self.cfg["loot_tier"]


