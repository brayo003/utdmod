package com.utdmod.event;

import com.utdmod.UTDMod;
import com.utdmod.core.TensionManager;
import com.utdmod.event.LivingEntityDamageEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TensionEvents {
    // Tension values (configurable) - Updated to match specification
    private static final double BLOCK_BREAK_TENSION = 0.0008;
    private static final double COMBAT_DAMAGE_MULTIPLIER = 0.002;
    private static final double EXPLOSION_TENSION = 0.02;
    private static final double DEEP_MINING_TENSION = 0.0015;
    private static final double NETHER_PORTAL_TENSION = 0.05;
    private static final double SLEEP_REDUCTION = -0.02;
    private static final double BULK_BUILD_TENSION = 0.01;
    private static final double ENCHANTING_TENSION = 0.01;
    private static final double BOSS_KILL_TENSION = 0.1;
    private static final double SPRINTING_TENSION = 0.0007;
    
    // Track player actions for bulk operations
    private static int blocksPlacedRecently = 0;
    private static long lastPlacementTime = 0;
    private static final long PLACEMENT_WINDOW_MS = 5000; // 5 seconds
    private static final int BULK_PLACEMENT_THRESHOLD = 30;

    public static void registerEvents() {
        // 1. Block Breaking (Controlled Extraction)
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!world.isClient()) {
                double tension = getBlockBreakTension(world.getBlockState(pos));
                addLocalTension(world, pos, tension);
            }
            return ActionResult.PASS;
        });

        // 2. Combat Energy (Violence Injection)
        LivingEntityDamageEvents.AFTER_DAMAGE.register((entity, source, amount, result) -> {
            if (source.getAttacker() instanceof PlayerEntity player && !entity.getWorld().isClient) {
                addLocalTension(entity.getWorld(), player.getBlockPos(), COMBAT_DAMAGE_MULTIPLIER * amount);
            }
        });

        // 3. TNT / Explosions (High Shock) - TODO: Implement via mixin or alternative event
        // ExplosionEvents.DETONATE.register((world, explosion, affectedBlocks, affectedEntities) -> {
        //     if (!world.isClient()) {
        //         addLocalTension(world, BlockPos.ofFloored(explosion.getPosition()), EXPLOSION_TENSION);
        //     }
        // });

        // 4. Nether Portal Activation
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient && hitResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                BlockPos pos = hitResult.getBlockPos();
                BlockState state = world.getBlockState(pos);
                ItemStack stack = player.getStackInHand(hand);
                
                if (stack.isOf(net.minecraft.item.Items.FLINT_AND_STEEL) && state.isOf(Blocks.OBSIDIAN)) {
                    if (world.getBlockState(pos.up()).isOf(Blocks.OBSIDIAN) || 
                        world.getBlockState(pos.down()).isOf(Blocks.OBSIDIAN)) {
                        addGlobalTension(world, NETHER_PORTAL_TENSION);
                    }
                }
            }
            return ActionResult.PASS;
        });

        // 5. Sleeping (Relief Mechanic)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isSleeping()) {
                    addGlobalTension(player.getWorld(), SLEEP_REDUCTION);
                }
            }
        });

        // 6. Large Structure Building
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() && hitResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                long currentTime = System.currentTimeMillis();
                
                if (currentTime - lastPlacementTime > PLACEMENT_WINDOW_MS) {
                    blocksPlacedRecently = 0;
                }
                
                blocksPlacedRecently++;
                lastPlacementTime = currentTime;
                
                if (blocksPlacedRecently >= BULK_PLACEMENT_THRESHOLD) {
                    addLocalTension(world, ((BlockHitResult)hitResult).getBlockPos(), BULK_BUILD_TENSION);
                    blocksPlacedRecently = 0;
                }
            }
            return ActionResult.PASS;
        });

        // 7. Enchanting Table Usage
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() && hitResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                BlockPos pos = hitResult.getBlockPos();
                if (world.getBlockState(pos).isOf(Blocks.ENCHANTING_TABLE)) {
                    addLocalTension(world, pos, ENCHANTING_TENSION);
                }
            }
            return ActionResult.PASS;
        });

        // 8. Boss Kill Event - TODO: Use correct entity death event
        // ServerEntityEvents.ENTITY_DEATH.register((entity, world) -> {
        //     if (!world.isClient()) {
        //         if (entity instanceof WitherEntity || entity instanceof EnderDragonEntity) {
        //             addGlobalTension(world, BOSS_KILL_TENSION);
        //         }
        //     }
        // });

        // 9. Player Sprinting vs Walking
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isSprinting()) {
                    addLocalTension(player.getWorld(), player.getBlockPos(), SPRINTING_TENSION);
                }
            }
        });

        // 10. Deep Underground Mining
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long currentTime = server.getOverworld().getTime();
            if (currentTime % 200 == 0) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    if (player.getY() < 20) {
                        addLocalTension(player.getWorld(), player.getBlockPos(), DEEP_MINING_TENSION);
                    }
                }
            }
        });
    }

    private static double getBlockBreakTension(BlockState state) {
        // Scaling: Stone +0.0006, Ore +0.002, Ancient debris +0.005
        if (state.isIn(BlockTags.GOLD_ORES) || state.isIn(BlockTags.IRON_ORES) || 
            state.isIn(BlockTags.DIAMOND_ORES) || state.isIn(BlockTags.REDSTONE_ORES) ||
            state.isIn(BlockTags.LAPIS_ORES) || state.isIn(BlockTags.EMERALD_ORES) ||
            state.isIn(BlockTags.COPPER_ORES)) {
            return 0.002; // Ores
        } else if (state.isOf(Blocks.ANCIENT_DEBRIS)) {
            return 0.005; // Ancient debris
        } else if (state.isOf(Blocks.STONE) || state.isOf(Blocks.DEEPSLATE)) {
            return 0.0006; // Stone
        }
        return BLOCK_BREAK_TENSION; // 0.0008 default
    }

    private static void addLocalTension(World world, BlockPos pos, double amount) {
        if (world.isClient()) return;
        TensionManager.addEvent(amount);
        if (UTDMod.LOGGER.isDebugEnabled()) {
            UTDMod.LOGGER.debug("Local tension +{} at {}", amount, pos);
        }
    }

    private static void addGlobalTension(World world, double amount) {
        if (world.isClient()) return;
        TensionManager.addEvent(amount);
        UTDMod.LOGGER.info("Global tension {}", amount > 0 ? "+" + amount : amount);
    }
}
