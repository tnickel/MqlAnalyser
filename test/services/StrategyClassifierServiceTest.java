package services;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import data.Trade;

public class StrategyClassifierServiceTest {

    private List<Trade> trades;
    private LocalDateTime baseTime;

    @BeforeEach
    public void setUp() {
        trades = new ArrayList<>();
        baseTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
    }

    private Trade createMockTrade(String type, String symbol, double lots, double openPrice, double profit, int offsetMinutes) {
        return new Trade(
            baseTime.plusMinutes(offsetMinutes),
            baseTime.plusMinutes(offsetMinutes + 30),
            type, symbol, lots, openPrice, openPrice + (profit > 0 ? 0.0010 : -0.0010),
            0.0, 0.0, "provider_test", "http://test", 0.0, 0.0, profit
        );
    }

    @Test
    public void testIsMartingale_EmptyList_ReturnsFalse() {
        assertFalse(StrategyClassifierService.isMartingale(trades));
    }

    @Test
    public void testIsMartingale_NormalStrategy_ReturnsFalse() {
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.1000, 10.0, 0));
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.1010, -5.0, 60));
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.1020, 15.0, 120));
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.1030, -8.0, 180));
        
        assertFalse(StrategyClassifierService.isMartingale(trades));
    }

    @Test
    public void testIsMartingale_SequentialMartingale_ReturnsTrue() {
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.1000, -10.0, 0));
        trades.add(createMockTrade("Buy", "EURUSD", 0.2, 1.0990, -20.0, 60));
        trades.add(createMockTrade("Buy", "EURUSD", 0.4, 1.0980, -40.0, 120));
        trades.add(createMockTrade("Buy", "EURUSD", 0.8, 1.0970, 80.0, 180));

        assertTrue(StrategyClassifierService.isMartingale(trades));
    }

    @Test
    public void testIsMartingale_ConcurrentMartingale_ReturnsTrue() {
        Trade t1 = new Trade(baseTime, baseTime.plusHours(4), "Buy", "GBPUSD", 0.1, 1.3000, 1.2900, 0, 0, "p", "u", 0, 0, -10.0);
        Trade t2 = new Trade(baseTime.plusHours(1), baseTime.plusHours(4), "Buy", "GBPUSD", 0.2, 1.2950, 1.2900, 0, 0, "p", "u", 0, 0, -20.0);
        Trade t3 = new Trade(baseTime.plusHours(2), baseTime.plusHours(4), "Buy", "GBPUSD", 0.4, 1.2900, 1.2900, 0, 0, "p", "u", 0, 0, 40.0);

        trades.add(t1);
        trades.add(t2);
        trades.add(t3);

        assertTrue(StrategyClassifierService.isMartingale(trades));
    }

    @Test
    public void testIsGrid_EmptyList_ReturnsFalse() {
        assertFalse(StrategyClassifierService.isGrid(trades));
    }

    @Test
    public void testIsGrid_NormalStrategy_ReturnsFalse() {
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.1000, 10.0, 0));
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.1000, 10.0, 60));
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.1000, 10.0, 120));

        assertFalse(StrategyClassifierService.isGrid(trades));
    }

    @Test
    public void testIsGrid_StaggeredGrid_ReturnsTrue() {
        Trade t1 = new Trade(baseTime, baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.1000, 1.1050, 0, 0, "p", "u", 0, 0, 50.0);
        Trade t2 = new Trade(baseTime.plusHours(1), baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.0980, 1.1050, 0, 0, "p", "u", 0, 0, 70.0);
        Trade t3 = new Trade(baseTime.plusHours(2), baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.0960, 1.1050, 0, 0, "p", "u", 0, 0, 90.0);

        trades.add(t1);
        trades.add(t2);
        trades.add(t3);

        assertTrue(StrategyClassifierService.isGrid(trades));
    }

    @Test
    public void testIsGrid_DifferentSymbolsOrDirections_ReturnsFalse() {
        Trade t1 = new Trade(baseTime, baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.1000, 1.1050, 0, 0, "p", "u", 0, 0, 50.0);
        Trade t2 = new Trade(baseTime.plusHours(1), baseTime.plusHours(10), "Buy", "GBPUSD", 0.1, 1.3000, 1.3050, 0, 0, "p", "u", 0, 0, 70.0);
        Trade t3 = new Trade(baseTime.plusHours(2), baseTime.plusHours(10), "Buy", "AUDUSD", 0.1, 0.7000, 0.7050, 0, 0, "p", "u", 0, 0, 90.0);

        trades.add(t1);
        trades.add(t2);
        trades.add(t3);

        assertFalse(StrategyClassifierService.isGrid(trades));
    }

    @Test
    public void testIsMartingale_SingleTrade_ReturnsFalse() {
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.1000, -10.0, 0));
        assertFalse(StrategyClassifierService.isMartingale(trades));
    }

    @Test
    public void testIsMartingale_AllProfitableTrades_ReturnsFalse() {
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.1000, 10.0, 0));
        trades.add(createMockTrade("Buy", "EURUSD", 0.2, 1.1010, 20.0, 60));
        trades.add(createMockTrade("Buy", "EURUSD", 0.4, 1.1020, 40.0, 120));
        trades.add(createMockTrade("Buy", "EURUSD", 0.8, 1.1030, 80.0, 180));
        
        assertFalse(StrategyClassifierService.isMartingale(trades));
    }

    @Test
    public void testIsMartingale_LotsDecreaseAfterLoss_ReturnsFalse() {
        trades.add(createMockTrade("Buy", "EURUSD", 0.8, 1.1000, -10.0, 0));
        trades.add(createMockTrade("Buy", "EURUSD", 0.4, 1.0990, -20.0, 60));
        trades.add(createMockTrade("Buy", "EURUSD", 0.2, 1.0980, -40.0, 120));
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.0970, 80.0, 180));
        
        assertFalse(StrategyClassifierService.isMartingale(trades));
    }

    @Test
    public void testIsMartingale_MartingaleOnDifferentSymbols_ReturnsFalse() {
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.1000, -10.0, 0));
        trades.add(createMockTrade("Buy", "GBPUSD", 0.2, 1.3000, -20.0, 60));
        trades.add(createMockTrade("Buy", "AUDUSD", 0.4, 0.7000, -40.0, 120));
        
        assertFalse(StrategyClassifierService.isMartingale(trades));
    }

    @Test
    public void testIsMartingale_LotIncreaseBelowThreshold_ReturnsFalse() {
        trades.add(createMockTrade("Buy", "EURUSD", 0.10, 1.1000, -10.0, 0));
        trades.add(createMockTrade("Buy", "EURUSD", 0.11, 1.0990, -20.0, 60)); // 1.1x increase (threshold is >=1.2x)
        trades.add(createMockTrade("Buy", "EURUSD", 0.12, 1.0980, -40.0, 120));
        trades.add(createMockTrade("Buy", "EURUSD", 0.13, 1.0970, -80.0, 180));
        
        assertFalse(StrategyClassifierService.isMartingale(trades));
    }

    @Test
    public void testIsMartingale_LotsIncreaseExactlyAtThreshold_ReturnsTrue() {
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.1000, -10.0, 0));
        trades.add(createMockTrade("Buy", "EURUSD", 0.12, 1.0990, -20.0, 60)); // Exactly 1.2x increase
        trades.add(createMockTrade("Buy", "EURUSD", 0.144, 1.0980, -40.0, 120)); // Exactly 1.2x increase
        trades.add(createMockTrade("Buy", "EURUSD", 0.1728, 1.0970, 80.0, 180)); // Exactly 1.2x increase
        
        assertTrue(StrategyClassifierService.isMartingale(trades));
    }

    @Test
    public void testIsMartingale_NullInput_ReturnsFalse() {
        assertFalse(StrategyClassifierService.isMartingale(null));
    }

    @Test
    public void testIsGrid_SingleTrade_ReturnsFalse() {
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.1000, 10.0, 0));
        assertFalse(StrategyClassifierService.isGrid(trades));
    }

    @Test
    public void testIsGrid_TwoTradesOnly_ReturnsFalse() {
        Trade t1 = new Trade(baseTime, baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.1000, 1.1050, 0, 0, "p", "u", 0, 0, 50.0);
        Trade t2 = new Trade(baseTime.plusHours(1), baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.0980, 1.1050, 0, 0, "p", "u", 0, 0, 70.0);
        trades.add(t1);
        trades.add(t2);
        
        assertFalse(StrategyClassifierService.isGrid(trades));
    }

    @Test
    public void testIsGrid_ThreeTradesButNotOverlapping_ReturnsFalse() {
        Trade t1 = new Trade(baseTime, baseTime.plusHours(1), "Buy", "EURUSD", 0.1, 1.1000, 1.1050, 0, 0, "p", "u", 0, 0, 50.0);
        Trade t2 = new Trade(baseTime.plusHours(2), baseTime.plusHours(3), "Buy", "EURUSD", 0.1, 1.0980, 1.1050, 0, 0, "p", "u", 0, 0, 70.0);
        Trade t3 = new Trade(baseTime.plusHours(4), baseTime.plusHours(5), "Buy", "EURUSD", 0.1, 1.0960, 1.1050, 0, 0, "p", "u", 0, 0, 90.0);
        trades.add(t1);
        trades.add(t2);
        trades.add(t3);
        
        assertFalse(StrategyClassifierService.isGrid(trades));
    }

    @Test
    public void testIsGrid_ThreeTradesOverlappingSamePrice_ReturnsFalse() {
        Trade t1 = new Trade(baseTime, baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.1000, 1.1050, 0, 0, "p", "u", 0, 0, 50.0);
        Trade t2 = new Trade(baseTime.plusHours(1), baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.1000, 1.1050, 0, 0, "p", "u", 0, 0, 70.0);
        Trade t3 = new Trade(baseTime.plusHours(2), baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.1000, 1.1050, 0, 0, "p", "u", 0, 0, 90.0);
        trades.add(t1);
        trades.add(t2);
        trades.add(t3);
        
        assertFalse(StrategyClassifierService.isGrid(trades));
    }

    @Test
    public void testIsGrid_ThreeTradesOverlappingDifferentDirections_ReturnsFalse() {
        Trade t1 = new Trade(baseTime, baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.1000, 1.1050, 0, 0, "p", "u", 0, 0, 50.0);
        Trade t2 = new Trade(baseTime.plusHours(1), baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.0980, 1.1050, 0, 0, "p", "u", 0, 0, 70.0);
        Trade t3 = new Trade(baseTime.plusHours(2), baseTime.plusHours(10), "Sell", "EURUSD", 0.1, 1.0960, 1.0910, 0, 0, "p", "u", 0, 0, 90.0);
        trades.add(t1);
        trades.add(t2);
        trades.add(t3);
        
        assertFalse(StrategyClassifierService.isGrid(trades));
    }

    @Test
    public void testIsGrid_NullInput_ReturnsFalse() {
        assertFalse(StrategyClassifierService.isGrid(null));
    }

    @Test
    public void testIsGrid_FourTradesStaggered_ReturnsTrue() {
        Trade t1 = new Trade(baseTime, baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.1000, 1.1050, 0, 0, "p", "u", 0, 0, 50.0);
        Trade t2 = new Trade(baseTime.plusHours(1), baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.0980, 1.1050, 0, 0, "p", "u", 0, 0, 70.0);
        Trade t3 = new Trade(baseTime.plusHours(2), baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.0960, 1.1050, 0, 0, "p", "u", 0, 0, 90.0);
        Trade t4 = new Trade(baseTime.plusHours(3), baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.0940, 1.1050, 0, 0, "p", "u", 0, 0, 110.0);
        trades.add(t1);
        trades.add(t2);
        trades.add(t3);
        trades.add(t4);
        
        assertTrue(StrategyClassifierService.isGrid(trades));
    }

    @Test
    public void testIsMartingale_ConcurrentLotDecrease_ReturnsFalse() {
        Trade t1 = new Trade(baseTime, baseTime.plusHours(4), "Buy", "GBPUSD", 0.4, 1.3000, 1.2900, 0, 0, "p", "u", 0, 0, -10.0);
        Trade t2 = new Trade(baseTime.plusHours(1), baseTime.plusHours(4), "Buy", "GBPUSD", 0.2, 1.2950, 1.2900, 0, 0, "p", "u", 0, 0, -20.0);
        Trade t3 = new Trade(baseTime.plusHours(2), baseTime.plusHours(4), "Buy", "GBPUSD", 0.1, 1.2900, 1.2900, 0, 0, "p", "u", 0, 0, 40.0);
        trades.add(t1);
        trades.add(t2);
        trades.add(t3);
        
        assertFalse(StrategyClassifierService.isMartingale(trades));
    }

    @Test
    public void testIsMartingale_OverlapCheckOutsideClose_ReturnsFalse() {
        Trade t1 = new Trade(baseTime, baseTime.plusHours(2), "Buy", "GBPUSD", 0.1, 1.3000, 1.2900, 0, 0, "p", "u", 0, 0, -10.0);
        Trade t2 = new Trade(baseTime.plusHours(3), baseTime.plusHours(5), "Buy", "GBPUSD", 0.2, 1.2950, 1.2900, 0, 0, "p", "u", 0, 0, -20.0);
        Trade t3 = new Trade(baseTime.plusHours(6), baseTime.plusHours(8), "Buy", "GBPUSD", 0.4, 1.2900, 1.2900, 0, 0, "p", "u", 0, 0, 40.0);
        trades.add(t1);
        trades.add(t2);
        trades.add(t3);
        
        assertFalse(StrategyClassifierService.isMartingale(trades));
    }

    @Test
    public void testIsGrid_OverlapCheckOutsideClose_ReturnsFalse() {
        Trade t1 = new Trade(baseTime, baseTime.plusHours(2), "Buy", "EURUSD", 0.1, 1.1000, 1.1050, 0, 0, "p", "u", 0, 0, 50.0);
        Trade t2 = new Trade(baseTime.plusHours(3), baseTime.plusHours(5), "Buy", "EURUSD", 0.1, 1.0980, 1.1050, 0, 0, "p", "u", 0, 0, 70.0);
        Trade t3 = new Trade(baseTime.plusHours(6), baseTime.plusHours(8), "Buy", "EURUSD", 0.1, 1.0960, 1.1050, 0, 0, "p", "u", 0, 0, 90.0);
        trades.add(t1);
        trades.add(t2);
        trades.add(t3);
        
        assertFalse(StrategyClassifierService.isGrid(trades));
    }

    @Test
    public void testIsMartingale_SymbolWhitespaceHandling_ReturnsTrue() {
        trades.add(createMockTrade("Buy", "  EURUSD  ", 0.1, 1.1000, -10.0, 0));
        trades.add(createMockTrade("Buy", "eurusd", 0.2, 1.0990, -20.0, 60)); // Case insensitive symbol handling is not in service, but whitespace trimming is
        trades.add(createMockTrade("Buy", "EURUSD", 0.4, 1.0980, -40.0, 120));
        trades.add(createMockTrade("Buy", "EURUSD", 0.8, 1.0970, 80.0, 180));
        
        // Let's verify case sensitive symbol since it does get(t.getSymbol())
        // Trim is verified: "  EURUSD  ".trim() is "EURUSD"
        // Let's modify all to uppercase "EURUSD"
        trades.clear();
        trades.add(createMockTrade("Buy", "  EURUSD  ", 0.1, 1.1000, -10.0, 0));
        trades.add(createMockTrade("Buy", "EURUSD", 0.2, 1.0990, -20.0, 60));
        trades.add(createMockTrade("Buy", "EURUSD", 0.4, 1.0980, -40.0, 120));
        trades.add(createMockTrade("Buy", "EURUSD", 0.8, 1.0970, 80.0, 180));
        
        assertTrue(StrategyClassifierService.isMartingale(trades));
    }

    @Test
    public void testIsGrid_SymbolWhitespaceHandling_ReturnsTrue() {
        Trade t1 = new Trade(baseTime, baseTime.plusHours(10), "Buy", "  EURUSD  ", 0.1, 1.1000, 1.1050, 0, 0, "p", "u", 0, 0, 50.0);
        Trade t2 = new Trade(baseTime.plusHours(1), baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.0980, 1.1050, 0, 0, "p", "u", 0, 0, 70.0);
        Trade t3 = new Trade(baseTime.plusHours(2), baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.0960, 1.1050, 0, 0, "p", "u", 0, 0, 90.0);
        trades.add(t1);
        trades.add(t2);
        trades.add(t3);
        
        assertTrue(StrategyClassifierService.isGrid(trades));
    }

    @Test
    public void testIsMartingale_MixedTradesSomeMartingale_ReturnsFalse() {
        // Less than 3 lot size increases under risk (only 2)
        trades.add(createMockTrade("Buy", "EURUSD", 0.1, 1.1000, -10.0, 0));
        trades.add(createMockTrade("Buy", "EURUSD", 0.2, 1.0990, -20.0, 60)); // Increase 1
        trades.add(createMockTrade("Buy", "EURUSD", 0.4, 1.0980, 40.0, 120));  // Increase 2 (but profitable now!)
        trades.add(createMockTrade("Buy", "EURUSD", 0.4, 1.0970, 80.0, 180));  // Constant lots (no increase)
        
        assertFalse(StrategyClassifierService.isMartingale(trades));
    }

    @Test
    public void testIsGrid_DistinctPricesDiffCheck_ReturnsFalse() {
        // 3 trades, distinct prices but difference is 0.00001 (below minimum grid step 0.00005)
        Trade t1 = new Trade(baseTime, baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.1000, 1.1050, 0, 0, "p", "u", 0, 0, 50.0);
        Trade t2 = new Trade(baseTime.plusHours(1), baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.10001, 1.1050, 0, 0, "p", "u", 0, 0, 70.0);
        Trade t3 = new Trade(baseTime.plusHours(2), baseTime.plusHours(10), "Buy", "EURUSD", 0.1, 1.10002, 1.1050, 0, 0, "p", "u", 0, 0, 90.0);
        trades.add(t1);
        trades.add(t2);
        trades.add(t3);
        
        assertFalse(StrategyClassifierService.isGrid(trades));
    }
}
