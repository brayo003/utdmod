package com.utdmod.mixin;

import com.utdmod.signals.TensionManager;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.TradeOffer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Modifies villager trades based on the nearest player's tension.
 * Offers a discount if tension is low (<0.15).
 */
@Mixin(VillagerEntity.class)
public abstract class VillagerEntityTradeMixin {

    @Shadow
    public World world;

    @Inject(method = "getCurrentOffer", at = @At("RETURN"), cancellable = true)
    private void utdmod$applyTensionTradeEffects(CallbackInfoReturnable<TradeOffer> cir) {
        VillagerEntity villager = (VillagerEntity)(Object)this;
        TradeOffer offer = cir.getReturnValue();
        if (offer == null) return;

        PlayerEntity nearestPlayer = world.getClosestPlayer(villager, 16.0);
        if (nearestPlayer instanceof ServerPlayerEntity serverPlayer) {
            double tension = TensionManager.getTension();

            // Apply discount if tension is low
            if (tension < 0.15) {
                ItemStack price = offer.getOriginalFirstBuyItem(); // Updated API method
                int originalCount = price.getCount();
                int discountedCount = Math.max(1, originalCount - 1);
                price.setCount(discountedCount);
                // M-5: Log villager trade reward applied count
                String villagerType = villager.getVillagerData().getProfession().toString();
                System.out.println("[VILLAGER TRADE REWARD] Tension discount applied. Tension: " + tension + 
                    ", Original: " + originalCount + ", New: " + price.getCount() + 
                    ", Player: " + serverPlayer.getName().getString() + ", Type: " + villagerType);
            }
        }

        cir.setReturnValue(offer);
    }
}
