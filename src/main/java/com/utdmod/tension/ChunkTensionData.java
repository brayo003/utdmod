package com.utdmod.tension;

import com.utdmod.core.TensionManager;
import com.utdmod.core.TensionTraceClock;
import com.utdmod.core.TensionTraceLogger;
import com.utdmod.experiment.ExperimentTelemetry;
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

    // Tension thresholds for state transitions (aligned with DiagnosticLogs approximate ladder)
    private static final double T1 = 0.95;
    /** FRACTURED: sustained regional mining (~2–4 min hotspot). */
    private static final double T2 = 1.28;
    private static final double T3 = 3.0;
    /** Downgrade hysteresis so states persist briefly as tension falls (spatial memory). */
    private static final double STATE_HYSTERESIS = 0.22;

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
    // Contamination memory (slow recovery); persisted per chunk
    private final Map<ChunkPos, Double> contaminationPeak = new HashMap<>();
    /** 0 none, 1 medium (>=1.15), 2 heavy (>=1.35) */
    private final Map<ChunkPos, Byte> contaminationLevel = new HashMap<>();
    // Map of ChunkPos to last access timestamp (ms) for TTL-based pruning
    private final Map<ChunkPos, Long> lastAccessMs = new HashMap<>();

    public static ChunkTensionData getServerState(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(TYPE, "chunk_tension_data");
    }

    public Map<ChunkPos, Double> getTensionMap() {
        return new HashMap<>(localTensions);
    }

    public double getLocalTension(ChunkPos pos) {
        touch(pos);
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
        touch(pos);
        markDirty();
    }

    public boolean isStormActive(ChunkPos pos) {
        touch(pos);
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
        touch(pos);
        return chunkStates.getOrDefault(pos, ChunkState.STABLE);
    }

    public void addMiningTension(ChunkPos pos, double baseAmount, ServerWorld world) {
        // Increment recent mining rate
        double currentRate = recentMiningRate.getOrDefault(pos, 0.0);
        recentMiningRate.put(pos, currentRate + 1.0);

        double multiplier = 1.0 + (currentRate * 0.12);
        double applied = baseAmount * multiplier;
        com.utdmod.core.TensionActivityLedger.addMining(applied);

        addLocalTension(pos, applied, world);
        touch(pos);
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
            contaminationPeak.remove(pos);
            contaminationLevel.remove(pos);
        } else {
            localTensions.put(pos, tension);
            updateContaminationMemory(pos, tension);
            ChunkState newState = calculateState(tension, oldState);
            if (newState != oldState) {
                chunkStates.put(pos, newState);
                long wtick = world != null ? world.getTime() : TensionTraceClock.getServerTick();
                com.utdmod.diag.DiagnosticLogs.tensionState(
                    pos,
                    oldState,
                    newState,
                    tension,
                    wtick,
                    "STATE_HYSTERESIS=" + STATE_HYSTERESIS
                );

                if (world != null) {
                    ExperimentTelemetry.onChunkStateTransition(pos, oldState, newState, tension, wtick);
                }

                // Trigger effects for state transitions
                if (world != null) {
                    triggerStateTransitionEffects(world, pos, oldState, newState);
                }
            }
        }
        touch(pos);
        markDirty();
    }

    public void addLocalTension(ChunkPos pos, double amount) {
        addLocalTension(pos, amount, null);
    }

    public void addLocalTension(ChunkPos pos, double amount, ServerWorld world) {
        double current = getLocalTension(pos);
        setLocalTension(pos, current + amount, world);
        touch(pos);
    }

    /**
     * Multiplier applied to linear decay term (smaller = slower recovery) when contamination memory active.
     */
    public double getContaminationDecayMultiplier(ChunkPos pos) {
        byte lv = contaminationLevel.getOrDefault(pos, (byte) 0);
        return lv >= 2 ? 0.38 : lv >= 1 ? 0.68 : 1.0;
    }

    public double getContaminationPeak(ChunkPos pos) {
        return contaminationPeak.getOrDefault(pos, 0.0);
    }

    public int getContaminationLevel(ChunkPos pos) {
        return (int) contaminationLevel.getOrDefault(pos, (byte) 0);
    }

    public void tickContaminationMemory(ChunkPos pos, double tension, long worldTick) {
        if (tension > 0.56) return;
        int slot = Math.floorMod(pos.x + pos.z * 31, 200);
        if (worldTick % 200 != slot) return;
        Byte lv = contaminationLevel.get(pos);
        if (lv == null || lv <= 0) return;
        if (tension < 0.42) {
            contaminationLevel.put(pos, (byte) (lv - 1));
            markDirty();
        }
    }

    private void updateContaminationMemory(ChunkPos pos, double tension) {
        if (tension <= 0.0) return;
        contaminationPeak.merge(pos, tension, Math::max);
        byte lv = contaminationLevel.getOrDefault(pos, (byte) 0);
        if (tension >= 1.35) {
            contaminationLevel.put(pos, (byte) 2);
        } else if (tension >= 1.15) {
            contaminationLevel.put(pos, (byte) Math.max(1, lv));
        }
    }

    /**
     * Record access to a chunk for TTL-based pruning.
     */
    private void touch(ChunkPos pos) {
        if (pos == null) return;
        lastAccessMs.put(pos, System.currentTimeMillis());
    }

    /**
     * Prune entries not touched within the given timeout (ms).
     * Returns number of removed chunk entries.
     */
    public int pruneInactive(long timeoutMs) {
        if (timeoutMs <= 0) return 0;
        long now = System.currentTimeMillis();
        int removed = 0;
        java.util.List<ChunkPos> toRemove = new java.util.ArrayList<>();
        for (java.util.Map.Entry<ChunkPos, Long> e : lastAccessMs.entrySet()) {
            ChunkPos pos = e.getKey();
            long age = now - e.getValue();
            double t = getLocalTension(pos);
            int cLv = getContaminationLevel(pos);
            long effectiveTimeout = timeoutMs;
            if (t >= 0.85) {
                effectiveTimeout = Math.max(effectiveTimeout, 4L * 60L * 60L * 1000L);
            }
            if (cLv >= 2) {
                effectiveTimeout = Math.max(effectiveTimeout, 6L * 60L * 60L * 1000L);
            } else if (cLv >= 1) {
                effectiveTimeout = Math.max(effectiveTimeout, 5L * 60L * 60L * 1000L);
            }
            if (age > effectiveTimeout) {
                toRemove.add(pos);
            }
        }
        for (ChunkPos pos : toRemove) {
            localTensions.remove(pos);
            chunkStates.remove(pos);
            recentMiningRate.remove(pos);
            stormActive.remove(pos);
            lastAccessMs.remove(pos);
            contaminationPeak.remove(pos);
            contaminationLevel.remove(pos);
            removed++;
        }
        if (removed > 0) {
            markDirty();
            TensionTraceLogger.traceSystem(
                TensionTraceClock.getServerTick(),
                "CHUNK_PRUNE",
                "removed=" + removed + " timeoutMs=" + timeoutMs,
                TensionManager.getTension(),
                "ttl_prune"
            );
        }
        return removed;
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

    private ChunkState calculateState(double tension, ChunkState current) {
        if (tension <= 0) return ChunkState.STABLE;
        if (tension >= T3) return ChunkState.DECOUPLED;
        if (current == ChunkState.DECOUPLED && tension >= T3 - STATE_HYSTERESIS) {
            return ChunkState.DECOUPLED;
        }
        if (tension >= T2) return ChunkState.FRACTURED;
        if (current == ChunkState.FRACTURED && tension >= T2 - STATE_HYSTERESIS) {
            return ChunkState.FRACTURED;
        }
        if (tension >= T1) return ChunkState.STRAINED;
        if (current == ChunkState.STRAINED && tension >= T1 - STATE_HYSTERESIS) {
            return ChunkState.STRAINED;
        }
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
            chunkNbt.putDouble("cPeak", contaminationPeak.getOrDefault(entry.getKey(), 0.0));
            chunkNbt.putByte("cLv", contaminationLevel.getOrDefault(entry.getKey(), (byte) 0));
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
            if (chunkNbt.contains("cPeak")) {
                data.contaminationPeak.put(pos, chunkNbt.getDouble("cPeak"));
            }
            if (chunkNbt.contains("cLv")) {
                data.contaminationLevel.put(pos, chunkNbt.getByte("cLv"));
            }
        }
        return data;
    }
}
