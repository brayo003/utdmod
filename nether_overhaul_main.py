"""
Entry point. Wires Nether Overhaul into the Minecraft modloader.
"""

from nether_overhaul_runtime import NetherOverhaulRuntime

runtime = NetherOverhaulRuntime()

def on_biome_load(biome):
return runtime.apply_environment_modifiers(biome)

def on_entity_spawn(entity):
return runtime.apply_entity_modifiers(entity)

def on_loot_generate(loot):
return runtime.scale_loot(loot)
