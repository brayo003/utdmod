package com.utdmod.ecology;

import com.utdmod.core.RegionDiagnosticsManager;
import com.utdmod.diag.DiagnosticLogs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Contextual spawn weighting via spawn-cycle thread context + sparse extra hostiles (no global registry edits).
 */
public final class TensionSpawnEcology {

    private static final ThreadLocal<Deque<Context>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private static long lastEcologyLogTick = Long.MIN_VALUE;
    private static int extraSpawnBudget;

    private TensionSpawnEcology() {}

    private record Context(SpawnGroup group, ServerWorld world, ChunkPos chunk) {}

    public static void push(SpawnGroup group, ServerWorld world, ChunkPos chunk) {
        STACK.get().push(new Context(group, world, chunk));
    }

    public static void pop() {
        Deque<Context> d = STACK.get();
        if (!d.isEmpty()) {
            d.pop();
        }
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(TensionSpawnEcology::onEntityLoad);
        ServerTickEvents.END_WORLD_TICK.register(TensionSpawnEcology::onWorldTickEnd);
    }

    private static void onEntityLoad(Entity entity, ServerWorld world) {
        if (world.isClient()) return;
        Deque<Context> d = STACK.get();
        Context ctx = d.peek();
        if (ctx == null || ctx.world() != world) return;
        if (!(entity instanceof MobEntity mob)) return;

        ChunkPos cp = new ChunkPos(mob.getBlockPos());
        RegionDiagnosticsManager.RegionSnapshot snap = RegionDiagnosticsManager.get(cp.x, cp.z);
        if (snap == null) return;

        RegionDiagnosticsManager.EcoState eco = snap.ecoState();
        double passiveMod = 1.0;
        double hostileMod = 1.0;
        boolean cullPassive = false;
        boolean night = world.isNight();

        switch (eco) {
            case STRAINED -> {
                passiveMod = 0.65;
                hostileMod = night ? 1.55 : 1.40;
                cullPassive = isPassiveSpawn(ctx.group()) && world.random.nextFloat() > passiveMod;
            }
            case FRACTURED -> {
                passiveMod = 0.30;
                hostileMod = night ? 2.45 : 2.20;
                cullPassive = isPassiveSpawn(ctx.group()) && world.random.nextFloat() > passiveMod;
            }
            default -> {
            }
        }

        if (cullPassive && mob instanceof PassiveEntity) {
            mob.discard();
            maybeLogEcology(snap, eco, passiveMod, hostileMod, world.getTime());
            return;
        }

        if (eco == RegionDiagnosticsManager.EcoState.FRACTURED && ctx.group() == SpawnGroup.MONSTER && mob instanceof HostileEntity) {
            if (world.random.nextFloat() < 0.08f && (mob.getType() == EntityType.ENDERMAN || mob.getType() == EntityType.WITCH)) {
                mob.setVelocity(mob.getVelocity().multiply(1.15));
            }
        }
    }

    private static boolean isPassiveSpawn(SpawnGroup g) {
        return g == SpawnGroup.CREATURE || g == SpawnGroup.WATER_CREATURE || g == SpawnGroup.UNDERGROUND_WATER_CREATURE
            || g == SpawnGroup.AXOLOTLS || g == SpawnGroup.AMBIENT;
    }

    private static void maybeLogEcology(
        RegionDiagnosticsManager.RegionSnapshot snap,
        RegionDiagnosticsManager.EcoState eco,
        double passiveMod,
        double hostileMod,
        long tick
    ) {
        if (tick - lastEcologyLogTick < 200) return;
        lastEcologyLogTick = tick;
        DiagnosticLogs.spawnEcology(snap.regionX, snap.regionZ, eco.name(), passiveMod, hostileMod);
    }

    private static void onWorldTickEnd(ServerWorld world) {
        if (world.isClient() || world.getRegistryKey() != World.OVERWORLD) return;
        if (world.getTime() % 400 == 0L) {
            extraSpawnBudget = Math.max(0, extraSpawnBudget - 1);
        }
        if (world.getTime() % 220 != 0L) return;
        if (extraSpawnBudget > 6) return;
        for (var p : world.getPlayers()) {
            ChunkPos cp = new ChunkPos(p.getBlockPos());
            RegionDiagnosticsManager.RegionSnapshot snap = RegionDiagnosticsManager.get(cp.x, cp.z);
            if (snap == null || snap.ecoState() != RegionDiagnosticsManager.EcoState.FRACTURED) continue;
            if (world.random.nextFloat() > 0.18f) continue;
            spawnExtraPack(world, p.getBlockPos(), world.random);
            extraSpawnBudget++;
            DiagnosticLogs.spawnEcology(snap.regionX, snap.regionZ, "FRACTURED", 0.30, 2.20);
            return;
        }
    }

    private static void spawnExtraPack(ServerWorld world, BlockPos near, Random random) {
        int ox = random.nextInt(49) - 24;
        int oz = random.nextInt(49) - 24;
        int x = near.getX() + ox;
        int z = near.getZ() + oz;
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        EntityType<?> type = random.nextFloat() < 0.35f ? EntityType.WITCH : random.nextBoolean() ? EntityType.ENDERMAN : EntityType.ZOMBIE;
        Entity e = type.spawn(world, pos, net.minecraft.entity.SpawnReason.EVENT);
        if (e != null) {
            e.refreshPositionAfterTeleport(x + 0.5, y, z + 0.5);
        }
    }
}
