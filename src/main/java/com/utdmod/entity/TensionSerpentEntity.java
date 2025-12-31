package com.utdmod.entity;

import com.utdmod.signals.TensionManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.entity.SpawnReason;
import java.util.Random;

public class TensionSerpentEntity extends MobEntity {

    private static final float CRITICAL_TENSION_THRESHOLD = 0.90f;
    private static final float SPAWN_PROBABILITY_BASE = 0.00005f;
    private static final float MAX_HEALTH_DAMAGE_PERCENT = 0.70f;
    private static final int ATTACK_DEBUFF_DURATION = 120;
    private static double highTensionStartTime = 0.0;
    private static final double SPAWN_DISTANCE_MIN = 16.0;
    private static final double SPAWN_DISTANCE_MAX = 32.0;

    public TensionSerpentEntity(EntityType<? extends MobEntity> type, World world) {
        super(type, world);
    }

    public static void trackHighTensionDuration(float tension, String playerName) {
        if (tension >= CRITICAL_TENSION_THRESHOLD && highTensionStartTime == 0.0) {
            highTensionStartTime = System.currentTimeMillis();
        } else if (tension < CRITICAL_TENSION_THRESHOLD && highTensionStartTime > 0.0) {
            double duration = System.currentTimeMillis() - highTensionStartTime;
            System.out.println("[HIGH TENSION] Duration: " + duration + "ms for player " + playerName);
            highTensionStartTime = 0.0;
        }
    }

    public static boolean canSpawn(EntityType<TensionSerpentEntity> type, ServerWorld world, SpawnReason reason, BlockPos pos, Random random) {
        PlayerEntity nearestPlayer = world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 64.0, false);
        if (!(nearestPlayer instanceof ServerPlayerEntity serverPlayer)) return false;
        
        double tension = TensionManager.getTension();
        if (tension < CRITICAL_TENSION_THRESHOLD) return false;
        if (!world.getBiome(pos).isIn(BiomeTags.IS_OCEAN)) return false;
        
        float spawnChance = SPAWN_PROBABILITY_BASE * ((float)tension - CRITICAL_TENSION_THRESHOLD) * 10000;
        boolean canSpawn = random.nextFloat() < spawnChance;
        
        if (canSpawn) {
            System.out.println("[SERPENT SPAWN] Tension Serpent spawned at tension " + tension + " for player " + serverPlayer.getName().getString());
        }
        
        return canSpawn;
    }

    @Override
    public boolean isInvisible() {
        return true; 
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient()) return;

        PlayerEntity target = getWorld().getClosestPlayer(this, 16.0);
        if (target != null && target instanceof ServerPlayerEntity serverPlayer) {
            double tension = TensionManager.getTension();
            if (tension < 0.85 || target.isDead() || !target.isSubmergedIn(net.minecraft.registry.tag.FluidTags.WATER)) {
                this.discard();
                return;
            }

            if (this.distanceTo(target) < 2.5) {
                DamageSource tearDamage = getWorld().getDamageSources().create(DamageTypes.MAGIC, this, this);
                float lethalDamage = target.getMaxHealth() * MAX_HEALTH_DAMAGE_PERCENT;
                if (target.damage(tearDamage, lethalDamage)) {
                    System.out.println("[SERPENT ATTACK] Player hit! Health reduced by 70%.");
                }
                target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.WITHER, ATTACK_DEBUFF_DURATION, 1));
                this.discard();
            }
        } else if (age > 600) {
            this.discard();
        }
    }
}
