package services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import data.Trade;

public class StrategyClassifierService {

    /**
     * Analyzes trade history to determine if a strategy uses a Martingale system.
     */
    public static boolean isMartingale(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return false;
        }

        // Group trades by symbol
        Map<String, List<Trade>> tradesBySymbol = new HashMap<>();
        for (Trade t : trades) {
            if (t.getSymbol() != null && !t.getSymbol().trim().isEmpty()) {
                String cleanSymbol = t.getSymbol().trim().toUpperCase();
                tradesBySymbol.computeIfAbsent(cleanSymbol, k -> new ArrayList<>()).add(t);
            }
        }

        int martingaleIncreases = 0;

        for (List<Trade> symbolTrades : tradesBySymbol.values()) {
            if (symbolTrades.size() < 2) {
                continue;
            }

            // Sort by open time
            symbolTrades.sort((t1, t2) -> t1.getOpenTime().compareTo(t2.getOpenTime()));

            // 1. Sequential Martingale: lot size increases after a loss
            for (int i = 1; i < symbolTrades.size(); i++) {
                Trade prev = symbolTrades.get(i - 1);
                Trade curr = symbolTrades.get(i);
                if (prev.getProfit() < 0 && curr.getLots() >= prev.getLots() * 1.2) {
                    martingaleIncreases++;
                }
            }

            // 2. Concurrent Martingale: overlapping trades with increasing lot sizes
            for (int i = 0; i < symbolTrades.size(); i++) {
                Trade t1 = symbolTrades.get(i);
                for (int j = i + 1; j < symbolTrades.size(); j++) {
                    Trade t2 = symbolTrades.get(j);
                    // Check if they are in the same direction and overlap in time
                    if (t1.getType().equalsIgnoreCase(t2.getType()) &&
                        !t2.getOpenTime().isAfter(t1.getCloseTime()) &&
                        !t2.getOpenTime().isBefore(t1.getOpenTime())) {
                        if (t2.getLots() >= t1.getLots() * 1.2) {
                            martingaleIncreases++;
                        }
                    }
                }
            }
        }

        // If we find 3 or more instances of lot increases under risk, classify as Martingale
        return martingaleIncreases >= 3;
    }

    /**
     * Analyzes trade history to determine if a strategy uses a Grid system.
     */
    public static boolean isGrid(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return false;
        }

        // Group trades by symbol
        Map<String, List<Trade>> tradesBySymbol = new HashMap<>();
        for (Trade t : trades) {
            if (t.getSymbol() != null && !t.getSymbol().trim().isEmpty()) {
                String cleanSymbol = t.getSymbol().trim().toUpperCase();
                tradesBySymbol.computeIfAbsent(cleanSymbol, k -> new ArrayList<>()).add(t);
            }
        }

        for (List<Trade> symbolTrades : tradesBySymbol.values()) {
            if (symbolTrades.size() < 3) {
                continue;
            }

            // Sort by open time
            symbolTrades.sort((t1, t2) -> t1.getOpenTime().compareTo(t2.getOpenTime()));

            // Find concurrent trades in the same direction
            for (Trade t1 : symbolTrades) {
                List<Double> openPrices = new ArrayList<>();
                openPrices.add(t1.getOpenPrice());

                for (Trade t2 : symbolTrades) {
                    if (t1 == t2) {
                        continue;
                    }

                    // Must be in the same direction (Buy/Sell)
                    if (!t1.getType().equalsIgnoreCase(t2.getType())) {
                        continue;
                    }

                    // Check if t2 was open concurrently with t1
                    boolean overlaps = !t2.getOpenTime().isAfter(t1.getCloseTime()) &&
                                       !t2.getCloseTime().isBefore(t1.getOpenTime());

                    if (overlaps) {
                        openPrices.add(t2.getOpenPrice());
                    }
                }

                // If 3 or more concurrent trades exist in the same direction
                if (openPrices.size() >= 3) {
                    // Check for grid entries (different open prices)
                    int distinctPricesCount = 1;
                    for (int i = 0; i < openPrices.size(); i++) {
                        for (int j = i + 1; j < openPrices.size(); j++) {
                            double diff = Math.abs(openPrices.get(i) - openPrices.get(j));
                            if (diff > 0.00005) { // Minimum grid step
                                distinctPricesCount++;
                                break;
                            }
                        }
                    }
                    if (distinctPricesCount >= 3) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
