package com.utdmod.signals;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BiomeResponder {
public static void respond(World world, BlockPos pos) {
// Example placeholder: fetch biome at position
// Replace with actual biome detection code from Minecraft world
BiomeCategories biome = BiomeCategories.PLAINS;

    switch (biome) {
        case DESERT, NETHER, THEEND -> System.out.println("High tension biome detected");
        case PLAINS, FOREST, SWAMP, TAIGA -> System.out.println("Low tension biome detected");
    }
}


}
