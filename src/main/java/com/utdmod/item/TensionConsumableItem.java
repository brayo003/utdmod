package com.utdmod.item;

import com.utdmod.core.TensionManager;
import com.utdmod.core.TensionTraceClock;
import com.utdmod.tick.TensionLogger;
import com.utdmod.tension.ChunkTensionManager;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public class TensionConsumableItem extends Item {
    private final String itemId;

    public TensionConsumableItem(String id, FabricItemSettings settings) {
        super(settings);
        this.itemId = id;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);

        if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer && world instanceof ServerWorld sw) {
            ChunkPos cp = new ChunkPos(user.getBlockPos());
            double l0 = ChunkTensionManager.getLocalTension(sw, cp);

            switch (itemId) {
                case "volatile_insight_potion":
                    serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 600, 0));
                    double g0a = TensionManager.getTension();
                    TensionManager.setTension(TensionManager.getTension() + 0.05);
                    double g1a = TensionManager.getTension();
                    double l1a = ChunkTensionManager.getLocalTension(sw, cp);
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5F, 1.0F);
                    TensionLogger.traceWithGlobals(
                        TensionTraceClock.getServerTick(),
                        "RITUAL",
                        "consumable_tension_spike",
                        itemId,
                        cp,
                        serverPlayer,
                        sw,
                        l1a - l0,
                        l1a,
                        g0a,
                        g1a
                    );
                    break;

                case "hushed_lure":
                    double g0b = TensionManager.getTension();
                    TensionManager.setTension(Math.max(0, TensionManager.getTension() - 0.02));
                    serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 200, 0));
                    world.getPlayers().forEach(p -> {
                        if (p.distanceTo(user) < 32 && p != user) {
                            TensionManager.setTension(Math.max(0, TensionManager.getTension() - 0.01));
                        }
                    });
                    double g1b = TensionManager.getTension();
                    double l1b = ChunkTensionManager.getLocalTension(sw, cp);
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.AMBIENT, 0.5F, 0.1F);
                    TensionLogger.traceWithGlobals(
                        TensionTraceClock.getServerTick(),
                        "RITUAL",
                        "consumable_area_calm",
                        itemId,
                        cp,
                        serverPlayer,
                        sw,
                        l1b - l0,
                        l1b,
                        g0b,
                        g1b
                    );
                    break;

                case "kinetic_phial":
                    serverPlayer.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 1));
                    System.out.println("[TENSION LOCK] Kinetic Phial activated. Tension reduction effects are now disabled for 60 seconds.");
                    world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7F, 1.5F);
                    double loc = ChunkTensionManager.getLocalTension(sw, cp);
                    TensionLogger.log(
                        TensionTraceClock.getServerTick(),
                        "RITUAL",
                        "kinetic_phial_speed_only",
                        0.0,
                        loc,
                        loc,
                        cp,
                        serverPlayer,
                        sw
                    );
                    break;

                default:
                    break;
            }

            if (!user.getAbilities().creativeMode) {
                itemStack.decrement(1);
            }
        }

        return TypedActionResult.success(itemStack, world.isClient());
    }
}
