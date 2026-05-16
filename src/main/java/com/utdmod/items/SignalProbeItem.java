package com.utdmod.items;

import com.utdmod.core.TensionManager;
import com.utdmod.core.TensionTraceClock;
import com.utdmod.core.TensionTraceLogger;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class SignalProbeItem extends Item {

    public SignalProbeItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient()) {
            return TypedActionResult.success(player.getStackInHand(hand));
        }

        double tension = TensionManager.getTension();
        boolean storm = TensionManager.isStormActive();

        player.sendMessage(net.minecraft.text.Text.literal("UTD Probe: Tension = "
            + String.format("%.1f", tension) + " | Storm: " + (storm ? "ACTIVE" : "CALM")), false);

        if (player instanceof ServerPlayerEntity) {
            TensionTraceLogger.traceSystem(
                TensionTraceClock.getServerTick(),
                "PROBE_READ",
                "tension=" + String.format("%.3f", tension) + " storm=" + storm,
                tension,
                "signal_probe_item"
            );
        }

        return TypedActionResult.success(player.getStackInHand(hand));
    }
}
