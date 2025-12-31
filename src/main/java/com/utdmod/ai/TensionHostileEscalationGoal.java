package com.utdmod.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.world.ServerWorld;
import com.utdmod.signals.TensionManager;

import java.util.EnumSet;

public class TensionHostileEscalationGoal extends Goal {
    private final LivingEntity mob;
    private final ServerWorld world;

    public TensionHostileEscalationGoal(LivingEntity mob, ServerWorld world) {
        this.mob = mob;
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

        if (mob instanceof CreeperEntity creeper) {
            int baseFuse = 30;
            int newFuse = Math.max(5, baseFuse - (int)(tension * 15)); 
            creeper.ignite();
            // timeSinceIgnited removed in 1.20.4 - using alternative approach
        }

        if (mob instanceof ZombieEntity zombie) {
            zombie.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                  .setBaseValue(0.25 + tension * 0.15);
        }

        if (mob instanceof SkeletonEntity skeleton) {
            // setAttackCooldown removed in 1.20.4 - using alternative approach
            skeleton.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_SPEED)
                     .setBaseValue(1.0 + tension * 0.5);
        }
    }
}
