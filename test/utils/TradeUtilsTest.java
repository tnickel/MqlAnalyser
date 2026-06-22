package utils;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import data.Trade;

public class TradeUtilsTest {

    private List<Trade> trades;
    private LocalDateTime baseTime;

    @BeforeEach
    public void setUp() {
        trades = new ArrayList<>();
        baseTime = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
    }

    private Trade createMockTrade(LocalDateTime open, LocalDateTime close, double lots) {
        return new Trade(
            open, close, "Buy", "EURUSD", lots, 1.1000, 1.1010,
            0.0, 0.0, "provider_test", "http://test", 0.0, 0.0, 10.0
        );
    }

    @Test
    public void testGetActiveTradesAt_NoTrades_ReturnsEmptyList() {
        List<Trade> active = TradeUtils.getActiveTradesAt(trades, baseTime);
        assertTrue(active.isEmpty());
    }

    @Test
    public void testGetActiveTradesAt_CorrectFiltering() {
        Trade t1 = createMockTrade(baseTime.minusHours(1), baseTime.plusHours(1), 0.1);
        Trade t2 = createMockTrade(baseTime.plusHours(1), baseTime.plusHours(2), 0.2);
        trades.add(t1);
        trades.add(t2);

        List<Trade> active = TradeUtils.getActiveTradesAt(trades, baseTime);
        assertEquals(1, active.size());
        assertEquals(t1, active.get(0));
    }

    @Test
    public void testCalculateOpenTradesAt_CalculatesCorrectly() {
        trades.add(createMockTrade(baseTime.minusHours(1), baseTime.plusHours(1), 0.1));
        trades.add(createMockTrade(baseTime.minusMinutes(30), baseTime.plusMinutes(30), 0.2));
        trades.add(createMockTrade(baseTime.plusHours(2), baseTime.plusHours(3), 0.3));

        assertEquals(2, TradeUtils.calculateOpenTradesAt(trades, baseTime));
        assertEquals(1, TradeUtils.calculateOpenTradesAt(trades, baseTime.plusHours(2).plusMinutes(30)));
        assertEquals(0, TradeUtils.calculateOpenTradesAt(trades, baseTime.plusHours(5)));
    }

    @Test
    public void testCalculateOpenLotsAt_CalculatesCorrectly() {
        trades.add(createMockTrade(baseTime.minusHours(1), baseTime.plusHours(1), 0.1));
        trades.add(createMockTrade(baseTime.minusMinutes(30), baseTime.plusMinutes(30), 0.2));
        trades.add(createMockTrade(baseTime.plusHours(2), baseTime.plusHours(3), 0.3));

        assertEquals(0.3, TradeUtils.calculateOpenLotsAt(trades, baseTime), 0.0001);
        assertEquals(0.0, TradeUtils.calculateOpenLotsAt(trades, baseTime.plusHours(5)), 0.0001);
    }

    @Test
    public void testFindMaxConcurrentTrades_CalculatesCorrectly() {
        trades.add(createMockTrade(baseTime, baseTime.plusHours(5), 0.1));
        trades.add(createMockTrade(baseTime.plusHours(1), baseTime.plusHours(3), 0.2));
        trades.add(createMockTrade(baseTime.plusHours(2), baseTime.plusHours(4), 0.3));
        trades.add(createMockTrade(baseTime.plusHours(6), baseTime.plusHours(7), 0.4));

        assertEquals(3, TradeUtils.findMaxConcurrentTrades(trades));
    }

    @Test
    public void testFindMaxConcurrentLots_CalculatesCorrectly() {
        trades.add(createMockTrade(baseTime, baseTime.plusHours(5), 1.0));
        trades.add(createMockTrade(baseTime.plusHours(1), baseTime.plusHours(3), 1.5));
        trades.add(createMockTrade(baseTime.plusHours(2), baseTime.plusHours(4), 2.0));
        trades.add(createMockTrade(baseTime.plusHours(6), baseTime.plusHours(7), 5.0));

        assertEquals(5.0, TradeUtils.findMaxConcurrentLots(trades), 0.0001);
    }

    @Test
    public void testGetActiveTradesAt_BoundaryOpenTime() {
        Trade t1 = createMockTrade(baseTime, baseTime.plusHours(1), 0.1);
        trades.add(t1);
        List<Trade> active = TradeUtils.getActiveTradesAt(trades, baseTime);
        assertEquals(1, active.size());
        assertEquals(t1, active.get(0));
    }

