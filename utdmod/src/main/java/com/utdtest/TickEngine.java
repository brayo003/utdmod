package com.utdtest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class TickEngine implements ModInitializer {
    private double x = 0.01;
    private final double r = 0.153267;
    private final double a = 1.0;
    private final double b = 1.0;
    private final double sigma = 0.35;
    private final double dt = 0.05;
    private final Random rng = new Random(42);
    private int tick = 0;
    private FileWriter log;

    @Override
    public void onInitialize() {
        try {
            log = new FileWriter("utd_server_trace.csv");
            log.write("tick,x\n");
        } catch (Exception e) {}

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (tick >= 2000) return;
            double det = r * x + a * (x * x) - b * (x * x * x);
            x = x + dt * det + sigma * Math.sqrt(dt) * rng.nextGaussian();
            try {
                log.write(tick + "," + x + "\n");
            } catch (IOException e) {}
            tick += 1;
        });
    }
}
