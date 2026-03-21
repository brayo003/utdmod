package com.utdmod.tension;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores local tension data per chunk, persisted across world saves.
 */
public class ChunkTensionData extends PersistentState {
    public enum ChunkState {
        STABLE,
        STRAINED,
        FRACTURED,
        DECOUPLED
    }

    // Tension thresholds for state transitions
    private static final double T1 = 0.8;
    private static final double T2 = 1.5;
    private static final double T3 = 3.0;

    public static final PersistentState.Type<ChunkTensionData> TYPE = new PersistentState.Type<>(
        ChunkTensionData::new,
        ChunkTensionData::fromNbt,
        null // DataFixer, null for now
    );

    // Map of ChunkPos to local tension value
    private final Map<ChunkPos, Double> localTensions = new HashMap<>();
    // Map of ChunkPos to chunk state
    private final Map<ChunkPos, ChunkState> chunkStates = new HashMap<>();
    // Map of ChunkPos to recent mining activity (decays over time)
    private final Map<ChunkPos, Double> recentMiningRate = new HashMap<>();
    // Map of ChunkPos to storm active state
    private final Map<ChunkPos, Boolean> stormActive = new HashMap<>();

    public ChunkTensionData() {}

    public static ChunkTensionData getServerState(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(TYPE, "chunk_tension_data");
    }

    public Map<ChunkPos, Double> getTensionMap() {
        return new HashMap<>(localTensions);
    }

    public double getLocalTension(ChunkPos pos) {
        return localTensions.getOrDefault(pos, 0.0);
    }

    public double getRecentMiningRate(ChunkPos pos) {
        return recentMiningRate.getOrDefault(pos, 0.0);
    }

    public void setRecentMiningRate(ChunkPos pos, double rate) {
        if (rate <= 0.0) {
            recentMiningRate.remove(pos);
        } else {
            recentMiningRate.put(pos, rate);
        }
        markDirty();
    }

    public boolean isStormActive(ChunkPos pos) {
        return stormActive.getOrDefault(pos, false);
    }

    public void setStormActive(ChunkPos pos, boolean active) {
        if (!active) {
            stormActive.remove(pos);
        } else {
            stormActive.put(pos, true);
        }
        markDirty();
    }

    public ChunkState getChunkState(ChunkPos pos) {
        return chunkStates.getOrDefault(pos, ChunkState.STABLE);
    }

    public void addMiningTension(ChunkPos pos, double baseAmount, ServerWorld world) {
        // Increment recent mining rate
        double currentRate = recentMiningRate.getOrDefault(pos, 0.0);
        recentMiningRate.put(pos, currentRate + 1.0);
        
        // Calculate multiplier based on recent activity
        double multiplier = 1.0 + (currentRate * 0.1); // 10% increase per recent mining action
        
        // Apply tension with multiplier
        addLocalTension(pos, baseAmount * multiplier, world);
    }

    public void setLocalTension(ChunkPos pos, double tension) {
        setLocalTension(pos, tension, null);
    }

    public void setLocalTension(ChunkPos pos, double tension, ServerWorld world) {
        double oldTension = getLocalTension(pos);
        ChunkState oldState = getChunkState(pos);

        if (tension <= 0.0) {
            localTensions.remove(pos);
            chunkStates.remove(pos);
            stormActive.remove(pos);
        } else {
            localTensions.put(pos, tension);
            ChunkState newState = calculateState(tension);
            if (newState != oldState) {
                chunkStates.put(pos, newState);
                // Log state transition
                System.out.printf("[UTDMod] Chunk %d,%d state transition: %s -> %s (tension: %.2f -> %.2f)%n",
                    pos.x, pos.z, oldState, newState, oldTension, tension);

                // Trigger effects for state transitions
                if (world != null) {
                    triggerStateTransitionEffects(world, pos, oldState, newState);
                }
            }
        }
        markDirty();
    }

    public void addLocalTension(ChunkPos pos, double amount) {
        addLocalTension(pos, amount, null);
    }

    public void addLocalTension(ChunkPos pos, double amount, ServerWorld world) {
        double current = getLocalTension(pos);
        setLocalTension(pos, current + amount, world);
    }

    private void triggerStateTransitionEffects(ServerWorld world, ChunkPos pos, ChunkState oldState, ChunkState newState) {
        if (newState == ChunkState.FRACTURED && oldState != ChunkState.DECOUPLED) {
            // Spawn smoke particles in the chunk when it becomes FRACTURED
            for (int i = 0; i < 5; i++) {
                double x = (pos.x << 4) + world.random.nextDouble() * 16;
                double z = (pos.z << 4) + world.random.nextDouble() * 16;
                double y = world.getTopY() + 10; // Above ground level
                // Note: In a real implementation, you'd send particle packets to clients
                // For now, just log
                System.out.printf("[UTDMod] FRACTURED effect: smoke at %.1f, %.1f, %.1f%n", x, y, z);
            }
        }
    }

    private ChunkState calculateState(double tension) {
        if (tension > T3) return ChunkState.DECOUPLED;
        if (tension > T2) return ChunkState.FRACTURED;
        if (tension > T1) return ChunkState.STRAINED;
        return ChunkState.STABLE;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList tensionList = new NbtList();
        for (Map.Entry<ChunkPos, Double> entry : localTensions.entrySet()) {
            NbtCompound chunkNbt = new NbtCompound();
            chunkNbt.putInt("x", entry.getKey().x);
            chunkNbt.putInt("z", entry.getKey().z);
            chunkNbt.putDouble("tension", entry.getValue());
            chunkNbt.putInt("state", getChunkState(entry.getKey()).ordinal());
            chunkNbt.putDouble("miningRate", recentMiningRate.getOrDefault(entry.getKey(), 0.0));
            chunkNbt.putBoolean("stormActive", isStormActive(entry.getKey()));
            tensionList.add(chunkNbt);
        }
        nbt.put("localTensions", tensionList);
        return nbt;
    }

    public static ChunkTensionData fromNbt(NbtCompound nbt) {
        ChunkTensionData data = new ChunkTensionData();
        NbtList tensionList = nbt.getList("localTensions", 10); // 10 = NbtCompound type
        for (int i = 0; i < tensionList.size(); i++) {
            NbtCompound chunkNbt = tensionList.getCompound(i);
            int x = chunkNbt.getInt("x");
            int z = chunkNbt.getInt("z");
            double tension = chunkNbt.getDouble("tension");
            int stateOrdinal = chunkNbt.getInt("state");
            double miningRate = chunkNbt.getDouble("miningRate");
            boolean storm = chunkNbt.getBoolean("stormActive");
            ChunkPos pos = new ChunkPos(x, z);
            data.localTensions.put(pos, tension);
            data.chunkStates.put(pos, ChunkState.values()[stateOrdinal]);
            if (miningRate > 0) {
                data.recentMiningRate.put(pos, miningRate);
            }
            if (storm) {
                data.stormActive.put(pos, true);
            }
        }
        return data;
    }
}
