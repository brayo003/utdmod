package com.utdmod.item;

import com.utdmod.core.TensionManager;
import com.utdmod.core.TensionTraceClock;
import com.utdmod.tick.TensionLogger;
import com.utdmod.tension.ChunkTensionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

public class RitualStabilizerItem extends Item {

    public RitualStabilizerItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity sp && world instanceof ServerWorld sw) {
            ChunkPos cp = new ChunkPos(user.getBlockPos());
            double g0 = TensionManager.getTension();
            double l0 = ChunkTensionManager.getLocalTension(sw, cp);
            TensionManager.reduceTension(3.0);
            user.sendMessage(Text.literal("Ritual stabilized the area! -3.0 tension"), true);

            ItemStack stack = user.getStackInHand(hand);
            if (!user.getAbilities().creativeMode) {
                stack.decrement(1);
            }
            double g1 = TensionManager.getTension();
            double l1 = ChunkTensionManager.getLocalTension(sw, cp);
            TensionLogger.traceWithGlobals(
                TensionTraceClock.getServerTick(),
                "RITUAL",
                "stabilizer_use",
                "global_reduce_3.0",
                cp,
                sp,
                sw,
                l1 - l0,
                l1,
                g0,
                g1
            );
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }
}
