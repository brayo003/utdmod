package com.utdmod.event;

import com.utdmod.core.TensionActivityLedger;
import com.utdmod.core.TensionManager;
import com.utdmod.core.TensionTraceClock;
import com.utdmod.tick.TensionLogger;
import com.utdmod.tension.ChunkTensionManager;
import com.utdmod.UTDMod;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public final class TensionEvents {

    private static final double NETHER_PORTAL_TENSION = 0.05;
    private static final double SLEEP_LOCAL_RELIEF = 0.02;
    private static final double BULK_BUILD_TENSION = 0.01;
    private static final double ENCHANTING_TENSION = 0.01;
    private static final double DEEP_MINING_TENSION = 0.0015;

    private static int blocksPlacedRecently = 0;
    private static long lastPlacementTime = 0;
    private static final long PLACEMENT_WINDOW_MS = 5000;
    private static final int BULK_PLACEMENT_THRESHOLD = 30;

    private TensionEvents() {}

    public static void registerEvents() {
        LivingEntityDamageEvents.initialize();

        LivingEntityDamageEvents.AFTER_DAMAGE.register((LivingEntity entity, net.minecraft.entity.damage.DamageSource source, float amount, net.minecraft.util.ActionResult result) -> {
            if (!(entity instanceof ServerPlayerEntity sp)) return;
            if (entity.getWorld().isClient() || !(entity.getWorld() instanceof ServerWorld sw)) return;
            ChunkPos cp = new ChunkPos(entity.getBlockPos());
            double loc = ChunkTensionManager.getLocalTension(sw, cp);
            String src = source.getType().msgId();
            TensionLogger.log(
                TensionTraceClock.getServerTick(),
                "COMBAT",
                "player_damage:" + src + ":amt=" + amount,
                0.0,
                loc,
                loc,
                cp,
                sp,
                sw
            );
        });

        PlayerBlockBreakEvents.AFTER.register((World world, PlayerEntity player, BlockPos pos, BlockState state, net.minecraft.block.entity.BlockEntity blockEntity) -> {
            if (world.isClient() || !(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) {
                return;
            }
            String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
            ChunkPos cp = new ChunkPos(pos);
            double g0 = TensionManager.getTension();
            double before = ChunkTensionManager.getLocalTension(sw, cp);
            ChunkTensionManager.addMiningTension(sw, cp, blockId);
            double after = ChunkTensionManager.getLocalTension(sw, cp);
            double g1 = TensionManager.getTension();
            String action = state.isIn(BlockTags.LOGS) ? "LOG_BREAK" : "BLOCK_BREAK";
            TensionLogger.traceWithGlobals(
                TensionTraceClock.getServerTick(),
                "MINING",
                action,
                blockId,
                cp,
                sp,
                sw,
                after - before,
                after,
                g0,
                g1
            );
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity.getWorld().isClient() || !(entity.getWorld() instanceof ServerWorld sw)) return;

            if (entity instanceof ServerPlayerEntity sp) {
                ChunkPos cp = new ChunkPos(sp.getBlockPos());
                double loc = ChunkTensionManager.getLocalTension(sw, cp);
                TensionLogger.log(
                    TensionTraceClock.getServerTick(),
                    "COMBAT",
                    "player_death:" + damageSource.getType().msgId(),
                    0.0,
                    loc,
                    loc,
                    cp,
                    sp,
                    sw
                );
                return;
            }

            if (!(damageSource.getAttacker() instanceof ServerPlayerEntity player)) return;
            ChunkPos cp = new ChunkPos(entity.getBlockPos());
            String typeId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            double g0 = TensionManager.getTension();
            double before = ChunkTensionManager.getLocalTension(sw, cp);
            ChunkTensionManager.addSlaughterTension(sw, cp, typeId);
            double after = ChunkTensionManager.getLocalTension(sw, cp);
            double g1 = TensionManager.getTension();
            String killKind = typeId.contains("villager") ? "villager_kill" : "mob_kill";
            if (typeId.contains("cow") || typeId.contains("pig") || typeId.contains("sheep")
                || typeId.contains("chicken") || typeId.contains("rabbit")) {
                killKind = "passive_mob_kill";
            }
            TensionLogger.traceWithGlobals(
                TensionTraceClock.getServerTick(),
                "COMBAT",
                killKind,
                typeId,
                cp,
                player,
                sw,
                after - before,
                after,
                g0,
                g1
            );
        });

        EntitySleepEvents.ALLOW_SLEEPING.register((player, sleepingPos) -> {
            if (player.getWorld().isClient() || !(player instanceof ServerPlayerEntity sp)) {
                return null;
            }
            ServerWorld sw = sp.getServerWorld();
            ChunkPos cp = new ChunkPos(sleepingPos);
            double effective = Math.max(TensionManager.getTension(), ChunkTensionManager.getLocalTension(sw, cp));
            if (effective > 1.12) {
                return PlayerEntity.SleepFailureReason.OTHER_PROBLEM;
            }
            return null;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() && world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp
                && hitResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                BlockPos pos = hitResult.getBlockPos();
                BlockState state = world.getBlockState(pos);
                ItemStack stack = player.getStackInHand(hand);

                if (stack.isOf(net.minecraft.item.Items.FLINT_AND_STEEL) && state.isOf(Blocks.OBSIDIAN)) {
                    if (world.getBlockState(pos.up()).isOf(Blocks.OBSIDIAN)
                        || world.getBlockState(pos.down()).isOf(Blocks.OBSIDIAN)) {
                        addLocal(sw, pos, NETHER_PORTAL_TENSION, sp, "MOVEMENT", "nether_portal_ignite");
                    }
                }
            }
            return ActionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.isSleeping() && player.getWorld() instanceof ServerWorld sw) {
                    reduceLocal(sw, player.getBlockPos(), SLEEP_LOCAL_RELIEF, player, "MOVEMENT", "sleep_relief");
                }
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() && world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp
                && hitResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                long currentTime = System.currentTimeMillis();

                if (currentTime - lastPlacementTime > PLACEMENT_WINDOW_MS) {
                    blocksPlacedRecently = 0;
                }

                blocksPlacedRecently++;
                lastPlacementTime = currentTime;

                if (blocksPlacedRecently >= BULK_PLACEMENT_THRESHOLD) {
                    addLocal(sw, ((BlockHitResult) hitResult).getBlockPos(), BULK_BUILD_TENSION, sp, "MINING", "bulk_build_placement");
                    blocksPlacedRecently = 0;
                }
            }
            return ActionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClient() && world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp
                && hitResult.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                BlockPos pos = hitResult.getBlockPos();
                if (world.getBlockState(pos).isOf(Blocks.ENCHANTING_TABLE)) {
                    addLocal(sw, pos, ENCHANTING_TENSION, sp, "MINING", "enchanting_table_use");
                }
            }
            return ActionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long currentTime = server.getOverworld().getTime();
            if (currentTime % 200 != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getY() < 20 && player.getWorld() instanceof ServerWorld sw) {
                    addLocal(sw, player.getBlockPos(), DEEP_MINING_TENSION, player, "MINING", "deep_layer_y<20");
                }
            }
        });
    }

    private static void addLocal(ServerWorld sw, BlockPos pos, double amount, ServerPlayerEntity player, String source, String detail) {
        ChunkPos chunkPos = new ChunkPos(pos);
        double g0 = TensionManager.getTension();
        double before = ChunkTensionManager.getLocalTension(sw, chunkPos);
        ChunkTensionManager.addLocalTension(sw, chunkPos, amount);
        double after = ChunkTensionManager.getLocalTension(sw, chunkPos);
        double g1 = TensionManager.getTension();
        if (UTDMod.LOGGER.isDebugEnabled()) {
            UTDMod.LOGGER.debug("{} +{} at {}", source, amount, pos);
        }
        if (amount > 0 && "MINING".equals(source) && detail != null
            && (detail.contains("enchant") || detail.contains("bulk") || detail.contains("deep_layer"))) {
            TensionActivityLedger.addMining(amount);
        }
        if (amount > 0 && "MOVEMENT".equals(source)) {
            TensionActivityLedger.addMovement(amount);
        }
        TensionLogger.traceWithGlobals(
            TensionTraceClock.getServerTick(),
            source,
            source,
            detail,
            chunkPos,
            player,
            sw,
            after - before,
            after,
            g0,
            g1
        );
    }

    private static void reduceLocal(ServerWorld sw, BlockPos pos, double amount, ServerPlayerEntity player, String source, String detail) {
        ChunkPos chunkPos = new ChunkPos(pos);
        double g0 = TensionManager.getTension();
        double before = ChunkTensionManager.getLocalTension(sw, chunkPos);
        ChunkTensionManager.reduceLocalTension(sw, chunkPos, amount);
        double after = ChunkTensionManager.getLocalTension(sw, chunkPos);
        double g1 = TensionManager.getTension();
        double localDelta = after - before;
        TensionLogger.traceWithGlobals(
            TensionTraceClock.getServerTick(),
            source,
            source,
            detail,
            chunkPos,
            player,
            sw,
            localDelta,
            after,
            g0,
            g1
        );
    }
}
