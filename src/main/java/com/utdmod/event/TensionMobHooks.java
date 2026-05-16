package com.utdmod.event;

import com.utdmod.tension.ChunkTensionData;
import com.utdmod.tension.ChunkTensionManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.UUID;

/**
 * Light hostile pressure near strained / fractured chunks (attribute-only, no new AI).
 */
public final class TensionMobHooks {

    private static final UUID UTD_SPEED = UUID.fromString("6d2c3b1d-0b0a-4c2e-9f1a-000000000001");
    private static final UUID UTD_FOLLOW = UUID.fromString("6d2c3b1d-0b0a-4c2e-9f1a-000000000002");

    private TensionMobHooks() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((Entity entity, ServerWorld world) -> {
            if (world.isClient()) return;
            if (!(entity instanceof HostileEntity mob)) return;

            ChunkPos cp = new ChunkPos(mob.getBlockPos());
            ChunkTensionData.ChunkState st = ChunkTensionManager.getChunkState(world, cp);
            if (st != ChunkTensionData.ChunkState.STRAINED && st != ChunkTensionData.ChunkState.FRACTURED
                && st != ChunkTensionData.ChunkState.DECOUPLED) {
                return;
            }

            EntityAttributeInstance move = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            EntityAttributeInstance follow = mob.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
            double speedAdd = st == ChunkTensionData.ChunkState.FRACTURED || st == ChunkTensionData.ChunkState.DECOUPLED ? 0.06 : 0.03;
            double followAdd = st == ChunkTensionData.ChunkState.FRACTURED || st == ChunkTensionData.ChunkState.DECOUPLED ? 6.0 : 3.0;

            if (move != null && move.getModifier(UTD_SPEED) == null) {
                move.addTemporaryModifier(new EntityAttributeModifier(
                    UTD_SPEED,
                    "utd_tension_region_speed",
                    speedAdd,
                    EntityAttributeModifier.Operation.ADDITION
                ));
            }
            if (follow != null && follow.getModifier(UTD_FOLLOW) == null) {
                follow.addTemporaryModifier(new EntityAttributeModifier(
                    UTD_FOLLOW,
                    "utd_tension_region_follow",
                    followAdd,
                    EntityAttributeModifier.Operation.ADDITION
                ));
            }
        });
    }
}
