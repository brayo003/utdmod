package com.utdmod.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class TensionWraithEntity extends HostileEntity {

    private Vec3d lastAvoidancePoint = null;
    private int avoidanceCooldownTicks = 0;
    private static final int PENALTY_DURATION = 200;
    private boolean hasLoggedSpawnDistance = false;

    public TensionWraithEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createTensionWraithAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.add(3, new WanderAroundFarGoal(this, 1.0D, 0.0F));
        this.goalSelector.add(4, new LookAroundGoal(this));

        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    public void logAvoidance(PlayerEntity player) {
        if (this.isAttacking() && this.getTarget() == player) {
            this.lastAvoidancePoint = player.getPos();
            this.avoidanceCooldownTicks = PENALTY_DURATION;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.avoidanceCooldownTicks > 0) {
            this.avoidanceCooldownTicks--;
        }
        
        // Spawn tracking removed to prevent lag
    }
}
