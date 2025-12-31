package com.utdmod.ai;

import com.utdmod.entity.TensionWraithEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class WraithAvoidancePenaltyGoal extends Goal {
    private final TensionWraithEntity mob;

    public WraithAvoidancePenaltyGoal(TensionWraithEntity mob) {
        this.mob = mob;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        return mob.getNavigation().isFollowingPath();
    }

    @Override
    public void tick() {
        Vec3d currentPathTarget = Vec3d.ofCenter(mob.getNavigation().getTargetPos());
        
        EntityAttributeInstance speedAttribute = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttribute != null) {
            float baseSpeed = (float)speedAttribute.getBaseValue();
            mob.setMovementSpeed(baseSpeed * 0.8f); // Slight slowdown penalty
            
            if (mob.age % 20 == 0) {
                System.out.println("[AI LOG] Wraith: Movement penalty applied");
            }
        }
    }

    @Override
    public void stop() {
        EntityAttributeInstance speedAttribute = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttribute != null) {
            float baseSpeed = (float)speedAttribute.getBaseValue();
            mob.setMovementSpeed(baseSpeed);
        }
    }
}
