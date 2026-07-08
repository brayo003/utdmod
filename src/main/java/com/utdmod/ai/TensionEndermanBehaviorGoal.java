package com.utdmod.ai;

import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import com.utdmod.core.TensionManager;

import java.util.EnumSet;

public class TensionEndermanBehaviorGoal extends Goal {
    private final EndermanEntity enderman;
    private final ServerWorld world;

    public TensionEndermanBehaviorGoal(EndermanEntity enderman, ServerWorld world) {
        this.enderman = enderman;
        this.world = world;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.TARGET));
    }

    @Override
    public boolean canStart() {
        return true;
    }

    @Override
    public void tick() {
        double tension = TensionManager.getTension();
        long worldTime = world.getTime();
        float chance = deterministicFloat(enderman.getBlockPos(), worldTime, 0);

        if (chance < tension * 0.05) {
            double dx = deterministicFloat(enderman.getBlockPos(), worldTime, 1) * 16.0 - 8.0;
            double dy = deterministicFloat(enderman.getBlockPos(), worldTime, 2) * 8.0 - 4.0;
            double dz = deterministicFloat(enderman.getBlockPos(), worldTime, 3) * 16.0 - 8.0;
            BlockPos targetPos = enderman.getBlockPos().add((int) Math.round(dx), (int) Math.round(dy), (int) Math.round(dz));
            enderman.teleport(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        }
    }

    private static float deterministicFloat(BlockPos pos, long tick, int salt) {
        double value = Math.sin((pos.getX() * 31L + pos.getY() * 17L + pos.getZ() * 13L + tick * 7L + salt * 97L) * 0.6180339887498948) * 43758.5453123;
        return (float) (value - Math.floor(value));
    }
}
