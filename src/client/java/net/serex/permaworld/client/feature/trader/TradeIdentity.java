package net.serex.permaworld.client.feature.trader;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.Objects;

public final class TradeIdentity {

    private TradeIdentity() {
    }

    public static int hash(TradeDescriptor descriptor) {
        return Objects.hash(
                descriptor.firstCostId(), descriptor.firstCostCount(),
                descriptor.secondCostId(), descriptor.secondCostCount(),
                descriptor.resultId(), descriptor.resultCount()
        );
    }

    public static int hash(MerchantOffer offer) {
        return hash(descriptor(offer));
    }

    public static int legacyHash(MerchantOffer offer) {
        return hash(legacyDescriptor(offer));
    }

    public static TradeDescriptor descriptor(MerchantOffer offer) {
        return new TradeDescriptor(
                itemId(offer.getBaseCostA()),
                count(offer.getBaseCostA()),
                itemId(offer.getCostB()),
                count(offer.getCostB()),
                itemId(offer.getResult()),
                count(offer.getResult())
        );
    }

    private static TradeDescriptor legacyDescriptor(MerchantOffer offer) {
        return new TradeDescriptor(
                itemId(offer.getCostA()),
                count(offer.getCostA()),
                itemId(offer.getCostB()),
                count(offer.getCostB()),
                itemId(offer.getResult()),
                count(offer.getResult())
        );
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:air";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static int count(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return stack.getCount();
    }
}
