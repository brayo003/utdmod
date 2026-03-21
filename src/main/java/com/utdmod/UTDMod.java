package com.utdmod;

import com.utdmod.core.TensionManager;
import com.utdmod.registry.ModItems;
import com.utdmod.tension.ChunkTensionData;
import com.utdmod.event.TensionEvents;
import com.utdmod.tension.ChunkTensionManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UTDMod implements ModInitializer {
    public static final String MOD_ID = "utdmod";
    public static final Logger LOGGER = LoggerFactory.getLogger("UTDMod");
    private static long tickCounter = 0;
    private static double lastPlayerX, lastPlayerY, lastPlayerZ;
    private static boolean firstTick = true;
    private static int stormCooldown = 0; // Lightning cooldown
    
    // Parameters for reaction-diffusion-saturation system
    private static final double D = 0.0025;      // Diffusion rate
    private static final double LAMBDA = 0.0035;  // Increased from 0.0020 - faster decay
    private static final double ALPHA = 0.035;    // Nonlinear growth
    private static final double BETA = 0.12;      // Increased from 0.06 - stronger cubic damping
    private static final double GAMMA = 0.004;    // Global feedback
    private static final double STORM_THRESHOLD = 0.45;
    private static final double STORM_END_THRESHOLD = 0.25;
    private static final double STORM_DRAIN_RATE = 0.002;  // Storm energy drain
    
    public static void triggerLocalStorm(ChunkPos chunkPos, ServerWorld world) {
        // Spawn 2-3 lightning bolts in the chunk
        for (int i = 0; i < 2 + world.random.nextInt(2); i++) {
            double x = (chunkPos.x << 4) + world.random.nextDouble() * 16;
            double z = (chunkPos.z << 4) + world.random.nextDouble() * 16;
            
            // Find surface level
            int y = world.getTopY();
            BlockPos pos = new BlockPos((int)x, y, (int)z);
            while (y > 0 && world.getBlockState(pos).isAir()) {
                y--;
                pos = pos.down();
            }
            y++; // Above the block
            
            LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
            if (lightning != null) {
                lightning.refreshPositionAfterTeleport(new Vec3d(x, y, z));
                world.spawnEntity(lightning);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Lightning spawned at {}, {}, {} in chunk {},{}", x, y, z, chunkPos.x, chunkPos.z);
                }
            }
        }
    }
    
    @Override
    public void onInitialize() {
        System.out.println("[UTDMod] Starting initialization...");

        // Register items via ModItems registry
        ModItems.registerModItems();
        
        // Register tension-related events
        TensionEvents.registerEvents();

        // Environmental corruption: Mining and deforestation
        // TODO: PlayerBlockBreakEvents not available in fabric-api 0.92.1 - update API or find alternative
        // PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
        //     if (!world.isClient) {
        //         ServerWorld serverWorld = (ServerWorld) world;
        //         ChunkPos chunkPos = new ChunkPos(pos);
        //         String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        //         
        //         // Check for log blocks (deforestation)
        //         if (blockId.contains("_log") || blockId.contains("_wood") || blockId.contains("_stem")) {
        //             ChunkTensionManager.addDeforestationTension(serverWorld, chunkPos);
        //         } else {
        //             // Regular mining tension
        //             ChunkTensionManager.addMiningTension(serverWorld, chunkPos, blockId);
        //         }
        //     }
        //     return true; // Allow the block break to proceed
        // });

        // Environmental corruption: Mob slaughter
        // TODO: ServerEntityEvents.ENTITY_DEATH not available - find correct event
        // ServerEntityEvents.ENTITY_DEATH.register((entity, damageSource) -> {
        //     // Only count deaths of living entities caused by players
        //     if (entity instanceof LivingEntity && damageSource.getAttacker() instanceof PlayerEntity) {
        //         String entityType = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
        //         TensionManager.addSlaughterTension(entityType);
        //     }
        // });

        // Unified server tick: movement → decay → log (guaranteed order)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            // 1. Movement signal - accumulate continuous inflow
            double totalContinuousInflow = 0.0;
            for (var player : server.getPlayerManager().getPlayerList()) {
                double currentX = player.getX();
                double currentY = player.getY();
                double currentZ = player.getZ();
                
                if (firstTick) {
                    // Initialize position on first tick - no movement calculation
                    lastPlayerX = currentX;
                    lastPlayerY = currentY;
                    lastPlayerZ = currentZ;
                    firstTick = false;
                    continue; // Skip movement calc on first tick entirely
                }
                
                double dx = currentX - lastPlayerX;
                double dy = currentY - lastPlayerY;
                double dz = currentZ - lastPlayerZ;
                double distSq = dx * dx + dy * dy + dz * dz;
                
                // Add continuous inflow for movement
                if (distSq > 0.01) { // Threshold for movement
                    totalContinuousInflow += 0.0006;
                }
                
                // Update last position for next tick (AFTER distance calculation)
                lastPlayerX = currentX;
                lastPlayerY = currentY;
                lastPlayerZ = currentZ;
            }
            TensionManager.setContinuousInflow(totalContinuousInflow);

            // 2. Full tension update
            TensionManager.tickTension();

            // 2.5. Local chunk tension processing (every 5 ticks to reduce load)
            if (tickCounter % 5 == 0) {
                for (var world : server.getWorlds()) {
                    if (world instanceof ServerWorld) {
                        ServerWorld sw = (ServerWorld) world;
                        ChunkTensionData chunkData = ChunkTensionData.getServerState(sw);
                        double globalTension = TensionManager.getTension();
                        
                        // Get all chunks with tension (copy to avoid concurrent modification)
                        var tensionChunks = new java.util.HashMap<>(chunkData.getTensionMap());
                        
                        for (var entry : tensionChunks.entrySet()) {
                            ChunkPos chunkPos = entry.getKey();
                            double localTension = entry.getValue();
                            ChunkTensionData.ChunkState state = chunkData.getChunkState(chunkPos);
                            
                            // Calculate neighbor average for diffusion
                            double neighborAvg = 0.0;
                            int neighborCount = 0;
                            ChunkPos[] neighbors = {
                                new ChunkPos(chunkPos.x + 1, chunkPos.z),
                                new ChunkPos(chunkPos.x - 1, chunkPos.z),
                                new ChunkPos(chunkPos.x, chunkPos.z + 1),
                                new ChunkPos(chunkPos.x, chunkPos.z - 1)
                            };
                            for (ChunkPos n : neighbors) {
                                double nTension = chunkData.getLocalTension(n);
                                if (nTension > 0) {
                                    neighborAvg += nTension;
                                    neighborCount++;
                                }
                            }
                            if (neighborCount > 0) {
                                neighborAvg /= neighborCount;
                            }
                            
                            // Check storm state
                            boolean stormActive = chunkData.isStormActive(chunkPos);
                            double effectiveAlpha = stormActive ? ALPHA * 1.8 : ALPHA;
                            
                            // Reaction-diffusion-saturation equation with storm drain
                            double diffusion = D * neighborAvg;
                            double decay = LAMBDA * localTension;
                            double nonlinear = effectiveAlpha * localTension * localTension;
                            double saturation = BETA * localTension * localTension * localTension;
                            double globalFeedback = GAMMA * globalTension;
                            
                            localTension += diffusion + nonlinear - saturation + globalFeedback;
                            if (state != ChunkTensionData.ChunkState.DECOUPLED) {
                                localTension -= decay;
                            }
                            
                            // Apply storm drain if storm is active
                            if (stormActive) {
                                localTension -= STORM_DRAIN_RATE;
                            }
                            
                            // Enforce bounds with a hard cap at 0.9 to prevent locking
                            localTension = Math.max(0, Math.min(localTension, 0.9));
                            
                            // Storm nucleation
                            if (localTension > STORM_THRESHOLD && !stormActive) {
                                chunkData.setStormActive(chunkPos, true);
                                triggerLocalStorm(chunkPos, sw);
                            }
                            if (localTension < STORM_END_THRESHOLD) {
                                chunkData.setStormActive(chunkPos, false);
                            }
                            
                            // Set the updated tension
                            chunkData.setLocalTension(chunkPos, localTension, sw);
                            
                            // Decay recent mining rate
                            double currentMiningRate = chunkData.getRecentMiningRate(chunkPos);
                            if (currentMiningRate > 0) {
                                double miningDecay = currentMiningRate * 0.01; // 1% decay per tick
                                chunkData.setRecentMiningRate(chunkPos, Math.max(0, currentMiningRate - miningDecay));
                            }
                        }
                    }
                }

                // Update global tension as average of all chunks
                double totalTension = 0.0;
                int chunkCount = 0;
                for (var world : server.getWorlds()) {
                    if (world instanceof ServerWorld) {
                        ServerWorld sw = (ServerWorld) world;
                        ChunkTensionData chunkData = ChunkTensionData.getServerState(sw);
                        for (double t : chunkData.getTensionMap().values()) {
                            totalTension += t;
                            chunkCount++;
                        }
                    }
                }
                if (chunkCount > 0) {
                    double averageTension = totalTension / chunkCount;
                    // Optional smoothing
                    double currentGlobal = TensionManager.getTension();
                    double smoothedGlobal = 0.95 * currentGlobal + 0.05 * averageTension;
                    TensionManager.setTension(smoothedGlobal);
                }
            }

            // 3. Storm effects and feedback
            if (TensionManager.isStormActive()) {
                // Decrease cooldown
                if (stormCooldown > 0) {
                    stormCooldown--;
                }
                
                // Lightning with cooldown - every 200 ticks (10 seconds)
                if (stormCooldown <= 0) {
                    for (var player : server.getPlayerManager().getPlayerList()) {
                        ServerWorld world = (ServerWorld) player.getWorld();
                        Vec3d pos = player.getPos();
                        
                        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
                        if (lightning != null) {
                            lightning.refreshPositionAfterTeleport(pos);
                            world.spawnEntity(lightning);
                        }
                    }
                    stormCooldown = 200; // Reset cooldown
                }
                
                // Hunger drain during storm
                if (tickCounter % 40 == 0) {
                    for (var player : server.getPlayerManager().getPlayerList()) {
                        player.getHungerManager().addExhaustion(0.5f);
                    }
                }
            } else {
                // Reset cooldown when storm ends
                stormCooldown = 0;
            }

            // 4. Player feedback every 40 ticks
            if (tickCounter % 40 == 0) {
                for (var player : server.getPlayerManager().getPlayerList()) {
                    ServerWorld world = (ServerWorld) player.getWorld();
                    ChunkPos chunkPos = new ChunkPos(player.getBlockPos());
                    double localTension = ChunkTensionManager.getLocalTension(world, chunkPos);
                    ChunkTensionData.ChunkState state = ChunkTensionManager.getChunkState(world, chunkPos);
                    double globalTension = TensionManager.getTension();
                    String corruptionLevel = TensionManager.getCorruptionLevelName();
                    String status = TensionManager.isStormActive() ? "⚡ STORM" : "Calm";
                    player.sendMessage(Text.literal(String.format("Chunk Tension: %.2f (%s) | Global: %.2f (%s) | %s", 
                        localTension, state, globalTension, corruptionLevel, status)), true);
                }
            }

            // 5. Minimal debug logging
            if (tickCounter % 40 == 0) { // Every 2 seconds instead of 30
                System.out.printf("[UTDMod] Global Tension: %.2f | Storm: %s%n",
                        TensionManager.getTension(), TensionManager.isStormActive());
            }
        });

        System.out.println("[UTDMod] Items registered successfully!");
        System.out.println("[UTDMod] Mod initialized!");
    }
}