    @Test
    public void testGetActiveTradesAt_BoundaryCloseTime() {
        Trade t1 = createMockTrade(baseTime.minusHours(1), baseTime, 0.1);
        trades.add(t1);
        List<Trade> active = TradeUtils.getActiveTradesAt(trades, baseTime);
        // Closed trades should not be active exactly at their close time
        assertTrue(active.isEmpty());
    }

    @Test
    public void testFindMaxConcurrentTrades_EmptyTrades() {
        assertEquals(0, TradeUtils.findMaxConcurrentTrades(trades));
    }

    @Test
    public void testFindMaxConcurrentLots_EmptyTrades() {
        assertEquals(0.0, TradeUtils.findMaxConcurrentLots(trades), 0.0001);
    }

    @Test
    public void testCalculateOpenTradesAt_NullList() {
        assertEquals(0, TradeUtils.calculateOpenTradesAt(null, baseTime));
    }

    @Test
    public void testCalculateOpenLotsAt_NullList() {
        assertEquals(0.0, TradeUtils.calculateOpenLotsAt(null, baseTime), 0.0001);
    }

    @Test
    public void testFindMaxConcurrentTrades_SingleTrade() {
        trades.add(createMockTrade(baseTime, baseTime.plusHours(1), 0.1));
        assertEquals(1, TradeUtils.findMaxConcurrentTrades(trades));
    }

    @Test
    public void testFindMaxConcurrentLots_SingleTrade() {
        trades.add(createMockTrade(baseTime, baseTime.plusHours(1), 0.75));
        assertEquals(0.75, TradeUtils.findMaxConcurrentLots(trades), 0.0001);
    }

    @Test
    public void testFindMaxConcurrentTrades_DisjointTrades() {
        trades.add(createMockTrade(baseTime, baseTime.plusHours(1), 0.5));
        trades.add(createMockTrade(baseTime.plusHours(2), baseTime.plusHours(3), 0.5));
        assertEquals(1, TradeUtils.findMaxConcurrentTrades(trades));
    }

    @Test
    public void testFindMaxConcurrentLots_DisjointTrades() {
        trades.add(createMockTrade(baseTime, baseTime.plusHours(1), 1.5));
        trades.add(createMockTrade(baseTime.plusHours(2), baseTime.plusHours(3), 2.5));
        assertEquals(2.5, TradeUtils.findMaxConcurrentLots(trades), 0.0001);
    }

    @Test
    public void testFindMaxConcurrentTrades_NestedTrades() {
        trades.add(createMockTrade(baseTime, baseTime.plusHours(10), 0.5));
        trades.add(createMockTrade(baseTime.plusHours(2), baseTime.plusHours(8), 0.5));
        trades.add(createMockTrade(baseTime.plusHours(4), baseTime.plusHours(6), 0.5));
        assertEquals(3, TradeUtils.findMaxConcurrentTrades(trades));
    }

    @Test
    public void testFindMaxConcurrentLots_NestedTrades() {
        trades.add(createMockTrade(baseTime, baseTime.plusHours(10), 1.0));
        trades.add(createMockTrade(baseTime.plusHours(2), baseTime.plusHours(8), 2.0));
        trades.add(createMockTrade(baseTime.plusHours(4), baseTime.plusHours(6), 3.0));
        assertEquals(6.0, TradeUtils.findMaxConcurrentLots(trades), 0.0001);
    }

    @Test
    public void testFindMaxConcurrentTrades_ZeroDurationTrade() {
        trades.add(createMockTrade(baseTime, baseTime, 0.1));
        assertEquals(1, TradeUtils.findMaxConcurrentTrades(trades));
    }

    @Test
    public void testFindMaxConcurrentLots_ZeroLots() {
        trades.add(createMockTrade(baseTime, baseTime.plusHours(1), 0.0));
        assertEquals(0.0, TradeUtils.findMaxConcurrentLots(trades), 0.0001);
    }

    @Test
    public void testFindMaxConcurrentTrades_AllNullInputs() {
        assertEquals(0, TradeUtils.findMaxConcurrentTrades(null));
        assertEquals(0.0, TradeUtils.findMaxConcurrentLots(null), 0.0001);
    }
}
