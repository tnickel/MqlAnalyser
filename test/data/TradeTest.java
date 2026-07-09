package data;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

public class TradeTest {

    @Test
    public void testTradeCreationAndGetters() {
        LocalDateTime open = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime close = LocalDateTime.of(2026, 1, 1, 12, 30);
        
        Trade trade = new Trade(
            open, close, "Buy", "EURUSD", 0.5, 1.1000, 1.1020,
            1.0950, 1.1100, "provider_abc", "http://mql5.com/provider_abc",
            -1.5, 0.5, 100.0
        );

        assertEquals(open, trade.getOpenTime());
        assertEquals(close, trade.getCloseTime());
        assertEquals("Buy", trade.getType());
        assertEquals("EURUSD", trade.getSymbol());
        assertEquals(0.5, trade.getLots(), 0.0001);
        assertEquals(1.1000, trade.getOpenPrice(), 0.0001);
        assertEquals(1.1020, trade.getClosePrice(), 0.0001);
        assertEquals(1.0950, trade.getStopLoss(), 0.0001);
        assertEquals(1.1100, trade.getTakeProfit(), 0.0001);
        assertEquals("provider_abc", trade.getSignalProvider());
        assertEquals("http://mql5.com/provider_abc", trade.getSignalProviderURL());
        assertEquals(-1.5, trade.getCommission(), 0.0001);
        assertEquals(0.5, trade.getSwap(), 0.0001);
        assertEquals(100.0, trade.getProfit(), 0.0001);
        assertEquals(100.0, trade.getTotalProfit(), 0.0001);
    }

    @Test
    public void testTradeToString() {
        LocalDateTime open = LocalDateTime.of(2026, 1, 1, 12, 0);
        LocalDateTime close = LocalDateTime.of(2026, 1, 1, 12, 30);
        
        Trade trade = new Trade(
            open, close, "Buy", "EURUSD", 0.5, 1.1000, 1.1020,
            1.0950, 1.1100, "provider_abc", "http://mql5.com/provider_abc",
            -1.5, 0.5, 100.0
        );

        String str = trade.toString();
        assertTrue(str.contains("provider_abc"));
        assertTrue(str.contains("EURUSD"));
        assertTrue(str.contains("Buy"));
    }

    @Test
    public void testProviderStatsAverageDuration() {
        ProviderStats stats = new ProviderStats();
        
        // Test empty trades average duration is 0
        assertEquals(0.0, stats.getAverageDuration(), 0.001);
        
        LocalDateTime now = LocalDateTime.now();
        // Trade 1: 1 hour duration
        stats.addTrade(now, now.plusHours(1), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 10.0);
        // Trade 2: 2.5 hours duration
        stats.addTrade(now, now.plusMinutes(150), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 20.0);
        
        // Total duration = 1.0 + 2.5 = 3.5 hours. Average = 3.5 / 2 = 1.75 hours.
        assertEquals(1.75, stats.getAverageDuration(), 0.001);
    }

    @Test
    public void testProviderStatsPairsCount() {
        ProviderStats stats = new ProviderStats();
        
        // Empty trades has 0 pairs
        assertEquals(0, stats.getPairsCount());
        
        LocalDateTime now = LocalDateTime.now();
        stats.addTrade(now, now.plusHours(1), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 10.0);
        stats.addTrade(now, now.plusHours(1), "Buy", "GBPUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 20.0);
        stats.addTrade(now, now.plusHours(1), "Buy", "EURUSD", 0.1, 1.10, 1.11, 0, 0, 0, 0, 30.0);
        
        // Distinct pairs: EURUSD, GBPUSD -> 2 pairs
        assertEquals(2, stats.getPairsCount());
    }
}
