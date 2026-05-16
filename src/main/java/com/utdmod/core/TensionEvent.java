package com.utdmod.core;

import net.minecraft.util.math.ChunkPos;

public class TensionEvent {

    public final long tick;
    public final String player;
    public final String source;
    public final String detail;
    public final double delta;
    public final double before;
    public final double after;
    public final ChunkPos chunk;

    public TensionEvent(
        long tick,
        String player,
        String source,
        String detail,
        double delta,
        double before,
        double after,
        ChunkPos chunk
    ) {
        this.tick = tick;
        this.player = player;
        this.source = source;
        this.detail = detail;
        this.delta = delta;
        this.before = before;
        this.after = after;
        this.chunk = chunk;
    }
}
