public class DebugLagTest {
    public static void tick() {
        long start = System.nanoTime();
        
        // Test 1: SmallSignal update
        SmallSignal.update();
        long afterSignal = System.nanoTime();
        
        // Test 2: PlayerState update  
        PlayerStateTension.update();
        long afterPlayer = System.nanoTime();
        
        // Test 3: Biome effects
        BiomeTensionEffects.update();
        long afterBiome = System.nanoTime();
        
        // Log times
        System.out.println("SmallSignal: " + (afterSignal-start)/1000000.0 + "ms");
        System.out.println("PlayerState: " + (afterPlayer-afterSignal)/1000000.0 + "ms");
        System.out.println("BiomeEffects: " + (afterBiome-afterPlayer)/1000000.0 + "ms");
    }
}
