package com.utdmod.ai;

import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import com.utdmod.signals.TensionManager;

import java.util.EnumSet;
import java.util.Random;

public class TensionEndermanBehaviorGoal extends Goal {
    private final EndermanEntity enderman;
    private final ServerWorld world;
    private final Random random = new Random();

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

        if (random.nextFloat() < tension * 0.05) {
            BlockPos targetPos = enderman.getBlockPos().add(random.nextInt(16) - 8, random.nextInt(8) - 4, random.nextInt(16) - 8);
            enderman.teleport(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        }
    }
}
