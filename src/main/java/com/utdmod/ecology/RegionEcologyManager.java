package com.utdmod.ecology;

import com.utdmod.core.Region;
import com.utdmod.core.RegionManager;
import com.utdmod.core.RegionState;
import com.utdmod.core.TensionServerTick;
import com.utdmod.diag.DiagnosticLogs;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight visual ecological succession driven by region state.
 * This is intentionally decorative and constrained to active-region chunks only.
 */
public final class RegionEcologyManager {
    private static final int REGION_STEP = 4;
    private static final int CHUNKS_PER_REGION = 2;
    private static final int BLOCKS_PER_CHUNK = 2;
    private static final int MAX_STRESS = 20;

    private static final Map<Integer, Integer> stressByRegion = new HashMap<>();
    private static final Map<Integer, RegionHistory> historyByRegion = new HashMap<>();
    private static final Map<Integer, RegionState> previousStateByRegion = new HashMap<>();

    private RegionEcologyManager() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RegionEcologyManager::onEndServerTick);
    }

    public static void recordMiningHistory(ServerWorld world, BlockPos pos, boolean treeCut) {
        Region region = RegionManager.getRegionForChunk(world, new ChunkPos(pos));
        if (region == null) {
            return;
        }
        RegionHistory history = historyByRegion.computeIfAbsent(region.getId(), ignored -> new RegionHistory());
        if (treeCut) {
            history.recordTreeCut();
        } else {
            history.recordBlocksMined();
        }
    }

    private static void onEndServerTick(MinecraftServer server) {
        long tick = TensionServerTick.getTickCounter();
        if (tick % REGION_STEP != 0) {
            return;
        }

        List<Region> regions = RegionManager.getRegions();
        if (regions.isEmpty()) {
            return;
        }

        int index = (int) (tick / REGION_STEP % Math.max(1, regions.size()));
        Region region = regions.get(index);
        if (region == null) {
            return;
        }

        ServerWorld world = server.getWorld(region.getWorldKey());
        if (world == null) {
            return;
        }

        int decorationsApplied = 0;
        int recoveriesApplied = 0;
        RegionState previousState = previousStateByRegion.get(region.getId());
        int currentStress = stressByRegion.getOrDefault(region.getId(), 0);
        boolean coolingTransition = false;
        boolean recoveringTransition = false;
        boolean healedTransition = false;

        List<ChunkPos> selectedChunks = selectChunks(region, CHUNKS_PER_REGION, world.random.nextLong());
        for (ChunkPos chunkPos : selectedChunks) {
            if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                continue;
            }
            for (int i = 0; i < BLOCKS_PER_CHUNK; i++) {
                BlockPos surfacePos = findSurfacePos(world, chunkPos);
                if (surfacePos == null) {
                    continue;
                }
                RegionState state = region.getState();
                RegionHistory history = historyByRegion.computeIfAbsent(region.getId(), ignored -> new RegionHistory());
                if (history.getAge() < region.getAge()) {
                    history.setAge(region.getAge());
                }
                history.advanceAge();

                EcologyPolicy policy = selectPolicy(state, history, region.getAverageTension(), currentStress);
                if (policy == EcologyPolicy.RECOVERY && currentStress > 0) {
                    recoveriesApplied += applyRecovery(world, surfacePos);
                    currentStress = Math.max(0, currentStress - 1);
                    recoveringTransition = true;
                } else {
                    decorationsApplied += applyPolicyAction(world, surfacePos, policy, history);
                }

                if (state == RegionState.STRAINED) {
                    currentStress = Math.min(MAX_STRESS, currentStress + 1);
                } else if (state == RegionState.FRACTURED) {
                    currentStress = Math.min(MAX_STRESS, currentStress + 2);
                } else if (state == RegionState.ANCIENT) {
                    currentStress = Math.min(MAX_STRESS, currentStress + 1);
                } else if (state == RegionState.GROWING || state == RegionState.DORMANT) {
                    currentStress = Math.max(0, currentStress - 1);
                }
            }
        }

        RegionState state = region.getState();
        if (previousState != null && !previousState.equals(state)) {
            if (previousState == RegionState.STRAINED && state == RegionState.GROWING) {
                coolingTransition = true;
            } else if (previousState != RegionState.DORMANT && state == RegionState.DORMANT && currentStress <= 0) {
                healedTransition = true;
            }
        }
        if (coolingTransition) {
            DiagnosticLogs.regionCooling(region.getId(), state, region.getAverageTension(), region.getMaturity(), currentStress);
        } else if (recoveringTransition && recoveriesApplied > 0) {
            DiagnosticLogs.regionRecovering(region.getId(), state, region.getAverageTension(), region.getMaturity(), currentStress);
        } else if (healedTransition) {
            DiagnosticLogs.regionRehealed(region.getId(), state, region.getAverageTension(), region.getMaturity(), currentStress);
        }

        stressByRegion.put(region.getId(), Math.max(0, currentStress));
        previousStateByRegion.put(region.getId(), state);

        if (tick % 200 == 0) {
            DiagnosticLogs.ecologySummary(
                region.getId(),
                state,
                region.getAverageTension(),
                region.getMaturity(),
                coolingTransition ? "Cooling" : (recoveriesApplied > 0 ? "Recovering" : "Steady"),
                currentStress,
                decorationsApplied,
                recoveriesApplied,
                region.getStormChunks() > 0
            );
        }
    }

    private static List<ChunkPos> selectChunks(Region region, int limit, long seed) {
        List<ChunkPos> all = new ArrayList<>(region.getChunks());
        if (all.size() <= limit) {
            return all;
        }
        List<ChunkPos> result = new ArrayList<>(limit);
        int[] order = new int[all.size()];
        for (int i = 0; i < all.size(); i++) {
            order[i] = i;
        }
        long state = seed;
        for (int i = all.size() - 1; i > 0; i--) {
            state = state * 1103515245L + 12345L;
            int j = (int) ((state >>> 16) % (i + 1));
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
        for (int i = 0; i < limit; i++) {
            result.add(all.get(order[i]));
        }
        return result;
    }

    private static BlockPos findSurfacePos(ServerWorld world, ChunkPos chunkPos) {
        int x = (chunkPos.x << 4) + world.random.nextInt(16);
        int z = (chunkPos.z << 4) + world.random.nextInt(16);
        int y = world.getTopY();
        while (y >= world.getBottomY()) {
            BlockPos candidate = new BlockPos(x, y, z);
            if (!world.getBlockState(candidate).isAir()) {
                return candidate;
            }
            y--;
        }
        return new BlockPos(x, world.getTopY(), z);
    }

    static EcologyPolicy selectPolicy(RegionState state, RegionHistory history, double averageTension, int currentStress) {
        if (state == RegionState.ANCIENT) {
            return EcologyPolicy.ANCIENT;
        }
        if (state == RegionState.FRACTURED) {
            return EcologyPolicy.FRACTURED;
        }
        if (state == RegionState.STRAINED) {
            if (currentStress >= 8 || history.getTreesCut() >= 28 || history.getBlocksMined() >= 90) {
                return EcologyPolicy.STRAINED;
            }
            return EcologyPolicy.RECOVERY;
        }
        if (state == RegionState.GROWING) {
            if (history.getTreesCut() >= 30 && averageTension >= 0.78) {
                return EcologyPolicy.STRAINED;
            }
            if (currentStress > 2 || history.getTreesCut() >= 55 || history.getBlocksMined() >= 140) {
                return EcologyPolicy.RECOVERY;
            }
            return EcologyPolicy.GROWING;
        }
        if (state == RegionState.DORMANT) {
            if (currentStress > 2 || history.getTreesCut() >= 16 || history.getBlocksMined() >= 80) {
                return EcologyPolicy.RECOVERY;
            }
            return EcologyPolicy.GROWING;
        }
        return EcologyPolicy.GROWING;
    }

    private static int applyPolicyAction(ServerWorld world, BlockPos surfacePos, EcologyPolicy policy, RegionHistory history) {
        switch (policy) {
            case GROWING -> {
                return applyGrowing(world, surfacePos, history);
            }
            case STRAINED -> {
                return applyStrained(world, surfacePos);
            }
            case FRACTURED -> {
                return applyFractured(world, surfacePos);
            }
            case ANCIENT -> {
                return applyAncient(world, surfacePos);
            }
            case RECOVERY -> {
                return applyRecovery(world, surfacePos);
            }
        }
        return 0;
    }

    private static int applyGrowing(ServerWorld world, BlockPos surfacePos, RegionHistory history) {
        BlockState surface = world.getBlockState(surfacePos);
        BlockState above = world.getBlockState(surfacePos.up());
        if (surface.isOf(Blocks.GRASS_BLOCK) && above.isAir()) {
            if (history.getTreesCut() >= 25 && world.random.nextInt(100) < 10) {
                world.setBlockState(surfacePos.up(), Blocks.OAK_SAPLING.getDefaultState());
            } else if (world.random.nextInt(100) < 18) {
                world.setBlockState(surfacePos.up(), Blocks.TALL_GRASS.getDefaultState());
            } else if (world.random.nextInt(100) < 10) {
                world.setBlockState(surfacePos.up(), Blocks.POPPY.getDefaultState());
            }
            return 1;
        }
        if (surface.isOf(Blocks.DIRT) || surface.isOf(Blocks.COARSE_DIRT)) {
            world.setBlockState(surfacePos, Blocks.GRASS_BLOCK.getDefaultState());
            return 1;
        }
        return 0;
    }

    private static int applyStrained(ServerWorld world, BlockPos surfacePos) {
        BlockState surface = world.getBlockState(surfacePos);
        int applied = 0;
        if (surface.isOf(Blocks.GRASS_BLOCK)) {
            world.setBlockState(surfacePos, Blocks.COARSE_DIRT.getDefaultState());
            applied++;
        }
        BlockState above = world.getBlockState(surfacePos.up());
        if (above.isOf(Blocks.TALL_GRASS) || above.isOf(Blocks.DANDELION) || above.isOf(Blocks.POPPY)) {
            world.setBlockState(surfacePos.up(), Blocks.AIR.getDefaultState());
            applied++;
        } else if (above.isAir() && world.random.nextInt(3) == 0) {
            world.setBlockState(surfacePos.up(), Blocks.DEAD_BUSH.getDefaultState());
            applied++;
        }
        return applied;
    }

    private static int applyFractured(ServerWorld world, BlockPos surfacePos) {
        int applied = 0;
        if (world.getBlockState(surfacePos).isOf(Blocks.GRASS_BLOCK) || world.getBlockState(surfacePos).isOf(Blocks.DIRT)) {
            world.setBlockState(surfacePos, Blocks.COARSE_DIRT.getDefaultState());
            applied++;
        }
        if (world.getBlockState(surfacePos.up()).isAir()) {
            world.setBlockState(surfacePos.up(), Blocks.DEAD_BUSH.getDefaultState());
            applied++;
        }
        if (world.random.nextInt(6) == 0) {
            world.setBlockState(surfacePos.east(), Blocks.COBBLESTONE.getDefaultState());
            applied++;
        }
        return applied;
    }

    private static int applyAncient(ServerWorld world, BlockPos surfacePos) {
        int applied = 0;
        if (world.random.nextInt(8) == 0 && world.getBlockState(surfacePos.up()).isAir()) {
            world.setBlockState(surfacePos.up(), Blocks.AMETHYST_CLUSTER.getDefaultState());
            applied++;
        }
        if (world.random.nextInt(10) == 0 && world.getBlockState(surfacePos).isOf(Blocks.COARSE_DIRT)) {
            world.setBlockState(surfacePos, Blocks.MOSSY_COBBLESTONE.getDefaultState());
            applied++;
        }
        if (world.random.nextInt(12) == 0 && world.getBlockState(surfacePos.up()).isAir()) {
            world.setBlockState(surfacePos.up(), Blocks.VINE.getDefaultState());
            applied++;
        }
        return applied;
    }

    private static int applyRecovery(ServerWorld world, BlockPos surfacePos) {
        int applied = 0;
        if (world.getBlockState(surfacePos).isOf(Blocks.COARSE_DIRT) || world.getBlockState(surfacePos).isOf(Blocks.DIRT)) {
            world.setBlockState(surfacePos, Blocks.GRASS_BLOCK.getDefaultState());
            applied++;
        }
        if (world.getBlockState(surfacePos.up()).isOf(Blocks.DEAD_BUSH)) {
            world.setBlockState(surfacePos.up(), Blocks.AIR.getDefaultState());
            applied++;
        }
        if (world.random.nextInt(6) == 0 && world.getBlockState(surfacePos.up()).isAir()) {
            world.setBlockState(surfacePos.up(), Blocks.TALL_GRASS.getDefaultState());
            applied++;
        } else if (world.random.nextInt(10) == 0 && world.getBlockState(surfacePos.up()).isAir()) {
            world.setBlockState(surfacePos.up(), Blocks.POPPY.getDefaultState());
            applied++;
        } else if (world.random.nextInt(18) == 0 && world.getBlockState(surfacePos.up()).isAir()) {
            world.setBlockState(surfacePos.up(), Blocks.OAK_SAPLING.getDefaultState());
            applied++;
        }
        return applied;
    }
}
