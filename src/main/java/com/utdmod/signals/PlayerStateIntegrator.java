package com.utdmod.signals;

import com.utdmod.core.TensionTraceClock;
import com.utdmod.tick.TensionLogger;
import com.utdmod.tension.ChunkTensionManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Long-session temporal accumulation and light per-player coupling into chunk tension.
 */
public final class PlayerStateIntegrator {

    private static final Map<UUID, Integer> sessionTicks = new HashMap<>();

    private PlayerStateIntegrator() {}

    public static void integrate() {
        // Reserved for batch hooks; per-player work uses {@link #onServerPlayerTick}.
    }

    public static void onServerPlayerTick(ServerPlayerEntity player) {
        if (player.getServer() == null) return;
        UUID id = player.getUuid();
        int ticks = sessionTicks.merge(id, 1, Integer::sum);

        // After ~1 h in-world (72000 ticks), slowly leak fatigue into local tension.
        if (ticks > 72_000 && ticks % 1200 == 0) {
            int hours = ticks / 72_000;
            double leak = 0.00015 * hours;
            ChunkPos pos = new ChunkPos(player.getBlockPos());
            double before = ChunkTensionManager.getLocalTension(player.getServerWorld(), pos);
            ChunkTensionManager.addLocalTension(player.getServerWorld(), pos, leak);
            double after = ChunkTensionManager.getLocalTension(player.getServerWorld(), pos);
            TensionLogger.log(
                TensionTraceClock.getServerTick(),
                "MOVEMENT",
                "session_fatigue_leak",
                leak,
                before,
                after,
                pos,
                player,
                player.getServerWorld()
            );
        }
    }
}
