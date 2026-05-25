package net.serex.permaworld.client.feature.trader;

public record TradeDescriptor(
        String firstCostId,
        int firstCostCount,
        String secondCostId,
        int secondCostCount,
        String resultId,
        int resultCount
) {
}
